package vn.codegym.smartcafe.database;

import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.zip.CRC32;

/** Explicit test-tool entry point; never part of application startup or the runtime migration locations. */
public final class DatabaseMigrationRehearsal {
    public static void main(String[] args) throws Exception {
        Path archive = Path.of(System.getenv("MIGRATION_ARCHIVE_DIR")).toRealPath();
        Path proposal = Path.of("../docs/database/proposals/2026-11-13-dt2-2/home-dependencies.sql").toRealPath();
        var history = JsonMapper.builder().build().readTree(new ClassPathResource("db/current-shared-history.json").getInputStream()).get("history");
        Path migrations = Files.createTempDirectory("pr7-migration-rehearsal-").toRealPath();
        var copied = new ArrayList<Path>();
        try (var schema = new TestSchema("db/current-shared-2026-11-13.sql")) {
            schema.sharedHistory();
            for (var row : history) {
                String file = row.get("script").asText();
                if (!file.matches("V00[1-8]__[a-z_]+\\.sql")) throw new IllegalStateException("Unexpected historical script name");
                Path source = archive.resolve(file);
                CRC32 crc = new CRC32();
                var lines = Files.readAllLines(source, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) crc.update((i == 0 ? lines.get(i).replaceFirst("^\\uFEFF", "") : lines.get(i)).getBytes(StandardCharsets.UTF_8));
                if ((int) crc.getValue() != row.get("checksum").asInt()) throw new IllegalStateException("Source checksum mismatch: " + file);
                copied.add(Files.copy(source, migrations.resolve(file)));
            }
            // Repeatable name is a rehearsal device only, not a reserved production version/lineage decision.
            copied.add(Files.copy(proposal, migrations.resolve("R__candidate_home_dependencies.sql")));
            schema.assertIsolated();
            schema.update("INSERT INTO users(username,password_hash,full_name,email,role,status) VALUES('migration_sentinel',?,'Synthetic sentinel','migration_sentinel@example.test','STAFF','ACTIVE')",
                    new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(UUID.randomUUID().toString()));
            schema.update("INSERT INTO auth_otp_challenges(id,email,purpose,code_hash,expires_at,next_send_at) VALUES(REPEAT('e',64),'migration_sentinel@example.test','REGISTER',REPEAT('f',64),?,?)",
                    Timestamp.from(Instant.now().plusSeconds(600)), Timestamp.from(Instant.now().plusSeconds(60)));
            var users = schema.jdbc.queryForList("SELECT * FROM users");
            var otp = schema.jdbc.queryForList("SELECT * FROM auth_otp_challenges");
            var before = schema.jdbc.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank");
            Flyway flyway = Flyway.configure().dataSource(schema.source).locations("filesystem:" + migrations)
                    .validateOnMigrate(true).baselineOnMigrate(false).load();
            if (flyway.migrate().migrationsExecuted != 1) throw new AssertionError("Only the candidate should apply");
            if (!schema.jdbc.queryForList("SELECT * FROM users").equals(users) || !schema.jdbc.queryForList("SELECT * FROM auth_otp_challenges").equals(otp)) throw new AssertionError("Sentinels changed");
            if (!schema.jdbc.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank").equals(before)) throw new AssertionError("Immutable history changed");
            if (flyway.migrate().migrationsExecuted != 0) throw new AssertionError("Candidate applied twice");
            var after = schema.jdbc.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank");
            Path tampered = migrations.resolve("V001__create_home_highlights.sql");
            Files.writeString(tampered, "-- deliberate test checksum drift\n" + Files.readString(tampered));
            if (Flyway.configure().dataSource(schema.source).locations("filesystem:" + migrations).load().validateWithResult().validationSuccessful) throw new AssertionError("Changed historical source was accepted");
            if (!schema.jdbc.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank").equals(after)) throw new AssertionError("Validation wrote history");
            System.out.println("MIGRATION_REHEARSAL_PASS sourcesMatched=8 candidateApplied=1 secondRun=0 sentinelsPreserved=true checksumDriftRejected=true approvedTarget=false");
        } finally {
            // Delete only the exact9 files copied into this process-owned test directory.
            for (Path file : copied) {
                if (!file.getParent().equals(migrations)) throw new IllegalStateException("Unsafe test cleanup path");
                Files.deleteIfExists(file);
            }
            Files.delete(migrations);
        }
    }
}

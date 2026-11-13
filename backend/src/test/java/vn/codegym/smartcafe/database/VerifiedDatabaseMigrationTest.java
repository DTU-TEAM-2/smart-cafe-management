package vn.codegym.smartcafe.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;

class VerifiedDatabaseMigrationTest {
    private static final String SQL = "db/smart-cafe/V20261113_01__smart_cafe_schema.sql";

    private Flyway nativeFlyway(TestSchema schema) {
        return Flyway.configure().dataSource(schema.source).locations("classpath:db/smart-cafe")
                .baselineVersion("20261113.1").baselineDescription(VerifiedSchemaContract.BASELINE_DESCRIPTION)
                .baselineOnMigrate(false).validateOnMigrate(true).load();
    }

    private ApplicationContextRunner startup(TestSchema schema) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
                .withBean(DataSource.class, () -> schema.source).withUserConfiguration(DatabaseMigrationConfiguration.class)
                .withPropertyValues("smartcafe.database.policy=verified-20261113", "spring.flyway.enabled=true",
                        "spring.flyway.locations=classpath:db/smart-cafe", "spring.flyway.baseline-on-migrate=false",
                        "spring.flyway.validate-on-migrate=true");
    }

    private void sentinel(TestSchema schema) {
        schema.update("""
                INSERT INTO users(username,password_hash,full_name,email,role,status)
                VALUES('migration_fixture','synthetic-unused','Synthetic','migration@example.test','ADMIN','ACTIVE')
                """);
    }

    @Test void explicitProvisionRunsOneNativeMigrationAndStartupValidatesWithZeroRepeat() throws Exception {
        try (var schema = new TestSchema(null)) {
            startup(schema).run(context -> assertThat(context).hasFailed());
            try (var connection = schema.source.getConnection()) { assertThat(VerifiedSchemaContract.objects(connection)).isEmpty(); }
            var flyway = nativeFlyway(schema);
            assertThat(DatabaseRehearsal.execute(flyway, "provision")).isEqualTo(1);
            assertThat(schema.jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE type='SQL'", Integer.class)).isEqualTo(-22922275);
            startup(schema).run(context -> assertThat(context).hasNotFailed());
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (var connection = schema.source.getConnection()) {
                assertThat(new VerifiedSchemaContract(VerifiedSchemaContract.RESOURCE).inspect(connection, true).compatible()).isTrue();
                assertThat(VerifiedSchemaContract.fingerprints(connection)).hasSize(24);
            }
        }
    }

    @Test void explicitBaselinePreservesExistingRowsAndFullDdlWhileStartupNeverAutoBaselines() throws Exception {
        try (var schema = new TestSchema(SQL)) {
            sentinel(schema);
            var rows = schema.jdbc.queryForList("SELECT * FROM users");
            java.util.Map<String,String> ddl;
            try (var connection = schema.source.getConnection()) { ddl = VerifiedSchemaContract.fingerprints(connection); }
            startup(schema).run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("UNTRACKED_SCHEMA"));
            assertThat(schema.jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history'", Integer.class)).isZero();
            assertThat(DatabaseRehearsal.execute(nativeFlyway(schema), "baseline")).isZero();
            startup(schema).run(context -> assertThat(context).hasNotFailed());
            assertThat(nativeFlyway(schema).migrate().migrationsExecuted).isZero();
            assertThat(schema.jdbc.queryForList("SELECT * FROM users")).isEqualTo(rows);
            try (var connection = schema.source.getConnection()) { assertThat(VerifiedSchemaContract.fingerprints(connection)).isEqualTo(ddl); }
            assertThat(schema.jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE type='BASELINE' AND version='20261113.1'", Integer.class)).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "DROP TABLE news_images",
        "DROP VIEW v_daily_revenue",
        "ALTER VIEW v_daily_revenue AS SELECT 1 AS business_date,0 AS bill_count,0 AS revenue_amount",
        "ALTER TABLE staff_profiles DROP COLUMN address",
        "ALTER TABLE news_posts ALTER COLUMN audience_staff SET DEFAULT 1",
        "ALTER TABLE news_posts DROP CHECK ck_news_audience_flags",
        "ALTER TABLE staff_profiles DROP FOREIGN KEY fk_staff_profiles_user",
        "ALTER TABLE feedback_images ADD KEY keep_fk_feedback(feedback_id), DROP PRIMARY KEY",
        "ALTER TABLE users DROP INDEX ix_users_name",
        "ALTER TABLE session_clients DROP FOREIGN KEY fk_session_clients_customer; ALTER TABLE session_clients MODIFY customer_user_id bigint NOT NULL COMMENT 'NULL: khách vãng lai'; ALTER TABLE session_clients ADD CONSTRAINT fk_session_clients_customer FOREIGN KEY (customer_user_id) REFERENCES users(id)",
        "CREATE TABLE unexpected_object(id int PRIMARY KEY)",
        "UPDATE flyway_schema_history SET version='20261113.2'",
        "UPDATE flyway_schema_history SET description='wrong marker'",
        "UPDATE flyway_schema_history SET script='unknown script'",
        "UPDATE flyway_schema_history SET type='SQL',checksum=123",
        "UPDATE flyway_schema_history SET success=0",
        "DELETE FROM flyway_schema_history"
    })
    void rejectsHistoryAndDdlDriftWithoutChangingRowsOrHistory(String mutation) throws Exception {
        try (var schema = new TestSchema(SQL)) {
            sentinel(schema);
            DatabaseRehearsal.execute(nativeFlyway(schema), "baseline");
            for (String sql : mutation.split(";")) schema.update(sql.strip());
            var users = schema.jdbc.queryForList("SELECT * FROM users");
            var history = schema.jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
            startup(schema).run(context -> assertThat(context).hasFailed());
            assertThat(schema.jdbc.queryForList("SELECT * FROM users")).isEqualTo(users);
            assertThat(schema.jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(history);
        }
    }

    @Test void rejectsAppliedSqlChecksumDrift() throws Exception {
        try (var schema = new TestSchema(null)) {
            var flyway = nativeFlyway(schema);
            DatabaseRehearsal.execute(flyway, "provision");
            schema.update("UPDATE flyway_schema_history SET checksum=checksum+1");
            var history = schema.jdbc.queryForList("SELECT * FROM flyway_schema_history");
            startup(schema).run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("MIGRATION_LINEAGE_MISMATCH"));
            assertThat(schema.jdbc.queryForList("SELECT * FROM flyway_schema_history")).isEqualTo(history);
        }
    }

    @Test void rejectsBothForeignLineagesAndNeverCreatesAnotherHistory() throws Exception {
        for (String fixture : new String[]{SQL, "db/current-shared-2026-11-13.sql"}) {
            try (var schema = new TestSchema(fixture)) {
                if (fixture.equals(SQL)) schema.matchingAuthHistory(); else schema.sharedHistory();
                var history = schema.jdbc.queryForList("SELECT * FROM flyway_schema_history");
                startup(schema).run(context -> assertThat(context).hasFailed());
                assertThat(schema.jdbc.queryForList("SELECT * FROM flyway_schema_history")).isEqualTo(history);
                assertThatThrownBy(() -> DatabaseRehearsal.execute(nativeFlyway(schema), "baseline")).isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Test void refusesNonUtcAndProvisionOfPopulatedOrTrackedSchemas() throws Exception {
        try (var schema = new TestSchema(SQL)) {
            var users = schema.jdbc.queryForList("SELECT * FROM users");
            assertThatThrownBy(() -> DatabaseRehearsal.execute(nativeFlyway(schema), "provision")).hasMessageContaining("empty schema");
            try (var connection = schema.source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("SET SESSION time_zone='+07:00'");
                assertThat(new VerifiedSchemaContract(VerifiedSchemaContract.RESOURCE).inspect(connection, false).blockers())
                        .anyMatch(issue -> issue.code().equals("UTC_SESSION_REQUIRED"));
            }
            DatabaseRehearsal.execute(nativeFlyway(schema), "baseline");
            assertThatThrownBy(() -> DatabaseRehearsal.execute(nativeFlyway(schema), "provision")).hasMessageContaining("empty schema");
            assertThat(schema.jdbc.queryForList("SELECT * FROM users")).isEqualTo(users);
        }
    }

    @Test void permitsReviewedFutureDdlBeforeAndAfterNativeMigration() throws Exception {
        try (var schema = new TestSchema(null)) {
            DatabaseRehearsal.execute(nativeFlyway(schema), "provision");
            var future = Flyway.configure().dataSource(schema.source).locations("classpath:db/smart-cafe", "classpath:db/test-future")
                    .baselineOnMigrate(false).validateOnMigrate(true).load();
            var contract = new VerifiedSchemaContract("/db/contracts/test-future.properties");
            // An unresolved inventory is blocked before pending SQL can execute.
            assertThatThrownBy(() -> new VerifiedSchemaContract(VerifiedSchemaContract.RESOURCE).verifyResolved(future))
                    .hasMessageContaining("unreviewed resolved migration");
            DatabaseMigrationConfiguration.migrate(future, "verified-20261113", contract);
            assertThat(schema.jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='news_posts' AND column_name='integration_probe'", Integer.class)).isEqualTo(1);
            assertThat(schema.jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class)).isEqualTo(2);
            DatabaseMigrationConfiguration.migrate(future, "verified-20261113", contract);
            assertThat(future.migrate().migrationsExecuted).isZero();
        }
    }

    @Test void refusesCatalogOverrideBeforeAnyHistoryWrite() {
        try (var schema = new TestSchema(null)) {
            var flyway = Flyway.configure().configuration(nativeFlyway(schema).getConfiguration())
                    .defaultSchema("unselected_test").load();
            assertThatThrownBy(() -> DatabaseRehearsal.execute(flyway, "provision")).hasMessageContaining("selected JDBC catalog");
            assertThat(schema.jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class)).isZero();
        }
    }
}

package vn.codegym.smartcafe.database;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** Reviewed migration inventory and complete MySQL SHOW CREATE fingerprints, versioned together. */
final class VerifiedSchemaContract {
    static final String RESOURCE = "/db/contracts/smart-cafe.properties";
    static final String BASELINE_DESCRIPTION = "Smart Cafe verified schema 2026-11-13";
    final Properties manifest;
    final List<String> versions;

    VerifiedSchemaContract(String resource) throws Exception {
        manifest = properties(resource);
        versions = List.copyOf(Arrays.asList(manifest.getProperty("versions").split(",")));
        if (versions.isEmpty() || !"20261113.1".equals(versions.getFirst())) throw new IllegalStateException("Invalid contract base");
        for (String version : versions) {
            String path = manifest.getProperty(version + ".resource");
            try (var stream = VerifiedSchemaContract.class.getResourceAsStream(path)) {
                if (stream == null || !sha256(stream.readAllBytes()).equals(manifest.getProperty(version + ".sha256"))
                        || DatabasePreflight.checksum(path) != Integer.parseInt(manifest.getProperty(version + ".checksum"))) {
                    throw new IllegalStateException("Bundled migration differs from reviewed manifest: " + version);
                }
            }
        }
    }

    void verifyResolved(org.flywaydb.core.Flyway flyway) {
        for (var migration : flyway.info().all()) {
            if (!"SQL".equals(migration.getType().name())) continue;
            String version = versions.stream().filter(value -> org.flywaydb.core.api.MigrationVersion.fromVersion(value)
                    .equals(migration.getVersion())).findFirst().orElse("");
            if (!versions.contains(version) || !manifest.getProperty(version + ".script").equals(migration.getScript())
                    || !java.util.Objects.equals(Integer.valueOf(manifest.getProperty(version + ".checksum")), migration.getChecksum())) {
                throw new IllegalStateException("DATABASE_PREFLIGHT_BLOCKED: unreviewed resolved migration");
            }
        }
    }

    DatabasePreflight.Report inspect(Connection connection, boolean requireHistory) throws Exception {
        var issues = new ArrayList<DatabasePreflight.Issue>();
        DatabasePreflight.inspectTimezone(connection, issues);
        String version = versions.getFirst();
        boolean history = objects(connection).containsKey("flyway_schema_history");
        if (requireHistory && !history) issue(issues, "UNTRACKED_SCHEMA", "Manual verification and baseline required");
        if (history) version = inspectHistory(connection, issues);
        compareSchema(connection, version, issues);
        return new DatabasePreflight.Report(List.copyOf(issues), List.of());
    }

    private String inspectHistory(Connection connection, List<DatabasePreflight.Issue> issues) throws Exception {
        int next = 0;
        String current = versions.getFirst();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT version,description,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank")) {
            while (rows.next()) {
                String rawVersion = rows.getString("version"), type = rows.getString("type");
                String version = next < versions.size() ? versions.get(next) : "";
                boolean known = !version.isEmpty() && rawVersion != null
                        && org.flywaydb.core.api.MigrationVersion.fromVersion(version).equals(org.flywaydb.core.api.MigrationVersion.fromVersion(rawVersion));
                boolean baseline = next == 0 && "BASELINE".equals(type)
                        && version.equals(rawVersion)
                        && BASELINE_DESCRIPTION.equals(rows.getString("description"))
                        && BASELINE_DESCRIPTION.equals(rows.getString("script")) && rows.getObject("checksum") == null;
                boolean sql = known && "SQL".equals(type)
                        && manifest.getProperty(version + ".script").equals(rows.getString("script"))
                        && rows.getObject("checksum") != null
                        && Integer.parseInt(manifest.getProperty(version + ".checksum")) == rows.getInt("checksum");
                if (!known || (!baseline && !sql) || !rows.getBoolean("success")) {
                    issue(issues, "MIGRATION_LINEAGE_MISMATCH", "Unrecognized migration at position " + (next + 1));
                } else current = version;
                next++;
            }
        }
        if (next == 0) issue(issues, "EMPTY_MIGRATION_HISTORY", "No applied migrations");
        return current;
    }

    private void compareSchema(Connection connection, String version, List<DatabasePreflight.Issue> issues) throws Exception {
        var expected = properties(manifest.getProperty(version + ".schema"));
        var actual = fingerprints(connection);
        for (String name : expected.stringPropertyNames()) {
            if (!expected.getProperty(name).equals(actual.remove(name))) issue(issues, "SCHEMA_DRIFT", name);
        }
        actual.keySet().forEach(name -> issue(issues, "UNEXPECTED_SCHEMA_OBJECT", name));
        try (var query = connection.prepareStatement("""
                SELECT routine_name FROM information_schema.routines WHERE routine_schema=?
                UNION ALL SELECT trigger_name FROM information_schema.triggers WHERE trigger_schema=?
                UNION ALL SELECT event_name FROM information_schema.events WHERE event_schema=?
                """)) {
            for (int i = 1; i <= 3; i++) query.setString(i, connection.getCatalog());
            try (var rows = query.executeQuery()) {
                while (rows.next()) issue(issues, "UNEXPECTED_SCHEMA_OBJECT", rows.getString(1));
            }
        }
    }

    static Map<String, String> objects(Connection connection) throws Exception {
        var result = new TreeMap<String, String>();
        try (var query = connection.prepareStatement("SELECT table_name,table_type FROM information_schema.tables WHERE table_schema=?")) {
            query.setString(1, connection.getCatalog());
            try (var rows = query.executeQuery()) { while (rows.next()) result.put(rows.getString(1), rows.getString(2)); }
        }
        return result;
    }

    static Map<String, String> fingerprints(Connection connection) throws Exception {
        var result = new TreeMap<String, String>();
        for (var object : objects(connection).entrySet()) {
            if ("flyway_schema_history".equals(object.getKey())) continue;
            String identifier = "`" + object.getKey().replace("`", "``") + "`";
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SHOW CREATE " + ("VIEW".equals(object.getValue()) ? "VIEW " : "TABLE ") + identifier)) {
                rows.next();
                result.put(object.getKey(), sha256(canonical(rows.getString(2), connection.getCatalog()).getBytes(StandardCharsets.UTF_8)));
            }
        }
        return result;
    }

    // Ignore only allocation counters, environment-specific definers and the selected catalog qualifier.
    static String canonical(String ddl, String catalog) {
        return ddl.replace("\r\n", "\n").replaceAll(" AUTO_INCREMENT=[0-9]+(?= DEFAULT CHARSET=)", "")
                .replaceFirst("(?<=CREATE ALGORITHM=UNDEFINED) DEFINER=`(?:``|[^`])*`@`(?:``|[^`])*`", "")
                .replace("`" + catalog.replace("`", "``") + "`.", "");
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Properties properties(String path) throws Exception {
        try (var stream = VerifiedSchemaContract.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing schema contract " + path);
            var result = new Properties();
            result.load(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
            return result;
        }
    }

    private static void issue(List<DatabasePreflight.Issue> issues, String code, String detail) {
        issues.add(new DatabasePreflight.Issue(code, detail));
    }
}

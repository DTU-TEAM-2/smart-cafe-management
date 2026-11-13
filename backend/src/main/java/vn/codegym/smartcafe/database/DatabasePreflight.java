package vn.codegym.smartcafe.database;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/** Read-only checks of the Auth/Home SQL contract, not approval of a deployment schema. */
public final class DatabasePreflight {
    private DatabasePreflight() { }

    private static final List<String> MODEL_TABLES = List.of("users", "staff_profiles", "auth_otp_challenges", "auth_otp_limits",
            "media_assets", "menu_categories", "menu_items", "cafe_tables", "table_sessions", "session_clients",
            "order_batches", "order_items", "service_requests", "bills", "bill_items", "payments", "feedback", "feedback_images", "news_posts", "news_images");

    private static final Map<String, String> REQUIRED = Map.of(
            "users", "id username password_hash full_name email phone role status avatar_media_id password_changed_at must_change_password auth_version updated_at",
            "staff_profiles", "user_id address birthday gender salary",
            "media_assets", "id secure_url deleted_at",
            "auth_otp_challenges", "id user_id email purpose code_hash attempts sends expires_at next_send_at used_at delivery_status created_at last_error_code sent_at",
            "auth_otp_limits", "email_key purpose next_send_at window_start request_count");

    public record Issue(String code, String detail) { }
    public record Report(List<Issue> blockers, List<Issue> observations) {
        public boolean compatible() { return blockers.isEmpty(); }
        public void requireCompatible() {
            if (!compatible()) throw new IllegalStateException("DATABASE_PREFLIGHT_BLOCKED: " + blockers);
        }
    }

    public static Report inspect(Connection connection) throws Exception {
        String schema = connection.getCatalog();
        if (schema == null || schema.isBlank()) throw new IllegalStateException("Select an explicit database first");
        List<Issue> blockers = new ArrayList<>();
        List<Issue> observations = new ArrayList<>();
        var metadata = connection.getMetaData();
        var present = new java.util.HashSet<String>();
        try (var tables = metadata.getTables(schema, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) if (schema.equals(tables.getString("TABLE_CAT"))) present.add(tables.getString("TABLE_NAME"));
        }
        for (String table : MODEL_TABLES) if (!present.contains(table) && !REQUIRED.containsKey(table)) blockers.add(new Issue("MISSING_MODEL_TABLE", table));
        for (var table : REQUIRED.entrySet()) {
            Map<String, String> columns = new LinkedHashMap<>();
            try (var rows = metadata.getColumns(schema, null, table.getKey(), "%")) {
                while (rows.next()) if (schema.equals(rows.getString("TABLE_CAT")) && table.getKey().equals(rows.getString("TABLE_NAME"))) {
                    columns.put(rows.getString("COLUMN_NAME"), rows.getString("TYPE_NAME"));
                }
            }
            if (columns.isEmpty()) {
                blockers.add(new Issue("MISSING_TABLE", table.getKey()));
                continue;
            }
            for (String column : table.getValue().split(" ")) {
                if (!columns.containsKey(column)) blockers.add(new Issue("MISSING_COLUMN", table.getKey() + "." + column));
            }
            var keys = new java.util.TreeMap<Integer, String>();
            try (var rows = metadata.getPrimaryKeys(schema, null, table.getKey())) {
                while (rows.next()) keys.put(rows.getInt("KEY_SEQ"), rows.getString("COLUMN_NAME"));
            }
            List<String> expectedKeys = switch (table.getKey()) {
                case "staff_profiles" -> List.of("user_id");
                case "auth_otp_limits" -> List.of("email_key", "purpose");
                default -> List.of("id");
            };
            if (!new ArrayList<>(keys.values()).equals(expectedKeys)) blockers.add(new Issue("PRIMARY_KEY_MISMATCH", table.getKey()));
            for (String column : List.of("expires_at", "next_send_at", "window_start", "created_at", "sent_at")) {
                if (columns.containsKey(column)) observations.add(new Issue("TEMPORAL_TYPE", table.getKey() + "." + column + "=" + columns.get(column)));
            }
        }
        try (var query = connection.prepareStatement("""
                SELECT column_name, collation_name FROM information_schema.columns
                WHERE table_schema=? AND table_name='auth_otp_challenges' AND column_name IN ('id','code_hash')
                """)) {
            query.setString(1, schema);
            try (var rows = query.executeQuery()) {
                while (rows.next()) observations.add(new Issue("OTP_COLLATION", rows.getString(1) + "=" + rows.getString(2)));
            }
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT @@session.time_zone")) {
            rows.next();
            if (!List.of("+00:00", "UTC", "Etc/UTC").contains(rows.getString(1))) blockers.add(new Issue("UTC_SESSION_REQUIRED", "Set the connection session time_zone to UTC"));
        }
        boolean history = present.contains("flyway_schema_history");
        if (!history) blockers.add(new Issue("UNTRACKED_SCHEMA", "No migration history: automatic baseline is not reconciliation"));
        else inspectHistory(connection, blockers);
        return new Report(List.copyOf(blockers), List.copyOf(observations));
    }

    private static void inspectHistory(Connection connection, List<Issue> blockers) throws Exception {
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (String version : List.of("001", "002")) {
            String file = "V" + version + "__create_users_and_auth_tables.sql";
            expected.put(file, checksum("/db/migration/" + file));
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT version,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank")) {
            while (rows.next()) {
                String script = rows.getString("script");
                if (!rows.getBoolean("success")) blockers.add(new Issue("FAILED_MIGRATION", rows.getString("version")));
                Integer checksum = expected.get(script);
                String expectedVersion = script != null && script.startsWith("V001__") ? "001" : "002";
                if (!"SQL".equals(rows.getString("type")) || checksum == null ||
                        !expectedVersion.equals(rows.getString("version")) || rows.getObject("checksum") == null || checksum != rows.getInt("checksum")) {
                    blockers.add(new Issue("MIGRATION_LINEAGE_MISMATCH", "version=" + rows.getString("version") + ", script=" + script));
                }
            }
        }
    }

    // Flyway's checksum is CRC32 of UTF-8 lines, excluding BOM and line terminators.
    public static int checksum(String resource) throws Exception {
        CRC32 crc = new CRC32();
        var stream = DatabasePreflight.class.getResourceAsStream(resource);
        if (stream == null) throw new IllegalStateException("Missing bundled migration " + resource);
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first && line.startsWith("\uFEFF")) line = line.substring(1);
                crc.update(line.getBytes(StandardCharsets.UTF_8));
                first = false;
            }
        }
        return (int) crc.getValue();
    }

    /** CLI: private DB_URL/DB_USERNAME/DB_PASSWORD in process env; never starts Spring/Flyway. */
    public static void main(String[] args) {
        try {
            String url = System.getenv("DB_URL");
            if (url == null || !url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException("Explicit MySQL DB_URL required");
            try (var connection = DriverManager.getConnection(url, System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
                try (var statement = connection.createStatement()) { statement.execute("SET SESSION time_zone = '+00:00'"); }
                connection.setReadOnly(true);
                connection.setAutoCommit(false);
                var report = inspect(connection);
                report.observations().forEach(item -> System.out.println("OBSERVATION " + item.code() + " " + item.detail()));
                report.blockers().forEach(item -> System.out.println("BLOCKED " + item.code() + " " + item.detail()));
                connection.rollback();
                System.out.println(report.compatible() ? "PREFLIGHT_PASS: SQL/migration checks only, not deployment approval" : "PREFLIGHT_BLOCKED");
                if (!report.compatible()) System.exit(2);
            }
        } catch (Exception ex) {
            // JDBC exception messages may include connection details: print only a safe category.
            System.err.println("PREFLIGHT_ERROR " + (ex instanceof SQLException sql ? "SQLSTATE=" + sql.getSQLState() : ex.getClass().getSimpleName()));
            System.exit(1);
        }
    }
}

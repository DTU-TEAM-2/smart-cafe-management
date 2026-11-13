package vn.codegym.smartcafe.database;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.net.URI;
import java.util.UUID;

/** Owns one fresh *_test schema; never loads business rows from any source DB. */
final class TestSchema implements AutoCloseable {
    final String name;
    final DriverManagerDataSource source;
    final JdbcTemplate jdbc;
    private final JdbcTemplate server;

    TestSchema(String fixture) {
        String url = System.getenv("TEST_DB_URL");
        if (url == null || !url.matches("^jdbc:mysql://[^/]+/[^?;/]+_test(?:\\?.*)?$")) throw new IllegalStateException("Explicit isolated TEST_DB_URL required");
        URI uri = URI.create(url.substring(5));
        String prefix = "jdbc:mysql://" + uri.getRawAuthority() + "/";
        String query = "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
        String user = System.getenv("TEST_DB_USERNAME"), password = System.getenv("TEST_DB_PASSWORD");
        server = new JdbcTemplate(new DriverManagerDataSource(prefix + uri.getPath().substring(1) + query, user, password));
        name = "pr7_reconcile_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "_test";
        server.execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        source = new DriverManagerDataSource(prefix + name + query, user, password);
        jdbc = new JdbcTemplate(source);
        if (fixture == null) { assertIsolated(); return; }
        try {
            String sql = new ClassPathResource(fixture).getContentAsString(java.nio.charset.StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
            var populator = new ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            populator.setSqlScriptEncoding("UTF-8");
            populator.execute(source);
            assertIsolated();
        } catch (java.io.IOException ex) { close(); throw new IllegalStateException("Cannot read DDL fixture", ex); }
        catch (RuntimeException ex) { close(); throw ex; }
    }

    void assertIsolated() {
        if (!name.equals(jdbc.queryForObject("SELECT DATABASE()", String.class)) || !name.endsWith("_test")) throw new IllegalStateException("Wrong owned schema");
    }

    int update(String sql, Object... args) {
        assertIsolated();
        return jdbc.update(sql, args);
    }

    void sharedHistory() {
        assertIsolated();
        try {
            var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(new ClassPathResource("db/current-shared-history.json").getInputStream());
            for (var row : json.get("history")) update("""
                    INSERT INTO flyway_schema_history(installed_rank,version,description,type,script,checksum,installed_by,execution_time,success)
                    VALUES(?,?,?,?,?,?,'synthetic-test',0,?)
                    """, row.get("rank").asInt(), row.get("version").asText(), row.get("description").asText(), row.get("type").asText(), row.get("script").asText(), row.get("checksum").asInt(), row.get("success").asBoolean());
            if (jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class) != 8) {
                throw new IllegalStateException("Snapshot must preserve all8 successful shared-history rows");
            }
        } catch (java.io.IOException ex) { throw new IllegalStateException("Cannot read metadata fixture", ex); }
    }

    void matchingAuthHistory() throws Exception {
        assertIsolated();
        String snapshot = new ClassPathResource("db/current-shared-2026-11-13.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        int start = snapshot.indexOf("CREATE TABLE `flyway_schema_history`");
        jdbc.execute(snapshot.substring(start, snapshot.indexOf(';', start)));
        for (int i = 1; i <= 2; i++) {
            String version = "00" + i;
            String script = "V" + version + "__create_users_and_auth_tables.sql";
            update("""
                    INSERT INTO flyway_schema_history(installed_rank,version,description,type,script,checksum,installed_by,execution_time,success)
                    VALUES(?,?,'create users and auth tables','SQL',?,?,'synthetic-test',0,1)
                    """, i, version, script, DatabasePreflight.checksum("/db/migration/" + script));
        }
    }

    @Override public void close() {
        if (!name.matches("pr7_reconcile_[a-f0-9]{12}_test")) throw new IllegalStateException("Refuse cleanup outside owned test schema");
        server.execute("DROP DATABASE `" + name + "`");
    }
}

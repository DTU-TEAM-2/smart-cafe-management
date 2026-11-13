package vn.codegym.smartcafe.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class DatabaseTemporalCompatibilityTest {
    private void insert(TestSchema schema, Instant expiry) {
        schema.assertIsolated();
        schema.update("""
                INSERT INTO auth_otp_challenges(id,email,purpose,code_hash,created_at,expires_at,next_send_at)
                VALUES(REPEAT('a',64),'time_fixture@example.test','REGISTER',REPEAT('b',64),?,?,?)
                """, Timestamp.from(expiry.minusSeconds(600)), Timestamp.from(expiry), Timestamp.from(expiry.minusSeconds(540)));
    }

    @Test void utcPreservesMicrosecondsOnBothCurrentAndHistoricalTemporalTypes() throws Exception {
        Instant expiry = Instant.parse("2030-04-05T06:07:08.123456Z");
        for (String fixture : new String[]{"db/current-shared-2026-11-13.sql", "schema.sql"}) {
            try (var schema = new TestSchema(fixture); Connection connection = schema.source.getConnection()) {
                insert(schema, expiry);
                try (var statement = connection.createStatement(); var row = statement.executeQuery("SELECT expires_at FROM auth_otp_challenges")) {
                    row.next();
                    assertThat(row.getTimestamp(1).toInstant()).isEqualTo(expiry);
                }
                try (var statement = connection.createStatement()) {
                    statement.execute("SET SESSION time_zone = '+07:00'");
                    try (var row = statement.executeQuery("SELECT DATE_FORMAT(expires_at,'%Y-%m-%d %H:%i:%s.%f') FROM auth_otp_challenges")) {
                        row.next();
                        assertThat(row.getString(1)).isEqualTo(fixture.equals("schema.sql") ? "2030-04-05 06:07:08.123456" : "2030-04-05 13:07:08.123456");
                    }
                    assertThat(DatabasePreflight.inspect(connection).blockers()).anyMatch(i -> i.code().equals("UTC_SESSION_REQUIRED"));
                }
            }
        }
    }

    @Test void timestampRangeCannotBeDeclaredEquivalentToDatetimeBeyond2038() {
        Instant future = Instant.parse("2040-04-05T06:07:08Z");
        try (var current = new TestSchema("db/current-shared-2026-11-13.sql"); var historical = new TestSchema("schema.sql")) {
            assertThatThrownBy(() -> insert(current, future)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            insert(historical, future);
            assertThat(historical.jdbc.queryForObject("SELECT COUNT(*) FROM auth_otp_challenges", Integer.class)).isEqualTo(1);
        }
    }
}

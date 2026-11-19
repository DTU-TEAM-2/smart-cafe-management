package vn.codegym.smartcafe.customeractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerAccessTest {
    private static final String VALID_TOKEN = "A".repeat(43);

    @Test void acceptsCaseInsensitiveBearerSchemePerRfc9110() {
        var jdbc = mock(JdbcTemplate.class);
        var clock = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);
        var access = new CustomerAccess(jdbc, clock);

        when(jdbc.queryForList(anyString(), eq(1L), anyString()))
                .thenReturn(List.of(Map.of("id", 10L, "table_id", 20L)));
        when(jdbc.queryForObject(contains("FROM cafe_tables"), eq(String.class), eq(20L)))
                .thenReturn("ACTIVE");
        when(jdbc.queryForMap(contains("FROM table_sessions"), eq(1L)))
                .thenReturn(Map.of("status", "OPEN"));
        when(jdbc.queryForMap(contains("FROM session_clients"), eq(10L), eq(1L), anyString()))
                .thenReturn(Map.of("expires_at", java.sql.Timestamp.from(Instant.parse("2026-11-13T01:00:00Z"))));

        for (String scheme : List.of("Bearer ", "bearer ", "BEARER ", "bEaReR ")) {
            var client = access.lock(1L, scheme + VALID_TOKEN);
            assertThat(client.id()).isEqualTo(10L);
            assertThat(client.sessionId()).isEqualTo(1L);
            assertThat(client.sessionStatus()).isEqualTo("OPEN");
        }
    }

    @Test void rejectsInvalidSchemesOrMalformedHeadersWith401() {
        var jdbc = mock(JdbcTemplate.class);
        var clock = Clock.systemUTC();
        var access = new CustomerAccess(jdbc, clock);

        for (String badAuth : List.of(
                "Basic " + VALID_TOKEN,
                "Token " + VALID_TOKEN,
                "bearer",
                "Bearer",
                "bearer  ",
                "",
                "invalid-token-without-scheme")) {
            assertThatThrownBy(() -> access.lock(1L, badAuth))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        assertThatThrownBy(() -> access.lock(1L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> access.lock(0L, "Bearer " + VALID_TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(jdbc);
    }
}

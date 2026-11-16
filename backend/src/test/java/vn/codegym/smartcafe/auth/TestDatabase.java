package vn.codegym.smartcafe.auth;

import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

final class TestDatabase {
    private TestDatabase() { }

    static int update(JdbcTemplate jdbc, String sql, Object... args) {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isNotNull().endsWith("_test");
        return jdbc.update(sql, args);
    }
}

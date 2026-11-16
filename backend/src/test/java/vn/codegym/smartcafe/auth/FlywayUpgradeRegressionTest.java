package vn.codegym.smartcafe.auth;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for reviewer finding:
 * Schemas that already recorded version 001 in flyway_schema_history
 * must still receive auth tables via V002 without being skipped.
 */
@SpringBootTest
class FlywayUpgradeRegressionTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void verifyTestDatabaseIsolation() {
        String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertThat(dbName).isNotNull().endsWith("_test");
    }

    @AfterEach
    void cleanupFlywayHistoryFixture() {
        try {
            // Clean up test migration entries from custom table to avoid affecting other tests
            jdbcTemplate.update("DROP TABLE IF EXISTS flyway_regression_history");
        } catch (Exception ignored) {
        }
    }

    @Test
    void appliesV002WhenSchemaAlreadyHasV001RecordedInHistory() {
        String customHistoryTable = "flyway_regression_history";

        // 1. Simulate an existing database baselined at version 001 using a dedicated history table
        Flyway baselineFlyway = Flyway.configure()
                .dataSource(dataSource)
                .table(customHistoryTable)
                .baselineVersion("001")
                .baselineDescription("simulated_legacy_v001")
                .load();
        baselineFlyway.baseline();

        // Verify baseline recorded version 001 in the history table
        Integer baselineCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + customHistoryTable + " WHERE version = '001'",
                Integer.class
        );
        assertThat(baselineCount).isNotNull().isEqualTo(1);

        // 2. Run Flyway migration on this schema with our migration scripts
        Flyway migrationFlyway = Flyway.configure()
                .dataSource(dataSource)
                .table(customHistoryTable)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(false)
                .load();

        MigrateResult result = migrationFlyway.migrate();
        assertThat(result.success).isTrue();

        // 3. Verify V001 was skipped because version <= 001, and V002 was applied
        Integer v002Record = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + customHistoryTable + " WHERE version = '002' AND success = 1",
                Integer.class
        );
        assertThat(v002Record).isNotNull().isEqualTo(1);

        // 4. Verify the required auth tables exist and are functional
        Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(userCount).isNotNull();

        Integer otpCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_otp_challenges", Integer.class);
        assertThat(otpCount).isNotNull();

        Integer limitsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_otp_limits", Integer.class);
        assertThat(limitsCount).isNotNull();
    }
}

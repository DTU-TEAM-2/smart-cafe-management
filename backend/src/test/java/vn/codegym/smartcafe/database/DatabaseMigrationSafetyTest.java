package vn.codegym.smartcafe.database;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationSafetyTest {
    private ApplicationContextRunner startup(TestSchema schema) {
        var runner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
                .withBean(DataSource.class, () -> schema.source)
                .withPropertyValues("spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=true", "spring.flyway.baseline-version=0", "spring.flyway.validate-on-migrate=false");
        // Opt-in RED probe recreates94deb6d's native auto-config, before the new migration strategy.
        return Boolean.getBoolean("preflight.legacy-probe") ? runner : runner.withUserConfiguration(DatabaseMigrationConfiguration.class);
    }

    @Test void refusesAutomaticBaselineOfPopulatedUntrackedSchema() {
        try (var schema = new TestSchema("schema.sql")) {
            startup(schema).run(context -> assertThat(context).hasFailed());
            assertThat(schema.jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history'", Integer.class)).isZero();
        }
    }

    @Test void acceptsCompleteHistoricalFixtureWithMatchingAuthHistory() throws Exception {
        try (var schema = new TestSchema("schema.sql")) {
            schema.matchingAuthHistory();
            var before = schema.jdbc.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank");
            startup(schema).run(context -> assertThat(context).hasNotFailed());
            assertThat(schema.jdbc.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
        }
    }

    @Test void refusesChecksumDriftEvenWhenOldEnvironmentDisablesValidation() throws Exception {
        try (var schema = new TestSchema("schema.sql")) {
            schema.matchingAuthHistory();
            schema.assertIsolated();
            schema.update("UPDATE flyway_schema_history SET checksum=checksum+1 WHERE version='001'");
            startup(schema).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("MIGRATION_LINEAGE_MISMATCH");
            });
        }
    }

    @Test void permitsKnownPendingMigrationWithStrictValidationAndNoNewBaseline() throws Exception {
        try (var schema = new TestSchema("schema.sql")) {
            schema.matchingAuthHistory();
            schema.assertIsolated();
            schema.update("DELETE FROM flyway_schema_history WHERE version='002'");
            startup(schema).run(context -> assertThat(context).hasNotFailed());
            assertThat(schema.jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE type='SQL' AND success=1", Integer.class)).isEqualTo(2);
            assertThat(schema.jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE type='BASELINE'", Integer.class)).isZero();
        }
    }

    @Test void refusesForeignHistoryBeforeAnyAutomaticMigration() {
        try (var schema = new TestSchema("db/current-shared-2026-11-13.sql")) {
            schema.sharedHistory();
            var before = schema.jdbc.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank");
            startup(schema).run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasStackTraceContaining("DATABASE_PREFLIGHT_BLOCKED");
                    });
            assertThat(schema.jdbc.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
        }
    }
}

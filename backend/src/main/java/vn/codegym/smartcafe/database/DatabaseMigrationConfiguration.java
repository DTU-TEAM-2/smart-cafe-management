package vn.codegym.smartcafe.database;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

@Configuration(proxyBeanMethods = false)
public class DatabaseMigrationConfiguration {
    @Bean FlywayMigrationStrategy verifiedDatabaseMigration() {
        return flyway -> {
            if (!"flyway_schema_history".equals(flyway.getConfiguration().getTable())) {
                throw new IllegalStateException("DATABASE_PREFLIGHT_BLOCKED: alternate history table requires an approved lineage");
            }
            try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
                connection.setReadOnly(true);
                DatabasePreflight.inspect(connection).requireCompatible();
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException("DATABASE_PREFLIGHT_BLOCKED: cannot verify database metadata", ex);
            }
            // Native migrate validates applied history while allowing legitimate pending migrations.
            org.flywaydb.core.Flyway.configure().configuration(flyway.getConfiguration())
                    .validateOnMigrate(true).baselineOnMigrate(false).load().migrate();
        };
    }
}

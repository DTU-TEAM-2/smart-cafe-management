package vn.codegym.smartcafe.database;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

@Configuration(proxyBeanMethods = false)
public class DatabaseMigrationConfiguration {
    @Bean FlywayMigrationStrategy verifiedDatabaseMigration(
            @org.springframework.beans.factory.annotation.Value("${smartcafe.database.policy:auth-local}") String policy) {
        return flyway -> {
            try {
                if (!java.util.List.of("auth-local", "verified-20261113").contains(policy)) throw new IllegalArgumentException("Unknown database policy");
                migrate(flyway, policy, "verified-20261113".equals(policy)
                        ? new VerifiedSchemaContract(VerifiedSchemaContract.RESOURCE) : null);
            } catch (RuntimeException ex) { throw ex; }
            catch (Exception ex) { throw new IllegalStateException("DATABASE_PREFLIGHT_BLOCKED: cannot verify migration contract", ex); }
        };
    }

    static void migrate(org.flywaydb.core.Flyway flyway, String policy, VerifiedSchemaContract contract) throws Exception {
        if (!"flyway_schema_history".equals(flyway.getConfiguration().getTable())) {
            throw new IllegalStateException("DATABASE_PREFLIGHT_BLOCKED: alternate history table requires an approved lineage");
        }
        var expectedLocations = "auth-local".equals(policy) ? java.util.List.of("classpath:db/migration")
                : java.util.List.of(contract.manifest.getProperty("locations").split(","));
        var locations = java.util.Arrays.stream(flyway.getConfiguration().getLocations()).map(org.flywaydb.core.api.Location::getDescriptor).toList();
        if (!expectedLocations.equals(locations) || flyway.getConfiguration().isOutOfOrder()) {
            throw new IllegalStateException("DATABASE_PREFLIGHT_BLOCKED: migration location/order does not match policy");
        }
        try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
            requireSelectedCatalog(flyway, connection);
            connection.setReadOnly(true);
            if (contract == null) DatabasePreflight.inspect(connection, policy).requireCompatible();
            else contract.inspect(connection, true).requireCompatible();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("DATABASE_PREFLIGHT_BLOCKED: cannot verify database metadata", ex);
        }
        if (contract != null) contract.verifyResolved(flyway);
        // Native migrate validates applied history while allowing legitimate pending migrations.
        org.flywaydb.core.Flyway.configure().configuration(flyway.getConfiguration())
                .validateOnMigrate(true).baselineOnMigrate(false).load().migrate();
        if (contract != null) try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
            connection.setReadOnly(true);
            contract.inspect(connection, true).requireCompatible();
        }
    }

    static void requireSelectedCatalog(org.flywaydb.core.Flyway flyway, java.sql.Connection connection) throws Exception {
        String catalog = connection.getCatalog();
        var config = flyway.getConfiguration();
        if (catalog == null || catalog.isBlank() || (config.getDefaultSchema() != null && !catalog.equals(config.getDefaultSchema()))
                || java.util.Arrays.stream(config.getSchemas()).anyMatch(schema -> !catalog.equals(schema))) {
            throw new IllegalStateException("DATABASE_PREFLIGHT_BLOCKED: Flyway schema differs from selected JDBC catalog");
        }
    }
}

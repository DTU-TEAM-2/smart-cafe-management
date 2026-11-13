package vn.codegym.smartcafe.database;

import org.flywaydb.core.Flyway;

/** Separate native lifecycle commands, deliberately restricted to explicitly selected *_test catalogs. */
public final class DatabaseRehearsal {
    private DatabaseRehearsal() { }

    static int execute(Flyway flyway, String operation) throws Exception {
        var contract = new VerifiedSchemaContract(VerifiedSchemaContract.RESOURCE);
        var config = flyway.getConfiguration();
        if (!"flyway_schema_history".equals(config.getTable()) || config.getLocations().length != 1
                || !"classpath:db/smart-cafe".equals(config.getLocations()[0].getDescriptor())
                || config.isBaselineOnMigrate() || !config.isValidateOnMigrate() || config.isOutOfOrder()) {
            throw new IllegalStateException("Unsafe lifecycle configuration");
        }
        try (var connection = config.getDataSource().getConnection()) {
            DatabaseMigrationConfiguration.requireSelectedCatalog(flyway, connection);
            if (connection.getCatalog() == null || !connection.getCatalog().matches("[A-Za-z0-9_]+_test")) {
                throw new IllegalStateException("Explicit isolated *_test catalog required");
            }
            var timezone = new java.util.ArrayList<DatabasePreflight.Issue>();
            DatabasePreflight.inspectTimezone(connection, timezone);
            new DatabasePreflight.Report(timezone, java.util.List.of()).requireCompatible();
            if ("provision".equals(operation)) {
                if (!VerifiedSchemaContract.objects(connection).isEmpty()) throw new IllegalStateException("Provision requires an empty schema");
                try (var query = connection.prepareStatement("""
                        SELECT (SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema=?)
                             + (SELECT COUNT(*) FROM information_schema.events WHERE event_schema=?)
                        """)) {
                    query.setString(1, connection.getCatalog()); query.setString(2, connection.getCatalog());
                    try (var rows = query.executeQuery()) { rows.next(); if (rows.getInt(1) != 0) throw new IllegalStateException("Provision requires no stored objects"); }
                }
            } else if ("baseline".equals(operation)) {
                if (VerifiedSchemaContract.objects(connection).containsKey("flyway_schema_history")) throw new IllegalStateException("History already exists");
                contract.inspect(connection, false).requireCompatible();
                if (!"20261113.1".equals(config.getBaselineVersion().getVersion())
                        || !VerifiedSchemaContract.BASELINE_DESCRIPTION.equals(config.getBaselineDescription())) {
                    throw new IllegalStateException("Unrecognized baseline marker");
                }
            } else throw new IllegalArgumentException("Use provision or baseline");
        }
        contract.verifyResolved(flyway);
        if ("baseline".equals(operation)) flyway.baseline();
        int count = flyway.migrate().migrationsExecuted;
        flyway.validate();
        try (var connection = config.getDataSource().getConnection()) { contract.inspect(connection, true).requireCompatible(); }
        return count;
    }

    public static void main(String[] args) {
        try {
            String url = System.getenv("TEST_DB_URL");
            if (args.length != 1 || url == null || !url.matches("^jdbc:mysql://[^/]+/[A-Za-z0-9_]+_test(?:\\?.*)?$")) {
                throw new IllegalArgumentException("Explicit isolated TEST_DB_URL and operation required");
            }
            var flyway = Flyway.configure().dataSource(url, System.getenv("TEST_DB_USERNAME"), System.getenv("TEST_DB_PASSWORD"))
                    .initSql("SET SESSION time_zone='+00:00'").locations("classpath:db/smart-cafe")
                    .baselineVersion("20261113.1").baselineDescription(VerifiedSchemaContract.BASELINE_DESCRIPTION)
                    .baselineOnMigrate(false).validateOnMigrate(true).cleanDisabled(true).load();
            System.out.println("MIGRATIONS=" + execute(flyway, args[0]) + " VALIDATE=PASS");
            if (flyway.migrate().migrationsExecuted != 0) throw new IllegalStateException("Repeat migrate was not a no-op");
            System.out.println("REPEAT_MIGRATIONS=0");
        } catch (Exception ex) {
            System.err.println("REHEARSAL_BLOCKED " + ex.getClass().getSimpleName());
            System.exit(1);
        }
    }
}

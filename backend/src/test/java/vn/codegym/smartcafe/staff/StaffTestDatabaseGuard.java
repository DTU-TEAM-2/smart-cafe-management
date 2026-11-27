package vn.codegym.smartcafe.staff;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

final class StaffTestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final String TEST_CATALOG = "[A-Za-z0-9_]+_test";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String url = context.getEnvironment().getProperty("spring.datasource.url", "");
        if (!url.matches("jdbc:mysql://[^/?#]+/" + TEST_CATALOG + "(?:\\?.*)?")) {
            throw new IllegalStateException("Staff integration tests require a dedicated MySQL *_test URL");
        }
    }

    static void requireTestCatalog(JdbcTemplate jdbc) {
        String catalog = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (catalog == null || !catalog.matches(TEST_CATALOG)) {
            throw new IllegalStateException("Staff fixtures require an active MySQL *_test database");
        }
    }
}

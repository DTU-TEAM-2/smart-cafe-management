package vn.codegym.smartcafe.menu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in browser -> real Spring HTTP -> isolated MySQL. No mocked application APIs. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1")
@ContextConfiguration(initializers = MenuIntegrationTest.TestDatabaseGuard.class)
@Import(MenuBrowserIntegrationTest.ProviderConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MENU_BROWSER_TEST", matches = "true")
class MenuBrowserIntegrationTest {
    private static final boolean LIVE = "true".equals(System.getenv("MENU_BROWSER_LIVE"));

    @DynamicPropertySource
    static void imageSettings(DynamicPropertyRegistry properties) {
        properties.add("menu.images.cloud-name", () -> LIVE ? System.getenv("CLOUDINARY_CLOUD_NAME") : "menu-test");
        properties.add("menu.images.api-key", () -> LIVE ? System.getenv("CLOUDINARY_API_KEY") : "test-only-key");
        properties.add("menu.images.api-secret", () -> LIVE ? System.getenv("CLOUDINARY_API_SECRET") : "test-only-secret");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfiguration {
        @Bean(destroyMethod = "close") MenuProviderStub browserProvider() throws Exception { return new MenuProviderStub(); }
        @Bean @Primary @Qualifier("menuImageClient")
        RestClient browserImageClient(MenuProviderStub provider) {
            return LIVE ? RestClient.builder().build() : provider.client();
        }
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired MenuProviderStub provider;

    @Test
    void browserPersistsCrudAndPublishesUploadedImagesToCustomerMenu() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).matches("[A-Za-z0-9_]+_test");
        if (LIVE) {
            for (String name : new String[]{"CLOUDINARY_CLOUD_NAME", "CLOUDINARY_API_KEY", "CLOUDINARY_API_SECRET"}) {
                assertThat(System.getenv(name)).as("Required live provider setting %s", name).isNotBlank();
            }
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String username = "browser-" + suffix;
        String category = "BG" + suffix;
        String item = "BI" + suffix;
        String password = "MenuBrowser!2026";
        jdbc.update("INSERT INTO users(username,password_hash,full_name,email,role,status) VALUES (?, ?, 'Browser test', ?, 'ADMIN', 'ACTIVE')",
                username, passwords.encode(password), username + "@example.invalid");
        Path log = Path.of("target", "menu-browser.log").toAbsolutePath();
        Files.createDirectories(log.getParent());
        try {
            var builder = new ProcessBuilder("node", "scripts/menu-browser-smoke.mjs")
                    .directory(Path.of("..", "frontend").toFile()).redirectErrorStream(true)
                    .redirectOutput(log.toFile());
            var environment = builder.environment();
            environment.put("MENU_BACKEND_URL", "http://127.0.0.1:" + port);
            environment.put("MENU_TEST_USERNAME", username);
            environment.put("MENU_TEST_PASSWORD", password);
            environment.put("MENU_TEST_CATEGORY", category);
            environment.put("MENU_TEST_ITEM", item);
            environment.put("MENU_BROWSER_LIVE", Boolean.toString(LIVE));
            Process browser = builder.start();
            if (!browser.waitFor(120, TimeUnit.SECONDS)) {
                browser.descendants().forEach(ProcessHandle::destroyForcibly);
                browser.destroyForcibly();
                throw new AssertionError("Browser timed out. See " + log);
            }
            assertThat(browser.exitValue()).withFailMessage("Browser failed; see %s%n%s", log, Files.readString(log)).isZero();
            assertThat(jdbc.queryForObject("SELECT active FROM menu_categories WHERE code=?", Boolean.class, category)).isFalse();
            assertThat(jdbc.queryForObject("SELECT active FROM menu_items WHERE code=?", Boolean.class, item)).isFalse();
            assertThat(jdbc.queryForObject("SELECT CAST(price AS CHAR) FROM menu_items WHERE code=?", String.class, item)).isEqualTo("48000");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_items i JOIN media_assets m ON m.id=i.image_media_id WHERE i.code=? AND m.provider='CLOUDINARY' AND m.deleted_at IS NULL", Integer.class, item)).isEqualTo(1);
            if (!LIVE) {
                assertThat(provider.calls()).isEqualTo(3);
                // Three uploads were registered, but only the acknowledged second image is referenced.
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets WHERE provider_asset_id LIKE ?", Integer.class, provider.assetPrefix() + "%")).isEqualTo(3);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets m WHERE m.provider_asset_id LIKE ? AND EXISTS (SELECT 1 FROM menu_items i WHERE i.image_media_id=m.id)", Integer.class, provider.assetPrefix() + "%")).isEqualTo(1);
                assertThat(provider.invalidRequest()).isNull();
            }
        } finally {
            jdbc.update("DELETE FROM menu_items WHERE code=?", item);
            jdbc.update("DELETE FROM menu_categories WHERE code=?", category);
            jdbc.update("DELETE FROM users WHERE username=?", username);
            if (!LIVE) jdbc.update("DELETE FROM media_assets WHERE provider_asset_id LIKE ?", provider.assetPrefix() + "%");
            // Live assets are intentionally retained: deleting provider assets needs an approved cleanup policy.
        }
    }
}

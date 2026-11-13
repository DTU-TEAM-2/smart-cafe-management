package vn.codegym.smartcafe.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.PropertiesPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class AuthConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @Import(TokenService.class)
    static class TokenConfig { }

    private ApplicationContextRunner config(String profile) {
        return new ApplicationContextRunner().withInitializer(ctx -> {
            try {
                for (String file : profile.equals("default") ? new String[]{"application.properties"} :
                        new String[]{"application.properties", "application-" + profile + ".properties"}) {
                    Properties props = new Properties();
                    try (var reader = Files.newBufferedReader(Path.of("src/main/resources", file))) { props.load(reader); }
                    ctx.getEnvironment().getPropertySources().addFirst(new PropertiesPropertySource(file, props));
                }
            } catch (Exception ex) { throw new IllegalStateException("Cannot load actual runtime configuration", ex); }
        }).withUserConfiguration(TokenConfig.class);
    }

    @ParameterizedTest @ValueSource(strings = {"default", "dev", "prod"})
    void realPropertiesRespectOverridesAndUseTheSameStableKey(String profile) {
        String fixtureKey = "synthetic-config-key-at-least-32-bytes";
        config(profile).withPropertyValues("AUTH_TOKEN_SECRET=" + fixtureKey,
                "OTP_VALIDITY_SECONDS=345", "AUTH_TOKEN_VALIDITY_SECONDS=456",
                "PENDING_REGISTRATION_VALIDITY_SECONDS=567", "DB_USERNAME=fixture_app",
                "SMTP_HOST=127.0.0.1", "SMTP_PORT=1025", "SMTP_AUTH=false", "SMTP_STARTTLS=false", "SMTP_SSL=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    var env = ctx.getEnvironment();
                    assertThat(env.getProperty("smartcafe.auth.otp-validity-seconds")).isEqualTo("345");
                    assertThat(env.getProperty("smartcafe.auth.token-validity-seconds")).isEqualTo("456");
                    assertThat(env.getProperty("smartcafe.auth.pending-registration-validity-seconds")).isEqualTo("567");
                    assertThat(env.getProperty("spring.datasource.username")).isEqualTo("fixture_app");
                    assertThat(env.getProperty("spring.mail.host")).isEqualTo("127.0.0.1");
                    assertThat(env.getProperty("spring.mail.properties.mail.smtp.starttls.required")).isEqualTo("false");
                    TokenService restarted = new TokenService(fixtureKey, 456);
                    String token = ctx.getBean(TokenService.class).generateToken(1L, "fixture", "STAFF", 0);
                    assertThat(restarted.parseToken(token)).isNotNull();
                    assertThat(new TokenService("different-fixture-key-at-least-32-bytes", 456).parseToken(token)).isNull();
                });
    }

    @Test void shortKeyFailsSafelyAndExpiredTokensAreRejected() {
        config("prod").withPropertyValues("AUTH_TOKEN_SECRET=short-fixture").run(ctx -> assertThat(ctx).hasFailed());
        TokenService expired = new TokenService("fixture-expiry-secret-at-least-32-bytes", -1);
        assertThat(expired.parseToken(expired.generateToken(1L, "fixture", "STAFF", 0))).isNull();
        assertThat(expired.parseResetGrantToken(expired.generateResetGrantToken("fixture", 1L, 0, -1))).isNull();
    }

    @Test void missingKeyCurrentlyUsesEphemeralKeysRatherThanClaimingProductionReadiness() {
        config("prod").withPropertyValues("AUTH_TOKEN_SECRET=").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            String token = ctx.getBean(TokenService.class).generateToken(1L, "fixture", "STAFF", 0);
            assertThat(new TokenService("", 60).parseToken(token)).isNull();
        });
    }
}

package vn.codegym.smartcafe.database;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "spring.flyway.enabled=false"})
@DirtiesContext
class ForbiddenProblemHttpTest {
    private static final TestSchema SCHEMA = new TestSchema("schema.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SCHEMA.source::getUrl);
        registry.add("spring.datasource.username", SCHEMA.source::getUsername);
        registry.add("spring.datasource.password", SCHEMA.source::getPassword);
    }

    @LocalServerPort int port;
    @Autowired PasswordEncoder passwords;

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void forbiddenProblemsUseUtf8ForAuthenticatedStaffAndCustomer() throws Exception {
        assertForbidden("STAFF", "/api/v1/admin/dashboard");
        assertForbidden("CUSTOMER", "/api/v1/home/notifications");
    }

    private void assertForbidden(String role, String path) throws Exception {
        String username = "utf8_" + role.toLowerCase(java.util.Locale.ROOT);
        String password = "Aa1!" + UUID.randomUUID();
        SCHEMA.update("""
                INSERT INTO users(username, password_hash, full_name, email, role, status)
                VALUES (?, ?, 'Synthetic UTF-8 fixture', ?, ?, 'ACTIVE')
                """, username, passwords.encode(password), username + "@example.test", role);

        var login = http.send(request("/api/v1/auth/login").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("username", username, "password", password)))).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(login.statusCode()).isEqualTo(200);
        var session = json.readTree(login.body());
        assertThat(session.path("user").path("role").asText()).isEqualTo(role);
        String bearer = session.path("token").asText();
        assertThat(bearer).isNotBlank();
        var me = http.send(request("/api/v1/auth/me").header("Authorization", "Bearer " + bearer).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(me.statusCode()).isEqualTo(200);

        var denied = http.send(request(path).header("Authorization", "Bearer " + bearer).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(denied.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        String body = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(denied.body())).toString();
        var problem = json.readTree(body);
        assertThat(problem.path("status").asInt()).isEqualTo(403);
        assertThat(problem.path("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(problem.path("detail").asText()).isEqualTo("Bạn không có quyền truy cập chức năng này.");
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
    }

    @AfterAll
    static void cleanup(@Autowired DataSource dataSource) throws Exception {
        try {
            dataSource.unwrap(HikariDataSource.class).close();
        } finally {
            SCHEMA.close();
        }
    }
}

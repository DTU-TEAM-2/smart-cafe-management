package vn.codegym.smartcafe.config;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import vn.codegym.smartcafe.auth.TokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks authentication at module boundaries; the owning module's chain determines authorization. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
class HeaderTokenFilterMySqlTest {
    private static final List<String> PATHS = List.of("/api/v1/staff", "/api/v1/bills", "/api/v1/statistics/revenue");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired BearerTokenAuthenticator authenticator;

    @BeforeEach
    void setup() {
        clean();
        for (String role : List.of("ADMIN", "STAFF", "CUSTOMER")) {
            String username = "filter_contract_" + role.toLowerCase(java.util.Locale.ROOT);
            jdbc.update("""
                    INSERT INTO users(username,password_hash,full_name,email,role,status)
                    VALUES(?,?,'Synthetic filter fixture',?,?,'ACTIVE')
                    """, username, passwords.encode("FilterTest@123"), username + "@example.test", role);
        }
    }

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        jdbc.update("DELETE FROM users WHERE username IN ('filter_contract_admin','filter_contract_staff','filter_contract_customer')");
    }

    @Test
    void realLoginTokensReachEveryCollectionAndStatisticsBoundaryWithTheirDatabaseRole() throws Exception {
        for (String role : List.of("ADMIN", "STAFF", "CUSTOMER")) {
            String username = "filter_contract_" + role.toLowerCase(java.util.Locale.ROOT);
            String bearer = login(username);
            for (String path : PATHS) {
                Authentication identity = authenticate(path, bearer);
                assertThat(identity).as(path).isNotNull();
                assertThat(identity.getName()).isEqualTo(username);
                assertThat(identity.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_" + role);
            }
        }
    }

    @Test
    void collectionAndStatisticsBoundariesRejectExpiredRevokedAndTamperedTokens() throws Exception {
        String username = "filter_contract_admin";
        String bearer = login(username);
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username);
        var expiredIssuer = new TokenService(new String(tokens.getSecretKey(), StandardCharsets.UTF_8), -10);
        String expired = expiredIssuer.generateToken(id, username, "ADMIN", 0);
        String tampered = bearer.substring(0, bearer.length() - 1) + (bearer.endsWith("0") ? "1" : "0");
        jdbc.update("UPDATE users SET auth_version=auth_version+1 WHERE id=?", id);
        for (String path : PATHS) {
            for (String invalid : List.of(bearer, expired, tampered)) assertThat(authenticate(path, invalid)).as(path).isNull();
            assertThat(authenticate(path, null)).isNull();
        }
    }

    private String login(String username) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"FilterTest@123\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(response.getResponse().getContentAsString(), "$.token");
    }

    private Authentication authenticate(String path, String token) throws Exception {
        SecurityContextHolder.clearContext();
        try {
            var request = new MockHttpServletRequest("GET", "/cafe" + path);
            request.setContextPath("/cafe");
            if (token != null) request.addHeader("Authorization", "Bearer " + token);
            var observed = new AtomicReference<Authentication>();
            new HeaderTokenFilter(authenticator).doFilter(request, new MockHttpServletResponse(),
                    (req, res) -> observed.set(SecurityContextHolder.getContext().getAuthentication()));
            return observed.get();
        } finally { SecurityContextHolder.clearContext(); }
    }
}

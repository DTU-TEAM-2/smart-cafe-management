package vn.codegym.smartcafe.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @BeforeEach
    void setupTestData() {
        // Ensure test users exist with known passwords
        jdbcTemplate.update("DELETE FROM users WHERE username IN ('test_admin', 'test_staff', 'test_inactive')");

        String adminHash = passwordEncoder.encode("Admin@123");
        String staffHash = passwordEncoder.encode("Staff@123");

        jdbcTemplate.update("""
            INSERT INTO users (username, password_hash, full_name, email, phone, role, status, auth_version)
            VALUES ('test_admin', ?, 'Test Administrator', 'test_admin@smartcafe.vn', '0901111111', 'ADMIN', 'ACTIVE', 0)
            """, adminHash);

        jdbcTemplate.update("""
            INSERT INTO users (username, password_hash, full_name, email, phone, role, status, auth_version)
            VALUES ('test_staff', ?, 'Test Staff Member', 'test_staff@smartcafe.vn', '0902222222', 'STAFF', 'ACTIVE', 0)
            """, staffHash);

        jdbcTemplate.update("""
            INSERT INTO users (username, password_hash, full_name, email, phone, role, status, auth_version)
            VALUES ('test_inactive', ?, 'Inactive User', 'inactive@smartcafe.vn', '0903333333', 'STAFF', 'INACTIVE', 0)
            """, staffHash);
    }

    @Test
    void loginWithAdminCredentialsSucceeds() throws Exception {
        String body = """
            {
                "username": "test_admin",
                "password": "Admin@123"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.username").value("test_admin"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.fullName").value("Test Administrator"));
    }

    @Test
    void loginWithStaffCredentialsSucceeds() throws Exception {
        String body = """
            {
                "username": "test_staff",
                "password": "Staff@123"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.username").value("test_staff"))
                .andExpect(jsonPath("$.user.role").value("STAFF"));
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        String body = """
            {
                "username": "test_admin",
                "password": "WrongPassword123"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Tài khoản hoặc mật khẩu không chính xác."));
    }

    @Test
    void loginWithNonexistentUsernameFails() throws Exception {
        String body = """
            {
                "username": "non_existent_user_999",
                "password": "SomePassword123"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Tài khoản hoặc mật khẩu không chính xác."));
    }

    @Test
    void loginWithInactiveUserFails() throws Exception {
        String body = """
            {
                "username": "test_inactive",
                "password": "Staff@123"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminEndpointRequiresAdminRole() throws Exception {
        // Without token -> 401
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized());

        // With staff token -> 403
        Long staffId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'test_staff'", Long.class);
        String staffToken = tokenService.generateToken(staffId, "STAFF", 0L);

        mockMvc.perform(get("/api/v1/admin/dashboard")
                .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        // With admin token -> 200
        Long adminId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'test_admin'", Long.class);
        String adminToken = tokenService.generateToken(adminId, "ADMIN", 0L);

        mockMvc.perform(get("/api/v1/admin/dashboard")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.area").value("ADMIN"));
    }

    @Test
    void staffEndpointAccessibleByStaffAndAdmin() throws Exception {
        Long staffId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'test_staff'", Long.class);
        String staffToken = tokenService.generateToken(staffId, "STAFF", 0L);

        mockMvc.perform(get("/api/v1/staff/dashboard")
                .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area").value("STAFF"));

        Long adminId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'test_admin'", Long.class);
        String adminToken = tokenService.generateToken(adminId, "ADMIN", 0L);

        mockMvc.perform(get("/api/v1/staff/dashboard")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void getCurrentUserReturnsAuthenticatedUser() throws Exception {
        Long adminId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'test_admin'", Long.class);
        String token = tokenService.generateToken(adminId, "ADMIN", 0L);

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("test_admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.email").value("test_admin@smartcafe.vn"));
    }

    @Test
    void logoutReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đăng xuất thành công"));
    }
}

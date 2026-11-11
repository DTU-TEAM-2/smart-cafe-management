package vn.codegym.smartcafe.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OtpIntegrationTest {

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
        jdbcTemplate.update("DELETE FROM users WHERE username = 'otp_user'");
        jdbcTemplate.update("DELETE FROM auth_otp_challenges WHERE email = 'otp_user@smartcafe.vn'");
        jdbcTemplate.update("DELETE FROM auth_otp_limits WHERE email_key = ?",
                tokenService.hmacSha256("LIMIT:otp_user@smartcafe.vn"));

        String hash = passwordEncoder.encode("OldPassword@123");
        jdbcTemplate.update("""
            INSERT INTO users (username, password_hash, full_name, email, phone, role, status, auth_version)
            VALUES ('otp_user', ?, 'OTP Test User', 'otp_user@smartcafe.vn', '0909999999', 'STAFF', 'ACTIVE', 0)
            """, hash);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM auth_otp_challenges");
        jdbcTemplate.update("DELETE FROM users WHERE username = 'otp_user'");
    }

    @Test
    void forgotPasswordReturnsNeutralSuccessForAnyEmail() throws Exception {
        String existingEmail = "{\"email\": \"otp_user@smartcafe.vn\"}";
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));

        String nonExistingEmail = "{\"email\": \"nonexistent_999@smartcafe.vn\"}";
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(nonExistingEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").isString());
    }

    @Test
    void wrongOtpAttemptsArePersistedAndLockedAfterFiveFailures() throws Exception {
        // 1. Request forgot password
        String forgotBody = "{\"email\": \"otp_user@smartcafe.vn\"}";
        MvcResult forgotResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotBody))
                .andExpect(status().isOk())
                .andReturn();

        String challengeId = JsonPath.read(forgotResult.getResponse().getContentAsString(), "$.challengeId");

        // Find correct OTP
        String codeHash = jdbcTemplate.queryForObject(
                "SELECT code_hash FROM auth_otp_challenges WHERE id = ?",
                String.class,
                challengeId
        );
        String correctOtp = null;
        for (int i = 0; i < 1_000_000; i++) {
            String candidate = String.format("%06d", i);
            if (tokenService.hmacSha256(challengeId + "|RESET|" + candidate).equals(codeHash)) {
                correctOtp = candidate;
                break;
            }
        }
        assertThat(correctOtp).isNotNull();

        // 2. Enter wrong OTP 5 times, verify attempt increments are committed to database
        String wrongOtp = correctOtp.equals("000000") ? "111111" : "000000";
        String wrongOtpBody = String.format("{\"challengeId\": \"%s\", \"otp\": \"%s\"}", challengeId, wrongOtp);

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(wrongOtpBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OTP_ERROR"));

            Integer attemptsInDb = jdbcTemplate.queryForObject(
                    "SELECT attempts FROM auth_otp_challenges WHERE id = ?",
                    Integer.class,
                    challengeId
            );
            assertThat(attemptsInDb).isEqualTo(i);
        }

        // 3. 6th attempt with wrong OTP is locked
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongOtpBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("quá 5 lần")));

        // 4. 7th attempt with CORRECT OTP is ALSO rejected because challenge is locked
        String correctOtpBody = String.format("{\"challengeId\": \"%s\", \"otp\": \"%s\"}", challengeId, correctOtp);
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctOtpBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("quá 5 lần")));
    }

    @Test
    void completeOtpAndResetPasswordFlow() throws Exception {
        // 1. Request forgot password
        String forgotBody = "{\"email\": \"otp_user@smartcafe.vn\"}";
        MvcResult forgotResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotBody))
                .andExpect(status().isOk())
                .andReturn();

        String challengeId = JsonPath.read(forgotResult.getResponse().getContentAsString(), "$.challengeId");

        // 2. Query codeHash and find the OTP in tests
        String codeHash = jdbcTemplate.queryForObject(
                "SELECT code_hash FROM auth_otp_challenges WHERE id = ?",
                String.class,
                challengeId
        );

        String foundOtp = null;
        for (int i = 0; i < 1_000_000; i++) {
            String candidate = String.format("%06d", i);
            if (tokenService.hmacSha256(challengeId + "|RESET|" + candidate).equals(codeHash)) {
                foundOtp = candidate;
                break;
            }
        }
        assertTrue(foundOtp != null, "OTP must be crackable in test memory via known HMAC key");

        // 3. Test wrong OTP
        String wrongOtpBody = String.format("{\"challengeId\": \"%s\", \"otp\": \"000000\"}", challengeId);
        if (!foundOtp.equals("000000")) {
            mockMvc.perform(post("/api/v1/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(wrongOtpBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OTP_ERROR"));
        }

        // 4. Test correct OTP
        String correctOtpBody = String.format("{\"challengeId\": \"%s\", \"otp\": \"%s\"}", challengeId, foundOtp);
        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctOtpBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("RESET"))
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        String resetToken = JsonPath.read(verifyResult.getResponse().getContentAsString(), "$.token");

        // 5. Verify reset token endpoint
        mockMvc.perform(get("/api/v1/auth/verify-reset-token").param("token", resetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        // 6. Test OTP cannot be used twice
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctOtpBody))
                .andExpect(status().isBadRequest());

        // 7. Reset password with invalid policy
        String weakPasswordBody = String.format("""
            {
                "token": "%s",
                "newPassword": "short",
                "confirmPassword": "short"
            }
            """, resetToken);
        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(weakPasswordBody))
                .andExpect(status().isBadRequest());

        // 8. Reset password with valid password
        String validResetBody = String.format("""
            {
                "token": "%s",
                "newPassword": "NewPassword@2026",
                "confirmPassword": "NewPassword@2026"
            }
            """, resetToken);
        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validResetBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("thành công")));

        // 9. Reset token cannot be reused
        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validResetBody))
                .andExpect(status().isUnauthorized());

        // 10. Login with new password succeeds
        String newLoginBody = """
            {
                "username": "otp_user",
                "password": "NewPassword@2026"
            }
            """;
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(newLoginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("otp_user"));

        // 11. Login with old password fails
        String oldLoginBody = """
            {
                "username": "otp_user",
                "password": "OldPassword@123"
            }
            """;
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(oldLoginBody))
                .andExpect(status().isUnauthorized());
    }
}

package vn.codegym.smartcafe.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.host=",
        "spring.mail.username="
})
class UnconfiguredSmtpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        for (String email : java.util.List.of("unconfigured_smtp@smartcafe.vn", "ghost_unconfigured@smartcafe.vn")) {
            update("DELETE FROM auth_otp_challenges WHERE email = ?", email);
            update("DELETE FROM auth_otp_limits WHERE email_key = ? AND purpose = 'RESET'",
                    tokenService.hmacSha256("LIMIT:" + email));
        }
        update("DELETE FROM users WHERE username = 'smtp_unconfigured_user'");

        update(
                """
                INSERT INTO users (
                    username, password_hash, full_name, email, phone, role, status,
                    must_change_password, auth_version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
                """,
                "smtp_unconfigured_user",
                passwordEncoder.encode("ExistingPassword123!"),
                "Unconfigured SMTP User",
                "unconfigured_smtp@smartcafe.vn",
                "0988776655",
                "STAFF",
                "ACTIVE",
                0,
                0L
        );
    }

    @org.junit.jupiter.api.AfterEach
    void cleanUp() {
        for (String email : java.util.List.of("unconfigured_smtp@smartcafe.vn", "ghost_unconfigured@smartcafe.vn")) {
            update("DELETE FROM auth_otp_challenges WHERE email = ?", email);
            update("DELETE FROM auth_otp_limits WHERE email_key = ? AND purpose = 'RESET'",
                    tokenService.hmacSha256("LIMIT:" + email));
        }
        update("DELETE FROM users WHERE username = 'smtp_unconfigured_user'");
    }

    private int update(String sql, Object... args) {
        return TestDatabase.update(jdbcTemplate, sql, args);
    }

    @Test
    void resendOtpWhenSmtpHostNotConfiguredProducesIdentical400AndPreservesOriginalOtp() throws Exception {
        // 1. Forgot password for real user and dummy user when SMTP is not configured
        MvcResult realForgot = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"unconfigured_smtp@smartcafe.vn\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult dummyForgot = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"ghost_unconfigured@smartcafe.vn\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String realChallengeId = JsonPath.read(realForgot.getResponse().getContentAsString(), "$.challengeId");
        String dummyChallengeId = JsonPath.read(dummyForgot.getResponse().getContentAsString(), "$.challengeId");

        // Query original codeHash and crack original OTP for real user
        String originalCodeHash = jdbcTemplate.queryForObject(
                "SELECT code_hash FROM auth_otp_challenges WHERE id = ?",
                String.class,
                realChallengeId
        );
        String originalOtp = null;
        for (int i = 0; i < 1_000_000; i++) {
            String candidate = String.format("%06d", i);
            if (tokenService.hmacSha256(realChallengeId + "|RESET|" + candidate).equals(originalCodeHash)) {
                originalOtp = candidate;
                break;
            }
        }
        assertThat(originalOtp).isNotNull();

        // Fast-forward cooldown
        update(
                "UPDATE auth_otp_challenges SET next_send_at = DATE_SUB(NOW(6), INTERVAL 5 SECOND) WHERE id IN (?, ?)",
                realChallengeId,
                dummyChallengeId
        );

        // 2. Call resend-otp for real user when SMTP host is unconfigured (Nam P2 finding)
        MvcResult realResendResult = mockMvc.perform(post("/api/v1/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"challengeId\": \"%s\"}", realChallengeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_ERROR"))
                .andReturn();

        // 3. Call resend-otp for dummy user when SMTP host is unconfigured
        MvcResult dummyResendResult = mockMvc.perform(post("/api/v1/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"challengeId\": \"%s\"}", dummyChallengeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_ERROR"))
                .andReturn();

        // 4. Assert responses are completely indistinguishable (Anti-Account Enumeration)
        assertThat(realResendResult.getResponse().getStatus())
                .isEqualTo(dummyResendResult.getResponse().getStatus())
                .isEqualTo(400);

        String realDetail = JsonPath.read(realResendResult.getResponse().getContentAsString(), "$.detail");
        String dummyDetail = JsonPath.read(dummyResendResult.getResponse().getContentAsString(), "$.detail");
        assertThat(realDetail)
                .isEqualTo(dummyDetail)
                .contains("Không thể gửi email OTP qua dịch vụ gửi thư");

        java.util.Map<String, Object> realBefore = jdbcTemplate.queryForMap("SELECT * FROM auth_otp_challenges WHERE id = ?", realChallengeId);
        java.util.Map<String, Object> dummyBefore = jdbcTemplate.queryForMap("SELECT * FROM auth_otp_challenges WHERE id = ?", dummyChallengeId);
        for (int i = 0; i < 3; i++) {
            for (String id : java.util.List.of(realChallengeId, dummyChallengeId)) {
                MvcResult result = mockMvc.perform(post("/api/v1/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"challengeId\":\"%s\"}", id)))
                        .andExpect(status().isBadRequest()).andReturn();
                assertThat(result.getResponse().getContentAsString())
                        .isEqualTo(realResendResult.getResponse().getContentAsString());
            }
            assertThat(jdbcTemplate.queryForMap("SELECT * FROM auth_otp_challenges WHERE id = ?", realChallengeId)).isEqualTo(realBefore);
            assertThat(jdbcTemplate.queryForMap("SELECT * FROM auth_otp_challenges WHERE id = ?", dummyChallengeId)).isEqualTo(dummyBefore);
        }

        // 5. Verify database state is untouched: code_hash unchanged, sends still 1 (Nam P2 preserved)
        String codeHashAfter = jdbcTemplate.queryForObject(
                "SELECT code_hash FROM auth_otp_challenges WHERE id = ?",
                String.class,
                realChallengeId
        );
        assertThat(codeHashAfter).isEqualTo(originalCodeHash);

        Integer realSends = jdbcTemplate.queryForObject(
                "SELECT sends FROM auth_otp_challenges WHERE id = ?",
                Integer.class,
                realChallengeId
        );
        assertThat(realSends).isEqualTo(1);

        Integer dummySends = jdbcTemplate.queryForObject(
                "SELECT sends FROM auth_otp_challenges WHERE id = ?",
                Integer.class,
                dummyChallengeId
        );
        assertThat(dummySends).isEqualTo(1);

        // 6. Verify original OTP remains valid and can still be verified
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"challengeId\": \"%s\", \"otp\": \"%s\"}", realChallengeId, originalOtp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("RESET"))
                .andExpect(jsonPath("$.token").isString());
    }
}

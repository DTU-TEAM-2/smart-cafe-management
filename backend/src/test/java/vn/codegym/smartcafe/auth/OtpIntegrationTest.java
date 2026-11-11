package vn.codegym.smartcafe.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertThat(dbName).isNotNull().endsWith("_test");

        jdbcTemplate.update("DELETE FROM users WHERE username = 'otp_user'");
        jdbcTemplate.update("DELETE FROM auth_otp_challenges WHERE email LIKE '%@smartcafe.vn'");
        jdbcTemplate.update("DELETE FROM auth_otp_limits WHERE purpose = 'RESET'");

        String hash = passwordEncoder.encode("OldPassword@123");
        jdbcTemplate.update("""
            INSERT INTO users (username, password_hash, full_name, email, phone, role, status, auth_version)
            VALUES ('otp_user', ?, 'OTP Test User', 'otp_user@smartcafe.vn', '0909999999', 'STAFF', 'ACTIVE', 0)
            """, hash);
    }

    @AfterEach
    void cleanUp() {
        String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (dbName != null && dbName.endsWith("_test")) {
            jdbcTemplate.update("DELETE FROM auth_otp_challenges WHERE email LIKE '%@smartcafe.vn'");
            jdbcTemplate.update("DELETE FROM auth_otp_limits WHERE purpose = 'RESET'");
            jdbcTemplate.update("DELETE FROM users WHERE username = 'otp_user'");
        }
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

    @Test
    void resendOtpFailsGracefullyWhenSmtpUnavailableAndPreservesOriginalOtp() throws Exception {
        // 1. Initial forgot password request
        String forgotBody = "{\"email\": \"otp_user@smartcafe.vn\"}";
        MvcResult forgotResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotBody))
                .andExpect(status().isOk())
                .andReturn();

        String challengeId = JsonPath.read(forgotResult.getResponse().getContentAsString(), "$.challengeId");

        // 2. Query original codeHash and crack original OTP
        String originalCodeHash = jdbcTemplate.queryForObject(
                "SELECT code_hash FROM auth_otp_challenges WHERE id = ?",
                String.class,
                challengeId
        );
        String originalOtp = null;
        for (int i = 0; i < 1_000_000; i++) {
            String candidate = String.format("%06d", i);
            if (tokenService.hmacSha256(challengeId + "|RESET|" + candidate).equals(originalCodeHash)) {
                originalOtp = candidate;
                break;
            }
        }
        assertThat(originalOtp).isNotNull();

        // 3. Fast-forward cooldown in DB so resend is allowed
        jdbcTemplate.update(
                "UPDATE auth_otp_challenges SET next_send_at = DATE_SUB(NOW(6), INTERVAL 5 SECOND) WHERE id = ?",
                challengeId
        );

        // 4. Request resend-otp when SMTP is down (spring.mail.port=1025 has no listening server in test)
        String resendBody = String.format("{\"challengeId\": \"%s\"}", challengeId);
        mockMvc.perform(post("/api/v1/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resendBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_ERROR"))
                .andExpect(jsonPath("$.detail").value(containsString("Không thể gửi email OTP")));

        // 5. Verify database state is untouched (codeHash unchanged, sends still 1)
        String codeHashAfterFailedResend = jdbcTemplate.queryForObject(
                "SELECT code_hash FROM auth_otp_challenges WHERE id = ?",
                String.class,
                challengeId
        );
        assertThat(codeHashAfterFailedResend).isEqualTo(originalCodeHash);

        Integer sendsCount = jdbcTemplate.queryForObject(
                "SELECT sends FROM auth_otp_challenges WHERE id = ?",
                Integer.class,
                challengeId
        );
        assertThat(sendsCount).isEqualTo(1);

        // 6. Verify original OTP remains 100% valid and can be verified
        String verifyBody = String.format("{\"challengeId\": \"%s\", \"otp\": \"%s\"}", challengeId, originalOtp);
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("RESET"))
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void emailEnumerationIndistinguishableAcrossVerifyAndResend() throws Exception {
        // 1. Request forgot password for existing vs non-existent email
        MvcResult existingResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"otp_user@smartcafe.vn\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult nonExistingResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"ghost_user@smartcafe.vn\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String existingChallengeId = JsonPath.read(existingResult.getResponse().getContentAsString(), "$.challengeId");
        String nonExistingChallengeId = JsonPath.read(nonExistingResult.getResponse().getContentAsString(), "$.challengeId");

        // Verify messages at step 1 are identical
        String msg1 = JsonPath.read(existingResult.getResponse().getContentAsString(), "$.message");
        String msg2 = JsonPath.read(nonExistingResult.getResponse().getContentAsString(), "$.message");
        assertThat(msg1).isEqualTo(msg2);

        // 2. Both return identical 400 with "Mã OTP không chính xác. Bạn còn 4 lần thử." on wrong OTP
        String wrongOtpBodyExisting = String.format("{\"challengeId\": \"%s\", \"otp\": \"999999\"}", existingChallengeId);
        String wrongOtpBodyNonExisting = String.format("{\"challengeId\": \"%s\", \"otp\": \"999999\"}", nonExistingChallengeId);

        MvcResult verifyFail1 = mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongOtpBodyExisting))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_ERROR"))
                .andReturn();

        MvcResult verifyFail2 = mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongOtpBodyNonExisting))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_ERROR"))
                .andReturn();

        String detail1 = JsonPath.read(verifyFail1.getResponse().getContentAsString(), "$.detail");
        String detail2 = JsonPath.read(verifyFail2.getResponse().getContentAsString(), "$.detail");
        assertThat(detail1).isEqualTo(detail2).contains("còn 4 lần thử");

        // 3. Enter wrong OTP 4 more times to lock out both challenges
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(wrongOtpBodyExisting));
            mockMvc.perform(post("/api/v1/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(wrongOtpBodyNonExisting));
        }

        // 4. 6th attempt on both returns identical lockout error
        MvcResult lockResult1 = mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongOtpBodyExisting))
                .andExpect(status().isBadRequest())
                .andReturn();

        MvcResult lockResult2 = mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongOtpBodyNonExisting))
                .andExpect(status().isBadRequest())
                .andReturn();

        String lockMsg1 = JsonPath.read(lockResult1.getResponse().getContentAsString(), "$.detail");
        String lockMsg2 = JsonPath.read(lockResult2.getResponse().getContentAsString(), "$.detail");
        assertThat(lockMsg1).isEqualTo(lockMsg2).contains("quá 5 lần");
    }

    @Test
    void resendOtpUnderSmtpFailureProducesIdentical400ForRealAndDummyEmails() throws Exception {
        // 1. Forgot password for real user and dummy user
        MvcResult realResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"otp_user@smartcafe.vn\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult dummyResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"ghost_resend_smtp_down@smartcafe.vn\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String realChallengeId = JsonPath.read(realResult.getResponse().getContentAsString(), "$.challengeId");
        String dummyChallengeId = JsonPath.read(dummyResult.getResponse().getContentAsString(), "$.challengeId");

        // 2. Query original codeHash and crack original OTP for real user
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

        // 3. Fast-forward cooldown in DB for both challenges
        jdbcTemplate.update(
                "UPDATE auth_otp_challenges SET next_send_at = DATE_SUB(NOW(6), INTERVAL 5 SECOND) WHERE id IN (?, ?)",
                realChallengeId,
                dummyChallengeId
        );

        // 4. Call resend-otp for real user when SMTP is down (port 1025 has no listening service)
        MvcResult realResendResult = mockMvc.perform(post("/api/v1/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"challengeId\": \"%s\"}", realChallengeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_ERROR"))
                .andReturn();

        // 5. Call resend-otp for dummy user when SMTP is down
        MvcResult dummyResendResult = mockMvc.perform(post("/api/v1/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"challengeId\": \"%s\"}", dummyChallengeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_ERROR"))
                .andReturn();

        // 6. Assert responses are completely indistinguishable (Anti-Account Enumeration)
        assertThat(realResendResult.getResponse().getStatus())
                .isEqualTo(dummyResendResult.getResponse().getStatus())
                .isEqualTo(400);

        String realDetail = JsonPath.read(realResendResult.getResponse().getContentAsString(), "$.detail");
        String dummyDetail = JsonPath.read(dummyResendResult.getResponse().getContentAsString(), "$.detail");
        assertThat(realDetail)
                .isEqualTo(dummyDetail)
                .contains("Không thể gửi email OTP qua dịch vụ gửi thư");

        // 7. Verify real challenge database state is untouched (codeHash unchanged, sends still 1)
        String codeHashAfterFailedResend = jdbcTemplate.queryForObject(
                "SELECT code_hash FROM auth_otp_challenges WHERE id = ?",
                String.class,
                realChallengeId
        );
        assertThat(codeHashAfterFailedResend).isEqualTo(originalCodeHash);

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

        // 8. Verify original OTP remains valid for real user
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"challengeId\": \"%s\", \"otp\": \"%s\"}", realChallengeId, originalOtp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("RESET"))
                .andExpect(jsonPath("$.token").isString());

        // 9. Verify dummy challenge NEVER produces a valid token
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"challengeId\": \"%s\", \"otp\": \"123456\"}", dummyChallengeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_ERROR"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void concurrentForgotPasswordDoesNotExceedRateLimit() throws Exception {
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"concurrent_test@smartcafe.vn\"}"))
                            .andReturn();
                    statusCodes.add(result.getResponse().getStatus());
                } catch (Exception e) {
                    statusCodes.add(500);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        long successCount = statusCodes.stream().filter(code -> code == 200).count();
        long rateLimitedCount = statusCodes.stream().filter(code -> code == 400).count();

        assertThat(successCount).isEqualTo(1L);
        assertThat(rateLimitedCount).isEqualTo(19L);

        // Check database limit table
        String emailKey = tokenService.hmacSha256("LIMIT:concurrent_test@smartcafe.vn");
        Integer dbCount = jdbcTemplate.queryForObject(
                "SELECT request_count FROM auth_otp_limits WHERE email_key = ? AND purpose = 'RESET'",
                Integer.class,
                emailKey
        );
        assertThat(dbCount).isEqualTo(1);
    }

    @Test
    void concurrentResendOtpDoesNotExceedSendsLimit() throws Exception {
        // Start dummy SMTP server on port 1025 to simulate healthy SMTP accepting emails
        try (SimpleDummySmtpServer smtpServer = new SimpleDummySmtpServer(1025)) {
            // Initial forgot password request for real existing user
            MvcResult forgotResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"otp_user@smartcafe.vn\"}"))
                    .andExpect(status().isOk())
                    .andReturn();

            String challengeId = JsonPath.read(forgotResult.getResponse().getContentAsString(), "$.challengeId");

            // Fast-forward cooldown to the past
            jdbcTemplate.update(
                    "UPDATE auth_otp_challenges SET next_send_at = DATE_SUB(NOW(6), INTERVAL 5 SECOND) WHERE id = ?",
                    challengeId
            );

            int threads = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threads);
            List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        MvcResult result = mockMvc.perform(post("/api/v1/auth/resend-otp")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(String.format("{\"challengeId\": \"%s\"}", challengeId)))
                                .andReturn();
                        statusCodes.add(result.getResponse().getStatus());
                    } catch (Exception e) {
                        statusCodes.add(500);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            long successCount = statusCodes.stream().filter(code -> code == 200).count();
            long failureCount = statusCodes.stream().filter(code -> code == 429 || code == 400).count();

            // Exactly 1 succeeds, other fails with cooldown error (HTTP 429)
            assertThat(successCount).isEqualTo(1L);
            assertThat(failureCount).isEqualTo(1L);

            Integer finalSends = jdbcTemplate.queryForObject(
                    "SELECT sends FROM auth_otp_challenges WHERE id = ?",
                    Integer.class,
                    challengeId
            );
            assertThat(finalSends).isEqualTo(2);

            // Row-level locking (SELECT ... FOR UPDATE) ensured exactly 1 email was sent during resend!
            // Total emails received = 1 (forgot-password) + 1 (resend) = 2.
            assertThat(smtpServer.getEmailsReceived()).isEqualTo(2);
        }
    }

    @Test
    void verifyResetTokenViaPostAndCheckCacheControlHeader() throws Exception {
        // 1. Forgot password
        MvcResult forgotResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"otp_user@smartcafe.vn\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String challengeId = JsonPath.read(forgotResult.getResponse().getContentAsString(), "$.challengeId");

        // Find OTP
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

        // 2. Verify OTP to get resetToken
        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"challengeId\": \"%s\", \"otp\": \"%s\"}", challengeId, correctOtp)))
                .andExpect(status().isOk())
                .andReturn();

        String resetToken = JsonPath.read(verifyResult.getResponse().getContentAsString(), "$.token");

        // 3. Test POST /api/v1/auth/verify-reset-token with token in request body
        mockMvc.perform(post("/api/v1/auth/verify-reset-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"token\": \"%s\"}", resetToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.valid").value(true));

        // 4. Test GET /api/v1/auth/verify-reset-token also returns Cache-Control: no-store
        mockMvc.perform(get("/api/v1/auth/verify-reset-token")
                .param("token", resetToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.valid").value(true));
    }

    private static class SimpleDummySmtpServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor;
        private final AtomicInteger emailsReceived = new AtomicInteger(0);

        public SimpleDummySmtpServer(int port) throws IOException {
            this.serverSocket = new ServerSocket(port);
            this.executor = Executors.newCachedThreadPool();
            this.executor.submit(this::listen);
        }

        private void listen() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handleClient(socket));
                } catch (IOException e) {
                    break;
                }
            }
        }

        private void handleClient(Socket socket) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write("220 localhost ESMTP\r\n");
                writer.flush();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("HELO") || line.startsWith("EHLO")) {
                        writer.write("250 OK\r\n");
                    } else if (line.startsWith("MAIL FROM:") || line.startsWith("RCPT TO:")) {
                        writer.write("250 OK\r\n");
                    } else if (line.startsWith("DATA")) {
                        writer.write("354 Start mail input; end with <CRLF>.<CRLF>\r\n");
                        writer.flush();
                        while ((line = reader.readLine()) != null) {
                            if (line.equals(".")) {
                                emailsReceived.incrementAndGet();
                                writer.write("250 OK message queued\r\n");
                                writer.flush();
                                break;
                            }
                        }
                    } else if (line.startsWith("QUIT")) {
                        writer.write("221 Bye\r\n");
                        writer.flush();
                        break;
                    } else if (line.startsWith("RSET")) {
                        writer.write("250 OK\r\n");
                    } else {
                        writer.write("250 OK\r\n");
                    }
                    writer.flush();
                }
            } catch (Exception ignored) {
            }
        }

        public int getEmailsReceived() {
            return emailsReceived.get();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            executor.shutdownNow();
        }
    }
}

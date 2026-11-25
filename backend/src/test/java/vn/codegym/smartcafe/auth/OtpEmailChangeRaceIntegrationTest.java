package vn.codegym.smartcafe.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "smartcafe.auth.token-secret=dt268-test-only-signing-key-at-least-32-characters"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:mysql:.*")
@Timeout(30)
class OtpEmailChangeRaceIntegrationTest {

    private static final String OLD_EMAIL = "staff_race@example.test";
    private static final String NEW_EMAIL = "staff_race_new@example.test";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean AuthRepository repository;
    @MockitoBean EmailService mail;

    private final List<String> deliveredCodes = new CopyOnWriteArrayList<>();
    private Long staffUserId;
    private Long adminUserId;

    @BeforeEach
    void setup() {
        cleanup();

        // Insert Admin test user for PUT requests
        update("""
                INSERT INTO users (username, password_hash, full_name, email, role, status)
                VALUES ('admin_race', ?, 'Admin Race User', 'admin_race@example.test', 'ADMIN', 'ACTIVE')
                """, passwords.encode("AdminPass@123"));
        adminUserId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'admin_race'", Long.class);
        update("INSERT INTO staff_profiles (user_id, address, gender, salary) VALUES (?, 'DN', 'MALE', 15000000)", adminUserId);

        // Insert Staff test user
        update("""
                INSERT INTO users (username, password_hash, full_name, email, role, status)
                VALUES ('staff_race', ?, 'Staff Race User', ?, 'STAFF', 'ACTIVE')
                """, passwords.encode("StaffPass@123"), OLD_EMAIL);
        staffUserId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'staff_race'", Long.class);
        update("INSERT INTO staff_profiles (user_id, address, gender, salary) VALUES (?, 'DN', 'FEMALE', 10000000)", staffUserId);

        deliveredCodes.clear();
        doAnswer(call -> {
            deliveredCodes.add(call.getArgument(1));
            return null;
        }).when(mail).sendOtpEmail(anyString(), anyString(), eq("RESET"), anyLong());
    }

    @AfterEach
    void cleanup() {
        update("DELETE FROM staff_profiles WHERE user_id IN (SELECT id FROM users WHERE username IN ('staff_race', 'admin_race'))");
        update("DELETE FROM auth_otp_challenges WHERE email IN (?, ?, 'admin_race@example.test')", OLD_EMAIL, NEW_EMAIL);
        update("DELETE FROM auth_otp_limits WHERE email_key IN (?, ?)",
                tokens.hmacSha256("LIMIT:" + OLD_EMAIL), tokens.hmacSha256("LIMIT:" + NEW_EMAIL));
        update("DELETE FROM users WHERE username IN ('staff_race', 'admin_race')");
    }

    // (a) pause sau lookup email cũ trước user lock -> đổi email commit -> tiếp tục, không tạo RESET credential hợp lệ cho email cũ
    @Test
    void raceForgotLookupPausedThenStaffPutChangesEmail_doesNotCreateValidResetChallengeForOldEmail() throws Exception {
        var pauseBeforeLock = new CountDownLatch(1);
        var resumeLock = new CountDownLatch(1);

        // Intercept findUserByIdForUpdate when called with staffUserId during forgot-password
        doAnswer(call -> {
            Long id = call.getArgument(0);
            if (id.equals(staffUserId)) {
                pauseBeforeLock.countDown();
                await(resumeLock);
            }
            return call.callRealMethod();
        }).when(repository).findUserByIdForUpdate(staffUserId);

        try (var executor = Executors.newSingleThreadExecutor()) {
            // Thread A initiates forgot-password with old email
            var forgotFuture = executor.submit(() -> mvc.perform(post("/api/v1/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + OLD_EMAIL + "\"}"))
                    .andReturn());

            // Await Thread A reaching the lock point (after findUserByEmail, before findUserByIdForUpdate)
            await(pauseBeforeLock);

            // Thread B (Main thread) commits email update via Staff API
            String updateBody = """
                    {"fullName":"Staff Race User","email":"%s","phone":"0901234567",
                     "address":"DN","role":"STAFF","salary":10000000,"version":"0"}
                    """.formatted(NEW_EMAIL);
            mvc.perform(put("/api/v1/staff/" + staffUserId)
                    .with(user("admin_race").roles("ADMIN")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

            // Resume Thread A: findUserByIdForUpdate will now observe the updated email under lock
            resumeLock.countDown();

            MvcResult result = forgotFuture.get(10, TimeUnit.SECONDS);
            assertThat(result.getResponse().getStatus()).isEqualTo(200);

            String challengeId = (String) body(result).get("challengeId");
            assertThat(challengeId).isNotEmpty();

            // Challenge in DB must be a DUMMY challenge (user_id IS NULL, purpose REGISTER)
            var challenge = repository.findOtpChallengeById(challengeId).orElseThrow();
            assertThat(challenge.userId()).as("Must not associate challenge with user ID when email was changed").isNull();
            assertThat(challenge.purpose()).as("Must be a dummy registration challenge for anti-enumeration").isEqualTo("REGISTER");

            // No email should have been delivered to OLD_EMAIL
            verify(mail, never()).sendOtpEmail(eq(OLD_EMAIL), anyString(), eq("RESET"), anyLong());

            // Trying to verify this dummy challenge as RESET must fail (400)
            MvcResult verifyResult = mvc.perform(post("/api/v1/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"123456\"}"))
                    .andReturn();
            assertThat(verifyResult.getResponse().getStatus()).isEqualTo(400);
        } finally {
            resumeLock.countDown();
        }
    }

    // (b) & (c) OTP/grant đã cấp trước đổi email bị từ chối, resend không gửi thêm
    @Test
    void existingOtpChallengeAndGrantRevokedUponEmailChange() throws Exception {
        // Step 1: Normal forgot-password with old email before update
        MvcResult forgotResult = mvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + OLD_EMAIL + "\"}"))
                .andReturn();
        assertThat(forgotResult.getResponse().getStatus()).isEqualTo(200);
        String challengeId = (String) body(forgotResult).get("challengeId");
        assertThat(deliveredCodes).isNotEmpty();
        String code = deliveredCodes.getLast();

        // Step 2: Staff PUT changes email to NEW_EMAIL
        String updateBody = """
                {"fullName":"Staff Race User","email":"%s","phone":"0901234567",
                 "address":"DN","role":"STAFF","salary":10000000,"version":"0"}
                """.formatted(NEW_EMAIL);
        mvc.perform(put("/api/v1/staff/" + staffUserId)
                .with(user("admin_race").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        // Step 3: Challenge in DB must have used_at NOT NULL (immediately revoked)
        var challenge = repository.findOtpChallengeById(challengeId).orElseThrow();
        assertThat(challenge.usedAt()).isNotNull();

        // Step 4: Verifying the old OTP must fail (400)
        MvcResult verifyResult = mvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"" + code + "\"}"))
                .andReturn();
        assertThat(verifyResult.getResponse().getStatus()).isEqualTo(400);

        // Step 5: Resend on the old challenge must fail (400) and send NO further emails
        int emailsBeforeResend = deliveredCodes.size();
        MvcResult resendResult = mvc.perform(post("/api/v1/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"" + challengeId + "\"}"))
                .andReturn();
        assertThat(resendResult.getResponse().getStatus()).isEqualTo(400);
        assertThat(deliveredCodes.size()).isEqualTo(emailsBeforeResend);
    }

    // (d) & (e) New email recovery/login succeeds, updating name/phone does not increment auth_version
    @Test
    void newEmailCanSuccessfullyRecoverAndLogin_updatingRegularFieldsPreservesAuthVersion() throws Exception {
        // Step 1: Change email to NEW_EMAIL
        String updateEmailBody = """
                {"fullName":"Staff Race User","email":"%s","phone":"0901234567",
                 "address":"DN","role":"STAFF","salary":10000000,"version":"0"}
                """.formatted(NEW_EMAIL);
        mvc.perform(put("/api/v1/staff/" + staffUserId)
                .with(user("admin_race").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateEmailBody))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        long authVersionAfterEmailChange = jdbc.queryForObject(
                "SELECT auth_version FROM users WHERE id = ?", Long.class, staffUserId);
        assertThat(authVersionAfterEmailChange).isEqualTo(1L);

        // Step 2: Request forgot-password with NEW_EMAIL
        MvcResult forgotResult = mvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + NEW_EMAIL + "\"}"))
                .andReturn();
        assertThat(forgotResult.getResponse().getStatus()).isEqualTo(200);
        String challengeId = (String) body(forgotResult).get("challengeId");
        assertThat(deliveredCodes).isNotEmpty();
        String code = deliveredCodes.getLast();

        // Step 3: Verify OTP for NEW_EMAIL succeeds and returns reset grant token
        MvcResult verifyResult = mvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"" + code + "\"}"))
                .andReturn();
        assertThat(verifyResult.getResponse().getStatus()).isEqualTo(200);
        String grantToken = (String) body(verifyResult).get("token");
        assertThat(grantToken).isNotEmpty();

        // Step 4: Reset password with grantToken succeeds
        MvcResult resetResult = mvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + grantToken + "\",\"newPassword\":\"BrandNewPass@123\",\"confirmPassword\":\"BrandNewPass@123\"}"))
                .andReturn();
        assertThat(resetResult.getResponse().getStatus()).isEqualTo(200);

        // Step 5: Login with new password succeeds
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"staff_race\",\"password\":\"BrandNewPass@123\"}"))
                .andReturn();
        assertThat(loginResult.getResponse().getStatus()).isEqualTo(200);

        // Step 6: Updating regular field (fullName, phone) does NOT increment auth_version
        long currentVersion = jdbc.queryForObject(
                "SELECT version FROM users WHERE id = ?", Long.class, staffUserId);
        long currentAuthVersion = jdbc.queryForObject(
                "SELECT auth_version FROM users WHERE id = ?", Long.class, staffUserId);
        String updateNameBody = """
                {"fullName":"Staff Modified Name","email":"%s","phone":"0909999888",
                 "address":"DN","role":"STAFF","salary":10000000,"version":"%d"}
                """.formatted(NEW_EMAIL, currentVersion);
        mvc.perform(put("/api/v1/staff/" + staffUserId)
                .with(user("admin_race").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateNameBody))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        long finalAuthVersion = jdbc.queryForObject(
                "SELECT auth_version FROM users WHERE id = ?", Long.class, staffUserId);
        assertThat(finalAuthVersion).isEqualTo(currentAuthVersion);
    }

    private java.util.Map<String, Object> body(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$");
    }

    private int update(String sql, Object... args) {
        return TestDatabase.update(jdbc, sql, args);
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(10, TimeUnit.SECONDS)).as("race checkpoint within timeout").isTrue();
    }
}

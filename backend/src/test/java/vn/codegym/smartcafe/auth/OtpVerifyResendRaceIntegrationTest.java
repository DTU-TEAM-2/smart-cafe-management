package vn.codegym.smartcafe.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@Timeout(30)
class OtpVerifyResendRaceIntegrationTest {
    private static final String EMAIL = "otp_race@example.test";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean AuthRepository repository;
    @MockitoBean EmailService mail;
    private final List<String> deliveredCodes = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setup() {
        cleanup();
        update("""
                INSERT INTO users (username, password_hash, full_name, email, role, status)
                VALUES ('otp_race', ?, 'OTP race fixture', ?, 'STAFF', 'ACTIVE')
                """, passwords.encode("OldPassword@123"), EMAIL);
        deliveredCodes.clear();
        doAnswer(call -> { deliveredCodes.add(call.getArgument(1)); return null; }).when(mail)
                .sendOtpEmail(eq(EMAIL), anyString(), eq("RESET"), anyLong());
    }

    @AfterEach
    void cleanup() {
        update("DELETE FROM auth_otp_challenges WHERE email = ?", EMAIL);
        update("DELETE FROM auth_otp_limits WHERE email_key = ? AND purpose = 'RESET'", tokens.hmacSha256("LIMIT:" + EMAIL));
        update("DELETE FROM users WHERE username = 'otp_race'");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void oldOtpMustNotConsumeAChallengeRotatedByCommittedResend(boolean delivered) throws Exception {
        String id = forgot();
        String codeA = deliveredCodes.getFirst();
        if (!delivered) failMail();
        var beforeConsume = new CountDownLatch(1);
        var continueConsume = new CountDownLatch(1);
        doAnswer(call -> {
            beforeConsume.countDown();
            await(continueConsume);
            return call.callRealMethod();
        }).when(repository).markOtpUsedIfCurrent(eq(id), anyString(), anyInt());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var verifyA = executor.submit(() -> verifyOtp(id, codeA));
            try {
                await(beforeConsume);
                assertThat(resend(id).getResponse().getStatus()).isEqualTo(200);
                OtpChallengeRecord current = challenge(id);
                String codeB = deliveredCodes.getLast();
                assertThat(current.sends()).isEqualTo(2);
                assertThat(current.codeHash()).isEqualTo(tokens.hmacSha256(id + "|RESET|" + codeB));
                assertThat(current.usedAt()).isNull();
                continueConsume.countDown();
                MvcResult stale = verifyA.get(10, TimeUnit.SECONDS);
                assertThat(stale.getResponse().getStatus()).as("stale verify(A) after committed resend(B)").isEqualTo(400);
                assertThat(body(stale)).doesNotContainKey("token");
                assertThat(challenge(id)).isEqualTo(current);
                assertThat(verifyOtp(id, codeB).getResponse().getStatus()).isEqualTo(200);
                assertThat(deliveredCodes).hasSize(delivered ? 2 : 1);
                verify(mail, times(2)).sendOtpEmail(eq(EMAIL), anyString(), eq("RESET"), anyLong());
            } finally {
                continueConsume.countDown();
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void staleWrongOtpAttemptMustNotCorruptCurrentGeneration(boolean delivered) throws Exception {
        String id = forgot();
        String codeA = deliveredCodes.getFirst();
        if (!delivered) failMail();
        String wrong = codeA.equals("000000") ? "111111" : "000000";
        update("UPDATE auth_otp_challenges SET attempts = 4 WHERE id = ?", id);
        var beforeAttempt = new CountDownLatch(1);
        var continueAttempt = new CountDownLatch(1);
        doAnswer(call -> {
            beforeAttempt.countDown();
            await(continueAttempt);
            return call.callRealMethod();
        }).when(repository).incrementOtpAttemptsIfCurrent(eq(id), anyString(), anyInt());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var verifyWrong = executor.submit(() -> verifyOtp(id, wrong));
            try {
                await(beforeAttempt);
                assertThat(resend(id).getResponse().getStatus()).isEqualTo(200);
                OtpChallengeRecord current = challenge(id);
                assertThat(current.attempts()).isEqualTo(4);
                continueAttempt.countDown();
                assertThat(verifyWrong.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(400);
                assertThat(challenge(id).attempts()).as("stale wrong attempt must not lock the new OTP").isEqualTo(4);
                assertThat(challenge(id)).isEqualTo(current);
                assertThat(verifyOtp(id, deliveredCodes.getLast()).getResponse().getStatus()).isEqualTo(200);
            } finally {
                continueAttempt.countDown();
            }
        }
    }

    @Test
    void resendMustRejectWhenOtpAlreadyConsumed() throws Exception {
        String id = forgot();
        OtpChallengeRecord original = challenge(id);
        var consumed = new CountDownLatch(1);
        var finishVerify = new CountDownLatch(1);
        var resendReachedLock = new CountDownLatch(1);
        doAnswer(call -> {
            Object rows = call.callRealMethod();
            consumed.countDown();
            await(finishVerify);
            return rows;
        }).when(repository).markOtpUsedIfCurrent(eq(id), anyString(), anyInt());
        doAnswer(call -> {
            resendReachedLock.countDown();
            return call.callRealMethod();
        }).when(repository).findOtpChallengeByIdForUpdate(id);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var verify = executor.submit(() -> verifyOtp(id, deliveredCodes.getFirst()));
            try {
                await(consumed);
                var resend = executor.submit(() -> resend(id));
                await(resendReachedLock);
                finishVerify.countDown();
                assertThat(verify.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
                assertThat(resend.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(400);
            } finally {
                finishVerify.countDown();
            }
        }
        assertThat(challenge(id).usedAt()).isNotNull();
        assertThat(challenge(id).sends()).isEqualTo(original.sends());
        assertThat(challenge(id).codeHash()).isEqualTo(original.codeHash());
        assertThat(verifyOtp(id, deliveredCodes.getFirst()).getResponse().getStatus()).isEqualTo(400);
        verify(mail).sendOtpEmail(eq(EMAIL), anyString(), eq("RESET"), anyLong());
    }

    @org.junit.jupiter.api.RepeatedTest(3)
    void concurrentVerifyRequestsCanConsumeOtpOnlyOnce() throws Exception {
        String id = forgot();
        var atConsume = new CyclicBarrier(2);
        doAnswer(call -> {
            atConsume.await(10, TimeUnit.SECONDS);
            return call.callRealMethod();
        }).when(repository).markOtpUsedIfCurrent(eq(id), anyString(), anyInt());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> verifyOtp(id, deliveredCodes.getFirst()));
            var second = executor.submit(() -> verifyOtp(id, deliveredCodes.getFirst()));
            List<MvcResult> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().map(r -> r.getResponse().getStatus())).containsExactlyInAnyOrder(200, 400);
            int grants = 0;
            for (MvcResult result : results) if (body(result).containsKey("token")) grants++;
            assertThat(grants).isEqualTo(1);
        }
        assertThat(challenge(id).usedAt()).isNotNull();
        assertThat(challenge(id).sends()).isEqualTo(1);
    }

    @Test
    void oldOtpRemainsValidAfterFailedResendWhenVerifiedAgainstCurrentState() throws Exception {
        String id = forgot();
        OtpChallengeRecord before = challenge(id);
        failMail();
        assertThat(resend(id).getResponse().getStatus()).isEqualTo(200);
        assertThat(challenge(id).sends()).isEqualTo(2);
        assertThat(challenge(id).codeHash()).isEqualTo(before.codeHash());
        assertThat(challenge(id).expiresAt()).isEqualTo(before.expiresAt());
        assertThat(verifyOtp(id, deliveredCodes.getFirst()).getResponse().getStatus()).isEqualTo(200);
        assertThat(verifyOtp(id, deliveredCodes.getFirst()).getResponse().getStatus()).isEqualTo(400);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"HASH", "GENERATION"})
    void sameOtpHashAcrossDifferentResendGenerationsCannotConsumeStaleSnapshot(String changed) throws Exception {
        String id = forgot();
        OtpChallengeRecord old = challenge(id);
        String currentCode = deliveredCodes.getFirst();
        if (changed.equals("GENERATION")) {
            // Same hash, different generation: do not rely on random OTP collisions.
            update("UPDATE auth_otp_challenges SET sends = sends + 1 WHERE id = ?", id);
        } else {
            currentCode = currentCode.equals("000000") ? "111111" : "000000";
            update("UPDATE auth_otp_challenges SET code_hash = ? WHERE id = ?", tokens.hmacSha256(id + "|RESET|" + currentCode), id);
        }
        OtpChallengeRecord current = challenge(id);
        assertThat(repository.markOtpUsedIfCurrent(id, old.codeHash(), old.sends())).isZero();
        assertThat(repository.incrementOtpAttemptsIfCurrent(id, old.codeHash(), old.sends())).isZero();
        assertThat(challenge(id)).isEqualTo(current);
        assertThat(verifyOtp(id, currentCode).getResponse().getStatus()).isEqualTo(200);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"EXPIRED", "LOCKED", "USED"})
    void consumeRechecksExpiryAttemptsAndUsedAtAtSqlBoundary(String state) throws Exception {
        String id = forgot();
        var beforeConsume = new CountDownLatch(1);
        var continueConsume = new CountDownLatch(1);
        doAnswer(call -> {
            beforeConsume.countDown();
            await(continueConsume);
            return call.callRealMethod();
        }).when(repository).markOtpUsedIfCurrent(eq(id), anyString(), anyInt());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var verify = executor.submit(() -> verifyOtp(id, deliveredCodes.getFirst()));
            try {
                await(beforeConsume);
                switch (state) {
                    case "EXPIRED" -> update("UPDATE auth_otp_challenges SET created_at = DATE_SUB(NOW(6), INTERVAL 1 HOUR), expires_at = NOW(6) WHERE id = ?", id);
                    case "LOCKED" -> update("UPDATE auth_otp_challenges SET attempts = 5 WHERE id = ?", id);
                    case "USED" -> update("UPDATE auth_otp_challenges SET used_at = NOW(6) WHERE id = ?", id);
                    default -> throw new IllegalArgumentException(state);
                }
                OtpChallengeRecord current = challenge(id);
                continueConsume.countDown();
                MvcResult rejected = verify.get(10, TimeUnit.SECONDS);
                assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
                assertThat(body(rejected)).doesNotContainKey("token");
                assertThat(challenge(id)).isEqualTo(current);
                assertThat(repository.incrementOtpAttemptsIfCurrent(id, current.codeHash(), current.sends())).isZero();
                assertThat(challenge(id)).isEqualTo(current);
            } finally {
                continueConsume.countDown();
            }
        }
    }

    @Test
    void concurrentWrongAttemptsStayBoundedAndPersistOutsideOuterTransaction() throws Exception {
        String id = forgot();
        update("UPDATE auth_otp_challenges SET attempts = 3 WHERE id = ?", id);
        String wrong = deliveredCodes.getFirst().equals("000000") ? "111111" : "000000";
        // Three outer + three REQUIRES_NEW connections fit the configured pool.
        var atAttempt = new CyclicBarrier(3);
        doAnswer(call -> {
            atAttempt.await(10, TimeUnit.SECONDS);
            return call.callRealMethod();
        }).when(repository).incrementOtpAttemptsIfCurrent(eq(id), anyString(), anyInt());
        try (var executor = Executors.newFixedThreadPool(3)) {
            var first = executor.submit(() -> verifyOtp(id, wrong));
            var second = executor.submit(() -> verifyOtp(id, wrong));
            var third = executor.submit(() -> verifyOtp(id, wrong));
            for (var result : List.of(first, second, third)) {
                assertThat(result.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(400);
            }
        }
        assertThat(challenge(id).attempts()).isEqualTo(5);
        assertThat(verifyOtp(id, deliveredCodes.getFirst()).getResponse().getStatus()).isEqualTo(400);
        assertThat(challenge(id).usedAt()).isNull();
    }

    @Test
    void rotatedOtpCompletesRecoveryAndRevokesOldLoginAndGrant() throws Exception {
        MvcResult login = login("OldPassword@123");
        String oldToken = (String) body(login).get("token");
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String id = forgot();
        String codeA = deliveredCodes.getFirst();
        assertThat(resend(id).getResponse().getStatus()).isEqualTo(200);
        String codeB = deliveredCodes.getLast();
        // Equal random values are covered explicitly by the generation-CAS test.
        if (!codeA.equals(codeB)) assertThat(verifyOtp(id, codeA).getResponse().getStatus()).isEqualTo(400);
        MvcResult verified = verifyOtp(id, codeB);
        assertThat(verified.getResponse().getStatus()).isEqualTo(200);
        String grant = (String) body(verified).get("token");
        assertThat(verifyOtp(id, codeB).getResponse().getStatus()).isEqualTo(400);
        assertThat(reset(grant).getResponse().getStatus()).isEqualTo(200);
        assertThat(reset(grant).getResponse().getStatus()).isEqualTo(401);
        assertThat(login("OldPassword@123").getResponse().getStatus()).isEqualTo(401);
        assertThat(login("NewPassword@123").getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldToken)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void concurrentResetRequestsConsumeAuthVersionOnlyOnce() throws Exception {
        String id = forgot();
        String grant = (String) body(verifyOtp(id, deliveredCodes.getFirst())).get("token");
        var atPasswordUpdate = new CyclicBarrier(2);
        doAnswer(call -> {
            atPasswordUpdate.await(10, TimeUnit.SECONDS);
            return call.callRealMethod();
        }).when(repository).updateUserPassword(anyLong(), anyString(), eq(0L));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> reset(grant));
            var second = executor.submit(() -> reset(grant));
            assertThat(List.of(first.get(15, TimeUnit.SECONDS).getResponse().getStatus(), second.get(15, TimeUnit.SECONDS).getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 401);
        }
        assertThat(jdbc.queryForObject("SELECT auth_version FROM users WHERE username = 'otp_race'", Long.class)).isEqualTo(1L);
    }

    @Test
    void resetMustRejectIfUserBecomesInactiveBeforePasswordUpdate() throws Exception {
        String id = forgot();
        String grant = (String) body(verifyOtp(id, deliveredCodes.getFirst())).get("token");
        String originalHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'otp_race'", String.class);
        var beforeUpdate = new CountDownLatch(1);
        var continueUpdate = new CountDownLatch(1);
        doAnswer(call -> {
            beforeUpdate.countDown();
            await(continueUpdate);
            return call.callRealMethod();
        }).when(repository).updateUserPassword(anyLong(), anyString(), eq(0L));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var reset = executor.submit(() -> reset(grant));
            try {
                await(beforeUpdate);
                update("UPDATE users SET status = 'INACTIVE' WHERE username = 'otp_race'");
                continueUpdate.countDown();
                assertThat(reset.get(10, TimeUnit.SECONDS).getResponse().getStatus())
                        .as("password update must recheck current ACTIVE status").isEqualTo(401);
                assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'otp_race'", String.class)).isEqualTo(originalHash);
                assertThat(jdbc.queryForObject("SELECT auth_version FROM users WHERE username = 'otp_race'", Long.class)).isZero();
            } finally {
                continueUpdate.countDown();
            }
        }
    }

    private MvcResult reset(String grant) throws Exception {
        return request("reset-password", "{\"token\":\"" + grant + "\",\"newPassword\":\"NewPassword@123\",\"confirmPassword\":\"NewPassword@123\"}");
    }

    private MvcResult login(String password) throws Exception {
        return request("login", "{\"username\":\"otp_race\",\"password\":\"" + password + "\"}");
    }

    private void failMail() {
        doThrow(new IllegalStateException("fixture SMTP failure")).when(mail).sendOtpEmail(eq(EMAIL), anyString(), eq("RESET"), anyLong());
    }

    private String forgot() throws Exception {
        MvcResult result = request("forgot-password", "{\"email\":\"" + EMAIL + "\"}");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String id = (String) body(result).get("challengeId");
        update("UPDATE auth_otp_challenges SET next_send_at = DATE_SUB(NOW(6), INTERVAL 5 SECOND) WHERE id = ?", id);
        return id;
    }

    private OtpChallengeRecord challenge(String id) {
        return repository.findOtpChallengeById(id).orElseThrow();
    }

    private MvcResult verifyOtp(String id, String code) throws Exception {
        return request("verify-otp", "{\"challengeId\":\"" + id + "\",\"otp\":\"" + code + "\"}");
    }

    private MvcResult resend(String id) throws Exception {
        return request("resend-otp", "{\"challengeId\":\"" + id + "\"}");
    }

    private MvcResult request(String route, String body) throws Exception {
        return mvc.perform(post("/api/v1/auth/" + route).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
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

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class OtpRetryLifecycleIntegrationTest {
    private static final String REAL = "retry_lifecycle@smartcafe.vn";
    private static final String DUMMY = "retry_lifecycle_dummy@smartcafe.vn";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @MockitoBean EmailService mail;
    @MockitoBean Clock clock;
    @MockitoSpyBean AuthRepository repository;
    private Instant now;

    @BeforeEach
    void setup() {
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        when(clock.instant()).thenAnswer(invocation -> now);
        cleanup();
        update("""
                INSERT INTO users (username, password_hash, full_name, email, role, status)
                VALUES ('retry_lifecycle', 'unused-test-hash', 'Retry fixture', ?, 'STAFF', 'ACTIVE')
                """, REAL);
    }

    @AfterEach
    void cleanup() {
        update("DELETE FROM auth_otp_challenges WHERE email IN (?, ?)", REAL, DUMMY);
        for (String email : List.of(REAL, DUMMY)) {
            update("DELETE FROM auth_otp_limits WHERE email_key = ? AND purpose = 'RESET'",
                    tokens.hmacSha256("LIMIT:" + email));
        }
        update("DELETE FROM users WHERE username = 'retry_lifecycle'");
    }

    @Test
    void realAndDummyChallengesHaveIdenticalRetryLifecycleAfterRepeatedSmtpFailures() throws Exception {
        doThrow(new IllegalStateException("SMTP rejected delivery")).when(mail)
                .sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
        String real = forgot(REAL);
        String dummy = forgot(DUMMY);
        // Give both fixtures precisely the same expiry, well inside the original lifetime.
        update("UPDATE auth_otp_challenges SET expires_at = DATE_ADD(NOW(6), INTERVAL 300 SECOND) WHERE id IN (?, ?)", real, dummy);
        List<Integer> realStatuses = new ArrayList<>();
        List<Integer> dummyStatuses = new ArrayList<>();
        List<Integer> realSends = new ArrayList<>();
        List<Integer> dummySends = new ArrayList<>();
        List<MvcResult> realResults = new ArrayList<>();
        List<MvcResult> dummyResults = new ArrayList<>();
        List<Map<String, Object>> realStates = new ArrayList<>();
        List<Map<String, Object>> dummyStates = new ArrayList<>();
        Map<String, Object> realBefore = state(real);
        Map<String, Object> dummyBefore = state(dummy);
        for (int i = 0; i < 3; i++) {
            releaseCooldown(real, dummy);
            MvcResult realResult = resend(real);
            MvcResult dummyResult = resend(dummy);
            realStatuses.add(realResult.getResponse().getStatus());
            dummyStatuses.add(dummyResult.getResponse().getStatus());
            realSends.add((Integer) state(real).get("sends"));
            dummySends.add((Integer) state(dummy).get("sends"));
            realResults.add(realResult);
            dummyResults.add(dummyResult);
            realStates.add(state(real));
            dummyStates.add(state(dummy));
        }
        assertThat(realStatuses).as("real HTTP sequence; dummy=%s; sends real=%s dummy=%s",
                dummyStatuses, realSends, dummySends).containsExactly(200, 200, 400);
        assertThat(dummyStatuses).containsExactlyElementsOf(realStatuses);
        assertThat(realSends).containsExactly(2, 3, 3);
        assertThat(dummySends).containsExactlyElementsOf(realSends);
        for (int i = 0; i < 3; i++) {
            samePublicResponse(realResults.get(i), dummyResults.get(i));
            assertPreserved(realBefore, realStates.get(i));
            assertPreserved(dummyBefore, dummyStates.get(i));
        }
        verify(mail, times(3)).sendOtpEmail(eq(REAL), anyString(), eq("RESET"), anyLong());
        verify(mail, never()).sendOtpEmail(eq(DUMMY), anyString(), anyString(), anyLong());
    }

    @Test
    void smtpFailureConsumesResendBudgetWithoutInvalidatingPreviousOtp() throws Exception {
        List<String> codes = captureDelivery();
        String id = forgot(REAL);
        String original = codes.getFirst();
        update("UPDATE auth_otp_challenges SET attempts = 2 WHERE id = ?", id);
        Map<String, Object> before = state(id);
        doThrow(new IllegalStateException("SMTP failure")).when(mail)
                .sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
        releaseCooldown(id, id);
        assertThat(resend(id).getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> after = state(id);
        assertPreserved(before, after);
        assertThat(after.get("sends")).isEqualTo(2);
        assertThat(after.get("delivery_status")).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT next_send_at FROM auth_otp_challenges WHERE id = ?", Timestamp.class, id).toInstant()).isEqualTo(now.plusSeconds(60));
        assertThat(verifyOtp(id, original).getResponse().getStatus()).isEqualTo(200);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,true", "true,false", "false,false", "true,true"})
    void mixedSmtpFailureAndSuccessPreservesPublicRetryBudget(boolean firstSucceeds, boolean secondSucceeds) throws Exception {
        List<String> codes = captureDelivery();
        String real = forgot(REAL);
        String dummy = forgot(DUMMY);
        Object expiry = state(real).get("expires_at");
        String lastDelivered = codes.getFirst();
        for (boolean succeeds : new boolean[]{firstSucceeds, secondSucceeds}) {
            Map<String, Object> before = state(real);
            if (succeeds) {
                doAnswer(invocation -> { codes.add(invocation.getArgument(1)); return null; }).when(mail)
                        .sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
            } else {
                doThrow(new IllegalStateException("SMTP failure")).when(mail)
                        .sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
            }
            now = now.plusSeconds(60);
            MvcResult realResult = resend(real);
            samePublicResponse(realResult, resend(dummy));
            assertThat(realResult.getResponse().getStatus()).isEqualTo(200);
            if (succeeds) {
                lastDelivered = codes.getLast();
                assertThat(state(real).get("code_hash")).isEqualTo(tokens.hmacSha256(real + "|RESET|" + lastDelivered));
                assertThat(state(real).get("delivery_status")).isEqualTo("SENT");
                assertThat(jdbc.queryForObject("SELECT sent_at FROM auth_otp_challenges WHERE id = ?", Timestamp.class, real).toInstant()).isEqualTo(now);
            } else {
                assertPreserved(before, state(real));
            }
            assertThat(state(real).get("expires_at")).isEqualTo(expiry);
        }
        MvcResult rejected = resend(real);
        samePublicResponse(rejected, resend(dummy));
        assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
        assertThat(state(real).get("sends")).isEqualTo(3);
        assertThat(state(dummy).get("sends")).isEqualTo(3);
        verify(mail, times(3)).sendOtpEmail(eq(REAL), anyString(), eq("RESET"), anyLong());
        assertThat(verifyOtp(real, lastDelivered).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void realAndDummyCooldownBehaviorMatchesAfterMultipleResends() throws Exception {
        String real = forgot(REAL);
        String dummy = forgot(DUMMY);
        for (int i = 1; i <= 2; i++) {
            MvcResult blocked = resend(real);
            samePublicResponse(blocked, resend(dummy));
            assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
            assertThat(state(real).get("sends")).isEqualTo(i);
            assertThat(state(dummy).get("sends")).isEqualTo(i);
            now = now.plusSeconds(60);
            MvcResult accepted = resend(real);
            samePublicResponse(accepted, resend(dummy));
            assertThat(accepted.getResponse().getStatus()).isEqualTo(200);
            assertThat(publicBody(accepted).get("expiresInSeconds")).isEqualTo(600 - 60 * i);
        }
        verify(mail, times(3)).sendOtpEmail(eq(REAL), anyString(), eq("RESET"), anyLong());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {-1, 0, 1})
    void expiredRealAndDummyChallengesAreRejectedConsistently(long secondsFromExpiry) throws Exception {
        String real = forgot(REAL);
        String dummy = forgot(DUMMY);
        Map<String, Object> realBefore = state(real);
        Map<String, Object> dummyBefore = state(dummy);
        now = now.plusSeconds(600 + secondsFromExpiry);
        clearInvocations(mail);
        MvcResult result = resend(real);
        samePublicResponse(result, resend(dummy));
        assertThat(result.getResponse().getStatus()).isEqualTo(secondsFromExpiry < 0 ? 200 : 400);
        if (secondsFromExpiry >= 0) {
            assertThat(state(real)).isEqualTo(realBefore);
            assertThat(state(dummy)).isEqualTo(dummyBefore);
            verifyNoInteractions(mail);
            MvcResult verify = verifyOtp(real, "123456");
            samePublicResponse(verify, verifyOtp(dummy, "123456"));
            assertThat(verify.getResponse().getStatus()).isEqualTo(400);
        } else {
            assertThat(publicBody(result).get("expiresInSeconds")).isEqualTo(1);
            verify(mail).sendOtpEmail(eq(REAL), anyString(), eq("RESET"), eq(1L));
        }
    }

    @Test
    void dummyChallengeNeverIssuesPasswordResetGrant() throws Exception {
        String id = forgot(DUMMY);
        for (int i = 1; i <= 5; i++) {
            MvcResult result = verifyOtp(id, "123456");
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(publicBody(result)).doesNotContainKey("token");
            assertThat(state(id).get("attempts")).isEqualTo(i);
            if (i <= 2) {
                now = now.plusSeconds(60);
                assertThat(resend(id).getResponse().getStatus()).isEqualTo(200);
            }
        }
        assertThat(resend(id).getResponse().getStatus()).isEqualTo(400);
        assertThat(verifyOtp(id, "123456").getResponse().getStatus()).isEqualTo(400);
        now = now.plusSeconds(600);
        assertThat(verifyOtp(id, "123456").getResponse().getStatus()).isEqualTo(400);
        assertThat(state(id).get("used_at")).isNull();
        verify(mail, never()).sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void failedDatabaseTransitionRollsBackResend(boolean deliveryTransition, boolean sqlException) throws Exception {
        String id = forgot(REAL);
        releaseCooldown(id, id);
        Map<String, Object> before = state(id);
        clearInvocations(mail);
        RuntimeException failure = new org.springframework.dao.DataAccessResourceFailureException("fixture failure");
        if (deliveryTransition) {
            if (sqlException) {
                doThrow(failure).when(repository).recordOtpDelivery(eq(id), eq(2), anyString(), eq(true), any());
            } else {
                doReturn(0).when(repository).recordOtpDelivery(eq(id), eq(2), anyString(), eq(true), any());
            }
        } else if (sqlException) {
            doThrow(failure).when(repository).consumeOtpResend(eq(id), any(), any());
        } else {
            doReturn(0).when(repository).consumeOtpResend(eq(id), any(), any());
        }
        assertThat(resend(id).getResponse().getStatus()).isEqualTo(500);
        assertThat(state(id)).isEqualTo(before);
        verify(mail, times(deliveryTransition ? 1 : 0)).sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void concurrentRealResendsDeliverOnlyOneOtp(boolean delivered) throws Exception {
        List<String> codes = captureDelivery();
        String id = forgot(REAL);
        String original = codes.getFirst();
        now = now.plusSeconds(60);
        clearInvocations(mail);
        var enteredSmtp = new java.util.concurrent.CountDownLatch(1);
        var finishSmtp = new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation -> {
            codes.add(invocation.getArgument(1));
            enteredSmtp.countDown();
            assertThat(finishSmtp.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            if (!delivered) throw new IllegalStateException("SMTP failure");
            return null;
        }).when(mail).sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> resend(id));
            assertThat(enteredSmtp.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var secondStarted = new java.util.concurrent.CountDownLatch(1);
            var second = executor.submit(() -> { secondStarted.countDown(); return resend(id); });
            assertThat(secondStarted.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            finishSmtp.countDown();
            assertThat(first.get(15, java.util.concurrent.TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            assertThat(second.get(15, java.util.concurrent.TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(429);
        } finally {
            finishSmtp.countDown();
        }
        verify(mail).sendOtpEmail(eq(REAL), anyString(), eq("RESET"), anyLong());
        assertThat(state(id).get("sends")).isEqualTo(2);
        String valid = delivered ? codes.getLast() : original;
        assertThat(state(id).get("code_hash")).isEqualTo(tokens.hmacSha256(id + "|RESET|" + valid));
        assertThat(verifyOtp(id, valid).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void wrongOtpAndUsedChallengesHaveMatchingPublicErrors() throws Exception {
        List<String> codes = captureDelivery();
        String real = forgot(REAL);
        String dummy = forgot(DUMMY);
        String wrong = codes.getFirst().equals("000000") ? "111111" : "000000";
        for (int i = 1; i <= 6; i++) {
            MvcResult result = verifyOtp(real, wrong);
            samePublicResponse(result, verifyOtp(dummy, wrong));
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(state(real).get("attempts")).isEqualTo(Math.min(i, 5));
            assertThat(state(dummy).get("attempts")).isEqualTo(Math.min(i, 5));
        }
        update("UPDATE auth_otp_challenges SET used_at = NOW(6) WHERE id IN (?, ?)", real, dummy);
        clearInvocations(mail);
        MvcResult used = resend(real);
        samePublicResponse(used, resend(dummy));
        assertThat(used.getResponse().getStatus()).isEqualTo(400);
        assertThat(state(real).get("sends")).isEqualTo(1);
        assertThat(state(dummy).get("sends")).isEqualTo(1);
        verifyNoInteractions(mail);
    }

    private List<String> captureDelivery() {
        List<String> codes = new ArrayList<>();
        doAnswer(invocation -> { codes.add(invocation.getArgument(1)); return null; }).when(mail)
                .sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
        return codes;
    }

    private void assertPreserved(Map<String, Object> before, Map<String, Object> after) {
        for (String column : List.of("code_hash", "expires_at", "attempts", "used_at", "sent_at")) {
            assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
        }
    }

    private Map<String, Object> publicBody(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$");
    }

    private void samePublicResponse(MvcResult real, MvcResult dummy) throws Exception {
        assertThat(real.getResponse().getStatus()).isEqualTo(dummy.getResponse().getStatus());
        Map<String, Object> realBody = publicBody(real);
        Map<String, Object> dummyBody = publicBody(dummy);
        realBody.remove("challengeId");
        dummyBody.remove("challengeId");
        assertThat(realBody).isEqualTo(dummyBody);
        assertThat(real.getResponse().getHeaderNames()).isEqualTo(dummy.getResponse().getHeaderNames());
        for (String header : real.getResponse().getHeaderNames()) {
            assertThat(real.getResponse().getHeaders(header)).as(header).isEqualTo(dummy.getResponse().getHeaders(header));
        }
    }

    private MvcResult verifyOtp(String id, String otp) throws Exception {
        return request("verify-otp", "{\"challengeId\":\"" + id + "\",\"otp\":\"" + otp + "\"}");
    }

    private String forgot(String email) throws Exception {
        MvcResult result = request("forgot-password", "{\"email\":\"" + email + "\"}");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.challengeId");
    }

    private MvcResult resend(String id) throws Exception {
        return request("resend-otp", "{\"challengeId\":\"" + id + "\"}");
    }

    private MvcResult request(String route, String body) throws Exception {
        return mvc.perform(post("/api/v1/auth/" + route).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private Map<String, Object> state(String id) {
        return jdbc.queryForMap("SELECT * FROM auth_otp_challenges WHERE id = ?", id);
    }

    private void releaseCooldown(String real, String dummy) {
        update("UPDATE auth_otp_challenges SET next_send_at = DATE_SUB(NOW(6), INTERVAL 5 SECOND) WHERE id IN (?, ?)", real, dummy);
    }

    private int update(String sql, Object... args) {
        return TestDatabase.update(jdbc, sql, args);
    }
}

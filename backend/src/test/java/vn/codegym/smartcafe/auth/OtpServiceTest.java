package vn.codegym.smartcafe.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import vn.codegym.smartcafe.auth.dto.ForgotPasswordResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OtpServiceTest {

    private AuthRepository authRepository;
    private TokenService tokenService;
    private EmailService emailService;
    private PasswordEncoder passwordEncoder;
    private PlatformTransactionManager transactionManager;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        authRepository = mock(AuthRepository.class);
        tokenService = mock(TokenService.class);
        emailService = mock(EmailService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        transactionManager = mock(PlatformTransactionManager.class);

        when(tokenService.hmacSha256(anyString())).thenReturn("mocked-hash-value");
        when(tokenService.generateRandomHex(anyInt())).thenReturn("mocked-random-hex-id");

        otpService = new OtpService(
                authRepository,
                tokenService,
                emailService,
                passwordEncoder,
                transactionManager,
                Clock.systemUTC(),
                600
        );
    }

    private UserRecord createUser(Long id, String username, String email, String role, String status, long authVersion) {
        return new UserRecord(
                id,
                username,
                "password_hash",
                "Full Name",
                email,
                "0901234567",
                role,
                status,
                null,
                authVersion,
                Instant.now(),
                false
        );
    }

    private void allowInitialDispatch(UserRecord user) {
        Instant now = Instant.now();
        var challenge = new OtpChallengeRecord("mocked-random-hex-id", user.id(), user.email(), "RESET",
                "mocked-hash-value", 0, 1, now.plusSeconds(600), now.plusSeconds(60), null, "PENDING", now, null, now);
        when(authRepository.findOtpChallengeById(challenge.id())).thenReturn(Optional.of(challenge));
    }

    private OtpChallengeRecord reservedForResend(OtpChallengeRecord challenge) {
        return new OtpChallengeRecord(challenge.id(), challenge.userId(), challenge.email(), challenge.purpose(),
                challenge.codeHash(), challenge.attempts(), challenge.sends() + 1, challenge.expiresAt(),
                Instant.now().plusSeconds(60), null, "PENDING", challenge.createdAt(), null, challenge.sentAt());
    }

    @Test
    void resendOtpThrowsOtpExceptionAndNeverUpdatesDbWhenEmailServiceThrowsUnconfiguredSmtp() {
        String challengeId = "test-challenge-123";
        OtpChallengeRecord existingChallenge = new OtpChallengeRecord(
                challengeId,
                1L,
                "user@smartcafe.vn",
                "RESET",
                "initial-hash",
                0,
                1,
                Instant.now().plusSeconds(600),
                Instant.now().minusSeconds(10), // cooldown passed
                null,
                "SENT",
                Instant.now().minusSeconds(60),
                null,
                Instant.now().minusSeconds(60)
        );

        when(authRepository.findOtpChallengeById(challengeId)).thenReturn(Optional.of(existingChallenge));
        when(authRepository.findOtpChallengeByIdForUpdate(challengeId)).thenReturn(Optional.of(existingChallenge));
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(createUser(1L, "user", "user@smartcafe.vn", "STAFF", "ACTIVE", 0L)));
        when(authRepository.findUserByIdForUpdate(1L)).thenReturn(Optional.of(createUser(1L, "user", "user@smartcafe.vn", "STAFF", "ACTIVE", 0L)));
        doThrow(new IllegalStateException("Dịch vụ gửi email chưa được cấu hình máy chủ SMTP."))
                .when(emailService).validateReady();

        assertThatThrownBy(() -> otpService.resendOtp(challengeId))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Không thể gửi email OTP qua dịch vụ gửi thư");

        // Readiness failure is rejected before consuming any budget.
        verify(authRepository, never()).consumeOtpResend(anyString(), any(), any());
    }

    @Test
    void resendOtpPreservesOriginalOtpAndReturnsNeutralResponseWhenSmtpDeliveryFails() {
        String challengeId = "test-delivery-fail-123";
        OtpChallengeRecord existingChallenge = new OtpChallengeRecord(
                challengeId,
                1L,
                "user@smartcafe.vn",
                "RESET",
                "initial-hash",
                0,
                1,
                Instant.now().plusSeconds(600),
                Instant.now().minusSeconds(10), // cooldown passed
                null,
                "SENT",
                Instant.now().minusSeconds(60),
                null,
                Instant.now().minusSeconds(60)
        );

        when(authRepository.findOtpChallengeById(challengeId)).thenReturn(Optional.of(reservedForResend(existingChallenge)));
        when(authRepository.findOtpChallengeByIdForUpdate(challengeId)).thenReturn(Optional.of(existingChallenge));
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(createUser(1L, "user", "user@smartcafe.vn", "STAFF", "ACTIVE", 0L)));
        when(authRepository.findUserByIdForUpdate(1L)).thenReturn(Optional.of(createUser(1L, "user", "user@smartcafe.vn", "STAFF", "ACTIVE", 0L)));
        doThrow(new RuntimeException("SMTP delivery failed with 451"))
                .when(emailService).sendOtpEmail(eq("user@smartcafe.vn"), anyString(), eq("RESET"), anyLong());

        when(authRepository.consumeOtpResend(eq(challengeId), any(), any())).thenReturn(1);
        when(authRepository.recordOtpDelivery(eq(challengeId), eq(2), anyString(), eq(false), any())).thenReturn(1);
        ForgotPasswordResponse response = otpService.resendOtp(challengeId);

        assertThat(response).isNotNull();
        assertThat(response.challengeId()).isEqualTo(challengeId);
        assertThat(response.message()).contains("Yêu cầu gửi lại mã xác minh đã được tiếp nhận");

        // Delivery failure consumes one request without installing the undelivered hash.
        verify(authRepository).consumeOtpResend(eq(challengeId), any(), any());
        verify(authRepository).recordOtpDelivery(eq(challengeId), eq(2), anyString(), eq(false), any());
    }

    @Test
    void resendOtpForDummyChallengeThrowsOtpExceptionWhenMailServiceFails() {
        String challengeId = "dummy-challenge-456";
        OtpChallengeRecord dummyChallenge = new OtpChallengeRecord(
                challengeId,
                null,
                "ghost@smartcafe.vn",
                "REGISTER",
                "dummy-hash",
                0,
                1,
                Instant.now().plusSeconds(600),
                Instant.now().minusSeconds(10), // cooldown passed
                null,
                "PENDING",
                Instant.now().minusSeconds(60),
                null,
                Instant.now().minusSeconds(60)
        );

        when(authRepository.findOtpChallengeById(challengeId)).thenReturn(Optional.of(dummyChallenge));
        when(authRepository.findOtpChallengeByIdForUpdate(challengeId)).thenReturn(Optional.of(dummyChallenge));
        doThrow(new RuntimeException("Máy chủ SMTP hiện không phản hồi. Vui lòng thử lại sau."))
                .when(emailService).validateReady();

        assertThatThrownBy(() -> otpService.resendOtp(challengeId))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Không thể gửi email OTP qua dịch vụ gửi thư");

        // Apply the same readiness policy to dummy challenges.
        verify(authRepository, never()).consumeOtpResend(anyString(), any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void failedDatabaseTransitionDoesNotSilentlyReportSuccessfulResend(boolean deliveryTransition, boolean sqlException) {
        String id = "database-failure";
        OtpChallengeRecord challenge = new OtpChallengeRecord(
                id, 1L, "fixture@smartcafe.vn", "RESET", "old-hash", 2, 1,
                Instant.now().plusSeconds(300), Instant.now().minusSeconds(5), null,
                "SENT", Instant.now().minusSeconds(300), null, Instant.now().minusSeconds(300));
        when(authRepository.findOtpChallengeById(id)).thenReturn(Optional.of(reservedForResend(challenge)));
        when(authRepository.findOtpChallengeByIdForUpdate(id)).thenReturn(Optional.of(challenge));
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(createUser(1L, "fixture", "fixture@smartcafe.vn", "STAFF", "ACTIVE", 0L)));
        when(authRepository.findUserByIdForUpdate(1L)).thenReturn(Optional.of(createUser(1L, "fixture", "fixture@smartcafe.vn", "STAFF", "ACTIVE", 0L)));

        RuntimeException failure = new org.springframework.dao.DataAccessResourceFailureException("test database unavailable");
        if (deliveryTransition) {
            when(authRepository.consumeOtpResend(eq(id), any(), any())).thenReturn(1);
            if (sqlException) {
                when(authRepository.recordOtpDelivery(eq(id), eq(2), anyString(), eq(true), any())).thenThrow(failure);
            }
        } else if (sqlException) {
            when(authRepository.consumeOtpResend(eq(id), any(), any())).thenThrow(failure);
        }
        assertThatThrownBy(() -> otpService.resendOtp(id))
                .isInstanceOf(sqlException ? org.springframework.dao.DataAccessException.class : IllegalStateException.class);
        verify(emailService, times(deliveryTransition ? 1 : 0)).sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
        verify(authRepository, times(deliveryTransition ? 1 : 0))
                .recordOtpDelivery(eq(id), eq(2), anyString(), eq(true), any());
    }

    @Test
    void initiatePasswordResetKeepsNeutralResponseWhenEmailServiceThrowsUnconfiguredSmtp() {
        String email = "existing@smartcafe.vn";
        UserRecord user = createUser(1L, "existing_user", email, "STAFF", "ACTIVE", 0L);

        when(authRepository.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(user));
        when(authRepository.findUserByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(authRepository.checkAndIncrementLimit(anyString(), eq("RESET"), any(), any(), any())).thenReturn(true);
        allowInitialDispatch(user);
        doThrow(new IllegalStateException("Dịch vụ gửi email chưa được cấu hình máy chủ SMTP."))
                .when(emailService).sendOtpEmail(eq(email), anyString(), eq("RESET"), anyLong());

        ForgotPasswordResponse response = otpService.initiatePasswordResetOtp(email);

        assertThat(response).isNotNull();
        assertThat(response.challengeId()).isNotEmpty();
        assertThat(response.message()).contains("Yêu cầu gửi mã xác minh đã được tiếp nhận");

        // Verify challenge was saved even though sending threw, maintaining user enumeration protection
        verify(authRepository, times(1)).saveOtpChallenge(any());
        verify(emailService).sendOtpEmail(eq(email), anyString(), eq("RESET"), anyLong());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 2})
    void verifyNeverIssuesGrantUnlessExactlyOneCurrentSnapshotIsConsumed(int rows) {
        OtpChallengeRecord snapshot = new OtpChallengeRecord("verify-cas", 1L, "fixture@example.test",
                "RESET", "mocked-hash-value", 0, 2, Instant.now().plusSeconds(300),
                Instant.now(), null, "SENT", Instant.now().minusSeconds(300), null, Instant.now());
        when(authRepository.findOtpChallengeById(snapshot.id())).thenReturn(Optional.of(snapshot));
        when(authRepository.findOtpChallengeByIdForUpdate(snapshot.id())).thenReturn(Optional.of(snapshot));
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(createUser(1L, "fixture", "fixture@example.test", "STAFF", "ACTIVE", 0L)));
        when(authRepository.findUserByIdForUpdate(1L)).thenReturn(Optional.of(createUser(1L, "fixture", "fixture@example.test", "STAFF", "ACTIVE", 0L)));
        when(authRepository.markOtpUsedIfCurrent(snapshot.id(), snapshot.codeHash(), snapshot.sends())).thenReturn(rows);
        assertThatThrownBy(() -> otpService.verifyOtp(snapshot.id(), "123456")).isInstanceOf(OtpException.class);
        verify(authRepository).markOtpUsedIfCurrent(snapshot.id(), snapshot.codeHash(), snapshot.sends());
        verify(authRepository, never()).findUserById(anyLong());
        verify(tokenService, never()).generateResetGrantToken(anyString(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void verifyDoesNotConvertConsumeDatabaseFailureIntoSuccess() {
        OtpChallengeRecord snapshot = new OtpChallengeRecord("verify-db-failure", 1L, "fixture@example.test",
                "RESET", "mocked-hash-value", 0, 1, Instant.now().plusSeconds(300),
                Instant.now(), null, "SENT", Instant.now().minusSeconds(300), null, Instant.now());
        when(authRepository.findOtpChallengeById(snapshot.id())).thenReturn(Optional.of(snapshot));
        when(authRepository.findOtpChallengeByIdForUpdate(snapshot.id())).thenReturn(Optional.of(snapshot));
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(createUser(1L, "fixture", "fixture@example.test", "STAFF", "ACTIVE", 0L)));
        when(authRepository.findUserByIdForUpdate(1L)).thenReturn(Optional.of(createUser(1L, "fixture", "fixture@example.test", "STAFF", "ACTIVE", 0L)));
        when(authRepository.markOtpUsedIfCurrent(snapshot.id(), snapshot.codeHash(), snapshot.sends()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("fixture database failure"));
        assertThatThrownBy(() -> otpService.verifyOtp(snapshot.id(), "123456"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        verify(tokenService, never()).generateResetGrantToken(anyString(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void initiatePasswordResetCreatesDummyChallengeForNonExistentEmailWithoutSendingEmail() {
        String email = "ghost@smartcafe.vn";
        when(authRepository.findUserByEmail(email)).thenReturn(Optional.empty());
        when(authRepository.checkAndIncrementLimit(anyString(), eq("RESET"), any(), any(), any())).thenReturn(true);

        ForgotPasswordResponse response = otpService.initiatePasswordResetOtp(email);

        assertThat(response).isNotNull();
        assertThat(response.challengeId()).isNotEmpty();
        assertThat(response.message()).contains("Yêu cầu gửi mã xác minh đã được tiếp nhận");

        // Verify dummy challenge saved to DB with null userId
        verify(authRepository, times(1)).saveOtpChallenge(argThat(c -> c.userId() == null && c.email().equals(email)));

        // Verify NO email was sent
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void initiatePasswordResetCreatesDummyChallengeWhenUserEmailWasChangedUnderLock() {
        String oldEmail = "old@smartcafe.vn";
        UserRecord userWithNewEmail = createUser(1L, "user1", "new@smartcafe.vn", "STAFF", "ACTIVE", 1L);

        when(authRepository.findUserByEmail(oldEmail)).thenReturn(Optional.of(createUser(1L, "user1", oldEmail, "STAFF", "ACTIVE", 0L)));
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(userWithNewEmail));
        when(authRepository.findUserByIdForUpdate(1L)).thenReturn(Optional.of(userWithNewEmail));
        when(authRepository.checkAndIncrementLimit(anyString(), eq("RESET"), any(), any(), any())).thenReturn(true);

        ForgotPasswordResponse response = otpService.initiatePasswordResetOtp(oldEmail);

        assertThat(response).isNotNull();
        assertThat(response.challengeId()).isNotEmpty();
        // Verifies dummy challenge is saved with null userId and purpose REGISTER
        verify(authRepository).saveOtpChallenge(argThat(c -> c.userId() == null && "REGISTER".equals(c.purpose())));
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void verifyOtpRejectsWhenUserEmailNoLongerMatchesChallengeEmail() {
        String challengeId = "ch-verify-mismatch";
        OtpChallengeRecord challenge = new OtpChallengeRecord(
                challengeId,
                1L,
                "old@smartcafe.vn",
                "RESET",
                "mocked-hash-value",
                0,
                1,
                Instant.now().plusSeconds(300),
                Instant.now(),
                null,
                "SENT",
                Instant.now().minusSeconds(60),
                null,
                Instant.now()
        );

        when(authRepository.findOtpChallengeById(challengeId)).thenReturn(Optional.of(challenge));
        when(authRepository.findOtpChallengeByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(authRepository.markOtpUsedIfCurrent(eq(challengeId), anyString(), eq(1))).thenReturn(1);
        // User email changed to new@smartcafe.vn
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(createUser(1L, "user1", "new@smartcafe.vn", "STAFF", "ACTIVE", 1L)));
        when(authRepository.findUserByIdForUpdate(1L)).thenReturn(Optional.of(createUser(1L, "user1", "new@smartcafe.vn", "STAFF", "ACTIVE", 1L)));

        // Expect OtpException because email no longer matches
        assertThatThrownBy(() -> otpService.verifyOtp(challengeId, "123456"))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Yêu cầu xác minh không hợp lệ hoặc phiên đã kết thúc.");

        verify(tokenService, never()).generateResetGrantToken(anyString(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void resendOtpRejectsWhenUserEmailNoLongerMatchesChallengeEmail() {
        String challengeId = "ch-resend-mismatch";
        OtpChallengeRecord challenge = new OtpChallengeRecord(
                challengeId,
                1L,
                "old@smartcafe.vn",
                "RESET",
                "mocked-hash-value",
                0,
                1,
                Instant.now().plusSeconds(300),
                Instant.now().minusSeconds(10),
                null,
                "SENT",
                Instant.now().minusSeconds(60),
                null,
                Instant.now()
        );

        when(authRepository.findOtpChallengeById(challengeId)).thenReturn(Optional.of(challenge));
        when(authRepository.findOtpChallengeByIdForUpdate(challengeId)).thenReturn(Optional.of(challenge));
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(createUser(1L, "user1", "new@smartcafe.vn", "STAFF", "ACTIVE", 1L)));
        when(authRepository.findUserByIdForUpdate(1L)).thenReturn(Optional.of(createUser(1L, "user1", "new@smartcafe.vn", "STAFF", "ACTIVE", 1L)));

        assertThatThrownBy(() -> otpService.resendOtp(challengeId))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Yêu cầu xác minh không hợp lệ hoặc phiên đã kết thúc.");

        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void resetPasswordRejectsWhenUserEmailNoLongerMatchesChallengeEmail() {
        String rawToken = "valid.reset.grant.token";
        TokenService.ResetGrantClaims claims = new TokenService.ResetGrantClaims("ch-1", 1L, 0L, Instant.now().plusSeconds(300));
        when(tokenService.parseResetGrantToken(rawToken)).thenReturn(claims);

        OtpChallengeRecord challenge = new OtpChallengeRecord(
                "ch-1",
                1L,
                "old@smartcafe.vn",
                "RESET",
                "mocked-hash-value",
                0,
                1,
                Instant.now().plusSeconds(300),
                Instant.now(),
                Instant.now(), // usedAt is set
                "SENT",
                Instant.now().minusSeconds(60),
                null,
                Instant.now()
        );

        when(authRepository.findOtpChallengeById("ch-1")).thenReturn(Optional.of(challenge));
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(createUser(1L, "user1", "new@smartcafe.vn", "STAFF", "ACTIVE", 1L)));
        when(authRepository.findUserByIdForUpdate(1L)).thenReturn(Optional.of(createUser(1L, "user1", "new@smartcafe.vn", "STAFF", "ACTIVE", 1L)));

        assertThatThrownBy(() -> otpService.resetPassword(rawToken, "NewPass123@", "NewPass123@"))
                .isInstanceOf(InvalidTokenException.class);

        verify(authRepository, never()).updateUserPassword(anyLong(), anyString(), anyLong());
    }

    @Test
    void verifyResetTokenReturnsFalseWhenUserEmailNoLongerMatchesChallengeEmail() {
        String rawToken = "valid.reset.grant.token";
        TokenService.ResetGrantClaims claims = new TokenService.ResetGrantClaims("ch-1", 1L, 0L, Instant.now().plusSeconds(300));
        when(tokenService.parseResetGrantToken(rawToken)).thenReturn(claims);

        OtpChallengeRecord challenge = new OtpChallengeRecord(
                "ch-1",
                1L,
                "old@smartcafe.vn",
                "RESET",
                "mocked-hash-value",
                0,
                1,
                Instant.now().plusSeconds(300),
                Instant.now(),
                Instant.now(), // usedAt is set
                "SENT",
                Instant.now().minusSeconds(60),
                null,
                Instant.now()
        );

        when(authRepository.findOtpChallengeById("ch-1")).thenReturn(Optional.of(challenge));
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(createUser(1L, "user1", "new@smartcafe.vn", "STAFF", "ACTIVE", 0L)));

        boolean valid = otpService.verifyResetToken(rawToken);
        assertThat(valid).isFalse();
    }

    @Test
    void initiatePasswordResetDispatchesEmailSuccessfullyForValidUser() {
        String email = "staff_member@smartcafe.vn";
        UserRecord user = createUser(2L, "staff_member", email, "STAFF", "ACTIVE", 0L);

        when(authRepository.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(authRepository.findUserById(2L)).thenReturn(Optional.of(user));
        when(authRepository.findUserByIdForUpdate(2L)).thenReturn(Optional.of(user));
        when(authRepository.checkAndIncrementLimit(anyString(), eq("RESET"), any(), any(), any())).thenReturn(true);
        allowInitialDispatch(user);

        ForgotPasswordResponse response = otpService.initiatePasswordResetOtp(email);

        assertThat(response).isNotNull();
        assertThat(response.challengeId()).isNotEmpty();
        verify(authRepository).saveOtpChallenge(argThat(c -> c.userId() != null && c.userId().equals(2L) && "RESET".equals(c.purpose())));
        verify(emailService, times(1)).sendOtpEmail(eq(email), anyString(), eq("RESET"), anyLong());
    }

    @Test
    void resendOtpSkipsEmailDispatchWhenChallengeRevokedBetweenReserveAndDispatch() {
        String challengeId = "ch-revoked-before-dispatch";
        OtpChallengeRecord initial = new OtpChallengeRecord(
                challengeId, 1L, "user@smartcafe.vn", "RESET", "initial-hash", 0, 1,
                Instant.now().plusSeconds(600), Instant.now().minusSeconds(10), null,
                "PENDING", Instant.now().minusSeconds(60), null, null);
        OtpChallengeRecord revokedAfterReserve = new OtpChallengeRecord(
                challengeId, 1L, "user@smartcafe.vn", "RESET", "initial-hash", 0, 2,
                Instant.now().plusSeconds(600), Instant.now().plusSeconds(60), Instant.now(),
                "PENDING", Instant.now().minusSeconds(60), null, null);
        UserRecord activeUser = createUser(1L, "user", "user@smartcafe.vn", "STAFF", "ACTIVE", 0L);

        when(authRepository.findOtpChallengeByIdForUpdate(challengeId)).thenReturn(Optional.of(initial));
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(activeUser));
        when(authRepository.consumeOtpResend(eq(challengeId), any(), any())).thenReturn(1);
        when(authRepository.findOtpChallengeById(challengeId)).thenReturn(Optional.of(revokedAfterReserve));

        ForgotPasswordResponse response = otpService.resendOtp(challengeId);

        assertThat(response.challengeId()).isEqualTo(challengeId);
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
        verify(authRepository, never()).recordOtpDelivery(anyString(), anyInt(), anyString(), anyBoolean(), any());
    }

    @Test
    void resendOtpFinalizesSafelyAsNoOpWhenRevokedDuringSmtpDispatch() {
        String challengeId = "ch-revoked-during-dispatch";
        OtpChallengeRecord initial = new OtpChallengeRecord(
                challengeId, 1L, "user@smartcafe.vn", "RESET", "initial-hash", 0, 1,
                Instant.now().plusSeconds(600), Instant.now().minusSeconds(10), null,
                "PENDING", Instant.now().minusSeconds(60), null, null);
        OtpChallengeRecord reserved = new OtpChallengeRecord(
                challengeId, 1L, "user@smartcafe.vn", "RESET", "initial-hash", 0, 2,
                Instant.now().plusSeconds(600), Instant.now().plusSeconds(60), null,
                "PENDING", Instant.now().minusSeconds(60), null, null);
        OtpChallengeRecord revokedDuringSmtp = new OtpChallengeRecord(
                challengeId, 1L, "user@smartcafe.vn", "RESET", "initial-hash", 0, 2,
                Instant.now().plusSeconds(600), Instant.now().plusSeconds(60), Instant.now(),
                "PENDING", Instant.now().minusSeconds(60), null, null);
        UserRecord activeUser = createUser(1L, "user", "user@smartcafe.vn", "STAFF", "ACTIVE", 0L);

        when(authRepository.findOtpChallengeByIdForUpdate(challengeId))
                .thenReturn(Optional.of(initial))
                .thenReturn(Optional.of(revokedDuringSmtp));
        when(authRepository.findOtpChallengeById(challengeId)).thenReturn(Optional.of(reserved));
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(activeUser));
        when(authRepository.consumeOtpResend(eq(challengeId), any(), any())).thenReturn(1);
        when(authRepository.recordOtpDelivery(eq(challengeId), eq(2), anyString(), eq(true), any())).thenReturn(0);

        ForgotPasswordResponse response = otpService.resendOtp(challengeId);

        assertThat(response.challengeId()).isEqualTo(challengeId);
        verify(emailService, times(1)).sendOtpEmail(eq("user@smartcafe.vn"), anyString(), eq("RESET"), anyLong());
        verify(authRepository, times(1)).recordOtpDelivery(eq(challengeId), eq(2), anyString(), eq(true), any());
    }

    @Test
    void resendOtpStillAdvancesExpectedSendsWhenNewOtpCollidesWithPreviousOtp() {
        String challengeId = "ch-same-otp-collision";
        String fixedOtp = "123456";
        OtpService deterministicService = new OtpService(
                authRepository, tokenService, emailService, passwordEncoder,
                transactionManager, Clock.systemUTC(), 600) {
            @Override
            String generateNumericOtp() {
                return fixedOtp;
            }
        };
        OtpChallengeRecord initial = new OtpChallengeRecord(
                challengeId, 1L, "user@smartcafe.vn", "RESET", "mocked-hash-value", 0, 1,
                Instant.now().plusSeconds(600), Instant.now().minusSeconds(10), null,
                "PENDING", Instant.now().minusSeconds(60), null, null);
        UserRecord activeUser = createUser(1L, "user", "user@smartcafe.vn", "STAFF", "ACTIVE", 0L);

        when(authRepository.findOtpChallengeByIdForUpdate(challengeId)).thenReturn(Optional.of(initial));
        when(authRepository.findOtpChallengeById(challengeId)).thenReturn(Optional.of(reservedForResend(initial)));
        when(authRepository.findUserById(1L)).thenReturn(Optional.of(activeUser));
        when(authRepository.consumeOtpResend(eq(challengeId), any(), any())).thenReturn(1);
        when(authRepository.recordOtpDelivery(eq(challengeId), eq(2), eq("mocked-hash-value"), eq(true), any())).thenReturn(1);

        deterministicService.resendOtp(challengeId);

        verify(emailService).sendOtpEmail(eq("user@smartcafe.vn"), eq(fixedOtp), eq("RESET"), anyLong());
        verify(authRepository).recordOtpDelivery(eq(challengeId), eq(2), eq("mocked-hash-value"), eq(true), any());
    }
}

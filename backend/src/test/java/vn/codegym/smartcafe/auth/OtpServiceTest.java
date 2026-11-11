package vn.codegym.smartcafe.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import vn.codegym.smartcafe.auth.dto.ForgotPasswordResponse;

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
                600
        );
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
        doThrow(new IllegalStateException("Dịch vụ gửi email chưa được cấu hình máy chủ SMTP."))
                .when(emailService).validateReady();

        assertThatThrownBy(() -> otpService.resendOtp(challengeId))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Không thể gửi email OTP qua dịch vụ gửi thư");

        // Crucial verification: updateOtpResend must NEVER be called when email sending fails
        verify(authRepository, never()).updateOtpResend(anyString(), anyString(), any(), any());
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

        when(authRepository.findOtpChallengeById(challengeId)).thenReturn(Optional.of(existingChallenge));
        when(authRepository.findOtpChallengeByIdForUpdate(challengeId)).thenReturn(Optional.of(existingChallenge));
        doThrow(new RuntimeException("SMTP delivery failed with 451"))
                .when(emailService).sendOtpEmail(eq("user@smartcafe.vn"), anyString(), eq("RESET"), anyLong());

        ForgotPasswordResponse response = otpService.resendOtp(challengeId);

        assertThat(response).isNotNull();
        assertThat(response.challengeId()).isEqualTo(challengeId);
        assertThat(response.message()).contains("Yêu cầu gửi lại mã xác minh đã được tiếp nhận");

        // Crucial verification: updateOtpResend must NEVER be called, but updateOtpCooldownOnly is called
        verify(authRepository, never()).updateOtpResend(anyString(), anyString(), any(), any());
        verify(authRepository, times(1)).updateOtpCooldownOnly(eq(challengeId), any());
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

        when(authRepository.findOtpChallengeByIdForUpdate(challengeId)).thenReturn(Optional.of(dummyChallenge));
        doThrow(new RuntimeException("Máy chủ SMTP hiện không phản hồi. Vui lòng thử lại sau."))
                .when(emailService).validateReady();

        assertThatThrownBy(() -> otpService.resendOtp(challengeId))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Không thể gửi email OTP qua dịch vụ gửi thư");

        // Crucial verification: updateOtpResend must NEVER be called for dummy when email service fails
        verify(authRepository, never()).updateOtpResend(anyString(), anyString(), any(), any());
    }

    @Test
    void initiatePasswordResetKeepsNeutralResponseWhenEmailServiceThrowsUnconfiguredSmtp() {
        String email = "existing@smartcafe.vn";
        UserRecord user = new UserRecord(
                1L,
                "existing_user",
                "hashed_pwd",
                "Existing User",
                email,
                "0901234567",
                "STAFF",
                "ACTIVE",
                null,
                0L,
                Instant.now()
        );

        when(authRepository.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(authRepository.checkAndIncrementLimit(anyString(), eq("RESET"), any(), any(), any())).thenReturn(true);
        doThrow(new IllegalStateException("Dịch vụ gửi email chưa được cấu hình máy chủ SMTP."))
                .when(emailService).sendOtpEmail(eq(email), anyString(), eq("RESET"), anyLong());

        ForgotPasswordResponse response = otpService.initiatePasswordResetOtp(email);

        assertThat(response).isNotNull();
        assertThat(response.challengeId()).isNotEmpty();
        assertThat(response.message()).contains("Yêu cầu gửi mã xác minh đã được tiếp nhận");

        // Verify challenge was saved even though sending threw, maintaining user enumeration protection
        verify(authRepository, times(1)).saveOtpChallenge(any());
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
}

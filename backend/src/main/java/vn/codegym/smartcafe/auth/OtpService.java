package vn.codegym.smartcafe.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.codegym.smartcafe.auth.dto.ForgotPasswordResponse;
import vn.codegym.smartcafe.auth.dto.VerifyOtpResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SENDS = 3; // 1 initial send + max 2 resends
    private static final long COOLDOWN_SECONDS = 60;

    private final AuthRepository authRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final long otpValiditySeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(
            AuthRepository authRepository,
            TokenService tokenService,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            @Value("${smartcafe.auth.otp-validity-seconds:600}") long otpValiditySeconds
    ) {
        this.authRepository = authRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.otpValiditySeconds = otpValiditySeconds;
    }

    @Transactional
    public ForgotPasswordResponse initiatePasswordResetOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        String emailKey = tokenService.hmacSha256("LIMIT:" + normalizedEmail);
        Instant now = Instant.now();
        Instant nextSendAt = now.plusSeconds(COOLDOWN_SECONDS);

        boolean allowed = authRepository.checkAndIncrementLimit(emailKey, "RESET", now, now, nextSendAt);
        if (!allowed) {
            throw new OtpException("Bạn đã gửi yêu cầu quá nhiều lần. Vui lòng đợi trong giây lát rồi thử lại.", COOLDOWN_SECONDS);
        }

        String challengeId = tokenService.generateRandomHex(32);
        Optional<UserRecord> userOpt = authRepository.findUserByEmail(normalizedEmail);

        if (userOpt.isPresent() && "ACTIVE".equalsIgnoreCase(userOpt.get().status())) {
            UserRecord user = userOpt.get();
            String otpCode = generateNumericOtp();
            String codeHash = tokenService.hmacSha256(challengeId + "|RESET|" + otpCode);
            Instant expiresAt = now.plusSeconds(otpValiditySeconds);

            OtpChallengeRecord challenge = new OtpChallengeRecord(
                    challengeId,
                    user.id(),
                    normalizedEmail,
                    "RESET",
                    codeHash,
                    0,
                    1,
                    expiresAt,
                    nextSendAt,
                    null,
                    "PENDING",
                    now,
                    null,
                    now
            );
            authRepository.saveOtpChallenge(challenge);

            try {
                emailService.sendOtpEmail(normalizedEmail, otpCode, "RESET", otpValiditySeconds);
            } catch (Exception ex) {
                log.error("Failed to send OTP to recipient: {}", ex.getMessage());
            }
        } else {
            // Neutral behavior: record a dummy challenge or just return neutral response to prevent account enumeration
            log.info("Password reset requested for unknown/inactive email address.");
        }

        return new ForgotPasswordResponse(
                challengeId,
                "Yêu cầu gửi mã xác minh đã được tiếp nhận. Nếu email tồn tại trong hệ thống, bạn sẽ nhận được mã OTP trong hộp thư.",
                COOLDOWN_SECONDS,
                otpValiditySeconds
        );
    }

    @Transactional
    public VerifyOtpResponse verifyOtp(String challengeId, String inputOtp) {
        if (inputOtp == null || !inputOtp.matches("^[0-9]{6}$")) {
            throw new OtpException("Mã OTP phải gồm đúng 6 chữ số.");
        }

        OtpChallengeRecord challenge = authRepository.findOtpChallengeById(challengeId)
                .orElseThrow(() -> new OtpException("Yêu cầu xác minh không hợp lệ hoặc phiên đã kết thúc."));

        if (challenge.usedAt() != null) {
            throw new OtpException("Mã OTP này đã được sử dụng. Vui lòng yêu cầu mã mới.");
        }

        Instant now = Instant.now();
        if (now.isAfter(challenge.expiresAt())) {
            throw new OtpException("Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới.");
        }

        if (challenge.attempts() >= MAX_ATTEMPTS) {
            throw new OtpException("Bạn đã nhập sai quá 5 lần. Mã OTP đã bị vô hiệu hóa.");
        }

        authRepository.incrementOtpAttempts(challengeId);

        String expectedHash = tokenService.hmacSha256(challengeId + "|" + challenge.purpose() + "|" + inputOtp.trim());
        boolean isMatch = MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                challenge.codeHash().getBytes(StandardCharsets.UTF_8)
        );

        if (!isMatch) {
            int remaining = MAX_ATTEMPTS - (challenge.attempts() + 1);
            if (remaining <= 0) {
                throw new OtpException("Bạn đã nhập sai quá 5 lần. Mã OTP đã bị vô hiệu hóa.");
            }
            throw new OtpException("Mã OTP không chính xác. Bạn còn " + remaining + " lần thử.");
        }

        authRepository.markOtpUsed(challengeId);

        if ("RESET".equalsIgnoreCase(challenge.purpose())) {
            if (challenge.userId() == null) {
                throw new OtpException("Không tìm thấy thông tin tài khoản liên kết.");
            }
            String rawGrantToken = tokenService.generateRandomHex(32);
            String tokenHash = sha256(rawGrantToken);
            Instant tokenExpiresAt = now.plusSeconds(otpValiditySeconds);

            authRepository.savePasswordResetToken(challenge.userId(), tokenHash, tokenExpiresAt);
            return new VerifyOtpResponse("RESET", rawGrantToken, "Xác minh OTP thành công. Vui lòng nhập mật khẩu mới.");
        }

        return new VerifyOtpResponse(challenge.purpose(), null, "Xác minh OTP thành công.");
    }

    @Transactional
    public ForgotPasswordResponse resendOtp(String challengeId) {
        OtpChallengeRecord challenge = authRepository.findOtpChallengeById(challengeId)
                .orElseThrow(() -> new OtpException("Yêu cầu xác minh không hợp lệ hoặc phiên đã kết thúc."));

        if (challenge.usedAt() != null) {
            throw new OtpException("Phiên xác minh này đã hoàn tất. Vui lòng tạo yêu cầu mới.");
        }

        if (challenge.sends() >= MAX_SENDS) {
            throw new OtpException("Bạn đã vượt quá số lần gửi lại mã (tối đa 2 lần). Vui lòng yêu cầu lại từ đầu.");
        }

        Instant now = Instant.now();
        if (now.isBefore(challenge.nextSendAt())) {
            long remainingCooldown = challenge.nextSendAt().getEpochSecond() - now.getEpochSecond();
            throw new OtpException("Vui lòng đợi " + remainingCooldown + " giây trước khi gửi lại mã.", remainingCooldown);
        }

        String newOtpCode = generateNumericOtp();
        String newCodeHash = tokenService.hmacSha256(challengeId + "|" + challenge.purpose() + "|" + newOtpCode);
        Instant newExpiresAt = now.plusSeconds(otpValiditySeconds);
        Instant newNextSendAt = now.plusSeconds(COOLDOWN_SECONDS);

        authRepository.updateOtpResend(challengeId, newCodeHash, newExpiresAt, newNextSendAt);

        try {
            emailService.sendOtpEmail(challenge.email(), newOtpCode, challenge.purpose(), otpValiditySeconds);
        } catch (Exception ex) {
            log.error("Failed to resend OTP to recipient: {}", ex.getMessage());
        }

        return new ForgotPasswordResponse(
                challengeId,
                "Mã OTP mới đã được gửi tới email của bạn. Vui lòng kiểm tra hộp thư.",
                COOLDOWN_SECONDS,
                otpValiditySeconds
        );
    }

    public boolean verifyResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        String tokenHash = sha256(rawToken.trim());
        Optional<PasswordResetTokenRecord> tokenOpt = authRepository.findPasswordResetToken(tokenHash);
        if (tokenOpt.isEmpty()) {
            return false;
        }
        PasswordResetTokenRecord record = tokenOpt.get();
        return record.usedAt() == null && Instant.now().isBefore(record.expiresAt());
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword, String confirmPassword) {
        if (newPassword == null || confirmPassword == null) {
            throw new InvalidPasswordException("Mật khẩu mới và xác nhận mật khẩu không được để trống.");
        }

        if (!newPassword.equals(confirmPassword)) {
            throw new PasswordMismatchException("Xác nhận mật khẩu không khớp.");
        }

        if (!PasswordPolicy.isValid(newPassword)) {
            throw new InvalidPasswordException("Mật khẩu mới không hợp lệ. Mật khẩu phải có từ 8 đến 64 ký tự, gồm ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số.");
        }

        String tokenHash = sha256(rawToken.trim());
        PasswordResetTokenRecord tokenRecord = authRepository.findPasswordResetToken(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."));

        if (tokenRecord.usedAt() != null || Instant.now().isAfter(tokenRecord.expiresAt())) {
            throw new InvalidTokenException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
        }

        UserRecord user = authRepository.findUserById(tokenRecord.userId())
                .orElseThrow(() -> new InvalidTokenException("Không tìm thấy thông tin tài khoản hợp lệ."));

        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new InvalidPasswordException("Mật khẩu mới không được trùng với mật khẩu hiện tại.");
        }

        authRepository.markPasswordResetTokenUsed(tokenHash);

        String newPasswordHash = passwordEncoder.encode(newPassword);
        authRepository.updateUserPassword(user.id(), newPasswordHash);
        log.info("User password successfully reset for user id {}", user.id());
    }

    private String generateNumericOtp() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    private String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
}

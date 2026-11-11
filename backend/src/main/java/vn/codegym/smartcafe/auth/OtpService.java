package vn.codegym.smartcafe.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TransactionTemplate requiresNewTx;

    public OtpService(
            AuthRepository authRepository,
            TokenService tokenService,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager,
            @Value("${smartcafe.auth.otp-validity-seconds:600}") long otpValiditySeconds
    ) {
        this.authRepository = authRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.otpValiditySeconds = otpValiditySeconds;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public ForgotPasswordResponse initiatePasswordResetOtp(String email) {
        if (email == null || !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new OtpException("Định dạng email không hợp lệ.");
        }

        Instant now = Instant.now();
        Instant nextSendAt = now.plusSeconds(COOLDOWN_SECONDS);
        Instant windowStart = now;

        // Anti-enumeration: neutral response even if email doesn't exist
        Optional<UserRecord> userOpt = authRepository.findUserByEmail(email.trim());

        String emailKey = tokenService.hmacSha256("LIMIT:" + email.trim().toLowerCase());
        boolean allowed = authRepository.checkAndIncrementLimit(emailKey, "RESET", now, windowStart, nextSendAt);
        if (!allowed) {
            throw new OtpException("Bạn đã yêu cầu gửi mã quá số lần cho phép. Vui lòng đợi trước khi thử lại.");
        }

        String challengeId = tokenService.generateRandomHex(32);
        Instant expiresAt = now.plusSeconds(otpValiditySeconds);

        if (userOpt.isPresent()) {
            UserRecord user = userOpt.get();
            String otpCode = generateNumericOtp();
            String codeHash = tokenService.hmacSha256(challengeId + "|RESET|" + otpCode);
            OtpChallengeRecord challenge = new OtpChallengeRecord(
                    challengeId,
                    user.id(),
                    user.email(),
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
                emailService.sendOtpEmail(user.email(), otpCode, "RESET", otpValiditySeconds);
            } catch (Exception ex) {
                log.error("Failed to send OTP email to {}: {}", user.email(), ex.getMessage());
            }
        } else {
            // Anti-account enumeration: create dummy challenge record with unguessable HMAC hash
            // so subsequent verify-otp and resend-otp requests behave completely identically
            // to existing accounts without revealing account existence or sending external emails.
            // Conforms to database constraint ck_auth_otp_reset_user (allows user_id NULL for REGISTER).
            String dummyHash = tokenService.hmacSha256("DUMMY|" + challengeId + "|" + tokenService.generateRandomHex(32));
            OtpChallengeRecord dummyChallenge = new OtpChallengeRecord(
                    challengeId,
                    null,
                    email.trim().toLowerCase(),
                    "REGISTER",
                    dummyHash,
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
            authRepository.saveOtpChallenge(dummyChallenge);
            log.info("Password reset dummy challenge created for non-existent email: {}", email);
        }

        return new ForgotPasswordResponse(
                challengeId,
                "Yêu cầu gửi mã xác minh đã được tiếp nhận. Nếu email tồn tại trong hệ thống, bạn sẽ nhận được mã OTP trong hộp thư.",
                COOLDOWN_SECONDS,
                otpValiditySeconds
        );
    }

    @Transactional(noRollbackFor = OtpException.class)
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

        String expectedHash = tokenService.hmacSha256(challengeId + "|" + challenge.purpose() + "|" + inputOtp.trim());
        boolean isMatch = MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                challenge.codeHash().getBytes(StandardCharsets.UTF_8)
        );

        if (!isMatch) {
            // Commit attempt increment immediately in its own transaction so it cannot be rolled back
            int updatedAttempts = requiresNewTx.execute(status -> {
                authRepository.incrementOtpAttempts(challengeId);
                return authRepository.findOtpChallengeById(challengeId)
                        .map(OtpChallengeRecord::attempts)
                        .orElse(MAX_ATTEMPTS);
            });

            if (updatedAttempts >= MAX_ATTEMPTS) {
                throw new OtpException("Bạn đã nhập sai quá 5 lần. Mã OTP đã bị vô hiệu hóa.");
            }
            int remaining = MAX_ATTEMPTS - updatedAttempts;
            throw new OtpException("Mã OTP không chính xác. Bạn còn " + remaining + " lần thử.");
        }

        // Atomic conditional consume: only proceed if exactly 1 row was updated
        int consumed = authRepository.markOtpUsed(challengeId);
        if (consumed == 0) {
            throw new OtpException("Yêu cầu xác minh không hợp lệ hoặc đã được sử dụng.");
        }

        if ("RESET".equalsIgnoreCase(challenge.purpose())) {
            if (challenge.userId() == null) {
                throw new OtpException("Yêu cầu xác minh không hợp lệ hoặc phiên đã kết thúc.");
            }
            UserRecord user = authRepository.findUserById(challenge.userId())
                    .orElseThrow(() -> new OtpException("Yêu cầu xác minh không hợp lệ hoặc phiên đã kết thúc."));

            String rawGrantToken = tokenService.generateResetGrantToken(
                    challengeId,
                    user.id(),
                    user.authVersion(),
                    otpValiditySeconds
            );

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

        Instant newExpiresAt = now.plusSeconds(otpValiditySeconds);
        Instant newNextSendAt = now.plusSeconds(COOLDOWN_SECONDS);

        String newCodeHash;
        if (challenge.userId() != null) {
            String newOtpCode = generateNumericOtp();
            newCodeHash = tokenService.hmacSha256(challengeId + "|" + challenge.purpose() + "|" + newOtpCode);

            // Send email before updating database. If SMTP fails or is unconfigured, throw exception
            // so the previous OTP code remains valid and unchanged in the database.
            try {
                emailService.sendOtpEmail(challenge.email(), newOtpCode, challenge.purpose(), otpValiditySeconds);
            } catch (Exception ex) {
                log.error("Failed to resend OTP to recipient {}: {}", challenge.email(), ex.getMessage());
                throw new OtpException("Không thể gửi email OTP qua dịch vụ gửi thư. Vui lòng thử lại sau.");
            }
        } else {
            // For non-existent email dummy challenge: generate new unguessable dummy hash and skip sending email
            newCodeHash = tokenService.hmacSha256("DUMMY|" + challengeId + "|" + tokenService.generateRandomHex(32));
        }

        // Atomic update with concurrency and cooldown guard (sends < 3 AND CURRENT_TIMESTAMP(6) >= next_send_at)
        int updated = authRepository.updateOtpResend(challengeId, newCodeHash, newExpiresAt, newNextSendAt);
        if (updated == 0) {
            throw new OtpException("Không thể gửi lại mã vào lúc này hoặc đã vượt quá số lần cho phép.");
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
        TokenService.ResetGrantClaims claims = tokenService.parseResetGrantToken(rawToken.trim());
        if (claims == null) {
            return false;
        }

        Optional<OtpChallengeRecord> challengeOpt = authRepository.findOtpChallengeById(claims.challengeId());
        if (challengeOpt.isEmpty()) {
            return false;
        }
        OtpChallengeRecord challenge = challengeOpt.get();
        if (challenge.usedAt() == null || !claims.userId().equals(challenge.userId())) {
            return false;
        }

        Optional<UserRecord> userOpt = authRepository.findUserById(claims.userId());
        if (userOpt.isEmpty()) {
            return false;
        }
        UserRecord user = userOpt.get();
        return "ACTIVE".equalsIgnoreCase(user.status()) && user.authVersion() == claims.authVersion();
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
            throw new InvalidPasswordException("Mật khẩu mới không hợp lệ. Mật khẩu phải có từ 8 đến 64 ký tự (tối đa 72 byte), gồm ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số.");
        }

        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
        }

        TokenService.ResetGrantClaims claims = tokenService.parseResetGrantToken(rawToken.trim());
        if (claims == null) {
            throw new InvalidTokenException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
        }

        OtpChallengeRecord challenge = authRepository.findOtpChallengeById(claims.challengeId())
                .orElseThrow(() -> new InvalidTokenException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."));

        if (challenge.usedAt() == null || !claims.userId().equals(challenge.userId())) {
            throw new InvalidTokenException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
        }

        UserRecord user = authRepository.findUserById(claims.userId())
                .orElseThrow(() -> new InvalidTokenException("Không tìm thấy thông tin tài khoản hợp lệ."));

        if (!"ACTIVE".equalsIgnoreCase(user.status()) || user.authVersion() != claims.authVersion()) {
            throw new InvalidTokenException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
        }

        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new InvalidPasswordException("Mật khẩu mới không được trùng với mật khẩu hiện tại.");
        }

        // Atomic conditional consume: optimistic locking on auth_version
        int updated = authRepository.updateUserPassword(user.id(), passwordEncoder.encode(newPassword), claims.authVersion());
        if (updated == 0) {
            throw new InvalidTokenException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã được sử dụng.");
        }

        log.info("User password successfully reset for user id {}", user.id());
    }

    private String generateNumericOtp() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%06d", code);
    }
}

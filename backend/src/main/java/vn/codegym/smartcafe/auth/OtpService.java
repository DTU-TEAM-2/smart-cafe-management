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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SENDS = 3; // 1 initial request + max 2 accepted resends, regardless of delivery
    private static final long COOLDOWN_SECONDS = 60;

    private final AuthRepository authRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final long otpValiditySeconds;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate requiresNewTx;

    public OtpService(
            AuthRepository authRepository,
            TokenService tokenService,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${smartcafe.auth.otp-validity-seconds:600}") long otpValiditySeconds
    ) {
        this.clock = clock;
        this.authRepository = authRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.otpValiditySeconds = otpValiditySeconds;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private record ResetReservation(
            String challengeId,
            Long recipientId,
            String recipientEmail,
            String otpCode
    ) {}

    public ForgotPasswordResponse initiatePasswordResetOtp(String email) {
        if (email == null || !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new OtpException("Định dạng email không hợp lệ.");
        }

        Instant now = clock.instant();
        Instant nextSendAt = now.plusSeconds(COOLDOWN_SECONDS);
        Instant windowStart = now;

        // Perform rate limit check, locking read on users PK, identity validation and challenge insert
        // in a dedicated short transaction to release the exclusive row lock on users before external SMTP I/O.
        ResetReservation reservation = transactionTemplate.execute(status -> {
            // Anti-enumeration: neutral response even if email doesn't exist
            Optional<UserRecord> userOpt = authRepository.findUserByEmail(email.trim());

            String emailKey = tokenService.hmacSha256("LIMIT:" + email.trim().toLowerCase());
            boolean allowed = authRepository.checkAndIncrementLimit(emailKey, "RESET", now, windowStart, nextSendAt);
            if (!allowed) {
                throw new OtpException("Bạn đã yêu cầu gửi mã quá số lần cho phép. Vui lòng đợi trước khi thử lại.");
            }

            String challengeId = tokenService.generateRandomHex(32);
            Instant expiresAt = now.plusSeconds(otpValiditySeconds);

            Long recipientId = null;
            String recipientEmail = null;
            String otpCode = null;

            if (userOpt.isPresent()) {
                Long userId = userOpt.get().id();
                // Lock user row by PK to serialize with concurrent email/profile changes and read current state
                Optional<UserRecord> lockedUserOpt = authRepository.findUserByIdForUpdate(userId);
                if (lockedUserOpt.isPresent()
                        && "ACTIVE".equalsIgnoreCase(lockedUserOpt.get().status())
                        && lockedUserOpt.get().email().equalsIgnoreCase(email.trim())) {
                    UserRecord user = lockedUserOpt.get();
                    otpCode = generateNumericOtp();
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
                    recipientId = user.id();
                    recipientEmail = user.email();
                }
            }

            if (recipientEmail == null) {
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
                log.info("Password reset dummy challenge created for non-existent or modified email: {}", email);
            }

            return new ResetReservation(challengeId, recipientId, recipientEmail, otpCode);
        });

        // Dispatch email OUTSIDE the database transaction and without holding any user row locks
        if (reservation != null && reservation.recipientId() != null) {
            try {
                if (canDispatchOtp(reservation.challengeId(), reservation.recipientId(), reservation.recipientEmail(), 1)) {
                    emailService.sendOtpEmail(reservation.recipientEmail(), reservation.otpCode(), "RESET", otpValiditySeconds);
                }
            } catch (Exception ex) {
                log.error("Failed to send OTP email to {}: {}", reservation.recipientEmail(), ex.getMessage());
            }
        }

        String returnedChallengeId = (reservation != null) ? reservation.challengeId() : "";
        return new ForgotPasswordResponse(
                returnedChallengeId,
                "Yêu cầu gửi mã xác minh đã được tiếp nhận. Nếu email tồn tại trong hệ thống, bạn sẽ nhận được mã OTP trong hộp thư.",
                COOLDOWN_SECONDS,
                otpValiditySeconds
        );
    }

    public VerifyOtpResponse verifyOtp(String challengeId, String inputOtp) {
        if (inputOtp == null || !inputOtp.matches("^[0-9]{6}$")) {
            throw new OtpException("Mã OTP phải gồm đúng 6 chữ số.");
        }

        OtpChallengeRecord challenge = authRepository.findOtpChallengeById(challengeId)
                .orElseThrow(() -> new OtpException("Yêu cầu xác minh không hợp lệ hoặc phiên đã kết thúc."));

        if (challenge.usedAt() != null) {
            throw new OtpException("Mã OTP này đã được sử dụng. Vui lòng yêu cầu mã mới.");
        }

        Instant now = clock.instant();
        if (!now.isBefore(challenge.expiresAt())) {
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
            // The initial read holds no connection while this independent CAS transaction runs.
            int updatedAttempts = requiresNewTx.execute(status -> {
                if (authRepository.incrementOtpAttemptsIfCurrent(challengeId, challenge.codeHash(), challenge.sends()) != 1) {
                    throw new OtpException("Yêu cầu xác minh không hợp lệ hoặc đã được sử dụng.");
                }
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

        return requiresNewTx.execute(status -> {
            // Atomic conditional consume: only proceed if exactly 1 row was updated
            int consumed = authRepository.markOtpUsedIfCurrent(challengeId, challenge.codeHash(), challenge.sends());
            if (consumed != 1) {
                throw new OtpException("Yêu cầu xác minh không hợp lệ hoặc đã được sử dụng.");
            }

            if ("RESET".equalsIgnoreCase(challenge.purpose())) {
                if (challenge.userId() == null) {
                    throw new OtpException("Yêu cầu xác minh không hợp lệ hoặc phiên đã kết thúc.");
                }
                UserRecord user = authRepository.findUserById(challenge.userId())
                        .orElseThrow(() -> new OtpException("Yêu cầu xác minh không hợp lệ hoặc phiên đã kết thúc."));

                if (!"ACTIVE".equalsIgnoreCase(user.status()) || !user.email().equalsIgnoreCase(challenge.email())) {
                    throw new OtpException("Yêu cầu xác minh không hợp lệ hoặc phiên đã kết thúc.");
                }

                String rawGrantToken = tokenService.generateResetGrantToken(
                        challengeId,
                        user.id(),
                        user.authVersion(),
                        otpValiditySeconds
                );

                return new VerifyOtpResponse("RESET", rawGrantToken, "Xác minh OTP thành công. Vui lòng nhập mật khẩu mới.");
            }

            return new VerifyOtpResponse(challenge.purpose(), null, "Xác minh OTP thành công.");
        });
    }

    private record ResendReservation(
            String challengeId,
            Long userId,
            String recipientEmail,
            String purpose,
            int expectedSends,
            String newOtpCode,
            String newCodeHash,
            long remainingValiditySeconds
    ) {}

    public ForgotPasswordResponse resendOtp(String challengeId) {
        Instant now = clock.instant();

        // Phase 1 (Reserve): lock challenge row briefly, validate invariants/cooldown/budget/SMTP readiness,
        // reserve resend generation, and commit BEFORE any external SMTP network I/O.
        ResendReservation reservation = transactionTemplate.execute(status -> {
            OtpChallengeRecord challenge = authRepository.findOtpChallengeByIdForUpdate(challengeId)
                    .orElseThrow(() -> new OtpException("Yêu cầu xác minh không hợp lệ hoặc phiên đã kết thúc."));

            if (challenge.usedAt() != null) {
                throw new OtpException("Phiên xác minh này đã hoàn tất. Vui lòng tạo yêu cầu mới.");
            }

            if (challenge.userId() != null) {
                // Plain read on users avoids acquiring users lock after challenge lock (prevents lock cycle with Staff PUT).
                UserRecord user = authRepository.findUserById(challenge.userId())
                        .orElseThrow(() -> new OtpException("Yêu cầu xác minh không hợp lệ hoặc phiên đã kết thúc."));
                if (!"ACTIVE".equalsIgnoreCase(user.status()) || !user.email().equalsIgnoreCase(challenge.email())) {
                    throw new OtpException("Yêu cầu xác minh không hợp lệ hoặc phiên đã kết thúc.");
                }
            }

            if (!now.isBefore(challenge.expiresAt())) {
                throw new OtpException("Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới.");
            }

            if (challenge.sends() >= MAX_SENDS) {
                throw new OtpException("Bạn đã vượt quá số lần gửi lại mã (tối đa 2 lần). Vui lòng yêu cầu lại từ đầu.");
            }

            if (now.isBefore(challenge.nextSendAt())) {
                long remainingCooldown = challenge.nextSendAt().getEpochSecond() - now.getEpochSecond();
                throw new OtpException("Vui lòng đợi " + remainingCooldown + " giây trước khi gửi lại mã.", remainingCooldown);
            }

            // Validate SMTP configuration readiness upfront for ALL requests (Nam P2 & Anti-Account Enumeration)
            try {
                emailService.validateReady();
            } catch (Exception ex) {
                log.error("Failed to resend OTP due to unconfigured mail service: {}", ex.getMessage());
                throw new OtpException("Không thể gửi email OTP qua dịch vụ gửi thư. Vui lòng thử lại sau.");
            }

            Instant newNextSendAt = now.plusSeconds(COOLDOWN_SECONDS);
            if (authRepository.consumeOtpResend(challengeId, now, newNextSendAt) != 1) {
                throw new IllegalStateException("OTP resend reservation failed");
            }

            Duration remaining = Duration.between(now, challenge.expiresAt());
            long remainingValidity = remaining.toSeconds() + (remaining.getNano() > 0 ? 1 : 0);

            String newOtpCode = null;
            String newCodeHash = null;
            if (challenge.userId() != null) {
                newOtpCode = generateNumericOtp();
                newCodeHash = tokenService.hmacSha256(challengeId + "|" + challenge.purpose() + "|" + newOtpCode);
            }

            return new ResendReservation(
                    challengeId,
                    challenge.userId(),
                    challenge.email(),
                    challenge.purpose(),
                    challenge.sends() + 1,
                    newOtpCode,
                    newCodeHash,
                    remainingValidity
            );
        });

        // Phase 2 & 3 (Dispatch outside TX/locks, then Finalize in short CAS transaction):
        // Dummy challenges (userId == null) consume the same budget/cooldown without dispatching external mail.
        if (reservation != null && reservation.userId() != null) {
            if (canDispatchOtp(challengeId, reservation.userId(), reservation.recipientEmail(), reservation.expectedSends())) {
                boolean delivered = false;
                try {
                    emailService.sendOtpEmail(
                            reservation.recipientEmail(),
                            reservation.newOtpCode(),
                            reservation.purpose(),
                            reservation.remainingValiditySeconds()
                    );
                    delivered = true;
                } catch (Exception ex) {
                    log.warn("OTP resend delivery failed for challenge {}", challengeId);
                }

                final boolean deliverySucceeded = delivered;
                transactionTemplate.executeWithoutResult(status -> {
                    Optional<UserRecord> userOpt = authRepository.findUserById(reservation.userId());
                    if (userOpt.isEmpty()
                            || !"ACTIVE".equalsIgnoreCase(userOpt.get().status())
                            || !userOpt.get().email().equalsIgnoreCase(reservation.recipientEmail())) {
                        // Safe no-op: user became inactive or email binding changed during SMTP dispatch.
                        return;
                    }

                    // Keep database failures outside the SMTP catch: throw on genuine DB failure without rolling back Phase 1 quota.
                    int updated = authRepository.recordOtpDelivery(
                            challengeId,
                            reservation.expectedSends(),
                            reservation.newCodeHash(),
                            deliverySucceeded,
                            clock.instant()
                    );
                    if (updated != 1) {
                        // Use locking current read (FOR UPDATE) so MySQL REPEATABLE READ snapshot from findUserById
                        // does not mask a concurrent commit that consumed/revoked/expired the challenge.
                        Optional<OtpChallengeRecord> currentOpt = authRepository.findOtpChallengeByIdForUpdate(challengeId);
                        if (currentOpt.isPresent()) {
                            OtpChallengeRecord current = currentOpt.get();
                            if (current.usedAt() != null
                                    || !clock.instant().isBefore(current.expiresAt())
                                    || current.sends() > reservation.expectedSends()) {
                                // Safe no-op: challenge was consumed, revoked, expired, or superseded during dispatch.
                                return;
                            }
                        }
                        throw new IllegalStateException("OTP delivery transition failed");
                    }
                });
            }
        }

        return new ForgotPasswordResponse(
                challengeId,
                "Yêu cầu gửi lại mã xác minh đã được tiếp nhận. Vui lòng kiểm tra hộp thư của bạn.",
                COOLDOWN_SECONDS,
                reservation != null ? reservation.remainingValiditySeconds() : 0L
        );
    }

    private boolean canDispatchOtp(String challengeId, Long userId, String email, int expectedSends) {
        var user = authRepository.findUserById(userId);
        if (user.isEmpty() || !"ACTIVE".equalsIgnoreCase(user.get().status()) || !email.equalsIgnoreCase(user.get().email())) {
            return false;
        }
        return authRepository.findOtpChallengeById(challengeId)
                .filter(challenge -> userId.equals(challenge.userId()) && email.equalsIgnoreCase(challenge.email())
                        && challenge.usedAt() == null && challenge.sends() == expectedSends
                        && clock.instant().isBefore(challenge.expiresAt()))
                .isPresent();
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
        return "ACTIVE".equalsIgnoreCase(user.status())
                && user.authVersion() == claims.authVersion()
                && user.email().equalsIgnoreCase(challenge.email());
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

        if (!"ACTIVE".equalsIgnoreCase(user.status())
                || user.authVersion() != claims.authVersion()
                || !user.email().equalsIgnoreCase(challenge.email())) {
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

    String generateNumericOtp() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%06d", code);
    }
}

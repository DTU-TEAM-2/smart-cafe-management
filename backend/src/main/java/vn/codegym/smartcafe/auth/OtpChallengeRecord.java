package vn.codegym.smartcafe.auth;

import java.time.Instant;

public record OtpChallengeRecord(
    String id,
    Long userId,
    String email,
    String purpose,
    String codeHash,
    int attempts,
    int sends,
    Instant expiresAt,
    Instant nextSendAt,
    Instant usedAt,
    String deliveryStatus,
    Instant createdAt,
    String lastErrorCode,
    Instant sentAt
) {}

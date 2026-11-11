package vn.codegym.smartcafe.auth;

import java.time.Instant;

public record PasswordResetTokenRecord(
    Long id,
    Long userId,
    String tokenHash,
    Instant expiresAt,
    Instant usedAt,
    Instant createdAt
) {}

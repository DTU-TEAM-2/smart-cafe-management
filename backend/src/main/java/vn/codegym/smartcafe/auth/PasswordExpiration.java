package vn.codegym.smartcafe.auth;

import java.time.Duration;
import java.time.Instant;

/** UTC elapsed time, shared by Login and Profile; this does not restrict business APIs. */
public record PasswordExpiration(Instant passwordChangedAt, Instant passwordExpiresAt, boolean mustChangePassword) {
    public static final Duration MAX_AGE = Duration.ofDays(30);

    public static PasswordExpiration at(Instant changedAt, boolean required, Instant now) {
        Instant expiresAt = changedAt == null ? null : changedAt.plus(MAX_AGE);
        return new PasswordExpiration(changedAt, expiresAt, required || expiresAt == null || !now.isBefore(expiresAt));
    }
}

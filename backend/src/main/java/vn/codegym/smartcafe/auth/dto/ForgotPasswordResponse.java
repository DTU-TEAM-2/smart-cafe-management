package vn.codegym.smartcafe.auth.dto;

public record ForgotPasswordResponse(
    String challengeId,
    String message,
    long retryAfterSeconds,
    long expiresInSeconds
) {}

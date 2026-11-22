package vn.codegym.smartcafe.auth.dto;

public record LoginResponse(
    String token,
    UserDto user,
    java.time.Instant passwordChangedAt,
    java.time.Instant passwordExpiresAt,
    boolean mustChangePassword
) {}

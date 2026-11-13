package vn.codegym.smartcafe.auth.dto;

public record VerifyOtpResponse(
    String purpose,
    String token,
    String message
) {}

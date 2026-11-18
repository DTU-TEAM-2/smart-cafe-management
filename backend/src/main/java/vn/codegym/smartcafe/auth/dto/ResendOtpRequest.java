package vn.codegym.smartcafe.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendOtpRequest(
    @NotBlank(message = "challengeId không được để trống")
    String challengeId
) {}

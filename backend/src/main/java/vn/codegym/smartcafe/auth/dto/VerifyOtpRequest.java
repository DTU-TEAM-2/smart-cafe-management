package vn.codegym.smartcafe.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
    @NotBlank(message = "challengeId không được để trống")
    String challengeId,

    @NotBlank(message = "Mã OTP không được để trống")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP phải gồm 6 chữ số")
    String otp
) {}

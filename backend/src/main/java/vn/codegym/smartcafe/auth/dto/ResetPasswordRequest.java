package vn.codegym.smartcafe.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank(message = "Token không được để trống")
    String token,

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 8, max = 64, message = "Mật khẩu mới phải có từ 8 đến 64 ký tự")
    String newPassword,

    @NotBlank(message = "Xác nhận mật khẩu không được để trống")
    String confirmPassword
) {}

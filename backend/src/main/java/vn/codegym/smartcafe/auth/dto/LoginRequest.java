package vn.codegym.smartcafe.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(max = 64, message = "Tên đăng nhập không được vượt quá 64 ký tự")
    String username,

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(max = 128, message = "Mật khẩu không được vượt quá 128 ký tự")
    String password
) {}

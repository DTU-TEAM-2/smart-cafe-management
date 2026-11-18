package vn.codegym.smartcafe.auth.dto;

public record UserDto(
    Long id,
    String username,
    String fullName,
    String email,
    String phone,
    String role
) {}

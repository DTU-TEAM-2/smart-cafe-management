package vn.codegym.smartcafe.auth.dto;

public record LoginResponse(
    String token,
    UserDto user
) {}

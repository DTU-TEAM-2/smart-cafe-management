package vn.codegym.smartcafe.auth;

import java.security.Principal;
import java.time.Instant;

public record UserRecord(
    Long id,
    String username,
    String passwordHash,
    String fullName,
    String email,
    String phone,
    String role,
    String status,
    Long avatarMediaId,
    long authVersion,
    Instant passwordChangedAt
) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}

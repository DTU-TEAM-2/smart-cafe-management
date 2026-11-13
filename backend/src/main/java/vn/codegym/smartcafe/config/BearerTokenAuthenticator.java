package vn.codegym.smartcafe.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/** Verifies the existing module's bearer contract; does not issue tokens or accounts. */
@Component
public final class BearerTokenAuthenticator {
    private final JdbcClient jdbc;
    private final byte[] secret;

    public BearerTokenAuthenticator(JdbcClient jdbc, @Value("${smartcafe.auth.token-secret:}") String secret) {
        this.jdbc = jdbc;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public Authentication authenticate(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        var identity = verify(header.substring(7).trim());
        if (identity == null) return null;
        return jdbc.sql("""
                SELECT username, role FROM users
                WHERE id=? AND status='ACTIVE' AND auth_version=?
                    AND (?='' OR username=?)
                """).param(identity.id()).param(identity.authVersion())
                .param(identity.username()).param(identity.username()).query((row, index) ->
                new User(row.getString("username"), row.getString("role"))).optional()
                .filter(user -> List.of("ADMIN", "STAFF", "CUSTOMER").contains(user.role()))
                .map(user -> {
                    var authentication = UsernamePasswordAuthenticationToken.authenticated(user.username(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                    authentication.setDetails(new VerifiedIdentity(identity.id(), identity.expiry(), identity.authVersion()));
                    return (Authentication) authentication;
                }).orElse(null);
    }

    public boolean isCurrentStaff(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof VerifiedIdentity identity)
                || !identity.expiry().isAfter(Instant.now())) return false;
        return jdbc.sql("""
                SELECT id FROM users WHERE id=? AND username=? AND role='STAFF'
                    AND status='ACTIVE' AND auth_version=?
                """).param(identity.id()).param(authentication.getName()).param(identity.authVersion())
                .query(Long.class).optional().isPresent();
    }

    private record VerifiedIdentity(long id, Instant expiry, long authVersion) {}
    private Identity verify(String token) {
        if (secret.length < 32 || token.length() > 2048) return null;
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || parts[1].length() != 64) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = mac.doFinal(parts[0].getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(parts[1]))) return null;
            String[] fields = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split(":", -1);
            // PR #7's unified format is id:username:role:authVersion:expiry.
            // Retain its earlier id:role:authVersion:expiry and legacy username format.
            String username;
            long authVersion;
            String expires;
            if (fields.length == 5) {
                if (!List.of("ADMIN", "STAFF", "CUSTOMER").contains(fields[2])) return null;
                username = fields[1];
                authVersion = Long.parseLong(fields[3]);
                expires = fields[4];
            } else if (fields.length == 4 && fields[2].matches("[+-]?[0-9]+")) {
                if (!List.of("ADMIN", "STAFF", "CUSTOMER").contains(fields[1])) return null;
                username = "";
                authVersion = Long.parseLong(fields[2]);
                expires = fields[3];
            } else if (fields.length == 4 && !fields[1].isBlank()) {
                username = fields[1];
                authVersion = 0;
                expires = fields[3];
            } else return null;
            long id = Long.parseLong(fields[0]);
            long epoch = Long.parseLong(expires);
            // Same seconds/milliseconds compatibility rule as the actual auth issuer.
            Instant expiry = epoch > 10_000_000_000L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
            if (id <= 0 || authVersion < 0 || !expiry.isAfter(Instant.now())) return null;
            return new Identity(id, username, expiry, authVersion);
        } catch (IllegalArgumentException | java.time.DateTimeException | java.security.GeneralSecurityException failure) {
            return null;
        }
    }

    private record Identity(long id, String username, Instant expiry, long authVersion) {}
    private record User(String username, String role) {}
}

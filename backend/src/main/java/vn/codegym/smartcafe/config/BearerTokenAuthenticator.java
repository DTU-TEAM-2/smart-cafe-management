package vn.codegym.smartcafe.config;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import vn.codegym.smartcafe.auth.TokenService;
import vn.codegym.smartcafe.auth.UserRecord;

/** Verifies the existing module's bearer contract; does not issue tokens or accounts. */
@Component
public final class BearerTokenAuthenticator {
    private final JdbcClient jdbc;
    private final TokenService tokens;

    public BearerTokenAuthenticator(JdbcClient jdbc, TokenService tokens) {
        this.jdbc = jdbc;
        this.tokens = tokens;
    }

    public Authentication authenticate(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = header.substring(7).trim();
        if (token.length() > 2048) return null;
        var identity = tokens.parseToken(token);
        if (identity == null || identity.userId() <= 0 || identity.authVersion() < 0
                || !List.of("ADMIN", "STAFF", "CUSTOMER").contains(identity.role())
                || !identity.expiresAt().isAfter(Instant.now())) return null;
        String username = identity.username() == null ? "" : identity.username();
        return jdbc.sql("""
                SELECT username, role FROM users
                WHERE id=? AND status='ACTIVE' AND auth_version=?
                    AND (?='' OR username=?)
                """).param(identity.userId()).param(identity.authVersion())
                .param(username).param(username).query((row, index) ->
                new UserRecord(identity.userId(), row.getString("username"), "", "", "", "",
                        row.getString("role"), "ACTIVE", null, identity.authVersion(), null)).optional()
                .filter(user -> List.of("ADMIN", "STAFF", "CUSTOMER").contains(user.role()))
                .map(user -> {
                    var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                    authentication.setDetails(new VerifiedIdentity(identity.userId(), identity.expiresAt(), identity.authVersion()));
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
}

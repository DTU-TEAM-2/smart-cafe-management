package vn.codegym.smartcafe.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Consumer of the authentication module's signed bearer contract; never issues accounts or tokens. */
final class HeaderTokenFilter extends OncePerRequestFilter {
    private final JdbcClient jdbc;
    private final byte[] secret;

    HeaderTokenFilter(JdbcClient jdbc, String secret) {
        this.jdbc = jdbc;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals("/api/v1/home/account") && !path.equals("/api/v1/home/notifications")
                && !path.startsWith("/api/v1/sales/") && !path.equals("/api/v1/income")
                && !path.startsWith("/api/v1/bills") && !path.startsWith("/api/v1/statistics/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var existing = SecurityContextHolder.getContext().getAuthentication();
        String header = request.getHeader("Authorization");
        if ((existing == null || !existing.isAuthenticated()) && header != null
                && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            var identity = verify(header.substring(7).trim());
            if (identity != null) {
                try {
                    // Unversioned legacy tokens can authenticate only while auth_version remains zero.
                    // A password/role revocation must not revive an old signed token.
                    jdbc.sql("""
                            SELECT username, role FROM users
                            WHERE id=? AND username=? AND status='ACTIVE' AND auth_version=0
                            """).param(identity.id()).param(identity.username()).query((row, index) ->
                            new User(row.getString("username"), row.getString("role"))).optional()
                            .filter(user -> List.of("ADMIN", "STAFF", "CUSTOMER").contains(user.role()))
                            .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(
                                    UsernamePasswordAuthenticationToken.authenticated(user.username(), null,
                                            List.of(new SimpleGrantedAuthority("ROLE_" + user.role())))));
                } catch (DataAccessException failure) {
                    response.setStatus(503);
                    response.setContentType("application/problem+json;charset=UTF-8");
                    response.getWriter().write("{\"status\":503,\"title\":\"Service Unavailable\",\"detail\":\"Không thể xác minh tài khoản lúc này.\"}");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

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
            if (fields.length != 4 || fields[1].isBlank()) return null;
            long id = Long.parseLong(fields[0]);
            if (id <= 0 || !Instant.ofEpochMilli(Long.parseLong(fields[3])).isAfter(Instant.now())) return null;
            return new Identity(id, fields[1]);
        } catch (IllegalArgumentException | java.security.GeneralSecurityException failure) {
            return null;
        }
    }

    private record Identity(long id, String username) {}
    private record User(String username, String role) {}
}

package vn.codegym.smartcafe.news;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** SQL predicates are constants selected only from the authenticated user's current DB role. */
@Component
class NewsReaderAccess {
    private final JdbcClient jdbc;

    NewsReaderAccess(JdbcClient jdbc) { this.jdbc = jdbc; }

    String predicate(String username) {
        if (username == null || username.isBlank()) throw forbidden();
        String role = jdbc.sql("SELECT role FROM users WHERE username=? AND status='ACTIVE'")
                .param(username).query(String.class).optional().orElseThrow(this::forbidden);
        return switch (role) {
            case "ADMIN" -> "";
            case "STAFF" -> " AND p.status='PUBLISHED' AND p.audience_staff=1";
            case "CUSTOMER" -> " AND p.status='PUBLISHED' AND p.audience_customer=1";
            default -> throw forbidden();
        };
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền đọc tin tức.");
    }
}

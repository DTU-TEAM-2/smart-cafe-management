package vn.codegym.smartcafe.home;

import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class HeaderService {
    private final JdbcClient jdbc;

    HeaderService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public HomeAccount account(String username) {
        return jdbc.sql("""
                SELECT u.id, u.username, u.full_name, u.email, u.phone, u.role,
                       p.address, p.birthday, p.gender, p.salary, a.secure_url
                FROM users u
                LEFT JOIN staff_profiles p ON p.user_id=u.id
                LEFT JOIN media_assets a ON a.id=u.avatar_media_id AND a.deleted_at IS NULL
                WHERE u.username=? AND u.status='ACTIVE'
                """).param(username).query((row, index) -> new HomeAccount(
                        row.getString("id"), row.getString("username"), row.getString("full_name"),
                        row.getString("email"), row.getString("phone"), row.getString("role"),
                        row.getString("address"), row.getString("birthday"), row.getString("gender"),
                        row.getBigDecimal("salary") == null ? null : row.getBigDecimal("salary").toPlainString(),
                        row.getString("secure_url"))).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @Transactional(readOnly = true)
    public HomeNotificationFeed notifications(String username) {
        // The authenticated identity selects its own recipient. No recipient/role is accepted from the client.
        var recipient = account(username);
        if (!java.util.Set.of("STAFF", "ADMIN").contains(recipient.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var notices = jdbc.sql("""
                SELECT * FROM (
                    SELECT CONCAT('order:', b.id) AS event_id, 'ORDER' AS event_type,
                           CAST(t.id AS CHAR) AS table_id, t.name AS table_name,
                           CONCAT(t.name, ' vừa gọi món') AS message, b.submitted_at AS created_at
                    FROM order_batches b
                    JOIN table_sessions s ON s.id=b.session_id AND s.status IN ('OPEN','CHECKOUT')
                    JOIN cafe_tables t ON t.id=s.table_id
                    WHERE EXISTS (SELECT 1 FROM order_items i WHERE i.order_batch_id=b.id AND i.status='SUBMITTED')
                    UNION ALL
                    SELECT CONCAT('waiter:', r.id), 'WAITER', CAST(t.id AS CHAR), t.name,
                           CONCAT(t.name, ' đang gọi phục vụ'), r.created_at
                    FROM service_requests r
                    JOIN table_sessions s ON s.id=r.session_id AND s.status IN ('OPEN','CHECKOUT')
                    JOIN cafe_tables t ON t.id=s.table_id
                    WHERE r.request_type='WAITER' AND r.status='PENDING'
                ) events
                ORDER BY created_at DESC, event_id DESC LIMIT 30
                """).query((row, index) -> new HomeNotificationFeed.Notice(
                        row.getString("event_id"), row.getString("event_type"), row.getString("table_id"),
                        row.getString("table_name"), row.getString("message"),
                        row.getTimestamp("created_at").toLocalDateTime().atOffset(ZoneOffset.UTC).toString())).list();
        return new HomeNotificationFeed(recipient.id(), notices);
    }
}

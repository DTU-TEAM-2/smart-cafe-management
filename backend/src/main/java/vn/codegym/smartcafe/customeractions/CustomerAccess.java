package vn.codegym.smartcafe.customeractions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Verifies existing guest credentials. Does not issue tokens, join or open QR sessions. */
@Component
public class CustomerAccess {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public CustomerAccess(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    public record Client(long id, long sessionId, String sessionStatus) { }

    // Caller must hold a transaction. Lock order matches Sales/Checkout: table, session, client.
    public Client lock(long sessionId, String authorization) {
        if (sessionId <= 0 || authorization == null || !authorization.startsWith("Bearer ")) throw unauthorized();
        String token = authorization.substring(7);
        if (!token.matches("[A-Za-z0-9_-]{43,256}")) throw unauthorized();
        String hash = hash(token);
        var rows = jdbc.queryForList("""
                SELECT c.id,s.table_id FROM session_clients c JOIN table_sessions s ON s.id=c.session_id
                WHERE c.session_id=? AND c.access_token_hash=?
                """, sessionId, hash);
        if (rows.isEmpty()) throw unauthorized();
        long clientId = ((Number) rows.getFirst().get("id")).longValue();
        long tableId = ((Number) rows.getFirst().get("table_id")).longValue();
        String tableStatus = jdbc.queryForObject("SELECT status FROM cafe_tables WHERE id=? FOR UPDATE", String.class, tableId);
        var session = jdbc.queryForMap("SELECT status,ended_at FROM table_sessions WHERE id=? FOR UPDATE", sessionId);
        var client = jdbc.queryForMap("SELECT expires_at,revoked_at FROM session_clients WHERE id=? AND session_id=? AND access_token_hash=? FOR UPDATE",
                clientId, sessionId, hash);
        if (client.get("revoked_at") != null || !databaseInstant(client.get("expires_at")).isAfter(clock.instant())) throw unauthorized();
        String status = (String) session.get("status");
        if (session.get("ended_at") != null || !java.util.List.of("OPEN", "CHECKOUT").contains(status)) throw unauthorized();
        if (!"ACTIVE".equals(tableStatus)) throw conflict("Bàn hiện không phục vụ.");
        return new Client(clientId, sessionId, status);
    }

    public void requireWritable(Client client) {
        if (!"OPEN".equals(client.sessionStatus())) throw conflict("Phiên đang thanh toán, không thể thay đổi món.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM bills WHERE session_id=? AND status<>'VOID'", Long.class, client.sessionId()) != 0)
            throw conflict("Phiên đã lập hóa đơn, không thể thay đổi món.");
    }

    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static java.time.Instant databaseInstant(Object value) {
        // DATETIME is returned as LocalDateTime by Connector/J's generic row map.
        return value instanceof java.sql.Timestamp timestamp ? timestamp.toInstant()
                : ((java.time.LocalDateTime) value).toInstant(java.time.ZoneOffset.UTC);
    }
    public static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
    private ResponseStatusException unauthorized() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Quyền truy cập phiên không hợp lệ hoặc đã hết hạn."); }
}

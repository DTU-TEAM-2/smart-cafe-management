package vn.codegym.smartcafe.customeractions;

import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static vn.codegym.smartcafe.customeractions.CustomerAccess.conflict;

@Service
public class CustomerRequestService {
    private final JdbcTemplate jdbc;
    private final CustomerAccess access;
    private final Clock clock;
    public CustomerRequestService(JdbcTemplate jdbc, CustomerAccess access, Clock clock) {
        this.jdbc = jdbc; this.access = access; this.clock = clock;
    }
    public record Request(String id, String type, String status, String message) { }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Request submit(long sessionId, String authorization, String key, String type) {
        var client = access.lock(sessionId, authorization);
        CustomerOrderService.key(key);
        var previous = jdbc.queryForList("SELECT id,request_type,status FROM service_requests WHERE session_id=? AND idempotency_key=?", sessionId, key);
        if (!previous.isEmpty()) {
            if (!type.equals(previous.getFirst().get("request_type"))) throw conflict("Mã gửi lại đã được dùng cho loại yêu cầu khác.");
            return response(previous.getFirst());
        }
        var active = jdbc.queryForList("SELECT id,request_type,status FROM service_requests WHERE session_id=? AND request_type=? AND status IN ('PENDING','ACKNOWLEDGED')", sessionId, type);
        if (!active.isEmpty()) return response(active.getFirst());
        if (type.equals("BILL")) {
            access.requireWritable(client);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE session_id=? AND status<>'CANCELLED'", Long.class, sessionId) == 0)
                throw CustomerOrderService.bad("Chưa có món để yêu cầu thanh toán.");
        } else if (!client.sessionStatus().equals("OPEN")) throw conflict("Phiên đang thanh toán, vui lòng liên hệ nhân viên.");
        jdbc.update("INSERT INTO service_requests(session_id,requested_by_client_id,request_type,created_at,idempotency_key) VALUES(?,?,?,?,?)",
                sessionId, client.id(), type, Timestamp.from(clock.instant()), key);
        return response(jdbc.queryForMap("SELECT id,request_type,status FROM service_requests WHERE session_id=? AND idempotency_key=?", sessionId, key));
    }

    private Request response(java.util.Map<String, Object> row) {
        String type = (String) row.get("request_type");
        return new Request(row.get("id").toString(), type, (String) row.get("status"),
                type.equals("WAITER") ? "Đã gửi yêu cầu phục vụ." : "Đã gửi yêu cầu thanh toán.");
    }
}

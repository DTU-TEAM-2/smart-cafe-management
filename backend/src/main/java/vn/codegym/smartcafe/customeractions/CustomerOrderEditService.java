package vn.codegym.smartcafe.customeractions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static vn.codegym.smartcafe.customeractions.CustomerAccess.conflict;

@Service
public class CustomerOrderEditService {
    private final JdbcTemplate jdbc;
    private final CustomerAccess access;
    private final CustomerOrderService orders;
    private final Clock clock;
    public CustomerOrderEditService(JdbcTemplate jdbc, CustomerAccess access, CustomerOrderService orders, Clock clock) {
        this.jdbc = jdbc; this.access = access; this.orders = orders; this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CustomerOrderService.Order edit(long sessionId, long itemId, String authorization, long version, Integer quantity) {
        var client = access.lock(sessionId, authorization);
        access.requireWritable(client);
        var rows = jdbc.queryForList("""
                SELECT i.*,b.editable_until,b.edit_policy FROM order_items i JOIN order_batches b ON b.id=i.order_batch_id AND b.session_id=i.session_id
                WHERE i.id=? AND i.session_id=? AND b.submitted_by_client_id=? FOR UPDATE
                """, itemId, sessionId, client.id());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món thuộc lần gọi của bạn.");
        var item = rows.getFirst();
        String status = (String) item.get("status");
        if (((Number) item.get("version")).longValue() != version) throw conflict("Món đã thay đổi. Vui lòng tải lại danh sách.");
        if (!clock.instant().isBefore(CustomerAccess.databaseInstant(item.get("editable_until")))) throw conflict("Đã hết thời gian sửa hoặc hủy món.");
        if (!"BEFORE_PREPARATION".equals(item.get("edit_policy")) || !status.equals("SUBMITTED"))
            throw conflict("Trạng thái món hiện không cho phép sửa hoặc hủy.");
        if (quantity == null) {
            jdbc.update("UPDATE order_items SET status='CANCELLED',cancelled_at=?,cancellation_reason=?,version=version+1 WHERE id=? AND version=?",
                    Timestamp.from(clock.instant()), "Khách hủy món trong thời hạn cho phép.", itemId, version);
        } else {
            BigDecimal newLine = ((BigDecimal) item.get("unit_price")).multiply(BigDecimal.valueOf(quantity));
            BigDecimal otherTotal = jdbc.queryForObject("SELECT COALESCE(SUM(line_total),0) FROM order_items WHERE session_id=? AND id<>? AND status<>'CANCELLED'",
                    BigDecimal.class, sessionId, itemId);
            if (newLine.add(otherTotal).compareTo(new BigDecimal("999999999999999999")) > 0) throw CustomerOrderService.bad("Tổng tiền vượt giới hạn cho phép.");
            jdbc.update("UPDATE order_items SET quantity=?,version=version+1 WHERE id=? AND version=?", quantity, itemId, version);
        }
        var result = orders.read(((Number) item.get("order_batch_id")).longValue(), sessionId);
        return new CustomerOrderService.Order(result.id(),result.sessionId(),result.totalAmount(),result.sessionTotalAmount(),result.editableUntil(),result.items(),
                quantity == null ? "Đã hủy món." : "Đã cập nhật số lượng món.");
    }
}

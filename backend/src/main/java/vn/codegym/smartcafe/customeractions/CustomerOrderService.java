package vn.codegym.smartcafe.customeractions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static vn.codegym.smartcafe.customeractions.CustomerAccess.conflict;

@Service
public class CustomerOrderService {
    private final JdbcTemplate jdbc;
    private final CustomerAccess access;
    private final Clock clock;
    private static final String EDIT_POLICY = "BEFORE_PREPARATION";
    public CustomerOrderService(JdbcTemplate jdbc, CustomerAccess access, Clock clock) {
        this.jdbc = jdbc; this.access = access; this.clock = clock;
    }
    public record Item(String id, String menuItemId, String name, String unitPrice, int quantity,
            String lineTotal, String status, String version) { }
    public record Order(String id, String sessionId, String totalAmount, String sessionTotalAmount, String editableUntil, List<Item> items, String message) { }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Order submit(long sessionId, String authorization, String key, CustomerOrderController.Command command) {
        var client = access.lock(sessionId, authorization);
        key(key);
        var lines = command.items().stream().sorted(Comparator.comparing(CustomerOrderController.Line::menuItemId)).toList();
        if (lines.stream().map(CustomerOrderController.Line::menuItemId).distinct().count() != lines.size())
            throw bad("Mỗi món chỉ được xuất hiện một lần trong lần gửi đơn.");
        String hash = CustomerAccess.hash(client.id() + ":" + lines.stream().map(l -> l.menuItemId() + "=" + l.quantity().intValueExact()).toList());
        var prior = jdbc.queryForList("SELECT id,request_hash FROM order_batches WHERE session_id=? AND idempotency_key=?", sessionId, key);
        if (!prior.isEmpty()) {
            if (!hash.equals(prior.getFirst().get("request_hash"))) throw conflict("Mã gửi lại đã được dùng cho nội dung khác.");
            return read(((Number) prior.getFirst().get("id")).longValue(), sessionId);
        }
        access.requireWritable(client);
        var menus = new ArrayList<Map<String, Object>>();
        BigDecimal total = jdbc.queryForObject("SELECT COALESCE(SUM(line_total),0) FROM order_items WHERE session_id=? AND status<>'CANCELLED'", BigDecimal.class, sessionId);
        for (var line : lines) {
            var menu = jdbc.queryForList("""
                    SELECT m.id,m.code,m.name,m.price FROM menu_items m JOIN menu_categories c ON c.id=m.category_id
                    WHERE m.id=? AND m.active=1 AND m.available=1 AND c.active=1 FOR SHARE
                    """, line.menuItemId());
            if (menu.isEmpty()) throw conflict("Món đã ngừng phục vụ hoặc không còn tồn tại.");
            var row = menu.getFirst();
            BigDecimal amount = ((BigDecimal) row.get("price")).multiply(line.quantity());
            total = total.add(amount);
            if (amount.compareTo(new BigDecimal("999999999999999999")) > 0 || total.compareTo(new BigDecimal("999999999999999999")) > 0)
                throw bad("Tổng tiền vượt giới hạn cho phép.");
            menus.add(row);
        }
        String code = "ORD-" + UUID.randomUUID();
        var now = clock.instant();
        jdbc.update("""
                INSERT INTO order_batches(order_code,session_id,submitted_by_client_id,submitted_at,editable_until,edit_policy,idempotency_key,request_hash)
                VALUES(?,?,?,?,?,?,?,?)
                """, code, sessionId, client.id(), Timestamp.from(now), Timestamp.from(now.plusSeconds(180)), EDIT_POLICY, key, hash);
        long batch = jdbc.queryForObject("SELECT id FROM order_batches WHERE order_code=?", Long.class, code);
        for (int i = 0; i < lines.size(); i++) {
            var row = menus.get(i); var line = lines.get(i);
            jdbc.update("""
                    INSERT INTO order_items(session_id,order_batch_id,menu_item_id,item_code,item_name,unit_price,quantity)
                    VALUES(?,?,?,?,?,?,?)
                    """, sessionId, batch, line.menuItemId(), row.get("code"), row.get("name"), row.get("price"), line.quantity().intValueExact());
        }
        return read(batch, sessionId);
    }

    Order read(long batch, long sessionId) {
        var items = jdbc.query("SELECT * FROM order_items WHERE session_id=? AND order_batch_id=? ORDER BY id", (r, n) ->
                new Item(r.getString("id"), r.getString("menu_item_id"), r.getString("item_name"), r.getBigDecimal("unit_price").toPlainString(),
                        r.getInt("quantity"), r.getBigDecimal("line_total").toPlainString(), r.getString("status"), r.getString("version")), sessionId, batch);
        var until = jdbc.queryForObject("SELECT editable_until FROM order_batches WHERE id=?", Timestamp.class, batch);
        var total = items.stream().filter(i -> !i.status().equals("CANCELLED")).map(i -> new BigDecimal(i.lineTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
        var sessionTotal = jdbc.queryForObject("SELECT COALESCE(SUM(line_total),0) FROM order_items WHERE session_id=? AND status<>'CANCELLED'", BigDecimal.class, sessionId);
        return new Order(Long.toString(batch), Long.toString(sessionId), total.toPlainString(), sessionTotal.toPlainString(), until.toInstant().toString(), items, "Đặt món thành công!");
    }
    static void key(String key) {
        if (key == null || !key.matches("[!-~]{1,64}")) throw bad("Vui lòng cung cấp mã Idempotency-Key hợp lệ, tối đa 64 ký tự ASCII không có khoảng trắng.");
    }
    static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}

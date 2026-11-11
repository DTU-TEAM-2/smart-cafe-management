package vn.codegym.smartcafe.sales;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.codegym.smartcafe.billing.BillReader;
import vn.codegym.smartcafe.billing.BillSnapshot;

@Service
public class SalesService {
    private final JdbcClient jdbc;
    private final BillReader bills;
    private final Clock clock;

    public SalesService(JdbcClient jdbc, BillReader bills, Clock clock) {
        this.jdbc = jdbc; this.bills = bills; this.clock = clock;
    }

    public record Table(String id, String code, String name, String operationalStatus, String occupancy,
            String sessionCode, String sessionStatus, long pendingWaiterRequests, long pendingBillRequests,
            long submittedOrderItems) {}
    public record Item(String id, String itemCode, String itemName, String unitPrice, int quantity,
            String lineTotal, String status) {}
    public record Request(String id, String type, String status, Instant createdAt) {}
    public record Services(String tableId, String sessionCode, List<Item> items, String totalAmount,
            List<Request> activeRequests, CurrentBill currentBill) {}
    public record CurrentBill(String id, String status) {}
    public record BillCommand(String expectedSessionCode, String idempotencyKey) {}
    private record Session(long id, String code, String status) {}
    private record Actor(long id, String name, String role) {}

    @Transactional(readOnly = true)
    public List<Table> tables() {
        return jdbc.sql("""
                SELECT t.id,t.code,t.name,t.status,s.session_code,s.status session_status,
                    (SELECT COUNT(*) FROM service_requests r WHERE r.session_id=s.id
                        AND r.request_type='WAITER' AND r.status='PENDING') waiter_count,
                    (SELECT COUNT(*) FROM service_requests r WHERE r.session_id=s.id
                        AND r.request_type='BILL' AND r.status='PENDING') bill_count,
                    (SELECT COUNT(*) FROM order_items o WHERE o.session_id=s.id AND o.status='SUBMITTED') order_count
                FROM cafe_tables t LEFT JOIN table_sessions s ON s.table_id=t.id AND s.status IN ('OPEN','CHECKOUT')
                WHERE t.status<>'ARCHIVED' ORDER BY t.table_number IS NULL,t.table_number,t.id
                """).query((r, n) -> new Table(r.getString("id"), r.getString("code"), r.getString("name"),
                        r.getString("status"), r.getString("session_code") == null ? "EMPTY" : "OCCUPIED",
                        r.getString("session_code"), r.getString("session_status"),
                        r.getLong("waiter_count"), r.getLong("bill_count"), r.getLong("order_count"))).list();
    }

    @Transactional(readOnly = true)
    public Services services(long tableId) {
        requireTable(tableId, false);
        var sessions = jdbc.sql("""
                SELECT id,session_code,status FROM table_sessions WHERE table_id=? AND status IN ('OPEN','CHECKOUT')
                """).param(tableId).query((r, n) -> new Session(r.getLong("id"), r.getString("session_code"), r.getString("status"))).list();
        if (sessions.isEmpty()) return new Services(Long.toString(tableId), null, List.of(), "0", List.of(), null);
        var session = sessions.getFirst();
        var items = items(session.id(), false);
        var requests = jdbc.sql("""
                SELECT id,request_type,status,created_at FROM service_requests
                WHERE session_id=? AND status IN ('PENDING','ACKNOWLEDGED') ORDER BY id
                """).param(session.id()).query((r, n) -> new Request(r.getString("id"), r.getString("request_type"),
                        r.getString("status"), r.getTimestamp("created_at").toInstant())).list();
        var bill = jdbc.sql("SELECT id,status FROM bills WHERE current_session_id=?").param(session.id())
                .query((r,n) -> new CurrentBill(r.getString("id"),r.getString("status"))).optional().orElse(null);
        return new Services(Long.toString(tableId), session.code(), items, total(items).toPlainString(), requests, bill);
    }

    @Transactional
    public BillSnapshot createBill(long tableId, BillCommand command, String username) {
        if (command == null) bad("Bill command is required");
        bounded(command.expectedSessionCode(), 64, "expectedSessionCode");
        bounded(command.idempotencyKey(), 64, "idempotencyKey");
        // The physical table serializes staff billing commands; the session and item locks protect the snapshot.
        requireTable(tableId, true);
        var session = jdbc.sql("""
                SELECT id,session_code,status FROM table_sessions WHERE table_id=? AND session_code=? FOR UPDATE
                """).param(tableId).param(command.expectedSessionCode())
                .query((r, n) -> new Session(r.getLong("id"), r.getString("session_code"), r.getString("status")))
                .optional().orElseThrow(() -> conflict("Session has changed"));
        String hash = hash(tableId + ":" + session.code());
        var actor = jdbc.sql("SELECT id,full_name,role FROM users WHERE username=? AND status='ACTIVE' AND role IN ('STAFF','ADMIN')")
                .param(username).query((r, n) -> new Actor(r.getLong("id"),r.getString("full_name"),r.getString("role")))
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active staff identity required"));
        var prior = jdbc.sql("SELECT id,request_hash FROM bills WHERE session_id=? AND idempotency_key=?")
                .param(session.id()).param(command.idempotencyKey()).query((r, n) -> new String[] {
                        r.getString("id"), r.getString("request_hash")}).optional();
        if (prior.isPresent()) {
            if (!hash.equals(prior.get()[1])) throw conflict("Idempotency key payload mismatch");
            var snapshot = bills.get(Long.parseLong(prior.get()[0]));
            if (!snapshot.status().equals("PENDING") && !actor.role().equals("ADMIN"))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only managers can view completed invoices");
            return snapshot;
        }
        if (!List.of("OPEN", "CHECKOUT").contains(session.status())) throw conflict("Session has closed");
        if (jdbc.sql("SELECT COUNT(*) FROM bills WHERE session_id=? AND status<>'VOID'")
                .param(session.id()).query(Long.class).single() != 0) throw conflict("Session already has a current bill");
        var items = items(session.id(), true);
        if (items.isEmpty()) bad("No Service to Pay");
        var table = jdbc.sql("SELECT code,name FROM cafe_tables WHERE id=?").param(tableId)
                .query((r, n) -> new String[] {r.getString("code"), r.getString("name")}).single();
        String code = "HD-" + UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO bills(bill_code,session_id,created_by_user_id,creator_name_snapshot,
                    table_code_snapshot,table_name_snapshot,total_amount,status,created_at,idempotency_key,request_hash)
                VALUES (?,?,?,?,?,?,?,'PENDING',?,?,?)
                """).param(code).param(session.id()).param(actor.id()).param(actor.name())
                .param(table[0]).param(table[1]).param(total(items)).param(java.sql.Timestamp.from(clock.instant()))
                .param(command.idempotencyKey()).param(hash).update();
        long billId = jdbc.sql("SELECT id FROM bills WHERE bill_code=?").param(code).query(Long.class).single();
        for (var item : items) jdbc.sql("""
                INSERT INTO bill_items(bill_id,session_id,order_item_id,item_code,item_name,unit_price,quantity)
                VALUES (?,?,?,?,?,?,?)
                """).param(billId).param(session.id()).param(Long.parseLong(item.id())).param(item.itemCode())
                .param(item.itemName()).param(new BigDecimal(item.unitPrice())).param(item.quantity()).update();
        jdbc.sql("""
                UPDATE service_requests SET status='RESOLVED',resolved_at=?,resolved_by_user_id=?
                WHERE session_id=? AND request_type='BILL' AND status IN ('PENDING','ACKNOWLEDGED')
                """).param(java.sql.Timestamp.from(clock.instant())).param(actor.id()).param(session.id()).update();
        return bills.get(billId);
    }

    private List<Item> items(long sessionId, boolean lock) {
        return jdbc.sql("""
                SELECT id,item_code,item_name,unit_price,quantity,line_total,status FROM order_items
                WHERE session_id=? AND status<>'CANCELLED' ORDER BY id
                """ + (lock ? " FOR UPDATE" : "")).param(sessionId).query((r, n) -> new Item(r.getString("id"),
                        r.getString("item_code"), r.getString("item_name"), r.getBigDecimal("unit_price").toPlainString(),
                        r.getInt("quantity"), r.getBigDecimal("line_total").toPlainString(), r.getString("status"))).list();
    }

    @Transactional
    public void acknowledge(long tableId, long requestId, String expectedSessionCode, String username) {
        bounded(expectedSessionCode,64,"expectedSessionCode");
        requireTable(tableId,true);
        long sessionId=jdbc.sql("""
                SELECT id FROM table_sessions WHERE table_id=? AND session_code=?
                AND status IN ('OPEN','CHECKOUT') FOR UPDATE
                """).param(tableId).param(expectedSessionCode).query(Long.class).optional()
                .orElseThrow(()->conflict("Session has changed"));
        long actor=jdbc.sql("SELECT id FROM users WHERE username=? AND status='ACTIVE' AND role IN ('STAFF','ADMIN')")
                .param(username).query(Long.class).optional()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Active staff identity required"));
        var state=jdbc.sql("SELECT status FROM service_requests WHERE id=? AND session_id=? FOR UPDATE")
                .param(requestId).param(sessionId).query(String.class).optional()
                .orElseThrow(()->conflict("Request does not belong to the current session"));
        if (state.equals("ACKNOWLEDGED")) return;
        if (!state.equals("PENDING")) throw conflict("Request is no longer pending");
        jdbc.sql("UPDATE service_requests SET status='ACKNOWLEDGED',acknowledged_at=?,acknowledged_by_user_id=? WHERE id=? AND session_id=? AND status='PENDING'")
                .param(java.sql.Timestamp.from(clock.instant())).param(actor).param(requestId).param(sessionId).update();
    }

    private void requireTable(long id, boolean lock) {
        if (id <= 0) bad("tableId must be positive");
        var status = jdbc.sql("SELECT status FROM cafe_tables WHERE id=?" + (lock ? " FOR UPDATE" : ""))
                .param(id).query(String.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));
        if (status.equals("ARCHIVED")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
    }
    private static BigDecimal total(List<Item> items) {
        return items.stream().map(i -> new BigDecimal(i.unitPrice()).multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static void bounded(String value, int max, String field) {
        if (value == null || value.isBlank() || !value.matches("[!-~]{1," + max + "}")) bad(field + " must contain 1-" + max + " printable ASCII characters without spaces");
    }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void bad(String reason) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
    private static ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT, reason); }
}

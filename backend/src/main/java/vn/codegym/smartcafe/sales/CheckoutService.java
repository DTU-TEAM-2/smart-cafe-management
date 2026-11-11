package vn.codegym.smartcafe.sales;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;
import vn.codegym.smartcafe.billing.BillReader;
import vn.codegym.smartcafe.billing.BillSnapshot;

@Service
public class CheckoutService {
    private final JdbcClient jdbc;
    private final BillReader bills;
    private final Clock clock;

    public CheckoutService(JdbcClient jdbc, BillReader bills, Clock clock) {
        this.jdbc = jdbc;
        this.bills = bills;
        this.clock = clock;
    }

    public record ConfirmPayment(String method, String referenceCode, String idempotencyKey) {}
    public record Payment(String id, String method, String amount, String referenceCode, Instant receivedAt) {}
    public record Receipt(BillSnapshot bill, Payment payment) {}
    private record Payable(long sessionId, String status, BigDecimal amount) {}

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Receipt confirmPayment(long billId, ConfirmPayment command, String username) {
        long actor = admin(username);
        if (command == null || !List.of("CASH", "BANK_TRANSFER").contains(command.method() == null ? "" : command.method()))
            throw bad("Payment method must be CASH or BANK_TRANSFER");
        String key = command.idempotencyKey();
        if (key == null || !key.matches("[!-~]{1,64}")) throw bad("idempotencyKey must contain 1-64 printable ASCII characters");
        String reference = command.referenceCode() == null ? null : command.referenceCode().strip();
        if (reference != null && (reference.isEmpty() || reference.length() > 128)) throw bad("Invalid referenceCode");

        long sessionId = jdbc.sql("SELECT session_id FROM bills WHERE id=?").param(billId).query(Long.class)
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found"));
        // Lock the session before the bill, matching the lock order used by create and reset.
        var sessionStatus = jdbc.sql("SELECT status FROM table_sessions WHERE id=? FOR UPDATE")
                .param(sessionId).query(String.class).single();
        var bill = jdbc.sql("SELECT session_id,status,total_amount FROM bills WHERE id=? FOR UPDATE")
                .param(billId).query((r,n) -> new Payable(r.getLong("session_id"),r.getString("status"),r.getBigDecimal("total_amount"))).single();
        String hash = hash(billId + "\n" + command.method() + "\n" + (reference == null ? "" : reference));
        var replay = jdbc.sql("SELECT bill_id,request_hash FROM payments WHERE idempotency_key=? FOR UPDATE")
                .param(key).query((r,n) -> new String[] {r.getString("bill_id"),r.getString("request_hash")}).optional();
        if (replay.isPresent()) {
            if (!replay.get()[0].equals(Long.toString(billId)) || !replay.get()[1].equals(hash))
                throw conflict("Payment key belongs to a different request");
            return receipt(billId);
        }
        if (!bill.status().equals("PENDING") || !List.of("OPEN","CHECKOUT").contains(sessionStatus))
            throw conflict("Bill is not payable");
        rejectUnbilledItems(billId, bill.sessionId());
        var now = Timestamp.from(clock.instant());
        jdbc.sql("""
                INSERT INTO payments(bill_id,method,amount,reference_code,received_by_user_id,received_at,idempotency_key,request_hash)
                VALUES(?,?,?,?,?,?,?,?)
                """).param(billId).param(command.method()).param(bill.amount()).param(4,reference,java.sql.Types.VARCHAR)
                .param(actor).param(now).param(key).param(hash).update();
        jdbc.sql("UPDATE bills SET status='PAID',paid_at=?,version=version+1 WHERE id=? AND status='PENDING'")
                .param(now).param(billId).update();
        return receipt(billId);
    }

    @Transactional
    public void resetPaidSession(long tableId, String expectedSessionCode, String username) {
        long actor = admin(username);
        if (expectedSessionCode == null || !expectedSessionCode.matches("[!-~]{1,64}")) throw bad("Expected session code is required");
        jdbc.sql("SELECT id FROM cafe_tables WHERE id=? AND status<>'ARCHIVED' FOR UPDATE").param(tableId).query(Long.class)
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Table not found"));
        long sessionId = jdbc.sql("""
                SELECT id FROM table_sessions WHERE table_id=? AND session_code=? AND status IN ('OPEN','CHECKOUT') FOR UPDATE
                """).param(tableId).param(expectedSessionCode).query(Long.class).optional()
                .orElseThrow(() -> conflict("Session has changed"));
        long billId = jdbc.sql("""
                SELECT b.id FROM bills b JOIN payments p ON p.bill_id=b.id AND p.amount=b.total_amount
                WHERE b.session_id=? AND b.status='PAID' FOR UPDATE
                """).param(sessionId).query(Long.class).optional()
                .orElseThrow(() -> conflict("Only a confirmed paid session can be reset"));
        rejectUnbilledItems(billId,sessionId);
        var now = Timestamp.from(clock.instant());
        jdbc.sql("UPDATE session_clients SET revoked_at=? WHERE session_id=? AND revoked_at IS NULL").param(now).param(sessionId).update();
        jdbc.sql("""
                UPDATE service_requests SET status='CANCELLED',resolved_at=?,resolved_by_user_id=?
                WHERE session_id=? AND status IN ('PENDING','ACKNOWLEDGED')
                """).param(now).param(actor).param(sessionId).update();
        jdbc.sql("UPDATE table_sessions SET status='CLOSED',ended_at=?,closed_by_user_id=?,version=version+1 WHERE id=?")
                .param(now).param(actor).param(sessionId).update();
    }

    private void rejectUnbilledItems(long billId,long sessionId) {
        // Order producers must also serialize writes on the session row. Lock existing order rows
        // here and reject any quantity/price changes since the invoice snapshot was taken.
        var changed = jdbc.sql("""
                SELECT o.id FROM order_items o LEFT JOIN bill_items bi ON bi.order_item_id=o.id AND bi.bill_id=?
                WHERE o.session_id=? AND ((o.status<>'CANCELLED' AND (bi.id IS NULL OR o.quantity<>bi.quantity
                    OR o.unit_price<>bi.unit_price)) OR (o.status='CANCELLED' AND bi.id IS NOT NULL))
                LIMIT 1 FOR UPDATE
                """).param(billId).param(sessionId).query(Long.class).optional();
        if (changed.isPresent()) throw conflict("Orders changed after bill creation; reconcile before checkout");
    }

    private Receipt receipt(long id) {
        var payment = jdbc.sql("SELECT id,method,amount,reference_code,received_at FROM payments WHERE bill_id=?")
                .param(id).query((r,n) -> new Payment(r.getString("id"),r.getString("method"),
                        r.getBigDecimal("amount").toPlainString(),r.getString("reference_code"),r.getTimestamp("received_at").toInstant())).single();
        return new Receipt(bills.get(id),payment);
    }

    private long admin(String username) {
        return jdbc.sql("SELECT id FROM users WHERE username=? AND role='ADMIN' AND status='ACTIVE'")
                .param(username).query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,"Active manager required"));
    }

    private static String hash(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}

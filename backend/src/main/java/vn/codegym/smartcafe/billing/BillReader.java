package vn.codegym.smartcafe.billing;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BillReader {
    private final JdbcClient jdbc;

    public BillReader(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public BillSnapshot get(long id) {
        var header = jdbc.sql("""
                SELECT b.id,b.bill_code,b.creator_name_snapshot,b.table_code_snapshot,b.table_name_snapshot,
                    b.total_amount,b.status,b.created_at,b.paid_at,s.session_code
                FROM bills b JOIN table_sessions s ON s.id=b.session_id
                WHERE b.id=?
                """).param(id).query((r, n) -> new BillSnapshot(r.getString("id"), r.getString("bill_code"),
                        r.getString("session_code"), r.getString("creator_name_snapshot"),
                        r.getString("table_code_snapshot"), r.getString("table_name_snapshot"),
                        r.getBigDecimal("total_amount").toPlainString(), r.getString("status"),
                        r.getTimestamp("created_at").toInstant(),
                        r.getTimestamp("paid_at") == null ? null : r.getTimestamp("paid_at").toInstant(), null))
                .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found"));
        var items = jdbc.sql("""
                SELECT order_item_id,item_code,item_name,unit_price,quantity,line_total
                FROM bill_items WHERE bill_id=? ORDER BY id
                """).param(id).query((r, n) -> new BillSnapshot.Item(r.getString("order_item_id"),
                        r.getString("item_code"), r.getString("item_name"),
                        r.getBigDecimal("unit_price").toPlainString(), r.getInt("quantity"),
                        r.getBigDecimal("line_total").toPlainString())).list();
        return new BillSnapshot(header.id(), header.billCode(), header.sessionCode(), header.creatorName(),
                header.tableCode(), header.tableName(), header.totalAmount(), header.status(),
                header.createdAt(), header.paidAt(), items);
    }
}

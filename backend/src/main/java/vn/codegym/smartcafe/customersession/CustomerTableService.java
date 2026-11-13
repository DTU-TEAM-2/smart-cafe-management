package vn.codegym.smartcafe.customersession;

import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerTableService {
    private static final Pattern QR = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private final JdbcClient jdbc;

    CustomerTableService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record TableContext(String tableId, String tableCode, String tableName) { }
    private record TableRow(String id, String code, String name, String status) { }

    @Transactional(readOnly = true)
    public TableContext resolve(String qrPublicId) {
        if (qrPublicId == null || !QR.matcher(qrPublicId).matches()) {
            throw new CustomerTableException(HttpStatus.BAD_REQUEST, "Mã QR không hợp lệ.");
        }
        var table = jdbc.sql("SELECT id,code,name,status FROM cafe_tables WHERE qr_public_id=?")
                .param(qrPublicId)
                .query((row, index) -> new TableRow(row.getString("id"), row.getString("code"),
                        row.getString("name"), row.getString("status")))
                .optional().orElseThrow(() -> new CustomerTableException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn."));
        if (table.status().equals("ARCHIVED")) {
            throw new CustomerTableException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn.");
        }
        if (!table.status().equals("ACTIVE")) {
            throw new CustomerTableException(HttpStatus.CONFLICT, "Bàn hiện không phục vụ.");
        }
        return new TableContext(table.id(), table.code(), table.name());
    }
}

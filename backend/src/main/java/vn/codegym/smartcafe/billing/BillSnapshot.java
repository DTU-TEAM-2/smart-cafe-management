package vn.codegym.smartcafe.billing;

import java.time.Instant;
import java.util.List;

public record BillSnapshot(String id, String billCode, String sessionCode, String creatorName,
        String tableCode, String tableName, String totalAmount, String status, Instant createdAt,
        Instant paidAt, List<Item> items) {
    public record Item(String orderItemId, String itemCode, String itemName, String unitPrice,
            int quantity, String lineTotal) {}
}

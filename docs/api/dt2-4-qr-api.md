# DT2-4 — Tra bàn từ QR (phần độc lập của Chức năng 2)

`GET /api/v1/customer/tables/qr/{qrPublicId}` là API public cho khách chưa đăng nhập. Nó chỉ đọc ngữ cảnh bàn để frontend có thể mở Menu cho đúng bàn. Không cấp quyền order, không thay thế xác thực phiên, không tự mở phiên trong GET.

## Response

```json
{
  "tableId": "81001",
  "tableCode": "QR_TEST_A",
  "tableName": "Bàn QR A"
}
```

Ví dụ dùng dữ liệu synthetic ở schema `_test`. Các ID được serialize thành chuỗi. Không trả `sessionId`, `sessionCode`, `accessToken`, `join_code_hash`, danh sách khách hoặc dữ liệu các phiên cũ.

| Trường hợp | HTTP | Detail tiếng Việt |
| --- | --- | --- |
| QR đủ định dạng UUID, bàn ACTIVE | 200 | Context của đúng bàn |
| QR sai định dạng | 400 | Mã QR không hợp lệ. |
| QR không tồn tại hoặc bàn ARCHIVED | 404 | Không tìm thấy bàn. |
| Bàn OUT_OF_SERVICE | 409 | Bàn hiện không phục vụ. |
| DB/transaction không khả dụng | 503 | Hiện chưa kiểm tra được mã QR. Vui lòng thử lại. |

Lỗi service dùng `application/problem+json`, có `status`, `title`, `detail`; không lộ SQL/JDBC. Security giữ deny-by-default; POST trên route này và GET tra bảng theo ID số không tự được mở public. Security errors 403 giữ contract của filter hiện có.

## QR và frontend

Giá trị QR là `cafe_tables.qr_public_id` hiện hữu, không tạo lại QR của bàn hoặc sửa bảng. Đối chiếu mã chính xác theo collation ASCII binary của DDL fixture. Frontend giữ ngữ cảnh QR khi chuyển tới Menu; API này không dựng UI, mã ảnh QR hay tự chọn URL public/deployment chưa được chốt.

Theo yêu cầu người dùng, khách phải quét QR và dùng Menu ngay, không đăng nhập. Backend Menu là dependency PR #9. PR tra QR là stacked PR trên branch Menu để không gộp code hai chức năng trong một diff review.

## Giới hạn phiên

Yêu cầu tự mở phiên và cấp quyền đặt món còn bị chặn bởi `table_sessions.opened_by_user_id NOT NULL` FK tới users, trong khi khách chưa đăng nhập không có user ID. Không tự ghi tài khoản ADMIN, tạo account giả, đổi schema hoặc nới FK. Xem [nguồn và blocker](../handover/dt2-4-table-session.md).

Trước khi tiếp tục phần phiên, chủ DB/PO cần ghi nhận cách biểu diễn người mở là khách vãng lai và đối chiếu QA-02; thời hạn token, nhiều thiết bị và quét lại khi CHECKOUT cũng cần hợp đồng được duyệt. Chức năng 2 chưa được đánh dấu hoàn tất chỉ nhờ API tra QR.

## Kiểm thử

- Controller tests cho public GET, context không credential, 400/404/503 và không mở thêm route ghi.
- MySQL integration test với hai bàn, OUT_OF_SERVICE/ARCHIVED, QR sai/không tồn tại và gọi lặp không tạo phiên.
- Postman collection `docs/qa/postman/dt2-4-qr-api.postman_collection.json`, chỉ gọi các API đọc/ca từ chối. Cổng local kiểm thử mặc định `18084`; bàn synthetic dùng QR mẫu cố định trong fixture QR riêng.

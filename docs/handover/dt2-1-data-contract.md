# DT2-14 — Hợp đồng dữ liệu

Nguồn bắt buộc: [schema và sơ đồ do chủ repo xác nhận](../architecture/database-contract.md). Runtime dùng smart_cafe, không có seed hay database trình diễn trong cấu hình chạy.

| Dữ liệu | Quan hệ sử dụng |
| --- | --- |
| Top món mới | menu_items.category_id → menu_categories.id; cả hai active; created_at/id; LIMIT 5 |
| Top gọi nhiều | menu_items → v_menu_item_order_totals; view đọc SUM(order_items.quantity) trừ CANCELLED |
| Ảnh món | menu_items.image_media_id → media_assets.id, chỉ media chưa deleted_at; thiếu ảnh không mất món |
| Hồ sơ | users → staff_profiles/media_assets; chỉ user của token đã xác minh |
| Thông báo order | order_batches → order_items/table_sessions → cafe_tables; một ID cho một batch còn món SUBMITTED |
| Gọi phục vụ | service_requests → table_sessions/session_clients; chỉ WAITER PENDING trong phiên hoạt động |

Không thêm bảng Header/Footer/notification hoặc lặp table_id vào service_requests. Không đổi khóa/constraint/precision/index. Khóa idempotency và FK ghép giữ lịch sử đúng phiên; order_items lưu snapshot tên/giá, không xóa khi menu thay đổi. Tiền xử lý BigDecimal và truyền bằng string; thời gian lưu UTC, UI hiển thị Asia/Ho_Chi_Minh.

`backend/src/test/resources/schema.sql` được chụp từ SHOW CREATE của 20 bảng/4 view đang chạy. Chỉ bỏ definer, qualifier database và bộ đếm auto increment để tạo test schema riêng; không chép row hệ thống. Đây không phải migration để áp dụng lên smart_cafe. Test data chỉ tạo trong transaction ở *_test và rollback; không có seed dùng trong sản phẩm.

Ảnh nguồn có một số khác biệt với DDL đang chạy, đã ghi ở database-contract.md. DT2-1 không phụ thuộc bảng/cột chỉ có trong ảnh; không tự chế DDL để lấp chênh lệch. Nếu phần khác cần thay đổi, người phụ trách DB phải cung cấp/chốt migration trước.

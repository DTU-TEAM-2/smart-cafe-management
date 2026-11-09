# Database hệ thống — quy tắc bắt buộc

Chủ repo xác nhận sơ đồ ngày 06/11/2026: [ảnh nguồn](../handover/evidence/system-database-reference.png). Ứng dụng dùng MySQL `smart_cafe`; không dùng database xem thử và không tự thêm dữ liệu.

Đã đọc information_schema/SHOW CREATE từ database đang chạy. Có 20 bảng: auth_otp_challenges, auth_otp_limits, bill_items, bills, cafe_tables, feedback, feedback_images, media_assets, menu_categories, menu_items, news_images, news_posts, order_batches, order_items, payments, service_requests, session_clients, staff_profiles, table_sessions, users; cùng 4 view nghiệp vụ.

DT2-1 chỉ tái sử dụng quan hệ:

- menu_categories → menu_items → media_assets: tên/giá/ảnh/active/created_at.
- menu_items → order_items → order_batches → table_sessions → cafe_tables: top gọi nhiều và thông báo order đang chờ.
- service_requests → table_sessions/session_clients: thông báo gọi phục vụ.
- users → staff_profiles/media_assets: hồ sơ đúng người đăng nhập, role/status/auth_version tại DB quyết định quyền.

Không tạo bảng Header/Footer/notification trùng lặp. Không thêm trường hoặc thay FK/precision/constraint để khớp code. SQL kiểm thử `backend/src/test/resources/schema.sql` được chụp cấu trúc thực tế, không chứa row; view bỏ definer và qualifier smart_cafe chỉ để chạy trong DB test riêng. Đây không phải migration của hệ thống.

## Chênh lệch đã quan sát, không tự sửa

Ảnh có password_reset_tokens, trong khi database đang chạy không có bảng này. Một số precision trong ảnh khác DDL hiện tại: menu_items.price/order_items.unit_price và staff_profiles.salary hiện là DECIMAL(18,0). Ảnh biểu diễn active_table_id/current_session_id như cột; DDL hiện tại dùng cơ chế index/constraint tương ứng. DT2-1 không dựa vào các cột/bảng chỉ xuất hiện trong ảnh và không tự tạo chúng. Nếu task khác cần phần chênh lệch, chủ DB phải chốt DDL/migration trước.

Giữ kiểu tiền BigDecimal/string để đọc được precision trong cả nguồn tham chiếu và DDL hiện hành. Mọi dữ liệu kiểm thử chỉ nằm trong *_test, không ghi vào smart_cafe. Không suy ra hoặc nạp một tài khoản/người dùng/thông báo chỉ để lấp UI.

# Database hệ thống — nguồn chuẩn

**Bản chuẩn chủ repo cung cấp ngày 11/11/2026:** [sơ đồ database](../handover/evidence/system-database-reference-2026-11-11.jpg). Áp dụng cho toàn bộ dự án theo [rule database bắt buộc](../../.agents/rules/database-source-of-truth.md), không chỉ DT2-1. Runtime hiện được cấu hình dùng MySQL `smart_cafe`.

## Danh mục trong sơ đồ mới

| Nhóm | Bảng |
| --- | --- |
| Menu | `menu_categories`, `menu_items` |
| Vận hành | `cafe_tables`, `table_sessions`, `order_batches`, `order_items`, `session_clients`, `service_requests` |
| Thanh toán | `bills`, `bill_items`, `payments` |
| Tài khoản và xác thực | `users`, `staff_profiles`, `auth_otp_challenges`, `auth_otp_limits` |
| Nội dung và ảnh | `media_assets`, `news_posts`, `news_images`, `feedback`, `feedback_images` |

Tổng cộng **20 bảng**. Danh mục này giúp tìm nguồn, không thay DDL: tên/kiểu cột, nullability, khóa, default và giá trị trạng thái phải được đối chiếu chi tiết trước khi sử dụng. Không suy ra tất cả constraint/enum chỉ từ kiểu varchar hay đường nối trong ảnh. Ví dụ `session_clients.customer_user_id` và `feedback.customer_user_id` cho phép khách vãng lai theo ghi chú ảnh; không tự ép thành tài khoản đã đăng nhập. `feedback.rating` và `feedback.status` đã có trong bản mới, không tự tạo thêm trường tương tự.

## Quan hệ DT2-1 tái sử dụng

- `menu_categories` → `menu_items` → `media_assets`: tên/giá/ảnh/active/created_at.
- `menu_items` → `order_items` → `order_batches` → `table_sessions` → `cafe_tables`: top gọi nhiều và thông báo order đang chờ.
- `service_requests` → `table_sessions`/`session_clients`: thông báo gọi phục vụ.
- `users` → `staff_profiles`/`media_assets`: hồ sơ đúng người đăng nhập, role/status/auth_version tại DB quyết định quyền.

Không tạo bảng Header/Footer/notification trùng lặp. Không thêm trường hoặc thay FK/precision/constraint để khớp code. SQL `backend/src/test/resources/schema.sql` là fixture cấu trúc dùng cho DB test riêng, không phải migration hoặc nguồn có quyền thay thế sơ đồ chủ repo.

## Bằng chứng DDL và lịch sử đối chiếu

Lần kiểm tra DDL trước đây (06/11/2026) đã đọc `information_schema`/`SHOW CREATE` của database lúc đó: có 20 bảng và 4 view `v_menu_item_order_totals`, `v_paid_item_sales`, `v_daily_revenue`, `v_cafe_table_occupancy`. Fixture test lưu cấu trúc không chứa row; view bỏ definer và qualifier `smart_cafe` chỉ để chạy trong DB test riêng. Các view này có nguồn DDL được ghi nhận, không phải bảng nghiệp vụ bổ sung từ ảnh. Không tự tạo thêm view dựa trên suy đoán.

[Ảnh ngày 06/11](../handover/evidence/system-database-reference.png) chỉ giữ làm lịch sử: có `password_reset_tokens`, một số precision khác và `table_sessions.active_table_id`; các chi tiết này không có trong ảnh chuẩn mới, không dùng chúng để tự bổ sung schema. **Ảnh mới vẫn có `bills.current_session_id`**; trường này phải được đối chiếu riêng với DDL trước khi sử dụng, không được tự bỏ hoặc thêm vì dựa vào nhận xét của lần đối chiếu cũ. Những chênh lệch ghi nhận trước đây không chứng minh cấu trúc database ngày 11/11.

Lần cập nhật rule ngày 11/11 chỉ lưu/đối chiếu tài liệu và fixture đã có; **không đọc lại DB đang chạy, không xác nhận DB hiện tại khớp hoàn toàn sơ đồ mới và không chạy DDL/seed**. Task tiếp theo có persistence phải đọc DDL read-only, ghi chênh lệch cụ thể tại đây hoặc câu hỏi mở và dừng phần phụ thuộc cho tới khi được chốt. Đừng lấy bản fixture cũ để che chênh lệch.

## Khi cần bổ sung hoặc có chênh lệch

Ghi task, nguồn ảnh/DDL và ngày đối chiếu, bảng/cột/constraint, khác biệt, hành vi bị ảnh hưởng, người cần quyết định và link kết luận. Trước khi có kết luận, không tự thêm migration, đổi entity để tạo schema, tắt constraint hay nạp dữ liệu cho code chạy tạm.

Giữ tiền decimal/BigDecimal và biểu diễn chính xác qua API theo DDL đã chốt. Dữ liệu kiểm thử chỉ ở `_test`; không tự tạo người dùng, đơn, thanh toán hay thông báo vào DB hệ thống để lấp UI. Việc nhập dữ liệu ngày 06/11 là [yêu cầu riêng đã ghi nhận của chủ repo](../handover/2026-11-06-owner-requested-home-data.md), không phải quyền seed mặc định cho các task khác.

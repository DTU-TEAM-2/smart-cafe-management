# Hợp đồng API và dữ liệu

Đây là quy ước cho phần triển khai tiếp theo. API hiện có: `GET /actuator/health` và public `GET /api/v1/home/highlights`. MySQL đã được người dùng xác nhận cho DT2-1; cơ chế đăng nhập còn thuộc task riêng. Các ví dụ `/api/v1` khác chưa triển khai.

## HTTP và hợp đồng

- REST nghiệp vụ dưới `/api/v1`, JSON camelCase, resource danh từ số nhiều. Cập nhật breaking phải có quyết định version/chuyển đổi.
- `200` đọc/cập nhật thành công; `201` tạo kèm `Location`; `204` khi không có body; `400` payload/validation sai; `401` thiếu xác thực khi đã triển khai auth; `403` không có quyền; `404` không tồn tại/không được tiết lộ; `409` xung đột trạng thái/trùng; `429` giới hạn truy cập khi có cấu hình.
- Dùng DTO và allowlist trường nhận vào. Không nhận role, giá chốt, tổng tiền, trạng thái thanh toán từ client như sự thật.
- Lỗi nghiệp vụ dùng Problem Details (`application/problem+json`) với `type`, `title`, `status`, `detail`, `instance`; thêm `code`, `fieldErrors`, `traceId` khi có nhu cầu. Không trả stack trace, SQL hay secret.
- Danh sách mới thống nhất phân trang `page` từ 0, `size` mặc định 10, tối đa 100; allowlist sort. Quy tắc SRS đặc thù như tìm nhân viên OR phải được giữ hoặc có QA thay đổi.
- Timestamp dùng ISO 8601 có offset/UTC; lưu instant và tính ngày kinh doanh theo `Asia/Ho_Chi_Minh`. Tiền VND xử lý bằng decimal, quy tắc precision/rounding ghi rõ trong hợp đồng.
- Chốt request/response, quyền, lỗi và ví dụ với người dùng API trước khi UI/API làm song song. OpenAPI được bổ sung khi bắt đầu API nghiệp vụ; chưa có endpoint thì không tạo spec giả.

## Nhất quán và vòng đời

Phiên phục vụ tách khỏi bàn vật lý. Một phiên có nhiều đợt gọi; giá/tên tại thời điểm gọi hoặc lập bill phải được chụp theo chính sách đã duyệt. Sửa giá menu không sửa lịch sử hóa đơn.

Mọi thao tác có thể bị gửi lại cần chiến lược idempotency rõ. Ràng buộc uniqueness và transaction/locking bảo vệ trạng thái khi hai thiết bị cùng gọi hoặc hai nhân viên cùng chốt bill. Không chỉ dựa vào việc disable nút frontend.

Yêu cầu thanh toán, lập/in bill, nhận tiền, đóng phiên và reset bàn là những hành động cần được đặc tả riêng. Chỉ ghi doanh thu theo mốc đã được PO xác nhận. WebSocket báo thay đổi; REST/database là nguồn phục hồi khi reconnect.

## Persistence và tích hợp

Chọn engine qua ADR trước khi thêm JPA/driver/migration. Khi đã chọn, dùng migration versioned, seed riêng cho development, khóa ngoại/index/unique có ý nghĩa. Không dùng `ddl-auto=update` cho môi trường dùng chung và không cascade xóa lịch sử tài chính.

Cloudinary/Firebase/mail/socket theo phạm vi đã duyệt; dùng adapter nhỏ khi có tích hợp thực tế. Không mặc định Firebase upload đồng nghĩa Firebase Auth/Firestore. Không lưu token thô hoặc URL chứa secret; giới hạn kiểu/kích cỡ upload và quyền truy cập trước khi bật upload.

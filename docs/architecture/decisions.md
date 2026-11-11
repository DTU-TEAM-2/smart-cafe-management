# Nhật ký quyết định kiến trúc

Ngày mở vòng mới: 06/11/2026.

| ID | Trạng thái | Nội dung |
| --- | --- | --- |
| ADR-001 | Theo yêu cầu chủ repo | Khởi tạo repo mới với main rỗng; rule/skill được review qua PR trước khi vào main. Không mang code/migration/lịch sử PR cũ sang. |
| ADR-002 | Đề xuất chờ chốt | Đánh giá lại nền tảng Spring/React/MySQL từ dự án trước dựa trên phạm vi mới; chưa tạo application skeleton trong PR nền tảng. |
| ADR-003 | Theo yêu cầu viết lại backend DT2-9, 2026-11-11 | Dùng Spring Boot/JDK 21 và MySQL như nền tảng tham chiếu legacy, viết mới module menu và schema V001. JDBC/Flyway quản lý transaction/migration; không nhập code/migration history/dữ liệu cũ. Auth chung vẫn ngoài phạm vi, API ADMIN nhận JWT từ issuer được cấu hình, không có tài khoản/bypass mặc định. Nguồn: `docs/handover/dt2-9-backend-request.md`. |

Quyết định được chấp nhận phải có người chốt, ngày, căn cứ và hệ quả; không dùng bảng này để tự phê duyệt nghiệp vụ.

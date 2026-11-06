# Nhật ký quyết định kiến trúc

Ngày mở vòng mới: 06/11/2026.

| ID | Trạng thái | Nội dung |
| --- | --- | --- |
| ADR-001 | Theo yêu cầu chủ repo | Khởi tạo repo mới với main rỗng; rule/skill được review qua PR trước khi vào main. Không mang code/migration/lịch sử PR cũ sang. |
| ADR-002 | Đề xuất chờ chốt | Đánh giá lại nền tảng Spring/React/MySQL từ dự án trước dựa trên phạm vi mới; chưa tạo application skeleton trong PR nền tảng. |
| ADR-003 | Chủ repo chốt 06/11/2026 | NV-DuyManh hoặc mentor haitan2408 quyết định merge qua PR. Approval trên GitHub là tùy chọn để một trong hai người có thể xử lý PR. Cả nhóm có thể review. GitHub bắt buộc CI và xử lý thảo luận. Căn cứ và cách thực thi được ghi trong [PR #2](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/2) và [thiết lập GitHub](../engineering/github-settings.md). |

DT2-1 triển khai Spring Boot/JDBC + React/Vite + MySQL theo stack/schema hiện hành để review, thay thế trạng thái chưa có skeleton của ADR-002. Home chỉ đọc, không tự DDL/seed; CI dùng database test riêng. Đây là lựa chọn kỹ thuật chờ review, không tự phê duyệt nghiệp vụ ranking hoặc auth. Xem [handoff](../handover/dt2-1-home-layout.md).

Quyết định được chấp nhận phải có người chốt, ngày, căn cứ và hệ quả; không dùng bảng này để tự phê duyệt nghiệp vụ.

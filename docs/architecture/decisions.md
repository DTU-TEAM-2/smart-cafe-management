# Nhật ký quyết định kiến trúc

Ngày mở vòng mới: 06/11/2026.

| ID | Trạng thái | Nội dung |
| --- | --- | --- |
| ADR-001 | Theo yêu cầu chủ repo | Khởi tạo repo mới với main rỗng; rule/skill được review qua PR trước khi vào main. Không mang code/migration/lịch sử PR cũ sang. |
| ADR-002 | Lịch sử; phần stack được thay bằng ADR-004 ngày 11/11/2026 | Ở PR nền tảng từng đề xuất đánh giá lại Spring/React/MySQL và chưa có application skeleton. Đây không còn là trạng thái stack chờ chốt hiện tại. |
| ADR-003 | Chủ repo chốt 06/11/2026 | NV-DuyManh hoặc mentor haitan2408 quyết định merge qua PR. Approval trên GitHub là tùy chọn để một trong hai người có thể xử lý PR. Cả nhóm có thể review. GitHub bắt buộc CI và xử lý thảo luận. Căn cứ và cách thực thi được ghi trong [PR #2](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/2) và [thiết lập GitHub](../engineering/github-settings.md). |
| ADR-004 | Chủ repo yêu cầu áp dụng 11/11/2026 theo trao đổi mentor | **BE Spring Boot, FE ReactJS, database MySQL bắt buộc; còn lại tùy chọn.** Nguồn: [ảnh trao đổi Tân Hải do chủ repo cung cấp](../handover/evidence/technology-constraints-2026-11-11.png), chủ repo yêu cầu ghi rule và đồng bộ tài liệu. Áp dụng toàn dự án theo [rule công nghệ](../../.agents/rules/technology-constraints.md); không tự đổi stack, không biến công cụ tùy chọn hoặc version hiện tại thành yêu cầu mới của mentor. |

DT2-1 triển khai Spring Boot/JDBC + React/Vite + MySQL. Stack ba thành phần chính đã được chốt tại ADR-004; JDBC/Vite là công cụ đang dùng, code và lựa chọn cụ thể vẫn qua review. Home chỉ đọc, không tự DDL/seed; CI dùng database test riêng. Quyết định stack không tự phê duyệt nghiệp vụ ranking hoặc auth. Xem [handoff](../handover/dt2-1-home-layout.md).

Quyết định được chấp nhận phải có người chốt, ngày, căn cứ và hệ quả; không dùng bảng này để tự phê duyệt nghiệp vụ.

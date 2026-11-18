# Bàn giao DDL Smart Cafe ngày 13/11/2026

Đọc MIGRATION-DECISION.md để thực hiện. Bộ bàn giao chọn schema hiện hữu trong Navicat làm cấu trúc đích, có đủ 20 bảng nghiệp vụ, 4 view và phần mở rộng News audience.

| Artifact | Nội dung |
| --- | --- |
| db/smart-cafe/V20261113_01__smart_cafe_schema.sql | DDL provision DB trống, version 20261113.1, sắp thứ tự FK, không có dữ liệu nghiệp vụ. |
| manifest.json | SHA-256, nguồn DDL, head PR #7 và develop đã quan sát. |
| MIGRATION-DECISION.md | Đích tích hợp, location, version, baseline description, xử lý Auth-local và legacy, phần còn phải sửa trong PR #7. |
| legacy-column-gaps.md | Các cột cần mapping khi nâng cấp legacy. |
| evidence/version-20261113.1-results.json | Lượt native Flyway dùng chính file/version của bộ này. |
| evidence/prior-results.json | Kết quả rehearsal trước đó với version test 1, có startup và HTTP trên preflight ứng viên. |
| evidence/prior-rehearsal-final.log | Log của lượt thử version test 1. |
| tools/FlywayHandoffProbe.java | Probe native Flyway chỉ cho schema test cục bộ. |
| erd-2026-11-11.jpg | Ảnh ERD gốc để đối chiếu. Hai cột audience có trong DDL mở rộng. |
| SHA256SUMS.txt | Dấu kiểm tra toàn bộ file bàn giao. |

Kết quả lượt version 20261113.1 là DB mới migrate 1 rồi 0, DB đủ schema baseline thủ công rồi migrate 0 và 0, giữ dữ liệu synthetic ở cả 20 bảng. Hai schema thử được dọn sau lượt chạy. Lượt thử trước đã kiểm backup/restore dữ liệu tổng hợp, startup và các HTTP Auth/Home/Menu/Sales đọc.

Startup của PR #7 với chính version mới còn chờ Auth sửa preflight/location theo quyết định. Backend gốc vẫn chặn marker baseline. File preflight ứng viên trước đây chỉ hỗ trợ version test 1, không đưa nguyên trạng vào runtime.

Người nhận tiếp theo là người phụ trách Database/Auth của PR #7 và chat Document đang điều phối ngữ cảnh Navicat. Code hiện tại của PR #7 vẫn ở revision ghi trong manifest. Các bước kiểm schema hiện tại đã dùng read-only. Bộ artifact là đầu vào triển khai và kiểm thử, chưa là kết quả rollout trên smart_cafe.

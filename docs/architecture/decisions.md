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

## ADR-005 — tích hợp menu với mô hình DB và auth chung (11/11/2026)

Chủ repo cung cấp lại sơ đồ DB mới khi đối chiếu cho PR #8; mô hình đích và chênh lệch với DDL local được ghi tại [database contract](database-contract.md#dt2-9--đối-chiếu-ngày-11112026-cho-pr-8). Menu dùng category_id/image_media_id, giá decimal(18,0); URL là trường dẫn xuất, bỏ description không có trong mô hình. Không có migration runtime hay thay đổi DB local. Kiểm thử dùng fixture main nguyên trạng trong schema riêng.

Lựa chọn triển khai để review: tái sử dụng HeaderTokenFilter của main và AUTH_TOKEN_SECRET, kiểm tra quyền từ users; bỏ cơ chế JWT riêng của nhánh menu. Giữ CSRF cho mutation. Không phát hành token, seed tài khoản hoặc triển khai auth mới. Không thay quyền merge tại ADR-003.

## ADR-006 — vòng đời OTP resend trong DT2-2 (12/11/2026)

Nguồn: yêu cầu trực tiếp của chủ repo ngày 12/11/2026 để sửa [P1 PR #7](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/7#discussion_r4545684172); chính sách ưu tiên và phạm vi được ghi tại [bàn giao](../handover/dt2-2-otp-retry-lifecycle.md). Không có yêu cầu gia hạn TTL sau resend trong handoff DT2-2 đang có; lựa chọn sau thực hiện phương án chủ repo yêu cầu ưu tiên.

- `sends` đếm lượt yêu cầu được tiếp nhận: 1 lúc tạo, tối đa 2 resend, bất kể kết quả SMTP. Readiness/configuration failure bị từ chối trước khi tiêu thụ budget cho cả real/dummy.
- `expires_at` cố định từ lúc tạo challenge; resend thành công/thất bại/dummy đều không gia hạn. `expiresInSeconds` của resend là số giây còn lại tại lúc tiếp nhận (làm tròn lên); `now >= expires_at` từ chối resend/verify.
- Giữ transaction và row lock trước SMTP. Mỗi resend hợp lệ reserve budget/cooldown một lần. SMTP thành công mới thay hash/ghi `SENT` và `sent_at`; SMTP lỗi ghi `FAILED`, giữ hash/attempts/used_at/sent_at/hạn cũ. Dummy không gửi email và không giả lập `SENT`.
- Không thêm bảng/cột, migration, outbox hoặc background retry. Lỗi DB/row count bất thường rollback, trả lỗi máy chủ; không bắt như lỗi SMTP. SMTP và DB commit chưa atomic: thư có thể đã được nhận trước khi transaction thất bại.

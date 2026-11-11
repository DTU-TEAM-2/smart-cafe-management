# DT2-9 — trạng thái khi mở PR backend

Ngày 2026-11-11. Nhánh backend được tạo từ main `99acaa4`; khi chuẩn bị PR, origin/main đã ở `cf4828a` sau PR #4 (DT2-1) và các quy định DB/stack mới. Tạo Draft PR theo yêu cầu chủ repo để review, chưa đề nghị merge/deploy.

## Bằng chứng hiện có

Backend độc lập trên nhánh: Maven verify 23/23 JUnit đạt; báo cáo 46/46 mục PASS, 109 response HTTP API và một ca DB read-only. JWT test-only; TC-41/46 dùng provider HTTP giả lập theo lựa chọn người dùng. Chưa smoke IdP hoặc Cloudinary thật. Xem `docs/reports/dt2-9-api/` để biết giới hạn và các lượt chạy trước.

## Blocker tích hợp với main mới

- Main có `backend/pom.xml`, application/config/security và CI riêng; nhánh này thêm các file cùng đường dẫn từ baseline cũ. Cần giải quyết conflict và kiểm thử bản tích hợp, không lấy kết quả độc lập thay kết quả tích hợp.
- Main yêu cầu schema hệ thống theo sơ đồ ngày 11/11 và DDL thực. V001 của nhánh này tạo catalog riêng theo yêu cầu trước đó; chưa đối chiếu DDL/schema mới và **không được áp dụng vào database hệ thống**. Chưa sửa persistence/migration theo suy đoán để qua review.
- Main dùng auth/header contract chung; nhánh này có Resource Server JWT và chain bảo mật riêng. Cần thống nhất với module auth, tránh hai chain/filter tranh quyền hoặc chặn API trang chủ.
- ADR-003 của nhánh cũ trùng số ADR-003 đã được chốt trên main; cần hòa giải nhật ký quyết định khi tích hợp, không ghi đè quyết định merge của chủ repo.

Không merge main vào nhánh một cách tự động để giữ các thay đổi cần quyết định rõ ràng. Không sửa nguồn schema, tạo thêm migration hoặc thay auth chung trong task chỉ yêu cầu mở PR. Reviewer cần tập trung hợp đồng DB, auth/security chain, cấu hình/CI và hồi quy DT2-1 trước khi chuyển PR sang Ready.

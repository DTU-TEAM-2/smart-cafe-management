# DT2-9 — bản tích hợp PR #8

Ngày 11/11/2026, nhánh feature/dt2-9-manage-menu-items-and-categories-backend tích hợp main cf4828a bằng merge commit, không rewrite lịch sử. Chủ repo yêu cầu xử lý conflict và chuyển PR sang Ready; không yêu cầu merge/deploy.

## Kết quả

Giữ CI, bootstrap, auth chung, ADR-003/004 và FE từ main. Menu dùng security chain giới hạn route, HeaderTokenFilter chung, users quyết định quyền; CSRF vẫn bắt buộc. Bỏ JWT/resource-server riêng, V001 và Flyway của bản độc lập. Không có migration đã áp dụng vào DB dùng chung bị sửa.

Persistence theo sơ đồ mới chủ repo cung cấp lại: PK số, category_id, image_media_id, decimal(18,0), media_assets. Bỏ description và input imageUrl; upload trả mediaId, response món dẫn xuất URL. [Đối chiếu DDL](../architecture/database-contract.md) ghi rõ DB local cũ không phù hợp và không bị thay đổi. Fixture cấu trúc main giữ nguyên; không tuyên bố constraint/default/index triển khai đã xác minh từ ảnh.

## Bằng chứng local

- backend/mvnw.cmd -o -B -ntp clean verify: **30/30 JUnit**, 0 failures/errors/skips, MySQL 8.0.46 smart_cafe_merge_test riêng từ fixture main.
- [HTTP report](dt2-9-api/README.md): **46/46 PASS**, provider giả lập TC-41/46 có nhãn, không Cloudinary thật.
- FE main giữ nguyên: npm run lint, npm run build, npm run test:ui -- --workers=2: **14/14 đạt**.
- node scripts/skills.mjs check <full main SHA>: registry 17/16, checksum 169 files; node --test scripts/skills.test.mjs: **9/9 đạt**.
- git diff --check loại inherited snapshots; JAR không có fixture/test/provider stub hoặc migration; schema.sql/FE/CI giữ nguyên so với main.

## Phạm vi review còn lại

Self-review không có blocker đã biết sau sửa lỗi tích hợp; review độc lập và quyết định merge vẫn theo ADR-003. Chưa smoke login/Cloudinary thật, chưa có SAST Aikido. DB triển khai phải được chủ DB chuẩn bị theo sơ đồ và đối chiếu DDL; không dùng DB local legacy cũ để chạy module. Kết quả CI GitHub được cập nhật trong PR, tách khỏi bằng chứng local trên.

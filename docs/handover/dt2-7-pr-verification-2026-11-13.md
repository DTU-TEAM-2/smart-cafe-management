# DT2-7 — kiểm chứng trước PR ngày 13/11/2026

Task: [DT2-7](https://duymanhdnvn20092005.atlassian.net/browse/DT2-7), quản lý tin tức. Người thực hiện/PR: NV-DuyManh. Nhánh `feature/dt2-7-news-backend` → `develop`.

## Revision và tích hợp

Code được kiểm tại `16c5f5618ba7636a39403a98446033990e0ef781`, đã ghép `develop@79896a993e9a0f620734201efa2f05ed60090b95`. Commit tài liệu tiếp theo không đổi code. Đã giải quyết 5 file xung đột, giữ QR khách hàng, menu preview, CI/Postman của develop. Không nhập source auth chưa merge.

## Kết quả chạy lại

| Kiểm tra | Kết quả | Phạm vi |
| --- | --- | --- |
| Maven wrapper verify | 132 test đạt; 0 lỗi, 0 bỏ qua | MySQL 8.0 Docker riêng `codex_dt27_review_test`, schema fixture đúng contract; container được dừng sau test |
| npm ci / lint / build | Đạt | Lockfile frontend hiện hành |
| Playwright Chromium | 96/96 đạt | Desktop/mobile, News/Login/Header/Home/Sales và menu mới từ develop |
| Skill registry/checksum | Đạt | 17 skill, 169 file kế thừa; đối chiếu SHA develop |
| Git Flow/routing | 17/17 đạt | Validator và định tuyến skill |
| git diff --check | Đạt | Loại snapshot skill kế thừa theo rule |

UI suite dùng response/WebSocket kiểm soát cho hồi quy; không trình bày kết quả này như đăng nhập DT2-2 hoặc Firebase cloud thật. Test persistence chạy MySQL thật, không H2/mock database. Không dùng dữ liệu hệ thống hoặc tự seed vào smart_cafe. Không chạy lại toàn bộ bài kiểm chứng issuer/emulator end-to-end trong vòng 13/11 này; bằng chứng 12/11 trong hợp đồng là lịch sử, không thay kết quả revision mới.

## Cấu hình và giới hạn cần reviewer biết

- Đọc [hợp đồng BE](dt2-7-backend-contract.md), [FE](dt2-7-frontend-contract.md), [Firebase miễn phí](dt2-7-firebase-setup.md) và [Q&A 19–20](dt2-7-qa-replies-2026-11-11.md).
- Login cần issuer DT2-2 thật, hiện ở PR #7; có thể dùng proxy riêng cùng DB/secret để tích hợp. Module này không tự cấp tài khoản/token.
- Hai cột `news_posts.audience_staff` / `audience_customer` và CHECK theo quyết định đã được chủ repo duyệt. Migration thủ công, không chạy khi startup. Header SQL giữ thông tin lịch sử lúc mới viết; trạng thái local đã áp dụng được ghi tại ADR-006. Kiểm DDL trước khi áp dụng ở máy khác và không chạy lại trên schema đã có cột.
- Ảnh chỉ dùng Storage Emulator loopback + MySQL `_test` trong phạm vi miễn phí. Live Storage tắt, không bật billing/Blaze hoặc chia key cho FE; chưa nghiệm thu cloud/IAM.
- Socket chỉ STAFF theo Q&A 20, tín hiệu sau commit; một instance, client đọc lại REST khi reconnect.
- PR ở trạng thái mở để nhóm review, cần CI và đủ approval theo Git Flow trước khi merge. Không coi việc mở PR là nghiệm thu/deploy.

Skills áp dụng: `cafe-workflow`, `cafe-quality`, `cafe-review`, `cafe-spring-api`, `vercel-react-best-practices` tại `.agents/skills/`; giữ ranh giới auth, dữ liệu và các thành phần đã tích hợp.

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

## Sửa hai góp ý review PR #15 — 13/11/2026

- **Media boundary:** POST/PUT chỉ nhận PNG/JPEG còn hiệu lực từ `FIREBASE` trong namespace uploader News. List/detail/private-image cùng kiểm nguồn; không trả URL ảnh avatar/menu/feedback đã bị liên kết từ trước. Không xóa hoặc chuyển quyền dữ liệu cũ. Fixture ảnh hợp lệ được đổi sang namespace News, giữ schema nguyên trạng.
- **Socket budget:** giữ chỗ nguyên tử trước upgrade, mặc định tối đa 256 tổng / 64 pending / 16 IP peer / 4 tài khoản STAFF mỗi instance; từ chối HTTP 429 trước upgrade hoặc close 1013 ở bước xác thực. Pending hết hạn 10 giây, thu hồi khi handshake/transport lỗi, đóng hoặc shutdown. Micrometer ghi số kết nối/pending/từ chối, không gắn thông tin cá nhân. Hợp đồng BE ghi đầy đủ cấu hình, giới hạn proxy/NAT và rate-limit cần triển khai trước production.
- Đã tái hiện trước sửa: 5 regression media thất bại (ảnh ngoài News vẫn được nhận và URL cũ vẫn trả về). Sau sửa, Maven verify trên MySQL 8.0 `_test` riêng **146/146 đạt**, 0 lỗi, 0 bỏ qua. Trong đó có 7 test socket budget, hai đợt 1.000 admission đồng thời, auth đồng thời, timeout, cleanup và kiểm số lượt revalidation DB trong budget.
- Test mạng WebSocket/Tomcat thật xác nhận kết nối thứ 17 cùng IP bị chặn **429 + Retry-After**, đóng một socket rồi nhận kết nối mới và xác thực `READY`. Stress admission là test Java đồng thời trên handler (session/auth test doubles), không phải benchmark 2.000 socket TCP hoặc chứng nhận chống DDoS production. Test socket hai client/after-commit/quyền vẫn đạt trong full verify.
- Không đổi frontend trong bản sửa; kết quả UI 96/96 ở revision trước là bằng chứng hồi quy trước đó. CI của commit sửa phải chạy lại trước merge. Không chạy cloud/billing hoặc thay schema/migration.

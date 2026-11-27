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

## Đồng bộ Auth đã merge vào develop — 13/11/2026

- Đã giải quyết xung đột ở 12 file với `develop@ffa4d95`. REST và WebSocket dùng cùng bộ xác minh token DT2-2. Frontend dùng luồng đăng nhập và khôi phục mật khẩu của DT2-2, xác minh phiên qua `/api/v1/auth/me` trước khi lưu, rồi chuyển trang theo vai trò.
- Maven verify trên MySQL 8.0 Docker riêng đạt **295/295**, 0 bỏ qua. Test tích hợp kiểm tra token do luồng login phát hành đọc được News. Frontend lint/build và Playwright desktop/mobile đạt **112/112**. Registry/checksum, Git Flow, định tuyến, launcher và kiểm tra whitespace đều đạt.
- Test dùng database riêng và không chạy Firebase cloud. Các quyết định schema và quy trình chạy migration hiện hành được giữ nguyên.

## Kiểm lại trực tiếp DT2-1 và DT2-7 trên bản tích hợp

Revision `c0fa0805c7bba38122e6f70768fccba9ddc30393`, ngày 13/11/2026. HTTP, WebSocket và Chromium dùng Auth trong cùng bản mã nguồn, MySQL 8.0 riêng và Firebase Storage Emulator chính thức. Kết quả **126/126 bước đạt**.

- ADMIN/STAFF/CUSTOMER đăng nhập qua API Auth thật. REST và socket kiểm quyền theo dữ liệu hiện tại, phiên bị thu hồi và thay đổi vai trò.
- Header mở đúng hồ sơ ADMIN/STAFF và hiển thị thông báo gọi món/phục vụ một lần từ các bản ghi thử nghiệm được kiểm soát.
- Tin tức tạo/sửa/lưu trữ/khôi phục đúng bài, nhóm nhận và ETag. Hai trình duyệt kiểm cập nhật STAFF sau commit và tải lại dữ liệu.
- PNG/JPEG được upload vào Firebase Storage Emulator, đọc qua API phân quyền và hiển thị trong trình duyệt. Có kiểm EXIF, ảnh riêng và quyền sau lưu trữ.
- Giao diện editor kiểm ở độ rộng 320, 390, 768 và 1440 px. Toàn bộ runtime và database do lượt kiểm tạo đã được dừng sau kiểm tra.

[Bằng chứng từng bước](evidence/dt2-1-dt2-7-live-check-2026-11-13.json). Tài khoản và dữ liệu thử nằm trong database riêng. Storage cloud và luồng tự cấp phiên khách tại bàn chưa được kiểm trong lượt này.

## Xử lý review tài nguyên và độ trễ — 18/11/2026

Đối chiếu hai góp ý ngày 17/11 trên `ff10950` trong PR #15. Trước sửa đã tái hiện hai ca upload/read giữ cả 2 connection Hikari khi Storage chờ, và hai ca callback commit không hoàn tất trong 1 giây khi socket bị chặn ở quy mô 64/256 subscriber giả lập.

- Ảnh dùng transaction ngắn cho metadata và quyền. Upload/download chạy ngoài transaction. Cleanup chạy sau khi transaction đã giải phóng tài nguyên; lỗi begin hoặc rollback chắc chắn thì xóa object, outcome UNKNOWN thì giữ để đối soát. Test completion dùng Spring transaction manager giả lập để kiểm tra commit, begin failure, rollback, cleanup failure và UNKNOWN; test metadata/quyền/rollback INSERT dùng MySQL thật.
- Callback AFTER_COMMIT đưa lượt gửi vào executor 4 worker, queue tối đa bằng connection budget (mặc định 256). Worker kiểm lại quyền trước gửi, native Tomcat blocking send giới hạn 5 giây. Khi queue đầy, kết nối bị đánh dấu để bộ quét đóng 1013 và client reconnect/REST re-fetch. Có meter queue/active/drop/duration.
- Regression kiểm hai upload hoặc hai download bị chặn cùng lúc với Hikari pool 2, xác nhận 0 connection bận trong lúc chờ và API đọc News khác vẫn trả 200. Socket test kiểm 64/256 subscriber chậm, queue đầy, thu hồi quyền và native timeout. Test tích hợp MySQL kiểm POST/PUT/DELETE trả về trong 2 giây mỗi request khi 64 session giả lập còn chờ gửi; rollback và socket mạng thật tiếp tục được kiểm qua bộ News hiện có.
- Các phép chặn Storage/socket là test doubles có latch để tái hiện tài nguyên bị giữ, không phải benchmark Firebase cloud hoặc 256 kết nối TCP thật. Bằng chứng live 13/11 phía trên thuộc revision đã ghi. Luồng reconnect nhận READY và đọc lại REST vẫn là cơ chế phục hồi.

Kết quả local ngày 18/11 là **307/307 Maven verify đạt**, 0 lỗi và 0 bỏ qua, trên MySQL 8.0 Docker `_test` riêng. Registry/checksum 17 skill/169 file, 9 routing tests và kiểm whitespace đạt. Lệnh backend là `backend/mvnw.cmd -o -B -ntp verify` với `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` trỏ database thử riêng; container được dừng sau kiểm thử. Frontend được kiểm lại bởi CI của revision bàn giao.

Skills áp dụng trong lượt này gồm `cafe-workflow`, `cafe-spring-api`, `cafe-quality`, `cafe-review` tại `.agents/skills/<tên-skill>/SKILL.md`. Mã ảnh, socket và hợp đồng được đối chiếu với các caller hiện có; dữ liệu thử chỉ nằm trong MySQL `_test` riêng.

## Góp ý P1 về collection và thống kê — 18/11/2026

Góp ý mới được đăng trong lúc xử lý hai P2. `HeaderTokenFilter` bỏ sót collection `/api/v1/staff`, `/api/v1/bills` và nhóm statistics do kiểm prefix kết thúc bằng dấu `/`. Bản sửa dùng một danh sách resource root với điều kiện bằng root hoặc có subpath, bao gồm statistics. Handshake News và các route public được kiểm hồi quy riêng.

- Lượt Maven verify chọn lọc trên nhánh hiện tại đạt **62/62**, 0 bỏ qua, với MySQL `_test` riêng. Bao gồm Auth login thật, filter tại ba đường dẫn với ADMIN/STAFF/CUSTOMER, token hết hạn/sai chữ ký/đã thu hồi, Home public khi user DB lỗi, Menu và News REST/WebSocket.
- Bản ghép thử BE trong checkout riêng dùng News hiện tại cùng source PR #11 tại `035baace98c9d4c6d2530e2973b5983e89b3dbb6`, PR #18 tại `dfd71e3b90489353201e16af0e7bea6f69875bbe` và các authorization/CSRF matcher của hai PR. Giữ đúng rename StaffController → StaffDashboardController của #11. Chạy **38/38 test đạt**, 0 bỏ qua, trên database riêng.
- Kiểm HTTP trên controller thật của bản ghép xác nhận ADMIN GET ba endpoint trả 200; STAFF chỉ GET bills trả 200 và bị 403 ở staff/revenue; CUSTOMER bị 403 cả ba. Anonymous, bearer hết hạn, sai chữ ký và bị thu hồi đều bị chặn. POST staff thiếu CSRF vẫn trả 403. Đây là kiểm tích hợp backend tại các biên xác thực/phân quyền, dùng dữ liệu tổng hợp từ schema fixture hiện hành.

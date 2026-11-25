# PR #23 — khắc phục nhận xét review ngày 25/11/2026

Phạm vi: [PR #23](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/23), nhánh `feature/dt2-64_menu-crud-cloudinary`, nền `86eae479` / `develop@9a88ba6d`. Sửa tiếp cùng PR theo yêu cầu chủ repo; giữ nguyên attribution và checkout DT2-11 chưa commit. Không merge, tag hoặc deploy.

## Nhận xét và kết quả

| Thread | Nguyên nhân | Khắc phục và phòng hồi quy |
| --- | --- | --- |
| [4691153807](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/23#discussion_r4691153807) | Khóa nhóm đảm bảo atomicity nhưng version không thay đổi khi tập món thay đổi. | Thêm/xóa món tăng `menu_categories.version` trong cùng transaction và khóa nhóm. Xác nhận DELETE cũ trả 409 kể cả thay món làm số lượng giữ nguyên. MySQL test xác minh snapshot sau commit, rollback khi xóa stale, và insertion giữ khóa trước deletion. API contract cập nhật. |
| [4691153823](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/23#discussion_r4691153823) | HTTP status bị bỏ, nên lỗi menu không cập nhật AuthContext. | Giữ status/code và callback 401 gắn snapshot token/generation. Chỉ expire phiên hiện tại đã gửi request; abort/phiên thay thế được bảo vệ. Áp dụng chung cho list/read/CSRF/upload/save/delete. 403 giữ phiên và hướng dẫn kiểm tra quyền/CSRF; HTTP 403 hiện không đủ dữ kiện để tự kết luận token đã bị thu hồi. |
| [4691190814](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/23#discussion_r4691190814) | Mất/hỏng phản hồi bị coi giống một mutation bị từ chối. | Sau khi gửi mutation, lỗi mạng/body/5xx giữ snapshot payload và khóa gửi lại. Nút kiểm tra chỉ GET theo code, so sánh version và tất cả trường đã gửi, giữ chính xác giá/mediaId. Nếu dữ liệu khác, 404 hoặc read lỗi thì không tự ghi đè/gửi mutation thứ hai. Có thể kiểm tra lại hoặc hủy để tải lại. Lỗi trước khi gửi mutation (CSRF) vẫn cho retry bình thường. |
| [4691190819](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/23#discussion_r4691190819) | Route menu return trước cảnh báo mật khẩu chung. | Giữ `PasswordExpiryNotice` và liên kết đổi mật khẩu ngay trong route menu. Test trực tiếp ADMIN có mustChangePassword. |

## Phân tích rủi ro liên quan

- So sánh itemCount không phát hiện thay món cùng số lượng; vì vậy bảo vệ bằng version membership dưới khóa, không dùng GET trước DELETE làm điều kiện an toàn.
- Request 401 cũ có thể đến sau login/đổi mật khẩu; callback kiểm tra snapshot và tín hiệu abort trước khi expire. 403 không đồng nghĩa hết phiên vì CSRF cũng trả 403.
- GET đối chiếu kết quả lưu chỉ xác nhận trạng thái quan sát được; không chứng minh người nào tạo trạng thái đó. Nếu admin khác sửa tiếp hoặc membership nhóm làm version tăng tiếp, đối chiếu bảo thủ giữ form khóa và yêu cầu đọc lại.
- Không tự retry mutation sau mất phản hồi. Unique code/optimistic version vẫn là bảo vệ backend; đối chiếu frontend không thay thế transaction hoặc authorization.
- Tất cả application DML tác động membership đã được rà trong `backend/src/main`; tập trung tại MenuCatalog. Writer ngoài module/SQL trực tiếp cần tuân thủ cùng contract, không có cam kết chặn writer ngoài ứng dụng.
- Không thêm idempotency table, schema hay migration; không seed dữ liệu hoặc sửa DB nghiệp vụ. Upload bị mất phản hồi có thể để lại asset chưa gắn; chính sách cleanup provider vẫn thuộc phần chưa được duyệt, không tự xóa asset.

## Đối chiếu dữ liệu

Đã đọc database contract và DDL versioned `backend/src/main/resources/db/smart-cafe/V20261113_01__smart_cafe_schema.sql`, đồng thời chạy read-only SHOW CREATE TABLE trên MySQL 8.0.46 kiểm thử riêng cổng 13369 cho `menu_categories`/`menu_items`. Các trường đang sửa (`id`, `category_id`, `active`, `version`) khớp DDL; `price decimal(18,0)` và FK media giữ nguyên. Không có chênh lệch mới trong phạm vi này. Không tuyên bố đã xác minh lại DDL production.

Schema chạy test mới: `smart_cafe_pr23_20261125_test`, provision nguyên `backend/src/test/resources/schema.sql`; không dùng schema nghiệp vụ. Suite migration tự tạo/xóa các schema `_test` theo guard sẵn có.

## Kiểm chứng đã chạy

- `frontend`: `npm run lint` PASS; `npm run build` PASS; `npm run test:ui` PASS — 5 unit và 254 Playwright desktop/mobile. Bao gồm 16 lượt regression mới: 401 lưu/upload, CSRF 403, phiên thay thế, PUT mất/hỏng phản hồi, admin khác sửa tiếp, cảnh báo mật khẩu và POST mất phản hồi với GET đối chiếu lỗi rồi retry. UI fault injection dùng fixture có kiểm đếm request, không gọi là network fault injection trên server thật.
- `backend`: `TEST_DB_URL=jdbc:mysql://127.0.0.1:13369/smart_cafe_pr23_20261125_test?connectionTimeZone=UTC`, `mvnw.cmd -o -B -ntp verify` PASS — 322 tests, 0 failures/errors, 1 skip (browser opt-in). Lần đầu phát hiện assertion HTTP TC-40 cố định version nhóm cũ; đã sửa theo version từ snapshot xác nhận và chạy lại toàn suite đạt.
- `MENU_BROWSER_TEST=true`, `mvnw.cmd -o -B -ntp -Dtest=MenuBrowserIntegrationTest verify` PASS riêng — 1 test, 0 failures/errors/skips. Browser → API thật → MySQL thật: CRUD, reload, thay ảnh, menu khách, hủy/xóa nhóm. Provider ảnh HTTP fixture, **Cloudinary live NOT RUN**.
- Báo cáo HTTP local `backend/target/api-report/dt2-9-api-results.json`: 46/46 PASS; đây là các ca trong JUnit, không cộng lần nữa vào tổng test.
- `git diff --check -- . ':(exclude).agents/skills/**'` PASS. Ảnh do các test khác ghi lại được phục hồi về HEAD, không nhập thay đổi ảnh ngoài phạm vi vào commit.
- Aikido MCP 1.0.34: PENDING_AUTHENTICATION. Đã initialize và gọi `aikido_login`/`aikido_scan_paths`; cần hoàn tất callback cấp quyền trước khi có kết quả scan. Không coi lỗi authentication là scan PASS.

## Skills và review

Đã đọc/apply `cafe-review` (`.agents/skills/cafe-review/SKILL.md`) cho rà comment/diff; `cafe-spring-api` (`.agents/skills/cafe-spring-api/SKILL.md`) cho transaction/API; `vercel-react-best-practices` (`.agents/skills/react-best-practices/SKILL.md`) và `fixing-accessibility` (`.agents/skills/fixing-accessibility/SKILL.md`) cho state bất đồng bộ và điều khiển native; `cafe-quality` (`.agents/skills/cafe-quality/SKILL.md`) cho regression/MySQL/evidence. Aikido scan/setup dùng skill plugin tương ứng, không thêm dependency vào dự án.

Self-review đối chiếu callers và phần ghi membership, quyền/session, serialization chính xác, không tự ghi đè khi retry. Không tính là approval độc lập. Chủ repo yêu cầu rõ reply “Đã khắc phục + …” và resolve các thread đã kiểm chứng; chỉ làm sau khi push đúng commit. Vẫn cần hai reviewer độc lập duyệt revision mới và required CI, cùng evidence Cloudinary live theo AC trước nghiệm thu phần ảnh thật.

## Follow-up review trên nền 409e07c

Đọc lại toàn bộ Conversation/reviews và các thread mới, giữ nguyên nhánh PR #23. Bốn finding tiếp theo:

| Thread | Sửa và phạm vi kiểm chứng |
| --- | --- |
| [4691434658](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/23#discussion_r4691434658) | Hủy form truyền trạng thái có mutation chưa xác định về MenuAdmin; invalidate revision để tải lại danh sách hiện tại và tùy chọn nhóm. Test món/nhóm bị admin khác sửa tiếp; GET đối chiếu 404/503; hủy ngay chưa đối chiếu. Chỉ một PUT, không phát lại mutation. |
| [4691451539](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/23#discussion_r4691451539) | Upload có trạng thái riêng: cache ghi nhận File có kết quả chưa xác định, khóa lưu và giữ draft; chỉ cho hủy hoặc lựa chọn rõ tiếp tục không dùng ảnh mới, giữ imageMediaId cũ. Mất/hỏng response hoặc lỗi provider không cho retry ngầm. `503 IMAGE_NOT_CONFIGURED` được backend phát trước provider nên vẫn retry được; upload đã nhận mediaId rồi PUT 409 tiếp tục dùng lại mediaId. Test API gọi lại cùng File chỉ một upload; browser kiểm tra lựa chọn giữ ảnh cũ. E2E fixture để provider/MySQL commit rồi drop response, đếm asset và reference sau đó. Không thêm schema/idempotency key hoặc tự cleanup asset. |
| [4691451542](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/23#discussion_r4691451542) | DELETE mất response giữ snapshot kind/code/version và khóa xác nhận. Nút kiểm tra chỉ GET detail: 404 xác nhận mục hiện không còn hoạt động, không khẳng định ai xóa; 200 version mới yêu cầu đóng/mở xác nhận mới; 200 version cũ hoặc 503 vẫn giữ trạng thái chưa rõ. Hủy bằng nút/Escape cũng invalidate/refetch. Test cả món/nhóm cho năm kết quả, mỗi ca đúng một DELETE. |
| [4691451546](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/23#discussion_r4691451546) | Refresh tùy chọn nhóm khi query thay đổi, mở tạo mới hoặc bấm tải lại; có loading/error/retry ngay trong form tạo. Khi sửa món, nhóm bất biến dùng code/name từ detail, không bị cache thiếu nhóm chặn; backend tiếp tục kiểm tra active/immutable. Offset paging chỉ công bố hai lượt đọc đầy đủ khớp nhau, kiểm total/duplicate/số row, tối đa ba lượt rồi báo retry. Unit test 101→100 nhóm, thay nhóm cùng total, biến động liên tục; browser test nhóm thiếu trong cache và nhóm được thêm sau mount. |

Ranh giới an toàn: hai lượt đọc khớp chỉ chứng minh độ ổn định quan sát được, **không phải snapshot transaction**. Dữ liệu vẫn có thể đổi sau lần đọc; backend/CAS/khóa quyết định khi ghi. Chưa có contract để GET đối chiếu một upload chưa nhận mediaId; ảnh có thể tồn tại nhưng chưa gắn vẫn được giữ, không dò theo URL/hash hay tự xóa. Việc người dùng chủ động chọn ảnh khác là thao tác upload mới; không có cam kết idempotency provider giữa thiết bị hoặc qua reload.

Kiểm chứng follow-up:

- `npm run lint`, `npm run build`: PASS.
- `npm run test:ui`: **9/9 unit và 292/292 Playwright PASS**, Chromium desktop/mobile. Lượt sandbox đầu bị chặn khởi chạy Chromium, đã chạy lại ngoài sandbox. Hai lỗi locator test do loading món và nhóm cùng xuất hiện đã được sửa chọn đúng trạng thái rỗng, rồi chạy lại toàn suite PASS.
- `TEST_DB_URL` như phần trên, `MENU_BROWSER_TEST=true`, `backend/mvnw.cmd -o -B -ntp verify`: **322/322 PASS, 0 failures/errors/skips**. E2E mở rộng đạt (1 test, 9.875 giây): provider HTTP fixture/MySQL đã nhận upload thứ ba trước khi browser cố tình drop response; không upload lần nữa, sau lựa chọn bỏ ảnh mới món vẫn giữ mediaId thứ hai. Hậu kiểm JDBC đếm đúng 3 asset và 1 asset được món tham chiếu, kể cả sau archive. Không gọi Cloudinary thật, không cleanup asset production.
- Aikido `aikido_scan_paths` đã được gọi lại cho thay đổi lần này; MCP vẫn trả yêu cầu đăng nhập. **Chưa có scan result**, không tính authentication error là PASS. Không lặp lại yêu cầu cấp quyền khi chưa có thay đổi bên ngoài.
- Không sửa backend production/API contract/schema/dependency/policy; Java chỉ mở rộng assertion E2E. Dữ liệu synthetic chỉ trong schema `_test` đã dùng ở phần trên. Cloudinary live vẫn NOT RUN.

Skills follow-up: `cafe-review`, `vercel-react-best-practices`, `fixing-accessibility` và `cafe-quality` ở các path `.agents/skills/.../SKILL.md` đã nêu. State upload/save/delete tách theo bằng chứng thực tế; các nút native có tên, thông báo trạng thái/lỗi và giữ focus trap của dialog. Không tính self-review là approval độc lập.

## Follow-up upload 409 trên nền f9e3d7e

[Thread 4691649692](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/23#discussion_r4691649692): provider đã tạo asset trước khi `MenuMedia.register()` ném lỗi JDBC; `MenuErrors` có thể trả 409 `RETRY_MENU` hoặc `MENU_CONFLICT`. HTTP 409 không chứng minh upload chưa có side effect, dù metadata chưa được commit.

- `writeMenu` ghi nhận mutation đã được thử sau bước CSRF, tách khỏi lỗi CSRF trước khi gửi upload. Upload chỉ cho retry tự do với lỗi proven pre-provider theo contract: auth/CSRF 401/403, validation 400 có code đã biết, 413 IMAGE_TOO_LARGE và 503 IMAGE_NOT_CONFIGURED. Kết quả còn lại sau POST ảnh, kể cả 409/mã chưa biết, chuyển pendingUpload và nhớ File để không tự gửi lại.
- Giữ nguyên semantics 409 của PUT/POST món: không đổi thành pendingSave chỉ vì mutationAttempted. Upload có mediaId rồi PUT 409 vẫn reuse mediaId. Không sửa backend production, schema, mapping 409 hoặc tự xóa cloud asset.
- Frontend `npm run lint`, `npm run build`, `npm run test:ui`: PASS — **13 unit + 296 Playwright** desktop/mobile. Regression thêm RETRY_MENU, MENU_CONFLICT, mã 409 không biết; gọi lại cùng File chỉ một POST ảnh. CSRF 503 không gửi upload và không khóa File. Browser xác minh draft/khóa lưu/lựa chọn bỏ ảnh mới; các ca IMAGE_NOT_CONFIGURED retry và PUT conflict reuse tiếp tục PASS.
- Backend `mvnw.cmd -o -B -ntp -Dtest=MenuImagesTest verify`: **9/9 PASS**, 0 failures/errors/skips. Provider HTTP fixture trả thành công, dependency `register()` được inject CannotAcquireLockException hoặc DataIntegrityViolationException, xác minh provider đã gọi và mapping 409 tương ứng. Đây là fault injection tại dependency register, **không phải tái hiện deadlock MySQL thật hoặc Cloudinary live**. Không chạy lại toàn bộ MySQL local vì backend production không đổi; CI kiểm tra toàn bộ theo quy trình.
- `git diff --check` PASS; ảnh do test sinh ngoài phạm vi được phục hồi. Aikido vẫn chưa có scan result do xác thực chưa hoàn tất; không coi đó là PASS.

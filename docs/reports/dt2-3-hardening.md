# DT2-3 / DT2-27 — final integration, security & quality hardening

> Current correction: official Staff57ea6f1 has fixed version/CAS/auth_version/callers. Do not apply historical Staffb74 proposals or treat its5/9 failures as current. Current official/proposed candidates, Draft verification timestamp and remaining News/Bills blockers are recorded in [review follow-up](dt2-3-review-follow-up.md). The dated results below remain historical.


> Snapshot kiểm thử15/11/2026; **396test/2failure** vẫn được lưu làm lịch sử, không chuyển thành PASS. CF-04/publication đã được user cho phép18/11; develop và PR heads đã đổi nên có lượt kiểm thử mới tại [publication report](dt2-3-publication.md).

Ngày15/11/2026 (Asia/Saigon). Branch `feature/dt2-3_personal-account-backend`; HEAD/base/develop sau fetch `ffa4d9527e7924dd0bc9815fefb3034463d03156`. **Implementation còn uncommitted; chưa commit/push/tạo PR, merge hoặc Jira Done.** Không sửa CI, validator, Git Flow, quality gates, schema hay code Staff/News/Customer trên nhánh chính.

Kết luận: Backend Profile hiện tại đạt kiểm thử local; **chưa sẵn sàng xác nhận tích hợp/merge toàn hệ thống**. Các lỗi Staff/shared security đã tái hiện phải được owner liên quan xử lý; full candidate không được gọi PASS. Publication vẫn giữ theo chỉ đạo owner chờ mã CF, không suy diễn mã và không sửa title validator.

## 1. Bảo vệ và revision

Working tree tracked/untracked và private config được backup ngoài Git trước sửa; vị trí backup và private manifest chỉ nằm trong local review record. Không reset/clean/force-push. Đầu lượt phát hiện sáu file tài liệu/renderer thiếu so với báo cáo trước và bốn test/script khác manifest trước; đã lưu nguyên trạng trước khi khôi phục/hardening, không suy diễn nguyên nhân. Main Java/runtime được giữ nguyên vì chưa tìm lỗi mới cần viết lại Profile. Manifest cuối và exact stage allowlist được ghi ngoài Git; [final handoff](../handover/dt2-3-final-handoff.md) phân loại phạm vi PR.

Fetch origin --prune xác minh develop không đổi. [Đầu vào rehearsal](../qa/rehearsals/dt2-3/inputs.json) ghi SHA đầy đủ: Staff#11 `4c7b969`, Customer#14 `057531c`, News#15 `ff10950`, Menu#16 `2ac0104`. #11/#14/#15 còn mở; #16 **closed/unmerged**, không coi là đã vào develop. Không có PR mở mới liên quan tại lần kiểm. Review comments mới được đọc như evidence theo revision, không tái dùng kết luận PR#9 hay review Staff cũ như phê duyệt hiện tại.

## 2. Lỗi mới đã tái hiện và xử lý

| Mức/phạm vi | Trigger, actual, tác động | Xử lý / evidence |
| --- | --- | --- |
| P1 — DT2-10 writer | StaffService.java tại HEAD#11:291 đọc trước lock,322 UPDATE users không expected-version/tăng version,333 full profile upsert. Hai transaction cùng user trả200/200; Staff full payload ghi đè tên/khôi phục địa chỉ cũ. PATCH version0 sau Staff update vẫn thành công. | 9 test MySQL có overlap thật: baseline5FAIL/4PASS. Test giữ nguyên kỳ vọng; patch đề xuất đạt9/9. Bàn giao owner, không nhập Staff vào DT2-3. |
| P1 — composed security | Khi giữ Auth broad `/staff/**` cùng Staff management, bearer STAFF thật gọi GET `/api/v1/staff/1` trả200 thay403, đọc personnel/lương người khác. Auth/News broad matcher ở SecurityConfig; Staff standalone matcher riêng không có lỗi này. | HTTP regression tái hiện; candidate đề xuất tách GET dashboard trước namespace quản trị ADMIN-only, giữ CSRF. Chưa áp lên PR thành viên. |
| P2 — composed filter | News#15 HeaderTokenFilter.java:21–28 không bao phủ `/profiles/`; bearer Login hợp lệ gọi own Profile trả401 thay200; root Staff cũng không được filter. | Regression HTTP thất bại trước patch; 22 Profile tests pass trên candidate sau bổ sung consumer path. Không tạo Auth mới. |
| P2 — startup/integration | `auth.StaffController` và `staff.StaffController` dùng cùng bean `staffController`; Spring không khởi động khi ghép hai module. | Startup probe tái hiện; bean name riêng nằm trong patch đề xuất Staff. |
| P2 — literal source composition | Latest Staff#11/Customer#14 revert Auth, verified migration/safety resources và dependencies. Merge nguyên trạng với Profile không compile. | Có compile failure thực tế. Chỉ selective rehearsal giữ develop/Auth để khảo sát tiếp; không báo PR nguyên trạng compilePASS. |
| P2 — test harness DT2-3 | ProfileFailureTest restub bằng `when(...).thenThrow` khiến exception vòng trước nổ lúc setup vòng sau (2test/1error). | Đổi về `doThrow(...).when(...)`; giữ toàn bộ assertions; full260 pass. |
| P2 — contract documentation | Salary description trong YAML bị tách thành key ngoài schema do dấu colon chưa quote. | Sửa description, validate schema/HTTP examples; runtime không đổi. |

Các nhận xét auth_version: Staff deactivate tại358 chỉ SET status, không tăng auth_version/version. Current DB ACTIVE check đã làm token vô hiệu khi INACTIVE trong các ca đã chạy; **không tuyên bố** đã tái hiện reactivation-token resurrection. Proposal tăng auth_version khi đổi role/deactivate để cùng hợp đồng Auth; đây là correction đề xuất cho writer owner, không phải quyền tự sửa DT2-10.

## 3. Concurrency rehearsal DT2-10

Worktree tạm do app quản lý, tách khỏi nhánh chính; schema MySQL8 `_test` riêng, native DDL verified. Test gọi service proxy thật, TransactionTemplate thật và writer SQL thật. Connection thứ ba khóa users row; đợi `performance_schema.data_lock_waits` ghi nhận writer1 rồi writer2 cùng chờ, mới nhả lock. Không dùng mock repository, không gọi hai request tuần tự rồi gắn nhãn concurrent.

[Test source và patch có provenance](../qa/rehearsals/dt2-3/README.md) bao phủ: STAFF self edit với ADMIN edit; ADMIN name với STAFF address; cùng field; password với Staff update/deactivate theo cả thứ tự; stale version sau Staff write; users→staff_profiles locking lặp5lần; transaction rollback/token revocation. Baseline9test/5failure/0error/0skip, proposed9/9. Giá trị được ghi từ SQL tổng hợp; không export hash/password/token.

Hợp đồng đề xuất: mọi writer dữ liệu dùng chung lock users trước staff_profiles; đọc state/version sau lock; mutation nhận expected version, CAS và tăng users.version một lần/transaction; role/password/status security transition phải theo auth_version contract. Payload Staff full-replacement stale phải409, không âm thầm ghi đè self edit khác field. Không tự merge field-level payload trên dữ liệu cũ. Proposal yêu cầu caller/test DT2-10 gửi version; overload Java giữ source compile không cấp JSON default version.

## 4. Kiểm thử tổ hợp PR liên quan

Candidate **chọn lọc**, không literal merge đã duyệt: giữ toàn backend develop/Auth/Profile và safety DDL; thêm domain/tests Staff#11, Customer#14, News#15; ghép SecurityConfig có thứ tự; giữ config Auth/Flyway; dùng proposed Staff/version/bean-name và proposed shared-filter/management matcher. News storage external ở các test contributor vẫn mock theo test gốc; không coi đó là storage production acceptance.

Đã inspect SecurityFilterChain/matcher/order, consumer bearer, advice priority/scope, AuthRepository/AccountReader/HomeAccount, routes/DTO, DDL/Flyway và SQL writers. Profile advice scoped controller có ưu tiên cao; không tìm lỗi advice chéo mới qua suite đã chạy. Staff/News domain không tự nhập vào nhánh DT2-3 để làm test xanh.

Full candidate chạy thật `mvnw verify` có TEST_DB_URL. Lượt đầu396test/29failure/42error/0skip gồm thiếu file proposal SQL và cạn connection ở harness giữ nhiều contexts; News còn lỗi fixture assembly. Bổ sung đúng file từ main và giảm Spring test context cache xuống4: lượt trung gian396/31failure/7error do vẫn dùng fixture develop thiếu News audience. Hai lượt này được lưu nguyên bản, không quy chúng thành lỗi baseline News. Lấy nguyên fixture News#15 rồi chạy lại full suite kết thúc14:00:48+07:00: **396test/2failure/0error/0skip**, Profile22/22, concurrency9/9, Customer21/21 và News59/59MySQLPASS. Hai failure là StaffControllerTest.updateStaffPreservesUsernameAndUpdatesProfile:198 và StaffHttpReportTest.executeAllStaffApiTestCases:604/TC-17: success payload cũ thiếu required version của **proposal**, nhận400 thay200. Đây là caller migration chưa hoàn thiện của patch đề xuất, không phải baseline Staff test failures. Không sửa assertion/skip để che lỗi; không tăng shared MySQL limit. [integration-results.json](dt2-3-integration-results.json) ghi per-class, source/fixture/pom SHA và các lượt trước. Candidate verify dừng trước package nên không gán JAR hash cũ cho candidate; primary JAR có bằng chứng riêng. **Full integration vẫn FAIL; không dùng các nhóm pass để suy ra cả tổ hợp pass.**

News#15 **đã có fixture audience** đúng extension trong DDL verified. Hai lượt ghép đầu giữ historical schema.sql của develop nên lỗi thiếu cột là thiếu sót assembly của rehearsal, **không phải finding baseline PR News**. Đã lấy nguyên fixture tại HEAD#15 vào worktree và chạy lại full suite; không sửa byte migration hay tự sáng tác ALTER. Các test Staff cũ chưa gửi expected version phải cập nhật với owner khi nhận proposal. Các lượt lỗi trước được giữ/phân loại trong JSON, không skip case lỗi. Menu#16 không merge; collections Menu/QR trên executable chính vẫn chạy nguyên trạng.

## 5. Rate limiting / brute force

Auth hiện dùng BCrypt cost10; OTP có quota theo email/purpose, cooldown gửi, maximum5/h và5attempt/challenge, reset grant gắn auth_version. Các giới hạn OTP **không phải** admission control Login/current-password hoặc IP/global budget. Không tìm gateway/reverse-proxy policy được duyệt để tái sử dụng. Không sửa CHECK purpose của auth_otp_limits, không thêm bảng/limiter rời rạc.

20 request localhost mới có giới hạn, ngoài acceptance Newman:8 Login sai→401 (250–282ms);8 current-password sai, tối đa4đồng thời→400 (265–1032ms);2 password field131072 ký tự→400 (125/31ms); valid Login và Profile sau lỗi→200. Không response429, SQL snapshot unchanged. Auth regression cũng quan sát sáu Login sai và tám email synthetic khác nhau được chấp nhận. Đây là **observation tại volume nhỏ**, không là load/CPU/DoS test hay bằng chứng không thể có limiter ở mọi deployment.

DTO field validation có chặn field dài nhưng không thiết lập transport body cap. Sai current-password vẫn thực hiện BCrypt và giữ user-row lock trong transaction, nên cần shared budget để hạn chế CPU/lock contention. Không tìm/tuyên bố một limiter hiện hữu ngoài OTP. Reset chống replay/attempt đã test riêng; IP/global admission và cleanup/retention vẫn là quyết định chung Auth/deployment.

**Đề xuất chưa được duyệt/triển khai:** gateway JSON cap ví dụ8KiB cho Login/Profile/password/recovery JSON routes (không áp lên upload); trả413 sớm cho chunked/content-length oversize và thống nhất timeout. Trusted peer-IP rolling budget + per-identifier/account failure budget/backoff ngắn ở shared Auth layer; ví dụ5failure/5min cho identifier và30request/min/IP là điểm bắt đầu cần load/NAT testing, không phải số đã chốt. Không permanent lockout; không để attacker khóa tài khoản nạn nhân, successful auth/recovery vẫn có đường phục hồi. Cần aggregate BCrypt concurrency budget/multi-replica coordination và429/Retry-After contract trước release. Owner Auth/gateway phải duyệt policy, metrics/privacy và test trên topology thật; chưa production-ready brute-force protection.

## 6. API contract cho FE

[OpenAPI](../api/dt2-3-profile.yaml) và [4 ví dụ HTTP thật](../api/dt2-3-http-examples.json): Login200, own Profile200, PATCH200, password200, toàn bộ credential redacted; thêm [47 response lỗi thực tế](../api/dt2-3-http-errors.json) chỉ whitelist type/title/status/detail/code/instance. Login thêm ba status fields ở **top-level**. Profile.id/version/salary là string, nullable personnel; Login.user.id numeric được giữ tương thích. Đã parse YAML, kiểm internal refs/Schema Object keywords và validate4success responses bằng Ajv6.15.0 đã có cùng Newman, normalize OAS nullable; không thêm dependency vào repo. Handoff liệt kê status/code thực và shared-filter503 có thể thiếu code/type.

[Hướng dẫn DT2-28](../handover/dt2-3-backend.md) quy định replace AuthContext token trước refresh Profile/Header, loại request/token cũ, không blind retry POST password nếu mất response, reload409 PROFILE_CONFLICT, xử lý PROFILE_INCOMPLETE qua quản trị, chỉ input name/address/phone, dùng server warning30×24h và không tự cưỡng chế business API. Chưa sửa Frontend.

## 7. DDL và deployment

DDL20261113.1 giữ nguyên SHA-256 `84cb9f2e99073ea73b117e828dc7f144928052a8f8345cde6f903b82280e4b5e`. Native provision1/validate/repeat0 với verified-db/FlywayON trên schema `_test` mới; không tắt preflight. JAR runtime SHA-256 `7e1fca9a1138b02912700b2f7004508732e2e9361da359e8b39cf71b5e3f2b12`; giống đầu lượt vì executable Java/resource không thay, không phải dùng nhầm JAR cũ. Runner so class bytes và toàn source manifest.

Read-only recheck `smart_cafe` cấu hình local MySQL8.0.45 vẫn13base tables/0views, legacy V001–V008, thiếu staff_profiles/media_assets. Không đọc/export row nhân sự, không migrate/repair/baseline/seed/ALTER/DML DB chung. [Database contract](../architecture/database-contract.md) giữ drift/blocker. DBA/owner phải xác nhận instance đích, approve canonical migration/ref và20tables/4views/News extension, history/grants/DEFINER, backup/restore, writer freeze, plan provision hay conversion và rollout/rollback. Green `_test` không là phép deploy.

Raw logs/XML cần thiết được giữ ngoài Git. Managed worktree rehearsal còn được bảo toàn vì app từ chối archive checkout đang được bảo vệ; chi tiết host/path và backup chỉ giữ trong local review. Không xóa thủ công để vượt bảo vệ, không nhầm việc giữ checkout với schema/process test chưa được dọn.

## 8. Verification hiện hành

| Check | Kết quả thực tế |
| --- | --- |
| Full primary Maven/MySQL `test-be.ps1` → `backend/mvnw verify` | **PASS260test,0failure,0error,0skip**; Profile integration21 test, failure2, validation19 và PasswordExpiration2 theo XML hiện hành |
| Profile Newman chính + restore/rerun đủ | **PASS89request/270assertion mỗi vòng**, backend JAR/MySQL thật |
| Menu + QR Newman nguyên trạng | **PASS16/31 +9/16** |
| Tổng acceptance Profile×2/Menu/QR | **PASS203request/587assertion**;10SQLchecksPASS |
| Newman fail control | **EXPECTED FAIL**1request/3assertion/1failure, CLI exit1; không cộng vào acceptance |
| Auth/OTP/Home verified-db HTTP + SMTP loopback | **PASS39request/57assertion**;4fault-control assertion EXPECTED FAIL riêng |
| Primary test concurrency/expiry | MySQL race/rollback/reset tests trong260; exact before/equal/after boundary và DB flag/UTC/Vietnam covered; Staff cross-module9case baselineFAIL/proposalPASS riêng |
| Frontend lint/production build | **PASS**; không thay UI |
| Playwright regression | **PASS70/70**; phần HTTP mock hiện hữu không là authenticated deployment E2E |
| Skills check, governance/Git Flow | **PASS** registry17/routes16;17script tests; Auth/Newman launchers/assertions24testPASS |
| Combined PR full suite | **FAIL396test/2failure/0error/0skip**; Profile22/22, concurrency9/9, Customer21/21, News59/59MySQLPASS riêng; hai Staff success payload thiếu version proposal |
| SonarQube / GitHub Actions / deploy | **NOT RUN**: không có approved Sonar server/token/scanner; chưa push và chưa có deployment target/grants |

Primary log ngoài artifact public: `backend/target/dt2-3-hardening-full.log`, `...hardening-postman.log`, `...auth-postman.log`, `...hardening-playwright.log` và governance/frontend logs. Raw private reports không vào Git. [Newman sanitized evidence](dt2-3-postman/results.json) chứa timestamp UTC06:24–06:29 (13:24–13:29giờVN), sourceSha256/JARhash/cleanup và từng phase. Review ngoài Git lưu command/result, source manifest và các giới hạn; self-review không thay hai independent approvals.

## 9. Final review, blockers và readiness

Không phát hiện lỗi runtime mới trong module Profile sau review final; giữ chức năng đúng, không refactor kiến trúc. Sửa test setup/HTTP runner/renderer và contract docs; bổ sung authentication tests và assertion chính xác kết quả concurrency. Đã kiểm token-current-DB authority, protected-field injection, bigint/salary precision, rollback/post-commit token, password reset revocation, scoped advice và fixture/schema ownership. Async table/session/logout UI không thay trong scope; regression hiện hữu không tự đóng các câu hỏi module khác.

Còn chặn: owner Staff nhận hợp đồng writer/bean và caller version; owner Auth/Staff/News thống nhất filter/matchers, giải quyết Auth revert/dependency và hai Staff caller tests theo proposal; owner duyệt shared brute-force/body policy; DBA chốt deployment target; Sonar approved environment; mã CF/title/commit theo leader và hai independent review/CI theo Git Flow trước merge. **Local draft/handoff sẵn có; không xác nhận ready-to-merge hoặc integrated PASS.** Không hỏi lại những quyết định đã chốt.

## 10. Thay đổi và lý do

| Nhóm file | Lý do |
| --- | --- |
| backend profile/*; auth PasswordExpiration/AuthService/AuthRepository/UserRecord/LoginResponse/AuthController | Implementation DT2-3 được giữ: self-profile allowlist/CAS, atomic password, server expiration/top-level Login, no-store |
| home AccountReader/HeaderService/HomeAccount; SecurityConfig | Một projection Header/Profile, precision, tái dùng bearer authority và exact Profile rules |
| ProfileMySqlTest/ProfileFailureTest/validation/expiry tests | Bảo vệ auth/permission/race/rollback/boundary và sửa Mockito restubbing lỗi |
| Postman collection/fixture/environment, ProfileFixturePassword.java | Case thật, dữ liệu tổng hợp officialDDL, secrets chỉ runtime |
| profile-postman-live.py/auth-postman-live.cjs/render-profile-postman.py | verified-db không tắt Flyway, JDBC mode đúng, source/JAR provenance, full rerun, bounded probes, publish sanitized only |
| docs/api/*, handover/*, qa/*, reports/* | Contract/FE examples, reproduction/foreign proposal, actual counts/FAIL và local PR body |
| decisions/database-contract/open-questions | Phân biệt quyết định đã duyệt với proposal và deployment/integration blockers |

Danh sách từng file/hash và lý do giữ ngoài Git cùng exact stage allowlist; [final handoff](../handover/dt2-3-final-handoff.md) ghi phạm vi49file đề xuất từ50file gốc và4artifact mới. Không có .env, raw report, secret, cache/build output, migration đổi byte, whole foreign module hoặc CI edit trong danh sách bàn giao.

## Final handoff sau hardening

Owner chấp nhận Backend local đạt và giữ kết luận full integrationFAIL. [Review riêng PR #11](../handover/dt2-3-pr11-review.md), [shared security cho Auth/Staff/News](../handover/dt2-3-shared-security-review.md) và [publication/file plan](../handover/dt2-3-final-handoff.md) được soạn local; không comment GitHub. Runtime/test executable/base không đổi nên không chạy lại full suites trong phase handoff; đối chiếu source/JAR/proposal hashes và kiểm tra tài liệu/secrets ở đúng allowlist.

Provenance trước publication: sourceSha256 trong evidence là raw working-copy bytes của lượt chạy dirty, không phải commit triển khai. Git text normalization có thể đổi CRLF→LF; inputs.json giữ cả raw artifact hashes và canonical LF hashes cho proposal snapshots. Trước publication phải so đúng normalization scope và cập nhật evidence nếu nội dung/executable/base thay đổi, không gán hash lượt chạy cũ cho một artifact khác.

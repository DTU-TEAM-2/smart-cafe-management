# DT2-3 / DT2-27 — final local handoff

> Latest base update: Customer PR14 merged into develop b96a821; feature synchronization e2a3eb3 has no conflicts. Fresh primary315 and proposed B468 plus their separate live HTTP/Auth runs pass. Official A remains merge-blocked; News/Bills proposals still need owners. See the current review follow-up for hashes/timestamps; earlier baseline statements below are historical.

> Current correction: official Staff57ea6f1 has fixed version/CAS/auth_version/callers. Do not apply historical Staffb74 proposals or treat its5/9 failures as current. Current official/proposed candidates, Draft verification timestamp and remaining News/Bills blockers are recorded in [review follow-up](../reports/dt2-3-review-follow-up.md). The dated results below remain historical.


> Follow-up sau review PR #19: xem [kết quả hiện hành](../reports/dt2-3-review-follow-up.md). Các số liệu/JAR và hạn chế bên dưới là snapshot của các lượt handoff/publication trước.

> Cập nhật18/11/2026: CF-04 đã được xác nhận, user đã cho phép commit/push và Draft PR vào develop. Base mới331c1e3 đã tích hợp không conflict; trạng thái HOLD do thiếu CF ở phần handoff15/11 là lịch sử. Kết quả và blockers hiện hành ở [publication report](../reports/dt2-3-publication.md), không suy ra ready-to-merge.

Ngày15/11/2026. Owner đã chấp nhận Backend DT2-3 triển khai và kiểm thử local đạt; **không chấp nhận full integrationPASS**. Lượt này chỉ hoàn thiện tài liệu, phân loại file, đóng gói regression/proposal và chuẩn bị publication; không đổi runtime/test executable, API contract, DDL hoặc task của thành viên khác.

Branch giữ nguyên `feature/dt2-3_personal-account-backend`; HEAD/base `ffa4d9527e7924dd0bc9815fefb3034463d03156`, implementation còn working tree. PR #11 HEAD `4c7b9698ecdddd6f27ea093bea8030a90de4edfd`, #15 HEAD `ff109509cebc9e095dbe28efa9ff21e970f3d90c` được đối chiếu metadata lại, HEAD/base không đổi. Không commit, stage, push, tạo PR, comment GitHub, request review hoặc JiraDone.

## Kết quả được bàn giao

- ADMIN/STAFF xem và tự sửa đúng name/address/phone theo QA #8; CUSTOMER bị từ chối. DTO allowlist, current DB authority và optimistic version; không quản trị quyền/lương qua Profile.
- Đổi mật khẩu dùng policy/BCrypt/Auth hiện hữu, từ chối reuse, cập nhật timestamp/flag/auth_version/version nguyên tử và phát replacement token sau commit. Hạn đúng30×24h UTC; Login/Profile trả trạng thái server, chưa khóa business API toàn hệ thống.
- Header/Profile chung projection; ID/salary/version string giữ precision. [API contract](../api/dt2-3-profile.yaml), [success examples](../api/dt2-3-http-examples.json), [error examples](../api/dt2-3-http-errors.json) và [handoff DT2-28](dt2-3-backend.md) giữ nguyên.

DDL20261113.1 không đổi: `84cb9f2e99073ea73b117e828dc7f144928052a8f8345cde6f903b82280e4b5e`. Primary runtime JAR: `7e1fca9a1138b02912700b2f7004508732e2e9361da359e8b39cf71b5e3f2b12`.

| Bằng chứng được tái sử dụng, không chạy lại trong lượt handoff | Kết quả |
| --- | --- |
| Primary Maven/MySQL |260test,0failure,0error,0skip — PASS |
| Profile Newman hai vòng đầy đủ + Menu/QR |203request/587assertion — PASS;10SQLchecksPASS |
| Auth/OTP/Home HTTP + SMTP loopback |39request/57assertion — PASS |
| Frontend lint/build, Playwright |PASS;70/70 UI tests, giữ giới hạn HTTP mock |
| Concurrency Staff/Profile trước/sau proposal |9test/5failure baseline;9/9PASS sau proposal |
| Full selective candidate với fixture News đúng |396test/2failure/0error/0skip — **FAIL** |
| Candidate từng nhóm |Profile22/22, concurrency9/9, Customer21/21, News59/59MySQLPASS; không suy thành fullPASS |

Negative controls được ghi riêng, không cộng vào acceptance. [Hardening report](../reports/dt2-3-hardening.md), [primary machine evidence](../reports/dt2-3-postman/results.json), [Auth evidence](../reports/dt2-3-postman/auth-regression.json), [candidate evidence](../reports/dt2-3-integration-results.json) là các nguồn có mục đích khác nhau. Manifest backend133file/JAR và toàn runtime/tests/scripts/API được so với revision đã kiểm trước khi tái sử dụng; thay đổi lần này chỉ ảnh hưởng handoff/docs/proposal packaging. Raw logs/XML/config và private review record tiếp tục ở ngoài Git. Static links/whitespace, provenance và secret scan/manual review được ghi trong local review mới; không gán các checks này thành một lần Maven/Newman/Playwright mới.

## Dependency cần owner xử lý

| Gate | Finding / quyết định cần nhận | Người xử lý tiếp / nguồn |
| --- | --- | --- |
| Integration/merge |Staff writer không kiểm/tăng users.version gây lost update/stale acceptance; thống nhất users→staff_profiles, auth_version transition, bean-name management và caller version |Owner Staff `yennhi5033`; [review PR #11](dt2-3-pr11-review.md) |
| Integration/merge |Broad staff matcher cho STAFF đọc personnel/salary khác account; consumer News thiếu Profile; Auth/verified-db bị revert ở Staff/Customer; bean collision khi giữ Auth |Owner Auth/Staff/News và integrator; [shared review cho PR #11/#15](dt2-3-shared-security-review.md) |
| Integration/merge |Hai Staff success request chưa gửi required version của **proposal**: StaffControllerTest:198 và StaffHttpReportTest:604/TC-17 |Caller migration proposal chưa hoàn thiện; không phải baseline failure PR #11. Giữ assertion, thêm missing/stale-version cases trước khi chạy lại affected tests |
| Before release |**AUTH-GATEWAY-01 — NEEDS OWNER DECISION**: shared admission/body-size/backoff/trusted proxy/multi-replica policy |Owner Auth/Gateway + release owner; thông số trong report chỉ là đề xuất. Bounded localhost probe không quan sát429, không phải DoS/production brute-force proof |
| Deployment |Canonical target/lineage/grants/DEFINER/backup/restore/rollout |DBA/owner; snapshot read-only ở lượt hardening là legacy13tables/0views, target hiện hành cần DBA xác nhận; không tự sửa DB chung |
| Quality/publication |SonarQube approved environment chưa có; CF chính thức và quyền publication chưa được cấp |Leader/quality owner; Sonar/GitHub Actions/deployment NOT RUN, không sửa CI/validator |

## Phạm vi file đề xuất commit

Đối chiếu **50 file** cuối lượt hardening:45file giữ trong phạm vi PR,5file chỉ local. Thêm4artifact handoff cần thiết dưới đây nên allowlist đề xuất hiện có **49file**, tổng working set54file. Không dùng `git add .`, không stage toàn worktree candidate. Danh sách từng path/hash/reason và `stage-files.txt` được chuẩn bị ngoài Git cho người thực hiện publication.

| Nhóm cần stage | Số file | Lý do |
| --- | ---: | --- |
| backend/src/main Java — Profile6 và Auth/Home/Security10 |16|API self-profile, shared Auth lifecycle/projection/security tối thiểu; runtime đã kiểm giữ nguyên |
| backend/src/test — ProfileMySql, validation, failure, expiration |4|Permission/rollback/race/expiry regressions đã chạy |
| scripts — ProfileFixturePassword, profile/auth live runners, renderer |4|HTTP/MySQL acceptance tái lập, native verified-db và sanitized-only reporting |
| docs/api — OpenAPI + success/error examples |3|Contract FE và HTTP thực tế đã che credential |
| decisions/database-contract/open-questions |3|Nguồn quyết định, schema authority và blocker có owner |
| docs/handover — requirements, backend, hai owner reviews, final handoff |5|AC/API/caller instructions và dependency coordination |
| docs/qa — test-cases, collection, environment mẫu, data fixture |4|Requests/assertions và dữ liệu tổng hợp test, không chứa credentials thật |
| docs/qa/rehearsals/dt2-3 — README, source probe, inputs, ba patch |6|Chỉ proposal/documentation; không compile vào module DT2-3, không đưa whole Staff/News/Customer vào production source |
| docs/reports — hardening, integration JSON, primary Newman JSON, Auth JSON |4|Một báo cáo phân tích, hai loại HTTP evidence và candidate provenance; raw exports không được chọn |

Các file local không stage trong50file gốc:

| File | Lý do giữ ngoài PR |
| --- | --- |
| docs/handover/dt2-3-pr-description.md |Body thao tác GitHub chỉ local; bản cuối sao lưu ngoài repo, chưa publish |
| docs/reports/dt2-3-changed-files.json |Inventory50file của lượt trước, giữ snapshot ngoài Git; thay bằng allowlist49file local hiện hành |
| docs/reports/dt2-3-backend.md |Index lặp kết quả hardening/handoff |
| docs/reports/dt2-3-postman/README.md |Generated report lặp JSON/examples và report chính |
| docs/reports/dt2-3-postman/index.html |Generated presentation lặp cùng report; không cần cho code review |

Bốn artifact mới: `dt2-3-pr11-review.md`, `dt2-3-shared-security-review.md`, file handoff này và `shared-security-regressions.patch`. Các file loại trừ vẫn được bảo toàn local/backup, không xóa để dọn working tree. Không stage `.env`, private review/backup/host paths, raw Newman response/environment/token, raw logs/XML, database export, target/dist/node_modules/cache hoặc full candidate code. Tài liệu PR chỉ dùng đường dẫn repo/immutable source URLs cần thiết.

## Commit / Draft PR plan — chưa thực hiện

Đề xuất **một atomic feature commit** cho49file đã được review, đúng task DT2-3/DT2-27. Type/scope `feat(profile)`; subject nêu self-profile/password lifecycle, body link DT2-27 và ghi dependencies. Commit/title cuối **HOLD** cho tới khi leader xác nhận CF thật; không gán số tạm, không dùng CF task khác hoặc sửa validator. Stage theo exact allowlist, kiểm staged diff/secrets và review guide mới nhất trước push; nếu file/base/executable đổi, kiểm lại phần bị ảnh hưởng theo Git Flow.

Sau khi owner xác nhận CF **và** cho phép publication, đề xuất push branch hiện hữu và mở **Draft PR vào develop**, assignee là người thực hiện. Reviewers đề xuất `NV-DuyManh` (Auth/News integration) và `haitan2408` (Staff/DB/security), cả hai khác tài khoản đang chuẩn bị publication `deptraivodich`; kiểm lại tác giả/availability khi thực sự mở PR. Owner Staff nhận dependency handoff, chưa được nhắn hoặc gán việc qua công cụ. Không có approval được giả định.

**PR readiness:** local body, API handoff, file allowlist, review proposals và evidence đã chuẩn bị; publication đang HOLD. **Merge readiness: BLOCKED**, cần sửa P1/composition/caller migration, required CI và hai independent approvals mới trên revision cuối; self-review không thay approval. **Release/deploy readiness: BLOCKED** bởi policy/DBA/quality decisions nêu trên. Không gọi local backend acceptance là DoD/tích hợp/phát hành đã hoàn tất.

Skills đã đọc cho handoff: `cafe-workflow` (`.agents/skills/cafe-workflow/SKILL.md`), `cafe-review` (`.agents/skills/cafe-review/SKILL.md`), `cafe-quality` (`.agents/skills/cafe-quality/SKILL.md`). Chúng hướng việc giữ provenance, phân biệt standalone/composition, reuse checks theo SHA và giữ publication trong phạm vi user cho phép; không tự cấp quyền push/comment/merge.

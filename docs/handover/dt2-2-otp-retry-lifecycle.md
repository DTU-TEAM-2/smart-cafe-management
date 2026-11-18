# DT2-2 / CF-03 — OTP resend lifecycle, 12/11/2026

Phạm vi: sửa P1 còn lại trong [PR #7](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/7), theo yêu cầu trực tiếp của chủ repo ngày 12/11 và [review tại 2ee793d](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/7#pullrequestreview-5868280637). Chủ repo yêu cầu thực thi code/tests, ưu tiên mỗi resend được tiếp nhận tiêu thụ budget và expiry cố định, giữ OTP cũ khi SMTP lỗi; cho phép commit local sau kiểm thử, chưa cho phép push/merge/deploy/approve/resolve discussion. Đây cũng là nguồn yêu cầu được lưu trong repo cho [ADR-006](../architecture/decisions.md#adr-006--vòng-đời-otp-resend-trong-dt2-2-12112026).

## A. Root cause và chính sách

Tại `2ee793d5d0365c5dfc43f4ae59a13cd8d8aaefa8`, real + SMTP failure gọi `updateOtpCooldownOnly`, giữ `sends=1` và expiry cũ. Dummy gọi `updateOtpResend`, tăng sends/gia hạn. Sau hai resend, dummy chạm `sends=3`, lần thứ ba trả 400 trong khi real vẫn 200.

Sửa bằng một `consumeOtpResend` chung: giữ `FOR UPDATE`, kiểm used/expiry/budget/cooldown, kiểm SMTP readiness cho cả hai, conditional UPDATE tiêu thụ đúng một lượt trước SMTP. `sends` đếm yêu cầu được tiếp nhận, không đếm email đã giao. Resend không gia hạn challenge. Phản hồi TTL là thời gian còn lại tại lúc tiếp nhận, làm tròn lên; verify/resend từ chối tại `now >= expires_at`.

Kết quả SMTP chỉ thay hash khi gửi thành công. Lỗi SMTP giữ hash/attempts/used_at/sent_at/hạn cũ, ghi FAILED. Dummy giữ hash ngẫu nhiên, không gửi email hoặc ghi SENT. UPDATE trả khác 1 hoặc lỗi DB gây rollback và HTTP 500, không đi qua catch SMTP.

## B. Files changed

| File | Phương thức/phần | Thay đổi và lý do |
| --- | --- | --- |
| `backend/src/main/java/vn/codegym/smartcafe/auth/OtpService.java` | constructor, initiate/verify/resend | Dùng Clock hiện có; expiry boundary; reserve chung trước SMTP, TTL còn lại, bắt riêng lỗi giao thư để không nuốt lỗi DB. |
| `backend/src/main/java/vn/codegym/smartcafe/auth/AuthRepository.java` | consumeOtpResend, recordOtpDelivery | Thay hai update có lifecycle khác nhau bằng reservation có điều kiện và cập nhật kết quả giao thư độc lập; không thay schema. |
| `backend/src/test/java/vn/codegym/smartcafe/auth/OtpRetryLifecycleIntegrationTest.java` | các regression lifecycle | MockMvc + MySQL thật, Clock/EmailService kiểm soát; retry, hash cũ, outcome mix, cooldown, expiry boundary, dummy, concurrency và rollback. Repository spy chỉ giả lập lỗi transition trong test tương ứng. |
| `backend/src/test/java/vn/codegym/smartcafe/auth/OtpServiceTest.java` | resend/readiness/DB failure | Cập nhật contract budget, thêm 4 trường hợp row count 0/SQL exception trước/sau SMTP. |
| `backend/src/test/java/vn/codegym/smartcafe/auth/OtpIntegrationTest.java` | fixtures, SMTP failures, concurrency | Counter theo contract mới; giữ socket SMTP local cho MAIL FROM/RCPT TO/DATA/disconnect/healthy; chạy concurrency 5 lần, wait có giới hạn; cleanup chỉ email/key fixture. |
| `backend/src/test/java/vn/codegym/smartcafe/auth/UnconfiguredSmtpIntegrationTest.java` | setup/cleanup/resend | Bỏ DELETE toàn bảng, guard trước từng DML; nhiều vòng readiness failure giữ nguyên toàn bộ state real/dummy. |
| `backend/src/test/java/vn/codegym/smartcafe/auth/TestDatabase.java` | update | SELECT DATABASE() và fail-closed nếu không có hậu tố `_test` trước từng DML fixture của các test OTP bị tác động. |
| `docs/architecture/decisions.md` | ADR-006 | Ghi nguồn yêu cầu, semantics budget/expiry, transaction và residual risk. |
| `docs/architecture/database-contract.md` | đối chiếu 12/11 | Ghi DDL read-only thực tế và chênh lệch; chưa coi `_test` là bằng chứng schema triển khai. |
| `docs/product/open-questions.md` | DT2-2 DDL | Chủ DB cần chốt reconciliation/provisioning/deployment; giữ phần phụ thuộc, không tự migrate. |
| `docs/handover/dt2-2-otp-retry-lifecycle.md` | báo cáo này | AC, transition, kiểm chứng và giới hạn bàn giao. |

## C. State transition

`E` là hạn cố định lúc forgot; `N` là thời điểm nhận resend. Bảng áp dụng khi challenge chưa dùng, chưa hết hạn; real verify còn phụ thuộc attempts < 5. Không in hash/OTP/token.

| Bước | sends real/dummy | code_hash real/dummy | expires_at | next_send_at | delivery_status real/dummy | HTTP real/dummy | Verify |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Forgot | 1 / 1 | created / created | E | tạo + 60s | PENDING / PENDING (khởi tạo hiện có) | 200 / 200 | Real có mã đúng còn hạn; dummy không có grant |
| Resend #1 | 2 / 2 | changed nếu SMTP success, unchanged nếu fail / unchanged | E | N + 60s | SENT hoặc FAILED / PENDING | 200 / 200 | Real dùng mã giao gần nhất; dummy không có grant |
| Resend #2 | 3 / 3 | cùng quy tắc #1 | E | N + 60s | SENT hoặc FAILED / PENDING | 200 / 200 | Cùng quy tắc #1 |
| Resend #3 | 3 / 3 | unchanged / unchanged | E | unchanged | unchanged | 400 / 400 | Hết budget resend không tự consume OTP |
| Cooldown đang hoạt động, còn budget | unchanged | unchanged | E | unchanged | unchanged | 429 / 429, cùng Retry-After/body | Không gọi SMTP |
| Expiry (N >= E) | unchanged | unchanged | E, không hồi sinh | unchanged | unchanged | 400 / 400 | Không OTP/reset grant |
| SMTP failure sau readiness | +1 / +1 | unchanged / unchanged | E | N + 60s | FAILED / PENDING | 200 / 200 | OTP cũ real còn hạn vẫn hợp lệ |
| SMTP success | +1 / +1 | changed / unchanged | E | N + 60s | SENT / PENDING | 200 / 200 | Chỉ OTP mới real hợp lệ; sent_at cập nhật |
| Thiếu cấu hình SMTP | unchanged | unchanged | E | unchanged | unchanged | 400 / 400 | OTP cũ không bị thay |
| Lỗi DB / UPDATE 0 bất thường | rollback | rollback | unchanged | rollback | rollback | 500 | Không báo thành công giả; SMTP có thể đã nhận thư |

## D. Test results

Môi trường: Windows, Java 21, MySQL 8.0.45, `smart_cafe_auth_test`, Flyway runtime tắt trong test config. Chỉ nạp TEST_DB_* từ môi trường/.env local; không dùng DB_URL hệ thống làm fallback.

| Check | Kết quả | Phạm vi/bằng chứng |
| --- | --- | --- |
| BEFORE, `mvnw.cmd -B -ntp -Dtest=OtpRetryLifecycleIntegrationTest test` trên production code nguyên trạng `2ee793d` | **FAIL đúng P1**: 1 test, 1 failure, 0 error/skipped | Real HTTP [200,200,200], dummy [200,200,400]; sends real [1,1,1], dummy [2,3,3]. `pr7-otp-before.log`. |
| Lần targeted đầu | FAIL do 4 ClassCastException trong assertion test | Driver trả LocalDateTime cho DATETIME. Sửa assertion dùng typed Timestamp; không đổi production để né lỗi test. `pr7-otp-targeted.log`. |
| Targeted sau sửa assertion: `mvnw.cmd -o -B -ntp -Dtest=OtpRetryLifecycleIntegrationTest,OtpServiceTest,UnconfiguredSmtpIntegrationTest test` | **PASS 28/28**, 0 failure/error/skipped | 18 lifecycle integration + 9 service + 1 unconfigured SMTP. `pr7-otp-targeted-final.log`. |
| Full backend: `MAVEN_ARGS=-o`, `powershell -NoProfile -ExecutionPolicy Bypass -File .\test-be.ps1` | **137 PASS, 1 FAIL**, tổng 138, 0 error/skipped | Bản class đã compile còn assertion sendsCount=1 trong OtpIntegrationTest; contract mới cho kết quả 2. Giữ nguyên log thất bại: `pr7-otp-backend-verify.log`. |
| Sau cập nhật assertion, `mvnw.cmd -o -B -ntp -Dtest=OtpIntegrationTest verify` | **PASS 18/18**, BUILD SUCCESS | Bao gồm 5 lần cạnh tranh SMTP socket thật ở localhost; đóng gói JAR thành công. `pr7-otp-affected-verify.log`. |
| Tổng bằng chứng cuối | **138/138 PASS**, 0 failure/error/skipped trong 19 XML report hiện tại | **Kết hợp hai lượt**: 120 test ngoài lớp OtpIntegrationTest tái sử dụng từ full run với cùng production code + 18 test lớp bị tác động chạy lại. Không tuyên bố một full invocation duy nhất đã xanh hoặc đổi verdict của full run lỗi. |
| Frontend `npm run lint`, `npm run build` | **PASS / PASS** | Không sửa UI. Logs `pr7-frontend-lint.log`, `pr7-frontend-build.log`. |
| Frontend `npm run test:ui -- --workers=2` | **PASS 36/36** | Desktop/mobile, HTTP mock; `pr7-frontend-ui.log`. Không phải recovery E2E với mail thật. |
| Postman collection | Static inspection: flow forgot → verify → reset → login và negative requests có trong collection; **Newman/Postman NOT RUN** | Collection cần cung cấp `testOtpCode` từ môi trường test; các script happy path có nhánh conditional, chưa tự chứng minh luồng chạy thành công. Collection hiện không có request resend; hồi quy retry ở MockMvc/MySQL và SMTP socket tests. Không sửa hoặc chạy collection vào DB chung. |
| Governance `node scripts/skills.mjs check` | **PASS** | 17 skills, 16 routes, 169 inherited files. |
| `node --test scripts/skills.test.mjs` | **PASS 9/9** | Không sửa registry/skill snapshots. |
| `git diff --check -- . ':(exclude).agents/skills/**'` | **PASS** | Sau sửa blank EOF trong tài liệu. |

Regression mới bao gồm tên test tương ứng yêu cầu: `realAndDummyChallengesHaveIdenticalRetryLifecycleAfterRepeatedSmtpFailures`, `smtpFailureConsumesResendBudgetWithoutInvalidatingPreviousOtp`, `mixedSmtpFailureAndSuccessPreservesPublicRetryBudget` (4 tổ hợp), `realAndDummyCooldownBehaviorMatchesAfterMultipleResends`, `expiredRealAndDummyChallengesAreRejectedConsistently` (E−1/E/E+1), `dummyChallengeNeverIssuesPasswordResetGrant`, `concurrentRealResendsDeliverOnlyOneOtp` (success/failure), `wrongOtpAndUsedChallengesHaveMatchingPublicErrors`, `failedDatabaseTransitionRollsBackResend` và unit `failedDatabaseTransitionDoesNotSilentlyReportSuccessfulResend`. Có so sánh toàn bộ public JSON/headers sau bỏ challengeId ngẫu nhiên và kiểm DB state sau từng lượt.

Toàn bộ log ở `C:\Users\khatr\review-guides\evidence\`, ngoài Git. Chi tiết revision/blob SHA của bằng chứng được ghi trong review record local.

## E. Residual risk và giới hạn

- SMTP và DB commit không atomic. Nếu SMTP nhận thư rồi UPDATE/commit thất bại, thư có thể chứa OTP không được lưu; rollback giữ state cũ và API trả 500. Không có tự động retry/outbox trong phạm vi này.
- Giữ row lock trong lúc gọi SMTP; độ trễ/lỗi mạng ảnh hưởng thời gian giữ transaction. Không tuyên bố bảo vệ timing side-channel hoặc mọi đường account enumeration.
- `_test` dùng DDL đã có, không đổi schema/fixture/migration. DDL `smart_cafe` khác sơ đồ và `_test`, được ghi riêng; chưa nghiệm thu/deploy runtime chung. Không gửi Gmail thật.
- Frontend checks dùng HTTP mock; Postman/Newman chưa chạy. Không thay UI, quyền/auth contract, Home/Sales hoặc schema.

## F. Git và handoff

- Branch: `feature/cf-03-auth-recovery`; base công việc và remote đã fetch: `2ee793d5d0365c5dfc43f4ae59a13cd8d8aaefa8`.
- Revision mới: commit chứa báo cáo này (SHA cụ thể được cung cấp trong trả lời và review record local bên ngoài Git).
- Chỉ commit local khi checks đạt; chưa push. Không merge, deploy, approve hoặc resolve discussion.
- Local evidence/review đặt dưới `C:\Users\khatr\review-guides\`, ngoài Git. Bước tiếp: chủ repo kiểm tra commit local và quyết định có push lên PR #7 để reviewer xác nhận lại hay không.

Skills thực sự đọc: `diagnosing-bugs` (`.agents/skills/diagnosing-bugs/SKILL.md`, tạo repro đỏ trước sửa); `cafe-spring-api` (`.agents/skills/cafe-spring-api/SKILL.md`, transaction/Clock/API); `cafe-database` (`.agents/skills/cafe-database/SKILL.md`, đọc DDL, giữ schema); `cafe-quality` (`.agents/skills/cafe-quality/SKILL.md`, regression và bằng chứng phân loại); `cafe-review` (`.agents/skills/cafe-review/SKILL.md`, tự review correctness/security); `ponytail` (`C:\Users\khatr\.codex\skills\ponytail\SKILL.md`, thay đổi nhỏ ở shared resend, không thêm dependency/schema/outbox). Rule MySQL/database nguồn chuẩn của repo có ưu tiên hơn câu lịch sử chưa chọn engine trong cafe-database.

# DT2-2 / CF-03 — verify OTP versus resend race

Nguồn: yêu cầu trực tiếp chủ repo ngày 12/11/2026 và [finding P1 của NV-DuyManh tại fbaa952](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/7#discussion_r4550408862). Phạm vi được giao: CAS cho consume và wrong-attempt theo snapshot, regression thật trên MySQL, giữ nguyên các policy tại ADR-006; chỉ commit local khi checks đạt. Chưa được push/merge/deploy/approve/resolve discussion.

## A. Root cause

`verifyOtp` đọc/so hash A bằng SELECT thường; trong lúc verify dừng trước UPDATE, resend có thể commit hash B và tăng `sends`. UPDATE cũ chỉ kiểm used/attempts/expiry nên verify A vẫn consume row chứa B và phát reset grant. Row lock của resend không bảo vệ snapshot mà verify đã đọc trước đó.

Nhánh OTP sai cũng UPDATE theo ID mà không ràng buộc snapshot. Probe bổ sung đặt attempts=4, dừng wrong verify trước UPDATE, cho resend commit: request cũ tăng attempts của trạng thái mới thành 5 và khóa OTP mới. Hai lỗi đều được tái hiện trên production code nguyên trạng `fbaa9528039a3df258165b3765e03a86374b6e5f` trước sửa. Audit bổ sung phát hiện reset password kiểm ACTIVE trong Java nhưng UPDATE chỉ so auth_version; một probe chuyển user sang INACTIVE sau kiểm tra Java vẫn nhận 200, kỳ vọng 401. Probe này chạy trước khi thay SQL password update (đường này còn nguyên như fbaa952), sau khi CAS OTP đã được sửa.

## B. Code changes

| File | Phương thức | Thay đổi |
| --- | --- | --- |
| `backend/src/main/java/vn/codegym/smartcafe/auth/AuthRepository.java` | `markOtpUsedIfCurrent` (215) | Conditional UPDATE kiểm cả `code_hash` và `sends` đã xác minh, cùng used/attempts/expiry. Xóa signature consume chỉ nhận ID. |
| Cùng file | `updateUserPassword` (101, overload có expectedAuthVersion) | Thêm điều kiện ACTIVE ngay trong UPDATE để từ chối khi user bị vô hiệu sau SELECT, giữ nguyên CAS auth_version. |
| Cùng file | `incrementOtpAttemptsIfCurrent` (181) | Wrong-attempt cũng CAS hash/generation và kiểm chưa used, attempts < 5, chưa expired. Xóa signature increment chỉ nhận ID. |
| `backend/src/main/java/vn/codegym/smartcafe/auth/OtpService.java` | `verifyOtp` (146; guard tại 176/192) | Truyền đúng hash/sends từ snapshot; chỉ tiếp tục nếu row count đúng 1; lỗi CAS dùng lỗi OTP trung tính hiện có. Giữ `REQUIRES_NEW` cho attempts. |
| `backend/src/test/java/vn/codegym/smartcafe/auth/OtpVerifyResendRaceIntegrationTest.java` | regression mới | MockMvc, transaction/MySQL thật, latch/barrier với timeout; repository spy chỉ điều phối rồi `callRealMethod()`. Không mock row count/SQL state trong concurrency tests. |
| `backend/src/test/java/vn/codegym/smartcafe/auth/OtpServiceTest.java` | kiểm result consume (210/223) | Unit test row count 0/2 và DataAccessException không phát grant; tách khỏi bằng chứng MySQL concurrency. |
| `docs/handover/dt2-2-otp-verify-resend-race.md` | báo cáo này | Nguồn yêu cầu, evidence, transaction và phạm vi bàn giao. |

Không thay DDL, fixture schema, migration, SMTP adapter, UI, Sales, policy thời hạn/budget/attempts hoặc hợp đồng token.

## C. Test evidence

Môi trường: Java 21, MySQL 8.0.45, schema riêng `smart_cafe_auth_test`, UTC, Flyway runtime off. Đã kiểm `SELECT DATABASE()`/`VERSION()`/`SHOW CREATE TABLE auth_otp_challenges` read-only trước chạy; cấu trúc hiện có chứa đủ cột CAS. Fixture mới chỉ dọn email/user/HMAC limit key riêng và dùng guard `_test` trước DML. Không ghi vào DB chung.

| Test | Trước sửa | Sau sửa |
| --- | --- | --- |
| Verify A, resend commit trước consume | **FAIL**, expected 400 / actual 200 | **PASS** trong full run mới |
| Wrong OTP snapshot cũ versus resend | **FAIL**, expected attempts=4 / actual 5 | **PASS** trong full run mới |
| Verify consume trước resend | Chưa có test này | **PASS** trong full run mới |
| Hai verify cùng OTP (3 lượt barrier) | Chưa có test này | **PASS** trong full run mới |
| SMTP fail: verify cũ bị từ chối, verify mới đọc generation hiện tại dùng được OTP cũ | Bảo toàn OTP sau fail đã có; stale snapshot chưa được bảo vệ | **PASS** trong full run mới |
| Hash không đổi nhưng generation đổi / hash đổi nhưng generation giữ nguyên | Chưa có | **PASS** trong full run mới |
| Hết hạn/đủ attempts/used ở ranh giới UPDATE | Chưa có test điều phối này | **PASS** trong full run mới |
| Wrong verify đồng thời, attempts lưu và dừng ở 5 | Có sequential coverage | **PASS** trong full run mới |
| Hai resend đồng thời; real/dummy budget/expiry | PASS tại fbaa952 | **PASS** trong full run mới |
| Recovery sau rotate, token/grant cũ mất hiệu lực; hai reset cùng grant | Có recovery coverage, chưa có test điều phối reset này | **PASS** trong full run mới |
| User bị vô hiệu trước password UPDATE | **FAIL**, expected 401 / actual 200 trên SQL password update cũ | **PASS** trong full run mới |
| Full Backend suite | 138 PASS tại fbaa952 (bằng chứng lịch sử) | **159 PASS**, 0 failure/error/skipped trong **một full run mới** |

RED: `mvnw.cmd -o -B -ntp -Dtest=OtpVerifyResendRaceIntegrationTest test` trước sửa: **2 tests, 2 failures, 0 errors, 0 skipped**. Latch dừng đúng trước hai UPDATE cũ; SQL thật cho kết quả sai nêu trên. Log `C:\Users\khatr\review-guides\evidence\pr7-verify-resend-race-red.log` nằm ngoài Git; giữ nguyên verdict FAIL.

GREEN targeted (trước khi thêm probe ACTIVE và ba unit consume): `mvnw.cmd -o -B -ntp -Dtest=OtpVerifyResendRaceIntegrationTest,OtpRetryLifecycleIntegrationTest,OtpServiceTest test`: **44 tests PASS, 0 failure/error/skipped**, `pr7-verify-resend-race-green.log`.

RED bổ sung ACTIVE: **1 test FAIL, 0 errors/skipped**, `pr7-reset-active-race-red.log`. SQL password update chỉ thêm guard ACTIVE sau khi probe đỏ.

FULL: `MAVEN_ARGS=-o powershell -NoProfile -ExecutionPolicy Bypass -File .\test-be.ps1` sau khi chốt toàn bộ source/test: **159 tests PASS, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** trong một lượt, hoàn tất 12/11/2026 19:55:09 +07:00. Log `pr7-verify-resend-race-full-verify.log` ngoài Git. Gồm 138 ca hồi quy có sẵn + 18 ca race/state mới + 3 unit guard consume; không ghép targeted vào số full.

Governance `node scripts/skills.mjs check`: **PASS** (17 skills, 16 routes, 169 inherited files); `node --test scripts/skills.test.mjs`: **9/9 PASS**; `git diff --check -- . ':(exclude).agents/skills/**'`: **PASS**. Frontend/Newman/Gmail không chạy trong lượt này: không đổi UI hoặc HTTP contract. Không tuyên bố CI mới đã chạy vì chưa push.

## D. Transaction & locking

- UPDATE của MySQL là current read: dưới tranh chấp, điều kiện CAS phải khớp trạng thái đang được cập nhật, không phải snapshot SELECT cũ của transaction verify. Kiểm cả hash và generation tránh lọt khi resend giữ hash vì SMTP fail hoặc sinh lại cùng OTP.
- Khi CAS trả 0, không phát grant, không tìm user để cấp grant và không consume OTP mới; caller nhận `400 OTP_ERROR` trung tính. Row count khác 1 đều bị từ chối. DataAccessException không được nuốt thành thành công.
- Verify consume trước thì UPDATE giữ row lock tới commit; resend dùng FOR UPDATE hiện có sẽ thấy used và không gửi email. Resend commit trước thì verify snapshot cũ không khớp CAS. Hai verify cùng snapshot chỉ một UPDATE có thể chuyển used từ NULL.
- Không thêm FOR UPDATE cho SELECT verify. Wrong-attempt vẫn dùng REQUIRES_NEW: outer transaction chỉ đọc, không giữ row lock khiến inner transaction tự chờ. UPDATE attempts=1 commit ở inner rồi exception OTP ở outer không làm mất bộ đếm. UPDATE attempts=0 ném lỗi, inner rollback không ghi gì.
- Giữ `noRollbackFor=OtpException` ở outer; không mở rộng rollback exception hoặc đổi ranh giới transaction. Test wrong attempts đồng thời dùng ba outer + ba inner connections trong pool, kiểm giới hạn và hoàn tất hữu hạn. Đây không phải load/capacity test; REQUIRES_NEW vẫn cần pool có đủ connections.
- Resend thất bại tăng sends nhưng giữ hash. Verify đã đọc generation cũ phải retry; request verify mới đọc sends mới vẫn dùng OTP cũ nếu còn hạn/chưa locked. Không reset attempts khi resend.

## E. Hồi quy và audit phạm vi Auth/OTP

Đã rà call sites của consume/increment/password update, forgot limit, resend reservation/delivery, reset grant và kiểm tra `auth_version`. Consume/increment chỉ có caller trong verify và đã chuyển sang CAS. Resend vẫn row lock + conditional budget; forgot limit vẫn atomic/fail-closed. Reset dùng UPDATE có expected auth_version; regression hai reset đồng thời kiểm chỉ một lần đổi mật khẩu/increment version. Đã bổ sung guard ACTIVE tại UPDATE để sửa race deactivation chứng minh bằng probe riêng; khi thua không đổi hash/version và trả lỗi InvalidToken hiện có. Overload password update không nhận version không có caller ứng dụng trong lần rg này; reset tiếp tục chỉ dùng overload có version. Không đổi login/token issuer, rate limit hoặc policy nghiệp vụ khác.

Test recovery dùng API forgot → resend → verify → reset → login và bearer cũ → auth/me; kiểm mật khẩu cũ/grant cũ/OTP đã used bị từ chối. Nếu hai OTP ngẫu nhiên trùng nhau, giá trị số đó vẫn là OTP hiện tại cho một request mới; tình huống snapshot generation cũ được kiểm riêng, không dựa vào xác suất để test xanh.

Full suite bao gồm hồi quy real/dummy budget `1→2→3`, cooldown/expiry, SMTP lỗi/thiếu cấu hình, resend concurrency, login/RBAC/Home và các module không sửa. Không dùng kết quả UI HTTP mock để khẳng định authenticated recovery E2E.

Giới hạn giữ nguyên: chưa Gmail thật, Newman recovery, DDL/deployment chung, timing side-channel hoặc load test pool. SMTP nhận thư trước DB commit thất bại vẫn là residual risk. Chênh lệch DDL chung đã ghi ở database contract, không tự reconcile schema.

## F. Git và review

Branch `feature/cf-03-auth-recovery`; HEAD/remote lúc bắt đầu: `fbaa9528039a3df258165b3765e03a86374b6e5f`; working tree sạch trước khi thêm test. Commit mới là commit chứa báo cáo này, SHA cụ thể được trả trong phản hồi và record review local. Chỉ commit sau full suite/checks PASS; chưa push theo yêu cầu mới của chủ repo. Không đăng comment, approve hoặc resolve thread trong lượt này.

Skills đã đọc: `diagnosing-bugs`, `cafe-spring-api`, `cafe-quality`, `cafe-review` tại `.agents/skills/<identifier>/SKILL.md`; `ponytail` đã đọc ở phiên triển khai trước để giữ diff nhỏ. Đây là tự review, không thay reviewer độc lập NV-DuyManh. Log và record review nằm dưới `C:\Users\khatr\review-guides\`, ngoài Git.

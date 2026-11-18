# DT2-3 → Auth / Staff / News: shared-security review proposal

> Follow-up PR #19 ngày18/11/2026: source refs và proposal dành cho HEAD mới được ghi ở [review follow-up](../reports/dt2-3-review-follow-up.md) và [artifact README](../qa/rehearsals/dt2-3/README.md). Các findings/version/line bên dưới là snapshot có ngày; không coi historical revert/bean collision là lỗi của HEAD Staff hiện hành.


> Cập nhật18/11/2026: findings bên dưới là snapshot15/11. Staffb74a365 sửa Auth resources/dashboard rename/management matcher; candidate hiện hành giữ matcher mới trả403 đúng. Newsc2343ed sửa root/path boundary nhưng vẫn thiếu Profile consumer, tái hiện401; patch mới chỉ là proposal trong candidate. Customera41152d đã khôi phục Auth/verified resources; domain/tests trùng Git tree với ref đã chạy, shared-chain reconciliation vẫn cần giữ đầy đủ rules. [Publication report](../reports/dt2-3-publication.md) là trạng thái mới nhất.

**Local handoff, chưa đăng comment GitHub, chưa request reviewer hoặc approve PR.** Áp dụng cho integration candidate, không quy mọi finding cho từng PR standalone.

| Đầu vào đã kiểm | Revision | Owner nhận bàn giao |
| --- | --- | --- |
| develop/Auth/Profile base | `ffa4d9527e7924dd0bc9815fefb3034463d03156` + working-tree Profile được kiểm | Owner Auth và integrator |
| [Staff PR #11](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/11) | `4c7b9698ecdddd6f27ea093bea8030a90de4edfd` | `yennhi5033` |
| Customer PR #14 | `057531c26b68073354801c2394236ed76c7680e7` | Owner Customer và integrator |
| [News PR #15](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/15) | `ff109509cebc9e095dbe28efa9ff21e970f3d90c` | `NV-DuyManh` |

PR #11/#15 metadata được đọc lại ngày15/11/2026: HEAD/base không đổi so với rehearsal. Không dùng review ở HEAD cũ làm bằng chứng cho bản khác. Hai reviewer đề xuất cho DT2-3 là `NV-DuyManh` và `haitan2408`; đây chưa phải phân công/approval. Auth owner cần leader xác nhận khi điều phối, không tự suy ra từ tài khoản đang kết nối.

## S1 — P1: broad Staff matcher cấp quyền đọc personnel ngoài self-profile

Source liên quan: `backend/src/main/java/vn/codegym/smartcafe/config/SecurityConfig.java:84` tại News HEAD, giữ `.requestMatchers("/api/v1/staff/**").hasAnyRole("ADMIN", "STAFF")`; broad namespace cũng có trong Auth base. Staff management route nằm tại `staff/StaffController.java:11` trên HEAD #11. [Matcher bất biến](https://github.com/DTU-TEAM-2/smart-cafe-management/blob/ff109509cebc9e095dbe28efa9ff21e970f3d90c/backend/src/main/java/vn/codegym/smartcafe/config/SecurityConfig.java#L84).

Trong composed candidate, Login thật lấy bearer STAFF user2 rồi GET `/api/v1/staff/1` (account ADMIN khác, dữ liệu tổng hợp) đọc được personnel/salary. **Expected403, actual200.** Một matcher broad xuất hiện trước rule management chặt hơn có thể quyết định request trước; giữ matcher Auth cũ khi ghép không tương đương giữ quyền của Staff standalone. Finding là privilege/ownership leak của **composition đã chạy**, không phải bằng chứng PR #11 standalone vốn cho STAFF vào management. News standalone chưa có Staff management controller cũng không tự lộ endpoint này chỉ vì có matcher.

Proposal: exact GET `/api/v1/staff/dashboard` cho STAFF/ADMIN trước namespace management; root `/api/v1/staff` và management `/api/v1/staff/**` ADMIN-only. Giữ CSRF management theo contract Staff; Profile chỉ miễn đúng PATCH self-profile và POST self-password dùng bearer header stateless. Không mở rộng role hoặc tắt CSRF toàn hệ thống để làm test xanh.

## S2 — P2: authentication consumer News không chạy cho Profile

`HeaderTokenFilter.java:21–28` trên HEAD #15 dùng allowlist trong shouldNotFilter; chưa có `/api/v1/profiles/` và root `/api/v1/staff`. [Source bất biến](https://github.com/DTU-TEAM-2/smart-cafe-management/blob/ff109509cebc9e095dbe28efa9ff21e970f3d90c/backend/src/main/java/vn/codegym/smartcafe/config/HeaderTokenFilter.java#L21). Khi kết hợp với Profile, bearer Login hợp lệ gọi GET `/api/v1/profiles/me` bị401 thay200. Đây là lỗi tương thích consumer/route khi Profile có mặt, không phải auth bypass và không phải finding News business API đang sai quyền độc lập.

Proposal bổ sung path vào **consumer hiện hữu**, giữ một Auth issuer/consumer contract; không tạo filter/Auth thứ hai. Matcher và consumer phải thống nhất root/path/method, current ACTIVE/role/auth_version, và không làm mất route/CSRF của Customer/Menu/Sales/News.

## S3 — dependency và bean collision, chỉ xuất hiện khi compose

HEAD #11 và #14 xóa/revert Auth và verified-db resources so với develop đã có Auth: ví dụ `auth/AuthRepository.java`, `auth/TokenService.java`, `database/DatabaseMigrationConfiguration.java`, `resources/application-verified-db.properties`, `resources/db/smart-cafe/V20261113_01__smart_cafe_schema.sql` cùng contract resources/dependencies. Literal merge candidate + Profile đã compile FAIL vì thiếu Auth/DTO/PasswordPolicy/dependency. Không quy việc revert này cho News#15, không khẳng định từng PR standalone không compile. Owner các nhánh cần reconcile với develop, giữ canonical DDL/safety artifact thay vì nhập cả revert hoặc sao chép một Auth khác.

Giữ develop/Auth và thêm Staff tạo bean-name collision: `auth/StaffController.java:11` và `staff/StaffController.java:10` đều sinh `staffController`. Startup probe thất bại trước proposal. Đặt tên bean management riêng trong [Staff proposal](../qa/rehearsals/dt2-3/staff-version-locking.patch); không tắt collision protection hoặc bỏ dashboard để né lỗi.

## Regression và patch đã bàn giao

- [integration-security-proposal.patch](../qa/rehearsals/dt2-3/integration-security-proposal.patch): diff trên **candidate kết hợp**, chưa được áp trên PR nào; không áp mù lên riêng News hoặc Staff. Owner cần chuyển đúng consumer/matcher semantics sang integration revision mình chọn.
- [shared-security-regressions.patch](../qa/rehearsals/dt2-3/shared-security-regressions.patch): đóng gói đúng method HTTP đã chạy trong candidate, thêm `staffCannotReadOtherEmployeePersonnelThroughManagementNamespace` vào ProfileMySqlTest. Ca `adminAndStaffReadOnlyTheirOwnPreciseProfileAndHeaderUsesSameProjection` đã có kiểm Profile GET bằng Login thật và bắt được S2.
- Trước proposal: hai test HTTP đều FAIL, Profile401 expected200 và Staff-other200 expected403. Sau proposal: toàn ProfileMySqlTest **22/22PASS** trên Tomcat/MySQL thật; test source được đối chiếu SHA với candidate đã chạy. Đóng gói patch lần này không thay source test/runtime hoặc chạy test mới.
- Full candidate cuối, đúng fixture News HEAD: **396 test,2 failure,0 error,0 skip**; News59/59MySQL, Customer21/21, concurrency9/9PASS. Hai Staff success payload thiếu version của **proposal** còn FAIL; không phải lỗi baseline PR #11. Không coi 22 Profile tests xanh là full integrationPASS.
- News audience đã có trong official DDL và fixture HEAD#15. Lượt trước dùng nhầm develop fixture là lỗi assembly rehearsal, đã sửa đầu vào và lưu lịch sử; không báo thành lỗi baseline News. External storage mocks trong test contributor không chứng minh cloud storage production.

[inputs.json](../qa/rehearsals/dt2-3/inputs.json) và [integration-results.json](../reports/dt2-3-integration-results.json) ghi revisions, test source hashes, counts và failure classification. Raw XML/logs/private config được giữ ngoài Git. Không đính kèm credentials, raw Login response/token hoặc dữ liệu nhân sự thật vào đề xuất review.

## Quyết định chung cần owner duyệt trước release

**AUTH-GATEWAY-01 — NEEDS OWNER DECISION; chưa được phê duyệt/triển khai.** Giữ đề xuất body-size/admission của [hardening report](../reports/dt2-3-hardening.md): cap JSON theo route, trusted peer-IP + per-identifier/account rolling failure budget/backoff, aggregate BCrypt/multi-replica budget,429/Retry-After và đường phục hồi không permanent lockout. Các ví dụ8KiB/5failure mỗi5phút/30request mỗi phút mỗiIP là thông số khởi đầu để owner đo/duyệt, không phải policy đã chốt. Không áp JSON cap cho upload và không sửa purpose CHECK của auth_otp_limits.

Evidence hiện có chỉ20request localhost có kiểm soát:8Login sai401,8current-password sai400 với tối đa4concurrent,2field131072ký tự400 và2valid request200; **chưa quan sát response429**, SQL không đổi. Không suy ra đã kiểm thử DoS hoặc được bảo vệ brute force production. OTP quota và News WebSocket admission khác phạm vi, không tự thay shared Login/current-password budget. Owner Auth/Gateway phải duyệt topology/trusted proxy/NAT/recovery/metrics và thực hiện test phù hợp trước release; DT2-3 không thêm limiter/bảng/DDL riêng.

Hành động cần owner: thống nhất composition trên revision mới, migrate Staff caller version, giữ shared consumer/matcher/CSRF và canonical verified-db resources, rồi chạy affected regressions trước khi cân nhắc integration ready. Lượt này không sửa nhánh thành viên, không comment GitHub, không request review, không merge hoặc triển khai.

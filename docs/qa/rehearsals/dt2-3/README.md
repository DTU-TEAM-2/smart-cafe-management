# DT2-3 — integration rehearsal artifacts

Đây là proposal/documentation, không phải production Staff/News code hoặc PR đã merge. [inputs.json](inputs.json) pin source revisions và SHA của các patch/test; [candidate evidence](../../../reports/dt2-3-integration-results.json) giữ kết quả thực tế. [Staff owner handoff](../../../handover/dt2-3-pr11-review.md) có file/line, expected/actual và DTO/caller migration; [shared security handoff](../../../handover/dt2-3-shared-security-review.md) phân biệt standalone/composed failures.

## Files và provenance

- [StaffProfileConcurrencyTest.java](StaffProfileConcurrencyTest.java):9ca service/transaction/MySQL thật; baseline5failure, proposed9/9PASS. File nằm trong docs, không compile vào Maven DT2-3.
- [staff-version-locking.patch](staff-version-locking.patch):proposal cho StaffModels/Validation/Service/Controller, cần owner Staff nhận/cập nhật caller; không DDL.
- [integration-security-proposal.patch](integration-security-proposal.patch):diff trên candidate đã ghép, không áp mù lên riêng PR; consumer paths, dashboard/ADMIN management order và CSRF.
- [shared-security-regressions.patch](shared-security-regressions.patch):đúng test method HTTP được chạy trong candidate22test. Đóng gói từ source SHA đã kiểm, không tạo runtime/test mutation mới ở nhánh DT2-3.

## Dựng lại đúng candidate

Dùng worktree riêng từ refs ghi trong inputs, giữ develop/Auth/Profile và canonical safety resources; reconcile domain Staff/Customer/News có chủ đích thay vì literal merge các Auth reverts. Giữ config Auth và các consumer/matcher cần ghép; thêm đúng dependencies/config của module. Đây vẫn là selective, unapproved candidate. Áp proposal chỉ trong candidate khi owner đồng ý xem xét, không sửa nhánh member hoặc develop.

Suite root cần nguyên `backend/src/test/resources/schema.sql` tại News HEADff10950 (có audience extension đã nằm trong official DDL). Hai lượt đầu dùng develop fixture thiếu extension là assembly error, không phải baseline News. Native provision tests dùng nguyên migration20261113.1, checksum pinned; không ALTER DB chung hoặc sửa migration. Cấu hình TEST_DB_URL/USERNAME/PASSWORD private cho MySQL schema `_test` trống do mình sở hữu, provision fixture root đúng revision. Rehearsal giữ Auth/test safety config develop; context cache4 hạn chế pool trong combined suite, không đổi shared max_connections.

Copy probe Java vào `backend/src/test/java/vn/codegym/smartcafe/database/StaffProfileConcurrencyTest.java` trong candidate đã có Staff/Profile và TestSchema/DatabaseRehearsal. Probe cần quyền tạo/drop schema test và đọc performance_schema.data_lock_waits/data_locks để xác minh overlap, không đề xuất production app account có quyền này. Test tự provision official native schema riêng và dọn trong finally/AfterAll. Connection thứ ba giữ users row; cả hai transaction được nhìn thấy chờ lock trước khi release.9ca/5vòng không chứng minh mọi writer tương lai đều deadlock-free.

```powershell
# Chỉ trong candidate và với private TEST_DB_* trỏ isolated test DB:
./backend/mvnw.cmd -f backend/pom.xml '-Dtest=StaffProfileConcurrencyTest' test
./backend/mvnw.cmd -f backend/pom.xml '-Dtest=ProfileMySqlTest#adminAndStaffReadOnlyTheirOwnPreciseProfileAndHeaderUsesSameProjection+staffCannotReadOtherEmployeePersonnelThroughManagementNamespace' test
./backend/mvnw.cmd -f backend/pom.xml '-Dspring.test.context.cache.maxSize=4' verify
```

Full cuối396test/2failure/0error/0skip:Profile22/22, concurrency9/9, Customer21/21, News59/59MySQLPASS; hai Staff success request thiếu required version của proposal vẫnFAIL. Đây là caller migration proposal chưa xong, không phải baseline Staff failure; giữ assertions và thêm missing/stale-version cases. Không dùng nhóm xanh để gọi full integrationPASS.

Raw XML/logs, private environment/review record và managed worktree path ở ngoài Git. Các owned schemas/processes đã được dọn trong lượt hardening; checkout vẫn được bảo toàn khi app không cho archive. Chi tiết file-scope/publication ở [final handoff](../../../handover/dt2-3-final-handoff.md).

Hash scope: inputs.proposalFiles giữ SHA raw artifact tại lượt handoff đã kiểm; Git text attributes sẽ normalize CRLF→LF cho ba snapshot cũ. inputs.gitLfProposalFiles pin cùng nội dung theo LF để kiểm repository sau stage/checkout. Không đổi byte của patch/test snapshot đang bảo toàn, không đổi executable test; luôn chọn đúng hash scope, không gọi raw working-copy digest là committed Git blob.

## Rehearsal cập nhật18/11/2026

publicationRefs trong inputs pin develop331c1e3, Staffb74a365, Newsc2343ed, Customer057531c và Bills86b70e2. Candidate giữ actual Staff writer hiện hành (không áp version proposal lịch sử), nhận Auth dashboard rename/ADMIN matchers của Staff, ghép guest/News/Profile/Bills rules có nguồn. Baseline2HTTP+9concurrency có11test/6failure/0error/0skip:Profile401 và5version failures; STAFF-other403 đã đúng. [news-profile-consumer-latest.patch](news-profile-consumer-latest.patch) thêm đúng một resource vào consumer News hiện hữu, chỉ applied trong candidate để chạy full composition; không là production code DT2-3. [Publication report](../../../reports/dt2-3-publication.md) ghi full kết quả thực tế.

Các patch lịch sử được bỏ space-only context markers để staged whitespace/CI đạt; git apply vẫn kiểm thành công trên đúng historical source/candidate, code additions/deletions không đổi. asRehearsedProposalFiles giữ digest gốc; proposalFiles/gitLfProposalFiles mô tả artifacts publish hiện tại. Không dùng historical patch9/9PASS để gọi HEAD Staff mới đã nhận version contract.

## Review follow-up 18/11/2026

Các artifact trên giữ nguyên provenance lịch sử. Proposal dành cho HEAD mới:

- [staff-version-locking-latest.patch](staff-version-locking-latest.patch): chỉ Models/Service/Validation trên Staff `b74a365`; giữ avatar validation và exception mapping hiện hành. Required decimal-string version, không có constructor mặc định version=0. Không cần rename controller nữa vì Staff đã rename dashboard.
- [staff-caller-migration-latest.patch](staff-caller-migration-latest.patch): migrate cả StaffControllerTest/StaffHttpReportTest và concurrency probe. Trong candidate đã có Profile, copy probe từ docs sang đúng backend test path trước khi apply; standalone Staff chỉ nhận phần caller tests của module, probe tích hợp cần dependency Profile. Fixture mới version=0 được gửi tường minh; caller thực tế phải GET detail, lưu version lúc mở form, gửi version đó khi PUT, xử lý 409 bằng reload/confirmation, không tự fetch rồi retry payload cũ.
- [bills-token-fixture-latest.patch](bills-token-fixture-latest.patch): Bills `86b70e2` dùng TokenService và role/auth_version thực từ fixture DB, bỏ signer 4-field. Giữ nguyên toàn bộ business/permission assertions. Đây là MockMvc/MySQL coverage, không gọi là live HTTP.
- [shared-security-composition-latest.patch](shared-security-composition-latest.patch): diff News `c2343ed` → selective shared chain đang rehearsal, gồm filter Profile và union của Profile/Staff/Customer/Bills/News. Chỉ áp sau khi đã có đủ module/dependencies; owner phải đối chiếu từng matcher/filter, không thay wholesale config trên một PR độc lập. Customer SecurityConfig `a6e46a4` không đổi từ `a41152d`; domain/image/auth-scheme tests mới được đưa vào candidate riêng.
- [news-profile-consumer-latest.patch](news-profile-consumer-latest.patch): vẫn dành cho News `c2343ed`, cần owner nhận vào source. Không đủ để tự thay toàn bộ SecurityConfig: giữ guest CSRF/feedback, exact Profile rules, Staff management ADMIN-only và dashboard exception.

Đây là patch để owner review/adopt, không phải code production đã vào các PR phụ thuộc. Kết quả mới và giới hạn nằm ở [review follow-up report](../../../reports/dt2-3-review-follow-up.md). Không thay các lượt FAIL lịch sử bằng kết quả proposal.

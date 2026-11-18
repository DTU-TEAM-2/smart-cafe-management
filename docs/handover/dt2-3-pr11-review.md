# DT2-3 → DT2-10: review proposal cho PR #11

> Current correction: official Staff57ea6f1 has fixed version/CAS/auth_version/callers. Do not apply historical Staffb74 proposals or treat its5/9 failures as current. Current official/proposed candidates, Draft verification timestamp and remaining News/Bills blockers are recorded in [review follow-up](../reports/dt2-3-review-follow-up.md). The dated results below remain historical.


> Follow-up PR #19 ngày18/11/2026: source refs và proposal dành cho HEAD mới được ghi ở [review follow-up](../reports/dt2-3-review-follow-up.md) và [artifact README](../qa/rehearsals/dt2-3/README.md). Các findings/version/line bên dưới là snapshot có ngày; không coi historical revert/bean collision là lỗi của HEAD Staff hiện hành.


> Cập nhật18/11/2026: review chi tiết bên dưới giữ nguyên phạm vi HEAD4c7b969 lịch sử. HEAD mớib74a365 đã khôi phục Auth/verified-db, đổi Auth dashboard controller và có ADMIN management matcher; không còn quy các chênh lệch đó cho HEAD mới. Writer version vẫn chưa có CAS/increment,9ca hiện hành tái hiện5failure. [Kết quả mới](../reports/dt2-3-publication.md).

**Local handoff, chưa đăng GitHub và chưa yêu cầu review.** Task DT2-3/DT2-27 phụ thuộc writer DT2-10/DT2-69. Owner PR #11: `yennhi5033`; reviewer kỹ thuật đề xuất: `NV-DuyManh`, `haitan2408`. Đây không phải approval hoặc yêu cầu tự sửa nhánh của owner.

Source đã kiểm: [PR #11](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/11), HEAD `4c7b9698ecdddd6f27ea093bea8030a90de4edfd`; base/develop `ffa4d9527e7924dd0bc9815fefb3034463d03156`. Metadata được đối chiếu lại ngày 15/11/2026, HEAD/base không đổi. Kết quả dưới đây thuộc đúng revision này và candidate chọn lọc giữ Auth/Profile, không phải một PR đã merge.

## Finding và ranh giới

| Mức / vị trí trên HEAD #11 | Trigger và expected/actual | Tác động / correction đề xuất |
| --- | --- | --- |
| **P1** — `backend/src/main/java/vn/codegym/smartcafe/staff/StaffService.java:291`, `:322`, `:333` | STAFF cập nhật self-profile trong lúc ADMIN gửi full Staff payload từ snapshot cũ. Expected: một writer thành công, writer stale nhận409; giá trị/version của transaction trước không bị ghi đè. Actual: cả hai service hoàn tất, full Staff update ghi đè cùng field hoặc khôi phục address cũ. | Staff đọc trước lock; UPDATE users chỉ WHERE id, không kiểm/tăng version; upsert staff_profiles ghi toàn bộ address/birthday/gender/salary. Transaction annotation có sẵn tại287, nhưng chưa tạo optimistic concurrency giữa hai module. |
| **P1**, cùng writer `:322` | Staff update trước, sau đó PATCH Profile với version0 đã cũ. Expected409; actual200 vì Staff không tăng users.version. | Mọi writer của cùng account phải tham gia version chung; riêng Profile không thể phát hiện writer khác không đổi version. |
| Security transition — `StaffService.java:323`, `:358` | UPDATE có thể đổi role; soft delete chỉ SET status. Source không tăng auth_version. | Patch đề xuất tăng auth_version khi đổi role/deactivate, đồng thời tăng users.version. Kiểm ACTIVE hiện tại đã chặn token khi INACTIVE trong các ca đã chạy; **chưa tái hiện** token sống lại sau reactivation, không ghi thành finding đã chứng minh. |
| **P2, chỉ khi ghép module** — `staff/StaffController.java:10`, develop `auth/StaffController.java:11` | Giữ Auth controller và thêm Staff controller. Expected context khởi động; actual bean-name collision `staffController`. | Đặt tên bean quản trị riêng, ví dụ `@RestController("staffManagementController")`; giữ dashboard Auth. Không phải lỗi startup của Staff standalone khi Auth đã bị revert. |

Các line ở trên luôn trỏ revision nguồn, không phải số line sau patch. [Source writer bất biến](https://github.com/DTU-TEAM-2/smart-cafe-management/blob/4c7b9698ecdddd6f27ea093bea8030a90de4edfd/backend/src/main/java/vn/codegym/smartcafe/staff/StaffService.java#L291).

## Reproduction đã chạy

[StaffProfileConcurrencyTest.java](../qa/rehearsals/dt2-3/StaffProfileConcurrencyTest.java) gọi service proxy và transaction thật trên MySQL8/schema `_test` từ native DDL20261113.1. Một JDBC connection thứ ba giữ users row; khởi động writer1, đợi lock queue; khởi động writer2, đợi cả hai xuất hiện trong `performance_schema.data_lock_waits`, rồi nhả lock. Đây là overlap có kiểm chứng, không phải hai request tuần tự hoặc repository mock. Trạng thái `200/409` ở probe này là kết quả service/exception được chuẩn hóa, không gắn nhãn là concurrency HTTP.

Ví dụ dữ liệu tổng hợp: version0, address `Original address`; Profile đổi address thành `Self address`, Staff full payload cũ còn `Original address`. Baseline cho cả hai thành công và có lịch ghi khôi phục address cũ. Khi hai module sửa name, writer sau cũng ghi đè writer trước. Ca Staff write xong rồi Profile PATCH version0 tái hiện stale acceptance riêng.

| Lượt cùng 9 test | Tests | Failure | Error | Skip | Diễn giải |
| --- | ---: | ---: | ---: | ---: | --- |
| Staff writer hiện hữu, explicit service wiring tránh bean collision | 9 | 5 | 0 | 0 | Lost update/stale-version assertions fail đúng trigger |
| Candidate áp patch đề xuất | 9 | 0 | 0 | 0 | Version/locking proposal đạt trong phạm vi 9 ca |

Chín ca bao phủ self-edit với admin-edit, name/address khác field, cùng field, password với personnel update, deactivate/password theo cả thứ tự, stale version, lock users→staff_profiles lặp5 lần, rollback/release lock và token-state invariants. Không phát hiện Staff khôi phục password hash trong các ca đã chạy; 9 ca không chứng minh mọi writer tương lai đều deadlock-free. Số liệu và provenance tại [integration-results.json](../reports/dt2-3-integration-results.json).

## Patch và hợp đồng cần owner nhận

[staff-version-locking.patch](../qa/rehearsals/dt2-3/staff-version-locking.patch) là proposal, chưa áp vào nhánh DT2-3 hoặc PR #11. Patch sửa StaffModels/StaffValidation/StaffService và tên bean StaffController; không đổi DDL.

1. `StaffModels.java:25`/`:54`: trả version trong StaffDetail và nhận required version trong UpdateStaffRequest. Dùng decimal string signed BIGINT không âm, tối đa `9223372036854775807`; thiếu/null/sai kiểu/âm/vượt range phải400, không dùng default version cho JSON. Overload Java 9-argument trong proposal chỉ giữ test source cũ compile; owner nên chuyển caller Java sang explicit version và bỏ overload khi xong migration.
2. Caller GET detail, giữ version của **đúng snapshot đang sửa**; PUT `/api/v1/staff/{id}` gửi lại version đó cùng field quản trị hợp lệ. Không hardcode0, không lấy version mới rồi tự replay payload cũ. Success nhận detail/version mới;409 reload và cho người dùng quyết định lại.
3. Trong transaction, lock users của account trước, đọc current state/version sau lock, CAS users theo expected version và ACTIVE, tăng version đúng một lần. Sau đó ghi staff_profiles trong cùng transaction; thất bại phải rollback cả hai. Giữ users→staff_profiles cho cả update/deactivate/password; cân nhắc tương tác khóa admin-count khi owner hoàn thiện, không tự mở rộng rule số ADMIN.
4. Role change/deactivation theo auth_version contract hiện hữu; password change/reset tiếp tục dùng Auth writer. Không copy hash cũ từ DTO hoặc reset auth_version bằng snapshot. Consumer phải tiếp tục kiểm ACTIVE/current role/auth_version ở DB.

Payload minh họa **proposal**, không thay API hiện tại của PR #11 trước khi owner chấp thuận:

```json
{
  "fullName": "Synthetic Staff",
  "email": "synthetic_staff@example.test",
  "phone": "0900000002",
  "address": "Synthetic address",
  "gender": null,
  "birthday": null,
  "role": "STAFF",
  "salary": 10000000,
  "avatarMediaId": null,
  "version": "7"
}
```

Migration test/caller còn thiếu: `StaffControllerTest.updateStaffPreservesUsernameAndUpdatesProfile:198` và `StaffHttpReportTest.executeAllStaffApiTestCases:604`, ca TC-17. Sau proposal hai success request chưa gửi version nhận400 thay200. **Đây là caller migration chưa hoàn thiện của proposal, không phải baseline failure của PR #11.** Full candidate396 test/2 failure/0 error/0 skip vẫn FAIL. Owner cần lấy version từ fixture/detail trong success payload; giữ assertion200 và các assertion dữ liệu, thêm riêng missing-version400/no-write, stale409/no-write và refresh version. Với các negative case khác, gửi version hợp lệ để lỗi nghiệp vụ cần kiểm vẫn thực sự được thực thi, không để missing-version che validation/permission test.

## Bước bàn giao tiếp theo

Owner xem patch, chốt contract với Auth/Profile và cập nhật DTO/caller/collection/tests trên chính task Staff. Sau khi sửa, chạy lại Staff success/negative/version cases, 9 concurrency tests và shared-security regression trên candidate mới; nếu source/base đổi mới cân nhắc full verify phù hợp. Không bỏ assertion/skip để báo xanh. Lệnh, cấu hình, quyền đọc lock metadata và fixture đúng revision nằm trong [rehearsal README](../qa/rehearsals/dt2-3/README.md). Việc đăng comment, commit, push hoặc sửa PR cần authorization riêng; lượt này chỉ chuẩn bị handoff local.

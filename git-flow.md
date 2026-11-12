# Git Flow — quy trình chính thức Smart Cafe

Chủ repo yêu cầu áp dụng từ **12/11/2026**, thay thế GitHub Flow một nhánh `main` trước đây. Áp dụng cho mọi thành viên và AI, mọi task tiếp theo. Nguồn: [file chủ repo cung cấp, giữ nguyên để đối chiếu](docs/reference/git-flow-original-2026-11-12.md). Bản này điều chỉnh các ví dụ mobile và điểm chưa thống nhất trong nguồn cho dự án web Spring Boot / ReactJS / MySQL; không mở rộng tính năng ứng dụng.

## 1. Nhánh và đích PR

| Nhánh | Tách từ | Đích PR | Vai trò |
| --- | --- | --- | --- |
| `main` | Mốc release hiện hành | Không phát triển trực tiếp | Code sẵn sàng phát hành / lịch sử release, nhánh mặc định |
| `develop` | Khởi tạo từ `main` | Không phát triển trực tiếp | Tích hợp các task đã review, luôn build được |
| `feature/*` | `develop` mới nhất | `develop` | Một feature, enhancement hoặc task tài liệu/quy trình |
| `bugfix/*` | `develop`, hoặc release đang sửa | `develop`, hoặc đúng `release/*` đó | Sửa lỗi phát triển / ổn định release, không thêm feature |
| `release/*` | `develop` đã chốt scope | `main`, rồi đồng bộ về `develop` | Chỉ sửa bug cuối kỳ, text, version, changelog, cấu hình release |
| `hotfix/*` | `main` / tag production, hoặc release bị lỗi | `main` (production) hoặc `release/*` đang sửa, rồi đồng bộ | Vá khẩn đúng lỗi, không thêm feature |

Mọi thay đổi vào `main`, `develop`, `release/*` đi qua PR. Không commit thẳng hoặc force-push lên nhánh chung; không rewrite lịch sử/đổi tên nhánh của người khác. Không xóa `main`/`develop`. Không tạo release branch cho mỗi feature: feature hoàn tất đi thẳng vào `develop`, chỉ tạo release khi nhóm chốt phiên bản.

## 2. Bắt đầu và kết thúc task

```powershell
git fetch origin --prune
git switch develop
git pull --ff-only origin develop
git switch -c 'feature/cf-07_submit-order'
# Làm đúng task, tự review/AI review, build/test, commit rõ mục đích.
git push -u origin 'feature/cf-07_submit-order'
gh pr create --base develop
```

Nếu chưa có develop local: `git switch --track origin/develop`. Không chạy switch/pull khi còn công việc chưa lưu; tách checkout hoặc giữ an toàn các thay đổi trước. Một task có một nhánh/PR, sửa tiếp trên cùng nhánh khi PR còn mở; không tạo nhánh cho từng chỉnh màu/nhỏ. Push sớm/cuối ngày để backup, không push secret hoặc code ngoài phạm vi.

Tên mới theo mẫu `<type>/<ticket>_<short-description>`, chữ thường, phần mô tả dùng gạch ngang: `feature/cf-07_submit-order`, `bugfix/dt2-1_fix-header`, `release/1.4.0`, `hotfix/1.4.1_fix-bill-total`. Nếu dùng issue GitHub số, mẫu nguồn `feature/#123_add-search-filter` vẫn hợp lệ; quote tên khi gõ shell và URL-encode khi tạo link. Nhánh cũ `feature/dt2-...` được giữ để tránh phá PR, không ép rename.

Commit giữ convention hiện có: `type(scope): CF-xx mô tả cụ thể`. Không dùng `update`, `wip`, `fix bug` chung chung cho bản bàn giao; không gom nhiều task. Không rebase/force-push nhánh dùng chung để dọn lịch sử. Self-review và AI review diff trước commit/PR; không coi AI là reviewer độc lập.

## 3. Review và merge

- PR có task, mục tiêu, trước/sau, checks thực sự đã chạy, ảnh nếu liên quan, tác động/cấu hình và việc thành viên cần làm sau merge. Assignee là người thực hiện, reviewer là người được nhờ review.
- Chạy CI/bot kiểm tra tự động trước vòng duyệt của người review; hiện GitHub Actions kiểm Git Flow, registry, PR title, backend/frontend. Đây không phải bot AI review từng dòng code. Nếu cần một bot code-review riêng, phải ghi rõ công cụ thực tế đã được cấu hình; không giả báo đã có.
- Giữ yêu cầu đã được chủ repo chốt: **ít nhất 2 reviewer độc lập khác tác giả approve** vào mọi nhánh chung. Đây là mức chặt hơn tối thiểu 1 trong file nguồn, không hạ bảo vệ hiện có. Commit mới làm mất hiệu lực approval cũ; reviewer xem lại revision cuối. Không dùng nhiều tài khoản để giả đủ người review.
- CI bắt buộc xanh, nhánh cập nhật với đích, hết blocker và thảo luận được giải quyết. Tác giả trả lời/sửa, người tạo comment xác nhận/resolve; không tự đóng thay.
- Chỉ `NV-DuyManh` hoặc `haitan2408` quyết định merge qua PR, vẫn phải đủ approval/checks. Không tự merge task này, không triển khai chỉ vì CI xanh.
- Có thể squash task vào develop; **dùng merge commit cho release/hotfix và PR đồng bộ main → develop** để giữ quan hệ lịch sử. GitHub chưa khóa merge method theo loại PR, reviewer phải kiểm lựa chọn này.

## 4. Release, hotfix và đồng bộ

1. Khi scope ổn định, tạo `release/<version>` từ develop. Không thêm tính năng mới; sửa qua bugfix PR vào release nếu cần.
2. Review và kiểm thử luồng chính, version/changelog, cấu hình/migration/rollback; mở release PR vào main và lấy đủ approval.
3. Sau merge vào main, mở **PR `main` → `develop`** để đồng bộ release/hotfix, giải quyết xung đột và kiểm CI trước merge. Đây là đường đồng bộ rõ ràng tương đương yêu cầu merge về cả hai nhánh trong nguồn; không đưa feature chưa release theo chiều ngược lại.
4. Với hotfix production, tách từ main hoặc tag chính xác, test đúng lỗi, PR vào main rồi đồng bộ main → develop như trên. Nếu đồng thời có release đang mở cần bản vá, mở PR hotfix vào release đó; giữ nhánh hotfix cho tới khi mọi đích cần thiết đã nhận bản sửa.
5. GitHub có thể xóa release/hotfix head sau merge đầu tiên. Ghi SHA trước merge, dùng main → develop để không mất bước đồng bộ; nếu một release đang mở cần hotfix head đã xóa, phục hồi ref đúng SHA hoặc tạo nhánh chuyển bản vá và mở PR, không force-push.
6. Chỉ tag/deploy khi có quyết định phát hành riêng. Dự án này là web, không có TestFlight/App Store. Tag theo đích web thực tế: `v1.4.0_stg` cho staging, `v1.4.0_prod` cho production; tag phải trỏ revision đã kiểm và có release note. Không tự tạo tag giả để coi task code là đã release.

Task có thể hoàn tất tích hợp ở develop; nghiệm thu/phát hành có revision và tiêu chí riêng. Một branch chỉ xong khi build/test đạt, review hoàn tất, merge đúng đích, có bằng chứng và dọn nhánh theo quy tắc; không ghi đã deploy nếu chỉ merge.

## 5. Thực thi trên GitHub và chuyển đổi

GitHub bảo vệ main/develop, yêu cầu PR + 2 approval + xử lý thảo luận + CI, chặn force-push/xóa. CI **Git Flow** chặn feature vào main, sai loại nhánh/đích; kiểm tra không chứng minh nhánh đã được tạo từ SHA nào hay scope thực tế của diff, reviewer phải đối chiếu. Xem [cấu hình GitHub](docs/engineering/github-settings.md).

Ngày 12/11 develop được bootstrap từ `main@8476ee3`, không nhập công việc local còn dở. PR #7, #10, #11 chuyển đích main → develop, không sửa/rename head của thành viên. PR Git Flow mở vào develop; các PR cũ có thể chờ check mới cho tới khi workflow này được review/merge rồi cập nhật base/chạy lại. Không bỏ bảo vệ hoặc báo CI đã chạy nếu workflow cũ chưa hỗ trợ đích develop.

GitHub tự xóa head sau merge, nhưng develop/main được bảo vệ. Khi PR đồng bộ main → develop, main không bị xóa. Nhánh local không tự mất: chuyển về develop, fetch --prune và chỉ `git branch -d` khi xác nhận đã tích hợp; không xóa cưỡng bức hàng loạt, nhất là sau squash hoặc khi còn PR khác dùng nhánh.

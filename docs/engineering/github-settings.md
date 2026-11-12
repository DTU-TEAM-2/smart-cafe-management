# Thiết lập GitHub — Git Flow

Cập nhật 12/11/2026 theo yêu cầu chủ repo áp dụng [git-flow.md](../../git-flow.md). Tài khoản thực hiện: **NV-DuyManh**. `main` vẫn là default/release; `develop` đã tạo từ `main@8476ee30536fdd01b521cb53e5bb22d38b3b5833`, dùng tích hợp task. Không commit code trực tiếp vào develop trong bước bootstrap này.

## Ruleset đang active

| ID | Phạm vi | Quy tắc |
| --- | --- | --- |
| [26677767](https://github.com/DTU-TEAM-2/smart-cafe-management/rules/26677767) | main, develop | PR, 2 approval, dismiss stale, resolved threads, required checks cập nhật theo đích; chặn xóa và force-push; không có bypass |
| [26677768](https://github.com/DTU-TEAM-2/smart-cafe-management/rules/26677768) | main, develop, release/* | Chỉ NV-DuyManh / haitan2408 được cập nhật qua PR; hai người này vẫn phải đủ mọi điều kiện approval/CI, không bypass ruleset PR |
| [27121663](https://github.com/DTU-TEAM-2/smart-cafe-management/rules/27121663) | release/* | PR, 2 approval, dismiss stale, resolved threads, required checks; chặn force-push, cho tạo nhánh từ revision đã chọn và dọn sau tích hợp |

Năm required check từ GitHub Actions (integration_id 15368): **Governance**, **PR title**, **Backend MySQL**, **Frontend UI**, **Git Flow**. Strict status policy bật. Không tự hạ số approval từ 2 xuống 1 theo file mẫu; không dùng nhiều tài khoản để thay người review độc lập.

## CI và giới hạn

- CI chính chạy PR vào main/develop/release và push lên các nhánh chung này. Job Git Flow kiểm loại nhánh nguồn/đích và main-to-develop sync phải từ cùng repo, bằng `scripts/git-flow.mjs`; các case regression ở `scripts/git-flow.test.mjs`.
- Postman workflow cũng hỗ trợ develop/release; check đó có path filter, không đưa thành required check toàn bộ PR vì có thể không được tạo ở PR tài liệu.
- GitHub không tự chứng minh nhánh đã tách đúng SHA, code chỉ nằm trong scope release/hotfix hoặc merge method có đúng loại. Reviewer kiểm các phần này; dùng merge commit cho promotion và main-to-develop sync, không báo đã có enforcement chưa được cài.
- Bot CI kiểm quy trình/chất lượng tự động, không phải bot AI review nội dung code riêng. Tự review/AI review trước bàn giao; người review duyệt sau checks. Required checks xanh không thay 2 approval.

## Chuyển đổi PR và dọn nhánh

PR **#7, #10, #11** đổi base main → develop; head của từng thành viên và lịch sử commit không bị sửa. PR Git Flow mở vào develop và cần review trước khi workflow trở thành baseline của develop. Trong thời gian này, PR cũ có workflow chỉ nhận main có thể chưa tạo các check develop/Git Flow; **không merge chúng bằng bypass**. Sau khi PR quy trình được duyệt/merge, cập nhật nhánh task từ develop và chạy lại CI.

Repo bật tự xóa head sau merge. main/develop có deletion protection nên không bị dọn khi đồng bộ. Release/hotfix sau khi vào main phải có PR **main → develop**, kể cả head đã tự xóa; ghi revision trước merge để áp dụng vào release khác nếu cần. Chỉ xóa local sau khi xác nhận không còn việc/PR cần giữ. Không tạo tag/deploy trong thay đổi quy trình này.

Các thay đổi rule/branch/đích PR được đọc lại qua GitHub API, không chỉ mô tả trên giấy. PR tài liệu/workflow vẫn chờ hai reviewer và chưa được tự merge.

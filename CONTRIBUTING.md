# Đóng góp và quy trình Git

Đọc [quy tắc nhóm](docs/engineering/team-working-agreement.md), [đặt tên](docs/engineering/naming-conventions.md), [API/dữ liệu](docs/engineering/api-and-data.md), [chất lượng/bảo mật](docs/engineering/quality-and-security.md) và [skill registry](SKILLS_INSTALLED.md) trước task đầu tiên.

## Nhánh và tích hợp

| Nhánh | Tách từ | Đích PR | Vai trò |
| --- | --- | --- | --- |
| `main` | bootstrap | — | Nhánh mặc định, tích hợp lâu dài; luôn build được |
| `feature/cf-07-submit-order` | `main` mới nhất | `main` | Một feature/task |
| `bugfix/cf-11-duplicate-bill` | `main` mới nhất | `main` | Sửa lỗi |
| `chore/cf-01-update-workflow` | `main` mới nhất | `main` | Quy trình, công cụ hoặc tài liệu |
| `hotfix/0.1.1-bill-total` | `main` / tag production | `main` | Vá lỗi production; nếu xuất phát từ tag cũ, tích hợp lại vào `main` |

Nhóm dùng GitHub Flow: một nhánh lâu dài `main` và các nhánh task ngắn hạn. Không duy trì `develop` hoặc nhánh tên thành viên. Không phát triển tính năng trực tiếp trên `main`, không force-push/rewrite lịch sử nhánh chung. Dùng merge commit hoặc squash theo phạm vi và nhu cầu giữ lịch sử. Tag `v0.1.0` chỉ tạo cho release thực tế.

Sau bootstrap, thay đổi đi qua PR vào `main`. Một task/PR đang mở tiếp tục nhận các commit sửa lỗi, đổi màu, bổ sung tài liệu trên cùng nhánh; không tạo nhánh cho mỗi lần chỉnh nhỏ. Sau khi PR đã merge, task mới tách nhánh mới từ `main`, không tái sử dụng nhánh cũ đã merge.

```shell
git switch main
git pull --ff-only origin main
git switch -c feature/cf-07-submit-order
```

## Trước PR

1. Card có owner/reviewer, nguồn yêu cầu, AC và phụ thuộc rõ.
2. Chọn/đọc skill đúng phase, cập nhật quyết định/API nếu có thay đổi.
3. Tự kiểm tra diff, chạy checks phù hợp và rà dữ liệu nhạy cảm.
4. Đặt title như `feat(orders): CF-07 submit an order batch`.
5. Mô tả vấn đề, kết quả, cách kiểm thử, ảnh nếu thay UI, ảnh hưởng dữ liệu/cấu hình và skill đã áp dụng. Link issue/Jira/Trello thực tế; không bịa URL.
6. Nếu liên quan dữ liệu, tuân thủ [rule database nguồn chuẩn](.agents/rules/database-source-of-truth.md): nêu bảng/cột, DDL đã đối chiếu và chênh lệch; không tự bổ sung schema. Thay đổi schema chỉ thực hiện sau quyết định chấp thuận được ghi nhận.

PR tập trung một mục tiêu, ưu tiên review được trong một lượt. Không đặt giới hạn số dòng cứng với lockfile/tài liệu sinh tự động. Nếu cần PR lớn, tách commit/phần mô tả để reviewer hiểu được.

## Review và merge

- Mọi thành viên có thể review và góp ý. Mời thêm một người xem khi cần góc nhìn độc lập.
- Ưu tiên lỗi đúng/sai, quyền, dữ liệu, hồi quy trước stylistic nits. Nêu file/line, tình huống và tác động.
- Tác giả sửa hoặc trả lời từng finding. Người đặt discussion xác nhận/resolve; người khác không tự đóng thay khi chưa thống nhất.
- `NV-DuyManh` hoặc `haitan2408` kiểm tra kết quả và quyết định merge qua PR khi CI xanh, thảo luận đã xử lý, hết blocker và đạt DoD. Nếu phạm vi thay đổi, cập nhật tiêu đề và mô tả PR theo kết quả cuối.
- Giữ attribution khi cherry-pick/merge; dùng `cherry-pick -x` khi lấy commit chọn lọc. Không bịa `Co-authored-by`.

CI chạy **Governance** (registry, SHA-256, định tuyến, whitespace), **PR title**, **Backend MySQL** (Maven verify trên schema test riêng) và **Frontend UI** (lint, build, Playwright desktop/mobile). Hai job ứng dụng được bổ sung trong DT2-1; các required check trên GitHub hiện vẫn theo [thiết lập GitHub](docs/engineering/github-settings.md). CI xanh không thay thế nghiệm thu và review chéo.

## Dọn nhánh sau khi merge

GitHub đã bật **Automatically delete head branches**: nhánh task được xóa trên remote sau khi PR merge. Lịch sử commit đã tích hợp và PR vẫn được giữ. Tính năng này không tự merge PR, không xóa `main` và không tự dọn nhánh local của các thành viên.

Trên máy mình, sau khi commit/stash công việc còn dang dở:

```shell
git switch main
git pull --ff-only origin main
git fetch origin --prune
git branch -d feature/cf-07-submit-order
```

`fetch --prune` chỉ dọn remote-tracking ref đã mất trên server, không xóa nhánh local. Nếu `branch -d` từ chối (ví dụ PR được squash), kiểm tra PR và commit riêng trước; không dùng xóa cưỡng bức hàng loạt. Khi dọn nhánh cũ, xác minh PR đã merge, không có PR khác tham chiếu nhánh và không còn commit cần giữ. Nhánh có PR đang mở giữ nguyên đến khi review/merge hoặc hủy task được xác nhận.

## Release và khôi phục

Release phải có revision, phạm vi, checks, notes, cấu hình/migration, smoke và rollback. Deploy theo môi trường đã được giao quyền; không coi việc merge là đã deploy. Chi tiết ở [release workflow](.agents/workflows/release.md).

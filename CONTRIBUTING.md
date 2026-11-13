# Đóng góp và quy trình Git

**Bắt buộc theo [Git Flow](git-flow.md) từ 12/11/2026.** Quy tắc này thay GitHub Flow một nhánh main trước đây và áp dụng cho toàn nhóm/AI.

## Bắt đầu task

1. Đọc [quy tắc nhóm](docs/engineering/team-working-agreement.md), [đặt tên](docs/engineering/naming-conventions.md), [API/dữ liệu](docs/engineering/api-and-data.md), [chất lượng](docs/engineering/quality-and-security.md) và [skill registry](SKILLS_INSTALLED.md).
2. Có task, owner, AC, nguồn yêu cầu và phụ thuộc rõ; chọn/đọc skill đúng phase.
3. Tách `feature/*` hoặc `bugfix/*` từ develop mới nhất; một task/PR giữ cùng nhánh tới khi merge. Không phát triển trực tiếp trên main/develop.

```powershell
git fetch origin --prune
git switch develop
git pull --ff-only origin develop
git switch -c 'feature/cf-07_submit-order'
```

Nếu chưa có develop local dùng `git switch --track origin/develop`. Giữ an toàn công việc chưa commit trước khi chuyển nhánh.

## Đích PR

| Nhánh nguồn | Nhánh đích |
| --- | --- |
| feature/*, bugfix/* của task phát triển | develop |
| release/* | main, sau đó PR main → develop |
| hotfix/* production | main, sau đó PR main → develop |
| bugfix/* hoặc hotfix/* ổn định release | release/* tương ứng |
| main, chỉ đồng bộ release/hotfix đã tích hợp | develop |

Không tạo release cho mỗi feature. Chỉ tạo release từ develop khi nhóm chốt scope/version. Quy trình tag, release/hotfix và dọn nhánh có tại [git-flow.md](git-flow.md); không tự deploy/tag từ yêu cầu làm code.

## Trước PR và trước merge

- Tự đọc diff, dùng AI review theo rule, dọn code/debug thừa và chạy kiểm chứng thích hợp. AI review không thay người review độc lập.
- PR có title `type(scope): CF-xx mô tả`, link task thật, mục tiêu/kết quả, tests/ảnh, tác động/rủi ro và việc nhóm cần làm sau merge; assignee đúng người làm, reviewer đúng người cần xem.
- Nhờ người review duyệt sau khi CI/bot checks đạt. Hiện GitHub Actions có Git Flow, Governance, PR title, Backend MySQL, Frontend UI; không giả báo đã có bot AI review code riêng.
- Mọi PR vào nhánh chung cần **2 reviewer khác tác giả approve**, không dùng nhiều tài khoản của một người để đủ số lượng. Commit mới yêu cầu review lại và hủy approval cũ.
- Nếu đổi file CI/validator/policy được CODEOWNERS bảo vệ, một trong hai reviewer phải là NV-DuyManh hoặc haitan2408, khác tác giả. CODEOWNERS lấy từ base; PR bootstrap thêm file này cần owner review thủ công trước merge, không giả có enforcement khi file chưa vào base.
- Hết blocker, xử lý thảo luận; ai comment xác nhận/resolve comment đó. Chỉ NV-DuyManh hoặc haitan2408 quyết định merge qua PR khi đạt điều kiện, không bypass số approval/CI.
- Task có thể squash vào develop. Release/hotfix và đồng bộ main → develop dùng merge commit để giữ ancestry; không rebase/force-push nhánh chung hoặc rewrite lịch sử của người khác.
- Giữ attribution; cherry-pick chọn lọc có `-x` và ghi nguồn. Không bịa Co-authored-by.
- Tuân thủ [rule công nghệ](.agents/rules/technology-constraints.md): Spring Boot / ReactJS / MySQL bắt buộc, công cụ khác tùy chọn theo task.
- Dữ liệu tuân thủ [rule database](.agents/rules/database-source-of-truth.md), nêu bảng/cột/DDL đối chiếu; schema thiếu hoặc lệch phải được chốt trước, không tự chế.

## CI và dọn nhánh

Workflow hỗ trợ PR vào main/develop/release, push main/develop/release và kiểm nhánh nguồn/đích qua `scripts/git-flow.mjs`. Required checks và quyền GitHub xem tại [thiết lập GitHub](docs/engineering/github-settings.md). CI xanh không thay review/AC hoặc chứng minh nguồn tách nhánh và phạm vi nghiệp vụ.

GitHub tự xóa head sau merge; main/develop được bảo vệ và không xóa. Khi cần nhiều đích release/hotfix, ghi SHA và hoàn tất đồng bộ theo git-flow.md. Sau merge task: chuyển về develop, pull --ff-only, fetch --prune; xác minh PR/commit rồi mới `git branch -d <task>`. Squash có thể khiến `-d` từ chối: không xóa cưỡng bức khi chưa kiểm chứng. Nhánh local/PR có công việc chưa tích hợp phải giữ nguyên.

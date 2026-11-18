# Git Flow Rule

Tài liệu này mô tả quy ước và làm rõ **phương châm vận hành branch và chiến lược review** để mọi người có thể làm việc với quy trình và rule thống nhất, dễ review, dễ release và tránh ghi đè lẫn nhau. Rule được xây theo tinh thần [Atlassian Gitflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow) theo quy tắc đơn giản hóa:

- `main` là nhánh chính thức để ghi lại lịch sử release
- `develop` là nhánh tích hợp cho các thay đổi đang phát triển
- `feature/*` dùng cho từng task/issue riêng
- `release/*` dùng để chốt phiên bản
- `bugfix/*` dùng để sửa lỗi phát hiện được trong khi phát triển.
- `hotfix/*` dùng để vá lỗi production khẩn cấp (đã release)

Mỗi version triển khai lên Testflight/AppStore cần được đánh tag version.
- `v0.1.1_stg` hậu tố stg để xác định môi trường dàn dựng cho Testflight
- `v1.0.0_prod` hậu tố prod để xác định release cho môi trường AppStore

## 1. Mục tiêu

- Mỗi thay đổi phải có phạm vi rõ ràng, dễ review và dễ rollback.
- Không commit trực tiếp vào `main`.
- Không phát triển feature mới trực tiếp trên `develop` nếu task đó có thể tách thành nhánh riêng.
- Không để branch sống quá lâu nếu không có lý do rõ ràng.
- Mọi thay đổi lên nhánh chung phải đi qua Pull Request.
- Mỗi commit phải có nội dung rõ ràng, xác định được nội dung liên quan.

## 2. Vai trò từng nhánh

### `main`

- Chỉ chứa code đã sẵn sàng release hoặc đã release.
- Không ai được commit trực tiếp lên `main`.
- Mỗi lần merge vào `main` phải đi kèm version tag hoặc release note nếu team đang dùng release versioning.

### `develop`

- Là nhánh tích hợp trung gian cho toàn bộ feature đã hoàn thành.
- Chỉ nhận merge từ `feature/*`, `release/*` và `hotfix/*`.
- `develop` phải luôn ở trạng thái có thể build được.

### `feature/*`

- Mỗi task, bugfix lớn hoặc enhancement phải nằm trong một feature branch riêng.
- Branch phải tách từ `develop`.
- Chỉ chứa đúng phạm vi của task đang làm.
- Xong việc thì merge vào `develop` qua PR.

### `release/*`

- Tách từ `develop` khi team quyết định chốt phiên bản.
- Trong release branch chỉ được sửa:
  - bug cuối kỳ
  - format, text, metadata
  - version number, changelog, build setting cần thiết cho release
- Không thêm feature mới vào release branch.
- Xong release thì merge vào cả `main` và `develop`, sau đó tag version.

### `bugfix/*`

- Tách từ `develop` để sửa các lỗi phát hiện được trong quá trình phát triển.
- Chỉ sửa cho đúng phạm vi của bugs, ko thêm các tính năng mới.
- Khi xong phải merge vào `develop` qua PR.

### `hotfix/*`

- Tách từ `main` hoặc `release\*` hoặc `tag` khi production gặp lỗi khẩn cấp.
- Chỉ dùng để sửa bug nghiêm trọng, không dùng cho feature.
- Khi xong phải merge vào cả `main` và `develop` để tránh mất bản sửa.

## 3. Quy tắc đặt tên branch

Đặt tên ngắn gọn, rõ ràng, có ý nghĩa, viết thường, dùng dấu gạch ngang.
Các nội dung phân tách thì dùng dấu gạch dưới.
Không đặt tên bằng tiếng việt có dấu hay có các ký tự lạ.

Khuyến nghị:

- `feature/#<issue>_<short-description>`
- `bugfix/#<issue>_<short-description>`
- `release/<version>`
- `hotfix/<version>_<short-description>`

Ví dụ:

- `feature/#123_add-search-filter`
- `feature/#145_fix-player-ui`
- `release/1.4.0`
- `hotfix/1.4.1_crash-on-start`

Quy tắc:

- Không dùng tên mơ hồ như `temp`, `test`, `new-branch`.
- Không đặt tên branch quá dài hoặc khó đọc.
- Nên gắn issue ID nếu team có tracker.

## 4. Quy tắc commit

Mỗi commit phải là một thay đổi nhỏ, rõ mục đích.

Khuyến nghị format:

- `feat: ...`
- `fix: ...`
- `refactor: ...`
- `docs: ...`
- `test: ...`
- `chore: ...`

Ví dụ:

- `feat: add ringtone preview loading state`
- `fix: prevent crash when playlist is empty`
- `refactor: move export logic into service`

Rule commit:

- Trước khi commit phải review code kỹ, tự đọc lại diff như một reviewer.
- Sử dụng AI để review lại toàn bộ code đã thay đổi.
- Xóa toàn bộ code thừa, code dead, comment lỗi thời, log debug không cần thiết.
- Code phải tuân thủ convention của dự án và tiêu chuẩn chung của ngành.
- Không commit code chỉ để "chạy được tạm"; ưu tiên code sạch, rõ và dễ bảo trì.
- Không commit kiểu `wip`, `update`, `fix bug` chung chung nếu có thể mô tả rõ hơn.
- Không gom quá nhiều task khác nhau vào một commit.
- Nếu đang làm dở, có thể dùng commit trung gian nhưng phải dọn lại trước khi merge nếu team yêu cầu squash.

## 5. Quy tắc pull request

### Mọi thay đổi lên branch chung phải qua PR.

- Title cần ngắn gọn dễ hiểu (Nếu có ticket thì thêm ID của ticket)
- Mô tả của PR cần có:
  - Có đề cập đến issue của task đang làm
  - Mục tiêu thay đổi
  - Ảnh chụp bằng chứng liên quan đến vấn đề
  - Ghi chú rủi ro/ảnh hưởng nếu có
  - Các thay đổi mà người khác cần thực hiện sau khi merge.
- Assignee: Gán đúng người thực hiện task.
- Reviewer: Gán những người sẽ target/chịu trách nhiệm review

### Quy tắc review:

- PR phải được review qua bot theo rule của team trước khi người review người thật xem tiếp hoặc approve.
- Ít nhất 1 người review trước khi merge.
- PR phải nhỏ nhất có thể, ưu tiên một chủ đề chính.
- Không tự merge PR của mình nếu team đang áp dụng rule review bắt buộc.
- Tất cả comment của reviewer và bot phải được xử lý đầy đủ trước khi merge.
- Chỉ merge khi không còn comment blocker, hoặc comment đã được phản hồi và resolved rõ ràng.
- Không tự ý `resolve` các bình luận của người khác, nguyên tắc ai comment thì người đó sẽ phải đóng.
- Nếu người review không còn có comment thì tích chọn "Approve" trên Pull Request.

### Quy tắc merge:

- Không còn các comment nào chưa được xử lý.
- Đã tích xanh (Đã được "Approve" từ người review).
- CI/CD không còn có bất kỳ lỗi nào (nếu team áp dụng CI/CD).
- Ưu tiên `squash merge` cho feature branch nếu team muốn lịch sử sạch.
- Dùng `merge commit` nếu team muốn giữ đầy đủ history branch.
- Không rebase rồi force push vào branch chung nếu chưa được team thống nhất.

## 6. Quy trình làm việc chuẩn

### Khi bắt đầu task mới

1. Pull `develop` mới nhất.
2. Tạo branch mới từ `develop`.
3. Làm đúng phạm vi task.
4. Commit theo từng bước nhỏ và rõ ràng.
5. Push branch lên remote sớm để backup (cuối ngày).

### Khi xong feature

1. Pull `develop` mới nhất và tạo branch `release\*` từ theo quy tắc của dự án.
2. Lấy các thay đổi của feature từ branch trước đó (Optional nếu khác branch)
3. Tự kiểm tra local build/run và lấy evidence cho pull request.
4. Self review lại các thay đổi và tạo PR vào `develop`.
5. Fix comment review.

### Khi chuẩn bị release

1. Chỉ tạo `release/*` khi scope đã ổn định.
2. Chỉ sửa bug, nội dung, version, build metadata.
3. Test lại toàn bộ luồng chính.
4. Merge release vào `main` và `develop`.
5. Tag version theo chuẩn của team.

### Khi có hotfix production

1. Tạo `hotfix/*` từ `main`.
2. Sửa đúng bug production.
3. Test tối thiểu để xác nhận fix.
4. Merge vào `main` và `develop`.
5. Tag version hotfix.

## 7. Điều cấm

- Không commit thẳng vào `main`.
- Không mở feature branch từ nhánh sai.
- Không đưa feature mới vào `release/*`.
- Không bỏ qua review nếu PR là rule bắt buộc.
- Không `force push` lên branch chung nếu chưa có sự đồng thuận.
- Không rename branch hoặc rewrite history của người khác.
- Không merge một PR khi chưa build/test tối thiểu.

## 8. Definition of Done cho branch

Một branch chỉ được coi là xong khi:

- code đã build được
- luồng chính đã được test
- không còn console error rõ ràng do thay đổi mới
- PR đã được review
- branch đã merge đúng đích
- branch đã được xóa sau merge nếu team thống nhất dọn nhánh

## 9. Checklist ngắn trước khi merge

- Branch đúng loại chưa?
- Commit message có rõ chưa?
- PR có mô tả đủ chưa?
- Đã test local chưa?
- Có ảnh hưởng branch khác không?
- Có cần update version, note, hoặc changelog không?
- Merge target đã đúng là `develop`, `main`, hay `release/*` chưa?

## 10. Nguyên tắc quan trọng nhất

- Feature phát triển trên `feature/*`.
- Release chốt trên `release/*`.
- Hotfix sửa khẩn trên `hotfix/*`.
- `main` chỉ nhận code sạch, đã sẵn sàng phát hành.
- `develop` là nơi tích hợp trung gian, không phải nơi làm việc tùy tiện.

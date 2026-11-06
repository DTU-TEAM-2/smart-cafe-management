# Thiết lập repo mới

- Main chỉ có commit khởi tạo rỗng trước PR nền tảng; không dùng develop.
- Bộ rule/skill và CI governance được đề xuất bằng PR; không tự merge.
- Visibility và quyền truy cập phải được owner xác nhận và kiểm tra thực tế trước khi publish. Private không tự loại trừ quyền đọc kế thừa từ tổ chức.
- CODEOWNERS không tự cấp quyền hoặc bật branch protection. Chỉ ghi reviewer đã được xác minh.
- Đề xuất bảo vệ main: chỉ qua PR, chặn force-push/xóa main; checks Governance và PR title theo tên CI thực tế. Không ghi là đã bật nếu chưa kiểm tra trên GitHub.
- Bật tự xóa nhánh sau merge; không bật auto-merge mặc định.
- Chưa có backend/frontend, vì vậy không cấu hình required checks không tồn tại. Bổ sung khi đưa application vào repo.
- Hướng dẫn này không chứa lệnh tự cấp quyền hoặc sửa quyền toàn tổ chức.

## Mentor review — 06/11/2026

- Mentor `haitan2408` được owner chỉ định review code và đánh giá PR trước khi merge; đã được cấp Write trên riêng repo mới. Không cấp Maintain/Admin.
- Write cho phép mentor Approve/Request changes có thể được tính trong quy trình required reviews, đồng thời có quyền push/merge; đây không phải role chỉ đọc để review.
- PR nền tảng #1 được yêu cầu mentor review bằng reviewer request trực tiếp. Main vẫn rỗng và PR chưa được merge.
- CODEOWNERS trong PR này đề xuất mentor làm người review mặc định. File chỉ có mặt trên nhánh PR, chưa có trên main.
- GitHub API hiện từ chối rulesets của repo private với HTTP 403 và thông báo cần nâng gói hoặc dùng repo public. Vì vậy chưa bật được khóa kỹ thuật bắt buộc mentor duyệt trước merge; không khẳng định CODEOWNERS tự thực thi việc đó ở gói hiện tại.
- Trước mắt nhóm tuân thủ quy trình: mentor Approve sau khi CI đạt và discussions đã xử lý, rồi người có quyền mới merge. Không tự merge PR đang chờ mentor; không đổi visibility hoặc nâng gói khi chưa có yêu cầu của owner.

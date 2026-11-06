# Thiết lập repo mới

- Main chỉ có commit khởi tạo rỗng trước PR nền tảng; không dùng develop.
- Bộ rule/skill và CI governance được đề xuất bằng PR; không tự merge.
- Visibility và quyền truy cập phải được owner xác nhận và kiểm tra thực tế trước khi publish. Private không tự loại trừ quyền đọc kế thừa từ tổ chức.
- CODEOWNERS không tự cấp quyền hoặc bật branch protection. Chỉ ghi reviewer đã được xác minh.
- Đề xuất bảo vệ main: chỉ qua PR, chặn force-push/xóa main; checks Governance và PR title theo tên CI thực tế. Không ghi là đã bật nếu chưa kiểm tra trên GitHub.
- Bật tự xóa nhánh sau merge; không bật auto-merge mặc định.
- Chưa có backend/frontend, vì vậy không cấu hình required checks không tồn tại. Bổ sung khi đưa application vào repo.
- Hướng dẫn này không chứa lệnh tự cấp quyền hoặc sửa quyền toàn tổ chức.

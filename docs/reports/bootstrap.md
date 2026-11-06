# Chuẩn bị nền tảng — 06/11/2026

Yêu cầu: tạo lại repo cùng tên, main chưa có nội dung; đưa rule/skill vào PR trước khi merge. Repo cũ được giữ lại, có thể đổi tên để tránh trùng.

Nội dung kế thừa: 17 skill, 16 routes, các luật nhóm, naming/Git/quality, workflow và mẫu bàn giao. License/attribution và manifest nguồn được giữ nguyên. Không nhập code ứng dụng, migration, credential hoặc các báo cáo nghiệm thu cũ.

Repo cũ được đổi tên thành `DTU-TEAM-2/smart-cafe-management-legacy-20261106`, giữ nguyên lịch sử và dữ liệu GitHub. Repo mới private mang tên `DTU-TEAM-2/smart-cafe-management`; quyền đọc mặc định của tổ chức được giữ nguyên theo xác nhận của owner, nên thành viên tổ chức có thể xem repo mới. Không loại tài khoản hoặc thay quyền toàn tổ chức.

Main mới chỉ có commit khởi tạo rỗng. Nhánh `chore/cf-01-team-rules-and-skills` chứa toàn bộ đề xuất nền tảng, mở PR vào main và để chờ review, không merge. Trên máy, checkout mới ở `F:/CODEGYM/smart-cafe-management-next`; thư mục dự án cũ và các thay đổi chưa commit được giữ nguyên, chỉ đổi origin của repo cũ sang URL lưu trữ để tránh push nhầm.

Kiểm chứng local: registry 17 skills/16 routes đạt; 4/4 routing tests đạt; YAML CI đọc được với 2 jobs; 181 file registry/rule/skill khớp byte-for-byte với nguồn local. Main có 0 file. Whitespace nội dung tự viết đạt sau khi loại snapshot skill kế thừa khỏi phép kiểm tra. Không chạy hoặc tuyên bố backend/frontend tests vì repo mới chưa có application.

Một số file skill kế thừa chứa trailing whitespace từ nguồn. Giữ nguyên snapshot/license; kiểm tra whitespace của nội dung tự viết bằng pathspec loại thư mục skill kế thừa, không làm sạch hàng loạt hoặc sửa manifest để che thay đổi.

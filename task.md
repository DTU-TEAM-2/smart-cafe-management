# Task: DT2-10 Quản lý danh sách, thêm, sửa và xóa nhân viên

## Goal
Xây dựng đầy đủ tính năng Quản lý nhân viên (CRUD, tìm kiếm đa điều kiện, phân trang, xác thực quyền Admin, upload ảnh đại diện qua Cloudinary) cho hệ thống Smart Cafe, tuân thủ SRS v4.1 (mục 6.1.4, 6.1.5, 6.1.6, Other/Xóa nhân viên), Tasks_Sprint_1 và mô hình cơ sở dữ liệu hệ thống chuẩn ngày 11/11/2026.

## Context
- Người phụ trách: Nguyễn Thị Yến Nhi (Jira issue: DT2-10, thuộc Sprint 1).
- Đối chiếu tài liệu:
  - STT 10 trong Tasks_Sprint_1 — Project Plan.
  - SRS v4.1 mục 6.1.4 (Màn hình quản lý danh sách nhân viên), 6.1.5 (Màn hình thêm mới nhân viên), Other (Xóa nhân viên), 6.1.6 (Màn hình sửa thông tin nhân viên).
  - Sơ đồ cơ sở dữ liệu chuẩn hệ thống ngày 11/11/2026: bảng `users`, `staff_profiles`, `media_assets`.
- Các subtask liên quan:
  - DT2-67: [Prototype] Quản lý danh sách, thêm, sửa và xóa nhân viên
  - DT2-68: [Database] Quản lý danh sách, thêm, sửa và xóa nhân viên
  - DT2-69: [Coding BE (Spring Boot)] Quản lý danh sách, thêm, sửa và xóa nhân viên
  - DT2-70: [Coding FE (ReactJS)] Quản lý danh sách, thêm, sửa và xóa nhân viên
  - DT2-71: [Review & Quality] Quản lý danh sách, thêm, sửa và xóa nhân viên
  - DT2-72: [Fix Bug] Quản lý danh sách, thêm, sửa và xóa nhân viên

## Scope
### Included:
1. **Backend (Spring Boot):**
   - Phân quyền: Chỉ cho phép role `ADMIN` truy cập các endpoint quản lý nhân viên. Role `STAFF` hoặc người dùng chưa xác thực bị từ chối với mã 403 Forbidden.
   - Danh sách & Phân trang: Lấy danh sách nhân viên phân trang (mặc định 10 bản ghi/trang), hỗ trợ sắp xếp theo `username` (mặc định tăng dần) hoặc các trường cho phép.
   - Tìm kiếm: Tìm kiếm đa điều kiện kết hợp toán tử `OR` trên 3 trường: `username`, `name` (full_name), `phone` (tel). Nếu không nhập điều kiện tìm kiếm, trả về toàn bộ danh sách sắp xếp theo `username`.
   - Thêm nhân viên:
     - Các trường bắt buộc: `username`, `full_name`, `address`, `phone`, `salary`.
     - `role`: Cho phép chọn `STAFF` hoặc `ADMIN`, mặc định là `STAFF`.
     - `birthday`, `gender` (MALE/FEMALE): không bắt buộc.
     - Upload ảnh đại diện qua Cloudinary, lưu thông tin vào `media_assets` và gán `avatar_media_id` cho user.
     - Validation:
       - `username`: dài hơn 6 ký tự, không bắt đầu bằng số, không trùng trong hệ thống, không được đặt là `admin` hoặc `root`.
       - `salary`: lớn hơn 0 và là bội số của 100.000 VNĐ.
     - Tạo mật khẩu ban đầu an toàn (BCrypt hash) và thiết lập cờ `must_change_password = 1`.
   - Sửa thông tin nhân viên:
     - Nạp chi tiết nhân viên theo ID.
     - `username`: Chỉ đọc (read-only, bất biến, không được phép sửa).
     - Áp dụng các quy tắc validation tương tự khi thêm mới cho các trường được cập nhật.
     - Hỗ trợ đổi/cập nhật ảnh đại diện mới qua Cloudinary.
   - Xóa nhân viên:
     - Kiểm tra an toàn trước khi xóa (không cho phép tự xóa chính tài khoản Admin đang đăng nhập).
     - Xử lý ràng buộc toàn vẹn: nếu nhân viên đã từng tham gia lập bill / thao tác phiên bàn trong lịch sử, áp dụng cơ chế soft delete (chuyển `status = 'INACTIVE'`) để bảo toàn lịch sử dữ liệu tài chính theo quy định hệ thống.
2. **Frontend (ReactJS):**
   - Giao diện danh sách nhân viên: hiển thị bảng thông tin (Username, Name, Address, Tel, Gender, Role, Birthday, Salary, Avatar), phân trang 10 dòng/trang, ô tìm kiếm OR (Username, Name, Tel).
   - Hiển thị trạng thái rỗng ("Not found") khi tìm kiếm không có kết quả.
   - Màn hình/Form thêm mới nhân viên có đầy đủ validate trực quan phía client (kết hợp báo lỗi từ server), upload ảnh preview qua Cloudinary.
   - Màn hình/Form sửa thông tin: username disabled/read-only, nạp dữ liệu cũ, validate cập nhật, nút Update và Back.
   - Hộp thoại (Modal) xác nhận xóa nhân viên: hiển thị cảnh báo xác nhận, nút xác nhận và nút hủy (hủy thì giữ nguyên trạng thái danh sách).
3. **Kiểm thử (Verification & Quality):**
   - Unit/Integration test Backend với Spring Boot Test và schema MySQL `*_test`.
   - Kiểm tra các ca kiểm thử nghiệp vụ: quyền Admin, tìm kiếm OR, validate username (>6 ký tự, không đầu số, cấm admin/root), validate salary, username bất biến khi sửa, xóa an toàn và upload ảnh Cloudinary.

### Excluded:
- Không can thiệp vào module Authentication/Login đã được chốt (không tự sinh fake user token hay thay đổi contract bearer đã có).
- Không tự thay đổi cấu trúc bảng cơ sở dữ liệu hệ thống `smart_cafe` ngoài sơ đồ ngày 11/11/2026.

## Acceptance criteria
- [x] AC-1: Chỉ người dùng có role `ADMIN` mới truy cập được các API và màn hình quản lý nhân viên. Role `STAFF` hoặc không có token bị từ chối 403.
- [x] AC-2: Danh sách nhân viên hiển thị đầy đủ các trường (Username, Name, Address, Tel, Gender, Level/Role, Birthday, Salary, Avatar) và phân trang đúng 10 bản ghi/trang.
- [x] AC-3: Tìm kiếm hỗ trợ kết hợp đa điều kiện (Username, Name, Tel) bằng toán tử `OR`. Khi không nhập từ khóa, hiển thị toàn bộ danh sách sắp xếp theo `username`. Khi không có kết quả, hiển thị thông báo "Not found".
- [x] AC-4: Thêm mới nhân viên: bắt buộc Username, Name, Address, Tel, Salary; Role mặc định Staff. Validate Username (>6 ký tự, không bắt đầu bằng số, không trùng, không là admin/root) và Salary (>0, bội số của 100.000 VNĐ). Nếu không hợp lệ trả về lỗi và hiển thị rõ ràng trên form.
- [x] AC-5: Sửa nhân viên: nạp đúng dữ liệu nhân viên đã chọn; Username ở chế độ chỉ đọc (read-only); các trường khác áp dụng cùng validation; hỗ trợ nút Update và Back quay lại danh sách.
- [x] AC-6: Xóa nhân viên yêu cầu hộp thoại xác nhận trước khi xóa; hủy xác nhận giữ nguyên danh sách; không cho Admin tự xóa chính mình; bảo toàn toàn vẹn dữ liệu cho nhân viên đã có liên kết lịch sử.
- [x] AC-7: Upload hình nhân viên qua Cloudinary thành công, lưu thông tin vào `media_assets` và hiển thị ảnh đại diện chính xác sau khi lưu.

## Constraints and open questions
- **Cơ sở dữ liệu:** Tái sử dụng bảng `users`, `staff_profiles`, `media_assets` theo sơ đồ ngày 11/11/2026. Bảng `staff_profiles` lưu `address`, `birthday`, `gender`, `salary`, liên kết 1-1 với `users` qua `user_id`.
- **Mật khẩu khởi tạo:** Khi tạo nhân viên mới, mật khẩu mặc định được tạo an toàn (ví dụ mật khẩu mặc định theo quy định dự án hoặc ngẫu nhiên có hash BCrypt), kích hoạt cờ `must_change_password = 1`.
- **Xóa nhân viên có lịch sử:** Khi nhân viên đã có bản ghi liên kết trong `bills` (`created_by_user_id`), không thể xóa cứng (hard delete) do ràng buộc foreign key; giải pháp: chuyển trạng thái `users.status = 'INACTIVE'` (soft delete).
- **Rủi ro đồng thời:** Xử lý race condition khi trùng username cùng lúc (bắt lỗi duplicate key `uq_users_username` tại database level).

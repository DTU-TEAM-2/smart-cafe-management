# Implementation Plan: DT2-10 Quản lý danh sách, thêm, sửa và xóa nhân viên

## Approach
Xây dựng tính năng Quản lý nhân viên theo chuẩn kiến trúc Smart Cafe:
- **Backend**: Cung cấp bộ REST API tại `/api/v1/staff` tuân thủ quy tắc bảo mật (Spring Security kiểm tra role `ADMIN`), kiểm tra dữ liệu đầu vào chặt chẽ (Validation Boundary) và quản lý giao dịch `@Transactional` giữa hai bảng `users` và `staff_profiles`, liên kết ảnh `media_assets` qua Cloudinary.
- **Frontend**: Xây dựng module giao diện ReactJS tại `frontend/src/staff/` bao gồm danh sách có phân trang 10 bản ghi/trang, tìm kiếm đa tiêu chí OR, màn hình thêm/sửa nhân viên với validation tức thời, upload ảnh đại diện và hộp thoại xác nhận xóa bảo vệ dữ liệu.
- **Kiểm thử**: Viết kiểm thử tích hợp Spring Boot xác minh toàn diện các tiêu chí AC (quyền Admin, phân trang, tìm kiếm OR, ràng buộc username/salary, bất biến username khi sửa, xóa an toàn).

---

## Files and changes
| File/module | Thay đổi dự kiến | AC liên quan |
|---|---|---|
| `backend/src/main/java/vn/codegym/smartcafe/config/SecurityConfig.java` | Cấu hình cấp quyền: chỉ `ADMIN` truy cập `/api/v1/staff/**` | AC-1 |
| `backend/src/main/java/vn/codegym/smartcafe/staff/StaffModels.java` | DTOs (Request/Response): StaffSummary, StaffDetail, CreateStaffRequest, UpdateStaffRequest, PageResponse | AC-2, AC-4, AC-5 |
| `backend/src/main/java/vn/codegym/smartcafe/staff/StaffValidation.java` | Validation nghiệp vụ: username >6 ký tự, không bắt đầu bằng số, cấm admin/root, salary >0 bội số 100.000 | AC-4, AC-5 |
| `backend/src/main/java/vn/codegym/smartcafe/staff/StaffService.java` | Xử lý logic nghiệp vụ, giao dịch database (`users`, `staff_profiles`), phân trang, tìm kiếm OR, xóa an toàn | AC-2, AC-3, AC-4, AC-5, AC-6 |
| `backend/src/main/java/vn/codegym/smartcafe/staff/StaffController.java` | REST Controller cung cấp các endpoint CRUD, tìm kiếm, upload avatar | AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7 |
| `backend/src/test/java/vn/codegym/smartcafe/staff/StaffControllerTest.java` | Integration test kiểm chứng các luồng hợp lệ, từ chối quyền, validation lỗi, tìm kiếm OR | AC-1 -> AC-7 |
| `frontend/src/staff/staffApi.js` | Hàm gọi API backend (get list, get detail, create, update, delete, upload avatar) | AC-1 -> AC-7 |
| `frontend/src/staff/StaffListPage.jsx` | Màn hình hiển thị danh sách nhân viên, phân trang 10, thanh tìm kiếm OR, nút Thêm/Sửa/Xóa | AC-2, AC-3, AC-6 |
| `frontend/src/staff/StaffFormModal.jsx` | Modal/Form thêm mới và chỉnh sửa nhân viên, validate client, upload ảnh Cloudinary | AC-4, AC-5, AC-7 |
| `frontend/src/staff/DeleteStaffModal.jsx` | Hộp thoại xác nhận xóa nhân viên với cảnh báo an toàn | AC-6 |
| `frontend/src/staff/staff.css` | Phong cách CSS cho bảng danh sách, form nhập liệu, modal và huy hiệu trạng thái/role | AC-2 |
| `frontend/src/layout/SiteLayout.jsx` | Thêm điều hướng tới trang Quản lý nhân viên cho tài khoản Admin | AC-1, AC-2 |

---

## Steps
- [x] 1. **Khởi tạo nhánh Git**: Tạo nhánh tính năng `feature/dt2-10-staff-management` từ `main`.
- [x] 2. **Backend Security & Contracts**: Cập nhật `SecurityConfig.java` bảo vệ route `/api/v1/staff/**` chỉ cho role `ADMIN`. Tạo các DTOs trong package `vn.codegym.smartcafe.staff`.
- [x] 3. **Backend Service & Database**: Triển khai `StaffService.java` tương tác với `users`, `staff_profiles` và `media_assets`:
  - Phân trang 10 dòng/trang, sắp xếp mặc định theo `username`.
  - Tìm kiếm OR theo `username`, `full_name`, `phone`.
  - Thêm mới với validation username (>6 ký tự, không bắt đầu bằng số, không trùng, cấm admin/root), salary (>0, bội số 100k), hash mật khẩu an toàn.
  - Sửa thông tin: khóa `username` không cho đổi, cập nhật các trường hồ sơ và lương.
  - Xóa an toàn: chặn xóa tài khoản hiện tại, kiểm tra lịch sử để soft delete (`INACTIVE`) bảo vệ toàn vẹn dữ liệu.
- [x] 4. **Backend Controller & Cloudinary Avatar**: Triển khai `StaffController.java` và tích hợp upload ảnh qua Cloudinary lưu vào `media_assets`.
- [x] 5. **Backend Verification Tests**: Viết bộ test `StaffControllerTest.java` kiểm thử toàn diện các điều kiện AC, chạy `backend/mvnw test -Dtest=StaffControllerTest`.
- [ ] 6. **Frontend API & Components (DT2-70)**: *Tạm gác lại / đã xóa theo yêu cầu của lập trình viên: Ưu tiên hoàn tất 100% phần Backend (Coding BE + Database + Kiểm thử API) trước.*
- [ ] 7. **Frontend Verification**: *Sẽ thực hiện khi triển khai subtask DT2-70 sau.*
- [x] 8. **Backend HTTP Report Test & Postman Verification**: Hoàn tất bộ test tích hợp HTTP thật `StaffHttpReportTest` (19/19 PASS), xuất báo cáo HTML và cung cấp Postman collection.
- [ ] 9. **Chuẩn bị Pull Request**: Rà soát diff, chuẩn bị báo cáo kiểm thử và sẵn sàng tạo PR cho Dev xác nhận.

---

## Verification
- [x] Kiểm tra phân quyền: Request từ `STAFF` hoặc anonymous tới `/api/v1/staff` nhận `403 Forbidden`. Request từ `ADMIN` nhận `200 OK`.
- [x] Kiểm tra phân trang & sắp xếp: Trả về 10 bản ghi/trang, sắp xếp theo `username`.
- [x] Kiểm tra tìm kiếm OR: Tìm kiếm theo một phần username, name hoặc tel trả về đúng kết quả; không nhập từ khóa trả về toàn bộ; không khớp từ khóa trả về danh sách rỗng / thông báo "Not found".
- [x] Kiểm tra Validation thêm mới:
  - Username: 5 ký tự -> lỗi; bắt đầu bằng số `1user` -> lỗi; `admin` / `root` -> lỗi; trùng username -> lỗi `409 Conflict`.
  - Salary: 0 hoặc số âm -> lỗi; không chia hết cho 100.000 VNĐ -> lỗi.
- [x] Kiểm tra sửa nhân viên: Cố tình thay đổi username qua payload -> bị bỏ qua hoặc từ chối; dữ liệu khác được lưu đúng.
- [x] Kiểm tra xóa nhân viên: Hủy xóa -> không đổi dữ liệu; xác nhận xóa -> xóa thành công; Admin không thể tự xóa chính mình.
- [x] Kiểm tra upload ảnh: Upload ảnh nhân viên lên Cloudinary, lưu `media_assets` và nạp lại URL ảnh hiển thị trên danh sách/chi tiết.
- [x] Kiểm tra lệnh kiểm thử:
  - Backend: `.\mvnw.cmd test -Dtest=StaffControllerTest` (6/6 tests passed)
  - Backend regression: `.\mvnw.cmd test -Dtest=HeaderMySqlTest` (5/5 tests passed)
  - Frontend: `npm run lint` (0 error, 0 warning) và `npm run build` (build thành công)
  - Governance: `node scripts/skills.mjs check; node --test scripts/skills.test.mjs; git diff --check` (9/9 passed)

---

## Risks, dependencies, open questions
- **Phụ thuộc cơ sở dữ liệu**: Đảm bảo bảng `users` và `staff_profiles` trên môi trường MySQL local và test schema đã có đủ cấu trúc theo sơ đồ 11/11/2026.
- **Mật khẩu khởi tạo**: Tạo mật khẩu mặc định (ví dụ `Staff@123`) có mã hóa BCrypt và bật cờ `must_change_password = 1`.
- **Chính sách xóa**: Với nhân viên đã có lịch sử đơn hàng/hóa đơn, hệ thống cập nhật `status = 'INACTIVE'` thay vì xóa vật lý khỏi database để tránh lỗi khóa ngoại (FK constraint).

---

## Progress
- [x] Dev approved plan
- [x] Implementation complete
- [x] Checks run: command and result
- [ ] PR prepared or created
- [ ] Dev final confirmation

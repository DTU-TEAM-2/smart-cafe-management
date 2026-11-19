# DT2-5 — yêu cầu và kiểm kê, 19/11/2026

Nguồn được owner cung cấp: prompt “TRIỂN KHAI TASK DT2-5 (BACKEND + FRONTEND) — CHỜ PR #20 MERGE”, attachment `9e0a3831-f6bc-44c5-8d27-960927cdfc63`. Phạm vi: danh sách Feedback, chi tiết, Search by Date, Show All, metadata ảnh; chỉ ADMIN và ngày tại quán Asia/Ho_Chi_Minh theo xác nhận trước trong chat. Không sửa/xóa, không thay status READ/UNREAD, không triển khai Firebase/QR.

Jira DT2-5/DT2-37–42 và SRS v4.1 URL trong prompt không truy cập được bằng công cụ hiện có. `PHAN_TICH_DU_AN_CAFE.md` không tồn tại. Figma `7iFkUz20p6rE2fPC9Xn8rp`, node 3:2 bị từ chối quyền (debug d238e1cf-efb1-42b9-a2ef-2f4bd9ce810b). Không tự vượt quyền và không tuyên bố đã đối chiếu đầy đủ SRS/Figma. Triển khai UI theo prompt và layout Admin có sẵn; đối chiếu pixel với Figma là hạng mục chờ quyền.

## Kiểm kê trước triển khai

| Hạng mục | Đã có | Còn thiếu | File liên quan | Giải pháp |
| --- | --- | --- | --- | --- |
| Feedback Database | feedback, feedback_images, media_assets trong DDL bàn giao | Không cần schema mới | backend/src/main/resources/db/smart-cafe/V20261113_01__smart_cafe_schema.sql | Chỉ đọc, kiểm thử *_test |
| API danh sách | POST khách hàng từ DT2-4 | GET quản trị | customerfeedback/CustomerFeedbackService.java | Thêm read service riêng |
| API chi tiết | Chưa có | GET theo ID, ảnh | feedbackmanagement/ | Query feedback và join metadata một lần |
| Tìm kiếm theo ngày | Index created_at,id | Lọc ngày quán | feedbackmanagement/ | UTC half-open từ ngày Asia/Ho_Chi_Minh |
| Admin Feedback UI | AdminDashboard, SiteLayout | Bảng 5 cột, panel, tìm ngày | frontend/src/dashboard/AdminDashboard.jsx | Tái sử dụng dashboard, route #admin-feedback |
| Hiển thị ảnh | metadata khách lưu | Consumer đọc URL hợp lệ | media_assets.secure_url | HTTPS; không ký URL, không mở bucket |
| Security | Signed bearer và /api/v1/admin/** ADMIN | Regression GET | config/SecurityConfig.java | Tái dùng authorization hiện hành |
| Tests | Feedback POST + MySQL fixture | Read API, UI, Postman | tests, docs/qa/postman | Bổ sung test hành vi/quyền/date/race |

## Tiêu chí nghiệm thu

Connector Jira được kiểm tra sau khi khả dụng: query chỉ đọc DT2-5/DT2-37–42 trả `INVALID_ARGUMENT`, site cloud id chưa được user cấp quyền rõ ràng. Không tự cấp quyền hoặc thử tài khoản khác. Figma/SRS/Jira chưa xác minh là giới hạn nguồn, không lấy mô tả này để tuyên bố đã review nguồn gốc.

- Bảng có ID, Creator, Email, Date, Feedback; newest mặc định, phân trang page 0/size 20 (max100), oldest tùy chọn.
- Chọn dòng lấy chi tiết từ backend, không giữ chi tiết cũ khi đổi lựa chọn, lọc, lỗi hoặc phân trang. Có rating và ảnh hợp lệ; không có ảnh hoặc URL private/không hiển thị thì thông báo rõ.
- Date rỗng: “Please enter a keyword!”; không kết quả: “Not found!”. Show All xóa ngày, reset trang và selection. Lọc bằng khoảng UTC của một ngày quán, bao gồm đầu ngày và loại đầu ngày kế tiếp.
- Loading/error/empty rõ; lỗi 401/403/404/5xx/timeout không hiển thị dữ liệu cũ; request cũ không ghi đè kết quả mới. ID BIGINT giữ chuỗi. Backend kiểm signed token và role thực tế.
- Không thêm DDL/seed production; test trên MySQL riêng. Mock browser, integration MySQL, browser→backend thật và Firebase thật ghi riêng.

## Git và điều kiện bàn giao

Nhánh duy nhất: feature/dt2-5_feedback-management, từ develop@48bc136. PR #20 OPEN/chưa merged lúc kiểm tra bắt đầu. Không nhập PR #20, không sửa PR đó. Được code/test/commit/push, chưa tạo bất kỳ PR DT2-5 nào. Kể cả #20 merge vẫn chờ owner yêu cầu tạo PR; sau đó xác minh merge commit thuộc origin/develop, đồng bộ và chạy lại checks trước một PR chung BE+FE.

# DT2-9 — yêu cầu viết lại backend

Ngày 2026-11-11, chủ repo yêu cầu dựa trên `smart-cafe-management-legacy` để viết lại backend quản lý món/nhóm món và chọn nhánh backend riêng. Phạm vi triển khai: Spring Boot, MySQL, API dành cho ADMIN; không tạo tài khoản giả, không mang database/migration history/code ứng dụng cũ sang repo mới.

Nguồn đọc: `smart-cafe-management-legacy/docs/product/dt2-9-menu-contract.md`, SRS v4.1 phần TABLE block 80 R6–R40 (trích riêng trong file cùng thư mục), và các class `backend/src/main/java/vn/codegym/smartcafe/menu/`. Các báo cáo test/approval/ADR cũ không phải kết quả hay quyết định của repo này.

Tiêu chí kiểm chứng:

1. ADMIN CRUD, tìm món theo mã/tên/giá/nhóm, phân trang; STAFF/khách không được quản lý. Mutation vẫn cần CSRF.
2. Mã chuẩn hóa uppercase, bất biến khi sửa, không tái sử dụng sau xóa logic. Tên/giá/nhóm kiểm tra tại server, giá VND nguyên > 5.000; không đọc giá từ browser như quyền hạn chốt đơn.
3. Xóa logic nhóm và tất cả món trong một transaction, giữ hàng dữ liệu để bảo toàn tham chiếu tương lai; cập nhật/xóa có version, khóa nhóm chống ghi trong khi xóa.
4. Upload PNG/JPEG kiểm tra nội dung, tối đa 5 MB/20 triệu pixel, Cloudinary cấu hình bằng môi trường; thiếu cấu hình trả lỗi rõ. Không gửi ảnh/key ra provider trong test.
5. Schema mới từ V001, không seed tài khoản/menu/dữ liệu người dùng. Kiểm chứng MySQL thật trên schema test riêng, không chạy migration vào schema legacy.
6. Lỗi Problem Details có mã và fieldErrors; không lộ SQL/stack/secret. Không có login, order, bill, doanh thu hoặc tích hợp FE trong nhánh backend.

Khác FE demo: FE đang chặn xóa nhóm có món, cho đổi nhóm và giá dương bất kỳ, nhận WebP; backend theo nguồn legacy/SRS tham chiếu ở trên. Tích hợp FE sẽ phải đồng bộ riêng, không tự sửa nhánh FE trong task này. Các trường mô tả/trạng thái có sẵn được giữ để biểu diễn design; không tạo luồng session/order mới.

Không có auth chung trong repo mới. Backend cung cấp Resource Server JWT với issuer/audience cấu hình từ môi trường, roles claim phải được ký bởi issuer. Thiếu cấu hình thì từ chối token; không seed admin hay bypass. Kiểm thử quyền dùng fixture test-only, không tuyên bố đã đăng nhập thật.

# DT2-9 — nguồn yêu cầu giao diện

Ngày: 2026-11-10. Nguồn: yêu cầu trực tiếp của người dùng trong chat.

- Chỉ triển khai FE quản lý món và nhóm món trong repo mới; tham khảo `smart-cafe-management-legacy/frontend/src/menu/`.
- Thiết kế: https://www.figma.com/design/7iFkUz20p6rE2fPC9Xn8rp?node-id=8-292 (danh sách), node `8:319` (form), `57:306` (xác nhận xóa).
- Màu, bố cục và typography lấy từ design context Figma; mở rộng nhóm món theo cùng hệ thiết kế vì trang được cung cấp chưa có frame nhóm món riêng.
- Tiêu chí FE: chuyển món/nhóm món, tìm kiếm, phân trang, form thêm/sửa, xem trước ảnh, xác nhận xóa, trạng thái trống, điều khiển bằng bàn phím và bố cục mobile.
- Dữ liệu minh họa được khai báo riêng, chỉ tồn tại trong bộ nhớ. Tải lại trang sẽ khôi phục mẫu. Không có API, quyền truy cập, tài khoản đăng nhập hay thay đổi dữ liệu thật.
- Validation và quy tắc xóa của bản xem thử là ràng buộc UI tạm thời, chưa phải hợp đồng nghiệp vụ/backend được duyệt. Không nhập SRS, trạng thái hoàn thành hoặc dữ liệu nhân viên/khách từ legacy.

Tham khảo cấu trúc React/Vite, native dialog và luồng quản lý từ legacy; mã tính năng được viết mới, không sao chép lịch sử Git hoặc ghi nhận inherited work thành yêu cầu đã duyệt.

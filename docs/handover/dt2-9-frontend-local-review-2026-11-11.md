# DT2-9 — yêu cầu rà soát frontend local

Ngày 11/11/2026, chủ repo yêu cầu: “kiểm tra lại nhánh frontend và sửa lỗi nhưng chưa push hay tạo pull request”. Nhánh hiện có: feature/dt2-9-manage-menu-items-and-categories-frontend, baseline a2aa41a. Phạm vi là sửa module FE hiện có dùng dữ liệu minh họa; không tự mở rộng sang auth, backend, migration hoặc sửa DB.

Nguồn đối chiếu:

- [Tiêu chí nghiệm thu do chủ repo gửi](evidence/dt2-9-acceptance-2026-11-11.png): danh sách/CRUD liên quan; mã trùng/dữ liệu sai và chưa chọn dòng; xác nhận/hủy xóa nhóm có món; tìm 4 tiêu chí, tìm trống/không kết quả, Show List Service; ảnh Cloudinary dùng trong menu khách.
- [Yêu cầu FE và nguồn Figma](dt2-9-frontend-request.md): giữ bố cục/style đã triển khai, dữ liệu trong bộ nhớ.
- Backend đã merge [PR #8](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/8), đối chiếu local main 027a05ff47f8c7ef322eb5def6602fb4c182d57e: mã món 4–40, nhóm 1–32; tên món 6–160, nhóm 1–120; giá nguyên >5.000 và decimal(18,0); nhóm món bất biến khi sửa; xóa logic nhóm ẩn các món, giữ mã; PNG/JPEG ≤5 MiB/20 triệu pixel. Không có description. Không thêm ràng buộc trùng tên ngoài contract.

Chỉ đồng bộ hành vi UI có thể kiểm tra độc lập. Không có truy vấn/DDL/seed hoặc API trong lượt này. Giá dùng chuỗi chính xác trong fixture FE, không thay đổi API/persistence hoặc quyền chốt giá của backend. Ảnh cục bộ không tạo imageMediaId hoặc giả làm ảnh Cloudinary.

Các mục nghiệm thu đòi hỏi cập nhật DB thật, auth, Cloudinary và menu khách vẫn chưa đủ bằng chứng hoàn tất; test UI fixture chỉ xác nhận phần tương tác FE. Việc tích hợp nhánh với main mới và nối API cần thực hiện riêng, không ghi đè ứng dụng main trong lượt sửa local này.

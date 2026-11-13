# Dữ liệu trang chủ được chủ repo yêu cầu thêm

Ngày 06/11/2026, chủ repo yêu cầu trực tiếp: “vào db tạo vài cái để cho nó hiện lên cho tôi”. Đã thêm bằng một transaction vào đúng `smart_cafe`, không đổi schema và không thêm dữ liệu tĩnh vào frontend.

- 1 nhóm Cà phê; 6 món: Cà phê pha thủ công, Cà phê đen, Cà phê sữa, Bạc xỉu, Latte, Cappuccino.
- 4 media_assets CLOUDINARY thuộc cloud đã cấu hình của dự án, URL/bytes/kích thước lấy từ asset thật. Ba ảnh cà phê local có provenance được tải lên bằng public_id mới, không ghi đè ảnh cũ; một ảnh được tái sử dụng.
- 1 bàn, 1 phiên, 1 batch và 6 order_items, quantity lần lượt 8/6/5/4/3/2. Giá/tên/mã snapshot lấy trực tiếp từ menu_items khi INSERT SELECT; line_total do DB sinh.
- Vì users đang rỗng và FK bắt buộc người mở phiên, thêm 1 bản ghi kỹ thuật STAFF/INACTIVE, password BCrypt ngẫu nhiên không giữ plaintext. Không cấp tài khoản đăng nhập cho bản ghi này.
- Không tạo bill/payment, không ghi doanh thu hoặc sửa dữ liệu cũ. Script nhập và receipt ID chỉ giữ trong `.cache/`, không tự chạy lúc khởi động và không chứa credential MySQL trong Git.

Đây là dữ liệu khởi tạo do chủ repo yêu cầu để kiểm tra danh sách, không phải lịch sử giao dịch do khách thực hiện. App vẫn đọc API/MySQL thật. API đã trả 5 newest đúng thứ tự và 5 mostOrdered đúng quantity; browser đã được tải lại để hiển thị dữ liệu.

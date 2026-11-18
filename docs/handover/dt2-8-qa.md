# DT2-8 · Trả lời QA SRS

Các câu 21–23 được người dùng cung cấp từ [QA SRS](https://docs.google.com/spreadsheets/d/1m2vnTOdGWivwqokRiJYOpWmY2u3G_o-U/edit?gid=1741591823#gid=1741591823) ngày 17/11/2026. Cả ba câu có trạng thái Answered.

| Câu | Trả lời | Áp dụng |
| --- | --- | --- |
| 21 | “xem hóa đơn thì staff vẫn xem được. nhưng các quyền khác thì cần admin” | STAFF và ADMIN xem danh sách và chi tiết. Thống kê và nút in của DT2-8 dành cho ADMIN. |
| 22 | “ok em” | Tỷ trọng doanh thu theo phương thức thanh toán, dùng cùng kỳ với biểu đồ cột. |
| 23 | “nếu chuyển được thì tốt” | Nghiệm thu trên schema theo ERD mới. Đối chiếu khả năng chuyển hóa đơn cũ theo dữ liệu gốc và phương án được chủ DB xác nhận. |

Doanh thu theo phương thức phân nhóm các hóa đơn PAID theo `payments.method` và dùng ngày kinh doanh của `bills.paid_at`. Mẫu số là tổng doanh thu trong kỳ của Income. Hóa đơn PAID thiếu dòng thanh toán xuất hiện ở nhóm “Chưa rõ phương thức” để tổng các nhóm vẫn khớp tổng doanh thu. Khoảng dài hơn 31 ngày được gom theo tháng trên biểu đồ cột và giữ dữ liệu từng ngày trong bảng đối soát.

Đã đếm dữ liệu trên MySQL Docker local `smart_cafe_dt26` bằng truy vấn chỉ đọc. Hai bảng `bills` và `bill_items` đều có 0 dòng. Nếu còn bản dữ liệu cũ ở nguồn khác, cần xác nhận nguồn và đối chiếu các trường snapshot trước khi chuyển.

Ngày 18/11/2026 đã mở trực tiếp hai frame [Staff · Hóa đơn](https://www.figma.com/design/7iFkUz20p6rE2fPC9Xn8rp/LECKER?node-id=8-66) và [Admin · Thống kê doanh thu](https://www.figma.com/design/7iFkUz20p6rE2fPC9Xn8rp/LECKER?node-id=8-427) trên trình duyệt. Đã nhận bản `.fig` và hai PNG nguồn từ người dùng, lưu trong [gói tham chiếu local và manifest](../design/figma/dt2-8/README.md). Người dùng yêu cầu giữ biểu đồ hiện tại theo QA SRS 22 và chỉ sửa các sai lệch UI khác. Ảnh triển khai trên dữ liệu kiểm thử có trong [thư mục evidence](evidence/dt2-8/).

## Kiểm tra local trên `develop@ffa4d95`

| Kiểm tra | Kết quả |
| --- | --- |
| Backend MySQL | Maven `verify` đạt 230/230, gồm 14 kiểm thử hoá đơn DT2-8. Dùng JDK 21 và MySQL Docker 8.0.44 cô lập, nạp `schema.sql` như CI. |
| Frontend UI | Node 22.19.0, `npm ci`, lint và build đạt. Playwright đạt 90/90 với dữ liệu HTTP mô phỏng, trên desktop và mobile. |
| Governance | Skill registry và checksums so với base đạt, 20 kiểm thử scripts đạt, `git diff --check` sạch. |
| Git Flow và PR title | Script chấp nhận nhánh `feature/dt2-8-bills-statistics` vào `develop`. Regex tiêu đề đạt với dữ liệu mẫu. |
| Workflow DT2-4 Postman | Newman 6.2.1 đạt 47/47 assertions trên 25 HTTP requests của hai collection Menu và QR. Cả hai báo cáo Newman được render thành công. |
| API DT2-8 | Đăng nhập thật Admin, Staff và Customer trên DB kiểm thử. Danh sách/chi tiết trả 200 cho Admin và Staff, thống kê/Income chỉ Admin, Customer và khách chưa đăng nhập nhận 403. Tổng 200.000 ₫ khớp Income, bằng 110.000 ₫ tiền mặt và 90.000 ₫ chuyển khoản. |

Các kiểm tra chạy trên Windows local. Trạng thái check GitHub cho DT2-8 sẽ có khi có sự kiện pull request trên nhánh đích.

## Đối chiếu UI và gói nguồn ngày 18/11/2026

Đã đối chiếu trực tiếp hai frame trong trình duyệt và các PNG nguồn người dùng gửi. Đã chỉnh header desktop theo mốc 96 px, tách logo và dòng COFFEE CAFE, dùng Playfair Display 33 px cho tiêu đề, bổ sung dòng phụ, nhãn Thông báo/Tài khoản và khung danh sách. Bảng và chi tiết hiển thị trạng thái tiếng Việt. Nút Xem chi tiết nằm dưới danh sách và tiếp tục hỗ trợ chọn dòng, retry, bàn phím và in theo quyền.

Biểu đồ và phép tính tiếp tục theo QA SRS 22 theo yêu cầu mới nhất. Sidebar liên kết các route đã có của Tổng quan, Trạng thái bàn, Hóa đơn và Thống kê theo quyền. Điều hướng tới các module khác trong Figma phụ thuộc task tương ứng.

[Manifest local](../design/figma/dt2-8/manifest.json) ghi file key, node ID, thời điểm xuất, kích thước và SHA-256. Bản `.fig` đã qua kiểm tra CRC của ZIP và có canvas, metadata, thumbnail. Hai PNG nguồn đều có kích thước 1440×892. Gói nguồn khoảng 519 KiB và được hướng dẫn dùng trước trong AGENTS.md.

Sau chỉnh UI, `npm run lint`, `npm run build` và toàn bộ Playwright đạt 90/90. Đã chụp lại các ảnh evidence sau khi font tải xong, kiểm tra lại 6 ca liên quan ảnh desktop/mobile. Registry/checksums và 20 kiểm thử scripts đạt, `git diff --check` sạch. Các kiểm thử API/MySQL của bản trước được ghi ở trên, CI sẽ chạy lại trên commit cập nhật của PR #18.

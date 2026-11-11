# DT2-1 — Đối chiếu task con

Owner NV-DuyManh, cập nhật 09/11/2026. Bàn giao review trên nhánh feature/dt2-1-home-review, chưa merge/Done. Theo yêu cầu mới nhất, chỉ có [UI đăng nhập](dt2-1-login-ui.md), đã gỡ tài khoản và cơ chế demo; chưa triển khai luồng đăng nhập production.

| Task | Đầu ra | Trạng thái kiểm chứng |
| --- | --- | --- |
| DT2-13 Prototype | Trang chủ, trang hồ sơ, popup thông báo theo bố cục Figma; font Việt/ảnh UI | Cần chủ thiết kế duyệt; Figma MCP hết quota, chưa chứng nhận pixel-perfect |
| DT2-14 Database | DDL test lấy từ database thật, mapping quan hệ và query theo schema chủ repo | Đã chạy test MySQL; không sửa database hệ thống; xem database-contract.md |
| DT2-15 Coding BE | Highlights public, profile theo Principal, notification theo role; bearer contract thật | 8 test PASS, không bỏ qua test DB |
| DT2-16 Coding FE | Shared layout, avatar/#account, feed polling/retry/dedup và cleanup theo token; chuẩn bị UI login | 14 test desktop/mobile PASS; không còn đăng nhập mock, chưa nối xác thực thật |
| DT2-17 Review & Quality | Tự review và bằng chứng lệnh/checks | Còn review độc lập và smoke toàn hệ thống khi module auth/order nhập repo; Sonar chưa cấu hình |
| DT2-18 Fix Bug | Font Việt, ảnh API, own identity/recipient, token validation, retry không lặp và cleanup | Regression tests PASS; không đánh dấu Done khi chưa nghiệm thu |

## AC task cha

- Layout đủ Header/Body/Footer, responsive và điều hướng: đã kiểm tự động/quan sát; cần xác nhận thiết kế cuối.
- Top 5 đúng dữ liệu 0/<5/>5 và SQL schema thật: PASS trong MySQL test; runtime smart_cafe đã được chủ repo yêu cầu thêm 6 món/1 batch, API trả đúng 5 newest và 5 mostOrdered.
- Avatar/profile và thông báo đúng quyền: API kiểm bearer/DB role/status/version/identity; UI kiểm token đổi thì xóa state cũ. End-to-end với tài khoản vận hành chưa nghiệm thu: module login/order chưa nhập repo và bản ghi kỹ thuật khởi tạo đã bị khóa, không phải tài khoản đăng nhập.
- Trang chủ và #account dùng một Header/Main/Footer: PASS desktop/mobile.

Không dùng mock test hay dữ liệu trình diễn để tuyên bố hoàn tất luồng vận hành thật. Những lần kiểm tra trước bằng profile Basic không còn là bằng chứng cho phiên bản hiện tại. Không tự tạo account/order, nạp seed vào hệ thống hoặc thay schema để làm đầy màn hình.

## Kiểm chứng

Maven verify trên schema *_test riêng: 8 PASS/0 skipped. npm lint/build: PASS. Playwright: 14 PASS. Registry/checksum/routing: PASS (9 test). Runtime GET highlights: HTTP 200; lần kiểm thử không ghi/sửa smart_cafe. Kết quả CI trên revision bàn giao xem tại Checks của PR; không đồng nhất local PASS với CI PASS.

Lỗi đã sửa gồm font fallback Việt, thiếu ảnh media, chỉ nối prop mà chưa đọc API, giữ state người cũ khi đổi token, request retry feed bị lặp do refetch account và UI test mobile cần đóng popup bằng Escape. Token giả/sai chữ ký/hết hạn, tài khoản khóa và auth_version tăng đều bị từ chối. Role trong token không thay role thật tại DB.

Chờ chủ repo/mentor duyệt; SonarQube, thiết bị Safari thật và login/order production smoke: NOT RUN. Ranking dùng quantity không CANCELLED toàn thời gian theo view hiện hành; PO xác nhận khoảng thời gian và available trước nghiệm thu nghiệp vụ.

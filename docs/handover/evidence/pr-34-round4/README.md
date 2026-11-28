# Minh chứng Round 4 của PR #34

Frontend `bugfix/cf-22_news-reliability`. Mốc trước Round 4 là `64edf6b`, base `develop@be2306d`.

## Giao diện

| Màn hình | Điều được kiểm tra |
| --- | --- |
| [Bảng tin desktop](01-bang-tin-desktop.jpg) | Banner, danh sách thật từ REST, sắp xếp và ảnh giữ chỗ của bài chưa có ảnh. |
| [Chi tiết bài trên mobile](02-chi-tiet-mobile.jpg) | Nội dung bài dành cho khách hàng và bố cục hẹp. |
| [Trình soạn desktop](03-trinh-soan-desktop.jpg) | ADMIN đọc bài có sẵn, thấy nội dung, trạng thái, nhóm nhận tin và khu vực ảnh. |

Các ảnh được chụp trên Chromium bằng frontend của PR, Spring Boot và MySQL 8.0 trong schema local riêng có hậu tố `_test`. Tài khoản và hai bài viết trong ảnh là dữ liệu tổng hợp. Ảnh giữ chỗ minh họa dữ liệu chưa có ảnh đại diện.

## Kiểm thử Round 4

**34-F01 · WebSocket sau READY.** Test mới giữ tab mở và mô phỏng 10 phút không nhận frame, trong khi dữ liệu REST đã thay đổi. Client giữ một socket và cập nhật đúng một lần khi nhận `NEWS_CHANGED`. Các ca mất mạng, tab quay lại, chưa nhận READY, kết nối thất bại và close 1008 tiếp tục chạy. [Kiểm thử Tin tức](../../../../frontend/tests/news.spec.js).

Bộ quét backend kiểm tra lại quyền STAFF mỗi 5 giây. Ping/pong ở tầng giao thức không xuất hiện trong API JavaScript. Cấu hình timeout của reverse proxy triển khai và SLA phát hiện kết nối treo vẫn chưa được xác minh. Round 4 bổ sung test và giữ giao thức hiện tại. [WHATWG WebSockets](https://websockets.spec.whatwg.org/#ping-and-pong-frames).

## Kết quả chạy local

| Lệnh | Môi trường | Kết quả |
| --- | --- | --- |
| `npm run lint` và `npm run build` | Node 22.19, Vite production | Đạt. |
| `npm run test:unit` | Node 22.19 | 23 đạt. |
| `npx playwright test news.spec.js login.spec.js` | Chromium desktop và mobile giả lập | 114 đạt, 0 lỗi. |
| `mvnw.cmd -B -ntp "-Dtest=**/news/*Test.java" verify` | Java 21, MySQL 8.0 riêng, UTC | 89 đạt, 0 lỗi, 0 skip. |
| Kiểm tra UI bằng trình duyệt với API và DB thực | Hai tài khoản tổng hợp CUSTOMER và ADMIN trong schema riêng | Mở bảng tin, chi tiết và trình soạn thành công. Xem ảnh phía trên. |

CI của bản cập nhật được ghi trực tiếp trên [trang kiểm tra PR #34](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/34/checks).

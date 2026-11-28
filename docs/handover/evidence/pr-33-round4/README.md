# Minh chứng Round 4 của PR #33

Frontend `bugfix/cf-21_home-layout`. Mốc trước Round 4 là `2e35494`, base `develop@be2306d`.

## Giao diện

| Màn hình | Điều được kiểm tra |
| --- | --- |
| [Trang chủ desktop](01-trang-chu-desktop.jpg) | Banner, ba câu chuyện, danh sách món từ API và footer. |
| [Chi tiết câu chuyện desktop](02-cau-chuyen-desktop.jpg) | Hộp thoại mở từ thẻ câu chuyện. |
| [Hồ sơ mobile](03-ho-so-mobile.jpg) | Hồ sơ đã xác minh với dữ liệu nhân sự tổng hợp. |
| [Đang xác minh lại](04-dang-xac-minh.jpg) và [bản nháp sau xác minh](05-ban-nhap-sau-xac-minh.jpg) | Trình soạn ẩn trong lúc xác minh. Khi cùng phiên ADMIN được xác nhận, tiêu đề và nội dung xuất hiện lại. |

Ảnh được chụp trên Chromium, với Spring Boot và MySQL 8.0 trong schema local riêng có hậu tố `_test`. Hai ảnh xác minh lại mô phỏng thông báo trả 403 và tạm giữ phản hồi Header bằng Playwright. Tên, tài khoản và nội dung bài viết trong ảnh hồ sơ và trình soạn là dữ liệu tổng hợp. Phần Top 5 và thông tin quán theo giao diện hiện có, QA-06 vẫn chờ quyết định nghiệp vụ.

## Kiểm thử Round 4

| Finding | Kết quả và minh chứng |
| --- | --- |
| 33-F01 · P2 | Profile GET sai ID hoặc vai trò bị chặn. ID chuỗi lớn được đối soát chính xác theo cả hai thứ tự phản hồi. PATCH sai tài khoản giữ bản nháp và yêu cầu tải lại trước lần ghi tiếp theo. [Kiểm thử hồ sơ](../../../../frontend/tests/account-ui.spec.js). |
| 33-F02 · P2 | Cùng phiên ADMIN xác minh lại sẽ giữ bản nháp và ảnh. 403 xác minh, đổi vai trò hoặc đổi tài khoản dọn dữ liệu phiên cũ. POST/PUT mất phản hồi cần đối soát và chỉ gửi một lần. [Kiểm thử bản nháp và điều hướng](../../../../frontend/tests/home-news-shell.spec.js). |
| 33-F03 · Phòng thủ | Auth và Header phải khớp ID, vai trò. Kiểm tra cache giả, JSON không hợp lệ, lỗi 503 và xác minh lại có giới hạn. [Kiểm thử Auth](../../../../frontend/tests/auth-regression.spec.js) · [Kiểm thử Header](../../../../frontend/tests/header.spec.js). |
| 33-F04 · P3 | Dấu đã đọc tồn tại khi chuyển workspace trong cùng phiên và được xóa khi đổi phiên. [Kiểm thử Header](../../../../frontend/tests/header.spec.js). |
| 33-F05 · P3 | Phản hồi Header mới có thể cập nhật hiển thị. Header đọc trước lần cập nhật hồ sơ không ghi đè kết quả PATCH đến sau. [Kiểm thử thứ tự phản hồi](../../../../frontend/tests/account-ui.spec.js). |
| 33-F06 · P3 | Dùng nút trong popup tài khoản theo yêu cầu của chủ dự án. [Kiểm thử xác nhận rời form](../../../../frontend/tests/account-ui.spec.js) · [Back/Forward](../../../../frontend/tests/home-news-shell.spec.js). |

Lượt tái hiện trên `2e35494` xác nhận lỗi hồ sơ sai scope, mất bản nháp, lệch vai trò, dấu đã đọc và thông tin Header bị cũ. Bản sửa được kiểm thử độc lập trước khi kiểm tra tương tác với #34.

## Kết quả chạy local

| Lệnh hoặc bộ test | Môi trường | Kết quả |
| --- | --- | --- |
| `npm run lint` và production build trong Playwright | Node 22.19, Vite | Đạt. |
| `npm run test:ui -- --workers=4` | Chromium desktop và mobile giả lập | 20 unit đạt. Playwright 537 đạt, 5 skip theo cấu hình, 0 lỗi. |
| `mvnw.cmd verify` với nhóm Home, Header, Profile, OTP và hồi quy nghiệp vụ liên quan | Java 21, MySQL 8.0 riêng, UTC | 129 đạt, 0 lỗi, 0 skip. |
| Playwright trên bản ghép tạm với PR #34 | Chromium desktop và mobile giả lập | 150 đạt ở nhóm Tin tức, điều hướng, Header, Login. 6 ca bổ sung về thứ tự phản hồi và POST mất phản hồi đạt ở lượt riêng. |
| Playwright trên bản ghép tạm với PR #34 | WebKit giả lập và Firefox desktop | 8 ca về điều hướng, đổi phiên, dấu thông báo đạt ở lượt ban đầu. 7 ca Auth DTO đạt ở lượt WebKit sau cùng. Lượt mở rộng trên Firefox local có timeout điều hướng và teardown. |

Lượt Maven chạy hai nhánh song song đã đụng cổng SMTP trên máy local. Nhóm OTP bị ảnh hưởng chạy riêng đạt 19/19. CI của bản cập nhật được ghi trực tiếp trên [trang kiểm tra PR #33](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/33/checks).

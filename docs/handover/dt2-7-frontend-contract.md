# DT2-7 — giao diện tin tức

## Phạm vi và nguồn

Chủ repo mở rộng từ BE sang “làm ui luôn… full task 7” ngày 11/11/2026, tại thời điểm đó yêu cầu review local và chưa commit/push. Quyền mở PR đã được chủ repo cấp ngày 13/11, xem mục bàn giao cuối tài liệu. React dùng lại Header/Footer, bảng màu nâu/vàng, font và bố cục responsive của LECKER. Đây là cách nhóm hiện thực prototype theo Q&A 10, chưa phải xác nhận pixel-perfect từ một bản Figma tin tức được duyệt.

[Q&A 4, 5, 19, 20](dt2-7-qa-replies-2026-11-11.md) và [backend contract](dt2-7-backend-contract.md) xác định quyền, xóa mềm, nhóm nhận và socket. Không mở quyền quản trị STAFF hoặc quyền đọc ẩn danh. Không thay đổi schema trong phần UI.

## Điều hướng và thao tác

| Route | Hành vi |
| --- | --- |
| `#news` | ADMIN xem danh sách quản lý, lọc nháp/đã đăng/lưu trữ; STAFF/CUSTOMER xem tin đúng nhóm. Sắp xếp, phân trang 6 tin/trang, làm mới, loading/empty/error/retry. |
| `#news/{id}` | Đọc chi tiết, ảnh, tác giả, thời gian. ADMIN có chỉnh sửa và lưu trữ; đọc ngoài quyền dùng lỗi 404 của BE. |
| `#news/new` | ADMIN soạn tiêu đề/tóm tắt/nội dung/slug, chọn trạng thái và ít nhất một nhóm Nhân viên/Khách hàng. |
| `#news/{id}/edit` | ADMIN sửa, giữ ETag đã tải và gửi If-Match. Có thể khôi phục bài lưu trữ bằng trạng thái nháp/đã đăng. |

UI không đặt role hoặc dựng tài khoản. Form `#login` gọi `POST /api/v1/auth/login` của DT2-2, sau đó xác minh token qua `/api/v1/home/account` trước khi lưu vào sessionStorage `smart_cafe_token`. HomeShell đọc danh tính hiện hành; BE quyết định quyền cuối cùng. Không lưu mật khẩu, hủy request khi rời form, hiển thị lỗi và trạng thái chờ. Đăng xuất xóa credential của ứng dụng ở sessionStorage và localStorage cũ, phát `smart-cafe-auth-change`; không tuyên bố thu hồi mọi phiên phía server. Kiểm thử tích hợp thao tác form thật cho cả ba vai trò bằng issuer PR #7, không tự ký hoặc chèn token vào trình duyệt.

Sau tạo/sửa thành công, mở trang chi tiết. Nháp chỉ quản lý thấy. Lưu trữ cần xác nhận trong dialog, có Escape, focus ban đầu, giữ focus trong dialog và trả focus về nút mở khi hủy. Lưu trữ không xóa dữ liệu/ảnh vật lý.

Khi rời chi tiết trong lúc request lưu trữ còn chạy (ví dụ browser Back), component hủy chờ response và không điều hướng/đóng dialog của trang đã rời. Hủy chờ ở trình duyệt không bảo đảm hủy lệnh đã tới BE; trạng thái lưu trữ cuối cùng phải đọc lại từ REST. Đã tái hiện và sửa callback muộn 204/503, bổ sung 4 regression desktop/mobile. Sau tích hợp main 8476ee3, bộ UI đạt 68/68, gồm Sales, hồi quy socket và thông báo xung đột.

Form giữ nội dung khi có lỗi. HTTP 412 khóa lưu tiếp và yêu cầu tải bản mới; trước khi thay bản đang soạn phải xác nhận. Rời form chưa lưu qua link, back/hash hoặc đóng/tải lại trang có nhắc bỏ thay đổi. Không tự ghi đè bản mới hơn. Trường sai có label, aria-invalid, liên kết trợ giúp/lỗi và thông báo tổng; nội dung luôn render như văn bản, không dùng HTML từ bài viết.

## Ảnh và realtime

Upload part `file` đến `/api/v1/news/images`; client kiểm loại PNG/JPEG, kích cỡ và số lượng, BE xác minh nội dung/kích thước thực. Các ảnh được tải tuần tự để giới hạn bộ nhớ; ảnh thành công trước một lỗi vẫn giữ trong form. Có gỡ liên kết và đổi thứ tự, ảnh đầu làm đại diện. Khi lưu gửi media ID dạng chuỗi, giữ chính xác ID vượt giới hạn số JavaScript.

Ảnh riêng `/api/v1/news/images/{id}` tải bằng Authorization, tạo blob URL trong bộ nhớ và revoke khi đổi ảnh/credential/unmount. Không nhét token vào URL hoặc mở ảnh riêng thành public. URL ảnh cũ chỉ nhận HTTPS không chứa userinfo; dữ liệu bài vẫn được escape. Thiếu ảnh hiển thị dấu hiệu LECKER; ảnh tải lỗi có thông báo thay thế.

Chỉ STAFF mở WebSocket cùng origin; frame đầu gửi bearer, READY xác nhận kết nối. NEWS_CHANGED làm tải lại danh sách/chi tiết từ REST. Sau reconnect READY cũng tải lại để bù sự kiện bỏ lỡ. Mất kết nối tự thử lại với backoff tối đa 30 giây; đóng 1008 yêu cầu kiểm tra lại danh tính, chỉ kết nối tiếp khi response tài khoản mới vẫn xác nhận STAFF hợp lệ. Giữ backoff qua các lần kiểm tra này để tránh vòng gọi liên tục. CUSTOMER/ADMIN hoặc phiên thu hồi không kết nối lại. Không tự lưu lại nội dung bài cũ khi REST đã từ chối.

HTTP 409 phân biệt trùng đường dẫn và lỗi ghi đồng thời bằng code của BE. Cả hai giữ draft; chỉ `NEWS_SLUG_TAKEN` mới hướng dẫn đổi đường dẫn. JPEG từ điện thoại được BE áp dụng hướng EXIF trước khi bỏ metadata, FE vẫn đọc blob ảnh riêng qua bearer, không nhận key cloud.

## Chạy và giới hạn tích hợp

Đặt `VITE_API_PROXY_TARGET` tới BE Task 7 khi chạy Vite; proxy hỗ trợ HTTP lẫn WebSocket. Nếu DT2-2 chạy riêng, đặt `VITE_AUTH_PROXY_TARGET` tới issuer thật; chỉ login/me/logout được chuyển tới đó. Hai BE phải dùng cùng DB và signing secret. Production reverse proxy phải định tuyến các endpoint tương ứng, forward Upgrade cho `/api/v1/news/events`, giữ cùng origin và không log frame bearer. Không đặt secret Firebase/Auth trong biến VITE hoặc bundle frontend.

Backend cần schema audience đã được duyệt và AUTH_TOKEN_SECRET khớp module DT2-2. Trong phạm vi chỉ miễn phí, ảnh dùng Firebase Storage Emulator theo quickstart, không cần bucket cloud hoặc ADC/service-account. Theo chỉ thị kiểm tra dự án cũ `F:\CODEGYM\smart-cafe-management`, cấu hình tìm thấy là Cloudinary cho menu; không có bucket/credential Firebase để chuyển sang. Không tự coi Cloudinary là Firebase hoặc thay nguồn yêu cầu.

Chủ repo chốt chỉ dùng miễn phí, không bật billing. Project mới giữ Spark; Storage cloud yêu cầu Blaze nên chưa được bật. Có thể kiểm thử upload/ảnh riêng miễn phí qua profile `firebase-emulator` của BE trên MySQL `_test` riêng theo backend contract. UI tiếp tục gọi cùng API upload/đọc ảnh; không đưa endpoint emulator, key hoặc logic vượt quyền vào frontend. Kết quả emulator không thay thế bằng chứng cloud/IAM.

Test report, ảnh kiểm tra desktop/mobile, log và patch review nằm ngoài repo tại `F:\CODEGYM\_local-reviews\dt2-7-be`. Những tài liệu hợp đồng này không thay thế báo cáo kiểm chứng cloud hoặc nghiệm thu prototype.

## Bàn giao PR ngày 13/11/2026

Chủ repo yêu cầu “fix rồi đẩy lên, open luôn”: cho phép commit/push nhánh `feature/dt2-7-news-backend` và mở PR vào `develop`, thay giới hạn chỉ làm local trước đó. Đã ghép baseline `develop@79896a9`, giữ QR khách hàng, menu preview và workflow Postman mới. Không nhập source issuer DT2-2 chưa merge; đăng nhập cần module đó hoặc proxy tới instance thật, cùng DB và secret. Firebase chỉ dùng emulator miễn phí trên MySQL `_test`; không bật cloud/billing. PR chờ review, chưa merge hoặc deploy.

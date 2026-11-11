# DT2-6 — Bán hàng theo bàn và thu nhập

- Task: [DT2-6](https://duymanhdnvn20092005.atlassian.net/browse/DT2-6).
- Nền tích hợp: `feature/dt2-1-home-review` tại `46fb813`, [PR #4](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/4), trên lịch sử repository mới. Không mang migration/code từ repository trước.
- Nguồn: [Figma](https://www.figma.com/design/7iFkUz20p6rE2fPC9Xn8rp?node-id=10-70), frames 8:24, 8:45, 8:66, 8:87; [QA, các dòng 4–7, 11, 14, 17, 19](https://docs.google.com/spreadsheets/d/1m2vnTOdGWivwqokRiJYOpWmY2u3G_o-U/edit?gid=1741591823#gid=1741591823); [schema được ghi nhận trong PR #4](../architecture/database-contract.md).
- Giai đoạn: triển khai xong phạm vi mô tả bên dưới, chờ review nhóm và tích hợp các module phụ thuộc. Không xác nhận Done.

## Quyết định áp dụng từ Q&A

| Q&A | Áp dụng |
| --- | --- |
| No. 2 và 17 | DT2-6 đọc các đợt gọi và món của cùng phiên; không biến yêu cầu tính tiền thành một đợt gọi. Ghi order và giỏ hàng thuộc DT2-4. |
| No. 3 | Bill bắt đầu PENDING. Quản lý xác nhận đã nhận đủ tiền qua thao tác riêng rồi mới trả receipt PAID để in. Không nhận trạng thái thanh toán của khách làm bằng chứng. Socket báo thành công trước xác nhận cần producer thanh toán đã xác minh và giao kèo với nhóm tích hợp; chưa có trên nền hiện tại. |
| No. 4 | ADMIN mới được xem hóa đơn/doanh thu, xác nhận thu tiền và reset. STAFF vẫn xem bàn/món, tiếp nhận request và tạo bill PENDING theo SRS/Figma. UI không hiện mục Income cho Staff dù frame Staff trong prototype có mục này. |
| No. 5 và 15 | Không xóa cứng dữ liệu nghiệp vụ. Lấy giá đã mua từ order snapshot, lưu bill snapshot; thay giá/tên hiện hành không đổi hóa đơn cũ. |
| No. 9 | Tình trạng vận hành bàn và chiếm dụng là hai chiều. ACTIVE + không có phiên → Trống; có OPEN/CHECKOUT → Đang phục vụ; OUT_OF_SERVICE → Bảo trì/tạm ngừng. ARCHIVED không đưa lên sơ đồ. Không thêm enum/cột để tách bảo trì và tạm đóng. |
| No. 12 | UI báo lỗi mất kết nối, cho thử lại. Không báo thành công từ local state, không làm hàng đợi offline. |

## Hợp đồng API

[OpenAPI](../api/dt2-06.yaml). Các API dưới `/api/v1`, dùng bearer được module Auth ký và DB kiểm tra role/status; không phát token/tài khoản thử ở runtime. POST giữ CSRF mặc định: lấy token và tên header từ GET `/sales/csrf`, gửi token kèm cùng phiên cookie. UI tái sử dụng giao kèo `smart_cafe_token` hiện tại và account trả từ server.

| API | Quyền | Kết quả |
| --- | --- | --- |
| GET /sales/tables | STAFF, ADMIN | Bàn, tình trạng vận hành, chiếm dụng, phiên và số cảnh báo. |
| GET /sales/tables/{id}/services | STAFF, ADMIN | Dòng món chưa hủy, total, requests và mã/trạng thái bill hiện tại. |
| POST /sales/tables/{id}/requests/{requestId}/acknowledge | STAFF, ADMIN | `expectedSessionCode` bắt buộc; chỉ tiếp nhận đúng request/phiên. |
| POST /sales/tables/{id}/bill | STAFF, ADMIN | `expectedSessionCode` và `idempotencyKey`; tạo snapshot PENDING, 201/Location. Retry cùng key/payload đọc lại bill; STAFF không lấy hóa đơn đã hoàn tất qua replay. |
| POST /bills/{id}/payments | ADMIN | `method` CASH hoặc BANK_TRANSFER, `referenceCode` tùy chọn và `idempotencyKey`. Lấy số tiền từ bill, lưu payment và cập nhật PAID/paid_at cùng transaction; trả receipt. Không tích hợp cổng thanh toán. |
| POST /sales/tables/{id}/reset | ADMIN | `expectedSessionCode`; chỉ đóng phiên đã xác nhận đủ tiền và không còn món lệch snapshot. Thu hồi client phiên cũ, dọn request đúng phiên, không đổi trạng thái vận hành bàn. |
| GET /income | ADMIN | Tổng, số hóa đơn, trung bình, chuỗi ngày. Mặc định today. |
| GET /bills/{id} | ADMIN | Snapshot hóa đơn dùng khi thu tiền và in. |

- Tiền VND và ID JSON là chuỗi để giữ precision. Trung bình làm tròn về đồng theo HALF_UP. Biểu đồ sử dụng ngày Việt Nam, bao gồm ngày không doanh thu. Lọc báo cáo bằng `period=today|in_week|in_month|in_year` hoặc cặp `from/to` ISO date; không gửi đồng thời, tối đa một năm. Tuần bắt đầu thứ Hai. `paid_at` UTC, truy vấn [đầu ngày, đầu ngày kế tiếp), chỉ PAID; cùng quy ước view `v_daily_revenue`.
- Key opaque 1–64 ký tự ASCII không dấu cách; DB uniqueness và khóa phiên/bill bảo vệ ghi lặp. 400 dữ liệu không hợp lệ/đơn rỗng, 403 thiếu quyền, 404 không có resource, 409 phiên đổi/đơn thay đổi/key lệch, 503 DB không khả dụng.
- UI DT2-6: `/#sale` và `/#sale-income`; đi từ menu tài khoản Staff/Admin. Sơ đồ bàn và chi tiết làm mới định kỳ, thao tác theo đúng phiên. In từ snapshot PAID đã render; người dùng chủ động in/in lại. Lấy biểu đồ tuần riêng khi xem KPI hôm nay.

## Phụ thuộc và giới hạn

- Chưa có kết nối xác nhận tới DB hệ thống `smart_cafe` ở máy này. Kiểm thử trên MySQL 8.0.44 dùng cấu trúc `backend/src/test/resources/schema.sql` của PR #4, trong schema riêng `*_test`. Không thay DDL hoặc dữ liệu hệ thống. Khi triển khai cần DB owner đối chiếu DDL thực tế và cung cấp cấu hình runtime theo `backend/.env.example`.
- Auth ở PR #4 mới có giao kèo bearer, chưa nối đăng nhập thật. Không mở quyền tạm. Cần smoke với module Auth thật, producer order/session và socket sau khi tích hợp. Producer ghi/chỉnh món phải khóa cùng session để phối hợp an toàn với checkout. Nếu món thay đổi sau lập bill, API từ chối xác nhận/reset; quy trình hủy/tái lập bill, hoàn tiền và reset phiên chưa thu tiền còn chờ chốt.
- Phương thức CASH/BANK_TRANSFER là ghi nhận thủ công theo enum đã có trong schema; không xử lý QR/gateway hay nhận webhook. Không phát tín hiệu socket giả thay cho thanh toán thực.
- Chưa xác nhận in trên máy vật lý hoặc luồng đăng nhập thật. Playwright dùng HTTP mock để kiểm tra hành vi giao diện, không chứng minh full E2E.

## Kỹ năng và kiểm tra

- Maven `clean verify` với JDK 21 và MySQL 8.0.44 trên schema kiểm thử riêng đạt 28/28 test. Frontend lint, build và Playwright đạt 26/26 test trên desktop/mobile. Registry và 9 test định tuyến đạt.
- Ảnh sơ đồ bàn, hóa đơn và thu nhập chụp từ frontend chạy với API kiểm thử và được đính kèm trên issue DT2-6. Các ảnh minh họa giao diện bằng dữ liệu mẫu.


- Requirements: `cafe-requirements` (`.agents/skills/cafe-requirements/SKILL.md`), Figma `figma-design-to-code`, Google Sheets đọc Q&A.
- Backend: `cafe-spring-api`, `cafe-database` tại `.agents/skills/<id>/SKILL.md`: giao dịch, snapshot, khóa phiên, giữ schema.
- Frontend: `vercel-react-best-practices` tại `.agents/skills/react-best-practices/SKILL.md`, `fixing-accessibility` tại `.agents/skills/fixing-accessibility/SKILL.md`: hủy request khi rời view, xác nhận theo phiên, dialog native và in snapshot.
- Verification/review: `cafe-quality`, `cafe-review` tại `.agents/skills/<id>/SKILL.md`: regression về quyền, tiền, retry đồng thời, ngày Việt Nam và thay đổi phiên khi dialog mở. Chi tiết kết quả cuối ở PR.

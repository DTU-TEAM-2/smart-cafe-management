# DT2-1 — Header, Body, Footer

Owner: Duy Mạnh / NV-DuyManh. Cập nhật 09/11/2026. Bàn giao review trên nhánh feature/dt2-1-home-review; chưa merge hoặc chuyển Jira Done.

Cập nhật theo yêu cầu trực tiếp của chủ repo: đã [thêm dữ liệu trang chủ vào smart_cafe](2026-11-06-owner-requested-home-data.md), gồm 6 món có ảnh và 1 đợt gọi đủ quan hệ FK. API hiện trả đủ 5 món mỗi danh sách. Không có seed tự chạy hoặc toolbar xem thử trong ứng dụng.

## Nguồn và phạm vi

- [DT2-1 và các task con DT2-13 đến DT2-18](https://duymanhdnvn20092005.atlassian.net/browse/DT2-1), SRS v4.1 mục 6.3.1–6.3.3.
- [Figma của chủ repo](https://www.figma.com/design/7iFkUz20p6rE2fPC9Xn8rp/?node-id=10-70): Header nâu, LECKER., hero, ba ô câu chuyện, hai bảng top 5, Footer. Plugin Figma hết quota; đã đối chiếu bố cục qua browser, chưa xác nhận pixel-perfect/token chính thức.
- [Database hệ thống được chủ repo xác nhận](../architecture/database-contract.md). Runtime dùng `smart_cafe`, không nạp dữ liệu thay thế. Schema thực tế đã được đọc lại bằng SHOW CREATE; test fixture chứa cấu trúc, không chứa dữ liệu hệ thống.

Đã gỡ trang/thanh đăng nhập xem thử, profile Basic riêng và các seed dùng để trình diễn. Entry ứng dụng là `/`. Dữ liệu rỗng hiển thị rỗng, API lỗi hiển thị lỗi. Không tự tạo tài khoản, đơn hàng, số lượng hay ảnh món để lấp màn hình.

Theo yêu cầu chủ repo ngày 06/11, Header ghim ở đầu viewport khi cuộn trên desktop/mobile. Khoảng cuộn của anchor bù chiều cao Header theo breakpoint; menu/popup vẫn nổi trên nội dung, skip link bàn phím nổi trên Header. Đã kiểm sticky/anchor/menu ở 1440/768/390/320px; lint/build và 14 test UI đạt.

## API đọc thật

| Endpoint GET | Quyền và dữ liệu |
| --- | --- |
| /api/v1/home/highlights | Public; hai truy vấn MySQL cùng transaction read-only REPEATABLE READ |
| /api/v1/home/account | Token hợp lệ; hồ sơ của chính Principal đang ACTIVE; Cache-Control no-store |
| /api/v1/home/notifications | STAFF/ADMIN hiện hành ở DB; recipient lấy từ danh tính server; Cache-Control no-store |

Highlights: `{newest:[{id,name,price,imageUrl}], mostOrdered:[{id,name,orderedQuantity,imageUrl}]}`. ID/tiền/lượng là chuỗi thập phân, imageUrl nullable từ media chưa xóa. Không dùng ảnh cố định làm fallback cho một món không có ảnh.

- Newest: món và nhóm active, created_at DESC/id DESC, LIMIT 5.
- MostOrdered: view v_menu_item_order_totals, quantity > 0, quantity DESC/id ASC, LIMIT 5. View tổng quantity trừ CANCELLED, toàn thời gian; PO còn xác nhận quy tắc khoảng thời gian/available.
- Account: users LEFT JOIN staff_profiles/media_assets; chỉ thông tin chính mình, không trả password/hash hoặc nhận userId tùy chọn từ client. Tiền là chuỗi, thiếu dữ liệu là NULL.
- Feed: order_batches có dòng SUBMITTED và WAITER PENDING, trong phiên OPEN/CHECKOUT; tối đa 30 mục, ID ổn định order:id/waiter:id, timestamp UTC. Không tạo bảng notification mới. Staff/Admin của quán nhận yêu cầu; schema hiện không có phân công nhân viên theo bàn.
- Lỗi đọc DB trả 503 ProblemDetail đã lọc. Các API khác bị từ chối mặc định; CSRF giữ nguyên.

## Xác thực và luồng Header

HeaderTokenFilter chỉ tiêu thụ contract bearer hiện có của module auth đã tham khảo từ TokenService ở dự án trước: base64url của `id:username:role:expiryEpochMillis`, dấu chấm, chữ ký HMAC-SHA256 dạng hex. AUTH_TOKEN_SECRET phải trùng khóa thực của module auth, tối thiểu 32 byte. Header không phát hành token và không dựng tài khoản thay thế.

Server kiểm chữ ký/thời hạn, rồi đọc lại id/username/status/role/auth_version ở users; không cấp quyền theo role tự khai trong payload. Contract token cũ chưa mang version nên chỉ được dùng khi auth_version=0. Khi version đã tăng, token cũ bị từ chối; module auth phải cung cấp contract token có version trước khi dùng lại sau thu hồi. Không tự thay đổi schema/token issuer trong task Header.

Frontend entry đọc token `smart_cafe_token` do module đăng nhập đặt trong sessionStorage. Module auth phát `smart-cafe-auth-change` khi đổi phiên; Header remount state theo token, abort request/timer cũ. Có cập nhật khi storage/focus thay đổi. `HomeShell` cũng nhận Authorization trực tiếp để app auth tích hợp theo context.

Avatar mở trang #account dùng cùng SiteLayout. Feed tự lấy mỗi 5 giây, không chồng request; snapshot sai recipient bị loại, ID trùng được gộp. Lỗi có retry riêng, không kéo theo hai request do refetch identity. Thu hồi/khóa tài khoản xóa identity/feed khỏi Header. Dấu đã đọc chỉ ở bộ nhớ UI, không được coi là ACKNOWLEDGED/RESOLVED tại server.

DT2-2 chịu trách nhiệm đăng nhập/phát hành phiên, DT2-3 cập nhật hồ sơ/đổi mật khẩu, DT2-4 ghi order/service. Các module đó chưa có trong repo mới. Theo yêu cầu mới nhất ngày 07/11/2026, chỉ chuẩn bị [UI đăng nhập](dt2-1-login-ui.md), đã gỡ tài khoản và cơ chế mock. Khi tích hợp, phải smoke bằng tài khoản và sự kiện thực của các module đó.

## Bằng chứng hiện tại

- Maven verify: **8 test đạt, 0 skipped**, MySQL 8.0.46 riêng với fixture chụp từ DDL hệ thống. Bao phủ 0/<5/>5, tie, active/category, CANCELLED, Unicode, media NULL/xóa, bearer hợp lệ/sai/hết hạn/tamper, DB role/status/auth_version và own profile/recipient.
- npm lint/build: **PASS**; Playwright desktop/mobile: **14 test đạt**, kiểm lại ngày 09/11/2026. HTTP fixtures chỉ ở test tự động, không nằm trong entry sản phẩm.
- Browser → Vite → Spring → smart_cafe: HTTP 200. Sau yêu cầu nhập dữ liệu của chủ repo, hai danh sách trả đủ 5 món đúng thứ tự/quantity và URL ảnh. Chưa nghiệm thu login/order UI toàn hệ thống; không coi dữ liệu khởi tạo là giao dịch do khách thực hiện.
- Registry/checksum: 17 skills/16 routes/169 file hợp lệ; routing tests 9 đạt.
- SonarQube chưa cấu hình; Safari/iPhone vật lý, load production và toàn bộ login/order UI của module liên quan: **NOT RUN**.

Ảnh trang hiện hành: [desktop](evidence/dt2-1-desktop.png), [mobile](evidence/dt2-1-mobile.png). Xem [bảng từng task](dt2-1-acceptance.md). Không tự tick full AC/Done khi chưa có review và nghiệm thu tích hợp.

## Kiểm thử MySQL riêng

Chỉ đặt TEST_DB_URL tới database rỗng có hậu tố *_test; không trỏ smart_cafe. Test tạo dữ liệu trong transaction và rollback. Ví dụ PowerShell từ root, Docker đã chạy:

```powershell
docker run -d --rm --name cafe-home-test -p 127.0.0.1:53316:3306 -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=smart_cafe_home_test mysql:8.0
# Đợi MySQL ready trong docker logs cafe-home-test.
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Get-Content -Raw -Encoding UTF8 backend/src/test/resources/schema.sql | docker exec -i cafe-home-test mysql -uroot smart_cafe_home_test --default-character-set=utf8mb4
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:53316/smart_cafe_home_test?connectionTimeZone=UTC'
$env:TEST_DB_USERNAME='root'
Remove-Item Env:TEST_DB_PASSWORD -ErrorAction SilentlyContinue
cd backend
.\mvnw.cmd -B -ntp verify
cd ..
docker stop cafe-home-test
Remove-Item Env:TEST_DB_URL, Env:TEST_DB_USERNAME -ErrorAction SilentlyContinue
```

Skills thực tế theo phase: cafe-requirements, cafe-spring-api, cafe-database, vercel-react-best-practices (folder react-best-practices), fixing-accessibility, cafe-quality, cafe-review ở `.agents/skills/<folder>/SKILL.md`; host figma-design-to-code cho đối chiếu thiết kế/giới hạn công cụ. Tự review không thay approval độc lập.

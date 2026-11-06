# DT2-1 — Header, Body, Footer

Ngày: 06/11/2026. Owner: Duy Mạnh. Tự kiểm chứng → chờ review nhóm; chưa tick toàn bộ AC, chưa chuyển Jira Done.

## Nguồn và phạm vi

- [DT2-1](https://duymanhdnvn20092005.atlassian.net/browse/DT2-1) và DT2-13 đến DT2-18: layout chung, top 5 từ DB, avatar, vùng thông báo nhân viên/quản lý và responsive.
- SRS v4.1 mục 6.3.1–6.3.3, đối chiếu bản trích xuất local; không nhập toàn bộ tài liệu riêng.
- [Figma được chỉ định](https://www.figma.com/design/7iFkUz20p6rE2fPC9Xn8rp/?node-id=10-70): trang chủ phía trên frame hướng dẫn. Header nâu, LECKER., hero High Quality/COFFEE, ba ô câu chuyện, hai cột ranking, footer.
- Plugin Figma hết quota design context/screenshot. Đã đối chiếu bản hiển thị qua trình duyệt; chưa có token/font/asset gốc để xác nhận pixel-perfect. Các khối trang trí CSS mô phỏng placeholder nhìn thấy, không phải asset export. Cần designer xác nhận từ export/Dev Mode ở review.

Không thêm auth, đặt món, thanh toán, quản lý menu, tìm kiếm hoặc wishlist. Kính lúp dẫn tới danh sách món, trái tim dẫn tới món gọi nhiều. CTA Menu hiện dẫn tới danh sách trên trang, chờ route DT2-9. Tin tức và các liên kết footer chưa có route thì hiện nhãn chưa kết nối, không tạo link hỏng.

## API và schema contract

GET `/api/v1/home/highlights` public, chỉ đọc:

```json
{
  "newest": [{"id":"1","name":"Cà phê sữa","price":"35000"}],
  "mostOrdered": [{"id":"1","name":"Cà phê sữa","orderedQuantity":"3"}]
}
```

Ví dụ hợp đồng, không phải fallback. ID, tiền VND nguyên và quantity là chuỗi thập phân để không mất độ chính xác JavaScript. Mỗi danh sách tối đa 5; rỗng là `[]`. Hai truy vấn cùng transaction read-only REPEATABLE READ.

| Danh sách | Điều kiện/thứ tự |
| --- | --- |
| Mới nhất | Món và nhóm active; created_at DESC, id DESC; LIMIT 5 |
| Gọi nhiều | Món và nhóm active; quantity > 0; quantity DESC, id ASC; LIMIT 5 |

Quantity lấy từ view `v_menu_item_order_totals`, tổng `order_items.quantity` loại CANCELLED, toàn thời gian. Đây là diễn giải kỹ thuật theo schema hiện hành, **chờ PO xác nhận** quantity hay số lần gọi và khoảng thời gian. Không dùng doanh thu/paid quantity. Chưa lọc `available` vì schema ghi dự phòng chờ PO.

Fixture `backend/src/test/resources/schema.sql` lấy từ thiết kế local `database-design-20261106/01-schema.sql`, chỉ để kiểm contract MySQL. Giữ đủ bảng/FK để tạo đơn test hợp lệ; không có dữ liệu thật, không yêu cầu triển khai các module đó. Không nhập migration Flyway cũ. `spring.sql.init.mode=never`: ứng dụng không tự DDL/seed. Schema sử dụng thật do task database cung cấp và duyệt riêng.

Lỗi DB trả 503 ProblemDetail, không lộ SQL/credential cho browser. Các API khác bị từ chối mặc định, CSRF giữ nguyên. Chưa có endpoint account/notification hoặc xác thực trong PR.

## Điểm tích hợp Header

`SiteLayout` nhận children và các prop sau; chỉ khai báo một Header/Footer:

- `account`: `{ id, name, role, profileHref }`, từ session server của DT2-3; entry thật mặc định guest.
- `notifications`: `{ recipientId, items: [{ id, message, read, href }] }`, từ DT2-4 sau kiểm quyền server. UI hiện cho STAFF/ADMIN khi recipientId khớp account.id và gộp ID trùng. UI không thay thế authorization.
- `links`: login/news/contact/hours/support, đường dẫn nội bộ đã tồn tại. Thiếu đích thì không tạo link giả.

Harness trong `frontend/tests/` kiểm hợp đồng hiển thị, không được import vào entry production; không chứng minh đăng nhập hoặc realtime thật.

## Bằng chứng

| Kiểm tra local | Kết quả |
| --- | --- |
| Maven verify, Java 21, MySQL 8.0.46 container riêng | 3 test đạt, 0 skipped |
| 0/<5/>5 món, tie, món/nhóm ẩn, CANCELLED, Unicode | Đạt qua Spring/MockMvc/MySQL thật; transaction rollback |
| API 503 không lộ lỗi nội bộ, route ngoài allowlist | Đạt |
| npm lint/build | Đạt |
| Playwright Chromium desktop/mobile | 6 test đạt, HTTP mock/harness cho trạng thái UI, keyboard, hợp đồng Header |
| Browser → Vite → Spring → MySQL thật | Đạt ở 1440/390/320px, không tràn ngang/pageerror, fixture riêng |
| Registry/checksum/routing | 17 skills/16 routes/169 files hợp lệ, 9 test đạt |

Ảnh dữ liệu tổng hợp: [desktop](evidence/dt2-1-desktop.png), [mobile](evidence/dt2-1-mobile.png). Mobile là Chromium giả lập, chưa kiểm Safari/iPhone vật lý. Chưa có kiểm tải production/visual regression với token Figma chính thức. Một lần build bị khóa jar Windows do tiến trình test đang chạy; dừng tiến trình và chạy lại verify đã đạt.

## Kiểm thử MySQL riêng

Không dùng DB vận hành. Test yêu cầu tên kết thúc `_test`, menu_items rỗng và rollback dữ liệu sau test. PowerShell, port 53316 cần còn trống:

```powershell
docker run -d --rm --name cafe-home-test -p 127.0.0.1:53316:3306 -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=smart_cafe_home_test mysql:8.0
# Đợi mysqld ready trong docker logs cafe-home-test.
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

Container test dùng mật khẩu rỗng và chỉ bind loopback, không dùng cho production. CI import fixture qua redirect UTF-8 trên Linux.

## Chờ nghiệm thu

- Review chéo và xác nhận UI từ bản export Figma chính thức.
- DT2-3/DT2-4 cung cấp session/event thật rồi kiểm thông báo gọi món/phục vụ tới đúng nhân viên và quản lý end-to-end.
- Tích hợp các route thật dùng chung layout, kiểm không lặp Header/Footer. Hiện chỉ entry trang chủ và test harness.
- PO chốt ranking/available và đích route menu/footer. Không tick full AC hiện tại.

Skills đã đọc: `cafe-requirements` (nguồn/AC), `cafe-spring-api` (allowlist/transaction), `cafe-database` (test DB riêng), `vercel-react-best-practices` (fetch cleanup), `fixing-accessibility` (semantic/keyboard/focus), `cafe-quality` (case dữ liệu/lỗi), `cafe-review` (tự review, không thay review chéo). Path `.agents/skills/<folder>/SKILL.md`; vercel-react-best-practices ở folder `react-best-practices`. Host `figma-design-to-code` hướng dẫn lấy context và ghi giới hạn công cụ.

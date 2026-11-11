# Smart Cafe — backend thực đơn

JDK 21, Spring Boot 4.1.2, MySQL 8.0.16+ (CHECK constraints), JDBC/Flyway. Module được viết mới theo nguồn DT2-9 trong `docs/handover/`, không nhập migration history hay dữ liệu legacy. Nhánh backend tách từ main; FE ở nhánh `feature/dt2-9-manage-menu-items-and-categories-frontend` chưa được nối API.

## Chạy local

Tạo schema **mới, rỗng** `smart_cafe_menu` và cấp quyền cho user ứng dụng của bạn. Không trỏ vào database legacy hoặc production. Đặt `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` trong môi trường terminal; xem `.env.example` để biết tên biến. Spring không tự nạp `.env`.

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Linux/macOS: `bash ./mvnw spring-boot:run`. Maven wrapper tải Maven 3.10.0 nếu chưa có cache. Backend mặc định chỉ bind `127.0.0.1:8080`. `GET /actuator/health` public và không lộ chi tiết datasource.

Flyway chạy V001 tạo `menu_categories`, `menu_items`. Không seed tài khoản/món/nhóm, không `ddl-auto=update`, không `baselineOnMigrate` hoặc repair. V001 không được sửa sau khi đã áp dụng ở môi trường dùng chung; thay đổi sau đó thêm migration mới. Rollback phát hành cần backup/restore hoặc migration bù đã được review; không drop dữ liệu để rollback.

## Auth và CSRF

API `/api/v1/menu/**` yêu cầu `ROLE_ADMIN`. Không có login/user database, tài khoản giả hoặc bypass local. Cấu hình cả `MENU_JWT_ISSUER` (HTTPS issuer OIDC) và `MENU_JWT_AUDIENCE`; Spring xác thực chữ ký/JWK, issuer, audience và hạn token. `roles` phải là array chứa `ADMIN` từ issuer tin cậy, STAFF bị từ chối. Khi thiếu cấu hình, ứng dụng vẫn chạy nhưng từ chối mọi Bearer token. Cần module auth chung/IdP thực trước khi người dùng thao tác API.

Frontend giữ Bearer token theo cơ chế auth chung, gọi `GET /api/v1/menu/csrf`, giữ cookie session CSRF và gửi header có tên `headerName` với giá trị `token` cho POST/PUT/DELETE/upload. CSRF vẫn bật, không mở CORS tùy ý. Nên dùng reverse proxy cùng origin khi tích hợp. Cookie CSRF không thay thế Bearer token. Không gửi token trong query string.

## Hợp đồng và ảnh

Xem [OpenAPI](../docs/api/dt2-9-menu.yaml). Mã chuẩn hóa uppercase, bất biến; giá VND nguyên >5.000; version mới bằng 0. Xóa logic nhóm ẩn toàn bộ món trong cùng transaction, không xóa hàng dữ liệu. Tên tìm không phân biệt hoa/dấu theo MySQL collation; mã và giá tìm chính xác; ký tự `%`/`_` trong từ khóa được tìm literal. Món đã phân nhóm không đổi nhóm qua PUT theo SRS tham chiếu. `description`, `available` hỗ trợ trình bày FE, khác `active` nội bộ.

Upload `POST /api/v1/menu/images` multipart `file`: PNG/JPEG, tối đa 5 MB và 20 triệu pixel; kiểm tra nội dung trước khi gọi Cloudinary. Đặt ba biến `CLOUDINARY_*`; thiếu cấu hình trả 503, provider lỗi trả 502 đã làm sạch. Test không dùng key thật/không upload ra ngoài. URL lưu chỉ chấp nhận Cloudinary của cloud đã cấu hình hoặc `/images/...` an toàn; backend không tải URL do client nhập. `/images/...` là tương thích dữ liệu local, không có ảnh mẫu hoặc API lưu file local đi kèm.

Upload thành công nhưng hủy form/save thất bại có thể để asset chưa tham chiếu. Không tự xóa ảnh cũ vì có thể được lịch sử sử dụng; cleanup cần quy trình riêng.

## Kiểm tra

Tạo schema test riêng tên kết thúc `_test`, đặt `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`, sau đó:

```powershell
.\mvnw.cmd -B -ntp verify
```

Test kiểm tra tên schema **trước Flyway** và trước khi dọn dữ liệu; chỉ dọn hai bảng menu trong schema test. MySQL thật là bắt buộc, không dùng H2 thay thế. CI có MySQL service tạm. Fixture quyền dùng MockMvc/test JWT, không chứng minh đã đăng nhập hoặc chạy IdP thật. Unit upload giả lập provider, integration kiểm tra CRUD/validation/CSRF/quyền/locking/version. JAR production không có fixture test.

## Nguồn và giấy phép

Mã ứng dụng được viết lại. Hành vi tham chiếu legacy được truy xuất trong [nguồn yêu cầu](../docs/handover/dt2-9-backend-request.md); không nhận báo cáo/approval cũ thành kết quả mới. `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` là Apache Maven Wrapper 3.3.4 lấy nguyên từ tooling của legacy, giữ header Apache License 2.0; không phải mã nghiệp vụ mới. Bản giấy phép trong `MAVEN-WRAPPER-LICENSE.txt`. Dependencies giữ giấy phép upstream qua Maven artifacts.

Tham khảo kỹ thuật: [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [Cloudinary Upload API](https://cloudinary.com/documentation/image_upload_api_reference).

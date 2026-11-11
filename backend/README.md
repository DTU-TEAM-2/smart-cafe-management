# Smart Cafe — backend

JDK 21, Spring Boot 4.1.2, JDBC/MySQL. DT2-9 bổ sung quản lý món/nhóm món vào backend trang chủ hiện có; không có login, tài khoản mặc định hoặc seed.

## Chạy local

Database phải được chủ DB provision theo [mô hình và đối chiếu DDL](../docs/architecture/database-contract.md). DB local legacy hiện thiếu bảng/cột cần thiết; module không tự nâng cấp database. Không có Flyway, runtime DDL hay ddl-auto=update.

Đặt cấu hình trong môi trường hoặc backend/.env theo .env.example, chạy từ repo root:

```powershell
.\start-be.ps1
```

CMD: `powershell -ExecutionPolicy Bypass -File .\start-be.ps1`. Hoặc đặt biến môi trường rồi chạy `.\mvnw.cmd spring-boot:run` trong backend. Linux/macOS: `sh mvnw spring-boot:run`. Ví dụ truy cập local: http://127.0.0.1:8080. GET /actuator/health public, không lộ chi tiết datasource. API trang chủ giữ contract main.

## Auth và CSRF

/api/v1/menu/** chỉ dành cho ADMIN. Dùng HeaderTokenFilter và AUTH_TOKEN_SECRET của auth chung, tối thiểu 32 byte: token đã ký, chưa hết hạn, đúng id/username, users ACTIVE và auth_version=0. Quyền lấy từ users hiện tại, không lấy role từ payload. Thiếu cấu hình hoặc token hợp lệ thì từ chối; module không phát hành token.

Gọi GET /api/v1/menu/csrf bằng Bearer hợp lệ, giữ cookie session và gửi header theo headerName/token cho POST/PUT/DELETE/upload. Bearer được xác thực lại mỗi request; session chỉ giữ CSRF. Không mở CORS tùy ý; tích hợp nên dùng cùng origin.

## Món và ảnh

[OpenAPI](../docs/api/dt2-9-menu.yaml) và [testcase Postman](../docs/qa/dt2-9-postman-test-cases.md). Mã uppercase bất biến; giá VND nguyên >5.000, tối đa 18 chữ số theo decimal(18,0). Xóa logic nhóm ẩn các món trong cùng transaction; không tái sử dụng mã đã xóa. Version kiểm soát ghi đồng thời. Nhóm của món không đổi qua PUT theo nguồn SRS tham chiếu.

Persistence dùng PK số, category_id, image_media_id. Input nhận categoryCode và imageMediaId (null nếu không ảnh); backend tra FK. Response imageUrl dẫn xuất từ media_assets.secure_url. Không nhận description hoặc imageUrl do client nhập.

POST /api/v1/menu/images multipart file: PNG/JPEG ≤5 MiB, ≤20 triệu pixel; kiểm tra nội dung trước khi gửi Cloudinary. Cấu hình ba biến CLOUDINARY_*; thiếu cấu hình trả 503, provider lỗi trả 502 đã làm sạch. Thành công đăng ký metadata trong media_assets, trả mediaId và imageUrl. Dùng mediaId làm imageMediaId khi lưu món. Không nhận id đã xóa/không tồn tại. Không tự xóa asset cũ; upload xong nhưng hủy form hoặc DB thất bại có thể để asset chưa tham chiếu, cần quy trình cleanup riêng.

## Kiểm thử

Provision fixture src/test/resources/schema.sql nguyên trạng vào schema mới, riêng, kết thúc `_test`. Đây là fixture cấu trúc main, không phải migration triển khai. Đặt TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD rồi chạy:

```powershell
cd backend
.\mvnw.cmd -B -ntp clean verify
```

Test chặn URL ngoài *_test trước khi dọn fixture. MySQL thật, không H2. Report HTTP có 46 mục; TC-41/46 dùng provider HTTP loopback giả lập theo lựa chọn chủ repo, không cần key. [Báo cáo và cách chạy lại](../docs/reports/dt2-9-api/README.md) phân biệt kết quả tự động với Postman thủ công và smoke Cloudinary thật. Tài khoản/token test chỉ tồn tại trong fixture test riêng, không đóng gói JAR.

## Nguồn

Module menu viết lại theo [yêu cầu đã lưu](../docs/handover/dt2-9-backend-request.md), không nhập code nghiệp vụ, dữ liệu hoặc migration history legacy. Apache Maven Wrapper giữ header/giấy phép upstream tại MAVEN-WRAPPER-LICENSE.txt; backend chung/FE được tích hợp nguyên lịch sử main.

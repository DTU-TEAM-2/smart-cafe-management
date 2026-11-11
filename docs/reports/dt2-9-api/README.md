# DT2-9 — kết quả chạy API và báo cáo giao diện

Ngày 2026-11-11, nhánh `feature/dt2-9-manage-menu-items-and-categories-backend`. Mở [báo cáo HTML](index.html), [ảnh xem trước](report-preview.png), [ảnh toàn trang](report-full.png) hoặc [JSON kết quả](results.json).

## Kết quả mới

- 46 mục theo [testcase](../../qa/dt2-9-postman-test-cases.md): **46 PASS, 0 FAIL, 0 BLOCKED**. Gồm 109 phản hồi HTTP API thực tế và một ca DB read-only; một mục có thể chứa nhiều biến thể/request.
- HTTP thật bằng Java HttpClient → Tomcat 11.0.27 / Spring Boot 4.1.2 → MySQL 8.0.46 trong instance test loopback riêng; không dùng MockMvc cho bộ báo cáo này.
- ADMIN/STAFF dùng JWT RSA ký bằng key tạm và decoder chỉ có trong `src/test`. Có kiểm tra chữ ký, issuer, thời hạn và mapping role; không phải đăng nhập/IdP production, không chứng minh tích hợp issuer/audience thực. Key/token/cookie không được ghi vào báo cáo hoặc JAR production.
- Theo lựa chọn chủ repo ngày 2026-11-11, TC-41/46 chuyển sang **provider HTTP giả lập trong lớp test**, có nhãn riêng trên báo cáo. Không còn phụ thuộc key Cloudinary để chạy bộ 46 ca; không tuyên bố đã upload/gây lỗi trên Cloudinary thật.
- TC-41: HTTP multipart API → adapter production → provider HTTP loopback trả URL hợp lệ; kiểm tra POST/auth/multipart, tạo món lưu URL trả về rồi GET lại đúng URL. TC-46: provider loopback trả 503 và body riêng tư, API phải trả 502 `IMAGE_PROVIDER_ERROR`, không lộ body hoặc secret fixture. Không chỉ mock response của controller.
- `mvnw -o -B -ntp verify`: **23 JUnit tests, 0 failures/errors/skips**, gồm 22 test trước và một test điều phối các mục báo cáo. Không cộng 46 dòng báo cáo thành 46 JUnit tests.
- Registry check đạt; routing tests 4/4 đạt. Report render kiểm tra đủ 46 dòng, details mở bằng bàn phím, desktop 1280 px, mobile 390 px không tràn ngang toàn trang. Đây không phải audit WCAG đầy đủ.

## Lỗi phát hiện qua HTTP và sửa chữa

[Lượt trước khi chọn provider giả lập](before-provider-stub-results.json) có 44 PASS, 2 BLOCKED. Lượt mới thực thi hai ca theo phạm vi test đã được người dùng chọn; dữ liệu kết quả cũ được giữ nguyên để truy xuất. Smoke với Cloudinary thật nằm ngoài kết quả 46/46 này.

[Kết quả lượt đầu](first-run-results.json) có 41 PASS, 3 FAIL, 2 BLOCKED. TC-07 cho thấy POST với Bearer nhưng thiếu CSRF vẫn trả 201. TC-08/33 thất bại phụ thuộc vì nhóm đã bị tạo ở bước âm và nhóm thứ hai chưa được tạo. Không sửa kỳ vọng để che lỗi.

Resource Server của Spring tự miễn CSRF cho Bearer request; các test trước dùng `WithMockUser` nên không đi qua nhánh miễn này. Sau khi ép CsrfFilter kiểm tra toàn bộ unsafe method, HTTP tiếp tục lộ vòng đời CSRF/session và ERROR dispatch mà MockMvc không biểu diễn đầy đủ. Bản cuối:

- CSRF vẫn bắt buộc với Bearer; request thiếu/sai CSRF trả 403.
- JWT authentication stateless; giữ session chỉ để lưu CSRF. Không chạy chiến lược xoay CSRF trên mỗi lần xác thực lại cùng JWT; không thêm login hoặc session authentication.
- Cho phép container `DispatcherType.ERROR` dựng response lỗi, giữ status 403/413 thay vì chặn lại trang lỗi. Các request HTTP thông thường ngoài allowlist vẫn bị từ chối.
- Regression HTTP kiểm tra cả thiếu/sai CSRF và nhiều POST/PUT/DELETE liên tiếp với token/cookie hợp lệ, ngoài các ca CRUD/permission/validation/version/race/ảnh.

Đối chiếu nguyên nhân framework: [Resource Server CSRF override](https://github.com/spring-projects/spring-security/blob/7.1.2/config/src/main/java/org/springframework/security/config/annotation/web/configurers/oauth2/server/resource/OAuth2ResourceServerConfigurer.java), [CsrfAuthenticationStrategy](https://github.com/spring-projects/spring-security/blob/7.1.2/web/src/main/java/org/springframework/security/web/csrf/CsrfAuthenticationStrategy.java).

## Chạy lại

Chuẩn bị schema MySQL riêng kết thúc `_test` và môi trường `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` theo backend README. Lệnh từ repo root:

```powershell
cd backend
.\mvnw.cmd -B -ntp -Dtest=MenuHttpReportTest test
cd ..
python scripts/render-menu-api-report.py backend/target/api-report/dt2-9-api-results.json docs/reports/dt2-9-api/index.html
```

Renderer chỉ đọc kết quả test, không tự tạo PASS và không gọi API. HTML không cần npm/server hoặc tài khoản để mở. JSON chỉ lưu method/path/status/metadata, không lưu request headers hay response nhạy cảm. Test dùng mã fixture riêng mỗi lượt và dọn đúng các nhóm/món của lượt chạy sau hậu kiểm, trên schema test đã được guard trước Flyway. Đây là **kết quả tự động qua HTTP**, chưa thực thi thủ công trong ứng dụng Postman.

`MenuProviderStub` và client chuyển hướng chỉ ở `src/test`. Provider bind 127.0.0.1/cổng ngẫu nhiên và được Spring đóng sau test. Client test chỉ cho phép URI Cloudinary upload đã định trước, chuyển transport sang loopback; không gửi request ra Cloudinary thật. Cấu hình/key là fixture không phải credentials thật. TC-42 vẫn kiểm tra cấu hình thiếu trả 503. Production endpoint/client không thay đổi. JAR production được kiểm tra không chứa stub hoặc class test.

Skills: `cafe-quality`, `ui-ux-pro-max`, `cafe-spring-api`, `diagnosing-bugs` từ `.agents/skills/`; ảnh đính kèm là nguồn bố cục. Aikido MCP không có tool nên chưa có kết quả SAST. Self-review/automated tests không thay review độc lập hoặc smoke IdP/Cloudinary thật. Không push/deploy trong lượt này.

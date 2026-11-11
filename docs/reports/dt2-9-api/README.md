# DT2-9 — báo cáo HTTP sau tích hợp main

Ngày 11/11/2026; main tích hợp cf4828a. [HTML](index.html), [ảnh xem trước](report-preview.png), [ảnh toàn trang](report-full.png), [JSON](results.json).

- **46 PASS, 0 FAIL, 0 BLOCKED**, 109 phản hồi HTTP và một ca DB read-only. Một mục có thể gồm nhiều request.
- Java HttpClient → Tomcat/Spring Boot 4.1.2 → MySQL 8.0.46 thật trên instance loopback riêng, schema smart_cafe_merge_test. Fixture cấu trúc main giữ nguyên; không thay DB local legacy.
- Bearer HMAC test-only theo contract auth chung; users test quyết định role/status/auth_version. Không phải login hoặc phát hành token production.
- TC-41/46 dùng **provider HTTP loopback giả lập** theo lựa chọn chủ repo, không cần key, không gọi Cloudinary thật. Adapter production gửi multipart và đăng ký metadata media_assets; TC-41 tạo món tham chiếu mediaId rồi đọc URL dẫn xuất. TC-46 kiểm tra upstream 503 thành 502, không lộ body riêng tư.
- Maven clean verify: **30 JUnit tests, 0 failures/errors/skips**, gồm test điều phối 46 mục; không cộng các dòng báo cáo thành số JUnit tests.
- Hồi quy main: home/account/highlights/notifications, đổi role/khóa/revoke quyền menu; CSRF thiếu/sai và mutation liên tiếp; ảnh không tồn tại/đã xóa; khóa/version và xóa logic.
- Render kiểm tra 46 dòng, details mở bằng bàn phím, desktop 1280 px/mobile 390 px không tràn toàn trang. FE main lint/build và 14/14 Playwright đạt. Registry 17 skills/16 routes, 169 inherited files, routing tests 9/9 đạt.

## Chạy lại

Provision nguyên fixture backend/src/test/resources/schema.sql vào schema MySQL **mới riêng kết thúc _test**. Không import vào DB nghiệp vụ. Đặt TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD rồi từ repo root:

```powershell
cd backend
.\mvnw.cmd -B -ntp -Dtest=MenuHttpReportTest test
cd ..
python scripts/render-menu-api-report.py backend/target/api-report/dt2-9-api-results.json docs/reports/dt2-9-api/index.html
```

CMD có thể chạy cùng lệnh mvnw.cmd sau khi đặt các biến bằng set. HTML mở trực tiếp, không cần server/npm. Renderer chỉ đọc kết quả, không tạo PASS. JSON không lưu auth header/token/cookie. Fixture được dọn trên schema test sau hậu kiểm; JAR không chứa test classes/provider stub.

## Giới hạn và lịch sử

Đây là tự động qua HTTP, chưa chạy thủ công trong Postman hoặc smoke Cloudinary thật. DDL local legacy chưa khớp mô hình mới; xem [database contract](../../architecture/database-contract.md). Không migration/runtime DDL hay thay DB local. Constraint fixture không thay xác nhận DDL triển khai.

[Lượt đầu](first-run-results.json) và [trước provider giả lập](before-provider-stub-results.json) giữ nguyên dữ liệu lịch sử của bản độc lập dùng JWT. Bản hiện tại bỏ JWT riêng, tái sử dụng auth chung. Session chỉ giữ CSRF, không xoay CSRF mỗi lần xác thực Bearer; ERROR dispatch nội bộ được phép tạo response lỗi, route ngoài allowlist vẫn đóng.

Chưa có Aikido SAST vì không có tool khả dụng; self-review và checks tự động không thay review độc lập. Không deploy/merge.

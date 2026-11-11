# DT2-9 — bàn giao backend

Ngày 2026-11-11. Nhánh `feature/dt2-9-manage-menu-items-and-categories-backend`, tách từ main theo lựa chọn chủ repo; giữ nguyên nhánh FE. Chỉ lưu local, không publish/deploy/merge. Nguồn và tiêu chí: [yêu cầu backend](../handover/dt2-9-backend-request.md), [trích SRS legacy có SHA256](../handover/dt2-9-legacy-menu-source.txt).

## Kết quả

- Viết mới module Spring Boot/JDK 21/JDBC/MySQL cho món và nhóm món; version chống ghi đè, khóa nhóm ngăn ghi khi đang xóa, xóa logic nhóm/món theo transaction. Không lấy code ứng dụng, dữ liệu, migration history, account hoặc trạng thái hoàn thành cũ.
- Schema mới V001 với PK/FK/check/index; không seed, không tự sửa history hoặc repair. Công cụ Apache Maven Wrapper giữ header/giấy phép upstream, không nhận là mã mới.
- API ADMIN, JWT từ issuer/audience được cấu hình; thiếu cấu hình từ chối token, STAFF bị chặn, CSRF vẫn bắt buộc. Không có login hay giả lập tài khoản production. Chỉ health public; mặc định bind loopback.
- Upload PNG/JPEG kiểm tra nội dung, 5 MB/20 triệu pixel; Cloudinary HTTPS endpoint cố định, secret ở backend, timeout và lỗi provider đã làm sạch. Thiếu cấu hình trả 503.
- Có [OpenAPI](../api/dt2-9-menu.yaml), hướng dẫn [chạy backend](../../backend/README.md), CI backend với MySQL tạm. Chưa tích hợp FE; các khác biệt với bản FE demo được ghi trong nguồn yêu cầu và QA-06/07.

## Kiểm chứng mới đã chạy

Môi trường: JDK 21.0.13, Spring Boot 4.1.2, Maven 3.10.0, MySQL 8.0.46. Docker daemon không chạy nên tạo một MySQL instance tạm trong `.cache/`, chỉ bind `127.0.0.1:13369`; không kết nối database legacy. Các tài khoản/fixture kiểm thử chỉ dùng cho instance test hoặc trong test classes.

Lệnh backend (môi trường TEST_DB_* chỉ tới schema `smart_cafe_menu_test` riêng):

```powershell
cd backend
.\mvnw.cmd -o -B -ntp verify
```

Kết quả: **BUILD SUCCESS, 22 tests, 0 failures/errors/skips**: 11 integration MySQL, 8 upload provider-mock, 3 security configuration/role-mapping. Hai lỗi của lượt chạy đầu thuộc test: MockMvc giữ nguyên chuỗi `%20` khi truyền bằng URI, và Spring Security thêm authority `FACTOR_BEARER`; đã sửa cách assertion/truyền tham số, không nới quyền production.

Integration xác nhận CRUD/detail/search/filter/page, escaping wildcard/SQL input, code bất biến/case normalization, validation/name/price, unknown JSON fields, stale version, nhóm bất hoạt, xóa giữ row, atomic rollback khi version sai, cạnh tranh cùng version chỉ một writer thắng, lock nhóm chặn thêm món cho tới khi delete commit. Test kiểm tra `*_test` trước Flyway và trước cleanup.

Upload tests không gửi dữ liệu ra Cloudinary thật; kiểm tra PNG decode trước upload, giả MIME, file rỗng/quá lớn/pixel bomb, provider thiếu cấu hình/lỗi/URL không tin cậy và endpoint/auth multipart cố định. Security tests dùng MockMvc test principal/JWT và xác minh decoder mặc định từ chối token, không phải bằng chứng login/IdP thật.

JAR production đã chạy HTTP thật trên schema rỗng `smart_cafe_menu_smoke`: V001 thành công; health 200/UP; items/categories/csrf không token đều 401; Bearer sai 401. Schema smoke có 0 nhóm/0 món/1 migration thành công, không có seed/account. Archive kiểm tra không chứa test/preview classes, có V001.

Root: `node scripts/skills.mjs check` đạt (17 skills/16 routes), `node --test scripts/skills.test.mjs` đạt 4/4, whitespace authored files và `git diff --check -- . ':(exclude).agents/skills/**'` được kiểm tra. Trích SRS giữ nguyên whitespace từ nguồn, với attribute riêng `-text -whitespace`; không sửa nội dung trích chỉ để qua whitespace check. OpenAPI YAML parse, 6 paths/74 local `$ref` được kiểm tra; không tuyên bố đã chạy full OpenAPI validator. CI được thêm nhưng chưa chạy trên GitHub. Sau smoke, đã dừng JAR và MySQL instance tạm; cache/data test không nằm trong Git.

## Review và giới hạn

Self-review theo `cafe-review` chưa phát hiện blocker còn lại trong phạm vi đã kiểm chứng; chưa thay thế teammate review. FE chưa gọi API, chưa có login/IdP/audience thực hoặc Cloudinary credentials, nên chưa smoke upload thật/ADMIN thật. Không kiểm tra order/bill/doanh thu vì chưa triển khai. Giá menu không tự thay giá lịch sử; các module order sau này phải snapshot giá/tên riêng. Upload không tự cleanup asset chưa tham chiếu sau hủy form.

Aikido MCP không có tool trong phiên nên chưa chạy Aikido SAST; kiểm thử và self-review không thay cho kết quả scan. Tham khảo triển khai: [Spring JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [Cloudinary Upload API](https://cloudinary.com/documentation/image_upload_api_reference).

Skills thực sự đọc tại `.agents/skills/`: `cafe-workflow/SKILL.md` (phases/handoff), `cafe-requirements/SKILL.md` (nguồn/AC), `cafe-spring-api/SKILL.md` (DTO, authority, transaction), `cafe-database/SKILL.md` (schema/migration/integrity), `cafe-quality/SKILL.md` (test theo rủi ro), `cafe-review/SKILL.md` (self-review). Skill `aikido:scan` của plugin được đọc để xác định cách scan và giới hạn công cụ. Không dùng sub-agent và không nhập báo cáo test/approval cũ làm bằng chứng mới.

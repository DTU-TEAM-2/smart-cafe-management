# DT2-4 — Kiểm thử Backend bằng Postman Collection

## Kết quả thực tế

- [Lần chạy GitHub Actions](https://github.com/DTU-TEAM-2/smart-cafe-management/actions/runs/42976528804), job `Customer Menu Postman HTTP`, ngày 11/11/2026.
- Revision kiểm thử: `495e9db` (SHA đầy đủ trong `results.json`).
- **16/16 request, 31/31 assertion PASS, 0 failure**.
- MySQL bật `NO_BACKSLASH_ESCAPES` trước khi backend khởi động; thay đổi chỉ áp dụng service MySQL test của job, không thay SQL mode DB hệ thống.
- Runner: **Newman 6.2.1**, thực thi Postman Collection v2.1. Đây là ảnh báo cáo kết quả chạy collection, **không phải ảnh ứng dụng Postman Desktop**.
- Môi trường: GitHub Actions Ubuntu, JDK 21, Spring Boot 4.1.2/Tomcat thật trên loopback, MySQL 8.0 trong service riêng `smart_cafe_postman_test`.
- Chỉ dùng dữ liệu menu synthetic từ `docs/qa/postman/dt2-4-menu-test-fixture.sql` trong DB mới biệt lập. Không sửa hoặc seed DB hệ thống. Schema từ fixture kiểm thử hiện có; không kết luận đã đối chiếu DDL production.
- JSON rút gọn chứa request, status, thời gian, response body và assertion được trích nguyên dữ liệu từ Newman JSON của CI, không chứa headers/cookie/credential. `results.json` có timestamp, revision và run URL để đối chiếu. Artifact `dt2-4-postman` trong lần chạy trên lưu Newman JSON gốc.
- `request-urls.json` lưu nguyên method, URL đầy đủ (gồm query parameters) và status từ log CLI của cùng job; đã đối chiếu đúng thứ tự cả 16 request trước khi dựng ảnh.

## Ảnh theo nhóm API

### Danh mục

![Kết quả Postman Collection — danh mục](01-categories.png)

### Danh sách, nhóm món, danh mục rỗng, tìm kiếm, trạng thái phục vụ và phân trang

![Kết quả Postman Collection — danh sách và lọc](02-list-filter.png)

### Chi tiết món

![Kết quả Postman Collection — chi tiết món](03-detail.png)

### Lỗi và giới hạn quyền ghi

![Kết quả Postman Collection — 400, 404 và 403](04-errors.png)

## Tái hiện

Workflow `.github/workflows/dt2-4-postman.yml` tạo service MySQL biệt lập, kiểm tra tên DB trước khi import cấu trúc và dữ liệu test, package backend, chạy server rồi thực thi:

```sh
npx --yes newman@6.2.1 run docs/qa/postman/dt2-4-menu-api.postman_collection.json \
  -e docs/qa/postman/smart-cafe-local.postman_environment.example.json \
  --env-var baseUrl=http://127.0.0.1:18084 \
  --timeout-request 10000 --timeout 120000 \
  -r cli,json --reporter-json-export backend/target/postman/newman.json
```

`scripts/render-customer-menu-postman.py` chuyển Newman JSON thành báo cáo JSON/HTML và giữ nguyên trạng thái thực tế. Bốn PNG là ảnh toàn trang của các HTML tương ứng, chụp bằng Playwright CLI với Edge. Chỉ dùng browser để chụp báo cáo, không xây dựng frontend ứng dụng.

```sh
python scripts/render-customer-menu-postman.py docs/reports/dt2-4-postman/results.json docs/reports/dt2-4-postman
npx --yes playwright@1.64.1 screenshot --channel msedge --full-page --viewport-size "1440,1000" file:///ABSOLUTE_PATH/01-categories.html 01-categories.png
```

## Giới hạn

Chỉ chứng minh chức năng Menu trên fixture MySQL test. Không có ca Postman cố tình làm mất DB để kiểm tra 503 trong lần chạy này; lỗi DB có test controller riêng, không cộng vào 31 assertion Newman. Các luồng QR/phiên, order, chỉnh/hủy, phục vụ, thanh toán, feedback và upload chưa được chứng minh bởi báo cáo này. Nguồn AC/DDL production còn thiếu vẫn cần chủ phụ trách đối chiếu trước nghiệm thu.

## Regression P2 — SQL mode và tìm ký tự đặc biệt

- Trước bản sửa: regression trên commit `c17c1f0` tái hiện MySQL 1210 `Incorrect arguments to ESCAPE`, HTTP 503 thay vì 200 khi bật `NO_BACKSLASH_ESCAPES`. [CI đỏ, attempt 2](https://github.com/DTU-TEAM-2/smart-cafe-management/actions/runs/42976135156/attempts/2), job Backend MySQL `130376008759`. Lượt đầu lỗi kết nối MySQL khi nạp schema, không được tính là tái hiện lỗi tìm kiếm.
- Sửa tại `495e9db`: dùng `ESCAPE '!'`, escape `!`, `%`, `_`; giữ dấu backslash như ký tự thường, thống nhất cách catalog quản trị xử lý.
- [CI xanh sau sửa](https://github.com/DTU-TEAM-2/smart-cafe-management/actions/runs/42976528798): Maven **60/60**, 0 failure/error/skip; `CustomerMenuMySqlTest` 3/3 đạt. Hai regression mới kiểm tra cả tên và mã món, tìm từng ký tự `%`, `_`, `!`, `\` và chuỗi kết hợp, ở mode thường và `NO_BACKSLASH_ESCAPES`. Session SQL mode được khôi phục trong `finally` trước khi trả connection về pool.
- Newman strict mode: **16/16 request, 31/31 assertion**, xem ảnh/JSON trên. Không đổi schema hoặc runtime profile của module khác.
- PR #7 chưa được tích hợp vào branch này; cần chạy lại Newman trên mã đã gộp nếu tích hợp PR #7 vì profile khởi động có thể thay đổi.

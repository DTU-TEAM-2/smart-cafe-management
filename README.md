# Smart Cafe — LECKER

Nhánh `main` chứa bộ quy tắc và skill phát triển Smart Cafe. Nền tảng được tích hợp qua [PR #1](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/1).

## Phạm vi PR nền tảng

- Giữ 17 skill, registry, bộ định tuyến và luồng chuyển phase.
- Giữ quy tắc làm việc nhóm, đặt tên, Git/PR, chất lượng và bàn giao.
- Không nhập code ứng dụng, migration, dữ liệu, credential, kết quả test ứng dụng hoặc lịch sử PR từ repo cũ.
- Giữ attribution, license và manifest của skill kế thừa; không tự nhận nội dung bên thứ ba là sản phẩm mới.

## Bắt đầu lại từ đâu

1. Đọc bộ quy tắc và chọn skill phù hợp với task.
2. Thu thập SRS/project plan hiện hành vào `docs/handover/`; xác nhận phạm vi và các câu hỏi nghiệp vụ.
3. Tạo backlog với owner, tiêu chí nghiệm thu, phụ thuộc và reviewer; không chuyển task cũ sang Done tự động.
4. Chốt mô hình dữ liệu, quyền, hợp đồng API và quyết định kiến trúc trước khi triển khai phần phụ thuộc.
5. Làm từng task trên nhánh ngắn hạn từ `main`, kiểm chứng theo rủi ro, review rồi merge qua PR.

Kiểm tra nền tảng:

```sh
node scripts/skills.mjs check
node --test scripts/skills.test.mjs
```

## Chạy DT2-1 trên Windows

**Công nghệ bắt buộc:** BE **Spring Boot**, FE **ReactJS**, database **MySQL** theo [rule công nghệ](.agents/rules/technology-constraints.md). Các công nghệ khác là tùy chọn theo task; không thay thế ba thành phần này.

Triển khai hiện tại dùng React/Vite + Spring Boot/JDBC + MySQL. Cần Java 21, Node 22.19+ (nhánh 22) hoặc Node 24+, MySQL 8.0.16+. Đây là yêu cầu chạy bản hiện tại, không phải version bắt buộc mới từ mentor. Xem [phạm vi/API/bằng chứng](docs/handover/dt2-1-home-layout.md).

Database `smart_cafe` phải có schema nhóm đã duyệt: `menu_items`, `menu_categories`, view `v_menu_item_order_totals`. Máy mới nhận script schema từ task database rồi nhập vào database mới bằng Navicat trước khi chạy backend. Backend **không tự tạo database/schema hoặc món mẫu**. Fixture trong test chỉ dùng kiểm thử, không phải migration production.

Tại thư mục gốc:

```powershell
Copy-Item backend/.env.example backend/.env
notepad backend/.env
```

Sửa cấu hình theo tài khoản MySQL trên máy bạn; không dùng nguyên mật khẩu mẫu:

```dotenv
DB_URL=jdbc:mysql://127.0.0.1:3306/smart_cafe?connectionTimeZone=UTC
DB_USERNAME=smart_cafe_app
DB_PASSWORD="your-local-password#"
SERVER_PORT=8080
```

`start-be.ps1` đọc các biến trên và `AUTH_TOKEN_SECRET` từ `backend/.env`; biến đã đặt trong terminal có ưu tiên. AUTH_TOKEN_SECRET phải trùng khóa của module đăng nhập thật, không tự sinh token/tài khoản trong task Header. Dấu `#` trong giá trị được giữ nguyên; nháy đơn/kép đều được. Comment nằm trên dòng riêng. `.env` không lên Git. Chạy Maven/IDE trực tiếp cần cấp biến môi trường tương đương, Spring không tự đọc file `.env` này.

Terminal thứ nhất, tại thư mục gốc:

```powershell
.\start-be.ps1
```

Terminal thứ hai:

```powershell
cd frontend
npm ci
npm run dev
```

Mở địa chỉ Vite in ra, mặc định [localhost:5173](http://localhost:5173). Proxy API mặc định port 8080. Nếu đổi port backend, đặt `VITE_API_PROXY_TARGET=http://127.0.0.1:PORT` trong `frontend/.env.local`. Không đặt credential MySQL vào `VITE_*`.

Navicat kết nối bằng host/port/tài khoản tương ứng. Máy khác dùng được nếu cùng schema MySQL và đổi cấu hình; PostgreSQL/schema khác không tương thích tự động. Database rỗng có schema trả danh sách rỗng; thiếu schema/sai kết nối trả 503, UI cho thử lại, không trả món mock.

## Kiểm chứng ứng dụng

```powershell
cd frontend
npm run lint
npm run build
npx playwright install chromium
npm run test:ui
```

Backend: `cd backend` rồi `.\mvnw.cmd verify`. Thiếu `TEST_DB_URL` sẽ bỏ qua các test MySQL; **không coi đó là đã kiểm thử database**. [Cách chạy test MySQL riêng](docs/handover/dt2-1-home-layout.md#kiểm-thử-mysql-riêng).

CI bổ sung Backend MySQL và Frontend UI cùng Governance/PR title. Header đọc tài khoản và thông báo thật từ DB; đăng nhập production và nơi tạo order/service thuộc task liên quan. Quy trình: [AGENTS](AGENTS.md), [CONTRIBUTING](CONTRIBUTING.md), [skills](SKILLS_INSTALLED.md).

## Tích hợp tài khoản và thông báo

Trang sử dụng chính là `/`; nút Đăng nhập mở `#login`. Hiện chỉ chuẩn bị UI, chưa nối backend đăng nhập; nút submit chưa khả dụng. Không có tài khoản mẫu, chọn vai trò hoặc cơ chế đăng nhập giả. Xem [bàn giao UI đăng nhập](docs/handover/dt2-1-login-ui.md).

Với xác thực thật, `HomeShell` nhận token từ module đăng nhập; entry hiện tại đọc key `smart_cafe_token` trong sessionStorage theo hợp đồng của module cũ. Khi login/logout đổi token, module auth phát sự kiện `smart-cafe-auth-change` để Header xóa state cũ ngay. Người phụ trách auth tích hợp xác thực/phân quyền trong task riêng.

Server kiểm chữ ký/HMAC và thời hạn theo hợp đồng bearer của module đăng nhập, rồi đọc lại username, role, status, auth_version từ users. Payload tự nhận ADMIN không cấp quyền ADMIN. Token sai/hết hạn, user khóa/thu hồi bị từ chối. Chưa đăng nhập thì avatar hiển thị trạng thái khách; không tạo người dùng thay thế.

Database hệ thống hiện chưa có món/tài khoản/order/service thì UI hiển thị rỗng đúng thực tế. Không nạp dữ liệu thử vào smart_cafe để làm đầy màn hình. Xem [hợp đồng DB](docs/architecture/database-contract.md) và [nghiệm thu DT2-1](docs/handover/dt2-1-acceptance.md).


## Backend DT2-9: món và nhóm món

API ADMIN cho CRUD, tìm kiếm, xóa logic, optimistic locking và upload ảnh tham chiếu media_assets. Dùng auth chung và mô hình DB mới; không tự DDL/seed. Xem [hướng dẫn backend](backend/README.md), [OpenAPI](docs/api/dt2-9-menu.yaml), [testcase Postman](docs/qa/dt2-9-postman-test-cases.md) và [báo cáo HTTP](docs/reports/dt2-9-api/index.html). DDL local legacy chưa khớp sơ đồ; không trỏ module này vào DB cũ để chạy thử.
## Xem thử FE quản lý món/nhóm món DT2-9

Sau khi chạy Vite, mở `/preview/menu` (ví dụ `http://127.0.0.1:5173/preview/menu`). Dữ liệu minh họa chỉ ở bộ nhớ; tải lại khôi phục mẫu. Các màn trang chủ/bán hàng tại đường dẫn gốc giữ nguyên. Xem [hướng dẫn FE](frontend/README.md) và [báo cáo tích hợp develop](docs/reports/dt2-9-frontend/develop-integration-2026-11-13.md). Menu preview chưa nối API hoặc Cloudinary thật.

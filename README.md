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

React/Vite + Spring Boot/JDBC + MySQL. Cần Java 21, Node 22.19+ (nhánh 22) hoặc Node 24+, MySQL 8.0.16+. Xem [phạm vi/API/bằng chứng](docs/handover/dt2-1-home-layout.md).

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

`start-be.ps1` đọc bốn biến này từ `backend/.env`; biến đã đặt trong terminal có ưu tiên. Dấu `#` trong giá trị được giữ nguyên; nháy đơn/kép đều được. Comment nằm trên dòng riêng. `.env` không lên Git. Chạy Maven/IDE trực tiếp cần cấp biến môi trường tương đương, Spring không tự đọc file `.env` này.

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

Backend: `cd backend` rồi `.\mvnw.cmd verify`. Thiếu `TEST_DB_URL` sẽ bỏ qua hai test MySQL; **không coi đó là đã kiểm thử database**. [Cách chạy đủ ba test với MySQL riêng](docs/handover/dt2-1-home-layout.md#kiểm-thử-mysql-riêng).

CI bổ sung Backend MySQL và Frontend UI cùng Governance/PR title. Chưa có đăng nhập hoặc sự kiện thông báo thật; xem các phụ thuộc trong handoff. Quy trình: [AGENTS](AGENTS.md), [CONTRIBUTING](CONTRIBUTING.md), [skills](SKILLS_INSTALLED.md).

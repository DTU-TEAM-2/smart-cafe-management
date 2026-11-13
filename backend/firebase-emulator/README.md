# Firebase Storage local cho mọi thành viên

Cách miễn phí mặc định để một máy mới clone về phát triển Task 7: chạy **Firebase Storage Emulator trên máy đó**. Không cần tài khoản Google của tác giả, Firebase login, API key, service-account, bucket cloud hoặc billing. Chủ repo cấm tuyệt đối bật thanh toán/Blaze/trial cần thẻ. Mỗi máy có DB/ảnh local riêng; clone source không tự chia sẻ ảnh giữa các máy. Cách dùng backend dev chung và giới hạn cloud ở [hướng dẫn Firebase của nhóm](../../docs/handover/dt2-7-firebase-setup.md).

## 1. Chuẩn bị máy và DB

Cần Java 21+, Node 22.19+ (22.x) hoặc Node 24+, MySQL 8.0.16+. Lần đầu `npm ci` và Firebase tải runtime cần internet. Không cần cài Firebase CLI global: package-lock ở thư mục này cố định Firebase CLI 15.35.0 và các dependency.

Dùng **database riêng tên kết thúc `_test`** (ví dụ `smart_cafe_local_test`), nhận schema từ người phụ trách DB. Với một DB `_test` mới và rỗng, fixture cấu trúc `backend/src/test/resources/schema.sql` đã được đối chiếu và chứa hai cột audience được duyệt; không nhập file này vào database hệ thống, không chạy lại ALTER audience sau fixture. Không dùng `smart_cafe` trong chế độ emulator. Với fixture đã nhập thủ công vào DB thử riêng, đặt `$env:SPRING_FLYWAY_ENABLED = "false"` trong terminal chạy backend để dùng fixture không có Flyway history. Các môi trường có Flyway dùng quy trình DB tương ứng trong [bàn giao DT2-2](../../docs/handover/dt2-2-verified-database-integration.md).

## 2. Cài và chạy Storage

Terminal A, từ thư mục gốc repo:

```powershell
cd backend/firebase-emulator
npm ci
npm run check
npm start
```

Đợi CLI in `All emulators ready`. Storage mặc định `127.0.0.1:19199`; hub `19400`, logging `19450`, đều loopback. `start.mjs` tự tìm config/dependency theo thư mục của chính nó, không có đường dẫn ổ đĩa của tác giả. Chỉ chạy Storage của project local `demo-smart-cafe-news`, không đăng nhập hay chạm project cloud.

## 3. Cấu hình và chạy backend

Terminal B mới, từ thư mục gốc repo:

```powershell
Copy-Item backend/firebase-emulator/env.example backend/.env.emulator
notepad backend/.env.emulator
```

Sửa `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` theo MySQL riêng của bạn, và `AUTH_TOKEN_SECRET` ít nhất 32 byte UTF-8, khớp module DT2-2 dùng cùng DB. Không dùng token/secret mẫu cố định. Giữ `SPRING_PROFILES_ACTIVE=firebase-emulator`, endpoint `NEWS_FIREBASE_EMULATOR_HOST` khớp cổng Storage, không cần điền cloud bucket/key.

```powershell
.\start-be.ps1 -EnvFile backend/.env.emulator
```

Script đọc cả profile/endpoint từ file đã chọn. Đường dẫn `-EnvFile` tương đối tính từ repo, nên có thể gọi script từ thư mục khác; đường dẫn có khoảng trắng được hỗ trợ. Biến môi trường đã có trong terminal được ưu tiên, vì vậy dùng terminal mới hoặc bỏ biến cũ nếu trước đó chạy mode khác. Script không sửa file cấu hình và khôi phục biến môi trường khi thoát.

macOS/Linux hoặc chạy Java trực tiếp: export các biến trong `env.example` vào process (không tự `source` dotenv với giá trị không được shell-quote); từ `backend` chạy `./mvnw spring-boot:run`. Spring/IDE không tự đọc `.env`. Không dùng đường dẫn Windows trong cấu hình máy khác.

## 4. Auth và frontend

Task 7 tiêu thụ issuer **DT2-2 thật**. Auth DT2-2 đã có trong bản tích hợp với develop. Nếu dùng Auth riêng, hai backend cần cùng DB `_test` và signing secret. Cấp account phát triển theo quy trình của module auth trong DB riêng; không copy account/password hệ thống hoặc tự ký token để thay login.

Terminal C, từ repo:

```powershell
Copy-Item frontend/.env.example frontend/.env.local
cd frontend
npm ci
npm run dev
```

Trong `frontend/.env.local`, điền URL BE của máy bạn, ví dụ:

```dotenv
VITE_API_PROXY_TARGET=http://127.0.0.1:8080
VITE_AUTH_PROXY_TARGET=http://127.0.0.1:18081
```

Dòng auth chỉ cần khi DT2-2 chạy riêng tại cổng đó; nếu chung BE thì bỏ dòng này. Không đưa signing secret/Google credential vào VITE. Mở URL Vite in ra, đăng nhập ADMIN thật của DB phát triển, mở Tin tức, upload PNG/JPEG và đăng bài. STAFF/CUSTOMER chỉ đọc bài đã đăng đúng nhóm. Ảnh vẫn qua bearer API và blob URL như cloud; không gọi trực tiếp emulator từ frontend.

## 5. Giữ ảnh khi khởi động lại

`npm start` tự export Storage khi **Ctrl+C một lần và đợi thoát hoàn toàn**, rồi import ở lần chạy tiếp theo từ `.local-data/` (Git bỏ qua). Giữ thư mục này cùng database `_test` tương ứng; DB lưu media ID/object name, emulator giữ bytes. Chỉ copy DB sang máy khác sẽ thiếu ảnh. Khi chuyển bộ dữ liệu dev, bàn giao cả DB riêng và export ảnh qua kênh được phép, không đưa account/ảnh/export vào Git. Có thể bắt đầu DB và ảnh mới trên từng máy.

Không tắt cưỡng bức tiến trình nếu muốn giữ ảnh mới nhất. Nếu máy crash hoặc chưa export, DB có thể còn metadata nhưng thiếu object; khôi phục export khớp DB hoặc upload lại ảnh, không đổi quyền/seed hệ thống để chữa lỗi. Launcher từ chối thư mục dữ liệu không rỗng nhưng thiếu export hợp lệ, không tự xóa dữ liệu đó.

## Xử lý cấu hình

| Hiện tượng | Kiểm tra |
| --- | --- |
| `npm run check` báo thiếu Java/dependency | Java 21+ trên PATH, đúng Node, chạy `npm ci` trong `backend/firebase-emulator`. |
| Cổng bận | Dừng emulator cũ của bạn hoặc đổi cổng trong `firebase.json` và `NEWS_FIREBASE_EMULATOR_HOST` tương ứng; giữ loopback. |
| BE từ chối profile | `DB_URL` phải trỏ DB riêng kết thúc `_test`; endpoint HTTP `127.0.0.1`, có port, không userinfo/path/query/fragment. |
| Upload 503 | Emulator đã ready chưa, profile/endpoint đã được launcher đọc chưa, terminal có biến cũ ghi đè không. |
| Login không có endpoint | Cần DT2-2 thật và proxy đúng; emulator Storage không phải Firebase Authentication. |
| 403/404 ảnh | Kiểm token, role, audience, trạng thái bài/link; ảnh mới chưa gắn bài chỉ ADMIN đọc. Nếu có quyền mà object thiếu, kiểm export/DB có cùng bộ dữ liệu. |

Adapter dùng credential rỗng, bucket local cố định `demo-smart-cafe-news.appspot.com`, không dùng ADC/key cloud. Rules từ chối SDK client trực tiếp; backend vẫn kiểm role/audience. Live adapter mặc định bị tắt bởi chính sách miễn phí, không bật để chữa 503. Emulator không mô phỏng IAM/billing và không chứng minh cloud đã chạy. Xem [giới hạn Firebase Emulator](https://firebase.google.com/docs/emulator-suite/connect_storage) và [import/export chính thức](https://firebase.google.com/docs/emulator-suite/install_and_configure#export_and_import_emulator_data).

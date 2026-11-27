# Task 7 — cấu hình Firebase cho máy mới và môi trường chung

Mục tiêu: người clone source có thể cấu hình và chạy ảnh tin tức mà không phụ thuộc ổ đĩa, Firebase login hoặc key của tác giả. Chủ repo nhắc lại ngày 12/11/2026: **cấm tuyệt đối bật thanh toán, liên kết thẻ, nâng Blaze hoặc trial yêu cầu thanh toán; chỉ dùng miễn phí**. Không hỏi bật billing lại. Cấu hình dùng môi trường, không sửa Java/React cho từng người.

| Nhu cầu | Cách dùng | Credential Firebase trên máy người clone |
| --- | --- | --- |
| Phát triển độc lập, miễn phí | Storage Emulator + MySQL `_test` riêng trên từng máy | Không cần. Làm theo [quickstart local](../../backend/firebase-emulator/README.md). |
| Cùng xem một bộ tin/ảnh | FE gọi cùng BE đã được nhóm cấu hình, BE dùng cùng DB và kho ảnh | Không cần trên FE. Chỉ BE giữ credential nếu dùng cloud. Backend dùng emulator thì vẫn chỉ là môi trường dev. |
| Firebase Storage cloud | Không sử dụng theo ràng buộc miễn phí hiện tại; adapter bị tắt mặc định | Không cấp, lấy hoặc chia sẻ key để mở cloud. |

**Clone không tự cấp quyền cloud, không tự mang ảnh hoặc account sang máy mới.** Project `smart-cafe-management-47578` hiện là Spark và console yêu cầu Blaze cho Storage; chưa có bucket dùng chung được nghiệm thu. Không điền tên bucket tưởng tượng để báo đã tích hợp. Với yêu cầu không thanh toán hiện tại, nhóm có thể chạy đầy đủ upload/đọc ảnh local bằng emulator. Không khẳng định Spark của project này cung cấp Storage cloud miễn phí.

## Chạy local sau clone

1. Chuẩn bị Java 21+, Node theo `package.json`, MySQL riêng theo schema được duyệt; không tự ALTER/seed DB hệ thống.
2. [Quickstart local](../../backend/firebase-emulator/README.md) có cấu hình mẫu, `npm ci`, `npm start`, lệnh BE/FE và auth DT2-2 cần có. CLI được khóa version bằng package-lock; không cần Firebase CLI global hoặc đăng nhập Google.
3. `.env.emulator` cấu hình profile + endpoint + DB/auth của từng người; `start-be.ps1 -EnvFile backend/.env.emulator` đọc đúng các biến đó. Không có đường dẫn `F:` bắt buộc trong lệnh chạy.
4. Khi thoát emulator đúng cách, ảnh được giữ trong `.local-data/` và tự khôi phục. Dữ liệu này không vào Git; giữ đồng bộ với DB `_test`. Nếu cần chuyển dữ liệu phát triển, chuyển cả export Storage và DB tương ứng qua kênh được phép.

## FE dùng backend chung

Người quản lý cung cấp base URL BE và thông tin đăng nhập ứng dụng theo quyền. Trong `frontend/.env.local` đặt:

```dotenv
VITE_API_PROXY_TARGET=https://YOUR_TEAM_BACKEND
```

Nếu issuer auth tách riêng, thêm `VITE_AUTH_PROXY_TARGET=https://YOUR_TEAM_AUTH`; nếu auth chung BE thì bỏ biến này. Vite proxy HTTP và WebSocket qua cùng origin; không sửa `fetch` thành URL Firebase. Production cần reverse proxy tương ứng, TLS và forward Upgrade cho `/api/v1/news/events`. Không log frame bearer.

Chỉ dùng base URL thật đã được nhóm cung cấp; đây là hướng dẫn cấu hình, chưa có backend chung được deploy trong task này. Các máy dùng chung BE/DB sẽ thấy cùng bài và ảnh theo quyền, không cần mỗi người có service-account key. Cổng emulator không mở ra Internet.

## Cloud hiện bị tắt theo yêu cầu miễn phí

`smartcafe.news.firebase.cloud-enabled=false` trong cấu hình ứng dụng: mặc định không tra ADC hoặc gọi live Storage, kể cả khi terminal có tên bucket/credential cũ. Không thay giá trị này trong phạm vi hiện tại. Không tạo bucket, key, public ACL hay billing để vượt giới hạn Spark. Adapter Firebase Admin được giữ cho contract kỹ thuật nhưng không coi đó là quyền kích hoạt cloud.

BE/FE local dùng project emulator demo, không cần Firebase login, thẻ hoặc Google key. Nếu nhóm muốn cùng dữ liệu, dùng backend dev chung trên hạ tầng sẵn có được phép dùng miễn phí, vẫn giữ Storage emulator ở loopback của máy BE và DB dev riêng. Hướng dẫn này không tự deploy/mở cổng backend ra Internet hoặc cam kết đã có hosting chung.

## Kiểm tra sau khi cấu hình

Đăng nhập ADMIN thật do DT2-2 cấp trên đúng DB, mở Tin tức, upload PNG/JPEG, gắn vào bài và đăng cho nhóm được chọn. Kiểm ảnh preview/chi tiết; STAFF/CUSTOMER đúng nhóm đọc được, khác nhóm không đọc được, lưu trữ ẩn bài/ảnh khỏi reader. Khởi động lại emulator bằng cách thoát Ctrl+C và `npm start`, kiểm ảnh cũ còn đọc được. Không chạy cloud; emulator không chứng minh IAM/billing/cloud đã được nghiệm thu.

Nếu upload trả 503, kiểm profile/endpoint emulator và trạng thái ready; mode mặc định không cho dùng live Storage. Không bật billing hoặc đổi sang lưu giả. Nếu login 404, cần issuer DT2-2/proxy đúng; nếu 403, kiểm token/DB role/status/version; nếu ảnh 404, kiểm quyền bài và cặp DB/export ảnh. Không ghi token, password, key hoặc dữ liệu cá nhân vào log bàn giao.

Source/config mẫu/lockfile/hướng dẫn được bàn giao trong repo; `.env*`, `.local-data` và node_modules bị Git ignore, credential thật phải giữ ngoài checkout. Chưa commit/push theo chỉ thị chủ repo; các thay đổi này cần review và tích hợp trước khi người khác clone remote nhận được.

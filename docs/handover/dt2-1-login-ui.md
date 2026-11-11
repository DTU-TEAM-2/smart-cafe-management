# Chuẩn bị giao diện đăng nhập

Nguồn: yêu cầu mới nhất ngày 07/11/2026 của chủ repo: bỏ demo, chỉ làm sẵn UI; xác thực và phân quyền do người phụ trách khác triển khai. Yêu cầu này thay thế cấu hình tài khoản mock trước đó.

## Phạm vi hiện tại

- Nút Đăng nhập ở Header dẫn tới #login, dùng chung Header/Footer.
- Form nâu/vàng cùng phong cách trang chủ, ảnh quán sẵn có, label tiếng Việt, trường tên đăng nhập/mật khẩu và nút hiện/ẩn mật khẩu.
- Nút submit chưa khả dụng và có giải thích ngắn trong form. Không gửi mật khẩu, không tạo/lưu token hoặc tài khoản, không chọn vai trò, không gọi API login giả.
- Đã gỡ demoAuth.js, banner, tài khoản dùng thử, logout giả và biến VITE_DEMO_AUTH. Username mock còn trong sessionStorage từ phiên cũ không được đọc hay khôi phục.
- Header vẫn tiêu thụ phiên thật của module auth khi tích hợp. Hồ sơ/thông báo hiện có không đổi hợp đồng API. Không thay backend hoặc database trong lần chỉnh UI này.

## Bàn giao tích hợp

DT2-2 cung cấp luồng đăng nhập thật và xử lý lỗi theo thiết kế được duyệt, sau đó nối form và bật submit. Token hiện được Header đọc từ smart_cafe_token trong sessionStorage; phát smart-cafe-auth-change khi đổi phiên. Backend là nơi kiểm tra phân quyền; UI không tự cấp vai trò. Không ghi Done luồng xác thực dựa trên form hiện tại.

## Kiểm chứng

Chạy npm run lint, npm run build và npm run test:ui -- --workers=2 từ frontend. Các case login kiểm tra không nhận tài khoản mock cũ, không phát hành token/gửi mật khẩu khi Enter, hiện/ẩn mật khẩu, một Header/Footer dùng chung và không tràn ngang ở 320/390/768/1440px. API/profile/notification UI được kiểm trong bộ test Header hiện có; HTTP fixtures chỉ dành cho test tự động.

Skills áp dụng: vercel-react-best-practices (.agents/skills/react-best-practices/SKILL.md), fixing-accessibility (.agents/skills/fixing-accessibility/SKILL.md), cafe-quality (.agents/skills/cafe-quality/SKILL.md). Lint/build và 14 test desktop/mobile đạt ngày 09/11/2026. Bàn giao qua PR để nhóm review; chưa merge và chưa thay thế review độc lập.

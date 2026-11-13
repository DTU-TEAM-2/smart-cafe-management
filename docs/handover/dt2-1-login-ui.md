# Giao diện đăng nhập và tích hợp DT2-2

Ngày 07/11/2026 chủ repo yêu cầu bỏ demo và chỉ chuẩn bị UI. Ngày 11/11/2026 yêu cầu mở rộng sang full Task 7 và kiểm tra đăng nhập thật; phạm vi hiện hành theo ADR-008 trong [nhật ký quyết định](../architecture/decisions.md). DT2-2 vẫn sở hữu phát hành credential và chính sách xác thực.

## Hành vi hiện tại

- Header mở `#login`, dùng chung Header/Footer và phong cách LECKER. Form có tên đăng nhập/email, mật khẩu, hiện/ẩn mật khẩu, trạng thái chờ và lỗi có thể tiếp cận bằng bàn phím.
- Submit gọi `POST /api/v1/auth/login` thật. Token trả về phải được `/api/v1/home/account` xác minh trước khi lưu vào sessionStorage `smart_cafe_token` và mở `#news`. Không lấy role từ response login để tự cấp quyền.
- Không lưu mật khẩu; hủy request khi rời form để tránh giữ phiên từ response đến muộn. Không có demo account, role selector hoặc auth provider tự dựng.
- Đăng xuất xác nhận khi có bản soạn chưa lưu, xóa credential ứng dụng khỏi cả sessionStorage và localStorage cũ, phát `smart-cafe-auth-change`. Đây là đăng xuất tại trình duyệt, không tuyên bố thu hồi token trên các thiết bị khác.

## Cấu hình tích hợp

Vite dùng `VITE_API_PROXY_TARGET` tới BE consumer. Nếu DT2-2 chạy riêng, `VITE_AUTH_PROXY_TARGET` chỉ định issuer cho login/me/logout. Production cần reverse proxy tương ứng; hai module dùng cùng DB và `AUTH_TOKEN_SECRET`. Không đặt secret trong biến VITE. Không merge source auth vào Task 7 hoặc tự đổi schema để thay thế tích hợp này.

## Kiểm chứng và giới hạn

Lint, production build và 68 test Playwright desktop/mobile đạt trong lần full Task 7, gồm 36 test tin tức, 20 hồi quy login/Header/trang chủ và 12 test Sales. Test login bao gồm lỗi API, token bị consumer từ chối, hủy request khi rời form, giữ phiên đã xác minh và đăng xuất. Regression tin tức gồm response lưu trữ đến muộn sau khi rời trang, socket kiểm tra lại danh tính/kết nối lại và thông báo xung đột đúng nguyên nhân.

Kiểm thử tích hợp mới nhất ngày 12/11/2026 chạy nguyên module auth PR #7 ở revision `fbaa9528039a3df258165b3765e03a86374b6e5f` trên MySQL `_test` riêng và Firebase Storage Emulator. Trình duyệt nhập credential fixture vào form thật cho ADMIN/STAFF/CUSTOMER; không ký hoặc chèn bearer để bỏ qua UI. Toàn bộ HTTP/WebSocket/browser/ảnh local đạt 113/113 checks, không dùng route mock trong lượt tích hợp này. Lượt trước khi bật emulator đạt 95/95 tại revision `72de814`. Fixture không được ghi vào DB hệ thống. Báo cáo/ảnh/log ở `F:/CODEGYM/_local-reviews/dt2-7-be`, ngoài Git; không coi emulator là bằng chứng cloud thật.

Chưa có mật khẩu account hệ thống nên không tuyên bố login thành công bằng user thật của `smart_cafe`. Không đổi mật khẩu hay seed account để vượt giới hạn đó. Auth PR vẫn cần người phụ trách xử lý findings trước merge; tính tương thích login không chứng minh toàn module auth đã đủ điều kiện approve.

Skills áp dụng: vercel-react-best-practices (`.agents/skills/react-best-practices/SKILL.md`), fixing-accessibility (`.agents/skills/fixing-accessibility/SKILL.md`), cafe-quality (`.agents/skills/cafe-quality/SKILL.md`). Không commit/push.

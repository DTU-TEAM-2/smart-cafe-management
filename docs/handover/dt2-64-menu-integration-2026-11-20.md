# DT2-64 / DT2-9 — CRUD và ảnh qua API

Ngày 20/11/2026, chủ repo yêu cầu trong chat: “thực hiện 2 luồng này trên code tạo nhánh riêng”. Hai luồng là giao diện quản lý cập nhật dữ liệu qua backend/MySQL và ảnh upload qua backend, gắn vào món, xuất hiện trong menu khách sau thêm/sửa. Tiêu chí gốc: `evidence/dt2-9-acceptance-2026-11-11.png`; checklist tích hợp: [DT2-64](dt2-64-menu-api-follow-up.md).

## Nhánh và nguồn

- Nhánh local `feature/dt2-64_menu-crud-cloudinary`, worktree riêng, tách từ `origin/develop@9a88ba6dfb82242b1c2f12fb1c4ebdd91b09a39b`. Không chuyển nhánh hoặc thay đổi công việc quản lý bàn ở checkout gốc.
- Tái sử dụng commit `2ac010451a027c5b508063efac40083b10966c71` của Nguyen Van Duy Manh từ nhánh CF-14 / [PR #16](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/16), cherry-pick `-x`, giữ author và nguồn. Commit local sau đối chiếu conflict là `bd0f74886f02ee132326f7b530152f63cd23e4e6`.
- Giữ auth/profile/customer mới từ develop khi tích hợp HomeShell; không mang cơ chế auth cũ từ commit CF-14 vào thay thế. Nhánh riêng được tạo theo yêu cầu rõ của chủ repo; không sửa/đổi tên/push vào nhánh PR #16. Chưa tạo PR mới, merge hoặc deploy.

## Kết quả triển khai

- `#admin-menu` xác minh tài khoản từ API, chỉ mở cho ADMIN có Bearer thật. Dashboard có liên kết quản lý thực đơn. Preview `/preview/menu` vẫn chỉ chạy trên dev và không được dùng làm dữ liệu quản trị thật.
- Danh sách, tìm bốn tiêu chí, lọc và phân trang do API quyết định. Có loading/error/retry/empty; hủy request cũ, giữ version từ server; đọc lại bản ghi trước sửa/xóa. CSRF lấy từ API, giữ cookie cùng origin cho từng mutation.
- Tạo/sửa/xóa nhóm và món gọi API hiện có. Giá VND và version/media ID được giữ dưới dạng chuỗi số nguyên chính xác, không chuyển qua Number; backend tiếp tục quyết định quyền, giá, transaction và xung đột. Không đổi schema hoặc backend production.
- Chọn ảnh được kiểm tra như trước; lưu gọi multipart `/api/v1/menu/images`, nhận `mediaId`, gửi `imageMediaId` khi tạo/sửa món. Sửa không chọn ảnh giữ tham chiếu cũ; bỏ ảnh gửi null. Provider lỗi giữ form; retry không upload lại file đã có mediaId sau lỗi lưu món. Chặn đổi file qua drag/drop khi đang lưu.
- Menu khách hiện có đọc URL server từ `menu_items.image_media_id → media_assets.secure_url`; không cần thay contract/schema/customer UI. Thay ảnh cập nhật tham chiếu món, không xóa asset cũ hoặc lịch sử giá.

## Kiểm thử

- `npm ci --offline`, `npm run lint`, `npm run build`: đạt, không đổi dependencies/lockfile.
- `npm run test:ui -- --workers=2`: 236/236 Playwright desktop/mobile và 5/5 unit đạt. Sau bổ sung test upload lỗi/retry, chạy lại riêng `tests/menu-admin.spec.js`: 8/8 đạt. Các API trong bộ UI này được điều khiển bằng fixture, không phải bằng chứng MySQL.
- E2E mới `MenuBrowserIntegrationTest` chạy Node/Chromium → Vite proxy → Spring/Tomcat → MySQL test thật. Dùng đăng nhập auth thật với user chỉ được tạo trong schema `_test`; không mock bất kỳ application API nào. Kiểm tra tạo nhóm/món, upload, tải lại, sửa giá/thay ảnh, URL/ảnh trên menu khách sau cả tạo và sửa, hủy/xác nhận xóa nhóm, hậu kiểm JDBC FK/media/giá/xóa logic. Test tự dọn các hàng do nó tạo.
- `MENU_BROWSER_TEST=true`, đặt `TEST_DB_URL` vào `smart_cafe_menu_20261120_test` trên MySQL 8.0.46 cổng 13369; `backend/mvnw.cmd -o -B -ntp verify`: **320/320 JUnit đạt, 0 failures/errors/skips**, bao gồm E2E mới. Báo cáo HTTP `backend/target/api-report/dt2-9-api-results.json` tạo mới ngày 20/11: **46/46 PASS**. Không cộng số ca HTTP vào số JUnit. Đây là baseline develop mới, không so trực tiếp số test với checkout cũ trước pull.
- Chỉ provider HTTP và ảnh CDN được giả lập trong chế độ mặc định; không sử dụng kết quả đó để khẳng định Cloudinary thật. Log: `backend/target/menu-browser.log`; ảnh: `backend/target/menu-browser-customer.png`; JUnit XML: `backend/target/surefire-reports/`. Các artifact này được ignore, không đưa vào Git.
- Khôi phục các ảnh chứng cứ lịch sử bị các test khác ghi lại; không nhập ảnh hồi quy ngoài phạm vi. Không có Aikido MCP khả dụng nên chưa chạy Aikido SAST. Self-review không thay hai review độc lập theo Git Flow.

## Chạy lại trên schema test

Provision nguyên fixture `backend/src/test/resources/schema.sql` vào schema mới riêng có hậu tố `_test`. Không dùng schema nghiệp vụ hoặc DDL tự chế. Đặt `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` bằng cơ chế local; không commit secret. Cài frontend theo lockfile và có Chromium của Playwright. Từ `backend/`:

```powershell
$env:MENU_BROWSER_TEST='true'
.\mvnw.cmd -B -ntp -Dtest=MenuBrowserIntegrationTest test
```

Mặc định provider là HTTP fixture. Để smoke Cloudinary thật, cấp ba biến `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` cho tiến trình local, thêm `$env:MENU_BROWSER_LIVE='true'`, chạy cùng lệnh. Chế độ live không chặn/thay thế request Cloudinary/CDN; tạo hai asset thật và giữ chúng lại. Việc xóa asset cũ/chưa tham chiếu cần quy trình cleanup được owner chốt; test không tự xóa tài nguyên provider. Không chạy chế độ live khi chưa có cấu hình và quyền upload vào cloud test phù hợp.

## Giới hạn nghiệm thu

Chưa có cấu hình Cloudinary trong môi trường phiên này nên chưa chạy live smoke. Code và đường chạy kiểm thử đã hỗ trợ luồng thật; tiêu chí “Cloudinary thật” tiếp tục chờ bằng chứng chạy live. Database hệ thống chưa được sửa/migrate; kiểm thử không chứng minh DDL triển khai hiện tại đã đủ để rollout. Không đánh dấu Done hệ thống hoặc phát hành chỉ dựa trên test riêng.

Skills: cafe-workflow, cafe-requirements tại `.agents/skills/{cafe-workflow,cafe-requirements}/SKILL.md` cho phạm vi/provenance; vercel-react-best-practices (`.agents/skills/react-best-practices/SKILL.md`), cafe-spring-api và fixing-accessibility tại thư mục skill tương ứng cho tích hợp API/form/transaction; cafe-quality, cafe-review, ui-ux-pro-max tại thư mục tương ứng cho kiểm chứng/self-review (UX query `error summary validation`). Skill `scan` từ plugin Aikido đã đọc, tool không khả dụng.

# DT2-11 — bản thử giao diện quản lý bàn

Ngày: 17/11/2026. Nguồn: [DT2-11](https://duymanhdnvn20092005.atlassian.net/browse/DT2-11), DT2-73/74/75; yêu cầu trực tiếp làm code thử trên nhánh riêng, không push/tạo PR, sau đó chọn giao diện độc lập và lưu câu hỏi. Nhánh local: `feature/dt2-11_table-management`, từ `origin/develop@ffa4d95`. Chưa nghiệm thu nghiệp vụ, chưa thay đổi Jira hay schema.

## Mở bản thử

Trong `frontend`, chạy `npm run dev -- --host 127.0.0.1` và mở `/preview/tables` theo địa chỉ Vite in ra. Trang chỉ được nạp ở development; production build không chứa module preview. Không thêm đường dẫn vào dashboard sản phẩm.

Danh sách ban đầu rỗng. Chọn **Thêm bàn**, nhập ID và State tùy ý, lưu; chọn bàn để nạp Table’s Info, sửa State hoặc xác nhận xóa. ID giữ nguyên khi sửa trong bản thử. Edit/Remove khi chưa chọn báo “No table is chosen”. Tìm kiếm ID/State, báo thiếu từ khóa/không có kết quả, Show List Table và phân trang 10 dòng. Các form có nhãn, lỗi gắn với trường nhập; dialog hỗ trợ Escape, giữ focus trong modal và trả focus khi đóng.

## Giới hạn và dữ liệu

- Dữ liệu chỉ trong React state, mất khi reload hoặc rời trang. Không fetch, browser storage, seed, auth giả hoặc kết nối DB.
- ID/State là nhãn chuỗi của prototype, không tự ánh xạ sang PK/mã/số bàn hoặc enum DB. On chỉ ghi “Chưa chốt”, không có thao tác phiên.
- Validation bắt buộc/ID trùng không phân biệt hoa thường và tìm chuỗi con không phân biệt hoa thường là hành vi thử UI, cần QA xác nhận trước khi thành contract.
- Xóa chỉ bỏ dòng trong bộ nhớ; chưa quyết định hard delete/archive hoặc an toàn phiên/hóa đơn. Không trình bày kết quả UI như bằng chứng persistence, phân quyền, MySQL hay tích hợp DT2-4/6/8.
- SRS/Project Plan gốc chưa truy cập được từ các liên kết Jira; chưa có bản versioned đầy đủ trong handover. Không suy ra đã được mentor duyệt từ trạng thái Mentor Review.

Các câu hỏi, khác biệt giữa snapshot legacy và DDL verified đã lưu trong [open questions](../product/open-questions.md#dt2-11--id-stateon-và-xóa-bàn-17112026). DDL verified đã có trong repo và hỗ trợ implementation/rehearsal; snapshot legacy thuộc môi trường khác. Người dùng chọn UI độc lập, chưa xác nhận ánh xạ nghiệp vụ hoặc môi trường tích hợp DT2-11. Không viết backend persistence hoặc chuyển lineage trong bản thử này.

## Kiểm chứng

Các lệnh cần chạy cho bản thử:

```powershell
cd frontend
npm run lint
npm run build
npm run test:ui -- tests/tables.spec.js
```

Kết quả trên working tree từ base `ffa4d95`, ngày 17/11/2026:

- `npm run lint`: đạt, không có warning.
- `npm run build`: đạt. Kiểm chuỗi/module preview trong `frontend/dist` không thấy module bàn; test production xác nhận không hiển thị preview.
- `npm run test:ui -- tests/tables.spec.js`: **12/12 đạt** (desktop Chromium và mobile giả lập iPhone 13 bằng Chromium), 35,3 giây. Lần đầu trong sandbox kẹt ở web server; đã dừng và chạy ngoài sandbox. Lượt browser đầu phát hiện focus vòng Tab ở dialog và locator chọn tiêu chí tìm kiếm; đã sửa và chạy lại đủ 12 ca.
- Axe: không có violation trong các trạng thái được kiểm (form có dữ liệu dài và dialog), không thay thế kiểm tra WCAG toàn diện. Đã xem ảnh desktop/mobile tại `frontend/test-results/tables-searches-ID-State-h-b0bf8-keyword-and-Show-List-Table-{desktop,mobile}/tables-overview.png`; không có tràn ngang trong ca dữ liệu dài. Ảnh/test-results được Git ignore.
- `node scripts/skills.mjs check`: đạt (17 skills, 16 routes, 169 inherited files).
- `git diff --check -- . ':(exclude).agents/skills/**'`: đạt.
- Aikido MCP không khả dụng trong phiên; không có kết quả scan bảo mật. Đã tự review luồng dữ liệu/React escaping, ranh giới development và việc không truy cập API/storage; không coi self-review là approval độc lập.

Playwright kiểm các luồng UI thực trong browser, dữ liệu nhập thử, không gọi API/không ghi storage, reload rỗng, không đưa preview vào production, focus/keyboard, accessibility và layout mobile. Đây không phải kiểm thử API/DB, xác thực, trình duyệt Safari thật hoặc nghiệm thu nghiệp vụ. Không chạy Maven/MySQL vì không đổi backend/persistence. Không push, tạo PR hay thực hiện release.

Skills đã đọc và áp dụng: `cafe-requirements` (ghi câu hỏi và giới hạn nguồn), `cafe-database` (giữ persistence khi DDL lệch), `ui-ux-pro-max` (controlled form và focus từ truy vấn React), `vercel-react-best-practices` (lazy import dev-only và state dẫn xuất), `fixing-accessibility` (native controls/dialog và lỗi form), `cafe-quality`/`cafe-review` (verification/review theo phạm vi). Tất cả tại `.agents/skills/<folder>/SKILL.md`; React dùng folder `react-best-practices`.

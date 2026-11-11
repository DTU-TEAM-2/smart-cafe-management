# DT2-9 — bàn giao FE thực đơn

Ngày triển khai: 2026-11-10. Đổi tên nhánh và lưu local theo yêu cầu ngày 2026-11-11: `feature/dt2-9-manage-menu-items-and-categories-frontend`. Thay đổi được lưu bằng commit local; không push/merge.

## Kết quả và nguồn

Triển khai FE React/Vite, CSS thuần cho món và nhóm món. Nguồn yêu cầu: [yêu cầu trong chat đã ghi nhận](../../handover/dt2-9-frontend-request.md). Đã đọc design context và screenshot Figma `8:292`, `8:319`, `57:306`; dùng màu nền, panel, sidebar, font và form hai cột tương ứng. Các frame không có ảnh/SVG tĩnh để tải. Inter/Playfair Display phục vụ cục bộ, nguồn và giấy phép trong `frontend/public/fonts/`.

Tham khảo cấu trúc/luồng của legacy; không nhập code backend, migration, dữ liệu thật, trạng thái hoàn thành cũ hay tài khoản giả. Các mục điều hướng ngoài phạm vi chỉ hiển thị nhãn.

Mở rộng có chủ đích so với Figma: nhóm món chưa có frame riêng nên dùng cùng hệ thiết kế; thêm tìm/lọc, thao tác từng dòng, phân trang, trạng thái trống và bản mobile. Chữ mục sidebar đang chọn được làm sáng từ `#d5a953` sang `#f4cf85` để đạt contrast; không tuyên bố khớp pixel tuyệt đối.

## Kiểm chứng đã chạy

- `npm install` trong `frontend`: 28 package được audit, 0 vulnerability tại thời điểm cài.
- `npm run lint`: đạt, không warning.
- `npm run build`: đạt production bundle.
- `npm run test:ui`: 12/12 đạt (6 hành vi × desktop Chromium 1440×1000 và mobile Chromium mô phỏng iPhone 13).
- Sau bổ sung ảnh nhóm món và sửa skip-link khi cuộn: `npm run test:ui -- --grep keyboard`: 2/2 đạt; axe không phát hiện vi phạm trên danh sách món, nhóm món, form và hộp thoại ở cả hai viewport. Đây là kiểm tra tự động trong phạm vi trang, không thay thế audit WCAG đầy đủ.
- `node scripts/skills.mjs check`: 17 skills, 16 routes hợp lệ.
- `node --test scripts/skills.test.mjs`: 4/4 đạt.
- `git diff --check -- . ':(exclude).agents/skills/**'`: đạt; các file mới được kiểm tra whitespace riêng.

Các case gồm tìm không dấu, lọc nhóm, trạng thái trống, thêm/sửa, từ chối mã trùng, mã bất biến khi sửa, xóa/hủy bằng Escape, khôi phục focus, nhóm có món, đổi tên nhóm phản ánh lên món, ảnh không hợp lệ/xem trước cục bộ, xóa dòng cuối trang, không gọi API và không tràn ngang toàn trang. Native dialog có vòng Tab, ưu tiên Hủy và khôi phục focus sau đóng.

Đã xem ảnh render và đối chiếu các màn hình trong phạm vi. Ảnh: [món desktop](desktop-list.png), [nhóm desktop](desktop-categories.png), [form desktop](desktop-editor.png), [xóa desktop](desktop-delete.png), [món mobile](mobile-list.png), [nhóm mobile](mobile-categories.png), [form mobile](mobile-editor.png), [xóa mobile](mobile-delete.png).

## Giới hạn và bước tiếp

Chỉ FE với React state; dữ liệu mẫu mất khi reload. Validation/khóa xóa nhóm có món chỉ là cách vận hành bản xem thử, chưa chốt hợp đồng nghiệp vụ. Chưa có API/auth, backend/database/integration test hay kiểm thử thiết bị vật lý. Không nhập các quy tắc session/order/payment từ legacy.

Aikido MCP không có công cụ trong phiên, nên chưa chạy Aikido SAST; npm audit không thay thế SAST. CI frontend đã được thêm nhưng chưa chạy trên GitHub. Self-review chưa phát hiện blocker còn lại trong phạm vi FE; chưa thay thế reviewer độc lập hoặc nghiệm thu nghiệp vụ. Không tạo PR/deploy/merge trong task này.

Skills thực sự đọc: `cafe-requirements` (`.agents/skills/cafe-requirements/SKILL.md`: ranh giới yêu cầu), `figma-design-to-code` (plugin Figma, design context và screenshot), `vercel-react-best-practices` (`.agents/skills/react-best-practices/SKILL.md`: React state/derived data), `fixing-accessibility` (`.agents/skills/fixing-accessibility/SKILL.md`: native controls, focus/dialog), `cafe-quality` (`.agents/skills/cafe-quality/SKILL.md`: kiểm chứng theo rủi ro), `cafe-review` (`.agents/skills/cafe-review/SKILL.md`: self-review), `ui-ux-pro-max` (`.agents/skills/ui-ux-pro-max/SKILL.md`: tham khảo hướng dẫn UI, Figma vẫn là nguồn thiết kế), `aikido:scan` (plugin Aikido: xác định giới hạn công cụ scan).

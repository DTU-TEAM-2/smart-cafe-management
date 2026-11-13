# Quy ước đặt tên và định dạng

Tên mã nguồn dùng tiếng Anh rõ nghĩa; nội dung hướng đến người dùng và tài liệu nhóm có thể dùng tiếng Việt. Giữ thuật ngữ nhất quán: `cafeTable` là bàn vật lý, `tableSession` là lượt phục vụ, `order` là đợt gọi, `bill` là hóa đơn. Tránh `data`, `temp`, `manager`, `helper` nếu không nói rõ trách nhiệm.

| Đối tượng | Quy tắc | Ví dụ |
| --- | --- | --- |
| Repository / thư mục tính năng FE | kebab-case | `smart-cafe-management`, `table-sessions` |
| Nhánh | loại / mã task chữ thường _ mô tả kebab-case theo Git Flow | `feature/cf-07_submit-order`, `bugfix/cf-11_duplicate-bill`, `release/1.4.0`, `hotfix/1.4.1_fix-bill` |
| Commit | Conventional Commit + scope khi hữu ích | `feat(orders): CF-07 submit an order batch` |
| PR title | Conventional Commit + mã CF/QA | `feat(orders): CF-07 submit an order batch` |
| Java package | chữ thường, theo tính năng | `vn.codegym.smartcafe.orders` |
| Java class / enum / record | PascalCase, vai trò cụ thể | `OrderService`, `CreateOrderRequest`, `OrderResponse` |
| Java method / biến | camelCase; method là động từ | `submitOrder`, `findBySessionId`, `unitPrice` |
| Boolean | `is`, `has`, `can` khi phù hợp | `isActive`, `hasOpenSession`, `canCancel` |
| Hằng số / enum value | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE`, `PAYMENT_REQUESTED` |
| React component / file | PascalCase `.jsx` | `OrderSummary.jsx` |
| React hook | `use` + PascalCase, file camelCase | `useTableSession.js` |
| JS function / variable | camelCase | `formatCurrency`, `sessionId` |
| CSS class | kebab-case; theo thành phần/trách nhiệm | `order-summary`, `status-message` |
| REST resource | danh từ số nhiều, kebab-case | `/api/v1/table-sessions/{sessionId}/orders` |
| JSON field | camelCase | `orderedAt`, `totalAmount` |
| SQL table / column / constraint | snake_case, bảng số nhiều | `order_items`, `unit_price`, `uq_bills_session_id` |
| Migration khi đã có Flyway | số thứ tự + mô tả bất biến | `V001__create_table_sessions.sql` |
| Environment variable | UPPER_SNAKE_CASE, rõ môi trường | `SERVER_PORT`, `VITE_API_PROXY_TARGET` |
| Backend test | subject + hành vi rõ | `OrderServiceTest`, `rejectsClosedSession` |
| Tài liệu / bằng chứng mới | kebab-case, ngày ISO nếu cần | `cf-07-order-contract.md`, `2026-11-04-kickoff.md` |

Nguồn bàn giao giữ tên gốc để truy xuất; không đổi tên hàng loạt code đang có chỉ vì stylistic preference. Không dùng ID cá nhân, số điện thoại hoặc secret trong tên file/branch.

Đích/tách nhánh theo [git-flow.md](../../git-flow.md): feature/bugfix từ develop vào develop; main chỉ nhận release/hotfix rồi đồng bộ lại develop. Nhánh cũ kiểu `feature/dt2-...` vẫn hợp lệ, không ép rename hay rewrite lịch sử. Mẫu GitHub issue số `feature/#123_add-search-filter` từ tài liệu nguồn được phép, nhớ quote trong shell/encode trong URL.

Java thụt 4 spaces; JS/JSX/CSS/JSON/YAML thụt 2 spaces. UTF-8, newline cuối file, bỏ trailing whitespace. Tệp văn bản LF; script Windows `.cmd` CRLF. Dùng native HTML semantic và nhãn có nghĩa. Comment giải thích lý do hoặc bất biến, không nhắc lại code.

Commit types: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`, `revert`. Breaking change dùng `!` và ghi ảnh hưởng/cách chuyển đổi. Issue ID GitHub/Jira có thể liên kết thêm trong body; CF/QA vẫn là mã truy xuất bộ bàn giao.

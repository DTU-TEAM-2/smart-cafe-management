# BÁO CÁO BÀN GIAO FRONTEND TASK DT2-4 — SMART CAFE MANAGEMENT (LECKER)

> **Dự án:** Smart Cafe Management (LECKER)<br>
> **Task:** DT2-4 — Customer Frontend Workflows (CF-06)<br>
> **Nhánh phát triển:** `feature/dt2-4_customer-frontend`<br>
> **Nhánh đích:** `develop`<br>
> **Pull Request:** [PR #20](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/20)<br>
> **Head Commit:** `3cfc64b`<br>
> **Figma chuẩn:** [LECKER • Smart Cafe — Prototype UI/UX (Node 3:2)](https://www.figma.com/design/7iFkUz20p6rE2fPC9Xn8rp/LECKER-%E2%80%A2-Smart-Cafe-%E2%80%94-Prototype-UI-UX--Customer-%C2%B7-Staff-%C2%B7-Admin-?node-id=3-2)<br>
> **Backend PR tham chiếu:** [PR #14 (`feature/dt2-4_customer-workflows`)](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/14)

---

## 1. TỔNG QUAN VÀ MỤC TIÊU BÀN GIAO

Task DT2-4 Frontend triển khai toàn bộ trải nghiệm khách hàng (Customer Mobile-First Experience) tại quán cà phê LECKER khi quét mã QR tại bàn. Hệ thống cho phép khách hàng tương tác mượt mà với quầy pha chế và phục vụ mà không cần cài đặt ứng dụng:

1. **Khám phá thực đơn (Menu Browsing):** Xem danh mục phân loại, tìm kiếm món theo từ khóa, lọc món có sẵn, xem chi tiết món ăn (ảnh, giá, mô tả), hỗ trợ phân trang danh mục lớn (`ItemPage`).
2. **Quản lý giỏ hàng (Cart Management):** Thêm món nhanh, tùy chỉnh số lượng (+/-), xóa món, tính tổng tiền thời gian thực, quản lý pending snapshot bảo toàn giỏ khi gửi đơn.
3. **Đặt món (Order Placement):** Gửi đơn với Idempotency-Key chống gửi trùng, bảo vệ CSRF Token, thông báo kết quả tức thời.
4. **Theo dõi đơn và chỉnh sửa (Active Orders & 180s Live Timer):** Hiển thị danh sách các món đã đặt theo từng đợt, live countdown đếm ngược 180 giây từ server (`editableUntil`), cho phép khách tăng/giảm hoặc hủy món trước khi quầy bắt đầu pha chế.
5. **Yêu cầu phục vụ & Thanh toán (Service Requests):** Gọi nhân viên phục vụ (`WAITER`) và yêu cầu xuất hóa đơn thanh toán (`BILL`), hỗ trợ kịch bản nhiều khách cùng bàn (multi-client A/B).
6. **Đánh giá trải nghiệm (Customer Feedback):** Cho điểm đánh giá sao (1-5 sao), nhập nhận xét, đính kèm ảnh thực tế và gửi phản hồi lên hệ thống.

---

## 2. BẢNG ĐỐI CHIẾU FIGMA DESIGN VS FRONTEND CODE

Toàn bộ giao diện được xây dựng bám sát bảng thiết kế chính thức của LECKER theo phong cách Dark Luxury Vintage:
* **Background chính:** `#100e0c` (Dark Charcoal), card background `#181411`
* **Màu nhấn thương hiệu (Gold Accent):** `#d5a953`, `#f1bf77`
* **Typography:** Tiêu đề font *Playfair Display* (Serif cổ điển sang trọng), nội dung *Inter / Montserrat / sans-serif*

| STT | Màn hình / Component | Figma Frame / Node ID | Thành phần Frontend tương ứng | Trạng thái đối chiếu |
|---|---|---|---|---|
| 1 | Header & Table Badge | Frame `Header` (`node-id=3:2`) | `CustomerLayout.jsx` (`.c-header`, `.c-brand`, `.c-table-badge`) | Khớp 100% Logo LECKER hoa và Badge số bàn viền vàng |
| 2 | Danh mục món (Categories) | Frame `Category Tabs` | `CustomerMenuView.jsx` (`.c-category-pills`, `.c-category-pill`) | Khớp 100% Pill buttons trượt ngang, active highlight gold |
| 3 | Thẻ món ăn (Menu Item Card) | Frame `Item Card Grid` | `CustomerMenuView.jsx` (`.c-item-card`, `.c-item-thumb`, `.c-btn-add`) | Khớp 100% Ảnh bo góc 12px, giá vàng `#d5a953`, nút `+` nhanh |
| 4 | Chi tiết món (Item Modal) | Frame `Item Detail Modal` | `ItemDetailModal.jsx` (`.c-modal-dialog`, `.c-stepper`) | Khớp 100% Ảnh lớn, bộ tăng giảm số lượng, nút thêm giỏ |
| 5 | Giỏ hàng (Cart View) | Frame `Cart Sheet` | `CustomerCartView.jsx` (`.c-cart-item`, `.c-cart-summary`) | Khớp 100% Tạm tính, phí dịch vụ miễn phí, nút gửi đơn lớn |
| 6 | Giỏ hàng trống (Empty State) | Frame `Cart Empty` | `CustomerCartView.jsx` (`.c-empty-state`, `.c-empty-icon`) | Khớp 100% Icon giỏ hàng nét mỏng, thông điệp rỗng và CTA |
| 7 | Kết quả gửi đơn (Success Modal)| Frame `Order Success Dialog`| `OrderResultModal.jsx` (`.c-result-box`, `.c-result-icon`) | Khớp 100% Icon tick xanh, mã đơn, nút Xem đơn / Tiếp tục |
| 8 | Đơn đã đặt (Active Orders) | Frame `Order Tracking List`| `ActiveOrdersView.jsx` (`.c-order-batch`, `.c-timer-badge`)| Khớp 100% Live countdown 180s nhấp nháy, nút sửa/hủy món |
| 9 | Gọi phục vụ (Waiter Request) | Frame `Service - Call Waiter`| `ServiceRequestsView.jsx` (`.c-service-card`, `type=WAITER`)| Khớp 100% Icon chuông phục vụ, xác nhận gửi yêu cầu bàn |
| 10| Yêu cầu tính tiền (Bill) | Frame `Service - Request Bill`| `ServiceRequestsView.jsx` (`.c-service-card`, `type=BILL`)| Khớp 100% Icon hóa đơn, tổng tiền dự kiến, ghi chú gửi |
| 11| Đánh giá (Feedback Form) | Frame `Feedback Sheet` | `FeedbackView.jsx` (`.c-star-rating`, `.c-upload-box`)| Khớp 100% 5 sao vàng, đính kèm 1-3 ảnh thu nhỏ |
| 12| Thanh điều hướng dưới (Dock) | Frame `Bottom Nav Dock` | `CustomerDock.jsx` (`.c-dock`, `.c-dock-btn`, `.c-dock-badge`)| Khớp 100% 5 tab icon + badge số lượng món trong giỏ |

---

## 3. BẢNG ĐỐI CHIẾU BACKEND API (PR #14) VS FRONTEND INTEGRATION

Frontend kết nối trực tiếp với các endpoint Backend chính thức của Module DT2-4 (`CustomerMenuController`, `CustomerOrderController`, `CustomerRequestController`, `CustomerFeedbackController`):

| Chức năng | Phương thức & Endpoint | Tham số / Payload | Response Contract Backend | Xử lý Frontend (`customerApi.js`) |
|---|---|---|---|---|
| Danh mục thực đơn | `GET /api/v1/customer/menu/categories` | Không | `List<CategoryResponse>`: `id, code, name, activeItemCount` | `fetchCategories()` tải danh mục và hiển thị pill lọc |
| Danh sách món (phân trang) | `GET /api/v1/customer/menu/items` | `categoryId, keyword, availableOnly, page, size` | `ItemPage`: `content[], totalElements, page, size` | `fetchMenuItems()` phân trang 20 món/trang, hỗ trợ "Xem thêm món" |
| Chi tiết 1 món | `GET /api/v1/customer/menu/items/{id}` | `itemId` trên path | `MenuItemResponse`: `id, name, price, available...` | `fetchMenuItem(id)` mở Modal chi tiết |
| Lấy CSRF token | `GET /api/v1/customer/csrf` | Không | `{ headerName: "X-CSRF-TOKEN", token: "..." }` | `fetchCsrfToken()` đính kèm header chống CSRF attack |
| Gửi đơn đặt món | `POST /api/v1/customer/sessions/{sessionId}/orders` | Body: `{ items: [{ menuItemId, quantity }] }`<br>Header: `Idempotency-Key` | `CustomerOrderService.Order`: `id, sessionId, totalAmount, editableUntil, items[]` | `createOrder()` với chống gửi trùng và trừ giỏ chính xác |
| Sửa số lượng món | `PATCH /api/v1/customer/sessions/{sessionId}/order-items/{itemId}` | Body: `{ quantity, expectedVersion }` | `CustomerOrderService.Order` (full updated order) | `updateOrderItemQuantity()` cập nhật batch trong 180s |
| Hủy món ăn | `DELETE /api/v1/customer/sessions/{sessionId}/order-items/{itemId}` | Body: `{ expectedVersion }` | `CustomerOrderService.Order` (full updated order) | `cancelOrderItem()` đánh dấu `CANCELLED` trong 180s |
| Gọi phục vụ bàn | `POST /api/v1/customer/sessions/{sessionId}/waiter-requests` | Body: `{ note }`<br>Header: `Idempotency-Key` | `ServiceRequestResponse`: `id, type: "WAITER", status` | `createWaiterRequest()` gửi chuông phục vụ |
| Yêu cầu hóa đơn | `POST /api/v1/customer/sessions/{sessionId}/bill-requests` | Body: `{ paymentMethod, note }`<br>Header: `Idempotency-Key`| `ServiceRequestResponse`: `id, type: "BILL", status` | `createBillRequest()` hỗ trợ multi-client A/B |
| Gửi đánh giá feedback| `POST /api/v1/customer/feedback` | Body: `{ rating, name, email, content, photoUrls[] }` | `CustomerFeedbackResponse`: `id, rating, createdAt` | `submitFeedback()` validate form và gửi nhận xét |

---

## 4. DANH SÁCH 15 HÌNH ẢNH MINH CHỨNG GIAO DIỆN THỰC TẾ

Toàn bộ 15 hình ảnh minh chứng được xuất tự động từ Playwright E2E Test Suite chạy trên giao diện thực tế và lưu trữ tại thư mục `docs/reports/dt2-4-frontend/`:

| STT | Tên file hình ảnh | Mô tả màn hình minh chứng thực tế |
|---|---|---|
| 01 | `01-menu.png` | Thực đơn chính LECKER với Header bàn, thanh tìm kiếm, danh mục cà phê/trà/bánh và danh sách món |
| 02 | `02-item-detail.png` | Modal xem chi tiết món ăn (Caramel Latte), hình ảnh phóng to, stepper tăng giảm và đơn giá |
| 03 | `03-cart.png` | Giỏ hàng đang có món, tạm tính tiền VND, chi tiết từng dòng món và nút gửi đơn lớn |
| 04 | `04-order-success.png` | Modal thông báo đặt món thành công, hiển thị mã đơn hàng gửi tới quầy pha chế |
| 05 | `05-active-orders.png` | Màn hình danh sách đơn đã gửi kèm bộ đếm ngược 180 giây thời gian thực (`live countdown timer`) |
| 06 | `06-order-edit.png` | Thao tác chỉnh sửa số lượng hoặc hủy món trực tiếp trong thời gian 180 giây cho phép |
| 07 | `07-waiter.png` | Màn hình gọi nhân viên phục vụ bàn (`WAITER`) kèm ô nhập ghi chú yêu cầu |
| 08 | `08-bill.png` | Màn hình yêu cầu thanh toán hóa đơn (`BILL`), hiển thị tổng tiền phiên và chọn phương thức |
| 09 | `09-feedback.png` | Màn hình đánh giá 5 sao, nhập họ tên, email, nhận xét trải nghiệm và upload ảnh |
| 10 | `10-feedback-success.png` | Thông báo gửi đánh giá feedback thành công, cảm ơn quý khách |
| 11 | `11-cart-empty.png` | Trạng thái giỏ hàng trống (Empty State) với nút CTA "XEM THỰC ĐƠN NGAY" |
| 12 | `12-error-state.png` | Trạng thái xử lý lỗi (chưa quét QR xác thực bàn hoặc lỗi kết nối mạng) |
| 13 | `13-mobile.png` | Giao diện hiển thị chuẩn Mobile (iPhone 13 - 390 x 844 px) |
| 14 | `14-tablet.png` | Giao diện hiển thị chuẩn Tablet (iPad - 768 x 1024 px) |
| 15 | `15-desktop.png` | Giao diện hiển thị chuẩn Desktop (1440 x 1300 px) |

---

## 5. LỊCH SỬ XỬ LÝ VÀ GIẢI TRÌNH REVIEW FINDINGS

Trong quá trình review PR #20, nhóm phát triển đã tiếp nhận và xử lý triệt để 9 ý kiến đánh giá từ các Reviewer:

1. **[P1 Security - Bearer Token URL]:** Không nhận bearer token trực tiếp qua query params `#customer?token=...`. Chuyển sang đọc phiên an toàn thông qua `customerSession.js` và `sessionStorage`.
2. **[P1 Session Isolation]:** Độc lập giỏ hàng và danh sách đơn của từng bàn theo namespace `lecker_customer_cart:{sessionId}:{clientStorageId}`.
3. **[P1 Idempotency Key]:** Tách riêng attempt key lưu trữ theo từng thao tác (order, waiter, bill) tránh sinh key mới khi retry thất bại.
4. **[P2 API Contract DTO]:** Đồng bộ contract `CustomerOrderService.Order` (`id`, `totalAmount`, `sessionTotalAmount`, `items[].unitPrice`, `items[].lineTotal`).
5. **[P2 Versioning / Optimistic Locking]:** Nhận diện full response từ PATCH/DELETE để cập nhật batch version mới nhất, tránh lỗi `409 Conflict`.
6. **[P2 Feedback Validation]:** Yêu cầu bắt buộc trường họ tên và email, kiểm tra regex định dạng email trước khi cho phép bấm gửi.
7. **[P2 Multi-client Bill Request]:** Bỏ guard local `orders.length === 0` tại `ServiceRequestsView.jsx`, cho phép khách B cùng bàn yêu cầu thanh toán khi khách A đã đặt món.
8. **[P2 Pending Order Snapshot & Cart Preservation]:** Quản lý pending snapshot tại `CustomerContext.jsx`, khóa nút sửa/xóa các món in-flight và chỉ trừ số lượng món đã gửi khi thành công, bảo toàn 100% món mới thêm vào giỏ.
9. **[P2 Menu Pagination]:** Phân trang động `PAGE_SIZE = 20`, đọc `totalElements`, hiển thị nút `XEM THÊM MÓN` và reset trang khi thay đổi nhóm hoặc từ khóa tìm kiếm.

---

## 6. KẾT QUẢ KIỂM THỬ TỔNG THỂ & CI STATUS

* **Frontend Linter:** `npm run lint` (`oxlint --deny-warnings`) đạt **0 errors, 0 warnings** trên toàn bộ 52 files.
* **Frontend Build:** `npm run build` (`vite build`) tạo production bundle tối ưu thành công (331.57 kB).
* **Playwright E2E Tests:** **124/124 tests PASS** trên cả 2 môi trường Desktop Chrome và iPhone 13 Mobile.
* **GitHub Actions CI (5/5 Required Checks PASS):**
  -  `CI / Git Flow`
  -  `CI / PR title`
  -  `CI / Governance`
  -  `CI / Frontend UI`
  -  `CI / Backend MySQL`

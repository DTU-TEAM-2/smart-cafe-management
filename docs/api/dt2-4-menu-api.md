# DT2-4 — Hợp đồng API Xem Menu (Chức năng 1)

Branch: `feature/dt2-4-menu-api`
Bảng sử dụng: `menu_categories`, `menu_items`, `media_assets` (theo `docs/architecture/database-contract.md` và `backend/src/test/resources/schema.sql`).

## Endpoints

| Method | Path | Quyền | Mô tả |
| --- | --- | --- | --- |
| `GET` | `/api/v1/customer/menu/categories` | Public | Danh sách nhóm món `active = TRUE`, kèm số món `active = TRUE` trong từng nhóm |
| `GET` | `/api/v1/customer/menu/items` | Public | Danh sách món `m.active = TRUE AND c.active = TRUE`, hỗ trợ lọc nhóm, từ khóa, trạng thái phục vụ và phân trang |
| `GET` | `/api/v1/customer/menu/items/{itemId}` | Public | Chi tiết một món hợp lệ (`m.active = TRUE AND c.active = TRUE`) |

Các method khác (`POST`, `PUT`, `DELETE`) trên `/api/v1/customer/menu/**` bị từ chối (`403`).

## Query params `GET /api/v1/customer/menu/items`

- `categoryId` (tùy chọn, số nguyên dương): lọc theo nhóm món. Nếu nhóm không tồn tại hoặc `active = FALSE` → `404`. Nếu nhóm tồn tại nhưng chưa có món → `200` với `content: []`, `totalElements: 0`.
- `keyword` (tùy chọn, tối đa 160 ký tự): tìm không phân biệt hoa thường theo `name` hoặc `code`, giữ nguyên ý nghĩa literal của ký tự đặc biệt.
- Tìm kiếm dùng `LIKE ... ESCAPE '!'`; `%`, `_` và `!` trong keyword được hiểu theo nghĩa đen. Dấu `\` cũng là ký tự thường vì không dùng làm escape. Hoạt động ở cả SQL mode mặc định và `NO_BACKSLASH_ESCAPES`; không thay cấu hình SQL mode của DB hệ thống.
- `availableOnly` (tùy chọn, mặc định `false`): khi `true`, chỉ lấy `m.available = TRUE`.
- `page` (mặc định `0`, `0..1000000`) và `size` (mặc định `10`, `1..100`).

## Response mẫu

`GET /api/v1/customer/menu/categories` (`200 OK`):

```json
[
  {
    "id": "1",
    "code": "COFFEE",
    "name": "Cà phê",
    "activeItemCount": 2
  }
]
```

`GET /api/v1/customer/menu/items` (`200 OK`):

```json
{
  "content": [
    {
      "id": "1",
      "code": "CF01",
      "name": "Caramel Latte",
      "price": "45000",
      "imageUrl": "https://example.invalid/latte.jpg",
      "available": true,
      "categoryId": "1",
      "categoryCode": "COFFEE",
      "categoryName": "Cà phê"
    }
  ],
  "totalElements": 1,
  "page": 0,
  "size": 10
}
```

Ghi chú: Bảng `menu_items` trong schema chuẩn hiện không có cột `description`, nên API chưa trả `description` cho tới khi schema được chủ repo phê duyệt bổ sung.

## Mã lỗi (`application/problem+json`)

- `400 Bad Request`: `"Tham số truy vấn thực đơn không hợp lệ."` hoặc `"Mã món không hợp lệ."`
- `404 Not Found`: `"Không tìm thấy nhóm món."` hoặc `"Không tìm thấy món."`
- `503 Service Unavailable`: `"Hiện chưa tải được thực đơn."` (lỗi kết nối/truy vấn DB, không lộ chi tiết SQL/JDBC).

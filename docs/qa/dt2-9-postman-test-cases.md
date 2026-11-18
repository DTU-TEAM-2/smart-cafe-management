# DT2-9 — Test case API món và nhóm món bằng Postman

Ngày soạn: 2026-11-11. Backend tham chiếu: bản tích hợp main của PR #8, nhánh `feature/dt2-9-manage-menu-items-and-categories-backend`. Nguồn: [OpenAPI](../api/dt2-9-menu.yaml), [hướng dẫn backend](../../backend/README.md). Đây là kế hoạch kiểm thử thủ công; **chưa thực thi bằng Postman**, không đánh dấu sẵn Pass.

Đã có [báo cáo chạy tự động HTTP API thật](../reports/dt2-9-api/index.html) theo 46 mục dưới đây: **46 PASS**, dùng Bearer HMAC test-only theo auth chung và provider HTTP giả lập ở TC-41/46 theo lựa chọn người dùng ngày 2026-11-11. Không phải đăng nhập thật, Cloudinary thật hoặc lượt chạy trong ứng dụng Postman. Xem [điều kiện và lỗi CSRF đã sửa](../reports/dt2-9-api/README.md). Khi kiểm thử Cloudinary thật bằng Postman, ghi kết quả smoke riêng, không lấy PASS giả lập làm bằng chứng provider thật.

## 1. Chuẩn bị

1. Chạy MySQL và backend theo hướng dẫn, chỉ trỏ vào schema kiểm thử riêng. API xóa nhóm sẽ ẩn toàn bộ món của nhóm đó. Không dùng mã/dữ liệu thật.
2. Cấu hình `AUTH_TOKEN_SECRET` theo module auth chung; lấy Bearer ADMIN/STAFF hợp lệ từ hệ thống auth được cấp quyền. Role/status/auth_version hiện tại trong `users` quyết định quyền; role trong payload không cấp quyền. Backend này **không có API login hoặc tài khoản mặc định**. Không tự sửa payload token để đổi role.
3. Nếu chưa có token hợp lệ: chạy TC-01–03; đánh dấu các ca cần ADMIN/STAFF là **Blocked — thiếu auth**, không tắt bảo mật để chạy thử.
4. Tạo Environment local trong Postman, chọn environment đó. Token/CSRF chỉ lưu riêng tư tại máy; không chia sẻ/export giá trị thật. Giữ cookie jar bật và dùng nhất quán host `127.0.0.1`, không đổi sang `localhost` giữa các request.

| Biến | Giá trị mẫu / cách lấy |
| --- | --- |
| `baseUrl` | `http://127.0.0.1:8080` |
| `adminToken` | Bearer ADMIN hợp lệ, để trống trong tài liệu/export |
| `staffToken` | Bearer STAFF hợp lệ, để trống trong tài liệu/export |
| `csrfHeader` | Lấy từ `headerName` của GET CSRF |
| `csrfToken` | Lấy từ `token` của GET CSRF |
| `categoryCode` | `PMCF_20261111A` |
| `otherCategoryCode` | `PMTE_20261111A` |
| `itemCode` | `PMITEM_20261111A` |
| `secondItemCode` | `PMITEM2_20261111A` |
| `categoryVersion` | Version mới nhất từ response nhóm; lúc tạo là `0` |
| `itemVersion` | Version mới nhất từ response món; lúc tạo là `0` |

**Mỗi lượt chạy đổi hậu tố mã** ở bốn biến mã. Mã đã xóa logic vẫn được giữ trong DB và không tái dùng. Không đặt `categoryVersion`/`itemVersion` theo số món hoặc theo phỏng đoán.

Thiết lập request thông thường:

- Authorization → Bearer Token → `{{adminToken}}`; ca không token chọn **No Auth**, bảo đảm không còn header Authorization kế thừa.
- POST/PUT JSON: Body → raw → JSON; `Content-Type: application/json`.
- POST/PUT/DELETE/upload: header tên `{{csrfHeader}}`, giá trị `{{csrfToken}}`, cùng cookie session nhận ở GET CSRF. GET không cần header CSRF.
- Upload: Body → form-data, key `file`, kiểu **File**. Bỏ Content-Type JSON đã đặt thủ công để Postman tạo multipart boundary.
- Giữ nguyên dữ liệu gốc khi chạy ca lỗi; sau ca lỗi GET lại để kiểm tra không ghi dữ liệu. Sau PUT thành công, lưu version trả về; ca stale cố ý dùng version cũ.

## 2. Request mẫu và thứ tự chạy

**A. Lấy CSRF:** `GET {{baseUrl}}/api/v1/menu/csrf`, Bearer ADMIN. Kỳ vọng 200 với `token`, `headerName`, `parameterName`; giữ cookie `JSESSIONID`. Có thể dán vào **Scripts → Post-response** của riêng request này:

```javascript
pm.test("CSRF trả 200", () => pm.response.to.have.status(200));
if (pm.response.code === 200) {
  const body = pm.response.json();
  pm.environment.set("csrfHeader", body.headerName);
  pm.environment.set("csrfToken", body.token);
}
```

Khi xóa cookie, đổi host hoặc cookie session hết hạn, gọi lại GET CSRF. Bearer token và CSRF là hai thứ khác nhau.

**B. Tạo nhóm:** `POST {{baseUrl}}/api/v1/menu/categories`, Bearer ADMIN + CSRF:

```json
{
  "code": "{{categoryCode}}",
  "name": "Cà phê Postman",
  "version": 0
}
```

Kỳ vọng 201, `Location` trỏ đến nhóm mới, response có `code`, `name`, `version: 0`, `itemCount: 0`. Tạo thêm nhóm bằng `{{otherCategoryCode}}`, tên `Trà Postman` để thử đổi nhóm.

**C. Tạo món:** `POST {{baseUrl}}/api/v1/menu/items`, Bearer ADMIN + CSRF:

```json
{
  "code": "{{itemCode}}",
  "name": "Caramel Latte Postman",
  "price": 45000,
  "categoryCode": "{{categoryCode}}",
  "imageMediaId": null,
  "available": true,
  "version": 0
}
```

`imageMediaId: null` tạo món chưa có ảnh. Muốn gắn ảnh, upload rồi dùng `mediaId` trả về làm `imageMediaId`; URL đọc từ `media_assets`. Không gửi `imageUrl` hoặc `description` trong body. Không cần Cloudinary cho CRUD không ảnh.

Tạo thêm món bằng `{{secondItemCode}}`, tên `Milk Coffee Postman`, giá 25000, cùng nhóm. Khi PUT, gửi đầy đủ body tương tự với `version: {{itemVersion}}` (không đặt dấu nháy quanh số); không đổi code/categoryCode. PUT nhóm gửi body B với `version: {{categoryVersion}}`.

**Thứ tự:** quyền/CSRF → tạo hai nhóm → tạo hai món → đọc/tìm/lọc/validation → cập nhật/version → xóa món → xóa nhóm có món → hậu kiểm. Ca lỗi tạo mới dùng mã mới chưa tồn tại để lỗi trùng mã không che lỗi cần kiểm tra. Ca race và upload chạy riêng theo điều kiện của chúng.

## 3. Danh sách test case

Trong bảng, `/menu` viết tắt cho `{{baseUrl}}/api/v1/menu`. Mặc định dùng ADMIN hợp lệ và CSRF đúng cho mutation, trừ khi ca ghi khác. Mỗi giá trị biên trong một ô là một lần chạy độc lập.

| ID | Tình huống / request và dữ liệu | Kết quả mong đợi |
| --- | --- | --- |
| TC-01 | No Auth: GET `{{baseUrl}}/actuator/health` | 200, `status=UP`, không lộ chi tiết datasource. |
| TC-02 | No Auth: GET `/menu/items`, `/menu/categories`, `/menu/csrf` | Mỗi request 401. |
| TC-03 | GET `/menu/items`, Bearer `invalid-token`; thử riêng No Auth + header `X-Role: ADMIN` | 401 cho cả hai, không cấp quyền từ header. |
| TC-04 | Bearer STAFF thực: GET `/menu/items`, `/menu/categories`, `/menu/csrf` | Mỗi request 403. |
| TC-05 | Bearer STAFF + CSRF hợp lệ: POST nhóm mẫu B | 403, không tạo nhóm. Lấy CSRF ADMIN trước rồi đổi Bearer sang STAFF, giữ cookie; không có token thì Blocked. |
| TC-06 | ADMIN: GET `/menu/csrf` | 200; ba trường CSRF có giá trị; lưu token/header và cookie. |
| TC-07 | ADMIN: POST nhóm với CSRF header bị bỏ; chạy lại với giá trị CSRF sai | 403 ở mỗi lần; nhóm chưa được tạo. |
| TC-08 | ADMIN: POST nhóm mẫu B, code chưa tồn tại | 201; Location đúng; mã uppercase, version 0, itemCount 0. |
| TC-09 | POST lại nhóm cùng mã, kể cả đổi hoa/thường | 409, `code=MENU_CONFLICT`; nhóm gốc không đổi. |
| TC-10 | POST nhóm mã có ký tự `!` hoặc dài 33 ký tự; tên rỗng/toàn khoảng trắng hoặc dài 121 ký tự | 400, `code=VALIDATION_ERROR`, `fieldErrors` chứa trường sai. |
| TC-11 | POST nhóm code mới, version `1`; riêng bỏ version/null/`-1` | Version 1: 400 `INVALID_VERSION`; các giá trị còn lại: 400 validation. |
| TC-12 | POST nhóm code mới, thêm thuộc tính `role: "ADMIN"`; riêng gửi JSON sai cú pháp | 400 `INVALID_REQUEST`; không tạo dữ liệu. |
| TC-13 | GET `/menu/categories/{{categoryCode}}`; GET `/menu/categories?keyword=Postman&page=0&size=10` | 200; detail đúng nhóm, danh sách có nhóm mẫu và metadata `content,totalElements,page,size`. |
| TC-14 | PUT nhóm B, name đổi thành `Coffee Postman`, version hiện tại | 200; version tăng đúng 1, code giữ nguyên. Lưu categoryVersion mới. |
| TC-15 | PUT nhóm với version cũ; DELETE nhóm với version cũ | 409 `STALE_MENU`; nhóm và món không bị đổi/ẩn. |
| TC-16 | PUT `/menu/categories/{{categoryCode}}`, body code bằng otherCategoryCode | 400 `IMMUTABLE_CODE`, nhóm gốc không đổi. |
| TC-17 | POST món mẫu C | 201; Location đúng; price 45000; categoryName đúng tên nhóm hiện tại; version 0; imageMediaId null và available đúng. |
| TC-18 | POST lại mã món vừa tạo | 409 `MENU_CONFLICT`; chỉ có một món với mã đó. |
| TC-19 | POST món code mới dài 3 hoặc 41 ký tự; tên sau trim dài 5 hoặc 161 ký tự | 400 validation; `fieldErrors` chứa code/name tương ứng. |
| TC-20 | POST món code mới, lần lượt price `5000`, `0`, `-1`, `5000.5`, `1000000000000000000` | 400 `VALIDATION_ERROR`, `fieldErrors.price`; không làm tròn để nhận giá. |
| TC-21 | POST món code mới với price `5001`; riêng mã khác với `999999999999999999` | 201 ở mỗi biên hợp lệ, đọc lại đúng giá. Dọn các món biên sau kiểm tra. |
| TC-22 | POST món code mới, categoryCode `PMNONE_20261111A` chưa tồn tại | 409 `CATEGORY_INACTIVE`; không tạo món. |
| TC-23 | POST món code mới với imageUrl `https://evil.invalid/a.jpg`, `//evil.invalid/a.jpg`, `/images/../a.jpg`, `data:image/png;base64,abc` | 400 `INVALID_REQUEST` vì imageUrl không thuộc input; không tạo món/không tải URL từ server. |
| TC-24 | GET `/menu/items/{{itemCode}}` | 200; code, nhóm, giá, imageMediaId/imageUrl, available và version khớp dữ liệu đã lưu. |
| TC-25 | GET `/menu/items?field=code&keyword={{itemCode}}`; thử riêng mã cùng tiền tố nhưng thiếu ký tự cuối | Mã đầy đủ: 200 có món; mã thiếu: 200 không trả nhầm món do tìm mã chính xác. |
| TC-26 | GET `/menu/items` với Params `field=name`, `keyword=caramel latte postman`; riêng `field=category`, `keyword=coffee postman` sau TC-14. Với fixture tên `Bạc xỉu`/nhóm `Cà phê`, thử `bac xiu`, `BẠC XỈU`, `CA PHE`. | 200; tên/nhóm không phân biệt hoa/thường và dấu theo collation utf8mb4_0900_ai_ci của schema hệ thống. “Literal” nghĩa là `%`, `_`, `!` không mở rộng thành wildcard, không nghĩa là so khớp nhị phân. Nhập khoảng trắng bằng bảng Params. |
| TC-27 | GET `/menu/items?field=price&keyword=45000`; thử `45001` | 200; giá chính xác trả món phù hợp, không trả itemCode khi giá không khớp. Không khẳng định danh sách rỗng nếu schema có dữ liệu khác. |
| TC-28 | GET `/menu/items?categoryCode={{categoryCode}}&page=0&size=1`, rồi page=1 | 200; tối đa một dòng/trang, code tăng dần, không lặp dòng khi dữ liệu không đổi; tổng phản ánh món đang hoạt động của nhóm. |
| TC-29 | GET `/menu/items`, Params keyword lần lượt `%`, `_`, `' OR 1=1 --` | 200; tìm ký tự literal, không trả toàn bộ dữ liệu vì wildcard/SQL injection. Kiểm tra trên dữ liệu mẫu đã biết. |
| TC-30 | GET `/menu/items` với `field=sql`, `size=0`, `size=101`, `page=-1`, hoặc `field=price&keyword=abc` | 400; không lộ SQL/stack. Giá tìm sai có `code=INVALID_PRICE`; tham số sai có `INVALID_REQUEST`. |
| TC-31 | PUT món C, tên `Updated Latte Postman`, price 50000, version hiện tại | 200; dữ liệu cập nhật, version tăng 1. GET detail kiểm tra lại; lưu itemVersion mới. |
| TC-32 | PUT món bằng version trước TC-31; riêng DELETE với version cũ | 409 `STALE_MENU`; giá/tên/version không đổi do request bị từ chối. |
| TC-33 | PUT itemCode nhưng body code khác; riêng giữ code, đổi categoryCode sang otherCategoryCode đang hoạt động | Đổi code: 400 `IMMUTABLE_CODE`; đổi nhóm: 409 `STALE_MENU`. Không chuyển món sang nhóm khác. |
| TC-34 | Hai request PUT cùng một món mới với cùng version, gửi từ hai tab gần đồng thời, body đổi tên khác nhau | Một request 200, một request 409; GET cuối tăng version đúng 1. Gửi tuần tự chỉ kiểm tra stale, chưa chứng minh locking đồng thời. |
| TC-35 | DELETE `/menu/items/{{itemCode}}?version={{itemVersion}}` | 204, body rỗng; GET detail 404 `MENU_NOT_FOUND`, danh sách không còn món đó. |
| TC-36 | DELETE lại món đã ẩn; POST lại đúng mã món đã xóa với version 0 | DELETE 404; POST 409 `MENU_CONFLICT`, mã không được tái sử dụng. |
| TC-37 | DELETE `/menu/items/{{secondItemCode}}` nhưng bỏ query version; riêng version `-1` | 400; secondItemCode vẫn còn hoạt động. |
| TC-38 | DELETE `/menu/categories/{{categoryCode}}?version={{categoryVersion}}` khi secondItemCode còn hoạt động | 204; GET nhóm và secondItemCode đều 404; list không trả nhóm/món đó. Xóa nhóm không bị chặn chỉ vì đang có món. |
| TC-39 | POST món mã mới vào nhóm đã xóa; POST lại mã nhóm đã xóa | Tạo món: 409 `CATEGORY_INACTIVE`; tái dùng mã nhóm: 409 `MENU_CONFLICT`. |
| TC-40 | Đối chiếu DB read-only sau TC-38, bằng công cụ DB được cấp quyền | Hàng nhóm/món vẫn tồn tại, active=false; version món đang hoạt động trước xóa tăng 1. Postman đơn thuần không chứng minh row còn tồn tại. |
| TC-41 | Bộ tự động: POST `/menu/images`, PNG hợp lệ, provider HTTP loopback giả lập trả URL Cloudinary hợp lệ; tạo món tham chiếu mediaId và GET lại | 200 upload, 201 tạo món, 200 đọc lại đúng URL; adapter gửi đúng auth/multipart tới stub. Nhãn PROVIDER GIẢ LẬP, không chứng minh Cloudinary thật. |
| TC-42 | Upload ảnh hợp lệ nhưng môi trường chưa cấu hình Cloudinary | 503 `IMAGE_NOT_CONFIGURED`. Đây là ca lỗi cấu hình dự kiến, không đánh Pass cho TC-41. |
| TC-43 | Upload tệp rỗng hoặc file text đổi đuôi `.jpg`, kích thước dưới giới hạn | 400 `INVALID_IMAGE`, không chấp nhận chỉ dựa vào đuôi/MIME. |
| TC-44 | Upload file lớn hơn 5 MiB (5.242.880 byte) | 413; không lưu/upload ảnh. Với request bị container chặn sớm, ưu tiên kiểm tra HTTP 413 thay vì yêu cầu mọi body giống nhau. |
| TC-45 | Upload PNG/JPEG có width × height >20.000.000 pixel, file dưới 5 MiB | 400 `INVALID_IMAGE`; không dùng tệp không rõ nguồn. |
| TC-46 | Bộ tự động: provider HTTP loopback giả lập trả 503 kèm thông tin nội bộ fixture | API trả 502 `IMAGE_PROVIDER_ERROR`; response không chứa API secret/body upstream. Nhãn PROVIDER GIẢ LẬP, không sửa/gây lỗi trên môi trường Cloudinary thật. |

## 4. Ghi nhận kết quả và dọn dữ liệu

Với mỗi ca, ghi `ID | ngày/người chạy | request và dữ liệu đã che token | HTTP/body thực tế | Pass/Fail/Blocked/Not run | bằng chứng`. Mỗi giá trị biên phải có kết quả riêng. Không đưa Authorization, cookie session hay CSRF vào ảnh/chia sẻ collection. Lỗi security 401/403 có thể không có body; không ép phải là Problem Details.

Tiêu chí Pass: đúng status **và** đúng dữ liệu/trạng thái sau thao tác. DELETE 204 không parse JSON. `available=false` chỉ biểu diễn hết món, không đồng nghĩa `active=false`/xóa logic. Dọn các món biên/race và nhóm thử còn lại bằng API với version mới nhất; không xóa thủ công lịch sử migration. Không cần tái tạo mã cũ để chạy lại, hãy dùng hậu tố mới.

Ví dụ assertion riêng cho request tạo nhóm/món ở Post-response:

```javascript
pm.test("Tạo mới thành công", () => pm.response.to.have.status(201));
pm.test("Có Location", () => pm.response.to.have.header("Location"));
```

Tài liệu Postman chính thức: [Post-response assertions](https://learning.postman.com/docs/tests-and-scripts/write-scripts/test-examples/), [Environment variables](https://learning.postman.com/docs/use/send-requests/variables/environment-variables), [Cookie manager](https://learning.postman.com/docs/use/send-requests/response-data/cookies).

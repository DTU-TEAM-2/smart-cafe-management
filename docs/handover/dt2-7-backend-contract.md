# DT2-7 — hợp đồng backend local

## Nguồn và phạm vi

- [DT2-7](https://duymanhdnvn20092005.atlassian.net/browse/DT2-7), đọc ngày 11/11/2026: danh sách, đăng, cập nhật, xóa tin; upload ảnh qua Firebase; socket thông báo thay đổi cho client khác. Ticket xác nhận SRS text chưa mô tả chi tiết quyền/trường của màn hình tin tức.
- Ban đầu người dùng yêu cầu chỉ làm BE, review và sửa local, chưa commit/push. Ngày 11/11 người dùng giao lựa chọn cách triển khai hợp lý và yêu cầu đưa phần chưa rõ vào Q&A để hỏi khách hàng. Chỉ thị tiếp theo mở rộng sang toàn bộ Task 7 gồm [React UI](dt2-7-frontend-contract.md); lệnh chưa commit/push vẫn giữ nguyên.
- BE Spring Boot, MySQL; schema gốc theo ảnh 11/11 và DDL thực tế, cộng phần mở rộng hai cột được chủ repo duyệt tại ADR-005. Ban đầu SQL chỉ áp dụng trên _test; sau chỉ thị riêng của chủ repo tại ADR-006, đã áp dụng cùng SQL trên smart_cafe local. Không seed hoặc ghi row nghiệp vụ.

## Q&A đã trả lời và quyết định triển khai

Nguồn Q&A gốc: No.4 chỉ quản lý đăng/sửa/xóa, No.5 mọi trường hợp xóa mềm/logic, No.10 nhóm thiết kế và chốt prototype. [Phản hồi Q&A 19–20 ngày 11/11](dt2-7-qa-replies-2026-11-11.md) bổ sung lựa chọn gửi đến Nhân viên/Khách hàng và chốt socket chỉ thông báo nhân viên.

Chủ repo đã xem đề xuất hai cột, được giải thích tác dụng lưu lựa chọn và xác nhận trong chat “vậy duyệt”. [ADR-005](../architecture/decisions.md) ghi phạm vi schema được chấp thuận: thêm audience_staff/audience_customer và CHECK 0/1; chỉ triển khai và test local, chưa ALTER database hệ thống hoặc commit/push. SQL versioned không tự chạy khi khởi động. Sau đó chủ repo yêu cầu áp dụng DB hệ thống; [ADR-006](../architecture/decisions.md) ghi nhận migration đã chạy và kiểm chứng trên smart_cafe local. Quyết định này mở rộng phạm vi áp dụng schema, không cho phép commit/push hoặc tạo dữ liệu thử.

- ADMIN quản lý mọi bài, kể cả nháp/lưu trữ và bài cũ chưa gán nhóm.
- STAFF/CUSTOMER đã xác thực chỉ đọc PUBLISHED thuộc nhóm mình, kể cả khi lọc trạng thái hoặc đoán ID bài/ảnh. Không lấy role/audiences từ query làm quyền đọc. Bài ngoài quyền trả 404 để không tiết lộ tồn tại.
- Chưa mở GET ẩn danh: phản hồi nêu “khách hàng”, chưa chốt guest/table-session auth. Không thêm tài khoản hoặc giả đăng nhập để lấp phần tích hợp auth.
- ADMIN chọn một hoặc cả hai nhóm khi tạo bài. Bài cũ mặc định cả hai cột 0, chỉ ADMIN thấy đến khi chọn nhóm; không tự phát tán bài cũ.
- DELETE chuyển ARCHIVED, giữ bài và liên kết ảnh; mặc định danh sách ADMIN loại ARCHIVED. ADMIN lọc/đọc/khôi phục bằng PUT được; STAFF/CUSTOMER không thấy bài lưu trữ.
- DRAFT mặc định; PUBLISHED có published_at UTC; về DRAFT đặt null để đúng CHECK. Body là văn bản thường; FE phải escape. Các giới hạn ảnh/editor là thiết kế triển khai theo QA No.10.

## HTTP API

Các endpoint ghi yêu cầu ADMIN; endpoint đọc dành cho ADMIN/STAFF/CUSTOMER với kiểm tra quyền theo bài tại DB. Thiếu/sai/hết hạn token, user bị khóa, auth_version không khớp DB hoặc sai quyền trả 403. Không phát hành token/tạo tài khoản trong module này.

Consumer đối chiếu module auth [PR #7](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/7), revision `2ee793d5d0365c5dfc43f4ae59a13cd8d8aaefa8`. Login thật `POST /api/v1/auth/login` do module đó cung cấp, trả token `base64url(payload).hex(HMAC-SHA256)` với payload `id:username:role:authVersion:expiryMillis`. Hai module phải dùng cùng AUTH_TOKEN_SECRET tối thiểu 32 byte. Consumer giữ tương thích dạng trước `id:role:authVersion:expiry` và legacy `id:username:role:expiry` (chỉ auth_version=0). Expiry nhận giây/millisecond theo contract issuer; giá trị lớn hơn 10 tỷ là millisecond. Username có thể trống ở overload issuer, khi đó resolve bằng ID trong DB. ID, status ACTIVE và auth_version phải khớp hiện hành; role trong token không cấp quyền thay cho role DB. Reset grant không phải bearer đăng nhập. UI Task 7 gọi endpoint thật của DT2-2 và xác minh qua consumer trước khi giữ phiên; DT2-2 vẫn sở hữu issuer và chính sách auth. Task 7 không tự thêm endpoint đăng nhập hoặc merge source auth.

| Method | Đường dẫn | Thành công | Ghi chú |
| --- | --- | --- | --- |
| GET | /api/v1/news | 200 | ADMIN theo filter quản trị; STAFF/CUSTOMER chỉ PUBLISHED đúng nhóm; page 0, size 1–100 |
| GET | /api/v1/news/{id} | 200 + ETag | ID số; ADMIN đọc mọi trạng thái; STAFF/CUSTOMER theo nhóm; ngoài quyền 404 |
| POST | /api/v1/news | 201 + Location + ETag | Tạo bài, version bắt đầu 0 |
| PUT | /api/v1/news/{id} | 200 + ETag | Bắt buộc If-Match của bản đang sửa |
| DELETE | /api/v1/news/{id} | 204 | Bắt buộc If-Match; lưu trữ, không xóa vật lý |
| POST | /api/v1/news/images | 201 + Location | multipart/form-data, part file |
| GET | /api/v1/news/images/{id} | 200 bytes | ADMIN đọc ảnh upload trước lưu bài; STAFF/CUSTOMER cần ít nhất một bài PUBLISHED đúng nhóm liên kết ảnh; no-store/nosniff |

JSON camelCase. POST bắt buộc thêm audiences, ví dụ ["STAFF"], ["CUSTOMER"] hoặc ["STAFF", "CUSTOMER"]. Không nhận ADMIN, giá trị lạ, null, [] hoặc nhóm trùng khi tạo. PUT bỏ audiences/null thì giữ nhóm cũ, truyền [] hoặc nhóm sai/trùng trả 400. Việc chọn nhóm chỉ thay đối tượng đọc, không cấp quyền quản trị. Nhóm nhận tin được ghi nguyên tử cùng bài/ảnh và If-Match.

Request POST/PUT: title (bắt buộc, tối đa 200 ký tự Unicode), summary (nullable, tối đa 500), body (bắt buộc), slug (tùy chọn), status (DRAFT/PUBLISHED/ARCHIVED), imageMediaIds (tối đa 20 ID nguyên dương không trùng). ID ảnh chấp nhận số nguyên JSON hoặc chuỗi chữ số, từ chối số thập phân/số vượt Long. ID response là chuỗi để tránh mất độ chính xác trong JavaScript.

Slug được chuẩn hóa ASCII chữ thường, chữ số và dấu gạch ngang, tối đa 180 ký tự. Bỏ slug khi POST thì sinh từ title và xử lý trùng bằng hậu tố; bỏ slug khi PUT thì giữ slug cũ. Trùng custom slug trả 409. PUT bắt buộc title/body; bỏ summary sẽ xóa summary; bỏ status giữ trạng thái; bỏ imageMediaIds/null giữ ảnh cũ, [] gỡ toàn bộ liên kết. Server tự quản lý author/updater, published_at, timestamps và version; trường giả mạo từ request không được dùng.

Sort allowlist: publishedAt, createdAt, updatedAt, id, mỗi trường kèm ,asc hoặc ,desc; mặc định publishedAt,desc, có id làm tie-breaker. status filter chỉ nhận ba giá trị DDL (không phân biệt hoa/thường). page/size/sort/status sai trả 400 Problem Details; offset dùng long, không tràn int.

ETag là phiên bản dạng `"0"`, `"1"`... Client lấy từ GET chi tiết hoặc response tạo/sửa, gửi lại ở If-Match khi PUT/DELETE. Thiếu If-Match trả 428; sai cú pháp trả 400; bản cũ trả 412; không tồn tại trả 404. Không chấp nhận wildcard/weak ETag. Lock hàng bài viết trong transaction trước khi so phiên bản; cập nhật bài, ảnh và version nguyên tử. Lặp DELETE trên bản đã ARCHIVED với ETag hiện tại trả 204 và không tăng version thêm.

Response danh sách: content, page, size, totalElements, totalPages, last. Summary có id, slug, title, summary, status, publishedAt, createdAt, updatedAt, authorName, thumbnailUrl. Summary và detail đều có audiences theo thứ tự STAFF rồi CUSTOMER. Detail bổ sung body, authorId, updatedByUserId, version, images[{mediaId,secureUrl,sortOrder}]. Bài cũ 0/0 trả audiences: [] cho ADMIN. Thời gian ISO 8601 UTC; ghi/đọc DATETIME theo UTC độc lập timezone JVM/session.

Lỗi MVC thuộc module dùng application/problem+json, không trả SQL/stack/credential. Xung đột ghi database trả 409: code NEWS_SLUG_TAKEN cho trùng slug, NEWS_WRITE_BUSY cho khóa/ghi đồng thời, DATA_CONFLICT cho ràng buộc dữ liệu khác. Không hướng dẫn đổi slug với mọi lỗi 409. Dịch vụ/transaction không khả dụng trả 503. Error URI dùng urn:smartcafe:problem:... hoặc giá trị mặc định Problem Details; không phụ thuộc domain triển khai.

## Ảnh Firebase

Máy mới làm theo [cấu hình Firebase cho nhóm](dt2-7-firebase-setup.md) và [quickstart local](../../backend/firebase-emulator/README.md). CLI local được khóa version bằng package-lock, có cấu hình mẫu, đọc profile/endpoint qua `start-be.ps1 -EnvFile`; không phụ thuộc đường dẫn hoặc key của tác giả. Launcher emulator lưu export khi thoát Ctrl+C và tự import lần sau; giữ export cùng DB `_test`, không đưa dữ liệu này vào Git.

Chủ repo nhắc lại cấm tuyệt đối thanh toán ngày 12/11: không billing/thẻ/Blaze/trial yêu cầu thanh toán. `smartcafe.news.firebase.cloud-enabled=false` khóa live adapter trước cả tra ADC hoặc gọi bucket; không mở flag này trong phạm vi hiện tại. Ảnh local chỉ dùng emulator, không coi cloud là dependency phải bật bằng trả phí.

JPEG được áp dụng hướng EXIF 1–8 (gồm xoay/lật) trước khi re-encode bỏ metadata, để ảnh điện thoại không mất hướng hiển thị. `width/height` trả về và metadata MySQL phản ánh kích thước sau chuẩn hóa. EXIF hỏng bị bỏ, không giữ metadata riêng hoặc làm hỏng ảnh có pixel giải mã được. Dùng dependency `com.drewnoakes:metadata-extractor:2.22.0` Apache-2.0, chỉ chạy ExifReader, không đọc XMP/IPTC hoặc tải tài nguyên ngoài. Version được pin trong pom; [release chính thức](https://github.com/drewnoakes/metadata-extractor/releases/tag/2.22.0). Có kiểm đủ 8 hướng và offset EXIF hỏng trên ảnh synthetic, không dùng ảnh cá nhân làm fixture.

Lỗi 409 có `code` để phân biệt `NEWS_SLUG_TAKEN` (đường dẫn trùng, kể cả race UNIQUE), `NEWS_WRITE_BUSY` (tranh chấp/khóa ghi) và `DATA_CONFLICT` (trùng dữ liệu khác). Không trả SQL. UI không hướng người dùng đổi đường dẫn khi lỗi thực tế là ghi đồng thời; giữ nguyên draft.

Upload PNG/JPEG thực sự, tối đa 5 MiB, tối đa 6000 px mỗi chiều và 12 triệu pixel; MIME khai báo phải khớp nội dung. Decode/re-encode để bỏ metadata và nội dung đính kèm; không dùng tên file do client cung cấp. SVG và file giả ảnh bị từ chối. Các giới hạn vận hành này là đề xuất cần thể hiện trong prototype theo Q&A No.10, không phải giới hạn lấy nguyên từ SRS.

Firebase Admin SDK dùng Application Default Credentials và NEWS_FIREBASE_BUCKET. Khóa service account để ngoài repo, chỉ cấu hình đường dẫn qua GOOGLE_APPLICATION_CREDENTIALS hoặc dùng workload identity của môi trường. Không thay MySQL bằng Firestore/Firebase Auth. Thiếu cấu hình trả 503 khi gọi upload, không làm startup giả vờ kết nối thành công. Backend không tạo bucket, bật billing, đổi ACL hoặc cấp quyền public.

Ngày 11/11/2026 chủ repo chốt chỉ dùng gói miễn phí, không thanh toán. Project `smart-cafe-management-47578` trên tài khoản được chỉ định đã ở Spark ($0/tháng); console yêu cầu Blaze mới dùng Storage. Chưa có bucket cloud hoặc key, không liên kết billing và không coi cloud đã được nghiệm thu.

Kiểm thử ảnh miễn phí dùng Firebase Storage Emulator chính thức, project local `demo-smart-cafe-news`. Bật rõ profile `firebase-emulator` và `NEWS_FIREBASE_EMULATOR_HOST=http://127.0.0.1:<port>`; adapter từ chối hostname ngoài `127.0.0.1`, URL có credential/path/query/fragment và database không có hậu tố `_test`. Bucket local cố định `demo-smart-cafe-news.appspot.com`; không dùng ADC/key hoặc project cloud. Java dùng Google Cloud object API đã có trong dependency Firebase vì emulator không hỗ trợ API đọc metadata bucket của Admin Java; adapter cloud vẫn dùng Firebase Admin SDK mặc định. Metadata local vẫn qua MySQL chuẩn, API phân quyền và blob URL không đổi. Profile này chỉ dành cho kiểm thử riêng, không phải cách bật Storage cloud trên Spark. Xem [giới hạn emulator chính thức](https://firebase.google.com/docs/emulator-suite/connect_storage#how_the_cloud_storage_for_firebase_emulator_differs_from_production).

Upload trả mediaId, url, mimeType, sizeBytes, width, height. Trước tiên upload, sau đó lưu metadata vào media_assets hiện có với provider FIREBASE. Nếu transaction metadata rollback, dọn object đã upload; khi kết quả transaction không xác định thì giữ object và ghi nhận cần đối soát. Dọn object thất bại là giới hạn cần vận hành xử lý, không giả vờ có distributed transaction với Firebase.

DB lưu canonical HTTPS object URL không chứa token. Với ảnh do module này upload, detail/thumbnail trả đường dẫn backend /api/v1/news/images/{id}. Client phải tải ảnh bằng Authorization rồi tạo blob URL; thẻ img đơn thuần không tự gửi bearer. Backend kiểm quyền bài trước khi dùng SDK đọc bucket riêng; không lưu URL ký có hạn hoặc Firebase download token trong DB. Media cũ ngoài namespace news giữ URL hiện có, không tự đổi quyền dịch vụ khác.

Upload tạo media độc lập để form dùng khi lưu bài. Hủy form có thể để lại ảnh chưa liên kết; không có job tự xóa khi chưa có chính sách retention/ownership của module media. Không tự xóa media dùng chung khi gỡ ảnh/lưu trữ bài.

## Socket

WebSocket native /api/v1/news/events, cùng origin. HTTP upgrade được allowlist riêng; chưa cấp quyền nhận dữ liệu. Frame text đầu trong 10 giây là Bearer <token>. Server kiểm chữ ký/expiry và DB role STAFF, ACTIVE, auth_version khớp token rồi trả {"type":"READY"}. CUSTOMER và ADMIN bị đóng 1008; quyền quản lý REST của ADMIN không bị ảnh hưởng. Không có token trong URL/cookie.

Sau transaction commit, chỉ khi thay đổi ảnh hưởng danh sách STAFF (bài PUBLISHED có audience_staff=1 ở trước hoặc sau thay đổi), server gửi {"type":"NEWS_CHANGED"}. Không gửi ID, title, body hoặc nhóm nhận tin trong payload socket. Nháp, thay đổi bài chỉ dành CUSTOMER và rollback không gây thông báo. Khi bài bỏ nhóm STAFF, chuyển nháp hoặc ARCHIVED, STAFF được báo để reload và bỏ bài khỏi danh sách.

Không gửi cho khách hàng. Không tự mở socket cho ADMIN vì đáp án chỉ nói nhân viên. Quyền/expiry được kiểm lại trước gửi và định kỳ; bị thu hồi thì đóng kết nối. Client không được publish, text bổ sung/binary bị từ chối.

BE cung cấp tín hiệu để FE có thể reload danh sách hoặc hiện thông báo; phản hồi 20 chốt người nhận, không tự khẳng định khách hàng đã chọn chi tiết UI. Kênh trong bộ nhớ một instance, không replay/durable delivery/multi-instance broadcast. Reconnect phải đọc REST; không tuyên bố exactly-once.

## Mapping dữ liệu

- news_posts: id, slug, title, summary, body, status, author_user_id, updated_by_user_id, published_at, created_at, updated_at, version, audience_staff, audience_customer. Hai cột audience thuộc phần mở rộng ADR-005, đã áp dụng vào smart_cafe local theo chỉ thị riêng ADR-006.
- news_images: news_id, media_id, sort_order, giữ uniqueness/FK thực tế.
- media_assets: id, provider, provider_asset_id, secure_url, mime_type, size_bytes, width_px, height_px, created_at, deleted_at.
- users: id, username, role, status, auth_version, full_name theo contract auth/hiển thị hiện có.

Đối chiếu DDL read-only và phạm vi được xác minh tại [database contract](../architecture/database-contract.md). Bằng chứng chạy test, báo cáo review và bản diff nằm ngoài repo theo yêu cầu chủ repo; tài liệu này mô tả hợp đồng, không phải chứng nhận nghiệm thu.

## Tích hợp main ngày 12/11/2026

Theo yêu cầu chủ repo, đã lấy main revision 8476ee30536fdd01b521cb53e5bb22d38b3b5833 bằng fast-forward (23 commit có sẵn, không tạo commit mới), giữ toàn bộ thay đổi local Task 7. Chín file xung đột nội dung đã được ghép; SecurityConfig giữ chain/quyền/CSRF và session strategy của main đồng thời bổ sung news. HomeShell giữ cả Sales và News, launcher nhận cấu hình cả Menu và Firebase Emulator. HeaderTokenFilter giữ constructor (JdbcClient, String) cho MenuSecurity và dùng cùng verifier kiểm DB role/status/auth_version.

Kiểm thử sau tích hợp phát hiện MenuErrors global bắt nhầm lỗi News; đã giới hạn advice vào MenuController, giữ mapping lỗi riêng của từng module. Hai fixture service_requests trong SalesMySqlTest được đặt created_at theo cùng Clock cố định để không vi phạm CHECK khi chạy ngày khác. Không sửa đồng hồ nghiệp vụ hoặc constraint/schema.

Maven verify trên MySQL riêng đạt 126/126, UI desktop/mobile 68/68; tích hợp HTTP/WebSocket/browser/ảnh với auth PR #7 revision fbaa952 đạt 113/113. Auth vẫn chạy riêng để kiểm thử, chưa nhập PR chưa được duyệt vào source. Log, bản sao trước tích hợp và diff ở F:/CODEGYM/_local-reviews/dt2-7-main-sync-2026-11-12, ngoài Git. Chưa stage/commit/push hoặc merge PR trên GitHub.

## Bàn giao PR ngày 13/11/2026

Chủ repo yêu cầu “fix rồi đẩy lên, open luôn”: cho phép commit/push nhánh `feature/dt2-7-news-backend` và mở PR vào `develop`, thay giới hạn chỉ làm local trước đó. Đã ghép baseline `develop@79896a9`, giữ QR khách hàng, menu preview và workflow Postman mới. Không nhập source issuer DT2-2 chưa merge; đăng nhập cần module đó hoặc proxy tới instance thật, cùng DB và secret. Firebase chỉ dùng emulator miễn phí trên MySQL `_test`; không bật cloud/billing. PR chờ review, chưa merge hoặc deploy.

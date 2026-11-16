# Quyết định triển khai migration để bàn giao Auth

Ngày 13/11/2026. Người giao việc là owner NV-DuyManh. Các lựa chọn kỹ thuật dưới đây do Codex chọn theo yêu cầu bàn giao artifact và quyết định cụ thể. Trạng thái là phương án triển khai đã diễn tập, đang chờ đưa vào code PR #7 và review theo Git Flow.

## Đích tích hợp

Chọn cấu trúc của Navicat Premium 16 → locall → smart_cafe làm mốc cho bản tích hợp hiện tại. Nó có 20 bảng nghiệp vụ và 4 view. Hai cột audience của news_posts được giữ theo phần mở rộng News đã có trong nguồn đối chiếu. DDL lấy từ SHOW CREATE, sắp thứ tự FK, giữ kiểu, precision, nullable/default, index và CHECK. Bộ đếm AUTO_INCREMENT theo dữ liệu và tài khoản DEFINER riêng được bỏ khỏi bản provision. SQL SECURITY của view được giữ.

Artifact thực thi là db/smart-cafe/V20261113_01__smart_cafe_schema.sql. SHA-256 là 84cb9f2e99073ea73b117e828dc7f144928052a8f8345cde6f903b82280e4b5e. Manifest lưu source/ref và version. Mốc 20261113.1 được chọn cho bộ bàn giao này sau khi rà các migration trên develop và các PR được fetch. Việc cấp mốc cho toàn nhóm cần được ghi vào PR DB khi đưa artifact vào repo.

## Version, location và cấu hình

- Migration file là V20261113_01__smart_cafe_schema.sql.
- Flyway hiểu version là 20261113.1.
- Runtime location được chọn là classpath:db/smart-cafe.
- Khi chạy provision riêng từ thư mục artifact, location là filesystem:<đường-dẫn-tuyệt-đối>/db/smart-cafe.
- Bảng history là flyway_schema_history.
- Mốc baseline thủ công là 20261113.1, description chính xác là Smart Cafe verified schema 2026-11-13.
- Giữ spring.flyway.enabled=true, spring.flyway.validate-on-migrate=true, spring.flyway.baseline-on-migrate=false và spring.sql.init.mode=never. Session DB dùng UTC.

## Ba đường đi

| Môi trường | Thao tác đã chọn | Điều kiện và kết quả |
| --- | --- | --- |
| DB mới hoàn toàn trống | Chạy provision Flyway riêng bằng artifact, sau đó startup ứng dụng | migrate 1, validate đạt, lần tiếp theo 0. History ghi SQL version 20261113.1. |
| Navicat locall / smart_cafe hiện tại có đủ schema, chưa có history | Kiểm fingerprint schema, dừng writer trong lúc ghi nhận, backup và kiểm restore, rồi chạy explicit Flyway baseline tại mốc đã nêu | History có một marker BASELINE. DDL tạo bảng không chạy lại. migrate 0, validate đạt, dữ liệu nghiệp vụ được giữ. |
| Instance legacy trong snapshot PR #7 có V001–V008 | Giữ nguyên lineage và dữ liệu hiện có, xử lý bằng kế hoạch nâng cấp riêng | Cần truy vấn lại đúng instance, mapping dữ liệu và version nâng cấp sau kiểm kê V009/V010. Giữ các bảng legacy cho tới khi mapping và đối soát được chốt. |

## Xử lý Auth-local V001/V002

PR #7 cần ngừng quét classpath:db/migration trong profile cho đích mới này và chuyển sang location nêu trên. Giữ nguyên byte và nguồn gốc các file đã có trong quá trình review, tránh sửa hoặc giả tạo history. Nếu có môi trường đã chạy Auth-local V001/V002, môi trường đó giữ cấu hình/lineage cũ tới khi có đường chuyển được kiểm thử. Không dùng baseline mới để che history đã tồn tại.

## Thay đổi code cần người phụ trách Auth thực hiện

1. Đưa artifact đã ghim vào backend/src/main/resources/db/smart-cafe, ghi manifest trong docs/database và cập nhật cấu hình cùng PR.
2. Giữ startup từ chối schema chưa có history. Provision DB trống bằng lệnh riêng trước startup.
3. DatabasePreflight nhận cả history SQL version 20261113.1 của DB tạo mới và marker BASELINE version 20261113.1 với description nêu trên của DB onboarding. Kiểm schema đã đối chiếu trước baseline vì marker không chứa checksum DDL. Tiếp tục kiểm schema/PK/FK/constraint/view cần thiết, UTC và lineage khi startup.
4. Giữ native validation cho tất cả SQL migration đã áp dụng và cho phép các migration tiếp theo trong manifest đã review. Bổ sung test một migration hợp lệ kế tiếp, checksum drift, marker sai, history khác và schema thiếu cấu trúc. Kiểm tra một dòng marker cố định trong bản rehearsal chỉ là probe.
5. Chạy startup Spring Boot với Flyway bật bằng chính version 20261113.1, rồi Auth/OTP/Home và consumer liên quan. Ca này còn chờ thay code. Kết quả startup cũ với version test 1 được giữ làm bằng chứng trước đó.

## Đối với legacy

V001–V008 giữ nguyên byte và thứ tự cài thực tế. V009/V010 đã có ở repo legacy, dùng category_code và UNCATEGORIZED. Đích hiện tại dùng category_id. session_order_items/bill_items cũ thiếu nguồn mapping order_item_id, actor và payment cần đối soát dữ liệu trước khi chuyển. Đề xuất thêm 11 bảng chỉ là bước mở rộng đã thử, chưa đủ để app dùng toàn bộ schema mới. Không áp file provision này lên instance legacy.

## Kiểm chứng và bước tiếp theo

Native Flyway trên MySQL cách ly với chính artifact/version 20261113.1 đã đạt các ca DB mới, từ chối auto-baseline, explicit baseline DB hiện hữu, validate và migrate lặp. Trước/sau có dữ liệu tổng hợp đủ 20 bảng, digest và DDL khớp. Xem evidence/version-20261113.1-results.json.

DDL và history thật trong smart_cafe chưa được ghi. Bước ghi cuối cần đối chiếu lại đúng instance/schema và bản backup, dùng artifact đã review, có window dừng writer. Người phụ trách Auth kiểm code preflight/config và chạy startup bằng version mới trước khi owner quyết định áp marker lên DB đang sử dụng.

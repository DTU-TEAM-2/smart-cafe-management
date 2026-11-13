# DT2-2 — Provision và baseline sau phản hồi Database

PR [#7](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/7), nhánh `feature/cf-03-auth-recovery` → `develop`. Điểm bắt đầu `0076bb27062b3be32f1865cd9df9f5ac42d87da2`, develop `5cc4132bdd13aff4720dd4031d5f75d69fbb0606`. Nguồn mô hình là [ERD 11/11](evidence/system-database-reference-2026-11-11.jpg). Phản hồi Database mô tả môi trường Navicat `locall`, MySQL 8.0.44. Kết nối workspace kiểm tra read-only trả MySQL 8.0.45, 13 bảng, 0 view, có history. Đây là hai quan sát từ hai môi trường.

| Môi trường | Bằng chứng | Quyết định còn thiếu |
| --- | --- | --- |
| DB local người phụ trách | Báo cáo 20 bảng + 4 view, chưa có Flyway history. News có `audience_staff`, `audience_customer`, `ck_news_audience_flags`. Hai FK `session_clients.customer_user_id` và `feedback.customer_user_id` cho phép NULL. | Xác nhận môi trường này là đích tích hợp và gửi DDL-only chính thức. |
| DB legacy workspace | 13 bảng kể cả history, 0 view. Tám script V001–V008 đối chiếu được nguồn và checksum. V009/V010 legacy có category_code và UNCATEGORIZED. | Xác nhận có nâng cấp môi trường này hay không. Nếu có, cần kế hoạch riêng cho mapping Menu, Order, Bill, Payment và worker OTP. |
| Fixture repo | `schema.sql` có 20 bảng + 4 view, thiếu audience flags của News. Dùng để kiểm thử. | DDL nền được duyệt cần kèm ref và checksum riêng. |
| Chuỗi Auth trong PR | `classpath:db/migration` chứa V001/V002 Auth khác script cùng số trong legacy. Kiểm thử đã áp dụng các script Auth trên các schema biệt lập. | Kiểm kê instance dùng từng chuỗi ngoài môi trường test. Chốt cách giữ tương thích với từng history thực tế. |

## Thay đổi đã triển khai

- `SecurityConfig.accessDeniedHandler` đặt UTF-8 trước `getWriter()`. Phản hồi 403 vẫn dùng `application/problem+json`, `FORBIDDEN` và thông báo tiếng Việt hiện có. Regression qua Tomcat/MySQL lấy bearer bằng endpoint login, xác nhận token qua `/auth/me`, rồi kiểm bytes UTF-8 và JSON cho STAFF → admin dashboard và CUSTOMER → Home notifications.
- `DatabasePreflight` kiểm sự hiện diện của bốn view đã được ghi nhận trong database contract và từ chối history rỗng trước khi Flyway chạy. Kiểm tra này xác nhận tên/loại view. Định nghĩa view, cột, FK, CHECK, default và collation vẫn cần đối chiếu với DDL chính thức. `DatabaseMigrationConfiguration` tiếp tục kiểm tra schema trước native Flyway với validation bật và automatic baseline tắt.
- Regression Flyway bật trên schema biệt lập kiểm history rỗng, thiếu view/cột và marker BASELINE chưa được duyệt. Test dùng native Flyway baseline với version **002 chỉ trong test**, xác minh đúng một dòng BASELINE, validate thành công, hai lượt migrate áp dụng 0, và startup vẫn từ chối marker theo policy hiện hành. Dữ liệu users, danh mục đối tượng và history được so sánh trước/sau. Không gán version production từ thử nghiệm này.

## Quy trình đề xuất sau khi owner chốt

1. **Chốt nguồn.** Cung cấp định danh instance/schema qua kênh riêng, DDL-only đủ 20 bảng/4 view, News ADR và DDL mở rộng, cùng Git ref bất biến và checksum. Chốt migration location, version/script/checksum của chuỗi mới, marker baseline, cùng ma trận instance đã dùng Auth V001/V002 hoặc legacy V001–V010. Giữ nguyên byte của script đã áp dụng. Nếu DDL nền đi PR riêng từ develop, review/merge PR DDL nền trước, sau đó đồng bộ PR #7 với develop và nghiệm thu revision kết hợp.
2. **Provision DB mới.** DBA xác minh đúng instance, tên schema và schema thật sự trống, kể cả views/routines/triggers/events. Dùng native Flyway migrate riêng với artifact migration đã duyệt, `baselineOnMigrate=false`, `validateOnMigrate=true`, UTC và tài khoản migration có quyền phù hợp. So sánh `SHOW CREATE TABLE/VIEW` với DDL chuẩn, xác nhận đủ 20 bảng/4 view và đúng history. Sau đó khởi động app với Flyway bật, xác minh chạy lại 0 migration. DDL và version còn chờ duyệt nên bước này chưa có lệnh production có thể chạy.
3. **Nhận quản lý schema 20 bảng hiện hữu.** App phải chặn startup trước khi có history. DBA xác minh instance/schema, so sánh toàn bộ DDL với artifact đã duyệt, chụp backup và thử restore trên `_test`. Trong cửa sổ bảo trì dùng native `flyway baseline` với đúng version và location đã được phê duyệt. Kỳ vọng chỉ tạo `flyway_schema_history` và marker BASELINE. Chạy validate và migrate, so sánh toàn bộ DDL và dữ liệu trước/sau. Không chạy lại migration tạo nền. Marker BASELINE không mang checksum của DDL được tạo trước đó, vì vậy phép đối chiếu DDL phải hoàn tất trước khi baseline.
4. **Nối policy với chuỗi đã chốt.** Chỉ nhận marker BASELINE được duyệt hoặc lịch sử SQL tương ứng. Từ chối marker sai, history rỗng/hỏng, script lạ, checksum lệch và schema thiếu cấu trúc. Dùng native Flyway validate/migrate để áp dụng version tiếp theo, rồi kiểm lại schema. Không yêu cầu history mãi mãi chỉ có một dòng. Kiểm thử version tiếp theo, migrate lặp 0 và startup thực tế với Flyway bật trước khi bàn giao. Không thêm một ngoại lệ cấu hình để chấp nhận marker bất kỳ.
5. **Legacy và khôi phục.** Giữ history legacy và mã nguồn đã xác minh, tách riêng thiết kế chuyển đổi nếu DBA xác nhận cần nâng cấp. Không baseline schema legacy theo mốc 20 bảng. Nếu có lỗi dừng rollout, giữ backup/history, xác định cách restore hoặc sửa tiến theo với DBA. Không sửa checksum, repair để bỏ qua drift, xóa history hay đổi bảng history.

## Nghiệm thu còn phụ thuộc nguồn

Provision đầy đủ, baseline đúng marker được duyệt, startup Flyway bật trên đích và migration tiếp theo của chuỗi mới hiện **BLOCKED**. Backup/restore và HTTP trên fixture lịch sử là bằng chứng độc lập, không thay cho nghiệm thu đích. News audience chưa thể kiểm thử trên fixture hiện tại. Kế hoạch legacy cần quyết định riêng. Không áp dụng DDL/DML hoặc Flyway lên `smart_cafe` dùng chung.

## Kết quả kiểm thử local

Commit UTF-8 riêng là `3698976fc771c85110bb1f9bfc08b928643f8a21`. Phần preflight và tài liệu nằm ở commit kế tiếp trên cùng nhánh. Các kiểm thử Java chạy trên source cuối trước commit đó. Chỉ cập nhật tài liệu sau lượt full suite. Báo cáo Git cuối ghi SHA chứa đầy đủ source đã kiểm thử.

| Tiêu chí | Kết quả quan sát | Phạm vi và mode |
| --- | --- | --- |
| UTF-8 403 trước sửa | FAIL, `MalformedInputException` khi client giải mã bytes thực từ Tomcat | Login STAFF thật, `/auth/me` 200, admin dashboard 403. Log `pr7-db-followup-403-red.log`. |
| UTF-8 sau sửa | PASS cả STAFF → admin và CUSTOMER → Home notifications. Status403, codeFORBIDDEN và detail tiếng Việt khớp | Tomcat HTTP + MySQL `_test`, bearer app phát, Flyway tắt riêng cho test API. Log `pr7-db-followup-403-green.log`. |
| Full Backend | **186 PASS, 0 failures/errors/skipped**, Maven verify BUILD SUCCESS | Lệnh `MAVEN_ARGS=-o powershell -NoProfile -ExecutionPolicy Bypass -File .\test-be.ps1`. Log `pr7-db-followup-full.log`. |
| Startup/Flyway safety | **8 PASS**, nằm trong 186. Chặn schema không history, history rỗng, thiếu view/cột, lineage lạ và checksum lệch. Nhận history Auth-local đúng và pending V002 hợp lệ | Flyway bật, schema MySQL được tạo riêng cho từng test. Kiểm tra history giữ nguyên ở các ca bị chặn. |
| Native baseline rehearsal | PASS, dùng marker002 riêng trong `_test`, validate đạt và hai lượt migrate đều0 | Native Flyway bật. Giữ DDL và toàn bộ dữ liệu giả của24 đối tượng nghiệp vụ. Đây là rehearsal trên fixture lịch sử. Startup policy vẫn từ chối marker chưa được duyệt. |
| Backup/restore | PASS, so sánh SHA-256 của SHOW CREATE và rows theo từng bảng/view trước/sau baseline, sau đó dump trong bộ nhớ và restore vào `_test` riêng | Có dữ liệu đại diện users, OTP, session_clients khách vãng lai, phiên bàn, order batch/line, bill/bill_items/payment, feedback và news. History marker cũng được giữ qua restore. DDL fixture thiếu News audience. |
| Smoke app trên bản restore | HTTP200 cả8 API đọc qua bearer thật ở nơi yêu cầu quyền | Home account/feed/highlights, customer menu categories, Sales tables/services, Bill1 và Income theo ngày. Flyway tắt riêng cho probe HTTP vì marker vẫn chưa được duyệt cho app. |
| UTC | PASS, notification tại03:05Z trả đúng03:05Z trong JVM `Asia/Ho_Chi_Minh` | MySQL sessionUTC, kiểm giá trị JSON qua HTTP thật. |
| Guest và News | PASS SQL cho nullable customer_user_id trong session_clients và feedback, row news được bảo toàn | Chưa kiểm HTTP guest feedback hoặc News audience. Nhánh hiện có chưa chứa DDL audience được mô tả. |
| Recovery | PASS collection39 requests/57 assertions với HTTP thật và SMTP loopback. Bốn fault injection riêng đều FAIL đúng kỳ vọng | Forgot, OTP sai, verify, reset, login mới, Home, từ chối OTP/grant đã dùng và token/password cũ. Log `pr7-db-followup-newman.log`. Flyway tắt riêng cho runner API. |
| Cooldown và resend đồng thời HTTP | PASS, đang cooldown429. Sau đưa cooldown của đúng fixture về quá khứ, hai request trả200 +429, sends=2. Verify mã mới200, dùng lại400 | Probe HTTP bổ sung trong bộ nhớ, SQL thật và SMTP capture. Quota bucket của fixture được reset chỉ trước probe bổ sung vì collection trước đã dùng hết5 initial requests. |
| Governance | PASS registry17/routes16/inherited169, Skills9 và Git Flow8, whitespace và liên kết local | Log `pr7-db-followup-governance.log`. |
| Frontend và CI revision mới | Chưa chạy | Frontend source/config/dependencies không thay đổi. Hai commit hiện lưu local để owner kiểm tra. |

Log, probe, manifest và dữ liệu tổng hợp đều nằm ngoài Git trong `C:\Users\khatr\review-guides\evidence\`. Probe đầu bổ sung vào Newman bị quota từ chối sau khi collection dùng hết5 request, được giữ tại `pr7-db-followup-newman-quota-setup-fail.log`. Lượt sửa setup đã chạy đầy đủ và đạt. Không đổi giới hạn ứng dụng. Những dữ liệu thật, URL riêng, password, OTP và bearer không được đưa vào báo cáo Git. Test schemas do probe sở hữu được dọn trong `finally`.

## Những việc cần Database cung cấp để hoàn tất

1. Xác nhận môi trường tích hợp được chọn, định danh instance/schema qua kênh riêng, và quyết định có nâng cấp legacy13 bảng hay không.
2. File DDL-only đã đối chiếu đủ20 bảng/4 view, bao gồm đúng type/nullable/default/collation/index/FK/CHECK, định nghĩa4 view và phần News audience. Gắn Git ref/commit, checksum và người phê duyệt.
3. Migration location, marker baseline version và danh sách version/script/checksum đã duyệt cho đường provision mới và đường nhận quản lý DB hiện hữu. Version1 trong bản thử Database và version002 trong rehearsal này đều chưa được cấp cho runtime mới.
4. Danh sách instance đã áp dụng Auth V001/V002 hoặc các version legacy, chủ sở hữu worker OTP và mapping dữ liệu cần giữ khi chuyển môi trường. Tám checksum legacy đã có manifest nguồn trong [proposal trước](../database/proposals/2026-11-13-dt2-2/legacy-lineage-provenance.json).

Nhận đủ nguồn trên mới có thể triển khai đường nhận BASELINE/SQL được công nhận, migration provision20 bảng/4 view, kiểm tra DDL chuẩn trước baseline và regression migration tiếp theo. Phiên bản hiện tại vẫn giới hạn history Auth-local theo hai script đã biết. Bản sửa này chưa hoàn tất mục tiêu startup trên đích sau baseline và chưa đủ điều kiện nghiệm thu database integration.

Lần fetch cuối phát hiện develop đã tiến từ `5cc4132` tới `576d6ad` qua PR #10 QR. Commit đó chưa bổ sung DDL/migration nền. Lượt186 tests ở trên dùng nền `5cc4132` hiện có trên nhánh Auth. Đồng bộ QR và kiểm chứng revision kết hợp còn cần thực hiện trước vòng nghiệm thu tích hợp cuối. Hai bản sửa follow-up đang lưu local, remote PR vẫn tại `0076bb2` ở thời điểm bàn giao.

# Các quyết định cần chốt lại

## DT2-2 — phản hồi Database về baseline, 13/11/2026

Phản hồi mới báo cáo một DB local20 bảng/4 view, News audience và chưa có history. Workspace đang kết nối môi trường13 bảng có history riêng. Cần xác nhận môi trường đích, quyết định xử lý legacy, DDL-only được duyệt cùng Git ref/checksum, migration location và marker/version/script/checksum. Cần bổ sung inventory các instance đã dùng Auth V001/V002 và legacy V001–V010. Chi tiết nguồn, quy trình đề xuất và kết quả186 Backend tests cùng rehearsal được ghi ở [bàn giao follow-up](../handover/dt2-2-flyway-baseline-followup.md). Runtime chấp nhận baseline và provision đầy đủ còn **BLOCKED** theo các đầu vào đó.

## DT2-2 database reconciliation — owner inputs required, 13/11/2026

Owner confirmed no approved full target DDL/ref or identified DBA is currently available. The 11/11 ERD remains the designated model; neither the current 13-table database nor historical `schema.sql` is an approved deployment target. Independent code/preflight/test work is completed as recorded in the [handoff](../handover/dt2-2-database-integration.md). No shared mutation or new business policy is authorized.

| Required artifact / decision | Concrete proposal and impact | Approval still needed |
| --- | --- | --- |
| Named database owner/DBA and authoritative target DDL | Version the complete 20-table DDL plus approved view contracts, all PK/FK/index/check/default/nullability/collation/time attributes and grant/execution policy. This lets every module test the same schema. | Who can approve model-to-DDL differences, schedule writes and authorize restore? Provide immutable ref and recorded approval. |
| Missing Home dependencies | Review the five-table [candidate](../database/proposals/2026-11-13-dt2-2/home-dependencies.sql): media_assets, staff_profiles, session_clients, order_batches, order_items. It restores account/feed SQL on a test copy without fabricated staff backfill; it does not reconcile the whole app. | Staff/media/order owners must approve fields, URL width, required profile fields, salary/gender checks, deletion rules, signedness and existing-user handling. Do not execute it as approved DDL. |
| Remaining model / legacy conversion | Supply menu_categories, payments, feedback, feedback_images, news_posts, news_images and approved changes to existing Menu/Sales/session/bill/service tables and views. Separate expansion from data conversion and later contraction. | Define mappings of legacy order/payment/history/state data, reconciliation queries and retention. No destructive conversion or guessed defaults. |
| One global Flyway lineage/location/version manifest | Preserve original V001–V008 bytes/history; their source checksums now match. Review already-existing V009/V010 and active module migrations, then reserve a global next version. Retire conflicting Auth-local V001/V002 from the agreed production chain only through a reviewed transition. | Approve source manifest, runtime locations, version owner and provisioning/upgrade procedure. No repair, replacement checksum, automatic baseline or second production history table. |
| OTP temporal/equality/integrity contract | UTC sessions preserve microseconds in both temporal types, but TIMESTAMP shifts under non-UTC sessions and rejects 2040. Current Unicode equality and missing checks differ from the fixture. | Approve canonical types, collation and constraints after auditing existing data. Confirm range/conversion/index effects; passing current SQL tests is not policy approval. |
| Legacy queued delivery ownership | Preserve delivery_attempts, next_delivery_at, otp_ciphertext, last_smtp_status. Located legacy source uses the queue; deployment status remains NOT VERIFIED. | Identify/stop or coordinate old senders before enabling current synchronous delivery on shared data. Approve state conversion; no duplicate worker or field deletion. |
| Rollout / restore and authenticated acceptance | Rehearse approved migrations on isolated MySQL with synthetic sentinels, strict validation and repeat-run no-op; verify Auth/OTP/RBAC/Home and all affected modules. MySQL DDL has no transactional rollback. | Approve maintenance window, writer freeze, secure backup/restore owner, grant separation and post-rollout checks. Retain expanded tables on app rollback unless DBA explicitly authorizes removal. |

These are **NEEDS OWNER DECISION / DEPLOYMENT BLOCKED**. The five-table proposal and successful rehearsal are ready for review, not authority to migrate `smart_cafe`. Existing rate-limit/retention/logout/signing-key proposals below remain unchanged.

## DT2-2 security hardening — owner clarification 12/11/2026

Source: direct owner reply in the PR #7 hardening task. Owner has not configured/verified Gateway/WAF/proxy limits and explicitly chose **NEEDS OWNER DECISION** for the proposals below. No new limiter, lockout, schema or cleanup job is authorized by that reply. Keep existing forgot-password per-email rate limit/cooldown and CAS unchanged.

| Proposal | Benefit / impact | Limits / decision needed |
| --- | --- | --- |
| Login: 5 failed attempts per identifier in 15 minutes, combined with IP admission limits at trusted gateway/proxy | Bounds repeated password checking; use normalized opaque identifier keys and uniform failure semantics for existing/unknown accounts; no permanent account lock or new user column | Owner must approve exact scope, recovery/reset-on-success and IP threshold. A local-memory implementation is per-instance, resets on restart and needs bounded key retention; it is not distributed production protection. Account-only denial can be abused against a victim. Decide shared gateway state/instance model before claiming production protection. |
| Forgot/dummy: retain current per-email controls; add per-IP and global quota at the gateway | Prevents bypass by rotating random addresses, protects storage and SMTP budget | Approve thresholds, trusted proxy chain and client-address rules; never trust arbitrary X-Forwarded-For. No gateway/WAF/deployment evidence currently verified. No application limiter change in this task. |
| OTP retention: retain expired/used rows for 24 hours for diagnosis, then bounded batches | Limits data accumulation while preserving a short audit window | **Proposal only**, no shared deletion/job executed. DB owner must approve legal/audit retention, batch cadence/size and query plan. Preserve active/unexpired rows, registration ownership and reset grants until their maximum validity/grace has elapsed; configured grant TTL must be considered, not assumed below 24h. |
| OTP limits cleanup | Reclaims expired identifier buckets | Only after hourly window/cooldown and approved audit retention have elapsed; deleting active buckets can reopen rate limits. Validate index support on approved DDL (current shared/fixture indexes differ); no unapproved index/migration. |
| Logout server revocation | Would stop reuse of a copied bearer after logout | Current implementation is stateless client cleanup plus acknowledgement; bearer remains valid until expiry or DB status/auth_version invalidates it. If per-token/per-device revocation is required, approve contract/storage first. Do not silently bump auth_version for all sessions or add a blacklist. |
| Production signing key: fail startup if AUTH_TOKEN_SECRET is absent or shorter than 32 characters; provision at least 32 cryptographically random bytes through the deployment secret store | Prevents accidental ephemeral signing keys and cross-instance/restart token rejection. Dev/test may retain explicit ephemeral behavior | **NEEDS OWNER DECISION** on deployment policy, shared key distribution and rotation/invalidation procedure across Auth/Header/Menu consumers. Existing short-key rejection is retained; missing-key prod fail-fast is a proposal, not implemented. Minimum length alone does not prove entropy. |

Concrete starting points for owner review, **not approved limits**: gateway login admission 20 requests/minute/IP with burst10, plus the proposed 5 failed/identifier/15min; forgot/dummy 5 initial requests/15min/IP plus a deployment-wide 100 initial requests/minute ceiling. Tune against expected shared-NAT traffic and SMTP provider quotas before enabling; identifier-only denial can target a victim, IP-only denial can affect a whole cafe. Retention proposal: hourly batches of at most500 eligible rows, bounded by both 24h after expiry/use and the latest possible grant/registration expiry; approve query/index support and dry-run selection first. All values are proposals only; no limiter, schema or cleanup job was added.

Deployment remains **BLOCKED** until the DB owner/leader reconciles current DDL and migration lineage; see the dated database-contract audit. No full-suite result replaces deployment acceptance.

Các mục nội bộ lịch sử bên dưới cần đối chiếu với Q&A hiện hành trước khi áp dụng. Phần DT2-7 đã được đối chiếu online ngày 11/11/2026 ở cuối tài liệu.

Các quyết định liên quan DT2-6 được đối chiếu với [QA nhóm cung cấp](https://docs.google.com/spreadsheets/d/1m2vnTOdGWivwqokRiJYOpWmY2u3G_o-U/edit?gid=1741591823#gid=1741591823). Xem [bàn giao DT2-6](../handover/dt2-06-sales-income.md) để phân biệt phần đã trả lời và phần tích hợp còn thiếu.

| ID | Câu hỏi | Trạng thái |
| --- | --- | --- |
| QA-01 | Phạm vi, người dùng, nguồn SRS/project plan và tiêu chí bàn giao của vòng mới? | Chờ nhóm xác nhận |
| QA-02 | Ai kích hoạt/đóng phiên bàn, nhiều thiết bị tham gia thế nào? | QA No. 4: chỉ quản lý mở/reset. Thời điểm mở, nhiều thiết bị và reset chưa thu tiền còn cần chốt. |
| QA-03 | Chỉnh/hủy món có thời hạn nào và tính từ mốc nào? | Chưa chốt |
| QA-04 | Phân biệt yêu cầu thanh toán, lập bill, nhận tiền, ghi doanh thu và reset? | QA No. 3/4/17: yêu cầu tính tiền riêng; quản lý xác nhận tiền trước khi in. Doanh thu đọc bill PAID theo view DB đã thống nhất; còn cần producer/giao kèo socket, chính sách void/hoàn tiền và xử lý món đến sau khi lập bill. |
| QA-05 | Hành vi khi mất kết nối và cơ chế phục hồi? | QA No. 12: báo lỗi mất kết nối. Chưa chốt chế độ bán offline; UI hiện tại có thử lại. |
| QA-06 | DT2-1: top gọi nhiều tính quantity hay số lần, trong khoảng thời gian nào; available=false có ẩn không? | Đang dùng view quantity loại CANCELLED toàn thời gian; chờ PO |
| QA-07 | DT2-1: route menu/footer và provider session production? | Header đã đọc profile/feed từ DB và kiểm chứng provider local; chờ nhập auth/route của DT2-2, DT2-3, DT2-4, DT2-9 để smoke production integration |

Mỗi câu trả lời cần người chốt, ngày, nguồn bằng chứng và task bị ảnh hưởng. Đề xuất kỹ thuật chưa được duyệt vẫn là đề xuất.

## DT2-7 — đối chiếu Q&A thật ngày 11/11/2026

Nguồn online: [Q&A dự án](https://docs.google.com/spreadsheets/d/1m2vnTOdGWivwqokRiJYOpWmY2u3G_o-U/edit), No.4/5/10; [Project Plan](https://docs.google.com/spreadsheets/d/1nT1hxrsgVdBxTbORlTwohwzmbvujldYR/edit), Tasks_Sprint_1!B9/E9. Các số No. này thuộc file Q&A khách hàng, không đồng nhất với mã QA-01…QA-07 nội bộ phía trên.

- **Đã trả lời, No.4:** chỉ quyền quản lý làm các tác vụ được hỏi, bao gồm đăng/sửa/xóa tin tức. ADMIN quản lý là cách mapping hiện tại; không hỏi lại STAFF có được thao tác hay không.
- **Đã trả lời, No.5:** tất cả trường hợp xóa dùng xóa mềm/logic. Không hỏi lại xóa cứng hay mềm. Dùng ARCHIVED sẵn có là lựa chọn hiện thực; không tự suy ra toàn bộ chính sách khôi phục/retention từ đáp án này.
- **Đã trả lời, No.10:** nhóm tự suy nghĩ thiết kế tin tức, chốt ở phiên prototype. Không xin bổ sung lại toàn bộ layout/trường dữ liệu; đưa prototype cụ thể để xác nhận.

Danh sách QA-08–QA-12 từng soạn local được thay bằng đối chiếu này vì có câu hỏi trùng đáp án. Giới hạn ảnh, kiểu editor và trạng thái cụ thể là đề xuất prototype, không phải điều khoản đã được nguồn yêu cầu xác nhận. Bucket/ADC là dependency cấu hình kỹ thuật riêng, không hỏi secret trong Q&A.

## DT2-7 — Q&A 19–20 đã được trả lời

[Ảnh phản hồi và cách triển khai](../handover/dt2-7-qa-replies-2026-11-11.md):

| No. Q&A | Trạng thái | Trả lời | Kết quả triển khai BE |
| --- | --- | --- | --- |
| 19 | Answered | Thêm lựa chọn gửi tin đến Nhân viên, Khách hàng | audiences STAFF/CUSTOMER, lưu hai cột theo ADR-005 được chủ repo duyệt; người đọc chỉ thấy PUBLISHED đúng nhóm. |
| 20 | Answered | Chỉ thông báo nhân viên | Socket chỉ STAFF; generic NEWS_CHANGED sau commit khi danh sách STAFF bị ảnh hưởng; CUSTOMER/ADMIN không nhận. |

Không hỏi lại hai câu đã trả lời. Hai checkbox độc lập, mặc định bài cũ 0/0 và API bắt buộc chọn ít nhất một nhóm khi tạo là cách triển khai đã trình chủ repo trong phương án schema. Chủ repo đã duyệt “vậy duyệt” sau khi nghe giải thích. Theo chỉ thị riêng tiếp theo tại ADR-006, đã áp dụng schema vào smart_cafe local; không seed hoặc ghi dữ liệu thử, chưa commit/push.

Phần chưa được phản hồi chốt: “khách hàng” có gồm người chưa đăng nhập/phiên QR không, chi tiết UI toast hay tự reload. Tiếp tục giới hạn đọc ở tài khoản CUSTOMER thật và để FE thiết kế prototype theo No.10; không tự giả auth hoặc mở public mọi tin. Không chặn việc triển khai hai câu đã được duyệt vì các chi tiết UI chưa thuộc BE.

Hợp đồng: [DT2-7 backend](../handover/dt2-7-backend-contract.md). Firebase cloud không sử dụng do ràng buộc miễn phí; local dùng Storage Emulator, không đổi trạng thái Q&A 19–20 về chưa trả lời.

### QA-02 — bổ sung yêu cầu DT2-4 ngày 11/11/2026

Người dùng yêu cầu khách quét QR tại bàn, vào thẳng Menu không đăng nhập và hệ thống tự mở phiên. Jira DT2-33 vẫn ghi QA-02 chưa chốt; QA nhóm trước đó ghi chỉ quản lý mở/reset. Cần PO/chủ DB xác nhận thay đổi trong phạm vi thẩm quyền và cách lưu người mở khi `table_sessions.opened_by_user_id` hiện NOT NULL FK tới `users`.

Các phương án cần quyết định: cho phép NULL để thể hiện khách vãng lai (cần duyệt schema), hoặc giữ schema và để người dùng nội bộ có thật mở phiên (không đáp ứng trải nghiệm tự mở hiện được yêu cầu). Không tạo tài khoản giả, gán ADMIN thay khách hoặc tắt FK. Thời hạn token, nhiều thiết bị và quét lại ở CHECKOUT chưa chốt. Xem [nguồn, phạm vi và blocker chức năng 2](../handover/dt2-4-table-session.md).


### DT2-9: DDL triển khai theo sơ đồ mới

Model đã theo sơ đồ mới do chủ repo cung cấp lại ngày 11/11. DB local legacy vẫn là bản cũ; cần chủ DB cung cấp/cập nhật DDL đúng mô hình trước smoke hoặc triển khai thực. PR không tự thay DB đó. Xem [đối chiếu](../architecture/database-contract.md#dt2-9--đối-chiếu-ngày-11112026-cho-pr-8).

### DT2-2 OTP retry — DDL triển khai cần chủ DB xác nhận (12/11/2026)

`smart_cafe` đang có kiểu timestamp/collation, cột delivery và constraint khác sơ đồ 11/11 và schema `_test`. Chủ repo/người phụ trách DB cần xác nhận DDL đích và chính sách cho các cột delivery bổ sung trước reconciliation/provisioning/deployment; chưa tự chọn nguồn để migrate. Sửa retry dùng các cột chung không cần thay schema. Nguồn và tác động: [database contract](../architecture/database-contract.md#dt2-2--đối-chiếu-read-only-cho-otp-retry-ngày-12112026).

### DT2-9: form Figma có Mô tả / WebP ngoài contract hiện tại

Frame Figma 8:319 có Mô tả và nhãn WebP, nhưng API/ERD menu chưa có description và backend chỉ nhận PNG/JPEG. Reviewer nêu P3 cần PO/UX/BE chốt. **Chủ repo chọn ngày 13/11: giữ contract hiện tại, ghi rõ chênh lệch và chờ PO chốt Figma.** Không tự thêm schema/field/format; phần cập nhật thiết kế vẫn chờ PO. Xem [đối chiếu và quyết định](../handover/dt2-9-figma-contract-alignment.md).


### DT2-2 — received DDL handoff, remaining owner/DBA decisions (2026-11-13)

The owner supplied `SmartCafe-DDL-handoff-20261113.zip`; verified SQL now supports isolated provision/baseline and opt-in `verified-db`. The earlier “no full DDL artifact” observation is historical. See [current integration and authority](../handover/dt2-2-verified-database-integration.md).

| Decision | Concrete remaining action / impact | Status / responsible owner |
| --- | --- | --- |
| Canonical environment and DDL approval | Identify target instance privately; accept all twenty tables, four DEFINER views and the News audience extension. Do not treat legacy 13-table DB as this target. | NEEDS OWNER DECISION — repository owner + designated DBA |
| Global migration allocation | Record `20261113.1`, source SHA and one canonical `classpath:db/smart-cafe` artifact/ref. If a separate Database PR owns it, merge that artifact first and synchronize Auth; avoid duplicate migrations. | NEEDS OWNER DECISION — DB/version owner |
| Existing environment inventory | List instances using Auth V001/V002, legacy V001–V010 or untracked full DDL. Preserve each chain; design conversion only for an identified source. | BLOCKED pending DBA/environment owner |
| Definers and rollout | Approve migration/application grants, definer account lifetime, secure backup/restore rehearsal, writer freeze and explicit baseline/deploy command/window. Local privileged MySQL success does not approve production grants. | DEPLOYMENT BLOCKED — DBA + release owner |

No new limiter/retention/lockout/job, shared schema write, or business policy is approved here. QA-02 auto guest-session ownership and previous security proposals remain open.

# Các quyết định cần chốt lại

## DT2-11 — ID, State/On và xóa bàn, 17/11/2026

Nguồn: [DT2-11](https://duymanhdnvn20092005.atlassian.net/browse/DT2-11), các task con DT2-73/74/75 và trao đổi trực tiếp ngày 17/11/2026. Người dùng chọn **làm giao diện độc lập và lưu lại câu hỏi**, chưa chốt các phương án nghiệp vụ dưới đây. PO/mentor xác nhận nghiệp vụ; chủ repo/người phụ trách DB xác nhận ánh xạ và DDL.

| Câu hỏi | Phương án cần xác nhận | Phần bị ảnh hưởng |
| --- | --- | --- |
| ID trên giao diện là gì? | `cafe_tables.id`, `code` hay `table_number`; định dạng, tính duy nhất, trường bắt buộc và quyền sửa? | Add, validation, tìm ID và API payload |
| State và On có nghĩa gì? | Trạng thái hoạt động của bàn, trạng thái có khách hay trạng thái phiên; tập giá trị hợp lệ; On chỉ hiển thị hay có quyền mở/reset phiên? | Edit, tìm State, hiển thị On, phối hợp DT2-4/6 và QA-02/08 |
| Remove xử lý thế nào? | Xóa vật lý bàn chưa có lịch sử, lưu trữ/ngừng sử dụng, hay từ chối; bàn có phiên đang mở/đang thanh toán/hóa đơn xử lý ra sao? | Delete, khóa giao dịch, lịch sử bill DT2-8 và hành vi QR cũ |
| Ai được xem/thêm/sửa/xóa? | Ma trận quyền từng thao tác và các trường cho phép sửa; cách đối sánh ID/State chính xác hay một phần? | API authorization, UI và tìm kiếm |

DDL-only legacy tại `backend/src/test/resources/db/current-shared-2026-11-13.sql` khác sơ đồ chuẩn: `cafe_tables` thiếu `code`, `qr_public_id`, `updated_at`, `version`; `table_number` NOT NULL và trạng thái `EMPTY/ACTIVE`. Repo cũng đã có DDL bàn giao verified tại `backend/src/main/resources/db/smart-cafe/V20261113_01__smart_cafe_schema.sql` với các trường theo sơ đồ và CHECK `ACTIVE/OUT_OF_SERVICE/ARCHIVED`, thuộc môi trường/lineage khác. Không lấy snapshot legacy để kết luận toàn dự án thiếu DDL. Chưa kiểm lại DB ngày 17/11 hoặc chọn môi trường tích hợp DT2-11; giữ persistence ngoài phạm vi UI theo lựa chọn người dùng. Xem [database contract](../architecture/database-contract.md).

Bản thử tại `/preview/tables` chỉ có trong Vite development, không gọi API, không lưu browser storage, không tạo người dùng/phiên. ID/State là chuỗi nhập thử, On ghi “Chưa chốt”. Tìm một phần không phân biệt hoa/thường, kiểm ID trùng và Remove khỏi bộ nhớ chỉ phục vụ review tương tác; chúng **không phải quyết định nghiệp vụ đã được duyệt**. Xem [bàn giao bản thử](../handover/dt2-11-table-ui-preview.md).
## DT2-3 / DT2-27 — trạng thái 15/11/2026

QA #8 và biên hết hạn/password reuse đã được owner chốt trong chat, ghi tại [ADR-013](../architecture/decisions.md#adr-013--hồ-sơ-cá-nhân-và-vòng-đời-mật-khẩu-dt2-3-15112026); không còn blocker quyền STAFF sửa ba trường. Các phần còn lại:

- DT2-10 owner: PR #11 cập nhật users/staff_profiles chưa kiểm/tăng users.version, cần thống nhất expected version và khóa users trước đọc/ghi nhân sự; không thể tuyên bố Profile đã ngăn stale write của mọi module. Chưa nhập nhánh Staff để né dependency.
- Auth/ops owner: current-password/login chưa có quota được phê duyệt để tái sử dụng. Quota/gateway chưa được kiểm chứng; giữ quyết định không tự thêm limiter ở hardening 12/11. Rủi ro thử mật khẩu nhiều lần còn tồn tại trước production.
- DBA: instance workspace smart_cafe vẫn legacy 13 bảng, thiếu staff_profiles/media_assets; không đủ để rollout Profile. Không tự migrate; tiếp tục native rehearsal/test trên DDL verified riêng.
- Leader: chưa xác minh được CF cho DT2-3. Owner yêu cầu giữ DT2-3/DT2-27, chưa commit cuối/push/PR và không thay validator PR title. Đây là blocker xuất bản, không chặn code/test.

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

Các quyết định liên quan DT2-6 được đối chiếu với [QA nhóm cung cấp](https://docs.google.com/spreadsheets/d/1m2vnTOdGWivwqokRiJYOpWmY2u3G_o-U/edit?gid=1741591823#gid=1741591823). Xem [bàn giao DT2-6](../handover/dt2-06-sales-income.md) để phân biệt phần đã trả lời và phần tích hợp còn thiếu.

| ID | Câu hỏi | Trạng thái |
| --- | --- | --- |
| QA-01 | Phạm vi, người dùng, nguồn SRS/project plan và tiêu chí bàn giao của vòng mới? | Chờ nhóm xác nhận |
| QA-02 | Ai kích hoạt/đóng phiên bàn, nhiều thiết bị tham gia thế nào? | QA No. 4: chỉ quản lý mở/reset. Thời điểm mở, nhiều thiết bị và reset chưa thu tiền còn cần chốt. |
| QA-03 | Chỉnh/hủy món có thời hạn nào và tính từ mốc nào? | Chủ yêu cầu xác nhận trong chat 13/11/2026: 3 phút từ lúc server nhận Order, chỉ khi món chưa chuẩn bị (SUBMITTED); hết hạn hoặc đã chuẩn bị thì từ chối. Lưu BEFORE_PREPARATION, dùng Clock server; xem bàn giao DT2-4 còn lại. |
| QA-04 | Phân biệt yêu cầu thanh toán, lập bill, nhận tiền, ghi doanh thu và reset? | QA No. 3/4/17: yêu cầu tính tiền riêng; quản lý xác nhận tiền trước khi in. Doanh thu đọc bill PAID theo view DB đã thống nhất; còn cần producer/giao kèo socket, chính sách void/hoàn tiền và xử lý món đến sau khi lập bill. |
| QA-05 | Hành vi khi mất kết nối và cơ chế phục hồi? | QA No. 12: báo lỗi mất kết nối. Chưa chốt chế độ bán offline; UI hiện tại có thử lại. |
| QA-06 | DT2-1: top gọi nhiều tính quantity hay số lần, trong khoảng thời gian nào; available=false có ẩn không? | Đang dùng view quantity loại CANCELLED toàn thời gian; chờ PO |
| QA-07 | DT2-1: route menu/footer và provider session production? | Header đã đọc profile/feed từ DB và kiểm chứng provider local; chờ nhập auth/route của DT2-2, DT2-3, DT2-4, DT2-9 để smoke production integration |

Mỗi câu trả lời cần người chốt, ngày, nguồn bằng chứng và task bị ảnh hưởng. Đề xuất kỹ thuật chưa được duyệt vẫn là đề xuất.

### DT2-4 — quyết định trong chat ngày 13/11/2026

Chủ yêu cầu giữ QR tạm dừng, cho xây dựng nghiệp vụ dùng session_clients hiện có để kiểm tra token khách; không làm lại bảng hoặc tự mở phiên. TTL/issuer của luồng QR vẫn chưa chốt; API chỉ dùng expires_at/revoked_at đã lưu. Chấp thuận quy tắc QA-03 nêu ở bảng trên. Firebase project/bucket/credential thuộc thành viên khác: chỉ chuẩn bị env/adapter, chưa chạy cloud thật. DT2-5 đã được yêu cầu tạm dừng, dù đã ghi nhận dự kiến quyền ADMIN và ngày tìm kiếm theo Asia/Ho_Chi_Minh để dùng khi được cho tiếp tục. Xem [bàn giao phần còn lại](../handover/dt2-4-remaining-backend.md).

Khách cũng đã yêu cầu QR tự mở phiên và vào Menu. QA-02, cách lưu người mở khi `opened_by_user_id` là NOT NULL, thời hạn token và nhiều thiết bị chưa được chốt với PO/chủ DB. PR #10 chỉ tra QR để xác định bàn, không mở phiên hoặc cấp token. Xem [bàn giao QR và phiên](../handover/dt2-4-table-session.md).


### DT2-9: DDL triển khai theo sơ đồ mới

Model đã theo sơ đồ mới do chủ repo cung cấp lại ngày 11/11. DB local legacy vẫn là bản cũ; cần chủ DB cung cấp/cập nhật DDL đúng mô hình trước smoke hoặc triển khai thực. PR không tự thay DB đó. Xem [đối chiếu](../architecture/database-contract.md#dt2-9--đối-chiếu-ngày-11112026-cho-pr-8).

### DT2-2 OTP retry — DDL triển khai cần chủ DB xác nhận (12/11/2026)

`smart_cafe` đang có kiểu timestamp/collation, cột delivery và constraint khác sơ đồ 11/11 và schema `_test`. Chủ repo/người phụ trách DB cần xác nhận DDL đích và chính sách cho các cột delivery bổ sung trước reconciliation/provisioning/deployment; chưa tự chọn nguồn để migrate. Sửa retry dùng các cột chung không cần thay schema. Nguồn và tác động: [database contract](../architecture/database-contract.md#dt2-2--đối-chiếu-read-only-cho-otp-retry-ngày-12112026).

### DT2-9: form Figma có Mô tả / WebP ngoài contract hiện tại

Frame Figma 8:319 có Mô tả và nhãn WebP, nhưng API/ERD menu chưa có description và backend chỉ nhận PNG/JPEG. Reviewer nêu P3 cần PO/UX/BE chốt. **Chủ repo chọn ngày 13/11: giữ contract hiện tại, ghi rõ chênh lệch và chờ PO chốt Figma.** Không tự thêm schema/field/format; phần cập nhật thiết kế vẫn chờ PO. Xem [đối chiếu và quyết định](../handover/dt2-9-figma-contract-alignment.md).

### QA-08 — DT2-10: Onboarding nhân viên và luồng kích hoạt tài khoản (Cập nhật 18/11/2026)

- **Mã issue / Dependency:** `DT2-2` (Module Authentication & Account Onboarding / Email Service).
- **Owner:** Nhóm phụ trách module Auth (`DT2-2`) và Staff (`DT2-10`).
- **Quyết định nghiệm thu backend-only (Phương án B):** Nhóm và PO đã thống nhất chốt **Phương án (B)** cho PR #11: nghiệm thu quy trình onboarding thủ công qua tính năng Quên mật khẩu đã merge của DT2-2 (`POST /api/v1/staff` -> Admin thông báo -> Nhân viên tự xin OTP qua `/api/v1/auth/forgot-password` -> Đặt mật khẩu mới qua `/api/v1/auth/reset-password`). Không tự claim tính năng auto email invitation.
- **Tiêu chí Release / Integration Gate:** Luồng tự động phát hành email lời mời kích hoạt (Option A) cùng token dùng 1 lần (TTL, challenge an toàn) được chuyển thành tiêu chí kiểm thử tích hợp E2E của giai đoạn release/tích hợp tiếp theo; không làm blocker nghiệm thu riêng của PR #11 backend-only.
- **Bảo toàn phân quyền SecurityConfig:** Đã bảo đảm giữ nguyên quy tắc phân quyền `/api/v1/staff/**` chỉ cho phép `ROLE_ADMIN` (như cấu hình trong PR #11); GET `/api/v1/staff/dashboard` cho phép `ROLE_ADMIN` và `ROLE_STAFF`.

### DT2-2 — received DDL handoff, remaining owner/DBA decisions (2026-11-13)

The owner supplied `SmartCafe-DDL-handoff-20261113.zip`; verified SQL now supports isolated provision/baseline and opt-in `verified-db`. The earlier “no full DDL artifact” observation is historical. See [current integration and authority](../handover/dt2-2-verified-database-integration.md).

| Decision | Concrete remaining action / impact | Status / responsible owner |
| --- | --- | --- |
| Canonical environment and DDL approval | Identify target instance privately; accept all twenty tables, four DEFINER views and the News audience extension. Do not treat legacy 13-table DB as this target. | NEEDS OWNER DECISION — repository owner + designated DBA |
| Global migration allocation | Record `20261113.1`, source SHA and one canonical `classpath:db/smart-cafe` artifact/ref. If a separate Database PR owns it, merge that artifact first and synchronize Auth; avoid duplicate migrations. | NEEDS OWNER DECISION — DB/version owner |
| Existing environment inventory | List instances using Auth V001/V002, legacy V001–V010 or untracked full DDL. Preserve each chain; design conversion only for an identified source. | BLOCKED pending DBA/environment owner |
| Definers and rollout | Approve migration/application grants, definer account lifetime, secure backup/restore rehearsal, writer freeze and explicit baseline/deploy command/window. Local privileged MySQL success does not approve production grants. | DEPLOYMENT BLOCKED — DBA + release owner |

No new limiter/retention/lockout/job, shared schema write, or business policy is approved here. QA-02 auto guest-session ownership and previous security proposals remain open.

### DT2-3 — integration/security findings cần owner xử lý (15/11/2026)

QA #8 STAFF self-edit và hợp đồng30×24h/replacement-token đã được owner chốt trong chat, được ghi tại [ADR-013](../architecture/decisions.md#adr-013--hồ-sơ-cá-nhân-và-vòng-đời-mật-khẩu-dt2-3-15112026); không hỏi lại hai quyết định này. Mã CF chưa có chỉ giữ commit/title/publication, không chặn implementation/test và không tự gán mã khác.

| Việc còn cần quyết định/khắc phục | Bằng chứng và trách nhiệm |
| --- | --- |
| Shared users.version/auth_version/lock order cho DT2-10 | HEAD#11 `4c7b969`:9MySQLconcurrency tests có5failure, lost update/stale acceptance thực tế. Owner Staff xem [test/patch proposal](../qa/rehearsals/dt2-3/README.md), cập nhật caller expected-version và xác nhận contract; chưa tự áp lên PR. |
| Auth/Staff/News integration ownership | Latest Staff/Customer revert Auth không compile nguyên trạng; bean collision; HeaderTokenFilter thiếu Profile, broad Staff matcher lộ personnel ở candidate. Owner tương ứng phải nhận shared consumer/matcher và hoàn thiện Staff caller version theo proposal (News fixture tại HEAD đã đúng); [full rehearsal chưaPASS](../reports/dt2-3-hardening.md). |
| Login/current-password admission và body-cap policy |20localhost requests không429, không lockout; BCrypt cost10 vẫn chạy cho password sai. Cần owner Auth/gateway duyệt quota/backoff/trustedIP/global budget/JSON cap và test multi-instance; chưa limiter mới, không permanent account lock hay sửa OTP CHECK. Các số đề xuất trong report chưa là yêu cầu đã duyệt. |
| Approved SonarQube environment | Chưa có server/token/scanner được duyệt; NOT RUN, không sửa CI/quality gate để thay thế. |
| Deployment target | Legacy smart_cafe cấu hình local vẫn13tables/0views; DBA xác định instance/lineage/grants/DEFINER/rollout/backup trước triển khai. Giữ DDL20261113.1 nguyên byte. |

Các finding là kết quả rehearsal theo revision, không phải lời nhắn/issue đã gửi cho thành viên. Local handoff/patch sẵn để owner review; chưa publish, merge hoặc Jira Done.

DT2-3 final handoff: owner đã chấp nhận Backend local, không chấp nhận full integrationPASS. Shared rate/body policy được theo dõi là **AUTH-GATEWAY-01 — NEEDS OWNER DECISION, trước release**, chưa triển khai limiter/bảng/DDL. Owner Auth/Gateway phải duyệt topology/quota/recovery trước release; bounded localhost probe không là DoS/brute-force production proof. [Owner handoffs và gate publication/merge](../handover/dt2-3-final-handoff.md) là next actions; chưa gửi comment/request reviewer.

### DT2-3 — cập nhật18/11/2026

**Historical publication snapshot at Staffb74a365 (superseded by57ea6f1):** CF đã chốt **CF-04**, user đã authorize commit/push/Draft PR; thiếu-CF không còn blocker. Staffb74a365 khôi phục Auth/verified resources, rename dashboard và ADMIN management rules; version/CAS writer vẫn còn finding9test/5failure. Newsc2343ed có root/path matching mới nhưng thiếu Profile consumer, baselineHTTP401 expected200 vẫn tái hiện; preserving matcher Staff mới chặn other-personnel403 đúng. Customer#14 đã lêna41152d và khôi phục Auth/verified resources; domain/tests trùng Git tree với ref đã chạy. Lỗi Auth-revert trước đây không còn quy cho HEAD mới, nhưng shared-chain composition vẫn phải giữ đủ authority/routes. AUTH-GATEWAY-01/DBA/Sonar và fresh independent review/CI trước merge không tự được giải quyết bởi publication. [Trạng thái mới nhất](../reports/dt2-3-publication.md).


## Current DT2-3 integration correction, 18/11/2026

Staff57ea6f1 officially fixes version/CAS/auth_version/caller migration; source defect is FIXED, combined regression must use that HEAD. Staffb74 baseline5/9FAIL and proposal9/9PASS remain historical. News consumer c2343ed and Bills fixture86b70e2 still need owner adoption of pinned minimal fixes. Literal official CandidateA is blocked by merge conflicts, not silently replaced by selective CandidateB. See [current report](../reports/dt2-3-review-follow-up.md) and [finding matrix](../handover/dt2-3-review-matrix.md). AUTH-GATEWAY-01, DBA/production/Sonar release gates remain owner decisions; no new policy, table, limiter or shared DB write.

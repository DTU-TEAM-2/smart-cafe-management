# CF-04 / DT2-28 — final verification 19/11/2026

Owner đã chấp nhận UI và cho phép commit/push/PR. Nhánh `feature/dt2-3_personal-account-frontend` tích hợp `origin/develop@e3ce5b1df9475f3bde709828ad0ccdead2fa19ef`; fetch/prune mới và merge trả `Already up to date`, không conflict, remote feature chưa tồn tại. Không nhập PR chưa merge. [Machine evidence](dt2-28-frontend-evidence.json) lưu checksum source trước commit; SHA commit và CI sau publication được ghi trên PR thực tế. [Bàn giao và toàn bộ ảnh](../handover/dt2-28-frontend.md).

| Kiểm tra mới ngày 19/11 | Kết quả thực tế |
| --- | --- |
| npm ci / lint / build | PASS; 0 lint warnings; 47 modules, JS309.79kB/gzip92.13kB; không đổi dependency/lockfile |
| Unit validation | **4/4 PASS** |
| Full Playwright, 2 workers | **176/176 PASS**, exit0, 0 skip/flaky: existing100 + lifecycle54 + product22, desktop/mobile |
| Probe session switch | FAIL trước fix: login cũ ghi đè token phiên mới; thêm snapshot guard, regression nằm trong full176 desktop/mobile |
| Real full-stack browser | **8/8 PASS**, Chromium/Vite → Spring Boot → MySQL8.0.45; **0 endpoint mock**, 0 skip/flaky |
| Backend/MySQL | **315/315 Maven verify PASS**, 0 failure/error/skip; isolated schema theo fixture CI |
| Native database lifecycle | PASS provision/validate/repeat và verified-db preflight; canonical DDL20261113.1/checksum giữ nguyên |
| Profile/Menu/QR Newman6.2.1 | **203 requests / 587 acceptance assertions PASS**, **10 SQL checks PASS**; negative control expected FAIL tách riêng |
| Auth/OTP/Home Newman | **39 requests / 57 acceptance assertions PASS**, real loopback SMTP; 4 fault controls expected FAIL tách riêng |
| Accessibility/responsive | Axe view/edit/password desktop/mobile0 violations; keyboard/focus/dialog/skip-link; 1920×1080,1440×900,1366×768,1024×768,768×1024,390×844 không overflow |
| Governance | Registry17/routes16/inherited169; 17/17 script tests PASS; local feature→develop/title validation PASS |
| npm audit | **0 vulnerabilities** trong lượt mới; audit18/11 từng báo5high là lịch sử. Không audit fix/force hoặc đổi package |
| Whitespace/secret patterns/internal links | PASS trên danh sách DT2-28; private raw logs/config không stage |
| GitHub Actions | Chạy sau publication; xác minh final HEAD trên PR, không dùng local làm CI PASS |
| SonarQube/production rollout/UAT | NOT RUN |

## Lệnh và revision

```text
cd frontend
npm ci
npm run lint
npm run build
npm run test:ui -- --workers=2
npm run test:ui -- tests/account-ui.spec.js --workers=2
backend/mvnw.cmd -B -ntp -o -Dspring.test.context.cache.maxSize=4 verify dependency:build-classpath -Dmdep.outputFile=target/dt2-3-classpath.txt
python scripts/profile-browser-live.py
python scripts/profile-postman-live.py
node scripts/auth-postman-live.cjs
node scripts/skills.mjs check <full-develop-SHA>
node --test scripts/skills.test.mjs scripts/git-flow.test.mjs
```

Backend command chạy từ thư mục backend; runner nhận TEST_DB_URL/credentials/Newman qua environment private. Final full UI log `backend/target/dt2-28-final-session-guard-playwright.log`, negative probe `dt2-28-session-switch-negative.log`, browser `dt2-28-browser/summary.json`, Maven `dt2-28-maven-summary.json`, Profile HTTP `postman/dt2-3/summary.json`, Auth HTTP `dt2-28-auth/newman.log`. Raw artifacts không trong Git. Lượt174/174 và capture rerun22/22 trước đó được giữ riêng. Self-review sau đó tái hiện một lỗi login cũ ghi đè phiên được đồng bộ qua auth event; đã capture session ở đầu login và kiểm snapshot trước commit. Toàn bộ176 UI và8 real E2E được chạy mới trên source cuối có fix và helper ảnh ổn định; source manifest62file được kiểm lại trước stage. Sau full176, git diff --check phát hiện dòng trống thừa ở EOF của profileValidation.js và report; chỉ bỏ newline thừa, giữ mọi byte nội dung. Unit4 và browser8 được kiểm lại trên byte source cuối. Backend không đổi, tái sử dụng đúng lượt315 và Newman mới cùng ngày.

JAR SHA-256: `6c8c7103ccd97923fe3d44b8c02e15b05edce41a72c7dee488b4ffdeabaa7230`. Canonical DDL `20261113.1`: `84cb9f2e99073ea73b117e828dc7f144928052a8f8345cde6f903b82280e4b5e`. Không có Backend/API/DDL/migration/workflow/validator diff. Schema và process do runner tạo được cleanup.

## Tám luồng browser thật và SQL

1. ADMIN login → avatar/Header → Profile → sửa ba trường với tiếng Việt/dấu nháy → PATCH 200 → Header đổi tên → reload → SQL users/staff_profiles khớp, version tăng đúng một.
2. STAFF thực hiện cùng luồng, cùng allowlist và kiểm SQL sau reload.
3. Cancel: count PATCH bằng 0, snapshot SQL trước/sau bằng nhau.
4. Password: POST 200, token mới khác token cũ; Profile bằng token mới 200, token cũ 401; login password mới 200, password cũ 401; SQL auth_version/version tăng một. Không dùng `page.route`.
5. Current password sai trả HTTP 400; confirm mismatch/reuse bị chặn ở UI; SQL snapshot không đổi.
6. Transaction SQL thứ hai lock/cập nhật cùng users.version sau khi browser bắt đầu edit; PATCH draft cũ trả **409 thật**, UI GET lại, không ghi đè tên/version mới; SQL trước submit/sau conflict bằng nhau.
7. Synthetic account đổi password cách đây 31 ngày: Login/Profile trả warning, reload vẫn warning; đổi password thành công xóa warning, Profile bằng token mới hoạt động và SQL flag/auth_version đúng.
8. Anonymous GET Profile 401; CUSTOMER hợp lệ GET Profile 403 và personnel UI bị chặn, không hiển thị salary/profile fields.

MySQL thực sự chạy 8.0.45; Java server dùng timezone Asia/Ho_Chi_Minh, JDBC UTC. Database source vẫn là canonical native migration; schema fixture không thay bằng H2/SQLite/mock. Snapshot helper chỉ dùng tài khoản tổng hợp và catalog do runner sở hữu. Các owned browser/Auth/Maven/Postman schemas đã được drop; server/process thuộc runner đã dừng.


## Tương thích và giới hạn hiện tại

Auth/OTP/reset, Home/Header, Menu, Sales/Billing/Statistics hiện có và Customer backend nằm trong các suite mới trên base đã nêu. Staff Management/News và Billing mới trong PR chưa merge không được nhập hoặc dùng proposal PASS làm bằng chứng chính thức. Customer FE PR20 có overlap HomeShell cần review khi tích hợp sau này.

Metadata database chung đọc lại ngày19/11: MySQL8.0.45,13BASE TABLE/0VIEW, trong ba dependency Profile chỉ có users; staff_profiles/media_assets chưa tồn tại. Không đọc row nhân sự hoặc ghi database chung. Đây là chênh lệch legacy đã có trong database contract, cần DBA rollout trước production; không thay schema để che lỗi. Acceptance dùng canonical MySQL _test riêng.

Thiết kế LECKER bổ sung đã được owner chấp nhận; không có frame Profile Figma để tuyên bố pixel-perfect. Browser từ chối storage không bảo đảm persistence qua reload; replacement trong memory được kiểm. Mất password response không chứng minh transaction chưa commit: xóa password và yêu cầu xác thực lại, không retry POST. AUTH-GATEWAY/production SMTP/storage/DBA/release gates ngoài task chưa được đóng bởi UI.

Không còn lỗi chức năng DT2-28 tái hiện trong lượt mới. Self-review không phải independent approval; hai reviewer độc lập, discussions và final HEAD CI vẫn là điều kiện merge. Không approve/resolve/merge/deploy/Jira Done.

## Lịch sử lỗi ngày18/11 (không thay thế lượt mới)

Các đoạn sau lưu diễn biến trước lượt fresh176/176 ngày19/11:


- Vòng product đầu 2/16 PASS, 14 FAIL: đã tái hiện click mở edit vô tình submit do React tái sử dụng cùng button; tách key/type và chặn default của thao tác mở edit. Nhãn `*` được tách khỏi label để exact label query/accessible name thống nhất. Có regression giữ input/focus và không PATCH khi mở edit.
- Axe phát hiện accessible name logo không bao gồm toàn bộ chữ nhìn thấy và thiếu khoảng trắng giữa hai text node. Chỉ sửa aria-label/spacing trong SiteLayout, không đổi hình dáng logo/layout; giữ kiểm tra toàn trang.
- Hai vòng live đầu 4/8 rồi 2/8 PASS: lỗi locator/test timing và stdout SQL helper dùng Windows cp1252 với tên tiếng Việt. Sửa readiness sau login, locator success phân biệt status loading và JSON ASCII escaping (giữ nguyên nội dung Unicode khi parse); không sửa Backend hoặc kỳ vọng HTTP/SQL để che lỗi.
- Một vòng full trong lúc source đang được chỉnh có 161/164 PASS và 3 failure do HMR/export/module navigation; không dùng vòng đó làm nghiệm thu. Lượt runtime cố định cuối là 172/174, với hai expected-guest-console instrumentation failures đã được xử lý qua rerun 22/22 nêu trên.
- Profile Newman probe đầu dừng ở health transport `BadStatusLine`, chưa có collection acceptance chạy; giữ là lần chạy FAIL. Vòng mới dùng cấu hình loopback NO_PROXY rõ ràng đạt 203/587. Negative control của health vẫn FAIL đúng dự kiến, không chuyển thành acceptance PASS.
- Review bổ sung bảo vệ cả draft chưa hợp lệ khi rời trang, bearer còn hợp lệ khi user cache hỏng, và Header dùng AuthContext thay vì đọc token độc lập từ storage. Rerun mới đã phủ các trường hợp này.
- Skip-link trong hash router phải focus main thay vì đổi route khỏi bản nháp. Đã sửa đúng hai handler liên quan và thêm kiểm tra bàn phím khi đang sửa; chạy lại 32 UI/Home/Header cases và 8 real E2E trên source mới, giữ nguyên checks Backend/lifecycle không bị ảnh hưởng.

Failure artifacts được giữ trong ignored `backend/target/dt2-28-*-first/second/third` và local review records; không ghi đè kết luận FAIL thành PASS hoặc gọi fixture HTTP là bằng chứng Backend thật.

# DT2-5 — bàn giao BE + FE, cập nhật 20/11/2026

- Owner: Nguyễn Xuân Lộc. Nhánh duy nhất: `feature/dt2-5_feedback-management`, tách `origin/develop@48bc136`.
- Ngày 20/11/2026 owner yêu cầu đưa PR Task 5 lên GitHub. Đã xác minh #20 MERGED, merge commit `9a88ba6dfb82242b1c2f12fb1c4ebdd91b09a39b` thuộc origin/develop. Đồng bộ vào nhánh Task 5 bằng merge `4252dcf`, không conflict; không merge Task 5 vào develop/main.
- Phase hiện tại: chuẩn bị một PR BE+FE vào develop theo yêu cầu owner. Diff so với develop chỉ chứa DT2-5; giữ routes Auth/Profile/Customer từ #20.
- Nguồn/AC/kiểm kê: [yêu cầu](dt2-5-requirements.md). Nội dung PR và ảnh nhúng tại [mô tả PR](dt2-5-pr-description.md).

## Đã triển khai

| Chức năng | Backend | Frontend | Kiểm chứng | Trạng thái |
| --- | --- | --- | --- | --- |
| Danh sách | GET admin, count/list, sort/date/id, phân trang | Bảng ID/Creator/Email/Date/Feedback, loading/error/empty | MySQL, mock browser, HTTP thật | Đã triển khai |
| Chi tiết | GET ID, TEXT/rating, ảnh join theo thứ tự | Feedback's Info; chọn A→B/lỗi xóa dữ liệu cũ | MySQL, race/404, HTTP thật | Đã triển khai |
| Search by Date | Range UTC của ngày Asia/Ho_Chi_Minh, half-open | Date/Search; thông báo đúng prompt | Biên đầu/cuối ngày, nhiều giờ, no results | Đã triển khai |
| Show All | GET không date | Reset date/trang/selection | Mock + browser HTTP thật | Đã triển khai |
| Ảnh | Metadata ảnh chưa deleted, HTTPS không userinfo | Hiển thị/fallback ảnh lỗi/không ảnh/private | Metadata SQL + ảnh fixture mock | Consumer đã có; Firebase thật chưa nghiệm thu |
| Authorization | Signed bearer và role/status/auth_version hiện hành | Dùng AuthContext; không mở cho anonymous/STAFF/CUSTOMER | MySQL signed bearer, UI và Newman401/403 | Đã triển khai |

API thực tế và contract: [GET list/detail](../api/dt2-5-feedback-management.md). Không thêm/sửa schema, index hoặc migration, không seed lúc app khởi động. Chỉ đọc feedback/feedback_images/media_assets. Không thay POST Feedback khách hoặc tự đổi UNREAD/READ. MySQL kiểm thử riêng dùng fixture hiện có theo DDL bàn giao; không coi đây là bằng chứng DDL production đã nghiệm thu.

## File thay đổi

- Backend: `feedbackmanagement/FeedbackReadController.java`, `FeedbackReadService.java`, `FeedbackReadErrors.java`; tests `FeedbackReadMySqlTest`, `FeedbackReadUrlTest`.
- FE: `frontend/src/feedback/FeedbackManagement.jsx`, `feedbackApi.js`, `feedback.css`; tích hợp nhỏ trong `AdminDashboard.jsx`, `HomeShell.jsx`.
- FE test: `frontend/tests/feedback-management.spec.js`, `frontend/unit/feedback-url.test.mjs`; runner thật `frontend/integration/dt2-5-real.mjs`.
- QA: `scripts/qa/dt2-5-real.ps1`, fixture `docs/qa/dt2-5-http-fixture.sql`, collection/environment mẫu `docs/qa/postman/dt2-5-feedback-management*`.
- Tài liệu: contract API, requirements/inventory, handoff/draft PR này, mục DT2-5 trong open-questions, ảnh/summary trong `docs/handover/evidence/dt2-5/`.

## Kiểm thử giai đoạn A — lịch sử trước đồng bộ #20

| Check | Lệnh / môi trường | Kết quả |
| --- | --- | --- |
| Backend focused + hồi quy khách | mvnw -B -ntp -o test -Dtest=FeedbackReadMySqlTest,FeedbackReadUrlTest,CustomerFeedbackMySqlTest,FeedbackImageMySqlTest, Mockito javaagent | 16/16 PASS |
| Backend toàn repository | mvnw -B -ntp -o verify, Mockito javaagent; Java25 compile release21, MySQL8.0.46 Docker, smart_cafe_dt25_test | **323/323 PASS**, 0 failures/errors/skips, BUILD SUCCESS |
| FE lint/build | npm run lint; npm run build | PASS |
| FE unit | npm run test:unit | **5/5 PASS** |
| Playwright toàn repository | npx playwright test --workers=4 | **206/206 PASS**, baseline develop chưa có FE #20 |
| Playwright DT2-5 cuối sau chỉnh CSS/keyboard | feedback-management.spec.js, desktop + mobile | **30/30 PASS**, gồm axe, timeout, race, error/empty/auth/image |
| Browser → API → MySQL thật | scripts/qa/dt2-5-real.ps1 -Container <own-test-container> | **14/14 checks PASS**, desktop và mobile viewport, không route mock |
| Newman6.2.1 collection DT2-5 | JAR HTTP thật, tokens tạm từ cùng signed contract, schema HTTP *_test mới | **9/9 requests, 9/9 assertions PASS** |
| Registry/routing | node scripts/skills.mjs check; node --test scripts/skills.test.mjs | 17 skills/16 routes/169 files OK, 9/9 PASS |
| Whitespace | git diff --check, sau đó staged diff check | Sạch |
| CI | Chưa có PR DT2-5; không sửa workflow hoặc tạo PR chỉ để kích CI | Chưa chạy CI riêng cho nhánh này |

Lượt HTTP đầu thất bại do mysql CLI diễn giải UTF-8 fixture bằng charset không đúng (HEX author_name chứng minh mojibake); runner đã thêm `--default-character-set=utf8mb4`, chạy lại trên schema mới và đạt. Không thay assertions hoặc dữ liệu production để che lỗi. Thử nghiệm thật dùng tài khoản tổng hợp trong *_test và secret runtime ngẫu nhiên; không dùng tài khoản demo trong production. Không xuất token vào summary/ảnh/Git. Java/Vite test được dừng, env process được khôi phục khi runner kết thúc. Fixture test còn trên container riêng để tái hiện, không ghi DB nhóm.

## Ảnh và bằng chứng

- 14 PNG mock API: desktop/mobile cho danh sách, chi tiết, search thành công, Not found, search trống, Show All, ảnh fixture.
- 6 PNG HTTP thật: [chi tiết desktop](evidence/dt2-5/real/01-detail-desktop.png), [chi tiết mobile](evidence/dt2-5/real/01-detail-mobile.png), tìm ngày và không kết quả ở mỗi viewport.
- [Summary HTTP thật](evidence/dt2-5/real/results.json). Không kiểm thử Firebase/bucket/IAM, không báo ảnh cloud thật PASS. Ảnh mock và HTTP thật được lưu ở hai nhóm riêng.

## Giới hạn và bước kế tiếp

- Jira DT2-5/DT2-37–42, SRS v4.1 và Figma chưa truy cập đủ quyền. Figma session bị từ chối, Jira connector trả site chưa được cấp quyền. UI dùng bố cục Admin hiện có + prompt owner; chưa tuyên bố pixel-match hoặc nghiệm thu toàn bộ tài liệu nguồn.
- Firebase/QR tạm dừng; metadata private không bảo đảm URL xem được. FE có fallback, chưa ký URL hay công khai bucket.
- Điều kiện publication đã đáp ứng ngày20/11: #20 MERGED và owner yêu cầu tạo PR. Kết quả giai đoạn A phía trên là lịch sử; kiểm thử candidate sau đồng bộ được ghi riêng, không dùng CI #20 thay CI Task 5.
- Chưa tự approve, resolve review thread, merge, thay quyền hoặc triển khai.

## Skills và tự review

`cafe-requirements` / `cafe-workflow` (`.agents/skills/<identifier>/SKILL.md`) dùng để version hóa prompt, kiểm kê và giữ gate PR. `cafe-spring-api` giữ query/DTO/quyền server và không đổi schema. `vercel-react-best-practices` (`.agents/skills/react-best-practices/SKILL.md`) cùng `fixing-accessibility` dùng cho request hủy được, reset state/selection, bảng/form/keyboard và responsive. `cafe-quality` / `cafe-review` dùng để kiểm lỗi/quyền/date/race, phân biệt mock/HTTP thật và đọc diff trước handoff. Figma design-to-code đã đọc nhưng không áp dụng bản thiết kế vì quyền truy cập bị từ chối. Self-review không thay hai independent approvals.

## Kiểm thử candidate sau đồng bộ — 20/11/2026

Lint/build PASS; FE unit6/6 PASS. Full Playwright local:259/260 PASS,1 lỗi ghi PNG của capture-evidence DT2-4 (Windows `UNKNOWN: unknown error, open ...07-fe-feedback.png`); chạy lại đúng ca mobile, workers1 đạt1/1. DT2-5 đạt30/30. Backend `mvnw -B -ntp -o verify -Dtest=FeedbackReadUrlTest` với Mockito javaagent đạt1/1, compile toàn bộ BE thành công. Full Maven đã thử nhưng dừng vì một số integration Auth vẫn cần MySQL kể cả khi không có TEST_DB_URL; Docker engine đã mở nhưng API `/info` và container/start trả500. MySQL/HTTP local chưa chạy lại, không coi DB unavailable hoặc test bị bỏ qua là PASS. Toàn bộ Backend MySQL/FE mới xem CI đúng HEAD Task5, không suy số206/323 lịch sử thành kết quả hiện tại. Mã CF cho tiêu đề chưa có trong nguồn Task5, đã hỏi owner, không tự gán mã hoặc sửa validator.

Một PR chung BE+FE; không tự approve, resolve thảo luận, merge hoặc triển khai. Jira/SRS/Figma và Firebase thật giữ các giới hạn nêu trên.

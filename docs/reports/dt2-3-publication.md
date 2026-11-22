# CF-04 / DT2-3 / DT2-27 — publication verification, 18/11/2026

> Publication baseline dưới đây thuộc `f6d38243`. Kết quả sửa review sau đó, candidate với Customer mới và trạng thái Draft được cập nhật ở [review follow-up](dt2-3-review-follow-up.md). Không dùng JAR/counts của baseline như bằng chứng cho runtime sau sửa.

Owner xác nhận **CF-04** và cho phép commit/push/**Draft PR vào develop**. Commit/title dùng chính xác:

```text
feat(account): CF-04 implement DT2-3 account profile and password management backend
```

Branch giữ `feature/dt2-3_personal-account-backend`; base hiện hành `331c1e329dac40780c56b75aaf32403c4acddadf`. Không force-push, merge/self-approve/JiraDone, deploy hoặc đổi CI/validator/policy. Primary Backend local đạt; **không gọi full integrationPASS hoặc ready-to-merge**. [Machine evidence](dt2-3-publication-results.json) pin sources/counts và nguồn kết quả, không chứa raw credentials/logs.

## Đồng bộ và phạm vi

54file local và private config đã backup ngoài Git trước thao tác. Fetch origin --prune, stash tracked/untracked có bảo vệ, fast-forward từffa4d95 lên331c1e3, apply stash; **0merge conflict**, đủ implementation được khôi phục. Stash/backup được giữ để phục hồi. Develop mới chỉ thêm Auth frontend PR #17 (đã merge); không ghi đè work của thành viên. Backend runtime/tests/scripts/API logic không bị viết lại; base đổi nên vẫn chạy lại regression theo yêu cầu.

Allowlist49file đã review làm cơ sở; thêm3artifact cần thiết: report này, machine evidence hiện hành và `news-profile-consumer-latest.patch`, tổng **52file**. Năm file chỉ local vẫn loại: PR body, inventory cũ, Backend report index, generated Postman README/HTML. Raw Newman/environments/tokens/logs/XML, `.env`, exports/build/cache/private reviews và whole candidate checkout không commit. Patch/module code của Staff/News chỉ ở documentation; Maven source primary không nhập Staff/News/Customer/Bills chưa merge.

Staged whitespace ban đầu phát hiện space-only context markers trong ba patch tài liệu. Đã bỏ đúng space context trên dòng rỗng; Git apply syntax kiểm lại trên source/candidate lịch sử vẫn đạt. Không đổi additions/deletions của code, assertions hoặc validator. `inputs.json` giữ as-rehearsed digests và hashes artifacts hiện hành/LF; lịch sử không bị đổi thành PASS.

## Kết quả local mới

| Check | Actual result |
| --- | --- |
| Full Maven/MySQL, fresh owned schema |**260tests,0failure,0error,0skip — PASS**, finished09:39:34+07 |
| Profile Newman đủ hai vòng + Menu/QR nguyên collection |**203requests/587assertions — PASS**,10SQLchecksPASS; finished09:44:34+07 |
| Auth/OTP/Home verified-db + HTTP/SMTP loopback thật |**39requests/57assertions — PASS**,4expected-failure controls riêng |
| Frontend hiện tại từ develop |lint/buildPASS; **Playwright100/100PASS**; không thay mocked UI bằng claim live Profile UI E2E |
| Governance/Git Flow |skills17/routes16/inherited169PASS;17testsPASS |
| Negative health control |1request/3assertions/1failure, CLIexit1 **đúng kỳ vọng**, không cộng acceptance |
| Candidate baseline trước News consumer proposal |11tests/6failure/0error/0skip — **FAIL**: Profile401 và5Staff concurrency failures |
| Full selective candidate sau đúng một News consumer proposal |**428tests/17failure/0error/0skip — FAIL**, finished09:54:03+07 |
| Focused Bills probe bằng Login thật sau full suite |**1test/4HTTPassertionsPASS**, finished09:57:23+07; không thay kết quả12Bills fixture failures |
| SonarQube |**NOT RUN**, chưa có approved server/token/scanner; không thay quality gates |

Primary source133file/JAR được đối chiếu với HTTP run hiện hành. JAR SHA-256 `7e1fca9a1138b02912700b2f7004508732e2e9361da359e8b39cf71b5e3f2b12`; không đổi vì executable Backend không đổi. DDL20261113.1 giữ SHA-256 `84cb9f2e99073ea73b117e828dc7f144928052a8f8345cde6f903b82280e4b5e`. Native Flyway/preflight ON trên verified test schemas; không sửa database chung. Các suite dùng fixture CI lịch sử được phân biệt với native provision tests/verified-db HTTP; không tuyên bố toàn bộ fixture là deployment target.

Updated HTTP examples/error responses lấy từ lượt hiện hành, credentials redacted. [Primary JSON](dt2-3-postman/results.json) chứa timestamp/source/JAR/checks; [Auth JSON](dt2-3-postman/auth-regression.json) là kết quả riêng. Logs/XML/private environment không ở Git. Source hashes là raw working-copy bytes của lượt chạy dirty, không phải claim về Git blob/commit; trước publication so đúng LF normalization scope.

## PR heads và blocker đã đối chiếu

| PR / revision | Trạng thái và kết luận |
| --- | --- |
| #17 `1b82e39` |Closed/merged; develop331c1e3 chứa Auth frontend. Đã chạy100UI tests trên base này |
| #11 Staff `b74a365c742eecc900d097ab040bd131bb1f6c97` |Open. Auth/verified resources đã khôi phục, Auth dashboard được rename và matcher management ADMIN-only. Không giữ finding revert/bean collision của HEAD cũ làm lỗi current. **Writer version/CAS vẫn thiếu**,9concurrency tests tái hiện5failure |
| #15 News `c2343edecd3b71e57ee3ce6605db4dc5a03bc6cc` |Open. Root/path boundary đã sửa; vẫn thiếu `/api/v1/profiles` trong authenticated resource list, real Login GET Profile401 expected200. Candidate-only one-line proposal làm Profile22/22PASS |
| #14 Customer `a41152da02fc8c76d180bdf17a0b64722ee579b3` |Open, mới cập nhật trong lúc verify. Auth/verified resources đã khôi phục. Bốn domain/test Git trees customeractions/customerfeedback **giống hệt ref057531c đã chạy**, nên reuse kết quả21tests; không giả đã chạy nguyên standalone security chain mới |
| #18 Bills `86b70e2d5540f96b3e32d1e92afcff611ec5ccf8` |Open. Candidate14Bill tests có12failure403: fixtures mint four-field `id:username:ANY:expiry`, không tương thích current versioned Auth parser/News consumer. Không bỏ test hoặc sửa member source để báo xanh |
| #16 Menu `2ac0104` |Closed/unmerged Draft; không coi đã vào develop. Current Menu/QR runtime regression vẫnPASS |

Source evidence trong [machine report](dt2-3-publication-results.json) gồm exact tested refs và Git-tree equivalence Customer. Selective candidate giữ primary Auth/Profile, thêm exact domain/tests Staff/Customer/News/Bills, nhận Staff dashboard rename, giữ Staff ADMIN management trước broad namespaces, ghép guest/News/Profile/Bills rules và dependency/config của modules. Không literal merge các PR chưa approve vào nhánh feature hoặc develop; không gọi shared-chain union này là quyết định đã duyệt.

Chỉ apply [News Profile consumer proposal](../qa/rehearsals/dt2-3/news-profile-consumer-latest.patch) trong candidate. **Không apply Staff version proposal lịch sử**: năm concurrency failure tiếp tục cho thấy writer hiện hữu chưa tham gia users.version. Full428failures gồm5Staff và12Bills fixture-auth, không có error/skip. Profile22/22, Customer21/21, News suites và các nhóm khác đạt trong candidate; không suy ra whole integrationPASS.

Để phân biệt Bills fixture với lỗi endpoint, thêm một diagnostic method chỉ trong candidate sau khi đã lưu full XML/source manifest. Dùng Login thật trên Tomcat/native MySQL với dữ liệu tổng hợp rỗng: STAFF GET bills200, ADMIN GET statistics/revenue200, STAFF statistics403, CUSTOMER bills403.1testPASS chứng minh plumbing/permissions trong phạm vi này; không chứng minh toàn bộ billing business cases hoặc làm12test gốc thành PASS. Owner Bills/Auth cần migrate bearer fixtures bằng issuer contract đúng và chạy lại tests của mình; DT2-3 không sửa module đó.

## Lịch sử FAIL và việc còn cần owner

[Rehearsal15/11](dt2-3-integration-results.json) **396test/2failure/0error/0skip vẫnFAIL**: hai success request Staff thiếu version của unaccepted proposal. Đó là caller migration proposal, không phải baseline failure PR #11. Proposal lịch sử9/9PASS không chứng minh Staff HEADb74a365 đã nhận contract. Lượt hiện hành dùng actual writer mới và tái hiện5failure riêng. Lỗi thiếu News audience ở các lượt assembly đầu đã được nhận là lỗi dựng candidate, không quy cho baseline News.

Owner Staff nhận shared expected-version/lock users→staff_profiles/auth_version transition; owner News/Auth nhận Profile consumer/shared matcher reconciliation; owner Bills nhận token fixtures. Staff-other-personnel regression hiện hành trả403 khi giữ matcher mới, không tiếp tục báo leak200 của candidate cũ như current behavior. Các isolated shared chains vẫn phải hợp nhất có chủ đích trước merge module khác.

AUTH-GATEWAY-01 vẫn **NEEDS OWNER DECISION trước release**; bounded localhost observation không phải DoS/brute-force production proof. DBA cần xác nhận instance/lineage/grants/DEFINER/rollout/backup; metadata shared DB ở report cũ là snapshot có ngày, không phải mới audit hoặc phép migrate. Sonar environment và quality decisions chưa được thay bằng local tests.

## Draft PR và review

Reviewer dự kiến: `NV-DuyManh`, `haitan2408`, khác tác giả. Commit/title đúng CF-04 ở đầu file, head current feature, base develop, Draft. PR body phải phân biệt primary PASS, candidate FAIL17, lịch sửFAIL2 và các kiểm tra NOT RUN. Trạng thái GitHub Actions được đọc trực tiếp sau publication và ghi trong PR; local report này không giả CI đã chạy.

Các lỗi CI thuộc DT2-3 sẽ sửa và push cùng nhánh sau review lại phần thay đổi; không sửa validator/workflow hoặc branch protection để vượt checks. Không self-approve, resolve hộ discussions, merge hoặc JiraDone. Draft publication không là merge/release readiness: còn owners/dependencies, fresh required checks và hai independent approvals trên revision cuối.

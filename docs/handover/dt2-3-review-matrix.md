# CF-04 / DT2-3 — finding matrix and proposed security contract

Latest base: develop `b96a821`, feature sync `e2a3eb3`; primary315 and proposed B468 freshly rerun after Customer merge. Earlier snapshot18/11/2026: develop331c1e3, Profilee7848df, officialStaff57ea6f1, Customer50745ba (round 2: 468 tests; round 1: ebe0a97, 461 tests), Newsc2343ed, Bills86b70e2. Full SHA, timestamps, source/fixture/POM/config/patch/JAR hashes and actual commands/results are in [machine evidence](../reports/dt2-3-review-follow-up-results.json). No member branch or foreign production module is changed in PR19.

Read6review submissions,5inline threads and1conversation. Conversation6970922514 translates review5951694756; it is not a new finding. The latter review's Staff allegation is stale against57ea6f1. At read time3old code threads were already marked resolved; this does not establish reviewer approval or who performed those earlier actions. The2documentation threads now have the authorized replies and were verified unresolved after restoring their open state when the final API snapshot showed author-account resolution. No resolve/approve/merge action was requested by this agent.

## Finding-to-fix matrix

| ID / severity | Reviewer/source | Expected/actual and root cause | Status / correction | Regression / evidence |
| --- | --- | --- | --- | --- |
| F01 P2 | tranhoainam298, [BCrypt thread](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/19#discussion_r4608894486), ProfileService63–78/e784 | f6 BCrypt held a JDBC connection/users lock; e784 verifies/encodes before transaction, then rechecks hash/auth_version/current authority and commits before issuing bearer. | **FIXED e784**, preserved; no rewrite. |6controlled BCrypt/snapshot cases, existing reset/password/rollback/token cases; current BProfile31/31PASS. |
| F02 P1 historical | NV-DuyManh/NguyenXuanLoc1, [writer thread](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/19#discussion_r4608868849), StaffService309/349/57ea6f1 | b74 ignored version, causing5/9failures. Official57 now locks users, requires version, CAS/increments version, updates auth_version on role/deactivate and migrates callers. | **FIXED IN OFFICIAL STAFF HEAD**; old allegation **STALE**. No Staff proposal applied. | OfficialStaffProfileConcurrencyTest13/13; official StaffControllerTest18/18/avatar caller and StaffHttpReportTest PASS in B.10official Staff source/test files match Git bytes. |
| F03 P2 | NV-DuyManh, [stale handoff thread](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/19#discussion_r4610003326) | Handoff continued to call missing CAS active despite new official57. | **FIXED documentation**: current sections, README/open-questions/reports/handoffs corrected. | Historical5/9baseline,9/9proposal,446old candidate remain unchanged; current A/B evidence kept separate. |
| F04 P1 | NV-DuyManh etal, [News thread](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/19#discussion_r4608868862), HeaderTokenFilter19/c234 | Valid Login Profile200 expected,401 actual because resource list omits Profile. Appears when modules compose. | **INTEGRATION BLOCKER / OPEN official News**. Minimal boundary-list patch in B/docs only; owner NV-DuyManh must adopt. | B0Profile1FAIL401; BProfile31PASS and path boundary6PASS; token/ACTIVE/auth_version/replacement and News REST/realWebSocket cases preserved. |
| F05 P1 historical/current composition | same reviews, shared SecurityConfig | Old broad staff matcher leaked personnel200. Current independent chains require explicit reconciliation. | Old leak **STALE/FIXED in corrected candidate**; literal shared-chain merge still **INTEGRATION BLOCKER**. | STAFF GET/PUT other account403 with valid CSRF context, no salary leak; context starts without bean overriding; Customer/News/Sales/Bills suites pass B. |
| F06 P2 | three re-reviews, Bills86 BillMySqlIntegrationTest53 | Four-field ANY fixture signer causes12composed403failures before business handler. | **INTEGRATION BLOCKER / OPEN official Bills**. TokenService/current DB claims fixture patch for tranhoainam298; no verifier relaxation. | B0Bills14/12FAIL; B14/14PASS with all business/status assertions retained. Not12standalone defects. |
| F07 P2 evidence | re-reviews5951669528/5951694756/5951810623 | Old446 used b74/a6 with proposals; cannot prove official57/latestCustomer composition. | **FIXED classification**; official A still **BLOCKED** by conflicts. B is proposed composition, not A/owner adoption. | Literal merge exit2/5conflict paths; B0 28/13FAIL; full B round1/ebe461/0failure/error/skip; round 2/507: 468/468 PASS, HTTP 230/623 and Auth 39/57. Pinned manifests and commands. |
| F08 P3 | NV-DuyManh, [Draft thread](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/19#discussion_r4610003311) | Committed handoff said Draft while REST returnedfalse. State can change outside Git. | **FIXED timestamped snapshot/process**: supported conversion, then APItrue at2026-11-18T06:40:01.779796Z; verify again at publication. HOLD independent of flag. | Before/after API evidence; final API verification and inline reply reference finalcommit. No permanent-state claim. |
| F09 security policy | all re-reviews, AUTH-GATEWAY-01 | Bounded probes show no429; no new approved shared admission/body-cap policy in current sources. | **RELEASE GATE / NEEDS OWNER DECISION**. No new threshold, limiter, table or lockout. | Local observations only; no production DoS/brute-force claim. Auth/Gateway owner approves deployed topology/budget/body cap and verification. |
| F10 operations | all re-reviews | Isolated schemas, loopback SMTP and provider doubles are not production acceptance. | **RELEASE GATE: BLOCKED/NOT RUN** DBA lineage/grants/DEFINER/backup/rollout, SonarQube, production SMTP/storage. | Native provision/validate/repeat on owned_test; owners supply approved environment/configuration and acceptance evidence. |
| F11 frontend | all re-reviews, AuthContext/HomeShell | Editable page/expiry state/replacement-token flow absent; no DT2-28 PR found at snapshot. | Browser Profile E2E **NOT RUN**, not a Backend defect. | npmci/lint/build/100PlaywrightPASS are existing UI regression. Full-stack plan below requires real HTTP/MySQL, no Profile/password mocks. |
| F12 inherited dependency risk | required npmci/npm audit | Audit exit1:5high affected packages from1picomatch advisory, not5new Profile defects. | **RELEASE GATE / OPEN Frontend/dependency owner**; no forced major downgrade/bypass. | [GHSA-fc5q-39xc-vjpf](https://github.com/advisories/GHSA-fc5q-39xc-vjpf), path picomatch→fdir/tinyglobby→vite→plugin-react. Advisory lists no patched version at lookup. Owner selects approved remediation and reruns audit/build/UI; no exploit or production reachability claim. |

No new standalone Profile P0/P1/P2 was reproduced. F03/F08 are the two authorized inline replies. Original historical stdout overwrite remains a limitation; XML/source manifest/report survive, no reconstruction claimed. New logs use distinct paths.

## Candidate B endpoint contract — proposed composition

TokenService is byte-identical across all6refs: standard5-field issuer. One News consumer retains HMAC/expiry/current DB ACTIVE/role/auth_version. Existing documented legacy forms are not expanded; ANY fixture remains rejected. Profile resource recognition is exact root or root+slash; lookalikes do not match and fallback denies them.

| Endpoint / method | Roles / authentication | CSRF | Ownership/authority and admission |
| --- | --- | --- | --- |
| Auth POST login/recovery/OTP | public credentials/proof | existing Auth exception | DB authority/OTP ownership, expiry and CAS; existing OTP quota only; shared login/body policy pending |
| GET home/account | ACTIVE signed bearer | safe GET | own projection, no CUSTOMER salary |
| GET home/notifications | current STAFF/ADMIN | safe GET | DB role/auth_version |
| GET profiles/me | ACTIVE ADMIN/STAFF bearer | safe GET | principal only, query cannot select another account |
| PATCH profiles/me | ACTIVE ADMIN/STAFF bearer | exact bearer-only exception | own name/address/phone/version allowlist, CAS; no private limiter |
| POST profiles/me/password | ACTIVE ADMIN/STAFF bearer | exact bearer-only exception | current/reuse/policy, snapshot recheck, atomic revocation and replacement after commit |
| GET staff/dashboard | ADMIN/STAFF bearer | safe GET | exact dashboard exception before management namespace |
| GET staff root/subpaths/csrf | ADMIN bearer | csrf GET creates token | explicit root+subpath management matcher; no STAFF personnel access |
| POST/PUT/DELETE staff/avatar | ADMIN bearer | required token/cookie | official DTO/version, users→personnel locks, role/salary/media validation |
| GET customer menu/QR | public | safe GET | server menu/QR/table context |
| GET customer/csrf,customer/feedback/csrf | guest capable | creates CSRF context | no personnel auth implied; existing admission budgets |
| POST customer session orders/waiter/bill requests | guest session token | required | session_clients hash/session/table locks; exact routes, no broad permitAll |
| PATCH/DELETE customer session order-items | guest session token | required | ownership/edit state/version/idempotency/server prices |
| POST customer/feedback | public submission | required | validated content/images, transaction/compensation; official quotas/multipart/image/storage and507 feedback-only JSON cap (does not close AUTH-GATEWAY-01) |
| GET news/list/detail/images | ADMIN/STAFF/CUSTOMER bearer as official reader permits | safe GET | publication/audience/current DB authority |
| POST/PUT/DELETE news, POST news/images | ADMIN bearer | existing News exception | versions/ETag/media/audiences, event after commit |
| WebSocket news/events | STAFF initial auth frame | same-origin handshake | current STAFF/auth_version revalidated, auth deadline and official connection budgets; implementation is WebSocket, not SSE |
| GET bills/list/detail | ADMIN/STAFF bearer | safe GET | server snapshots/paging/filtering |
| GET income/statistics/revenue | ADMIN bearer | safe GET | server VND totals and time windows |
| Sales GET csrf/tables/services, POST bill/ack | STAFF/ADMIN bearer | mutation CSRF retained | session/version/idempotency/server price authority |
| POST bill payments/table reset | ADMIN bearer | required | bill/session identity, idempotency, money/history |
| ERROR dispatch / all other paths and methods | error dispatch allowed; fallback denyAll | no new exemptions | sanitized advice/scoping; no bean override |

## DT2-28 real-browser E2E plan — NOT RUN until UI exists

1. Start verified MySQL_test and real Spring Boot; synthetic ADMIN/STAFF/CUSTOMER login through browser. No Profile/password interception. Check top-level expiry flags and precise ID/salary/version projection.
2. Edit only allowed fields. CANCEL leaves SQL unchanged. PATCH uses version from opened form. Second editor produces409; reload/show current values and require a new user decision, without blind retry.
3. Save replacement bearer before Header/Profile refresh; discard late old-bearer responses. Old bearer/reset grant fail, new bearer returns own page.
4. Lose response after committed password change: no replay with old bearer; sign in using new password. Verify invalid-token/logout cleanup and delayed requests cannot restore a session.
5. Verify before/at/after30×24h UTC and DB flag warning, CUSTOMER personnel denial. Capture actual HTTP/SQL with browser assertions; existing mocked UI tests do not establish this E2E.

Maintainer next gate: reconcile A conflicts with this endpoint table, adopt News/Bills fixes on official branches, then rerun combined evidence at final revisions before independent approval/merge. CI green alone does not close these gates.

Customer PR14 is now merged as b96a821. The feature integrated that approved base without conflicts; latest official A still exits2 at the same five conflict paths. Fresh primary315/315 and proposed B468/468, plus separate live HTTP/Auth, are pinned under machine evidence currentIntegration.postCustomerMerge. Own Profile runtime is unchanged from e7848df. Documentation corrections were first published at bd22f63; final synchronization commits are linked in PR19.

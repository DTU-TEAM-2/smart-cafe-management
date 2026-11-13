# DT2-2 — Nam review, ENV/config and edge-case audit

Supplement to [security hardening](dt2-2-security-hardening.md), same uncommitted work based on `62ca3f1b9c0c1f648436806cab30314d20c8a786`. No reset/checkout/clean performed. Owner explicitly requested this addition without abandoning earlier fixes. [Nam's review](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/7#pullrequestreview-5875518095) accepts CAS on code review and leaves three P2 notes; it does not establish an unfixed CAS P1.

## A. Git status and scope

Branch `feature/cf-03-auth-recovery`, starting HEAD `62ca3f1b9c0c1f648436806cab30314d20c8a786`. The original hardening round started clean; this supplemental request arrived with the in-progress hardening edits already uncommitted. Those edits were inspected and retained. Final changed-file inventory is in section G and the local commit; exact resulting SHA is reported in the final response and external review record, avoiding a self-referencing SHA in this file. Publication is not authorized in this round.

Skills carried into final verification/review: `cafe-quality` (`.agents/skills/cafe-quality/SKILL.md`) and `cafe-review` (`.agents/skills/cafe-review/SKILL.md`); implementation used the previously read `cafe-spring-api`, `cafe-database`, `diagnosing-bugs` and React skills recorded in the companion report. No delegation or additional dependencies.

## B. Nam's findings and root causes

| Finding | Classification / reproduced | Change | Evidence / final status |
| --- | --- | --- | --- |
| Nested transaction pool starvation | **CODE DEFECT**, reproduced with pool=2, 1s acquisition timeout: 7 cases, 6 failures, 0 errors/skips; active connections at read checkpoint=2 and requests return 500 | Remove transaction spanning initial verify read; retain CAS SQL unchanged. Wrong-attempt transaction still independently commits; successful CAS + user lookup/grant stay together inside the same TransactionTemplate. HTTP verify no longer holds an outer connection while acquiring an inner one. Do not increase runtime pool size | `pr7-pool-red.log`, final GREEN/full results below; bounded tests do not prove universal throughput |
| GET reset grant | **CODE DEFECT / hardening**, already addressed in the current diff; GET returned 200 before removal | POST body/no-store retained; GET405, consumer search, expired grant POST=false added to collection | See hardening report; do not apply duplicate patch |
| Stateless logout | **CONTRACT / OWNER DECISION**, bearer not revoked; actual UI cleanup defect reproduced with hanging acknowledgement | Keep server contract; clear browser credentials immediately, including failure/pending network; clarify copied token risk and proposal for any future revocation | Backend proof + desktop/mobile tests. Immediate server revocation remains **NEEDS OWNER DECISION** |

The first targeted GREEN pool attempt had one **ENVIRONMENT/fixture setup error** on cold JDBC initialization with a 1s timeout; six stress cases and the CAS regressions passed. The original error is retained in `pr7-pool-green.log`, not relabeled PASS. Cold fixture setup now uses a 5s acquisition budget, then restores and asserts the **1s** limit for all stressed requests. No runtime maximumPoolSize increase or business-test weakening.

### Transaction scope after fix

Initial challenge read is autocommit and releases its connection before verification. The conditional write is a current-state MySQL UPDATE checking the exact hash/generation, used flag, attempts and DB expiry. Failed CAS cannot issue grant. Wrong attempts run and commit in an independent transaction; their later OTP exception cannot undo the count. Successful consume, user lookup and grant generation share one independent transaction; a DB exception rolls them back. No new read lock is introduced. Resend keeps its row lock through delivery. The HTTP caller does not wrap verify in another transaction; all call sites were checked. A future caller that itself holds a transaction must be reviewed separately rather than assuming this HTTP capacity result covers it.

Pool tests use real MockMvc/MySQL, pool max=2/min=2, 1000ms stressed acquisition timeout, checkpoints after real SELECT, executors and finite waits. Cases cover 1,2,3 simultaneous wrong verifies, shared/distinct challenges, verify+resend, verify+reset, persisted counts/cap5 and a successful verify after load. Resend/reset must complete while the wrong verifies remain in flight at their checkpoint. They assert no pending connections and no active connections after requests finish. `POOL_METRICS` lines contain only counts/config/statuses, never OTP/user/token. Default application Hikari max/min are library defaults (10/10 unless overridden); configured connection timeout is 5000ms. Read-only metadata on the isolated schema confirms **REPEATABLE-READ**, session **+00:00**, server timezone **SYSTEM**, database collation **utf8mb4_0900_ai_ci** (`pr7-test-db-config.log`). MySQL isolation is not changed by this patch. No arbitrary sleep is used to schedule the race.

## C. ENV/config audit

### Loading model and verified defects

- `start-be.ps1` reads an allowlist from `backend/.env`, strips paired quotes, preserves already-defined process variables (including explicitly empty values), runs Maven and restores its process environment. It previously omitted all three validity keys and silently defaulted DB_USERNAME to root. Both defects are proven by copied-launcher/stub tests: 2 FAIL/1 PASS before, 3 PASS after. No real launcher was run against shared DB.
- `test-be.ps1` loads **only TEST_DB_URL/USERNAME/PASSWORD**, preserves nonempty process overrides, requires a `_test` URL and nonempty test credentials, then invokes Maven verify. It does not load runtime DB/SMTP/TTL/profile variables from the file. An explicitly empty test process variable is treated as absent, unlike the runtime launcher; tests document that boundary rather than relying on it for secrets.
- Direct Maven/JAR does **not** automatically import `.env` or `.env.example`; no spring.config.import of those files exists. Spring reads OS/process properties and application/profile resources. `.env.example` is documentation only. OS environment variables and higher-precedence CLI properties can override configured placeholders.
- Vite config calls loadEnv(mode,cwd,"VITE_") for VITE_API_PROXY_TARGET; application fetches use relative /api URLs. VITE_API_BASE_URL had no consumer and was removed from the example. Vite is not a way to inject backend secrets. No frontend default changed.
- Runtime DB default standardized to existing base Spring default **cafe_app** in launcher, prod example/property; explicit configured DB_USERNAME is preserved. No root fallback, account creation, credential change or DB switch occurs. CI root is explicit and restricted to its disposable test services.
- Startup fallback URL still names smart_cafe and runtime Flyway remains enabled in existing properties: **do not run start-be with unverified config**. This patch does not execute or approve a shared migration. Shared DDL/history remains deployment BLOCKED.

### Variable inventory

`SET/MISSING/INVALID/REDACTED` are used for secret/account values; no secret or secret hash is recorded. Examples are blank/placeholders, not copies of local `.env`.

| Variable | In example | Launcher | Application use / status |
| --- | --- | --- | --- |
| DB_URL | Backend | start only | datasource.url; example/fallback smart_cafe + UTC; shared deployment **BLOCKED** |
| DB_USERNAME | Backend | start only | datasource.username; default cafe_app; configured local **SET**, CI explicitly root on test service |
| DB_PASSWORD | Backend, blank | start only | datasource.password; local **SET**; never printed |
| TEST_DB_URL | Backend | test only | test datasource; validated `_test`, actual SELECT DATABASE verified |
| TEST_DB_USERNAME/PASSWORD | Backend | test only | local **SET**, isolation checked before fixture DML; no DB_URL fallback |
| SERVER_PORT | Backend | start | server.port default8080 |
| SPRING_PROFILES_ACTIVE | Backend, now explicit blank | start | default/dev/prod; no automatic prod default |
| AUTH_TOKEN_SECRET | Backend, now blank | start | TokenService key and shared Header/Menu consumers; local file **SET**, strength/entropy and other instances **NOT VERIFIED** |
| AUTH_TOKEN_VALIDITY_SECONDS | Backend | start, **fixed** | token TTL default86400; override test456 |
| OTP_VALIDITY_SECONDS | Backend | start, **fixed** | OTP TTL default600; override test345 |
| PENDING_REGISTRATION_VALIDITY_SECONDS | Backend, **added** | start, **fixed** | property default86400; override test567; no implemented registration owner/cleanup behavior is inferred |
| SMTP_HOST/PORT | Backend | start | default/prod blank-host/587; dev localhost/1025 unless overridden |
| SMTP_USERNAME/PASSWORD | Backend, blank | start | mail sender credentials; local **SET**, actual authorization **NOT VERIFIED** |
| SMTP_AUTH/STARTTLS/SSL | Backend | start | booleans; default/prod true/true/false; dev false/false/false for auth/starttls, SSL inheritsfalse |
| SMTP_FROM | Backend, **blank** | start | If absent, property uses SMTP_USERNAME; if explicitly blank, service uses legacy no-reply fallback. Gmail requires an explicit authorized From/alias; acceptance **NOT VERIFIED** |
| CLOUDINARY_CLOUD_NAME/API_KEY/API_SECRET | Backend | start | existing Menu config only, no changes to other module; secret fields local state not published |
| VITE_API_PROXY_TARGET | Frontend | Vite loadEnv | optional development/preview proxy target; defaults loopback8080 |
| VITE_API_BASE_URL | Removed unused example | none | no source consumer found; relative /api unchanged |

### Effective configuration scenarios

| Scenario | Expected | Actual / evidence type | Result |
| --- | --- | --- | --- |
| TTL only in backend/.env | Launcher passes configured TTL to Maven child | RED null; GREEN345/456/567 in copied launcher with Maven stub, no DB/network | **FIXED / PASS** |
| OS overrides file | Process value retained | GREEN OTP789 + username fixture override in stub | **PASS** |
| Test launcher scope | Runtime DB/TTL not imported; TEST_DB_* used | GREEN stub with disposable `_test` URL, explicit process username retained | **PASS** |
| Base/dev/prod resource placeholders | Explicit safe overrides reach beans and keys | AuthConfigurationTest loads actual main/profile properties into isolated context, no datasource/Spring server | **PASS**, 3 parameter cases; config-unit evidence |
| Too-short configured key | Startup fails safely | TokenService context rejection; existing regression retained | **PASS**, config-unit evidence |
| Missing secret | No stable signing identity | Current prod/default behavior still generates ephemeral key, warns; another process/key cannot verify token | **VERIFIED RISK**, fail-fast prod proposal **NEEDS OWNER DECISION** |
| Same stable key across restart | Tokens continue verifying | Two isolated TokenService instances, matching fixture key, expiry checks | **PASS**, not multi-host deployment proof |
| Different instance keys | Verification fails | Isolated test keys reject each other's tokens | Expected, production key distribution **NOT VERIFIED** |
| Real dev/prod runtime | Own JAR/loopback HTTP+SMTP, *_test only, explicit key, Flyway off | Each profile: original23 +14 negative requests /53 assertions PASS; four fault runs FAIL as required | **PASS**, no shared start-be or Gmail |
| Local secret/account inventory | No disclosure | File presence/blank/type and Process/User/Machine presence inventories only; no secret/hash printed | **VERIFIED** metadata, no running production process inspected |

`pr7-env-metadata.log`: local DB/test credentials, token secret and SMTP keys are **SET**; the secret meets the existing minimum length check, which does not prove entropy. `pr7-os-env-metadata.log` records OS-scope presence only. Direct Maven does not inherit values that exist only in the file; the live runner explicitly passes private TEST_DB values and its generated key instead. No claim that a production instance has the same effective environment.

### SMTP classification and safeguards

Do not conflate SMTP and CAS/config errors. Missing host is a readiness failure (neutral initial forgot; resend rejected consistently without hash change). SMTP auth failures, MAIL FROM/RCPT/DATA rejection, disconnect/network timeout are delivery failures; resend preserves old hash but consumes accepted budget. SQL/row-count exceptions remain outside delivery catch. Existing SmtpEmailServiceTest, UnconfiguredSmtpIntegrationTest and SMTP socket regressions retain those boundaries. Explicit provider-auth/alias validation against Gmail is **NOT RUN**.

Example uses587 + STARTTLS, with SSL disabled. Mixed modes/unauthorized sender can fail independently of OTP logic. Example requires an explicit authorized From for Gmail and no longer supplies a misleading arbitrary no-reply address. **Absent** SMTP_FROM falls back through the property to SMTP_USERNAME; **explicitly blank** SMTP_FROM instead reaches the service's legacy no-reply fallback. The example documents this distinction; copying it alone is not Gmail-ready. Actual local host/credentials are not published and no real email is sent. connection/read/write timeout5000ms are individual socket-operation limits, not a total end-to-end delivery SLA. Explicit test runtime overrides disable auth/TLS only for loopback capture, not for production.

## D. Auth/OTP edge-case evidence matrix

| ID | Function | Scenario | Environment / evidence | Expected | Actual | Result |
| --- | --- | --- | --- | --- | --- | --- |
| LOGIN01 | Login | Correct username/email, wrong/missing identity/password, INACTIVE | MySQL AuthIntegrationTest + real HTTP collection | Success only for valid ACTIVE credentials; neutral credential error | Existing backend regression and collection assertions retained | **PASS** in final suite/live |
| LOGIN02 | Authorization | Admin/Staff denial, role/status/version at token consumption | Header/Auth MySQL tests + live RBAC | DB current authority; reject stale/forbidden bearer | Authorized and forbidden paths covered | **PASS**; role change exactly between login reads is not a coordinated race test |
| LOGIN03 | Login | Six repeated failures then correct password | Real HTTP fixtures | Observe current behavior without unapproved policy | Six401 then200; no limiter established | **VERIFIED GAP / NEEDS OWNER DECISION** |
| LOGIN04 | Login | Arbitrary concurrent login saturation | Analysis only | Finite load/capacity evidence required for a capacity claim | No dedicated login-load test | **NOT RUN** |
| TOKEN01 | Tokens | Expired bearer/grant, matching/restarted/different/short keys | TokenServiceTest +5 config-unit cases | Reject expired/wrong keys; same stable key accepted; short key fails | Assertions pass | **PASS**, not multi-host deployment proof |
| TOKEN02 | Configuration | Missing secret, including prod properties | Isolated config context | Describe actual behavior; no silent readiness claim | Ephemeral key generated, restart rejects token | **VERIFIED RISK / OWNER DECISION** |
| FORGOT01 | Forgot | Existing/dummy, malformed, concurrent per-email rate/cooldown | MySQL OTP suites + loopback HTTP | Neutral valid request; preserve existing limits | Assertions pass | **PASS** for tested public responses; timing unmeasured |
| FORGOT02 | SMTP | Missing mail configuration | Unit + UnconfiguredSmtpIntegrationTest (real MySQL, mail not configured) | Neutral initial response; matching resend400; old OTP intact | Assertions pass | **PASS**; no delivery claim |
| FORGOT03 | SMTP | Delivery rejected/authentication exception | EmailService doubles in OTP suites; socket rejection cases where defined | Preserve old OTP and accepted public retry budget | Assertions pass | **PASS** for injected failures; real Gmail auth/TLS/alias **NOT RUN** |
| FORGOT04 | Storage | Eight different dummy emails from one client | Real HTTP / MySQL fixtures | Observe growth without changing policy | Eight dummy rows accepted | **VERIFIED GAP / OWNER DECISION** |
| VERIFY01 | Verify | Correct/wrong/expired/used/5 attempts | MySQL OTP suites + live negative fixtures | One-time valid grant; reject invalid states; cap5 | Assertions pass | **PASS** |
| VERIFY02 | CAS | Verify before/after resend, two verifies, stale wrong attempt, same hash with changed sends |18 real MySQL coordinated race cases | Current generation only; one consume; stale CAS0 cannot issue grant | Assertions pass | **PASS** |
| VERIFY03 | Errors | Consume row count0/unexpected count and SQL exception | OtpServiceTest repository/transaction doubles | Never turn DB failure into grant/success | Assertions pass | **PASS** at unit boundary; generalized DB outage **NOT RUN** |
| POOL01 | Pool |1 wrong verify, max2 | MySQL/Hikari bounded checkpoint |400, count1, recovery | PASS metrics, no pending/active leak | **PASS** |
| POOL02–03 | Pool |2 verifies =N, maxN=2 | Same environment, shared challenge | Both400; counts committed; no acquisition timeout | PASS metrics | **PASS** |
| POOL04 | Pool |N+1=3 wrong verifies, max2 | Same environment | All400 without starvation; attempt cap preserved | PASS metrics | **PASS** |
| POOL05–06 | Pool | Shared and distinct challenges,2/3 requests | Real SQL, latch/executor | Correct per-challenge counts | Persisted counts asserted | **PASS** |
| POOL07 | Pool | Wrong verify + resend on another challenge | Three verifies held at checkpoint, concurrent resend | Resend200 finishes before verifies resume; all verifies400 | Assertions pass | **PASS**; same-challenge semantics separately covered by CAS races |
| POOL08 | Pool | Wrong verify + reset on another challenge | Same bounded stress, same fixture user | Reset200 finishes; verify400; no starvation | Assertions pass | **PASS** |
| POOL09 | Pool | Recovery and cap after load | Each of7 parameter cases | No leaked active/waiting connections, cap5, fresh verify200 | Assertions pass | **PASS**, bounded stress only |
| RESEND01 | Resend | Cooldown boundary, sends3, real/dummy failure/success lifecycle and fixed expiry |18 retry MySQL cases + OTP suites | Same public1→2→3 budget, third resend rejected, old hash preserved on failure | Assertions pass | **PASS** |
| RESEND02 | Transaction | Two resends; SMTP accepted then DB rollback/row count failure | MySQL + mail spy/failure injection | One concurrent delivery; DB errors fail/rollback | Assertions pass | **PASS** for DB state; SMTP cannot be rolled back, **RESIDUAL RISK** |
| RESET01 | Reset | Valid/invalid/expired/used grant, mismatch/weak password, old password/bearer | MySQL suites + real HTTP negatives | Reject invalid paths; new password works for same fixture | Assertions pass | **PASS** |
| RESET02 | Reset | Two reset requests, INACTIVE at update, auth_version | Coordinated real MySQL race tests | One version update; ACTIVE enforced at SQL write | Assertions pass | **PASS** |
| URL01 | Grant validation | POST body valid/invalid/expired/no-store; GET removed | MockMvc + real HTTP collection | POST200 with boolean; GET405 | Assertions pass; no URL token in POST consumer | **PASS**; no-store cannot sanitize URL logs |
| LOGOUT01 | Client | Hanging/failed/503 acknowledgement, delayed completion, reload |42 desktop/mobile Playwright tests with HTTP mocks | Immediately clear both storages and recovery state; no restored session | All6 logout cases PASS with reload | **PASS** UI behavior, not authenticated E2E |
| LOGOUT02 | Server | Copied bearer after acknowledgement | Real MySQL/MockMvc contract test | Truthfully retain current stateless contract | Same bearer still gets /me200 | **VERIFIED**, revocation **OWNER DECISION** |
| CONFIG01 | Launchers | File-only TTLs, OS override, default username, TEST_DB-only loading |3 copied-launcher/Maven-stub cases | Correct allowlist/precedence, no shared access | RED2fail/1pass; GREEN3pass | **FIXED / PASS** |
| CONFIG02 | Runtime | dev/prod profile on safe datasource and mail | Real JAR / HTTP / MySQL / loopback SMTP | Complete recovery and strict negative assertions | Both profiles37 requests/53 assertions PASS;4 expected fault failures each | **PASS**, section F |
| CONFIG03 | DB | Isolation/timezone/collation, schema name | Read-only SELECT metadata, before fixture DML guards | *_test only; UTC session | REPEATABLE-READ,+00:00,utf8mb4_0900_ai_ci | **VERIFIED**, no schema change |
| CONFIG04 | Launch safety | Actual start-be against shared default DB | Code inspection and stub only | Avoid unapproved Flyway migration | Not started against smart_cafe | **NOT RUN** intentionally; deployment **BLOCKED** |
| INFRA01 | Production | Full DB outage, real Gmail, trusted proxy, timing/large-scale load | Not available/authorized | Independent infrastructure acceptance needed | No such claim or test result | **NOT RUN / NOT VERIFIED** |

Timing side-channel is not quantified. No synthetic probability or delivery-time guarantee is claimed. Local loopback tests are distinct from Gmail, production DB migration or trusted-proxy verification.

## E. Postman, database and security status

- **FIXED:** collection false-PASS assertions and fixture subject mismatch, GET grant endpoint, delayed client logout, TTL loader/default-username drift, verified nested-transaction starvation.
- **VERIFIED:** original CAS SQL/ACTIVE/auth_version behavior retained by regression; isolated test schema/UTC; current stateless bearer contract; no schema/migration/dependency changes in this patch.
- **BLOCKED:** shared database/Flyway deployment. Actual history collides with repository V001/V002 and staff_profiles is absent. See [the read-only mapping/history audit](../architecture/database-contract.md#dt2-2--hardening-audit-read-only-12112026-base-62ca3f1). No repair/baseline/migrate/DDL/DML on shared DB.
- **NEEDS OWNER DECISION:** login/IP/global admission, retention/cleanup, production key policy, server-side logout revocation. No new limiter, account lock, schema or deletion job added.
- **NOT RUN / NOT VERIFIED:** Gmail/provider sender approval and TLS/auth, deployed DB acceptance, real trusted proxy, timing quantification and general capacity. Newman profile results below are actual isolated runtime evidence, not these production checks.
- **RESIDUAL RISK:** delivery and DB commit cannot be atomic; synchronous mail continues holding the resend row lock; copied bearer remains valid; missing production secret still creates ephemeral signing identity. These limits are explicit, not converted into PASS.

## F. Test results

Evidence directory (outside Git): `C:\Users\khatr\review-guides\evidence\`. Final source/blob and log hashes are in the external verification manifest; no secret or secret hash is included.

| Check / actual command | Result | Evidence |
| --- | --- | --- |
| `$env:MAVEN_ARGS='-o'; powershell -NoProfile -ExecutionPolicy Bypass -File .\test-be.ps1` | **173 tests PASS,0 failures,0 errors,0 skipped,BUILD SUCCESS**; one new full invocation after final backend/pool/config test changes, completed12/11/2026 21:11:59+07,3m24s | `pr7-hardening-backend-final.log` and Surefire reports |
| Targeted `mvnw.cmd -o -B -ntp -Dtest=OtpPoolIntegrationTest,AuthConfigurationTest,OtpVerifyResendRaceIntegrationTest,OtpServiceTest test` |42 PASS before strengthening concurrent action checkpoints; final full includes strengthened7 pool cases and5 config cases | `pr7-pool-config-green.log`; final full is authoritative |
| `npm run lint`, `npm run build`, `npm run test:ui -- --workers=2` in frontend | Lint/build PASS;42 UI PASS including logout/reload desktop+mobile | `pr7-hardening-frontend-lint-final.log`, `...-build-final.log`, `...-ui-final.log` |
| `node --test scripts/auth-postman.test.cjs scripts/launchers.test.cjs` with private external NEWMAN_MODULE |24 PASS,0 skipped:21 assertion-integrity cases +3 copied-launcher/stub cases | `pr7-hardening-tool-tests-final.log`; HTTP stubs are not authenticated E2E |
| `AUTH_TEST_PROFILE=dev node scripts/auth-postman-live.cjs` with private TEST_DB/tool env | Original23 requests +14 negative requests,53 assertions,0 failures. Wrong OTP, invalid validation grant, rejected reset and wrong new-password each give expected Newman **FAIL** | `pr7-hardening-live-dev-final.log`; real HTTP/MySQL/loopback SMTP |
| Same command with `AUTH_TEST_PROFILE=prod` | Same37 requests/53 assertions PASS; all4 injected scenarios give expected **FAIL** | `pr7-hardening-live-prod-final.log`; explicit isolated overrides, not production deployment |
| `node scripts/skills.mjs check`, `node --test scripts/skills.test.mjs` | Registry17 skills/16 routes/169 inherited files PASS;9 routing tests PASS | `pr7-hardening-skills-final.log`, `pr7-hardening-routing-final.log` |
| `git diff --check -- . ':(exclude).agents/skills/**'` and final staged equivalent | PASS | External final review record |
| Secret/local-file review | Known local secret values checked without printing; env exports and database dumps excluded; final allowlisted staged inventory inspected | External final review record/manifest |

RED evidence retained: pool7 tests/6 failures; TTL/default username loader3 tests/2 failures; initial assertion integrity11 tests/7 failures; GET rejection1 failure; pending logout1 failure. The first post-fix pool attempt's cold-setup connection error and earlier harness fixture/authoring failures remain FAIL records. They are not relabeled or added to the final PASS count. Earlier161 backend full is historical, not part of173.

Both live profile runs assert scoped cleanup of their users/challenges/limit keys to zero. No raw Newman responses/environments, real SMTP credentials, OTP or password are exported to reports. Logs label expected fault runs **FAIL** rather than converting their Newman verdicts to PASS.

## G. Changed files and integration scope

- Runtime: `AuthController.java` removes unused GET; `OtpService.java` narrows verify transaction scope with unchanged CAS SQL; `AuthContext.jsx` clears local credentials/recovery state immediately.
- Config: `start-be.ps1`, `backend/.env.example`, `application-prod.properties`, `frontend/.env.example`: TTL allowlist, consistent existing cafe_app default, safe placeholders/sender guidance, unused frontend variable removed. No actual private `.env` edited/copied into Git; no TTL default changed.
- Tests: `AuthIntegrationTest.java`, `OtpIntegrationTest.java`, new `AuthConfigurationTest.java`, new `OtpPoolIntegrationTest.java`, `frontend/tests/auth.spec.js`.
- Tooling/API: auth Postman collection/environment, new `scripts/auth-postman.test.cjs`, `scripts/auth-postman-live.cjs`, `scripts/AuthFixturePassword.java`, `scripts/launchers.test.cjs`, `docs/api/dt2-2-auth-testing.md`.
- Documentation: database-contract, open-questions and both hardening/audit handoffs.

No DB schema/migration/test-schema file changed; no dependency or stack change; no auth_version/CAS/resend budget redesign; no account lock, Redis, blacklist or cleanup job. No reset/clean/history rewrite, and no other contributors' commits overwritten. Existing suite including other modules passed; that evidence covers regression scope, not a universal guarantee. Local commit only. No push/new PR/merge/deploy/comment/approve/resolve performed; no new remote CI run for this local revision.

## H. Decisions required from owner/leader

1. DB owner: approve authoritative schema/lineage and reconciliation/deployment plan, including staff_profiles and old delivery/queue ownership. Deployment remains **BLOCKED**.
2. Owner: approve login identifier/IP/global admission thresholds, trusted proxy chain/instance model, and24h retention/cleanup eligibility/batching. Concrete proposals/tradeoffs are in [open questions](../product/open-questions.md#dt2-2-security-hardening--owner-clarification-12112026); no implementation in this task.
3. Owner/deployment lead: approve production fail-fast for missing signing key and shared stable-key distribution/rotation. Current missing-key behavior remains a verified readiness risk.
4. Owner: decide whether logout needs per-session/server revocation or retain documented stateless client logout. Any storage/global auth_version policy needs explicit approval.

Real Gmail, deployed database and timing/capacity acceptance need their own authorized environment/evidence. No routine confirmation is required to finish the authorized local work; publication requires the owner's later authorization.

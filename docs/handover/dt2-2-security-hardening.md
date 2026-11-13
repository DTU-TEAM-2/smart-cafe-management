# DT2-2 / CF-03 — security hardening, 12/11/2026

Starting revision: `62ca3f1b9c0c1f648436806cab30314d20c8a786`, same local/remote `feature/cf-03-auth-recovery`, clean before work. Scope authorized by the owner's attached hardening request: fix collection integrity, audit DDL read-only, remove unused GET reset validation, clarify security controls, run regressions, local commit only. No push, merge, deploy, approve or discussion resolution authorized in this round.

## Requirements and decisions

- Read [Jira DT2-2](https://duymanhdnvn20092005.atlassian.net/browse/DT2-2) and [SRS v4.1](https://drive.google.com/file/d/1Mhaq4ttf8d-uiPbv2gSnfHK0AhCu4qpc) on 12/11. SRS section 6.1.1: username/password from USER, valid Admin → admin area, Staff → staff menu, invalid credentials remain at login with an error. Jira adds email recovery, completing reset and login with the new password. The Jira notes email/password details need QA; neither source supplied precise throttling/retention/server-logout-revocation policy in the reviewed content. This is a scoped source summary, not a full reapproval of historical requirements.
- Latest PR discussion content at start includes NV-DuyManh's verify/resend finding and the 62ca3f1 response. CAS SQL and existing race regressions are retained. Nam's supplemental pool finding led to a proven transaction-scope correction, detailed in [the config/pool audit](dt2-2-config-and-pool-audit.md). No historical fixed finding is assumed to recur.
- Owner's async clarification: no configured/verified gateway/WAF/proxy; no new application limiter, account lock, schema, shared cleanup job. Propose login 5 failed attempts/15 minutes, IP limits, OTP retention 24 hours for approval. Keep forgot limit/cooldown/CAS unchanged. Recorded with tradeoffs in [open questions](../product/open-questions.md#dt2-2-security-hardening--owner-clarification-12112026).

## Findings and status

| Finding | Severity | Reproduced / root cause | Result / files | Evidence | Status |
| --- | --- | --- | --- | --- | --- |
| Happy path could pass errors | P1 test integrity | Newman assertion probe: request 19 accepted 400, 20 accepted valid=false, 21/22 skipped required assertions on errors; empty/wrong identity tokens also accepted. Seven bad cases falsely passed | Collection 19–22 now unconditional; resetToken cleared first; valid=true/no-store; nonempty tokens and expected identity. 23 checks stateless acknowledgement. Fix recovery subject Admin-vs-Staff mismatch, variable-based account assertions and blank/private environment template. Add separate negative folder and sanitized loopback live runner | RED 7 integrity tests fail; GREEN integrity/live results below | FIXED, subject to recorded live acceptance |
| Migration lineage and DDL | P1 deployment | Read-only DDL/history differs from source; shared staff_profiles missing. IF NOT EXISTS does not reconcile old definitions; validation disabled. No migration run on shared DB | Detailed table/history/checksum audit in database-contract; correct old prose that implied V001 could simply be applied. Runtime/migrations untouched | Read-only metadata and aggregate counts outside Git | **BLOCKED** for deployment by DB owner/leader |
| Reset grant in GET URL | P2 | GET endpoint returned 200 for token query. Search found only legacy backend tests; frontend authApi, Postman and current app use POST. No SRS requirement or supported deployed external consumer evidence found | Remove GET handler, migrate old backend test consumer to POST; add GET → 405 regression. POST valid/no-store retained | RED actual 200 vs expected 405; GREEN targeted PASS; full suite PASS | FIXED for repository consumers; untracked external integrations require owner verification |
| Login brute force | P2 | No limiter found in AuthService/security filters or repo deployment configs. No gateway/WAF/Nginx/Docker/Vercel limiting configuration verified; owner explicitly confirms no such deployment protection established | No new limiter per owner clarification. Concrete proposal: 5 failed/identifier/15min plus trusted IP admission; disclose per-instance/shared-state limitations | Live audit observations below; existing login/RBAC regression retained | **NEEDS OWNER DECISION**, external protection **NOT CONFIGURED / NOT VERIFIED** |
| Logout/revocation contract | P2 | Backend stateless acknowledgement does not revoke bearer; frontend awaited network before local cleanup. Pending request probe left session token in storage after 5s | Keep stateless backend policy, explicitly document copied bearer remains usable until expiry/status/auth_version changes. Clear token/user/OTP/recovery state synchronously and detach acknowledgement request; existing /me effect ignores stale responses after token change | RED pending logout retains token; GREEN pending/abort/503 tests; backend test proves original bearer still authenticates after acknowledgement | Contract clarified; client cleanup **FIXED**; server revocation **NEEDS OWNER DECISION** if desired |
| Dummy growth/retention | P2 | Per-email limit exists, but distinct emails can create distinct rows; no IP/global limiter or cleanup scheduler found. Shared counts: 13 challenges, all expired, 8 used; limits 5, read-only dated observation | Preserve current controls; no shared deletion/job. Propose IP/global gateway quotas and 24h retention with active grants/registration/cooldown protection | Live isolated growth observation; aggregate metadata audit | **NEEDS OWNER DECISION**; no production protection/retention claim |
| Prior CAS/concurrency fixes | P1 historical | No recurrence identified; CAS SQL retained while fixing independent pool starvation | Keep code_hash+sends CAS, wrong-attempt REQUIRES_NEW, ACTIVE/password version guard, resend row lock and fixed expiry | Full suite includes OtpVerifyResendRaceIntegrationTest and retry/SMTP/concurrency suites | **ALREADY FIXED**, regression retained |

## Changes and purpose

| File | Change |
| --- | --- |
| `backend/.../auth/AuthController.java` | Remove GET verify-reset-token only; retain POST no-store and existing routes. |
| `backend/.../auth/OtpIntegrationTest.java` | New GET rejection regression and POST-only reset grant tests. |
| `backend/.../auth/AuthIntegrationTest.java` | Stateless logout test: same bearer still accesses /me; avoids a false server-revocation claim. |
| `frontend/src/auth/AuthContext.jsx` | Clear local credentials/recovery state before independent logout acknowledgement. No role or server authorization change. |
| `frontend/tests/auth.spec.js` | Pending, network abort and HTTP failure logout cases on desktop/mobile; credential cleanup and no page errors. |
| `docs/api/Smart_Cafe_DT2-2_Auth.postman_collection.json` | Original 1–23 kept in order; strict happy assertions, same Staff subject, separate negative/acceptance folder. |
| `docs/api/Smart_Cafe_Local.postman_environment.json` | Remove saved account/credential/token values; explicit isolated-test template. |
| `scripts/auth-postman.test.cjs` | Newman assertion-integrity tests using local HTTP stubs; clearly separate from runtime evidence. |
| `scripts/auth-postman-live.cjs`, `scripts/AuthFixturePassword.java` | Opt-in isolated fixture harness: real JAR/HTTP/MySQL and SMTP capture, in-memory secrets, expected FAIL injections, scoped cleanup and sanitized summary only. No runtime dependency/schema/service added. |
| `docs/api/dt2-2-auth-testing.md` | Safe collection/fixture variables/tool prerequisites and exact evidence boundaries. |
| `docs/architecture/database-contract.md`, `docs/product/open-questions.md` | Actual DDL/history audit and deployment hold; owner-required security proposals. |
| This handoff | Requirement provenance, changes, verdicts and remaining decisions. |

## Verification

- **Final Backend full** after pool/config changes: `$env:MAVEN_ARGS='-o'; powershell -NoProfile -ExecutionPolicy Bypass -File .\test-be.ps1` on MySQL 8.0.45 / `smart_cafe_auth_test`: **173 tests PASS, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** in one invocation (12/11 21:11:59 +07, 3m24s). Log `pr7-hardening-backend-final.log`. Earlier 161-test full run is preserved as historical evidence and is not substituted or added to this verdict.
- **Final Frontend**: `npm run lint` PASS, `npm run build` PASS, `npm run test:ui -- --workers=2`: **42/42 PASS**, including pending/abort/503 logout, both storage types cleared, late response and reload without restoring credentials. These are HTTP-mocked desktop/mobile UI tests, not authenticated backend/Gmail recovery E2E. Logs `pr7-hardening-frontend-*-final.log`.
- **RED** assertion integrity: 11 tests, 7 FAIL / 4 PASS against old scripts; preserved original false-PASS evidence. RED GET rejection: 1 failure/0 errors/skips. RED pending logout: 1 failure (token remained stored).
- **GREEN** GET targeted and pending logout targeted PASS before full runs. Full regressions above include POST validation/no-store, unchanged OTP/CAS and login/RBAC/Home.
- **Newman assertion integrity**: `node --test scripts/auth-postman.test.cjs scripts/launchers.test.cjs`: **24/24 PASS**, 0 skipped (21 assertion checks, 3 copied-launcher/stub cases). Real HTTP profile acceptance is recorded in the supplemental audit. The first live run failed on hardcoded admin/staff assertions and an authoring mistake placing the logout assertion on request 1; corrected rather than weakening checks. Second live run completed original 23 requests but its fixture preparation failed because CLI NOW() used local timezone; fixed CLI session to UTC to match app. Original FAIL logs retained. Neither failed harness run counts as backend functional PASS.
- **Governance**: registry PASS (17 skills/16 routes/169 inherited files), routing **9/9 PASS**; whitespace check on authored changes PASS. Final result is tied to source blobs/log hashes in an external verification manifest.

Logs remain outside Git under `C:\Users\khatr\review-guides\evidence\pr7-hardening-*`. Raw Newman environment/response artifacts are intentionally not exported. [Safe reproduction instructions](../api/dt2-2-auth-testing.md).

## Deployment and residual limitations

Deployment **BLOCKED** by shared DDL/history reconciliation. No production migration, data cleanup or login/admission limiter was added. No verified trusted proxy; client X-Forwarded-For is not used for a new limiter. Multipart upload limits are not evidence of a general JSON body cap. No verified external SMTP quota.

Stateless logout cannot invalidate an already copied bearer; expiry/current DB status/auth_version remain authorities. Reset changes version and old bearer/grant invalidation stays covered. SMTP/DB commit are not atomic; timing side-channel, broader load capacity and production infrastructure protection remain unverified. The bounded small-pool starvation reproduction/fix is covered in the supplemental audit; it is not universal capacity proof. No Gmail message sent; real loopback SMTP capture is a separate kind of evidence.

## Git / handoff

Same branch, base `62ca3f1b9c0c1f648436806cab30314d20c8a786`. Local commit containing this handoff is created only after checks; exact SHA supplied in final response and external review record. No push/new PR/merge/deploy/comment/approve/resolve in this task. Next: owner/DB leader decide blockers and proposals; author publishes only after fresh authorization, reviewer verifies changes.

Skills actually read/applied: diagnosing-bugs (RED probes), cafe-spring-api (POST/API/transaction constraints), cafe-quality (full regression and evidence classes), cafe-database (read-only DDL/history), cafe-review (final focused review), vercel-react-best-practices (logout state/network separation), google-drive (read supplied SRS). Repo skill paths `.agents/skills/<folder>/SKILL.md` (React folder `react-best-practices`); Drive skill under installed google-drive plugin. Rules/owner authorization override historical skill guidance; no schema engine choice or extra publication authority inferred.

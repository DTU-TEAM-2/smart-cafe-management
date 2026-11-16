# DT2-2 — verified handoff integration, 2026-11-13

PR [#7](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/7), `feature/cf-03-auth-recovery` → `develop`. This supersedes earlier statements that a full DDL artifact is unavailable. It does **not** approve shared rollout, legacy conversion or global version allocation.

## Artifact and authority

ZIP `SmartCafe-DDL-handoff-20261113.zip` SHA-256: `311dda5ff0ff2a19a739fe75ce235c553ad5f69401a3b7036faf7a8cde6bcb93`. Extraction outside Git checked traversal, absolute paths, symlinks and member size; all ten original SHA256SUMS entries matched. Archive inventory:

- `db/smart-cafe/V20261113_01__smart_cafe_schema.sql`
- `erd-2026-11-11.jpg`
- `MIGRATION-DECISION.md`, `manifest.json`, `README.md`, `legacy-column-gaps.md`, `SHA256SUMS.txt`
- `evidence/prior-rehearsal-final.log`, `evidence/prior-results.json`, `evidence/version-20261113.1-results.json`
- `tools/FlywayHandoffProbe.java`

The SQL is the DDL-only artifact; the ZIP contains **no separately named second DDL snapshot**. No business DML, credentials, fixed definer account or production rows were found. Original handoff text/checksum inventory is preserved under [docs/database/handoff/20261113](../database/handoff/20261113/); its checksum paths refer to the complete original ZIP, not this partial provenance directory. External raw evidence stays outside Git.

The manifest identifies read-only SHOW CREATE from the contributor's Navicat `locall / smart_cafe`, Auth revision `0076bb2`, develop `79896a9`. This supplied provenance does not identify the workspace's different server as the same instance. The decision supports implementation/rehearsal. Global version allocation, accountable DBA, target identity and rollout/restore authority remain owner decisions.

| Value | Verified result |
| --- | --- |
| Script | `V20261113_01__smart_cafe_schema.sql`, copied byte-for-byte |
| SQL SHA-256 | `84cb9f2e99073ea73b117e828dc7f144928052a8f8345cde6f903b82280e4b5e` |
| Logical / baseline version | `20261113.1` |
| Native SQL version spelling | `20261113.01`, equivalent under native Flyway version comparison |
| Flyway CRC32 | `-22922275`, calculated independently and checked against native history |
| Baseline description | `Smart Cafe verified schema 2026-11-13` |
| Runtime location | `classpath:db/smart-cafe` |

## DDL comparison

All twenty table CREATE definitions were compared with the historical fixture: nineteen match; `news_posts` adds `audience_staff`, `audience_customer` (NOT NULL, default 0) and `ck_news_audience_flags`. No other table type/default/nullability/PK/FK/index/CHECK difference was inferred. Dependency order avoids disabling FK checks. Both guest `customer_user_id` columns remain nullable; staff address/salary and session opener remain required. No invented default/backfill was introduced.

All four views change historical `SQL SECURITY INVOKER` to artifact `SQL SECURITY DEFINER`; their query bodies remain the same. The migration account becomes definer. Account ownership/lifetime, underlying SELECT privileges, application grants and restore portability need DBA review. A privileged local test is not least-privilege production evidence. Definer identity is excluded from structural hashes, while SQL SECURITY and full query bodies remain checked.

The ERD was inspected: same twenty-table model, guest NULL annotations and internal session opener. News audience extends the image and still needs recorded owner acceptance. The legacy snapshot remains a separate 13-table/zero-view source; the five-table Home candidate remains an unapproved legacy expansion, not this twenty-table target.

## Lineage compatibility and code

| Policy | Location | Accepted history / scope |
| --- | --- | --- |
| Default `auth-local` | `classpath:db/migration` | Existing Auth V001/V002 SQL, original bytes/checksums and existing structural checks. No new baseline accepted. |
| Explicit profile `verified-db` | `classpath:db/smart-cafe` | Inventoried SQL or exact manual baseline, then reviewed inventoried SQL upgrades with versioned DDL contracts. |
| Legacy V001–V008 (+ located V009/V010) | Existing legacy chain, outside new runtime | Native historical rehearsal only. Never baseline as the twenty-table target. Mapping, worker ownership and rollout remain blocked. |

`VerifiedSchemaContract` pins SQL SHA-256/CRC, validates ordered applied history and all 24 canonical JDBC SHOW CREATE hashes for that version. Only AUTO_INCREMENT counters, environment-specific definer and selected catalog qualifier are normalized. Column/type/null/default, PK/FK/index/CHECK, collation, generated expression, SQL SECURITY and view definitions stay in the hash. Unexpected visible tables/views/routines/triggers/events are rejected. Metadata visibility depends on grants; DBA inventory/permission checks are still required, especially for objects hidden from a restricted app account. Engine-format changes fail closed and require reviewed comparison; never regenerate hashes automatically at startup.

`DatabaseMigrationConfiguration` verifies location, ordering and selected catalog, performs read-only preflight, rejects unknown pending SQL, invokes native Flyway with validation forced ON and automatic baseline forced OFF, then checks the resulting schema. Future migrations extend both inventory and post-migration DDL contracts. A test-only future DDL script proves upgrade and repeat no-op; it is outside the runtime JAR and has no production version allocation.

## Isolated lifecycle commands

After Maven build, form a classpath from `backend/target/classes` and runtime dependency JARs. Supply private TEST_DB_URL/USERNAME/PASSWORD through process environment. The tool requires an explicit `_test` catalog and rejects Flyway catalog overrides:

```text
java -cp <runtime-classpath> vn.codegym.smartcafe.database.DatabaseRehearsal provision
java -cp <runtime-classpath> vn.codegym.smartcafe.database.DatabaseRehearsal baseline
```

`provision` checks an actually empty schema, including tables/views/history/stored objects, then native migrate creates the target. `baseline` requires matching full DDL, UTC, no history and the exact marker; it records metadata and migrate executes zero. Neither operation uses repair, clean, a second history table or backfill.

Before baseline of existing data: freeze writers/DDL, take a secure backup, restore into a separate `_test`, compare all row/DDL digests, recheck the source and keep the freeze through baseline. The tool cannot infer backup authority or prevent external concurrent DDL. MySQL DDL has no transactional rollback. Restore/grant/definer rehearsal is required; app rollback does not authorize dropping tables.

Launch the actual app with private DB_* and `--spring.profiles.active=verified-db`, Flyway ON. Do not switch Auth-local/legacy environments to this profile without a separately approved transition. For a real approved target the DBA supplies a controlled native Flyway command using these exact settings, reviewed DDL comparison, backup and writer window. The rehearsal tool intentionally refuses `smart_cafe`.

## Evidence and limits

Fresh results and final revision are recorded below after verification and in the private local review. Earlier 186/182/112/173 counts are historical, not this revision's full-suite evidence. Files prefixed `pr7-full-db-` live under `C:\Users\khatr\review-guides\evidence\`. Credentials, OTPs, reset grants and raw Auth exports are not committed.

Tests with Flyway intentionally OFF remain distinct from native lifecycle and real Tomcat startup with Flyway ON. Playwright's HTTP fixtures prove UI behavior, not authenticated Backend end-to-end. Guest feedback/News audience have SQL tests; this branch has no HTTP controllers for them, so HTTP is NOT VERIFIED. QR identifies a public table; automatic guest-session creation/token issuance remains blocked by QA-02 and required session-opener ownership.

## Review and rollout gates

No GitHub write is authorized. New-SHA CI, two fresh independent approvals and reviewer confirmation of discussions remain outstanding. Workflow changes need CODEOWNER NV-DuyManh or haitan2408 within those approvals. If the DDL belongs in a separate Database PR, publish one canonical artifact there first, then sync PR #7; never create a competing migration/version.

Deployment is BLOCKED pending target identity, DDL/News approval, global version allocation, named DBA, least-privilege grants/definer check, backup/restore/writer window and explicit rollout permission. Legacy conversion/Auth-local transition need separate plans where relevant. Gmail, deployed DB acceptance, gateway limits, timing side-channel, retention and server-side logout revocation are not approved by local tests.


## Current verification matrix

Application/test sources are the final post-merge source; documentation changes after these runs do not alter executable behavior. Local engine is MySQL 8.0.45, JDK 21, Spring Boot 4.1.2. The contributor reports MySQL 8.0.44; that exact server and restricted production grants were not tested here.

| Check | Result / count | Evidence (private directory above) |
| --- | --- | --- |
| Full Backend `MAVEN_ARGS=-o; ./test-be.ps1` | PASS 216, 0 failures/errors/skipped, ended 14:21:21 +07 | `pr7-full-db-backend-final-rerun.log`, `pr7-full-db-backend-counts.json` |
| Auth/OTP | 112 included in the full216; CAS, concurrency, lifecycle and RBAC regressions unchanged | Maven XML / full log |
| Database | 37 included: 24 new verified-contract cases, 8 existing Auth-lineage safety, 2 temporal, 2 legacy HTTP, 1 real UTF-8 HTTP | Maven XML / full log |
| Other Backend | Customer Menu9, QR5, Home9, Income3, Menu23, Sales18, included in216 | Maven XML / full log |
| Frontend | `npm ci`, lint, build PASS; full Playwright70 PASS | `pr7-full-db-frontend-*.log`, `pr7-full-db-playwright.log` |
| Governance | Registry17/routes16/inherited169; Skills9 + Git Flow8 = 17 tests PASS, feature → develop validator PASS | `pr7-full-db-registry-final.log`, `pr7-full-db-governance.log` |
| Script contracts | Auth Postman assertion-integrity + launchers24 tests PASS; these use HTTP fixtures and are not live authentication evidence | `pr7-full-db-script-tests.log` |
| Workflow guards | Both YAMLs/all embedded shell syntax PASS; exit/503, skip/missing/malformed JSON guards, real pass/fail report rendering PASS, QR upload guard PASS | `pr7-full-db-workflow.log`, `pr7-full-db-workflow-qr.log` |
| Historical native rehearsal | PASS: eight original checksums, five-table candidate once, repeat0, row/history preservation and checksum rejection | `pr7-full-db-legacy-rehearsal.log` |
| Runtime packaging / source safety | Pinned SQL SHA and immutable Auth blobs preserved; test-only migration absent from JAR; no forbidden files/recognized secret patterns; whitespace checks PASS | `pr7-full-db-secret-scan.json`, local review |
| Native provision / baseline + real startup | PASS: fresh migrate1/repeat0; both existing/test-restored baseline migrate0/repeat0; untracked real app startup blocked; both tracked paths health200 with Flyway ON | `pr7-full-db-http-utc-final.log`, native logs and server logs |
| Backup / data preservation | PASS: all20 tables contain synthetic rows, 4 views; pre-baseline logical restore and post-baseline DDL/row digests match. Guest NULL and four News audience combinations/CHECK verified | `pr7-full-db-http-results.json`, live log |
| Real application HTTP | PASS23 requests: three-role login, me, UTF-8 forbidden, Home + exact UTC under Vietnam JVM, public and protected Menu, Sales/Bill/Payment/Income, missing CSRF rejected, repeated payment creates one row, paid reset204 | `pr7-full-db-http-results.json` |
| Live Newman Menu / QR | PASS16 requests/31 assertions and9/16. Separate fault copies produce exactly1 FAIL each (32/17 assertions), rendered honestly. NO_BACKSLASH_ESCAPES is session-only | `pr7-full-db-menu-newman.json`, `pr7-full-db-qr-newman.json` |
| Live Newman Auth recovery | PASS39 requests/57 assertions, Flyway ON + verified-db, loopback SMTP, four separate fault scenarios correctly FAIL. Additional HTTP cooldown429, concurrent resend200/429 with sends2, verify200/reuse400 | `pr7-full-db-auth-newman.log` |
| New-SHA GitHub Actions | NOT RUN: changes are local and push is not authorized | No CI acceptance claimed |

Initial failing verification is retained: the first contract implementation needed native version equivalence and baseline-script semantics, plus JDBC hashes instead of Windows CLI line-ending output. A subsequent full216 run had one setup error because MySQL requires dropping/readding the FK before the nullability drift mutation; after correcting that setup (retaining the actual drift assertion), the new full216 run passed. A live probe initially omitted Postman environment variables; its failures were not counted as application PASS. A later paid-reset probe seeded one DATETIME via a non-UTC connection; the `ck_sessions_end` violation was reproduced with a seven-hour future fixture and reset passed with UTC data. All synthetic seed connections now explicitly set UTC; business code was not changed to accommodate bad fixture data.

Skills read/applied: `cafe-database`, `cafe-spring-api`, `cafe-quality`, `cafe-requirements`, `cafe-review` under `.agents/skills/<identifier>/SKILL.md`; UI merge checked against `vercel-react-best-practices` (`.agents/skills/react-best-practices/SKILL.md`) and `fixing-accessibility`. Phases used only their relevant selection. Host `ponytail` at `C:/Users/khatr/.codex/skills/ponytail/SKILL.md` kept existing dependencies/business logic, with explicit safety boundaries.


### Git integration and ownership

Merge commit `3475fdb` incorporates develop `79896a993e9a0f620734201efa2f05ed60090b95`; corrective commit `06aa1b9` completes the three conflict resolutions without rewriting the merge. Files: `.github/workflows/dt2-4-postman.yml`, `docs/product/open-questions.md`, `frontend/src/main.jsx`. AuthProvider and dev-only lazy Menu preview coexist; both question sections remain; both Menu/QR Newman jobs retain real execution, strict reports and failure evidence. The combined SecurityConfig retains the public GET QR matcher, existing Menu security chain, RBAC, bearer, CSRF and UTF-8 response handler.

Final fetch still reports develop `79896a9` and feature remote `0076bb2`. Read-only GitHub metadata confirms PR #7 already targets develop; no retarget/write is necessary or authorized here. Source/index checks preserve `3698976`, `24f0b0c`, original Auth migration bytes and identical AuthRepository/OtpService blobs from24f0b0c. No new policy, Auth refactor, limiter or schema conversion was added.

The final commit IDs, diff counts/full changed-file inventory and clean tree are recorded outside Git in the local review to avoid a self-referential document hash. All local tests apply to the final executable source; later commits only record evidence. READY FOR REVIEW locally; READY FOR MERGE remains pending authorized publication/current CI/two independent approvals and acceptance of the explicit DB ownership scope; DEPLOYMENT remains BLOCKED.

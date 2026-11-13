# DT2-2 — Database integration handoff, 13/11/2026

Scope: [PR #7](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/7), branch `feature/cf-03-auth-recovery`, starting revision `94deb6d01c07d7c746c44f8fa1a3837946daa183`. Local implementation and testing only. **READY FOR REVIEW; DEPLOYMENT BLOCKED.** No shared database mutation, push, review reply, resolve, approve, merge or deploy was performed in this task.

## A. Root cause

The current database was created by a legacy cross-module migration chain, while this repository bundles a different Auth-only V001/V002 and tests primarily against a historical 20-table fixture. `CREATE TABLE IF NOT EXISTS` cannot reconcile existing definitions. Automatic baseline and disabled validation could accept an unrelated history or mark an untracked populated schema as managed. Green tests on the historical fixture did not prove shared-schema compatibility.

The shared database is also incomplete against the owner-designated 11/11 ERD. Home account unconditionally joins missing `staff_profiles` and `media_assets`; notifications require missing order tables. These are genuine SQL dependencies, including for a valid Auth bearer. Removing JOINs or fabricating profiles would hide the integration defect.

A separate confirmed code defect was found while testing a real notification: `Timestamp.toLocalDateTime().atOffset(UTC)` changed `2030-04-05T06:07:08Z` to `13:07:08Z` under a Vietnam JVM. The mapper now uses `toInstant()`; the regression verifies the exact instant on MySQL while setting/restoring the JVM zone.

## B. Database compatibility

Sources are kept distinct: A = [ERD 11/11](evidence/system-database-reference-2026-11-11.jpg); B = fresh read-only `smart_cafe` DDL/history; C = current code and historical test fixture. The owner explicitly confirmed that complete approved target DDL and DBA authority are not yet identified. Neither B nor C is promoted to the target.

| Contract | Actual source B / result | Classification |
| --- | --- | --- |
| Inventory | MySQL 8.0.45; 13 base tables including history, 0 views/triggers/events; 9 of 20 ERD tables present | MIGRATION REQUIRED / OWNER DECISION REQUIRED |
| Missing tables | staff_profiles, media_assets, menu_categories, session_clients, order_batches, order_items, payments, feedback, feedback_images, news_posts, news_images | MIGRATION REQUIRED |
| Auth common columns | Login/OTP/reset/CAS/RBAC SQL works on the actual-DDL copy | COMPATIBLE for current tested SQL; not complete DDL equivalence |
| Home account/feed | Authenticated requests return 503 on the 13-table copy; five-table candidate restores both paths | MIGRATION REQUIRED; candidate not approved |
| OTP/limits time | TIMESTAMP(6) versus historical DATETIME(6); UTC preserves microseconds, +07 session changes timestamp representation; 2040 timestamp rejected | OWNER DECISION REQUIRED for canonical type/range/conversion |
| Opaque key/hash collation | Shared utf8mb4_unicode_ci versus historical ascii_bin | OWNER DECISION REQUIRED; no reproduced generated-key collision |
| Integrity / role widths | Shared role/status 32/32; different defaults/indexes/FK policies; several fixture checks absent | OWNER DECISION REQUIRED; do not add constraints without existing-data audit |
| Extra delivery state | delivery_attempts, next_delivery_at, otp_ciphertext, last_smtp_status retained | OWNER DECISION REQUIRED; legacy source has a queue consumer, deployed worker NOT VERIFIED |
| Header timestamp mapper | Seven-hour shift reproduced, then corrected | CODE FIX REQUIRED → FIXED |

The [full matrix](../database/proposals/2026-11-13-dt2-2/compatibility-matrix.md) lists relevant columns, nullability/defaults/types/collations and dependencies. The [DDL-only snapshot](../../backend/src/test/resources/db/current-shared-2026-11-13.sql) preserves actual FK/index/check definitions without user records, definers or AUTO_INCREMENT counters. Detailed metadata evidence remains outside Git.

The five-table candidate adds media_assets, staff_profiles, session_clients, order_batches and order_items. It uses explicitly historical definitions checked against ERD fields; it does not invent unknown DDL or backfill a fake address/salary. Staff branch dev DDL at 55fec92 differed for media; final fetch at 0b6a74b shows those definitions aligned and backfill removed. This is explicitly dev/test SQL, still provenance rather than shared deployment approval. Six ERD tables remain absent and existing Menu/Sales/session/bill/service shapes and four historical views remain unreconciled. Auth/Home acceptance on this candidate is not acceptance of the whole application.

## C. Flyway

All eight applied source scripts were located in [the legacy repository at db0e80b](https://github.com/DTU-TEAM-2/smart-cafe-management-legacy-20261106/tree/db0e80bd9113ffe4d9bead60989f6e613999e6f2/backend/src/main/resources/db/migration). Every source CRC32 matches shared metadata. [The manifest](../database/proposals/2026-11-13-dt2-2/legacy-lineage-provenance.json) records exact source paths, blobs and checksums. Actual installation order is `001,005,006,007,008,002,003,004`, all successful. V009/V010 already exist in that source; no version is assumed available.

Runtime now disables automatic baseline, enables validation and runs read-only preflight before native Flyway migration. It refuses alternate history tables, unknown/changed applied scripts, missing model tables/required Auth/Home columns/PKs and non-UTC sessions. A test with old baseline/validation overrides confirms the strategy still refuses unsafe startup. A known pending V002 remains allowed with strict validation. The shared legacy lineage intentionally remains blocked by the current Auth-local chain: locating its source does not authorize silently replacing runtime migrations.

`preflight-be.ps1` uses JDBC metadata/history SELECTs without starting Spring/Flyway. Exit 2 on shared DB reports 11 missing tables and all eight lineage mismatches. This is an observed blocker report, **not a compatibility PASS**. Its checks are limited; it does not certify every FK/default/check/type or replace approval of canonical DDL.

## D. Files changed

| Files / method | Purpose |
| --- | --- |
| `DatabasePreflight.inspect`, `checksum`, CLI `main`; `preflight-be.ps1` | Read-only contract/history diagnostics, no database fallback in the CLI, sanitized failure output |
| `DatabaseMigrationConfiguration.verifiedDatabaseMigration`; `application.properties` | Preflight before Flyway, strict validation and no automatic baseline; no global Flyway disable |
| `HeaderService.notifications` timestamp mapper | Preserve the JDBC instant under a non-UTC JVM |
| `TestSchema`; current DDL/history metadata resources | Guarded, unique generated *_test schemas; synthetic fixtures and cleanup; no shared records copied |
| `DatabaseMigrationSafetyTest`, `CurrentSharedDdlHttpTest`, `DatabaseTemporalCompatibilityTest` | Nine new tests for startup safety, actual missing dependencies, candidate recovery/Home and time semantics |
| `DatabaseMigrationRehearsal` | Explicit native Flyway test tool: original source checksums, candidate application, repeat no-op, sentinel/history preservation and drift rejection |
| Proposal SQL/README/matrix/provenance manifest | Five-table proposal outside runtime locations, migration lineage and rollout/restore decisions |
| Postman collection and live runner; API testing guide | Preserve original 37 requests/53 assertions; append strict authenticated Home account/feed checks after new-password login |
| Database contract, open questions, this handoff | Record verified differences, owner decisions and reproducible evidence |

No AuthRepository/OtpService/TokenService, migration V001/V002, frontend, SMTP configuration, dependency or business-policy change. CAS, real/dummy sends budget, cooldown/expiry, grant single use, ACTIVE checks, auth_version, pool fix, POST reset-token verification and client logout behavior remain intact.

Configuration review: dev/prod inherit datasource defaults including the shared URL fallback; `start-be.ps1` also retains that fallback. The new migration strategy blocks known incompatible startup before Flyway; this task never started the shared backend. SQL init remains `never`; tests explicitly disable Flyway except isolated migration tests. UTC connection-init and pool timeout stay unchanged. Signing-key production policy and least-privilege deployment grants remain owner decisions; CI uses an isolated schema. No private configuration value was copied into Git.

## E. Migration plan

The [proposal directory](../database/proposals/2026-11-13-dt2-2/README.md) contains the candidate SQL, source manifest, preflight instructions and explicit rehearsal entry point. Nothing there is in runtime migration locations. The candidate is not assigned a global V-number or authorized for shared execution.

1. Name the DBA/approver; provide the complete approved target DDL/ref, view contracts, data mappings and grants. Coordinate all module owners and any legacy mail worker.
2. Preserve V001–V008 bytes/history. Review V009/V010 and active branches, reserve a global next version and choose one team-owned runtime chain. Do not repair checksums, baseline populated tables or create a second production history table.
3. Approve additive tables separately from existing-table conversions/backfills. Review candidate fields/constraints and every remaining ERD difference before placing approved migrations in runtime locations. Do not use default staff identities or destructive cleanup.
4. Rehearse the approved chain on isolated MySQL with synthetic existing user/OTP data. Require strict validation, one application, repeat-run no-op and authenticated Auth/Home plus affected Menu/Sales tests. The current rehearsal proves the candidate mechanism only.
5. DBA prepares secure backup/restore, maintenance window and writer freeze, reruns read-only DDL/history comparison, obtains execution approval and then applies only the reviewed global migration. This task does not authorize that step.
6. Verify all affected APIs and preserved data/history after rollout. MySQL DDL has no transactional undo. On failure, stop writers/rollout; roll back application/config where compatible, retain new tables/data, and let DBA decide forward fix versus secured restore. Never automatically DROP tables or delete/edit history. A restore must account for intervening writes.

## F. Tests and evidence

Acceptance logs are local under `C:\Users\khatr\review-guides\evidence\`; raw reports/private runtime dependencies stay outside Git. Counts below are separate runs, not summed to manufacture a full-suite result.

| Run / schema | Observed result | Evidence file |
| --- | --- | --- |
| New full `test-be.ps1`, after final Java changes | **182 tests PASS, 0 failures/errors/skipped**, Maven verify BUILD SUCCESS, finished 13/11 09:28:11 +07. Historical fixture coverage plus nine new tests on separately generated schemas | `pr7-db-backend-full-final.log`, `pr7-db-full-surefire-final/` |
| Auth suite, actual 13-table DDL copy | **112 run: 110 PASS, 2 FAIL, 0 errors/skipped**. Both failures are authenticated Home expectations 200 versus actual 503. This RED run predates the independent timestamp mapper correction; the missing-table failure occurs before that mapper | `pr7-db-current-ddl-auth-suite.log` |
| Auth suite, actual DDL + five-table unapproved candidate | **112 PASS, 0 failures/errors/skipped**. Includes BCrypt/Admin/Staff/RBAC, OTP good/wrong/expiry/attempts/dummy/resend, real/dummy retry budget, verify/resend both orders, concurrent verify/reset, stale wrong attempt, INACTIVE race, auth_version/Home and pool=2 tests | `pr7-db-candidate-ddl-auth-suite-final.log` |
| Preflight/startup regression RED → GREEN | Old native auto-config: 2 tests, 2 failures, no errors/skips on owned copies; it allowed unrelated/untracked startup. New guard: all five safety cases PASS, included in final full suite | `pr7-db-migration-safety-red-faithful.log`, final full suite |
| Home timestamp regression RED → GREEN | Old mapper: 2 tests, 1 failure, no errors/skips; seven-hour shift. Fixed mapper: targeted 9/9 PASS, then full 182/182 PASS | `pr7-db-home-notice-time-red.log`, `pr7-db-home-notice-time-green.log` |
| Native Flyway, actual DDL/history copy + five-table candidate | **PASS**: original 8 source checksums validated; only candidate applied once; second migrate executed 0; existing synthetic user/OTP and all 8 history rows unchanged; deliberate source checksum drift rejected without history changes | `pr7-db-native-migration-rehearsal-final.log` |
| Newman actual 13-table DDL, dev | **FAIL**, exit 1. Original 37 requests/53 assertions pass; appended two Home requests fail all 4 assertions. 39 requests/57 assertions total, 4 unexpected functional assertion failures | `pr7-db-newman-current-ddl-final.log` |
| Newman candidate, dev | **PASS**, real loopback HTTP/MySQL/SMTP; 39 functional requests/57 assertions, 0 functional failures; four separate fault injections correctly report FAIL | `pr7-db-newman-candidate-dev-final.log` |
| Newman candidate, prod profile | **PASS**, same 39/57 and four expected fault failures; this is a test process with explicit private key/isolated DB/SMTP capture, not production acceptance | `pr7-db-newman-candidate-prod-final.log` |
| Shared read-only CLI preflight | **BLOCKED**, exit 2: 11 absent model tables + 8 applied-script lineage mismatches | `pr7-db-preflight-shared-final.log` |
| Shared metadata recheck after tests | **PASS for unchanged metadata only**: all 13 normalized SHOW CREATE definitions and the 8 ordered history entries still match the captured snapshot; 0 views/triggers/events. No business rows queried | `pr7-db-shared-final-verification.json` |
| Governance | Registry/inherited inventory PASS; 9 routing tests PASS | `pr7-db-governance-check-final.log`, `pr7-db-governance-tests-final.log` |
| Tool assertions / launcher stubs | 21 Postman assertion-integrity tests PASS; 3 launcher-stub tests PASS. These are not authenticated integration evidence | `pr7-db-postman-assertions-final.log`, `pr7-db-tooling-tests-final.log` |
| Approved full target DDL / production upgrade | **NOT RUN / BLOCKED**: target DDL/version/execution approval unavailable | Owner decision required |

The opt-in Newman runner verifies recovery → captured OTP → reset → new-password login → Home account/notifications against a real test backend. It scopes cleanup to generated fixtures. OTP/password/bearers stay in memory; no raw environment/response export. The four deliberate fault runs remain FAIL and are reported separately from functional success.

Setup failures are retained: an initial assertion-tool invocation lacked `NEWMAN_MODULE` (3 launcher checks passed, assertion module did not load); the corrected explicit module-path run supplies the 21 PASS result. Early DDL loader/history parser and manual validation experiments are not acceptance evidence. The final native rehearsal uses Flyway migrate with validation enabled, which permits a legitimate pending migration; it does not call standalone validation as a way to reject every pending migration. Failed probes were not relabeled PASS.

Frontend and browser tests were not rerun because frontend is unchanged. Earlier results/CI on 94deb6d are historical evidence only. No CI exists for this new local revision until a separately authorized push. Real Gmail, deployed database, timing side-channel, external worker deployment, production proxy rate limits and restore rehearsal are **NOT VERIFIED**.

## G. Remaining blockers / exact owner request

Request the artifacts and decisions in [open questions](../product/open-questions.md#dt2-2-database-reconciliation--owner-inputs-required-13112026):

- Named DBA/leader with authority to approve schema, migration execution and restore; immutable approved full target DDL/ref and all view/constraint/default/collation/time/grant contracts.
- Review of five proposed dependency tables, especially media URL/nullability and Staff required fields/checks/existing-user handling; approved definitions for the remaining six missing tables and legacy Menu/Sales conversions.
- One global migration manifest/runtime location, treatment of conflicting Auth-local V001/V002, review of existing V009/V010, and a reserved next version. Original V001–V008 history/source must remain immutable.
- Ownership/deployment status of old queued delivery workers, canonical OTP temporal/equality/integrity policy and data-preserving conversion plan.
- Maintenance/writer-freeze/backup/restore authority and authenticated acceptance on that approved target, including other affected modules.

No new rate limiter, retention cleanup, logout revocation, signing-key policy, schema change on shared DB or automatic provisioning was introduced. Those existing policy proposals remain NEEDS OWNER DECISION.

## H. Git and review

Work remains on `feature/cf-03-auth-recovery`; starting local/remote revision is 94deb6d. The commit containing this handoff is a new **local-only** revision; its exact SHA, changed-file inventory, log hashes and self-review record are recorded outside Git under `C:\Users\khatr\review-guides\reviews\`. No history rewrite or push is authorized. No personal data, credential, `.env`, legacy runtime migration source or local evidence directory is staged.

Skills read/applied: `cafe-database` (`.agents/skills/cafe-database/SKILL.md`) for source reconciliation and migration safety; `cafe-spring-api` for the focused mapper/startup corrections; `cafe-requirements` for held owner decisions; `cafe-quality` and `cafe-review` for separate per-schema evidence and self-review; `ponytail` (`C:\Users\khatr\.codex\skills\ponytail\SKILL.md`) for a focused change with existing dependencies. Skills do not authorize publication or shared writes. Self-review is not an independent teammate approval.

## I. Readiness

**READY FOR REVIEW.** Independent implementation is fixed and regression-tested; candidate DDL, source provenance, preflight and migration rehearsal are prepared. **Not READY FOR MERGE acceptance or deployment:** the full approved shared target and global migration decision are missing, and review/CI on this local revision have not occurred. **DEPLOYMENT BLOCKED** until those specific owner/DBA inputs and target-environment checks are completed.

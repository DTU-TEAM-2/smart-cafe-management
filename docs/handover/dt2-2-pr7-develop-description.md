# Proposed PR #7 description — local draft

Prepared for `feature/cf-03-auth-recovery` → `develop` under [Git Flow](../../git-flow.md). This file is a proposed replacement for the stale PR body, not evidence that GitHub has been edited. PR #7 was read during this task: base remains main, remote HEAD 94deb6d. Changing base and publishing commits require separate owner authorization.

## Problem and resulting behavior

DT2-2 implements BCrypt login, signed bearer integration, Admin/Staff authorization and email password recovery. Follow-up fixes preserve CAS for OTP verification/wrong attempts, consistent real/dummy resend budget and fixed expiry, single-use reset grants, ACTIVE/auth_version checks, bounded transactions around SMTP, POST-only reset-token verification and client logout cleanup on delayed/failed requests. Server-side per-token logout revocation is not implemented.

Database integration found a legacy migration chain and missing model tables. Startup now checks database metadata/history before Flyway, disables automatic baseline and enforces validation. Home notification timestamps preserve the actual UTC instant under a Vietnam JVM. No proposed shared DDL was added to runtime migration locations.

## Synchronization and scope

The feature retains commit `2644acf116420e79c9c1dbf3086daf8fec959743` and merges `develop@5cc4132` without rebase/reset/force-push. Two conflicts were resolved: preserve tested fixed timestamps plus develop's explanation in SalesMySqlTest, and retain both OTP ADR-006 and Git Flow ADR-012. No main application or frontend source changes are introduced by synchronization. Git Flow/workflow/policy files match develop.

## Database evidence and limits

| Source | Status |
| --- | --- |
| Shared DDL snapshot | 13 base tables including Flyway history, 0 views; only 9 of 20 ERD tables present. Isolated copy authenticates, but account/feed fail with 503. No shared mutation. |
| Five-table candidate | Adds media_assets, staff_profiles, session_clients, order_batches, order_items to an isolated copy. It is a proposal derived from historical definitions checked against ERD fields; not approved deployment DDL. Six model tables and wider legacy conversions remain unresolved. |
| Historical 20-table fixture | Used by the existing full regression suite; explicitly not an official deployment migration or complete target approval. |
| Approved target | NOT AVAILABLE / BLOCKED pending complete DDL/ref, DBA authority and unified migration decision. |

V001–V008 source checksums match the actual legacy history; V009/V010 already exist in that source. Preserve immutable source/history and reserve a global version only with the database owner. No repair, baseline, shared migrate or guessed backfill is authorized.

## Validation

The latest post-synchronization results and exact local revision are recorded in the [database integration handoff](dt2-2-database-integration.md#develop-synchronization--13112026). They distinguish Flyway-enabled startup tests/native rehearsal from API test runners that intentionally disable Flyway.

Earlier evidence on 2644acf: full Backend 182/182, candidate Auth 112/112, candidate Newman dev/prod each 39 requests/57 assertions and four separately expected fault failures. These remain revision-specific evidence, not substitute counts for the new full run. SMTP loopback and a prod-profile test process do not prove Gmail delivery or production acceptance. Frontend source is unchanged by the develop merge.

## Review and blockers

READY FOR REVIEW, DEPLOYMENT BLOCKED. Before merge into develop: publish only after owner permission; retarget this same PR to develop; require current Governance, PR title, Backend MySQL, Frontend UI and Git Flow checks; obtain two fresh independent approvals and have discussion authors confirm/resolve findings. Only NV-DuyManh or haitan2408 may merge. Do not self-resolve, approve, merge, tag or deploy.

DBA/owner must provide approved full target DDL/view/constraint/default/collation/time contracts, global lineage/runtime location/version reservation, legacy worker ownership, conversion and backup/restore plan, and acceptance on that target. Reviewers must decide whether the guarded, explicitly deployment-blocked scope satisfies DT2-2 integration acceptance; green fixture tests alone do not close schema blockers. Login admission limits, OTP retention, real Gmail, timing side-channel and production verification remain unaccepted as documented in [open questions](../product/open-questions.md).

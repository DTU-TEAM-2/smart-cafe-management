# Proposed PR #7 description — local draft

DT2-2 login/recovery and verified database integration, `feature/cf-03-auth-recovery` → `develop`. Read-only GitHub check confirms the same PR already targets `develop`; remote HEAD remains `0076bb2`. This local draft has not been posted.

## Problem and resulting behavior

Auth implements BCrypt login, signed bearer/Admin/Staff authorization and email recovery. Existing fixes retain CAS for verification/wrong attempts, equal real/dummy resend budget, fixed expiry, single-use reset grants, ACTIVE/auth_version validation, bounded SMTP transactions, POST-only reset-token verification, UTF-8 problem responses and prompt client logout cleanup. Per-token logout revocation remains unimplemented.

The verified DB handoff now supplies the twenty-table/four-view DDL, including News audience. Opt-in `verified-db` uses only `classpath:db/smart-cafe`; default Auth-local V001/V002 remains byte-immutable and available for existing environments. Full DDL fingerprints and inventoried history block drift, foreign history, arbitrary baseline markers and unreviewed pending SQL. Separate `_test` provision/manual-baseline commands enforce empty/full-schema checks; app startup never baselines automatically. Native validation stays on, sessions UTC and history uses the original single table.

## Synchronization

Merged `develop@79896a993e9a0f620734201efa2f05ed60090b95` without rebase/reset/force push; retained `3698976` and `24f0b0c` plus all preceding Auth commits. Conflicts combined AuthProvider with dev-only lazy Menu preview, both requirement sections, and genuine Menu/QR Newman execution/reporting guards. The merge initially retained conflict markers; a separate corrective commit removed them, preserving history. Current frontend checks cover the combined routes.

## Sources and limits

| Database source | Meaning |
| --- | --- |
| Handoff 20 tables + 4 DEFINER views | SQL SHA-256 `84cb9f2e99073ea73b117e828dc7f144928052a8f8345cde6f903b82280e4b5e`, Flyway CRC `-22922275`, logical version `20261113.1`; implementation/rehearsal artifact, shared rollout/global allocation still requires recorded approval. |
| Legacy 13-table / zero-view snapshot | Separate source with V001–V008 and located V009/V010. Not automatically converted or baselined to the target. |
| Five-table Home candidate | Previous legacy-expansion proposal, not the full target and not introduced into runtime. |
| Historical `schema.sql` | Existing API-test fixture, not an official production migration. |

Nineteen handoff table definitions match the historical fixture; News adds two audience flags/CHECK. Guest foreign keys stay nullable. Four view query bodies match, but SQL SECURITY becomes DEFINER and requires explicit grant/definer lifecycle review.

## Validation

Fresh local checks: full Backend216 (0 failures/errors/skips), Frontend lint/build + Playwright70, native provision/baseline/startup Flyway ON, real HTTP23, Newman Auth39/57 + Menu16/31 + QR9/16, separately expected fault failures, Governance/Git Flow17 and script24. Commands, exact revision and limitations are in [the current handoff](dt2-2-verified-database-integration.md). Flyway-ON startup/native lifecycle evidence is separate from deliberately Flyway-OFF API fixtures. UI Playwright fixtures are not authenticated end-to-end evidence. Older 186/182/112 and previous Newman numbers are historical and are not substituted for this full run.

SMTP is loopback only; Gmail/production acceptance is not claimed. News/guest feedback are SQL-only where no HTTP implementation exists. Public QR identifies a table; auto-session/token creation remains blocked by the existing QA-02 ownership decision.

## Review gates

Local commits only; no new-SHA GitHub CI exists until a separately authorized push. Need all required CI including real DT2-4 Menu/QR, two fresh independent approvals, reviewer-confirmed discussions and CODEOWNER NV-DuyManh/haitan2408 for the workflow. No self-resolve/approve/merge/deploy.

Deployment remains BLOCKED: named DBA, canonical target identity, DDL/News approval, global version allocation, one canonical Database PR artifact if required, least-privilege grants/definers, writer-freeze/backup/restore and explicit rollout permission. Legacy mapping and Auth-local transition are separate plans. Login gateway limits, retention, timing side-channel, Gmail and server-side revocation remain open.

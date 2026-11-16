# DT2-2 authentication frontend integration — 2026-11-16

[Jira DT2-2](https://duymanhdnvn20092005.atlassian.net/browse/DT2-2) · [Backend PR #7](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/7)

## Scope and provenance

`feature/dt2-2_auth-frontend` starts from `develop@ffa4d9527e7924dd0bc9815fefb3034463d03156`. The approved Antigravity UI is reconstructed from local branch `feature/dt2-2-auth-figma-fe@9b3d7aa0e7308df88f5da5969aa2c386b950dabc`, preserving contributor attribution. Source commits are `cd0104d`, `58bff28`, `d1d64d5`, and `9b3d7aa`. Only the authentication screens, their layout/font/style integration, browser tests, and relevant screenshots are included. The source branch's unrelated `start-be.ps1` edit is excluded.

Login retains the approved desktop cafe story and compact mobile two-column card. Forgot Password, OTP and Reset Password use the existing PR #7 contracts, including server-issued challenge/reset grants, expiry, error and success states. Registration remains a form that honestly reports unavailability: develop exposes no public registration endpoint. It does not create accounts, issue fake challenges or show fabricated success. There is no dependency on DT2-3 Profile APIs.

The integration adds input-to-error associations for assistive technology and a plain-language registration availability notice. The accepted UI layout is unchanged. Screenshot tests write to Playwright's isolated output directory so parallel projects cannot overwrite approved evidence. Direct OTP navigation without a challenge is explicitly checked.

## Integration and preservation

The selected patch applied cleanly with `git apply --3way --index`; there were no merge conflicts. A subsequent fetch still resolved develop to the SHA above. Backend, migrations, API helpers, dependency manifests, workflows and governance rules match that base. No private DT2-3 commits or working-tree changes are imported.

The original DT2-3 workspace remains on its original branch and HEAD with identical staged and unstaged binary patches, status, untracked files and local configuration. Local backup/manifests/review records remain outside Git. Do not clean the original workspace as part of this handoff.

## Verification

These checks ran against this FE worktree. Backend source matches `ffa4d95`; none of the original workspace's DT2-3 backend was used.

| Check | Result |
| --- | --- |
| `npm ci` | PASS; 26 packages installed, audit reported 0 vulnerabilities |
| `npm run lint` / `npm run build` | PASS |
| `npm run test:ui -- --workers=2` | PASS; 92 tests across desktop/mobile |
| Corrected direct-OTP-navigation test | PASS; 2 additional executions after the test correction |
| WCAG A/AA browser checks | PASS; 5 auth screens × 7 viewports × 2 browser contexts, with no page errors |
| Official `test-be.ps1` → Maven `verify` | PASS; 216 tests, 0 failures/errors/skips, isolated MySQL; includes native Flyway safety/lifecycle checks |
| Auth/Home Newman | PASS; 39 requests / 57 assertions; real HTTP/MySQL/loopback SMTP |
| Menu Newman | PASS; 16 requests / 31 assertions |
| QR Newman | PASS; 9 requests / 16 assertions |
| Real React → Spring → MySQL → loopback SMTP browser acceptance | PASS; 11 checks, desktop 1366×768 and mobile 390×844, no HTTP mocks |
| Collection assertion-integrity tests | PASS; 21 tests; HTTP stubs test the assertions only |
| Skill registry / inherited inventory | PASS; 17 skills, 16 routes, 169 inherited files |
| Skills / Git Flow regression tests | PASS; 9 + 8 tests |
| Launcher regressions | PASS; 3 tests |
| Git Flow source/target, PR title format, authored whitespace | PASS |

Required responsive viewports are 1920×1080, 1366×768, 768×1024, 430×932, 390×844, 375×812 and 360×800. Each of Login, Register, Forgot Password, OTP and Reset Password was checked for horizontal containment, reachable submit controls and WCAG A/AA violations. All 35 screenshots from the desktop-context matrix are retained locally; representative new images are versioned below. The 18 approved source screenshots remain byte-identical to the source FE branch.

- [Current Login, 430×932](evidence/dt2-2/integration/login-430x932.png)
- [Current OTP, 390×844](evidence/dt2-2/integration/verify-otp-390x844.png)
- [Current Reset Password, 1366×768](evidence/dt2-2/integration/reset-password-1366x768.png)
- [Approved desktop Login](evidence/dt2-2/responsive/login_desktop-1366x768.png)
- [Approved mobile Login](evidence/dt2-2/responsive/login_mobile-390x844.png)

Browser fixture tests and real browser acceptance are distinct evidence. The existing HTTP harness deliberately disables Flyway only for its imported disposable structural fixture, as documented in the Auth runner and DT2-4 workflow; the separate full Maven suite exercises Flyway validation/migrations. Menu/QR ran with `NO_BACKSLASH_ESCAPES` scoped to the test application's JDBC sessions, without changing the MySQL server's global mode. Four separate injected Newman faults retain their expected FAIL verdicts and are not counted as passing functional requests.

## Limits and review

Registration/activation is unavailable until a separately approved public API exists. Backend logout remains stateless; successful local cleanup does not prove server token revocation. Real email-provider delivery, shared-database rollout and production acceptance were not tested. Existing database deployment/lineage and login admission-policy questions remain with their owners; this FE PR does not change them.

GitHub's live develop rules require Governance, PR title, Backend MySQL, Frontend UI and Git Flow, plus two fresh independent approvals and resolved discussions. CI results must be checked on the published HEAD; local PASS is not a claim about GitHub Actions. The DT2-4 workflow has a backend path filter, so a FE-only PR does not automatically trigger it; its existing manual test trigger can verify the published FE revision separately. No merge, approval, discussion resolution, tag or deployment is included.

Skills applied: `cafe-workflow`, `cafe-requirements`, `cafe-quality`, and `cafe-review` at `.agents/skills/<identifier>/SKILL.md`; `vercel-react-best-practices` at `.agents/skills/react-best-practices/SKILL.md`; `fixing-accessibility` at `.agents/skills/fixing-accessibility/SKILL.md`; personal `ponytail` at `C:/Users/khatr/.codex/skills/ponytail/SKILL.md`. They guided scope separation, contract preservation, targeted accessibility fixes, risk-based verification and revision-specific self-review. Self-review is not an independent approval.

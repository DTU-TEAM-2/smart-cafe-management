# DT2-2 authentication frontend — P2/P3 review fixes, 2026-11-18

[Jira DT2-2](https://duymanhdnvn20092005.atlassian.net/browse/DT2-2) · [PR #17](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/17) · [Backend PR #7](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/7)

## Approved scope and current behavior

The owner selected these changes on 2026-11-18 in response to [NV-DuyManh's P2](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/17#discussion_r4607934991) and [P3](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/17#discussion_r4607934996). This handoff and the refreshed screenshots supersede the form-based registration evidence at `cd5634a`.

Register now immediately renders this status text, with no submit or personal information required:

> Đăng ký tài khoản trực tuyến hiện chưa khả dụng. Vui lòng đăng nhập bằng tài khoản hiện có hoặc liên hệ nhân viên quán để được hỗ trợ.

The page keeps LECKER branding, its shared sidebar, Playfair Display/Inter typography, dark surfaces and gold actions. It renders no form or personal-data inputs. Its working actions return to Login or Home. The Login registration link remains in its accepted position, visibly labeled “Chưa khả dụng” and named “Đăng ký (Chưa khả dụng)” for assistive technology. No Login layout rule changed.

All registration-only hooks, `initialStep`, `initialChallenge`, `step/setStep`, `registrationChallenge`, personal-data/password/OTP state, submit/verify handlers and activation markup are removed. Registration-only `.auth-header-row` and `.form-row` CSS was removed after checking other callers. No CSS merely hides unsupported controls. Shared recovery OTP CSS remains.

The existing `#register` prefix routing safely sends legacy `#register/verify`, `#register-verify` and `#register?step=verify&challengeId=legacy` URLs to the same unavailable page. No router expansion was needed. Password Recovery OTP remains separate and functional. AuthContext, authApi, Login submission, Forgot Password, recovery OTP/cooldown/resend, Reset Password, role routing and logout logic are unchanged. There is no registration/activation request, account reservation, fake challenge, fake success or dependency on DT2-3 Profile APIs.

## Integration and provenance

The existing branch `feature/dt2-2_auth-frontend` continues to target develop. Fetch and `git merge origin/develop` confirmed `ffa4d9527e7924dd0bc9815fefb3034463d03156` is current and already integrated; no conflict occurred. Backend, scripts, migrations, manifests and workflows are unchanged. No DT2-3 source/commits were imported.

Original approved Antigravity work came from `feature/dt2-2-auth-figma-fe@9b3d7aa`, commits `cd0104d`, `58bff28`, `d1d64d5`, `9b3d7aa`, reconstructed in `cd5634a`. Its unrelated backend-launcher edit remains excluded. Original UI evidence remains available in that Git history; current Login/Register screenshots below represent the corrected behavior.

The original workspace `C:/Smart_Cafe` remains on `feature/dt2-3_personal-account-backend@ffa4d95`. A new private snapshot was taken before these edits: 614 file hashes, branch/HEAD/status and staged/unstaged binary patches are identical. Its 12 modified and 42 untracked entries, including all DT2-3 work/configuration, are preserved. Backups, review records, raw logs and reviewer reply drafts stay outside Git.

## Verification on this revision

| Check | Result |
| --- | --- |
| `npm ci` | PASS installation; 26 packages; existing dependency audit has 5 high advisories |
| `npm run lint` / `npm run build` | PASS |
| `npm run test:ui -- --workers=2` | PASS; 100 tests across desktop/mobile |
| WCAG A/AA + responsive matrix | PASS; 5 auth screens × 7 viewports × 2 contexts, reachable controls, no page errors |
| Official `test-be.ps1` → Maven verify, isolated MySQL | PASS; 216 tests, 0 failures/errors/skips; native Flyway safety/lifecycle included |
| Real HTTP Newman Auth/Home, Menu, QR | PASS; Auth/Home 39 requests / 57 assertions, Menu 16 / 31, QR 9 / 16 |
| Registry and inherited inventory | PASS; 17 skills / 16 routes / 169 files |
| Skills / Git Flow regression | PASS; 9 / 8 tests |
| Git Flow source/target, title convention, scope, whitespace/conflict review | PASS; review record remains outside Git |

The real HTTP runner uses disposable imported structural fixtures with Flyway disabled only in that runner, as documented upstream; the separate full Maven suite exercises enabled Flyway validation/migration safety. Menu/QR used NO_BACKSLASH_ESCAPES in test JDBC sessions only. No shared schema or global MySQL mode changed. Four deliberate Newman fault injections retained their expected FAIL verdicts and are not counted as functional PASS.

New browser regressions verify the immediate notice, zero personal-data controls/forms, absence of promised OTP/activation/demo controls, no mutating requests, working Login/Home navigation, the visible Login availability label, and all three legacy registration URLs. Existing recovery/login/role/logout and other module regressions remain in the full suite. These browser HTTP fixtures do not claim a registration backend exists. The pre-fix P2 probe failed because the immediate notice was absent; its failure log is preserved privately.

The required visual viewports are 1920×1080, 1366×768, 768×1024, 390×844, 375×812 and 360×800. Existing broader tests also retain 430×932 and other responsive sizes. Twelve current Login/Register viewport screenshots replace their older files; the current 430×932 Login integration image is refreshed too. The full 35-image matrix is retained privately.

| Viewport | Current Login | Current Register |
| --- | --- | --- |
| 1920×1080 | [Login](evidence/dt2-2/responsive/login_desktop-1920x1080.png) | [Register](evidence/dt2-2/responsive/register_desktop-1920x1080.png) |
| 1366×768 | [Login](evidence/dt2-2/responsive/login_desktop-1366x768.png) | [Register](evidence/dt2-2/responsive/register_desktop-1366x768.png) |
| 768×1024 | [Login](evidence/dt2-2/responsive/login_tablet-768x1024.png) | [Register](evidence/dt2-2/responsive/register_tablet-768x1024.png) |
| 390×844 | [Login](evidence/dt2-2/responsive/login_mobile-390x844.png) | [Register](evidence/dt2-2/responsive/register_mobile-390x844.png) |
| 375×812 | [Login](evidence/dt2-2/responsive/login_mobile-375x812.png) | [Register](evidence/dt2-2/responsive/register_mobile-375x812.png) |
| 360×800 | [Login](evidence/dt2-2/responsive/login_mobile-360x800.png) | [Register](evidence/dt2-2/responsive/register_mobile-360x800.png) |

## Limits and review handoff

No dependency was added or changed. The 5 high npm audit advisories concern the existing Vite toolchain/dependency graph on the unchanged lockfile; no dependency upgrade/downgrade was attempted outside P2/P3. These advisories remain recorded for the dependency owner. Registration stays unavailable until a separately approved API exists. Backend logout remains stateless. Shared-database rollout, real provider email delivery and production acceptance are outside this change.

GitHub checks must complete on the newly published SHA. Live develop rules still require Backend MySQL, Frontend UI, Governance, Git Flow and PR title, two fresh independent approvals and discussion resolution. DT2-4 Postman is path-filtered and can be run through its existing manual test trigger. Reviewer responses are prepared privately for the user's approval; do not post them or resolve the two threads on the reviewer's behalf. No merge, self-approval, tag, deployment or shared DB mutation is part of this handoff.

Skills applied: `cafe-workflow`, `cafe-requirements`, `cafe-quality`, `cafe-review` at `.agents/skills/<identifier>/SKILL.md`; `vercel-react-best-practices` at `.agents/skills/react-best-practices/SKILL.md`; `fixing-accessibility` and `ui-ux-pro-max` at `.agents/skills/<identifier>/SKILL.md`; personal `ponytail` at `C:/Users/khatr/.codex/skills/ponytail/SKILL.md`. They guided scope/provenance, removal of dead UI, clear availability/keyboard semantics, unchanged design tokens and revision-specific verification/self-review.

# CF-04 / DT2-28 — PR handoff

Title: `feat(account): CF-04 implement DT2-28 account profile and password management frontend`

Head `feature/dt2-3_personal-account-frontend` → `develop`. Owner chấp nhận UI và cho phép publication ngày19/11/2026. [Jira DT2-28](https://duymanhdnvn20092005.atlassian.net/browse/DT2-28) · [Backend PR19](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/19).

Profile ADMIN/STAFF từ Header; inline edit name/address/phone; Change Password, expiry30ngày, loading/nullable/error/validation/conflict/success và responsive. GET/PATCH `/api/v1/profiles/me`, POST `/password`; version decimal string/snapshot, không replay stale draft. AuthContext thay token trước refresh, bảo vệ generation/stale401/logout, giữ server password status. CUSTOMER bị chặn personnel API/UI. [Handoff và25ảnh](dt2-28-frontend.md) · [Fresh results](../reports/dt2-28-frontend-test-results.md) · [Machine evidence/checksum](../reports/dt2-28-frontend-evidence.json).

LECKER supplemental design đã duyệt, không claim pixel-perfect Figma. Fetch/prune + merge develop e3ce5b1 trả Already up to date, không conflict. Backend/API/DDL/workflows/lockfile giữ nguyên; không nhập PR chưa merge. Full Playwright176, unit4, real browser8, Maven315/MySQL, Newman203/587+10SQL vàAuth39/57 PASS mới ngày19/11. Negative controls tách riêng. npm audit mới0vulnerabilities, không đổi dependency.

CI xác minh sau push trên final HEAD; Customer Menu Postman có path filter nên chạy workflow_dispatch đúng feature SHA. Chỉ chuyển Ready khi check thực tế đạt; hai independent approval vẫn cần. Shared legacy DB cần DBA rollout; Sonar/production UAT/deploy chưa thực hiện.

## Exact commit allowlist

Chỉ58file sau, không git add .:

```text
docs/architecture/decisions.md
docs/handover/dt2-28-frontend.md
docs/handover/dt2-28-pr-description.md
docs/handover/evidence/dt2-28/change-password.png
docs/handover/evidence/dt2-28/live-admin-profile.png
docs/handover/evidence/dt2-28/live-conflict.png
docs/handover/evidence/dt2-28/live-expiry.png
docs/handover/evidence/dt2-28/live-password-success.png
docs/handover/evidence/dt2-28/live-staff-profile.png
docs/handover/evidence/dt2-28/password-mobile.png
docs/handover/evidence/dt2-28/password-success.png
docs/handover/evidence/dt2-28/profile-1024x768.png
docs/handover/evidence/dt2-28/profile-1366x768.png
docs/handover/evidence/dt2-28/profile-1440x900.png
docs/handover/evidence/dt2-28/profile-1920x1080.png
docs/handover/evidence/dt2-28/profile-390x844.png
docs/handover/evidence/dt2-28/profile-768x1024.png
docs/handover/evidence/dt2-28/profile-admin.png
docs/handover/evidence/dt2-28/profile-conflict.png
docs/handover/evidence/dt2-28/profile-edit.png
docs/handover/evidence/dt2-28/profile-empty.png
docs/handover/evidence/dt2-28/profile-error.png
docs/handover/evidence/dt2-28/profile-expiry.png
docs/handover/evidence/dt2-28/profile-loading.png
docs/handover/evidence/dt2-28/profile-staff.png
docs/handover/evidence/dt2-28/profile-success.png
docs/handover/evidence/dt2-28/profile-validation.png
docs/handover/evidence/dt2-28/reference-auth.png
docs/reports/dt2-28-frontend-evidence.json
docs/reports/dt2-28-frontend-test-results.md
frontend/e2e-live/account.spec.js
frontend/package.json
frontend/playwright.account-live.config.js
frontend/src/account/AccountFeedback.jsx
frontend/src/account/AccountPage.jsx
frontend/src/account/ChangePasswordForm.jsx
frontend/src/account/LeaveProfileDialog.jsx
frontend/src/account/PasswordExpiryNotice.jsx
frontend/src/account/ProfileView.jsx
frontend/src/account/account.css
frontend/src/account/accountApi.js
frontend/src/account/profileValidation.js
frontend/src/account/useAccountProfile.js
frontend/src/auth/AuthContext.jsx
frontend/src/auth/authApi.js
frontend/src/layout/AccountAvatar.jsx
frontend/src/layout/HomeShell.jsx
frontend/src/layout/SiteLayout.jsx
frontend/src/main.jsx
frontend/tests/account-fixtures.js
frontend/tests/account-harness.html
frontend/tests/account-harness.jsx
frontend/tests/account-lifecycle.spec.js
frontend/tests/account-ui.spec.js
frontend/tests/header.spec.js
frontend/tests/home.spec.js
frontend/unit/profile-validation.test.mjs
scripts/profile-browser-live.py
```

Loại khỏi commit: .env/credentials/token, DB exports, raw logs, review/backup private, target/dist/node_modules/cache và năm tệp Backend untracked có trước. Backup ngoài repo và checksum giữ nguyên. Trước mỗi push đọc lại guide cá nhân, review exact diff/revision, lưu record ngoài Git. Không auto-resolve/self-approve/merge/Jira Done.

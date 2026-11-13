# Smart Cafe — repository instructions

This repository contains Smart Cafe's engineering foundation. Follow the mandatory Git Flow, review and merge policy in `CONTRIBUTING.md`.

## Before work

- Read `CONTRIBUTING.md`, `docs/engineering/team-working-agreement.md` and `docs/engineering/naming-conventions.md`.
- Always read and apply [Git Flow](git-flow.md), adopted by the owner on 2026-11-12. It supersedes main-only GitHub Flow; task branches start at develop and target develop. Release/hotfix promotion and main-to-develop sync follow git-flow.md. Run node --test scripts/git-flow.test.mjs for policy changes.
- Follow the mandatory [technology constraints](.agents/rules/technology-constraints.md): backend Spring Boot, frontend ReactJS, business database MySQL. Other tools are optional within task scope and compatibility requirements; they do not replace the required stack. Do not treat historical stack proposals or skill suggestions as permission to change it.
- Read `.agents/rules/mandatory-skill-router.md`; inspect `SKILLS_INSTALLED.md`, choose up to three relevant skills per phase and read their actual SKILL.md before applying them. State their identifiers and paths. Do not load the entire catalog.
- For any data-related task, read and follow the mandatory [database source-of-truth rule](.agents/rules/database-source-of-truth.md). It applies to the entire project and all contributors/agents, including frontend contracts, persistence, scripts and test fixtures.
- Skills guide work; they do not authorize account changes, publication, messaging, spending or deployment. Existing user authorization governs ordinary actions.
- Preserve contributor attribution, upstream licenses and provenance. Do not rewrite another project's history or claim inherited work as new.

## Resolve conflicting guidance

- Follow platform and access restrictions first. For business behavior, use the approved SRS and recorded decisions within the approver's authority. For engineering and integration, follow repository rules. Skills guide implementation within those constraints.
- A mentor's recorded decision supersedes an earlier decision only when the mentor has authority for that scope and explicitly records the change. When authoritative sources disagree or authority is unclear, record the sources and affected behavior, ask the responsible owner to decide, and continue independent work. See the [routing rules](.agents/rules/mandatory-skill-router.md).

## Current scope and verification

- Task 7 is strictly free-only per the owner's renewed instruction on 2026-11-12: never enable billing, link a payment method, upgrade to Blaze, or use a trial that requires payment setup. Do not ask to enable billing again. Use the local Firebase Storage Emulator without cloud credentials; keep live Storage disabled. A cloud dependency requiring billing remains unavailable under this scope.

- DT2-1 uses the system database `smart_cafe`. Public highlights and protected account/notification APIs read the actual tables. The owner's full Task 7 request on 2026-11-11 authorizes connecting the login form to the actual DT2-2 login endpoint, superseding the earlier UI-only scope. Authentication consumes that module's signed bearer contract and verifies identity through the consumer API before retaining a session; it never fabricates a logged-in user, issuer, demo account or role selector. Leave credential issuance and authentication policy to the responsible module owner. No automatic database seed belongs in application startup.
- Follow the latest owner-supplied database diagram, saved at `docs/handover/evidence/system-database-reference-2026-11-11.jpg`, and the [database rule](.agents/rules/database-source-of-truth.md). The 06/11 image is historical. Inspect actual DDL before changing persistence; never invent tables, columns, relations, constraints or data. Record diagram/live-DDL differences in `docs/architecture/database-contract.md` and hold affected changes for the owner; do not silently migrate the system database. Test fixtures must come from the approved actual schema and run only in isolated *_test databases.
- Run `node scripts/skills.mjs check` and `node --test scripts/skills.test.mjs` for registry/workflow changes. Check authored changes with `git diff --check -- . ':(exclude).agents/skills/**'`; inherited snapshots keep their original whitespace rather than rewriting upstream content during import.
- Run `backend/mvnw verify` with an isolated MySQL test database for API changes; without `TEST_DB_URL` the MySQL tests are skipped. Run frontend lint, build and Playwright for UI changes. CI provisions an isolated MySQL schema and runs these checks alongside governance and PR title.
- `.env`, database exports, tokens, credentials, caches and build output never belong in Git.

## Product and collaboration

- Source requirements must be supplied and versioned under `docs/handover/` before settling business behavior. Historical sections and files named inside inherited skills are reading targets, not proof that those requirements exist or were approved in this repo.
- Track unanswered rules in `docs/product/open-questions.md`. Do not silently decide session activation, cancellation deadlines, payment recognition, reset or offline behavior.
- Use `docs/architecture/decisions.md` for new accepted decisions. Keep implementation proposals distinct from approved requirements.
- Follow requirements → design/implementation → verification → review → handoff. Failed checks return to implementation; unresolved behavior returns to requirements for that part only.
- `main` and `develop` are long-lived. Start feature/bugfix tasks from current develop and target develop; release branches start from develop and target main; production hotfixes start from main/tag and target main. Synchronize approved main releases/hotfixes back to develop through a PR. Release stabilization accepts bugfix/hotfix PRs only. Reuse open task branches, never rename/rewrite someone else's branch; no direct commits or force-push to shared branches.
- Every PR into main/develop/release needs at least two approvals from distinct reviewers other than its author before merge. Self-review and multiple accounts operated to manufacture a second reviewer do not count. New reviewable commits dismiss stale approvals; re-review the final changes. Required checks, resolved discussions and authorized-merge restrictions still apply.
- Backend must eventually own authorization, prices, session/order state and totals. The browser never establishes those authorities.
- Do not import code, migration state, staff/customer data, old completion status or a fake authenticated account from the previous repo.
- Critical CI/validator/policy edits listed in `.github/CODEOWNERS` require NV-DuyManh or haitan2408, other than the author, among the two approving reviewers. GitHub loads CODEOWNERS from the base branch; its initial bootstrap needs explicit owner review and must not be represented as already mechanically enforced. Git Flow running PR code is not an independent policy boundary.

# Smart Cafe — restart repository instructions

This repository contains Smart Cafe's engineering foundation. Follow the main branch review and merge policy in `CONTRIBUTING.md`.

## Before work

- Read `CONTRIBUTING.md`, `docs/engineering/team-working-agreement.md` and `docs/engineering/naming-conventions.md`.
- Read `.agents/rules/mandatory-skill-router.md`; inspect `SKILLS_INSTALLED.md`, choose up to three relevant skills per phase and read their actual SKILL.md before applying them. State their identifiers and paths. Do not load the entire catalog.
- Skills guide work; they do not authorize account changes, publication, messaging, spending or deployment. Existing user authorization governs ordinary actions.
- Preserve contributor attribution, upstream licenses and provenance. Do not rewrite another project's history or claim inherited work as new.

## Resolve conflicting guidance

- Follow platform and access restrictions first. For business behavior, use the approved SRS and recorded decisions within the approver's authority. For engineering and integration, follow repository rules. Skills guide implementation within those constraints.
- A mentor's recorded decision supersedes an earlier decision only when the mentor has authority for that scope and explicitly records the change. When authoritative sources disagree or authority is unclear, record the sources and affected behavior, ask the responsible owner to decide, and continue independent work. See the [routing rules](.agents/rules/mandatory-skill-router.md).

## Current scope and verification

- This is a governance/skills foundation, not a runnable application. There is no backend, frontend, datasource or migration yet. Do not claim application tests have passed.
- Run `node scripts/skills.mjs check` and `node --test scripts/skills.test.mjs` for registry/workflow changes. Check authored changes with `git diff --check -- . ':(exclude).agents/skills/**'`; inherited snapshots keep their original whitespace rather than rewriting upstream content during import.
- Add build, lint, application and integration checks when the corresponding code is introduced. CI currently checks governance and PR title only.
- `.env`, database exports, tokens, credentials, caches and build output never belong in Git.

## Product and collaboration

- Source requirements must be supplied and versioned under `docs/handover/` before settling business behavior. Historical sections and files named inside inherited skills are reading targets, not proof that those requirements exist or were approved in this repo.
- Track unanswered rules in `docs/product/open-questions.md`. Do not silently decide session activation, cancellation deadlines, payment recognition, reset or offline behavior.
- Use `docs/architecture/decisions.md` for new accepted decisions. Keep implementation proposals distinct from approved requirements.
- Follow requirements → design/implementation → verification → review → handoff. Failed checks return to implementation; unresolved behavior returns to requirements for that part only.
- Main is the single long-lived branch. Start short task branches from current main, keep related fixes in the same open PR, and remove task branches after merge. No direct app development on main, no force-push to shared branches.
- Backend must eventually own authorization, prices, session/order state and totals. The browser never establishes those authorities.
- Do not import code, migration state, staff/customer data, old completion status or a fake authenticated account from the previous repo.

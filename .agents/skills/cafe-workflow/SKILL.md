---
name: cafe-workflow
description: Route Smart Cafe work across requirements, implementation, verification, review and handoff phases. Use for a feature spanning multiple domains or coordinating work between contributors.
---

# Cafe workflow

Read root `AGENTS.md`, `git-flow.md` and `.agents/rules/mandatory-skill-router.md`. Git Flow is mandatory: task branches start from develop and PR into develop; only release/hotfix promotion targets main, followed by a main-to-develop synchronization PR. Choose only the skills for the current phase using `.agents/registry.json`. Domain routing is a candidate list, not a requirement to load every candidate.

Start from the user's request and a CF/QA issue when available. For a feature, follow `.agents/workflows/feature.md`; for an incident, `.agents/workflows/bugfix.md`; for an authorized release, `.agents/workflows/release.md`.

Carry across phases: scope, source/decision links, acceptance criteria, affected files, exact commands/results and remaining questions. Use `docs/templates/task-handoff.md` for work handed to another person. Keep small fixes in the PR instead of generating a report for every edit.

When a requirement is unclear, isolate the dependent behavior and continue independent work. When verification fails, return to implementation with the concrete failure. A review is not complete merely because tests pass. Shipping requires its own authorization; routing does not grant it.

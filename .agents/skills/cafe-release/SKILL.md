---
name: cafe-release
description: Prepare or carry out an authorized Smart Cafe release with revision, checks, configuration, migration and rollback evidence. Use for release branches, version tags, deployment or release readiness.
---

# Cafe release

Read `CONTRIBUTING.md`, `.agents/workflows/release.md` and the task's destination/environment. Distinguish preparing a reviewable release from publishing/deploying it; existing user authorization governs execution.

Identify the exact revision, release scope, unresolved defects, CI result and configuration changes. If persistence exists, review migration order, backup and recovery first. Use environment secret storage; never commit live `.env` values.

Build the candidate, run the applicable smoke/permission/business checks, and prepare release notes plus a rollback path. Report unavailable credentials/tools or blocked required checks precisely. Do not mark a release complete when only a local build succeeded.

For an authorized deployment, verify the deployed revision and health plus one scoped business smoke after rollout. Integrate hotfixes into `main` through PRs; keep contributor metadata and delete the task branch after merge. No automatic new service/account, paid resource, or production data mutation is authorized by invoking this skill.

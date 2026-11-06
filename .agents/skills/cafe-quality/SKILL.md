---
name: cafe-quality
description: Verify Smart Cafe changes against acceptance criteria with risk-based tests, permission checks and reproducible evidence. Use for test planning, regression, integration checks or completing a code change.
---

# Cafe quality

Read `docs/engineering/quality-and-security.md` and the task criteria. Select tests by changed behavior and risk; avoid snapshots that merely mirror implementation.

For backend/API behavior, cover success plus relevant validation/authorization failures. For sessions/orders/bills, add the applicable retry, concurrent transition, cross-table access, expired-session and monetary-boundary cases. For UI, inspect loading, empty, failed and successful states; verify keyboard/focus and narrow layouts when affected.

Run the current baseline from root `AGENTS.md`, then only additional checks justified by this change. A docs-only change needs link/registry checks, not a fresh full application test suite. Use the database engine and browsers actually available; label anything not run.

Record command, revision/context, result, test count where useful and evidence location. Do not paste credentials or staff personal details into evidence. If a check fails, return its concrete reproduction to implementation. Hand off passed checks and remaining coverage gaps to review.

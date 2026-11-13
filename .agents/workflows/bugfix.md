# Bugfix handoff

1. Record observed vs expected behavior, affected role/table/session, reproduction and sanitized evidence. Confirm scope/severity in the issue.
2. Use the domain skill to reproduce and isolate the cause. Consult `cafe-requirements` only if expected behavior is unclear.
3. Implement the smallest correction with a regression test when it protects meaningful behavior. Under [Git Flow](../../git-flow.md), development bugfix branches start from develop and target develop. A release-only fix starts from and targets that release. Production hotfix branches start from main/the deployed tag and target main.
4. `cafe-quality` verifies the reproduction and affected adjacent behavior. `cafe-review` checks the final diff and evidence.
5. Hand off cause, correction, tests and residual risk in a PR to the appropriate target above. Keep related fixes on that branch until merged. After a production hotfix, open main -> develop sync and update any open release needing it before cleanup. Require CI/two independent approvals; deployment remains separately authorized via `cafe-release`.

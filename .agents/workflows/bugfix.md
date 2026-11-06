# Bugfix handoff

1. Record observed vs expected behavior, affected role/table/session, reproduction and sanitized evidence. Confirm scope/severity in the issue.
2. Use the domain skill to reproduce and isolate the cause. Consult `cafe-requirements` only if expected behavior is unclear.
3. Implement the smallest correction with a regression test when it protects meaningful behavior. Branch from current `main`; use the production tag for a hotfix only when needed, then integrate the correction into `main`.
4. `cafe-quality` verifies the reproduction and affected adjacent behavior. `cafe-review` checks the final diff and evidence.
5. Hand off cause, correction, tests and residual risk in a PR into `main`. Continue related fixes on that PR branch until it is merged, then delete the branch. If deployment is requested, pass the exact revision to `cafe-release`.

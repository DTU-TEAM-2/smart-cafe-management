# Release handoff

Input: target environment, exact revision, scope, test/CI evidence and existing authorization.

1. Follow [Git Flow](../../git-flow.md). `cafe-release` creates release/* from develop only after scope/version are chosen, then prepares the candidate, release notes, configuration delta, migration/backup needs and rollback action. No new features enter the release; fixes use bugfix/hotfix PRs targeting it.
2. `cafe-quality` runs checks appropriate to the release. A required failed check returns the candidate to implementation; record any deliberately accepted residual risk and who accepted it.
3. `cafe-review` verifies the final candidate and evidence. Release/hotfix promotion to main needs CI, two independent approvals and an authorized merger under CONTRIBUTING.md. Use merge commits; follow with a main -> develop sync PR and integrate into other affected releases before cleanup. GitHub head deletion does not remove the sync obligation.
4. Execute publication/deployment only when the user/task authorization covers the destination. Verify the actual deployed revision, health and scoped smoke check before marking it released.
5. Record tag, environment, time, operator, results and rollback reference. Do not invent successful rollout evidence.

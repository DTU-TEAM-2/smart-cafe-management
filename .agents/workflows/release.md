# Release handoff

Input: target environment, exact revision, scope, test/CI evidence and existing authorization.

1. `cafe-release` prepares the candidate, release notes, configuration delta, migration/backup needs and rollback action.
2. `cafe-quality` runs checks appropriate to the release. A required failed check returns the candidate to implementation; record any deliberately accepted residual risk and who accepted it.
3. `cafe-review` verifies the final candidate and evidence. Team release merges follow `CONTRIBUTING.md`.
4. Execute publication/deployment only when the user/task authorization covers the destination. Verify the actual deployed revision, health and scoped smoke check before marking it released.
5. Record tag, environment, time, operator, results and rollback reference. Do not invent successful rollout evidence.

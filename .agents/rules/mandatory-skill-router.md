# Mandatory skill routing

This rule is anchored by root `AGENTS.md`; it does not depend on an editor-specific `always_on` setting.

1. Identify the current phase and the task domains.
2. Inspect `SKILLS_INSTALLED.md` or run `node scripts/skills.mjs list`.
3. Run `node scripts/skills.mjs route <domain> ...` to inspect candidates. Use only domains needed now. Multiple domains are combined in order with duplicates removed. If more than three skills match, narrow the current phase and route the remaining work later. Start with `requirements` when the requested outcome is unclear. For an unsupported domain, report that no skill fits that domain and follow the repository rules.
4. Read each selected `.agents/skills/<folder>/SKILL.md` before work in that phase. Frontmatter `name` is the identifier; folder names may differ.
5. Declare `Skills loaded: <identifier> (<path>)` briefly. Say `Skills matched: none` when no installed skill fits; do not pretend to apply one.
6. At a phase change, carry forward the handoff evidence and load the next phase's relevant skills. A failed check returns to implementation; a business ambiguity returns to requirements only for the affected work.
7. Record skills actually read and material effects in the handoff/report. Reading the registry alone is not applying a skill.

Canonical paths are `.agents/skills/`. Do not create a duplicate `.agent/skills/` tree. Registry and routing cases are checked in CI. External skills may reference unavailable tools; use a documented equivalent or report that limitation. They do not authorize account changes or installs.

## Priority and phase transitions

Follow the [instruction priority](../../AGENTS.md#resolve-conflicting-guidance). A skill conflicting with an authoritative rule yields to that rule. When approved sources conflict, record the exact conflict in the task or decision log and obtain a decision from the business or repository owner as appropriate. Hold only the affected work.

| Phase | Input | Output | Proceed when |
| --- | --- | --- | --- |
| Requirements | Request, task and applicable SRS/decisions | Scope, observable acceptance criteria and open questions | The affected behavior has a clear outcome and source |
| Design | Acceptance criteria and constraints | API/data/UI boundaries, affected modules and decisions | Callers and owners agree on the affected interfaces |
| Implement | Agreed scope and contracts | Focused diff and affected checks | Normal entry points work and the diff is ready to verify |
| Verify | Diff and acceptance criteria | Commands, results and remaining coverage limits | Relevant checks pass. Reproduce failures and return them to implementation |
| Review | Diff, criteria and verification evidence | Findings and a merge decision | Blockers and discussions are resolved, required CI passes, and an authorized maintainer decides to merge |
| Handoff | Reviewed revision and remaining limits | PR/task links, evidence and follow-up ownership | The recipient can locate the result and act on remaining items |
| Release | Explicit release request and verified revision | Release checks, rollback plan and deployment evidence if applicable | Release conditions and authorization are met |

## Maintain skills

The repository maintainer (`NV-DuyManh`) owns the registry. The author proposes changes through a PR with the reason, affected domains, and examples of expected behavior. Invite the team to review the proposal. Verify the registry, routing tests and inherited checksums. Add a routing test when behavior changes. Update an inherited checksum only after reviewing the source revision, license and file changes, and record the provenance in the same PR.

See [.agents/workflows](../workflows/feature.md) for the concrete feature, bugfix and release handoffs. Skill routing does not spawn agents or create chats automatically.

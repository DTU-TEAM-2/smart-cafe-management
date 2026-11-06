# Mandatory skill routing

This rule is anchored by root `AGENTS.md`; it does not depend on an editor-specific `always_on` setting.

1. Identify the current phase and the task domains.
2. Inspect `SKILLS_INSTALLED.md` or run `node scripts/skills.mjs list`.
3. Run `node scripts/skills.mjs route <domain>` to inspect a domain's candidates. Select the smallest useful set, usually at most three per phase.
4. Read each selected `.agents/skills/<folder>/SKILL.md` before work in that phase. Frontmatter `name` is the identifier; folder names may differ.
5. Declare `Skills loaded: <identifier> (<path>)` briefly. Say `Skills matched: none` when no installed skill fits; do not pretend to apply one.
6. At a phase change, carry forward the handoff evidence and load the next phase's relevant skills. A failed check returns to implementation; a business ambiguity returns to requirements only for the affected work.
7. Record skills actually read and material effects in the handoff/report. Reading the registry alone is not applying a skill.

Canonical paths are `.agents/skills/`. Do not create a duplicate `.agent/skills/` tree. Registry and routing cases are checked in CI. External skills may reference unavailable tools; use a documented equivalent or report that limitation. They do not authorize account changes or installs.

## Phase transitions

| Phase | Input | Output and next phase |
| --- | --- | --- |
| Understand | Request, CF/QA card, SRS | Acceptance criteria, decisions, dependencies → design/implementation |
| Design | Ready criteria | API/data/UI boundary and affected modules → implementation |
| Implement | Agreed contract | Small code change, migrations if needed → verify |
| Verify | Change + acceptance criteria | Test results and evidence → review; failure → implement |
| Review | Diff + evidence | Findings resolved or explicit remaining risks → handoff |
| Release | Release request + verified revision | Release/rollback plan; execute only within authorization |

See [.agents/workflows](../workflows/feature.md) for the concrete feature, bugfix and release handoffs. Skill routing does not spawn agents or create chats automatically.

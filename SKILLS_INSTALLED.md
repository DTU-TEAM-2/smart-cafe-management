# Skill registry and handoffs

Canonical directory: `.agents/skills/`. Machine-readable registry: [.agents/registry.json](.agents/registry.json). Routing entrypoint: [AGENTS.md](AGENTS.md). This structure adapts MathVisionKid's canonical paths, selective loading and traceable reports to the cafe stack.

| Identifier | SKILL.md | Use |
| --- | --- | --- |
| `cafe-workflow` | [.agents/skills/cafe-workflow/SKILL.md](.agents/skills/cafe-workflow/SKILL.md) | Multi-phase work and handoff |
| `cafe-requirements` | [.agents/skills/cafe-requirements/SKILL.md](.agents/skills/cafe-requirements/SKILL.md) | SRS, CF/QA and acceptance criteria |
| `cafe-spring-api` | [.agents/skills/cafe-spring-api/SKILL.md](.agents/skills/cafe-spring-api/SKILL.md) | Spring, authorization and transactions |
| `cafe-database` | [.agents/skills/cafe-database/SKILL.md](.agents/skills/cafe-database/SKILL.md) | Schema, migrations and history |
| `cafe-quality` | [.agents/skills/cafe-quality/SKILL.md](.agents/skills/cafe-quality/SKILL.md) | Tests, regression and evidence |
| `cafe-review` | [.agents/skills/cafe-review/SKILL.md](.agents/skills/cafe-review/SKILL.md) | Correctness/security review |
| `cafe-release` | [.agents/skills/cafe-release/SKILL.md](.agents/skills/cafe-release/SKILL.md) | Release and recovery |
| `vercel-react-best-practices` | [.agents/skills/react-best-practices/SKILL.md](.agents/skills/react-best-practices/SKILL.md) | React; identifier differs from folder |
| `fixing-accessibility` | [.agents/skills/fixing-accessibility/SKILL.md](.agents/skills/fixing-accessibility/SKILL.md) | Accessible interaction |
| `web-design-guidelines` | [.agents/skills/web-design-guidelines/SKILL.md](.agents/skills/web-design-guidelines/SKILL.md) | Web UI review |
| `ui-ux-pro-max` | [.agents/skills/ui-ux-pro-max/SKILL.md](.agents/skills/ui-ux-pro-max/SKILL.md) | UI/UX research and design |
| `ponytail-review` | [.agents/skills/ponytail-review/SKILL.md](.agents/skills/ponytail-review/SKILL.md) | Complexity review only |
| `tdd` | [.agents/skills/tdd/SKILL.md](.agents/skills/tdd/SKILL.md) | Behavioral test-first loop; task AC authorize ordinary test seams |
| `diagnosing-bugs` | [.agents/skills/diagnosing-bugs/SKILL.md](.agents/skills/diagnosing-bugs/SKILL.md) | Reproducible diagnosis for hard bugs |
| `domain-modeling` | [.agents/skills/domain-modeling/SKILL.md](.agents/skills/domain-modeling/SKILL.md) | Shared terms and meaningful ADRs |
| `codebase-design` | [.agents/skills/codebase-design/SKILL.md](.agents/skills/codebase-design/SKILL.md) | Supporting module-interface vocabulary for TDD |
| `open-design-web-prototype` | [.agents/skills/open-design-web-prototype/SKILL.md](.agents/skills/open-design-web-prototype/SKILL.md) | Optional standalone prototype with OpenDesign seed/layouts; no daemon dependency for reading this bundle |

```shell
node scripts/skills.mjs list
node scripts/skills.mjs route backend
node scripts/skills.mjs route frontend
node scripts/skills.mjs route backend database
node scripts/skills.mjs route requirements
node scripts/skills.mjs check
node --test scripts/skills.test.mjs
```

These commands inspect and validate routing; they do not execute the skills or change permissions. Codex can discover repo skills from `.agents/skills` when opened inside this repo; other agents should read `AGENTS.md` and the registry explicitly. See [official skill documentation](https://learn.chatgpt.com/docs/build-skills).

Pass several domains for the same phase. The router deduplicates shared skills and asks you to narrow the selection if more than three match. Use `requirements` to clarify an unfinished brief. `check` verifies both the registry and every SHA-256 entry in the inherited skill manifest.

For UTF-8 files, checksum verification accepts equivalent LF and CRLF line endings so Windows snapshots and Git checkouts can be compared. Content changes or missing files fail the check.

Typical lifecycle: requirements → domain implementation → quality → correctness review → handoff. Release is a separate, requested phase. A backend bug routes straight to backend/quality/review unless its expected behavior is unclear. Each phase loads only its relevant files, usually at most three.

UI research scripts use Python 3; validation uses the standard Node runtime. The inherited web-review skill fetches current external guidelines, so an offline review must disclose that limitation. No account keys are bundled. See [inherited skill provenance](docs/engineering/skill-provenance.md).

Matt Pocock source pinned at `4ae2317ddb22b046e26455e4f9c9b3aaaf62212d`; OpenDesign at `5a0c47777975ddb91d09c1c16b423a3ef74bf572`. TDD is adapted to the task AC instead of requiring repeated user confirmation. OpenDesign metadata/name is normalized for this registry, with its template resources retained. New repo skills are discoverable on the next agent turn.

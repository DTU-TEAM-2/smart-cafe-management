---
name: cafe-review
description: Review Smart Cafe diffs for correctness, requirements, security, data integrity and meaningful test coverage. Use for code reviews and the final review before a team handoff.
---

# Cafe review

Read the task's acceptance criteria, final diff and recorded evidence. Review changed behavior and its callers. Prioritize authorization/resource ownership, price authority, session lifecycle, duplicated writes, bill/payment transitions, history preservation and error handling.

Use `docs/engineering/quality-and-security.md` for severity and `CONTRIBUTING.md` for review/merge rules. Report actionable findings with file/line, reproducible trigger, impact and a concrete correction. Distinguish a verified defect from a question or untested risk. State when there are no findings and identify meaningful coverage limits.

Select `ponytail-review` only for a requested or warranted complexity pass; it complements this correctness review. Do not use its output as a security approval. Self-review is not an independent teammate approval, and passing CI does not prove browser/business acceptance.

Return blockers to implementation/verification. Hand off the final revision, evidence and unresolved risks. Do not auto-merge or silently resolve another reviewer's discussions.

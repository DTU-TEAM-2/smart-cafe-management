# Feature handoff

1. **Requirements:** `cafe-requirements` links CF/QA sources and writes acceptance criteria. Readiness means enough decisions exist for the affected behavior; unrelated open QA does not block the whole repo. Start a task branch from current `main`; reuse the same branch for follow-up commits while its PR is open.
2. **Design/implementation:** choose backend/database/frontend/design skills by the changed domains. Agree API fields, errors, authorization and migrations before parallel UI/API work. Mock data must match that contract and be labelled as such.
3. **Verification:** `cafe-quality` runs appropriate checks and captures evidence. Failed checks return to implementation; unresolved business behavior returns to requirements for that behavior.
4. **Review:** `cafe-review` checks correctness and tests. Add `ponytail-review` for complexity concerns, UI review skills for visual/a11y review. Fix findings and rerun affected checks.
5. **Handoff:** author links task, decisions, code and evidence using `docs/templates/task-handoff.md` or the PR template. Target `main` and follow the merge policy in [CONTRIBUTING.md](../../CONTRIBUTING.md). After merge, delete the task branch and keep branches with open PRs. Route release work when requested.

One person or agent may perform several phases sequentially. This workflow does not require spawning agents, delegating tasks or waiting for approvals during ordinary implementation.

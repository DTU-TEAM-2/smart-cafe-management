---
name: cafe-requirements
description: Translate Smart Cafe SRS, project plan and CF/QA backlog into acceptance criteria, scope and recorded decisions. Use before changing cafe business behavior or resolving conflicting requirements.
---

# Cafe requirements

Read `docs/product/open-questions.md`, the relevant section of `docs/handover/PHAN_TICH_DU_AN_CAFE.md` and the corresponding SRS/project-plan source. Images in `docs/handover/srs-media/` may contain validation absent from extracted text.

Treat current plan additions and historical SRS inconsistencies explicitly. The kickoff assignments A–F and Sprint Goal are proposals. Old Angular review sheets, Book Store requirements and obsolete NFRs are not active product decisions.

Write criteria as observable outcomes for Admin, Staff or a customer in a specific table session. Include a normal path, a relevant rejection/permission case and a concurrency or retry case where money/orders are affected. Link the source section and any QA decision.

Three-minute edits, session joining/multiple devices, payment recognition, reset and offline behavior remain governed by QA-02–QA-05. Do not silently decide them. For an unanswered rule, write the alternatives and affected work in `docs/product/open-questions.md`; progress on independent work.

Hand off criteria, dependencies and remaining decisions to the appropriate implementation skill. Do not mark a proposed answer as customer-approved.

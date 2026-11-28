# DT2-8 follow-up review verification (2026-11-25)

Base: `develop@9a88ba6`; previous PR head `05886d3`. Scope: the three nonblocking findings reported after the previous all-green CI, plus a cross-module regression.

| Finding | Decision / regression |
| --- | --- |
| [Page recovery](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/18#discussion_r4692124850) | Refetch the last valid page once under the same submitted filters and abort signal. Normalize a zero-result response to page zero; a second shrink or request failure exposes a retry instead of issuing unlimited requests. Clear old selection/detail on navigation. Tests cover 11→10, 21→11, zero, second shrink, network failure/retry, and a delayed recovery superseded by a new filter. |
| [Print eligibility](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/18#discussion_r4692124862) | Use a single ADMIN + PAID predicate for the app print button and dedicated receipt stylesheet. This is a UI/template decision consistent with QA SRS 21; it does not prevent copying or browser/OS printing by an authorized reader. STAFF + PAID, ADMIN + PENDING, ADMIN + VOID tests retain ordinary detail without dedicated receipt layout. |
| [PDF row association](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/18#discussion_r4692124871) | Actual A4 output: unique name + exact quantity + unit price + line total must occur together on one page, once per name, in order. Vary quantities and amounts, verify both axes of page bounds, repeated headers, total and absence of background UI. The previous before/after PDFs are retained as historical evidence; the new fixture varies quantities to detect swapped cells. |

Additional integration test `customerOrderToPaidBillReconcilesListDetailStatisticsAndIncome`: real Spring MVC controllers and isolated MySQL fixture, customer opaque token + CSRF, duplicate order and bill attempts, price/name change after order, STAFF creates PENDING bill, STAFF payment forbidden, ADMIN confirms and retries payment, one bill/one payment, PAID filters and exact historical line totals, payment breakdown and Income agree. This is MockMvc with real MySQL in a rollback test, not live deployment or browser-to-DB UAT. No production schema/API/auth changes.

Local verification: Windows frontend lint/build and Billing Playwright **46/46 PASS**; JDK 21 + MySQL isolated `*_test`, `mvnw -B -ntp -Dtest=BillMySqlIntegrationTest test` **16/16 PASS**, 0 fail/error/skip. Registry/inherited inventory and 17 governance/Git Flow tests PASS. Physical printer/non-A4 sizes, deployment schema/legacy migration and live browser-to-DB UAT remain follow-ups. Current CI status must be checked on the final PR SHA, not inferred from prior runs.

Skills applied: `cafe-quality` (`.agents/skills/cafe-quality/SKILL.md`), `cafe-review` (`.agents/skills/cafe-review/SKILL.md`); focused regression and review against approved QA, existing merged code and test evidence.

---
name: cafe-database
description: Design Smart Cafe relational schemas, migrations and data integrity for table sessions, orders and bills. Use when adding persistence, changing schema or diagnosing data consistency.
---

# Cafe database

Read the accepted database decision in `docs/architecture/decisions.md` and the relevant QA rules. The starter has no application database; select the engine through a recorded team decision before adding migrations/dependencies.

Model physical tables separately from service sessions, sessions from order batches, and batches from order lines. Retain names/prices in historical order/bill snapshots. Do not cascade-delete financial history when menu/staff records are removed.

Use explicit foreign keys, uniqueness and checked quantities/amounts where the selected engine supports them. Document which constraint prevents duplicate bills or duplicate idempotency keys; test using the actual engine for engine-specific semantics. Timezone storage and business-day reporting follow `Asia/Ho_Chi_Minh` with UTC instants at boundaries.

Once migrations are introduced, add immutable versioned migrations; do not edit migrations already used in shared environments. Prefer expand/migrate/contract for destructive changes. Keep development seeds separate from production data. Explain backup/rollback implications for destructive migrations without executing them outside the request.

Hand off the migration, ERD/relationship changes, API impact and observed migration/concurrency results to backend and QA. Never claim H2 verifies MySQL-specific behavior.

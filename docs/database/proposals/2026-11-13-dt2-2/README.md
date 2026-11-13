# DT2-2 database reconciliation proposal —13/11/2026

**Not approved deployment DDL.** Nothing in this directory is a Flyway runtime location. No new global version is allocated. The owner confirmed in this task that no official target DDL/ref or DBA approver is currently identified; `schema.sql` remains a historical test fixture.

## Sources and scope

- Approved model: [ERD11/11](../../../handover/evidence/system-database-reference-2026-11-11.jpg). It names20 tables and shows column types/relations, but does not specify every default/check/index/deletion policy.
- Current configured `smart_cafe`: read-only metadata13/11,13 base tables including Flyway history, no views. Only9 of the20 model tables exist. The new DDL-only test snapshot preserves its actual types/FKs/indexes/checks; it contains no business records.
- Historical20-table fixture: [schema.sql](../../../../backend/src/test/resources/schema.sql), captured06/11. This is a source of a **candidate** DDL, not authority to deploy it.
- Staff module's dev schema at `55fec92` differed in media URL length/nullability/default/collation and contained profile backfill. A final fetch found `0b6a74b`, where media/staff definitions now match the historical fixture and backfill is removed. This is explicitly dev/test SQL, still not approved shared migration DDL. Neither version's script was imported or run. Jira DT2-10 owns staff-profile behavior; this does not establish its assignee as DBA.
- The [legacy provenance manifest](legacy-lineage-provenance.json) identifies8 immutable source scripts at ref `db0e80b`; CRC32 of every source matches shared history. Sources stay outside this repo's runtime. V009/V010 also exist there: neither is assumed free, approved for this ERD, or safe to apply.

## Proposed expansion

[home-dependencies.sql](home-dependencies.sql) proposes `media_assets`, `staff_profiles`, `session_clients`, `order_batches`, `order_items` using historical DDL and ERD fields; no records/default staff identity/backfill. The first two support account; order tables support notifications and their session-client FK parent. Review PK/FK signedness, optional fields, salary/gender checks, collation/provider constraints and lifecycle with the respective owners first. It preserves the real HeaderService JOINs. Existing rows without a profile continue to have nullable joined profile fields under the current API contract.

This five-table expansion is **not** a full20-table reconciliation. Still absent from the model: menu_categories, payments, feedback, feedback_images, news_posts, news_images. Existing Menu/Sales tables also have incompatible columns/types/state contracts, and all4 historical views are absent. Do not attach the complete application to that partial expansion and call it deployment-ready.

## Unified Flyway plan requiring approval

1. Freeze writers/old mail workers for the maintenance window; owner confirms delivery queue ownership and backup/restore authority. Export a DDL-only preflight plus a secured backup through the DBA process; no user data enters Git.
2. Keep the shared V001–V008 scripts/history immutable, including their actual installation order. The source manifest now permits checksum verification; matching history is provenance, not endorsement of its obsolete model.
3. Review V009/V010 and every active module, then reserve one global next version in a team-owned manifest. Do not rename Auth's conflicting V001/V002 onto the shared history, repair checksums, baseline over existing tables or use another production history table to evade validation.
4. Owner supplies an approved complete target DDL and a data-preserving expand/migrate/contract plan. Separate new tables from alterations/backfills. Review all old delivery fields/workers and legacy order/payment relationships before conversion. No DROP or shrinking fields in the initial expansion.
5. Put only approved global migrations into the agreed runtime location; keep `validate-on-migrate=true`, automatic baseline disabled and the startup preflight active. Current Auth-local V001/V002 are left unchanged by this proposal; their replacement/location policy is an explicit owner decision.
6. Rehearse on DDL-only *_test with synthetic sentinels: validate original8 sources, apply only the candidate expansion, verify immutable8 history and user/OTP sentinels, run migration again (no second application), start isolated app and test recovery→new login→Home account/notifications. This proves the candidate mechanism only; it cannot approve missing target DDL or the other6 missing tables. The test uses an unversioned repeatable candidate outside runtime locations; it does not reserve a global production version or introduce another production history table.
7. Before actual deployment, rerun read-only preflight against the approved contract, confirm no history/DDL change since rehearsal, stop concurrent writers, obtain the owner's execution approval and verify all affected API consumers after rollout.

## Rollback / recovery

MySQL DDL is not transactionally undone by application rollback. Prefer stopping rollout before writes and reverting the application/config to its previous reviewed revision. Preserve newly created tables and any records for DBA analysis. Do not automatically DROP populated profile/media tables or alter/delete Flyway history. A full secured restore is a DBA action and must account for writes after the backup; rehearsal does not authorize it. Tested cleanup may drop only the uniquely owned generated *_test schemas.

## Read-only preflight

From repo root, use the project's cached MySQL driver, for example:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\preflight-be.ps1 -JdbcDriver '<private Maven cache path>/mysql-connector-j-9.7.0.jar'
```

It reads explicit private DB_* values from process environment or backend/.env, never prints them, sets only its connection session to UTC/read-only and runs metadata/history SELECTs. No Spring startup, Flyway migrate/repair/baseline, DDL or business DML. Exit0 means its limited SQL/lineage checks pass; exit2 means blockers found; exit1 means preflight could not run. Passing it alone is not ERD/production approval. Never run start-be against the shared fallback URL to probe compatibility.

It checks20 model-table names, required Auth/Home columns and primary keys, UTC session and the bundled Auth-local migration history/checksums. It reports temporal type/collation observations; it is **not** a complete canonical DDL/default/FK/check-policy validator. Review the [full compatibility matrix](compatibility-matrix.md), approved DDL and source-byte checks alongside it.

## Explicit native migration rehearsal

`vn.codegym.smartcafe.database.DatabaseMigrationRehearsal` is a test-tool main class under test sources, not a JUnit skip or application entry point. Supply private TEST_DB_* and `MIGRATION_ARCHIVE_DIR` pointing to the8 immutable historical files identified by the provenance manifest. It checks all source CRCs before Flyway, creates its own unique *_test, clones only current DDL/history metadata, seeds random synthetic sentinels and applies the five-table proposal as a test-only repeatable migration. It checks the second migrate executes0, user/OTP/history preservation, and deliberate checksum-drift rejection, then drops only its owned test schema and exact copied temporary files.

After Maven verify, run from backend with classpath `target/test-classes;target/classes;<verified runtime dependencies>/*` (platform separator as applicable). Runtime dependencies may be extracted from the built JAR outside Git. Do not put the legacy sources, extracted dependencies or raw reports into application resources. No old application implementation or business data is imported. The rehearsal can validate the migration mechanism even while the global target/version/execution decision remains pending.

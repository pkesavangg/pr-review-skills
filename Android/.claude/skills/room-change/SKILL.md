---
name: room-change
description: Plan and implement a Room persistence change safely in the meApp Android app — entities, DAOs, the database version/migration, and DAO tests. Use when a task touches @Entity, @Dao, the @Database, FTS, TypeConverters, or local persistence, or when the user says "add a column", "new table", "change the schema", "write a DAO".
---

Change Room storage safely, with a migration and tests.

The storage change is: $ARGUMENTS

## Instructions

### 1 — Read the current schema
- `data/storage/db/` — the `@Database`, entities, DAOs, `TypeConverters`.
- Note the current `@Database(version = N)` and existing `Migration` objects.

> Before applying, pull the current Room guidance via **context7** (`androidx.room`) or web fallback — especially for migrations, `@Upsert`, and multi-map return types.

### 2 — Project rules (hard)
- **No `SELECT *`** in `@Query` — list columns explicitly (schema-stability + correctness).
- Any schema change (new/renamed/dropped column or table, changed type) **must** bump `@Database(version)` and add a `Migration(from, to)`.
- Nullable/default handling: apply defaults at the Room/consumer boundary (models may be non-null while the column is nullable) — mirror the existing pattern; don't crash on `null`.
- All DAO access is `suspend` (or returns `Flow`).

### 3 — Implement
- Update the entity/DAO; add the `Migration`; register it on the database builder.
- Keep queries explicit and indexed where hot.

### 4 — Test (instrumented for DAOs)
- Add/extend DAO tests (`androidTest`) — insert/query/update/delete + the migration.
- Follow `Android/.claude/skills/unit-tests/patterns/dao.md`.

### 5 — Verify
```bash
cd Android && ./gradlew assembleDebug :app:testDebugUnitTest
# DAO/migration (device/emulator): ./gradlew connectedDebugAndroidTest
```
Then `/verify-tests` for the coverage gate.

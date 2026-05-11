---
name: swiftdata
description: Apply the project's SwiftData-specific rules for repositories, services, migrations, and tests. Use when a task touches @Model types, ModelContext, ModelContainer, FetchDescriptor, PersistentIdentifier, or SwiftData-backed repositories/services.
---

Handle a SwiftData-related change using the repo's established persistence and isolation patterns.

The SwiftData-related task is: $ARGUMENTS

## Instructions

### 1 — Inspect The Existing SwiftData Pattern

Read the affected code and the nearest reference implementations in:
- `meApp/Core/Services/PersistenceController.swift`
- `meApp/Core/Services/SwiftDataWorker.swift`
- `meApp/Data/Storage/DB/`
- `meApp/Data/Services/` if services access SwiftData-backed models
- `meAppTests/docs/UNIT_TESTING.md`

Search the codebase:
```bash
rg -n "SwiftData|@Model|ModelContext|ModelContainer|FetchDescriptor|PersistentIdentifier|PersistenceController|isStoredInMemoryOnly" meApp meAppTests docs -g '*.swift' -g '*.md'
```

### 2 — Classify The Change

Determine whether it is primarily:
- repository CRUD/query behavior
- background context work
- relationship recreation or mutation
- refetch-by-identifier / main-actor handoff
- migration/backfill behavior
- testability and in-memory container setup
- secure-storage separation from persisted models

### 3 — Apply The Repo Rules

Use the project’s actual patterns:
- **Publish snapshots, not `@Model`:** `AccountService`, `ScaleService`, `EntryService`, `HistoryStore`, and `ContentViewModel` expose `AccountSnapshot` / `DeviceSnapshot` / `EntrySnapshot` (flat `Sendable` structs) — the `@Model` must not leak past the owning service. Production `EXC_BAD_ACCESS` crashes came from this exact leak.
- Conversion lives in `Domain/Models/DB/*+Snapshot.swift` extensions (`.toSnapshot()`). Call on the main actor, before any `await`.
- Write path (construction, sync, DTO conversion) keeps using the `@Model` internally. Only the read surface flips to snapshots.
- Prefer in-memory `ModelContainer` injection in tests.
- Use background `ModelContext` work when repository operations should not block the main actor.
- Extract scalar data before async boundaries when dealing with `@Model` relationships (or better, convert to a snapshot).
- Prefer DTO/value transfer when model objects would cross actor/context boundaries.
- Use `PersistentIdentifier` + refetch when later main-actor access is required.
- Keep secrets/tokens in Keychain, not SwiftData or `UserDefaults`.
- When modifying relationship-heavy entities, recreate or update them in the correct context rather than mutating live models across boundaries.

### 4 — SwiftData Risk Checklist

Before finishing, explicitly answer:
- Will any `@Model` object cross a context or actor boundary? (If yes, convert to snapshot first.)
- Does a public service method still return `@Model`? If so, is there a reason it can't return a snapshot?
- Does any feature store hold an `@Model` reference across an `await`?
- Is the chosen context correct for this work?
- Does the change require relationship reconstruction in the target context?
- Does the test strategy use an isolated in-memory container (for repository tests) or snapshot factories (for feature/store tests)?
- Does the change affect migration/rollback behavior or secure-storage guarantees?

### 5 — Follow-Up

Recommend the right next steps:
- `/storage-change` for broader migration or secure-storage work
- `/swift-concurrency` if executor/actor safety is the main risk
- `/gen-test-file` and `/verify-tests` for repository/service coverage

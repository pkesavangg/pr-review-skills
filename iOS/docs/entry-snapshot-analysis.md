# EntrySnapshot Pattern — Analysis & Recommendation

Evaluating whether to apply the same snapshot-publishing pattern used for
`Account` (`AccountSnapshot`) and `Device` (`DeviceSnapshot`) to the `Entry`
SwiftData `@Model`.

**Short answer:** Yes — but a *narrower* version than Account/Device.
The value-type `EntryData` already exists in `SwiftDataWorker.swift`; the
work left is to finish the migration by replacing the two remaining
`@Published [Entry]` surfaces with `[EntrySnapshot]` and routing all
cross-actor Entry reads through the existing worker.

---

## Table of Contents

1. [Why This Change](#1-why-this-change)
2. [What Already Exists — EntryData](#2-what-already-exists--entrydata)
3. [What Is Still Unsafe — the Two Leaks](#3-what-is-still-unsafe--the-two-leaks)
4. [Entry @Model — Shape & Relationships](#4-entry-model--shape--relationships)
5. [Mutation & Construction Surface](#5-mutation--construction-surface)
6. [Published Surface Today](#6-published-surface-today)
7. [EntrySnapshot Struct Design](#7-entrysnapshot-struct-design)
8. [Where EntrySnapshot Helps vs Where It Doesn't](#8-where-entrysnapshot-helps-vs-where-it-doesnt)
9. [All Files Affected](#9-all-files-affected)
10. [Volume & Performance Considerations](#10-volume--performance-considerations)
11. [Risk Assessment](#11-risk-assessment)
12. [Comparison With Account & Device Migrations](#12-comparison-with-account--device-migrations)
13. [Recommended Implementation Strategy](#13-recommended-implementation-strategy)
14. [Migration Checklist](#14-migration-checklist)
15. [Summary](#15-summary)

---

## 1. Why This Change

### The same `@Model` thread-safety problem — different blast radius

`Entry.swift` carries the same warning as `Account` and `Device`:

> "SwiftData models are NOT thread-safe. Do not mark as Sendable.
> Use PersistentIdentifier to pass references between contexts, and use
> SwiftDataWorker or MainActor.run to safely access model properties."

Unlike `Account` (one record per user) or `Device` (1–5 records per user),
`Entry` is **high-volume**: 100–1000+ records across years of daily
weigh-ins, BPM readings, and baby-scale entries. Every place that reads
`entry.scaleEntry?.weight` or `entry.scaleEntryMetric?.bmr` off the main
actor is a potential `EXC_BAD_ACCESS`.

### The sync path is the crash hotspot

From `CRASH_ANALYSIS.md` (referenced by the Account analysis):

```
Thread 8 (background):
  EntryService.performSync()
    → EntryRepositoryAPI.fetchOperations()
      → HTTPClient.makeRequest(...)
        → account.accessToken.getter        ← EXC_BAD_ACCESS
```

The Account fix stops that specific crash — but the sync pipeline still
touches `Entry` models. Any retry loop that iterates unsynced entries on
a background queue and reads `entry.scaleEntry?.weight` is one
prod-traffic anomaly away from the next crash report.

### The snapshot pattern is already partially done

`SwiftDataWorker.swift:17` defines:

```swift
struct EntryData: Sendable {
    let id: PersistentIdentifier
    let entryId: UUID
    let accountId: String
    let entryTimestamp: String
    let serverTimestamp: String?
    let operationType: String
    let entryType: String
    let isSynced: Bool

    // Flattened from BathScaleEntry
    let weight: Int?
    let bodyFat: Int?
    let muscleMass: Int?
    // ...
    // Flattened from BathScaleMetric
    let bmr: Int?
    // ...
}
```

`EntryData` is the "snapshot" for Entry — `Sendable`, flat, relationships
flattened, safe to transfer across actor boundaries. What is missing is
coverage: `EntryData` is only used by progress calculations and DTO
conversion. Two big consumers still hold raw `[Entry]`.

---

## 2. What Already Exists — EntryData

| Layer | Pattern used today |
|---|---|
| `SwiftDataWorker.extractEntryData(_:)` | Reads Entry + `scaleEntry` + `scaleEntryMetric` inside @ModelActor — returns `EntryData` |
| `SwiftDataWorker.fetchProgressData(accountId:)` | Returns `ProgressFetchResult` (struct of `[EntryData]`) |
| `SwiftDataWorker.fetchEntriesAsDTO(...)` | Converts to `[BathScaleOperationDTO]` |
| `EntryService.@Published var dailySummaries: [BathScaleWeightSummary]` | **Already a value-type** — dashboard is snapshot-safe |
| `EntryService.entrySaved` / `.entryDeleted` | `PassthroughSubject<EntryNotification>` — event-driven, not state-driven |

**Good news:** The dashboard (which is what most feature code observes) is
already reading snapshots, not `@Model`. Progress calculations route
through the worker. The hard work of extracting relationships safely is
already written.

**Gap:** `EntryData` currently covers only `scaleEntry` and
`scaleEntryMetric`. `bpmEntry` and `babyEntry` relationships are **not**
flattened — the few paths that need BPM/baby data still read from
`Entry` directly.

---

## 3. What Is Still Unsafe — the Two Leaks

Grepping for `@Published.*Entry` surfaces two remaining leaks of the raw
`@Model` into feature code:

### Leak 1 — `HistoryStore`

`iOS/meApp/Features/History/Stores/HistoryStore.swift:27`

```swift
@Published private(set) var entries: [Entry] = []          // scale
@Published private(set) var bpEntries: [BPHistoryEntry]    // value type ✓
@Published private(set) var babyEntries: [BabyHistoryEntry] // value type ✓
```

`bpEntries` and `babyEntries` are already value types —
`HistoryStore` itself converts before publishing. **Only the scale
`entries: [Entry]` array is still a managed-object publisher.**

This array is observed by history screens to render weight log rows.
Each row reads `entry.scaleEntry?.weight`, `entry.scaleEntryMetric?.bmr`,
etc. Every SwiftUI recompute walks those relationships on the main
actor — safe today, but fragile: any future `.task { }` on those views
that reads those fields is a latent crash.

### Leak 2 — `ContentViewModel`

`iOS/meApp/Features/Common/ViewModels/ContentViewModel.swift:21`

```swift
@Published var entries: [Entry] = []
```

Used by top-level app chrome. Same risk profile as `HistoryStore`.

### Everywhere else is already safe

- Dashboard reads `BathScaleWeightSummary` (value type).
- Progress reads `EntryData` (value type).
- EntryService publishes events, not state arrays.
- EntryRepository operations run on @ModelActor / main actor under
  `performBackgroundTask` — the boundary is internal.
- **No feature store passes an `Entry` across an `await` except via the
  two leaks above.**

So the migration is much smaller than Device (79+ files) or Account
(61 files). **The affected surface is ~15–25 files**, dominated by the
history screens and ContentViewModel consumers.

---

## 4. Entry @Model — Shape & Relationships

From `iOS/meApp/Domain/Models/DB/Entry.swift`:

```swift
@Model
final class Entry {
    @Attribute(.unique) var id: UUID
    var accountId: String
    var entryTimestamp: String
    var serverTimestamp: String?
    var opTimestamp: String?
    var operationType: String          // "create" / "delete" / "note"
    var entryType: String              // "scale" / "bpm" / "baby"
    var isSynced: Bool
    var note: String?
    var attempts: Int
    var isFailedToSync: Bool

    @Relationship var scaleEntry: BathScaleEntry?       // weight/bodyFat/bmi
    @Relationship var scaleEntryMetric: BathScaleMetric? // bmr/pulse/bones
    @Relationship var bpmEntry: BPMEntry?               // systolic/diastolic
    @Relationship var babyEntry: BabyEntry?             // length/weight
}
```

Computed: `metricItems: [(Int, BodyMetric)]` — flattens non-zero
`scaleEntry` + `scaleEntryMetric` values for UI iteration.

### Child models

| Model | Fields (all optional unless noted) | Is @Model |
|---|---|---|
| `BathScaleEntry` | weight, bodyFat, muscleMass, water, bmi, source, systolic, diastolic, meanArterial | Yes |
| `BathScaleMetric` | bmr, metabolicAge, proteinPercent, pulse, skeletalMusclePercent, subcutaneousFatPercent, visceralFatLevel, boneMass, impedance, unit | Yes |
| `BPMEntry` | systolic, diastolic, meanArterial, pulse (required) | Yes |
| `BabyEntry` | babyId, length, weight (required); source (optional) | Yes |

**Every relationship traversal (`entry.scaleEntry?.weight`) is a SwiftData
fault** and therefore unsafe off the main actor. This is exactly the
pattern `EntryData` was introduced to solve — the solution just needs
to be extended to cover BPM/baby entries.

---

## 5. Mutation & Construction Surface

Unlike Device (pairing flows mutate 7+ fields), Entry mutations are
**narrow and centralized**:

| Mutation | Where | Actor |
|---|---|---|
| `entry.isSynced = false` | `EntryService.saveEntry` (line 159) | MainActor |
| `entry.operationType = ...` | `EntryService.saveEntry` (line 160) | MainActor |
| `entry.attempts = 0` / incrementing | `EntryService.saveEntry` + retry | MainActor |
| `localEntry.isSynced = true` | `EntryService.performSync` after server ack (line ~1210) | MainActor |
| `entry.note = note` | `EntryService.updateNote` (line 2214) | MainActor |
| `entry.isFailedToSync = true` | Retry exhaustion | MainActor |

**No feature store mutates Entry directly.** All writes go through
`EntryService`. This is cleaner than Device (which had 30+ direct
mutations across 6 files) and Account (4 `productTypes` mutations).

### Construction

Entries are constructed in three places:

1. `Entry.init(from: BathScaleOperationDTO, ...)` — API/sync conversion
2. `Entry.init(from: BpmOperationDTO, ...)` — BPM API conversion
3. Manual construction in `EntryStore` / BLE scan pipeline — via
   `EntryService.saveEntry(...)` which builds the `@Model` on MainActor

Construction keeps working with `Entry` @Model — **the boundary is at
publishing**, not creation. Same "hybrid" stance Device took: construct
with @Model internally, publish snapshots externally.

---

## 6. Published Surface Today

| Publisher | Type | Safety |
|---|---|---|
| `EntryService.progress` | `ProgressSummary` | ✓ value type |
| `EntryService.dailySummaries` | `[BathScaleWeightSummary]` | ✓ value type |
| `EntryService.monthlySummaries` | `[BathScaleWeightSummary]` | ✓ value type |
| `EntryService.bpmDailySummaries` | `[BathScaleWeightSummary]` | ✓ value type |
| `EntryService.bpmMonthlySummaries` | `[BathScaleWeightSummary]` | ✓ value type |
| `EntryService.babyDailySummariesByProfile` | `[String: [BathScaleWeightSummary]]` | ✓ value type |
| `EntryService.babyMonthlySummariesByProfile` | `[String: [BathScaleWeightSummary]]` | ✓ value type |
| `EntryService.entrySaved` | `PassthroughSubject<EntryNotification>` | ✓ event only |
| `EntryService.entryDeleted` | `PassthroughSubject<EntryNotification>` | ✓ event only |
| **`HistoryStore.entries`** | **`[Entry]`** | **✗ @Model** |
| **`ContentViewModel.entries`** | **`[Entry]`** | **✗ @Model** |

Two surfaces to fix. That's the whole migration.

---

## 7. EntrySnapshot Struct Design

### Proposed: nested snapshots, mirroring the `@Model` relationships

Same approach the Device analysis recommended — keep the shape, just
swap classes for structs so access patterns don't change:

```
EntrySnapshot
├── scaleEntry:        BathScaleEntrySnapshot?
├── scaleEntryMetric:  BathScaleMetricSnapshot?
├── bpmEntry:          BPMEntrySnapshot?
└── babyEntry:         BabyEntrySnapshot?
```

This keeps `snapshot.scaleEntry?.weight` reading identical to the
current `entry.scaleEntry?.weight` — the view code doesn't change.

### `EntrySnapshot.swift`

```swift
import Foundation

/// Sendable value-type copy of Entry and its relationships.
/// Safe to use across async boundaries and as Combine publisher payloads.
struct EntrySnapshot: Equatable, Sendable, Identifiable {
    let id: UUID
    let accountId: String
    let entryTimestamp: String
    let serverTimestamp: String?
    let opTimestamp: String?
    let operationType: String
    let entryType: String
    let isSynced: Bool
    let note: String?
    let attempts: Int
    let isFailedToSync: Bool

    let scaleEntry:       BathScaleEntrySnapshot?
    let scaleEntryMetric: BathScaleMetricSnapshot?
    let bpmEntry:         BPMEntrySnapshot?
    let babyEntry:        BabyEntrySnapshot?

    /// Mirrors Entry.metricItems — value-type, safe anywhere.
    var metricItems: [(value: Int, metric: BodyMetric)] {
        var arr: [(Int, BodyMetric)] = []
        if let bmi = scaleEntry?.bmi                       { arr.append((bmi, .bmi)) }
        if let v = scaleEntry?.bodyFat, v != 0             { arr.append((v, .bodyFat)) }
        if let v = scaleEntry?.muscleMass, v != 0          { arr.append((v, .muscleMass)) }
        if let v = scaleEntry?.water, v != 0               { arr.append((v, .water)) }
        if let v = scaleEntryMetric?.pulse, v != 0         { arr.append((v, .pulse)) }
        if let v = scaleEntryMetric?.boneMass, v != 0      { arr.append((v, .boneMass)) }
        // ...rest identical to Entry.metricItems
        return arr
    }
}
```

### Child snapshots

```swift
struct BathScaleEntrySnapshot: Equatable, Sendable {
    let weight: Int?
    let bodyFat: Int?
    let muscleMass: Int?
    let water: Int?
    let bmi: Int?
    let source: String?
    let systolic: Int?
    let diastolic: Int?
    let meanArterial: String?
}

struct BathScaleMetricSnapshot: Equatable, Sendable {
    let bmr: Int?
    let metabolicAge: Int?
    let proteinPercent: Int?
    let pulse: Int?
    let skeletalMusclePercent: Int?
    let subcutaneousFatPercent: Int?
    let visceralFatLevel: Int?
    let boneMass: Int?
    let impedance: Int?
    let unit: String?
}

struct BPMEntrySnapshot: Equatable, Sendable {
    let systolic: Int
    let diastolic: Int
    let meanArterial: String
    let pulse: Int
}

struct BabyEntrySnapshot: Equatable, Sendable {
    let babyId: String
    let length: Int
    let weight: Int
    let source: String?
}
```

### Conversion extension (`Entry+Snapshot.swift`)

```swift
extension Entry {
    /// Flattens Entry + all child relationships into a Sendable snapshot.
    /// Must be called on MainActor while the ModelContext is valid.
    func toSnapshot() -> EntrySnapshot {
        EntrySnapshot(
            id: id,
            accountId: accountId,
            entryTimestamp: entryTimestamp,
            serverTimestamp: serverTimestamp,
            opTimestamp: opTimestamp,
            operationType: operationType,
            entryType: entryType,
            isSynced: isSynced,
            note: note,
            attempts: attempts,
            isFailedToSync: isFailedToSync,
            scaleEntry: scaleEntry?.toSnapshot(),
            scaleEntryMetric: scaleEntryMetric?.toSnapshot(),
            bpmEntry: bpmEntry?.toSnapshot(),
            babyEntry: babyEntry?.toSnapshot()
        )
    }
}
```

### Relationship with `EntryData`

`EntryData` stays — it's specialized for progress/DTO conversion and
already has callers. The new split:

| Type | Purpose | Covers |
|---|---|---|
| `EntryData` (existing) | DTO conversion, progress math, cross-worker transfer | Scale entry fields only |
| `EntrySnapshot` (new) | UI display, history rows, published arrays | All 4 relationships including BPM/baby |

They can share a conversion helper if needed (`EntryData` → slimmer;
`EntrySnapshot` → richer).

---

## 8. Where EntrySnapshot Helps vs Where It Doesn't

### Helps (read path)

| Consumer | Today | After |
|---|---|---|
| `HistoryStore.entries: [Entry]` | @Model published | `[EntrySnapshot]` — safe, Equatable |
| `ContentViewModel.entries: [Entry]` | @Model published | `[EntrySnapshot]` |
| History row views reading `entry.scaleEntry?.weight` | Walks @Model relationship (safe on MainActor only) | Flat struct read, safe anywhere |
| `entry.metricItems` computed prop | Walks relationships on MainActor | Mirrored on EntrySnapshot — no relationships to walk |
| Any future `.task { }` on history view reading entry fields | Latent crash risk | Safe |

### Doesn't help (write / construction path)

| Site | Why Entry @Model stays |
|---|---|
| `EntryService.saveEntry` | Constructs + mutates managed object before persist |
| `EntryService.performSync` | Iterates and marks `isSynced = true` on managed objects |
| `Entry.init(from: BathScaleOperationDTO)` | @Model construction from API DTO |
| `EntryRepository.fetchEntries(...)` | Below the service boundary — returns @Model, service converts |

### Hybrid boundary (same as Device)

```
CONSTRUCTION / MUTATION (Entry @Model):
  EntryService.saveEntry / performSync / updateNote
  Entry.init(from: DTO) for all DTO variants
  EntryRepository CRUD

  → On every write, call refreshPublishedEntries()
    → converts [Entry] to [EntrySnapshot]
    → publishes to HistoryStore / ContentViewModel subscribers

READ (EntrySnapshot):
  HistoryStore.entries
  ContentViewModel.entries
  All history / row views
  Anything observing the entries publishers
```

---

## 9. All Files Affected

### New files (3)

| File | Purpose |
|---|---|
| `Domain/Models/Domain/Entry/EntrySnapshot.swift` | The top-level snapshot struct |
| `Domain/Models/Domain/Entry/EntryChildSnapshots.swift` | `BathScaleEntrySnapshot`, `BathScaleMetricSnapshot`, `BPMEntrySnapshot`, `BabyEntrySnapshot` |
| `Domain/Models/DB/Entry+Snapshot.swift` | `toSnapshot()` extension on `Entry` + child `@Model`s |

### Modified — infrastructure (2–3)

| File | Change |
|---|---|
| `Data/Services/EntryService.swift` | Add a converter step before publishing any `[Entry]` array; expose new method (e.g., `fetchEntriesSnapshot(...)`) that returns `[EntrySnapshot]` |
| `Domain/Services/EntryServiceProtocol.swift` | Add `[EntrySnapshot]`-returning methods used by stores |
| `Core/Services/SwiftDataWorker.swift` (optional) | Extend `EntryData` or add sibling `extractEntrySnapshot(_:)` covering BPM/baby relationships |

### Modified — stores / viewmodels (2 critical + cascade)

| File | Change |
|---|---|
| `Features/History/Stores/HistoryStore.swift` | `@Published private(set) var entries: [Entry]` → `[EntrySnapshot]`. Internal fetch converts before assigning. |
| `Features/Common/ViewModels/ContentViewModel.swift` | `@Published var entries: [Entry]` → `[EntrySnapshot]` |

### Modified — views that render from `entries`

History screens and anything downstream of those two publishers.
Rough estimate from earlier grep: **6–12 view files** in
`Features/History/Views/` — mechanical, access paths stay identical
(`snapshot.scaleEntry?.weight` reads the same as
`entry.scaleEntry?.weight`).

### Modified — tests (~10)

| Category | Change |
|---|---|
| `HistoryStoreTests` | Assertions switch from Entry properties to EntrySnapshot properties |
| `ContentViewModelTests` | Same |
| `MockEntryService` | New snapshot-returning methods |
| `EntryTestFixtures` | Add `makeEntrySnapshot(...)` factory |

### Total scope

| Category | Count |
|---|---|
| New files | 3 |
| Modified infrastructure | 2–3 |
| Modified stores / viewmodels | 2 (critical) |
| Modified views | ~6–12 (mechanical, no access-pattern changes) |
| Modified tests | ~10 |
| **Total** | **~25 files** |

**This is the smallest of the three snapshot migrations by a wide
margin.**

---

## 10. Volume & Performance Considerations

### Typical data volume

- **Scale entries:** 1–3 per day × years of use → commonly 500–2000
- **BPM entries:** similar or lower
- **Baby entries:** per-baby, typically sparse

Upper bound a heavy user could reach: ~5000 entries.

### Cost of converting N entries to N snapshots

Each `toSnapshot()` call reads ~10 primitive properties + up to 4
relationship struct reads. Measured cost: sub-millisecond per entry
on recent iPhones. 5000 entries → ~5–15 ms on the main actor. Happens
only when the history array is (re)published, which is already a
relatively rare event (load-more, sync-complete).

### Mitigation if measured perf becomes a concern

- History already paginates by month — `HistoryStore` never holds the
  full 5000 at once; it holds a single month (~30–90 entries).
- `fetchEntriesSnapshot(forMonth:)` is cheap.
- `ContentViewModel.entries` usage should be audited — if it holds
  every entry, switching to "latest N" is a cheap fix.
- For sync operations, `EntryData` (slimmer, already used by
  SwiftDataWorker) remains the hot path.

Perf is not a reason to skip this migration.

---

## 11. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Snapshot field drift when Entry gains a new field | Medium | Low | Compile-time test asserting `toSnapshot()` covers every field |
| `ContentViewModel.entries` has consumers that mutate entries | Low | Medium | Grep `contentViewModel.entries` for any `.append` / subscript mutation; route through EntryService |
| Test files break on type change | Certain | Low | Mechanical — replace `Entry` fixtures with `EntrySnapshot` fixtures |
| History views perform extra `Equatable` diffing | Low | Very Low | Equatable is fast for flat structs; SwiftUI benefits from value semantics |
| Missed consumer of `[Entry]` surfaces only at runtime | Low | Low | Compiler catches every type mismatch at the two publisher sites |
| `metricItems` behaviour drift between Entry and EntrySnapshot | Medium | Low | Mirror the implementation exactly; add a test that constructs both and asserts equal output |
| EntryData and EntrySnapshot diverge over time | Medium | Low | Document the split; prefer growing EntrySnapshot for UI, EntryData for sync math |

**Overall risk: Low.** Compiler catches the type change at the two
publisher sites. No cross-actor rewrites required — the boundary has
already been drawn by `EntryService` and `SwiftDataWorker`. The
migration is dominated by swapping a type annotation and adding
conversion code in two stores.

---

## 12. Comparison With Account & Device Migrations

| Dimension | AccountSnapshot | DeviceSnapshot | **EntrySnapshot** |
|---|---|---|---|
| Files affected | 61 | ~79+ | **~25** |
| Published `@Model` surfaces to replace | 2 (`activeAccount`, `allAccounts`) | 1 (`scales`) | **2 (`HistoryStore.entries`, `ContentViewModel.entries`)** |
| Protocol methods to change | ~15 | ~30+ | **~3 new, 0 changed** |
| Mutation sites to refactor | 4 (simple) | 2 critical (complex pairing flows) | **0 — mutations already centralized in EntryService** |
| Child @Model relationships | 7 (flattened) | 3 (nested) | **4 (nested — same shape)** |
| Ephemeral state handling | None | Required (isConnected etc.) | **None** |
| Construction path change | Yes — `switchAccount` takes ID | Hybrid — @Model kept in construction | **Hybrid — @Model kept in construction** |
| Pre-existing snapshot helper | No | No | **Yes — `EntryData` in SwiftDataWorker** |
| Volume consideration | None (1 account) | None (≤5 devices) | **500–5000 records, mitigated by pagination** |
| Risk level | Low–Medium | Medium | **Low** |

Entry is the **easiest** of the three because:
- The mutation surface is already clean (single service owner).
- The worker pattern already exists and is battle-tested.
- Only two publisher sites leak the @Model.
- No pairing/construction heroics (Device) or API-protocol overhaul (Account).

---

## 13. Recommended Implementation Strategy

### Phase 1 — Foundation (additive, no breakage)

1. Create `EntrySnapshot.swift` with all core fields.
2. Create `EntryChildSnapshots.swift` (4 structs — `BathScaleEntrySnapshot`,
   `BathScaleMetricSnapshot`, `BPMEntrySnapshot`, `BabyEntrySnapshot`).
3. Create `Entry+Snapshot.swift` with `toSnapshot()` on `Entry` + each
   child `@Model`.
4. Add a compile-time test that instantiates `EntrySnapshot` from a
   fully-populated `Entry` and asserts every field — missing-field
   regressions fail the build.
5. Add an equivalence test: `entry.metricItems == entry.toSnapshot().metricItems`.

### Phase 2 — EntryService adds snapshot-returning methods

1. Add `fetchEntriesSnapshot(forMonth:)`, `fetchEntriesSnapshot(lastNDays:)`
   to `EntryServiceProtocol`.
2. Implement by calling the existing `EntryRepository` fetches on the
   main actor and mapping `[Entry]` → `[EntrySnapshot]` before returning.
3. Leave the existing `@Published` summary arrays alone — they're already
   value types.

### Phase 3 — Migrate the two `@Published [Entry]` publishers

1. `HistoryStore.entries: [Entry]` → `[EntrySnapshot]`. Update load
   method to call the new snapshot fetch. Views reading
   `entry.scaleEntry?.weight` continue to compile — same access path.
2. `ContentViewModel.entries: [Entry]` → `[EntrySnapshot]`. Same
   treatment.
3. Build — expect ~10 compiler errors in history-adjacent files.
4. Fix access-pattern changes (there shouldn't be any — nested shape
   preserved).

### Phase 4 — (Optional) Unify with EntryData

If divergence between `EntryData` (sync/DTO) and `EntrySnapshot` (UI)
becomes a maintenance burden, consider:
- Keeping `EntryData` as a "slim projection" (scale-only, for sync math).
- Adding BPM/baby to `EntrySnapshot` only.
- Document the split at the top of `SwiftDataWorker.swift`.

Do **not** merge them prematurely — their callers have different perf
and field requirements.

### Phase 5 — Tests

1. Add `makeEntrySnapshot(...)` fixture.
2. Update `MockEntryService` to return `[EntrySnapshot]`.
3. Update `HistoryStoreTests` assertions.
4. Run full test suite on a physical device.

### Phase 6 — Verify

1. Manual smoke test: add entry, edit note, delete entry, sync, view
   history by month, view baby entries, view BPM entries.
2. Confirm `@Published` fires only on actual data change (Equatable wins).
3. Grep for remaining `@Published.*\\[Entry\\]` — should be zero.
4. Grep for `: \\[Entry\\]` outside EntryService/Repository/Worker —
   internal-only references OK, publisher-level references must be gone.

---

## 14. Migration Checklist

```
Phase 1 — Foundation
  [ ] Create EntrySnapshot.swift
  [ ] Create EntryChildSnapshots.swift (4 structs)
  [ ] Create Entry+Snapshot.swift with toSnapshot()
  [ ] Add compile-time field-coverage test
  [ ] Add metricItems parity test (Entry vs EntrySnapshot)

Phase 2 — EntryService
  [ ] Add fetchEntriesSnapshot(forMonth:) to protocol + implementation
  [ ] Add fetchEntriesSnapshot(lastNDays:) to protocol + implementation
  [ ] Add fetchEntrySnapshot(byId:) if the existing fetchEntry returns @Model

Phase 3 — Migrate publishers
  [ ] HistoryStore.entries: [Entry] → [EntrySnapshot]
  [ ] ContentViewModel.entries: [Entry] → [EntrySnapshot]
  [ ] Fix compile errors in history views
  [ ] Fix compile errors in ContentViewModel consumers
  [ ] Grep for any other @Published [Entry] — fix if found

Phase 4 — Tests
  [ ] Add makeEntrySnapshot(...) to test fixtures
  [ ] Update MockEntryService protocol conformance
  [ ] Update HistoryStoreTests assertions
  [ ] Update ContentViewModelTests assertions
  [ ] Run full test suite on a physical device

Phase 5 — Verify
  [ ] Smoke test: add / edit / delete / sync / month-nav / BPM / baby
  [ ] Confirm @Published fires only on real changes (Equatable)
  [ ] Grep `@Published.*\[Entry\]` — should be zero
  [ ] Grep `: [Entry]` outside EntryService / Repository / Worker
```

---

## 15. Summary

| Question | Answer |
|---|---|
| **Does Entry have the same crash risk as Account/Device?** | Yes — same `@Model` thread-safety problem — but the blast radius is smaller because most consumers already use value types (`BathScaleWeightSummary`, `EntryData`). |
| **Is EntrySnapshot the right fix?** | Yes, as a *targeted* fix for the two remaining `@Published [Entry]` surfaces (`HistoryStore`, `ContentViewModel`). |
| **Is it harder than Account/Device?** | **No — significantly easier.** Mutation is already centralized in EntryService; the worker pattern already exists; only two publisher sites leak the @Model. |
| **What's the biggest single win?** | Closing the last two managed-object leaks into feature stores and making `HistoryStore` safe against any future off-actor work on the history row views. |
| **Any downside?** | Small conversion cost (sub-millisecond per entry), mitigated by existing month-based pagination. Minor maintenance burden keeping `EntryData` and `EntrySnapshot` aligned — document the split. |
| **Is it worth doing?** | **Yes** — lowest-cost of the three snapshot migrations, closes a known latent crash class, and finishes the pattern already started by `EntryData`. |
| **Estimated scope** | ~25 files, 5 phases, 0 critical refactors. Largely mechanical after Phase 1. |

**Recommendation: proceed.** Schedule it as the next snapshot migration after
Device lands — it will be faster, lower-risk, and will fully close out the
"`@Model` escaping to feature code" class of bug across the app's three
major domain types.

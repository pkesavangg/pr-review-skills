# EntrySnapshot Implementation Plan

**Ticket:** MA-TBD
**Date:** 2026-04-17
**Status:** PLANNED

---

## Background

`Entry` is a SwiftData `@Model` class — **not `Sendable`**, not thread-safe. It is
published via `@Published var entries: [Entry]` on `HistoryStore` and
`ContentViewModel`, returned by `EntryService` public methods, and walked by
SwiftUI views that read its `@Model` child relationships
(`scaleEntry`, `scaleEntryMetric`, `bpmEntry`, `babyEntry`). This is the same
structural pattern that caused production `EXC_BAD_ACCESS` crashes in the
`Account` code path and is actively causing crashes around `Device`.

### Why EntrySnapshot?

- `Entry` is `@Model` — cannot safely cross actor boundaries.
- `EntrySnapshot` is `struct EntrySnapshot: Equatable, Sendable, Identifiable` —
  safe for Combine publishers, async/await, and cross-boundary use.
- `Entry` has **four** child `@Model` relationships (`BathScaleEntry`,
  `BathScaleMetric`, `BPMEntry`, `BabyEntry`). Every `entry.scaleEntry?.weight`
  access is a SwiftData fault — safe only while the managed object's
  `ModelContext` is owned on the current actor.
- `EntryData` already exists in `SwiftDataWorker` as a partial snapshot — it
  flattens scale/metric but **does not cover** BPM or baby relationships.
  `EntrySnapshot` closes that gap and becomes the public publisher payload.

### Key Difference From AccountSnapshot / DeviceSnapshot

`Account` → `AccountSnapshot` was a clean swap across every consumer.
`Device` → `DeviceSnapshot` used a **hybrid** boundary: construction path keeps
the @Model, read path switches to snapshots.

`Entry` → `EntrySnapshot` also uses a hybrid boundary, but it is **narrower**
than Device because:

- Entry mutations already happen **only** inside `EntryService` on the main
  actor — no feature store mutates Entry directly.
- Only **two** `@Published [Entry]` surfaces leak the managed object to the UI
  (`HistoryStore.entries`, `ContentViewModel.entries`).
- `EntryService` already publishes value-type summaries
  (`BathScaleWeightSummary`) and uses events (`entrySaved`, `entryDeleted`)
  rather than a live entries array — those parts are already safe.

### Scope at a Glance

| Dimension | AccountSnapshot | DeviceSnapshot | **EntrySnapshot** |
|---|---|---|---|
| Files affected | 61 | ~79+ | **~18–22** |
| Published @Model surfaces to replace | 2 | 1 | **2** |
| Protocol methods changed | ~15 | ~30+ | **~6 new, 0 renamed** |
| Mutation sites to refactor | 4 | 2 critical (pairing flows) | **0 — mutations already in EntryService** |
| Construction path change | Yes (takes ID) | Hybrid (@Model kept) | **Hybrid (@Model kept)** |
| Pre-existing partial snapshot | No | No | **Yes — `EntryData`** |

---

## Scope of Changes

### New Files (3)

| # | File | Purpose |
|---|---|---|
| 1 | `meApp/Domain/Models/Domain/Entry/EntrySnapshot.swift` | Top-level Sendable value-type snapshot of `Entry` + `metricItems` computed property |
| 2 | `meApp/Domain/Models/Domain/Entry/EntryChildSnapshots.swift` | `BathScaleEntrySnapshot`, `BathScaleMetricSnapshot`, `BPMEntrySnapshot`, `BabyEntrySnapshot` |
| 3 | `meApp/Domain/Models/DB/Entry+Snapshot.swift` | `Entry.toSnapshot()` + `BathScaleEntry.toSnapshot()` + `BathScaleMetric.toSnapshot()` + `BPMEntry.toSnapshot()` + `BabyEntry.toSnapshot()` |

> Note: `EntryData` in `SwiftDataWorker.swift` stays as-is. It is used by
> progress/DTO paths that don't need BPM/baby fields. It can optionally be
> refactored to be a slim projection of `EntrySnapshot` later — not required.

### Modified Files — Production Code (~18–22 files)

| Category | Count | Notes |
|---|---|---|
| Protocol layer | 2 | `EntryServiceProtocol`, `EntryRepositoryProtocol` |
| Service layer | 2 | `EntryService`, `EntryRepository` (new snapshot-returning fetches) |
| Feature stores / viewmodels | 2 | `HistoryStore` (critical), `ContentViewModel` (critical) |
| Views | ~6–10 | History row views reading `entry.scaleEntry?.*` etc. |
| Models | 1 | `EntryNotification` payload type audit |
| Utilities / helpers | 1–3 | Any helper that takes `Entry` as a parameter (`EntryDisplayHelper`, etc.) |

---

## Phase 1 — Foundation (Additive, No Breakage)

Create all snapshot structs and conversion extensions. Nothing breaks —
these are new files only.

### 1a. `EntryChildSnapshots.swift`

**File:** `meApp/Domain/Models/Domain/Entry/EntryChildSnapshots.swift`

```swift
import Foundation

/// Value-type snapshot of BathScaleEntry. Sendable, safe across async boundaries.
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

/// Value-type snapshot of BathScaleMetric. Sendable, safe across async boundaries.
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

/// Value-type snapshot of BPMEntry. Sendable, safe across async boundaries.
struct BPMEntrySnapshot: Equatable, Sendable {
    let systolic: Int
    let diastolic: Int
    let meanArterial: String
    let pulse: Int
}

/// Value-type snapshot of BabyEntry. Sendable, safe across async boundaries.
struct BabyEntrySnapshot: Equatable, Sendable {
    let babyId: String
    let length: Int
    let weight: Int
    let source: String?
}
```

### 1b. `EntrySnapshot.swift`

**File:** `meApp/Domain/Models/Domain/Entry/EntrySnapshot.swift`

```swift
import Foundation

/// A value-type copy of `Entry` and its child relationships.
/// Published by `EntryService` / `HistoryStore` instead of the SwiftData `@Model` directly.
///
/// Safe to use across async boundaries and as Combine publisher payloads.
/// Reading `snapshot.scaleEntry?.weight` is a plain `let Int?` access — no
/// SwiftData backing store involved.
///
/// ## Relationship with `EntryData` (SwiftDataWorker)
///
/// `EntryData` is a slim projection used by progress calculations and DTO
/// conversion (scale fields only). `EntrySnapshot` is the richer, UI-facing
/// value type covering every relationship including BPM and baby. They can
/// coexist — `EntryData` stays where it is used; consumers of
/// `@Published [Entry]` switch to `[EntrySnapshot]`.
struct EntrySnapshot: Equatable, Sendable, Identifiable {

    // MARK: - Core Entry fields
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

    // MARK: - Child relationship snapshots
    let scaleEntry: BathScaleEntrySnapshot?
    let scaleEntryMetric: BathScaleMetricSnapshot?
    let bpmEntry: BPMEntrySnapshot?
    let babyEntry: BabyEntrySnapshot?

    // MARK: - Computed (mirrors Entry.metricItems)
    var metricItems: [(value: Int, metric: BodyMetric)] {
        var arr: [(Int, BodyMetric)] = []
        if let bmi = scaleEntry?.bmi {
            arr.append((bmi, .bmi))
        }
        if let v = scaleEntry?.bodyFat, v != 0 {
            arr.append((v, .bodyFat))
        }
        if let v = scaleEntry?.muscleMass, v != 0 {
            arr.append((v, .muscleMass))
        }
        if let v = scaleEntry?.water, v != 0 {
            arr.append((v, .water))
        }
        if let v = scaleEntryMetric?.pulse, v != 0 {
            arr.append((v, .pulse))
        }
        if let v = scaleEntryMetric?.boneMass, v != 0 {
            arr.append((v, .boneMass))
        }
        if let v = scaleEntryMetric?.visceralFatLevel, v != 0 {
            arr.append((v, .visceralFatLevel))
        }
        if let v = scaleEntryMetric?.subcutaneousFatPercent, v != 0 {
            arr.append((v, .subcutaneousFatPercent))
        }
        if let v = scaleEntryMetric?.proteinPercent, v != 0 {
            arr.append((v, .proteinPercent))
        }
        if let v = scaleEntryMetric?.skeletalMusclePercent, v != 0 {
            arr.append((v, .skeletalMusclePercent))
        }
        if let v = scaleEntryMetric?.bmr, v != 0 {
            arr.append((v, .bmr))
        }
        if let v = scaleEntryMetric?.metabolicAge, v != 0 {
            arr.append((v, .metabolicAge))
        }
        return arr
    }
}
```

### 1c. `Entry+Snapshot.swift`

**File:** `meApp/Domain/Models/DB/Entry+Snapshot.swift`

All five `toSnapshot()` conversions live in a single file next to
`Entry.swift`. Relationship access happens here, safely on the main actor
before any `await`.

```swift
import Foundation

extension BathScaleEntry {
    func toSnapshot() -> BathScaleEntrySnapshot {
        BathScaleEntrySnapshot(
            weight:        weight,
            bodyFat:       bodyFat,
            muscleMass:    muscleMass,
            water:         water,
            bmi:           bmi,
            source:        source,
            systolic:      systolic,
            diastolic:     diastolic,
            meanArterial:  meanArterial
        )
    }
}

extension BathScaleMetric {
    func toSnapshot() -> BathScaleMetricSnapshot {
        BathScaleMetricSnapshot(
            bmr:                    bmr,
            metabolicAge:           metabolicAge,
            proteinPercent:         proteinPercent,
            pulse:                  pulse,
            skeletalMusclePercent:  skeletalMusclePercent,
            subcutaneousFatPercent: subcutaneousFatPercent,
            visceralFatLevel:       visceralFatLevel,
            boneMass:               boneMass,
            impedance:              impedance,
            unit:                   unit
        )
    }
}

extension BPMEntry {
    func toSnapshot() -> BPMEntrySnapshot {
        BPMEntrySnapshot(
            systolic:     systolic,
            diastolic:    diastolic,
            meanArterial: meanArterial,
            pulse:        pulse
        )
    }
}

extension BabyEntry {
    func toSnapshot() -> BabyEntrySnapshot {
        BabyEntrySnapshot(
            babyId: babyId,
            length: length,
            weight: weight,
            source: source
        )
    }
}

extension Entry {
    /// Converts the SwiftData `Entry` and all its child models into a
    /// Sendable `EntrySnapshot`. Call this only on the main actor while the
    /// model context is valid and before any await boundary.
    func toSnapshot() -> EntrySnapshot {
        EntrySnapshot(
            id:                 id,
            accountId:          accountId,
            entryTimestamp:     entryTimestamp,
            serverTimestamp:    serverTimestamp,
            opTimestamp:        opTimestamp,
            operationType:      operationType,
            entryType:          entryType,
            isSynced:           isSynced,
            note:               note,
            attempts:           attempts,
            isFailedToSync:     isFailedToSync,
            scaleEntry:         scaleEntry?.toSnapshot(),
            scaleEntryMetric:   scaleEntryMetric?.toSnapshot(),
            bpmEntry:           bpmEntry?.toSnapshot(),
            babyEntry:          babyEntry?.toSnapshot()
        )
    }
}
```

---

## Phase 2 — Service Layer (Adds Snapshot-Returning Methods)

**Goal:** Give consumers a way to fetch `[EntrySnapshot]` without touching the
`@Model`. Existing `Entry`-returning methods stay for now — deleted in Phase 4
once all consumers migrate.

### 2a. `EntryServiceProtocol.swift` — Add snapshot methods

**File:** `meApp/Domain/Services/EntryServiceProtocol.swift`

Add these members (existing methods untouched):

```swift
@MainActor
protocol EntryServiceProtocol {

    // ... existing publishers unchanged ...

    // MARK: - Snapshot-returning queries (new)
    func fetchEntrySnapshot(byId id: UUID) async throws -> EntrySnapshot?
    func fetchAllEntrySnapshots() async throws -> [EntrySnapshot]
    func fetchEntrySnapshots(lastNDays: Int, entryType: EntryType) async throws -> [EntrySnapshot]
    func fetchEntrySnapshots(forMonth month: String, entryType: EntryType) async throws -> [EntrySnapshot]
    func fetchEntrySnapshots(forBabyProfile babyId: String) async throws -> [EntrySnapshot]

    // MARK: - Mutation API — unchanged in signature, Entry stays internal to service
    // saveNewEntry, saveNewEntries, deleteEntry, updateNote, etc. keep accepting
    // construction-time @Model objects or request DTOs. Callers stop holding
    // Entry refs after the call returns — they read from snapshot publishers instead.
}
```

> **Why accept `Entry` on writes?** The manual entry form, BLE scan pipeline,
> and BPM/baby creation paths all construct a new `Entry` @Model on main actor
> and hand it to `EntryService` for persistence. That construction surface
> stays — the snapshot boundary is between service and consumers, not inside
> the service itself.

### 2b. `EntryRepositoryProtocol.swift` — Keep returning @Model (private to service)

**File:** `meApp/Domain/Repositories/EntryRepositoryProtocol.swift`

**No changes.** The repository keeps returning `Entry` @Model — it lives
below the snapshot boundary and is used only by `EntryService`.

### 2c. `EntryService.swift` — Implement snapshot fetches

**File:** `meApp/Data/Services/EntryService.swift`

Implementation pattern: call the existing repository fetch, then map on the
main actor before returning.

```swift
// Add alongside existing getEntry / getAllEntries

func fetchEntrySnapshot(byId id: UUID) async throws -> EntrySnapshot? {
    guard let entry = try await localRepository.fetchEntry(byId: id.uuidString) else {
        return nil
    }
    return entry.toSnapshot()
}

func fetchAllEntrySnapshots() async throws -> [EntrySnapshot] {
    let entries = try await localRepository.fetchAllEntries()
    return entries.map { $0.toSnapshot() }
}

func fetchEntrySnapshots(lastNDays days: Int, entryType: EntryType) async throws -> [EntrySnapshot] {
    // Reuse existing `getEntries(lastNDays:entryType:)` logic but map to snapshot
    let entries = try await getEntries(lastNDays: days, entryType: entryType)
    return entries.map { $0.toSnapshot() }
}

func fetchEntrySnapshots(forMonth month: String, entryType: EntryType) async throws -> [EntrySnapshot] {
    let entries = try await getMonthDetail(month: month, entryType: entryType)
    return entries.map { $0.toSnapshot() }
}

func fetchEntrySnapshots(forBabyProfile babyId: String) async throws -> [EntrySnapshot] {
    let entries = try await getBabyEntries(forProfile: babyId)   // existing helper
    return entries.map { $0.toSnapshot() }
}
```

> **Existing `@Published` summaries (`dailySummaries`, `monthlySummaries`,
> `bpmDailySummaries`, `bpmMonthlySummaries`,
> `babyDailySummariesByProfile`, `babyMonthlySummariesByProfile`) are
> already value types (`BathScaleWeightSummary`). No change needed there.**

### 2d. `EntryService` — Internal handling of `entrySaved` / `entryDeleted`

**File:** `meApp/Data/Services/EntryService.swift`

`EntryNotification` currently carries an `Entry` reference. Audit its
payload: if consumers only use `entryType` / `entryId`, leave it — it's
already emitted on main actor. If a subscriber uses the attached `Entry`
to read relationships, convert at emit time:

```swift
// If EntryNotification currently exposes `entry: Entry`, change to:
// struct EntryNotification { let snapshot: EntrySnapshot }

entrySaved.send(EntryNotification(snapshot: entry.toSnapshot()))
entryDeleted.send(EntryNotification(snapshot: entry.toSnapshot()))
```

> Check the `EntryNotification` type definition in the model layer. If it
> does not hold a live `Entry`, no change is needed here.

---

## Phase 3 — Migrate the Two `@Published [Entry]` Publishers

This is where the managed-object leak is closed.

### 3a. `HistoryStore.swift` — `entries: [Entry]` → `[EntrySnapshot]`

**File:** `meApp/Features/History/Stores/HistoryStore.swift`

```swift
// Before (line 27)
@Published private(set) var entries: [Entry] = []

// After
@Published private(set) var entries: [EntrySnapshot] = []
```

Update every producer assignment in the store:

```swift
// loadEntries(for month:, showLoader:) — line ~320
// Before
let fetched = try await entryService.getMonthDetail(month: monthKey, entryType: .scale)
self.entries = fetched

// After
let fetched = try await entryService.fetchEntrySnapshots(forMonth: monthKey, entryType: .scale)
self.entries = fetched
```

The three other write sites also collapse to `self.entries = []`:

- `setSelectedMonth(selectedMonth:)` (line ~158): `self.entries = []` — type-safe, unchanged
- `resetSelectedMonth()` (line ~163): `self.entries = []` — unchanged

Grouping / filtering helpers that take `Entry`:

```swift
// Before
let grouped = Dictionary(grouping: fetched) { $0.entryTimestamp.dateOnly }

// After — identical (EntrySnapshot has the same top-level field)
let grouped = Dictionary(grouping: fetched) { $0.entryTimestamp.dateOnly }
```

`mapBabyEntriesToWeeks(_:profile:)` (line ~622) reads
`entry.babyEntry?.weight` and `entry.entryTimestamp` — access paths are
identical on `EntrySnapshot`. Change the parameter type from `[Entry]` to
`[EntrySnapshot]`.

`showDeleteEntryAlert(entry: Entry, ...)` (line ~189) — change parameter to
`EntrySnapshot`. The store subsequently calls
`entryService.deleteEntry(_:)`, which today accepts `Entry`. Two options:

1. **Preferred:** Add `EntryService.deleteEntry(entryId: UUID)` overload;
   HistoryStore passes `snapshot.id`.
2. **Minimum:** Re-fetch the managed `Entry` inside the service before
   deletion (already happens internally).

Pick option (1) — it makes the delete API id-based like `switchAccount`
did for Account, and fully severs the store from the @Model.

```swift
// Add to EntryServiceProtocol
func deleteEntry(entryId: UUID) async throws

// Inside EntryService
func deleteEntry(entryId: UUID) async throws {
    guard let entry = try await localRepository.fetchEntry(byId: entryId.uuidString) else {
        throw EntryError.entryNotFound
    }
    try await deleteEntry(entry)   // existing overload keeps internal logic
}
```

### 3b. `ContentViewModel.swift` — `entries: [Entry]` → `[EntrySnapshot]`

**File:** `meApp/Features/Common/ViewModels/ContentViewModel.swift`

```swift
// Before (line 21)
@Published var entries: [Entry] = []

// After
@Published var entries: [EntrySnapshot] = []
```

Update the populator (`loadData()` around lines 133–193) to call
`entryService.fetchAllEntrySnapshots()` (or a narrower fetch if only the
latest entries are needed).

Grep for every subscriber of `ContentViewModel.entries` — fix field
accesses if any step off the flat top-level fields. Nested access patterns
(`entry.scaleEntry?.weight`) read identically on the snapshot.

---

## Phase 4 — View-Layer Migration (Mechanical)

Views that receive `Entry` as a parameter switch to `EntrySnapshot`. Field
access paths are **identical** — `snapshot.scaleEntry?.weight` reads the
same as `entry.scaleEntry?.weight`.

### 4a. History views

| File | Change |
|---|---|
| `Features/History/Views/Components/HistoryEntryItem.swift` | `let entry: Entry` → `let entry: EntrySnapshot`. Preview-only `entry.scaleEntry = BathScaleEntry(...)` construction must switch to building a synthetic `EntrySnapshot` in the preview block. |
| `Features/History/Views/Components/BPHistoryEntryItem.swift` | Already takes `BPHistoryEntry` (value type) — **no change** |
| `Features/History/Views/Components/BabyHistoryEntryItem.swift` | Already takes `BabyHistoryEntry` (value type) — **no change** |
| `Features/History/Views/Screens/HistoryListScreen.swift` | Passes `entries` array items into row views — type flows through naturally from the store change. Verify any `@State` / `@Binding` uses the new type. |
| `Features/History/Views/Screens/MonthHistoryScreen.swift` | Same pattern as above |
| `Features/History/Views/Components/EntryDetailView.swift` (if exists) | `entry: Entry` → `entry: EntrySnapshot` |

**Pattern for preview blocks that currently build `Entry` directly:**

```swift
// Before — constructs @Model inside preview
#Preview {
    let entry = Entry(entryTimestamp: "2026-04-17",
                      accountId: "x",
                      operationType: "create")
    entry.scaleEntry = BathScaleEntry(weight: 15000, ...)
    entry.scaleEntryMetric = BathScaleMetric(bmr: 1800, ...)
    return HistoryEntryItem(entry: entry)
}

// After — constructs snapshot directly, no SwiftData context needed
#Preview {
    HistoryEntryItem(
        entry: EntrySnapshot(
            id: UUID(),
            accountId: "x",
            entryTimestamp: "2026-04-17",
            serverTimestamp: nil,
            opTimestamp: nil,
            operationType: "create",
            entryType: "scale",
            isSynced: true,
            note: nil,
            attempts: 0,
            isFailedToSync: false,
            scaleEntry: BathScaleEntrySnapshot(
                weight: 15000, bodyFat: 200, muscleMass: 4000,
                water: 500, bmi: 220, source: "manual",
                systolic: nil, diastolic: nil, meanArterial: nil
            ),
            scaleEntryMetric: BathScaleMetricSnapshot(
                bmr: 1800, metabolicAge: 30, proteinPercent: nil,
                pulse: nil, skeletalMusclePercent: nil,
                subcutaneousFatPercent: nil, visceralFatLevel: nil,
                boneMass: nil, impedance: nil, unit: "kg"
            ),
            bpmEntry: nil,
            babyEntry: nil
        )
    )
}
```

### 4b. BPM history views

If any BPM history row reads `entry.bpmEntry?.systolic` (i.e., does not go
through `BPHistoryEntry`), switch to `EntrySnapshot`. Most BPM screens
already use the value-type mapping in `HistoryStore` — no change expected.

### 4c. Baby history views

Same as BPM — `HistoryStore` maps to `BabyHistoryEntry` before publishing.
Views that directly receive an `Entry` (rare) switch to `EntrySnapshot`.

---

## Phase 5 — Utilities & Helpers

Any helper that takes `Entry` as a parameter for display / formatting
switches to `EntrySnapshot`. Access patterns stay identical.

### Files to audit and update (not exhaustive — compiler will catch all)

| File | Change |
|---|---|
| `Features/Common/Utilities/EntryDisplayHelper.swift` (if exists) | Parameter type `Entry` → `EntrySnapshot` |
| `Features/Common/Utilities/EntryFormatter.swift` (if exists) | Same |
| `Features/Dashboard/Utilities/EntrySummarizer.swift` (if exists) | Same |
| Any `func format(entry: Entry)` or `func icon(for entry: Entry)` | Switch to `EntrySnapshot` |

Use this grep to find candidates:

```bash
rg "(entry|Entry): Entry\b" iOS/meApp --type swift
rg "(entries|Entries): \[Entry\]" iOS/meApp --type swift
```

---

## Phase 6 — Internal-Only Entry @Model Audit

At this point, no feature code should hold an `Entry` @Model. Verify:

### 6a. Greps that must return zero hits outside allowed files

```bash
# Allowed files that legitimately work with Entry @Model:
#   - Data/Services/EntryService.swift
#   - Data/Storage/DB/EntryRepository.swift
#   - Data/API/EntryRepositoryAPI.swift (constructs from DTO)
#   - Domain/Models/DB/Entry.swift (the @Model itself)
#   - Domain/Models/DB/Entry+Snapshot.swift (conversion extension)
#   - Core/Services/SwiftDataWorker.swift (uses EntryData internally)
#   - Domain/Models/DB/BathScaleEntry.swift / BathScaleMetric.swift /
#     BPMEntry.swift / BabyEntry.swift (child @Models)

# Must be zero hits elsewhere:
rg ": \[Entry\]" iOS/meApp --type swift
rg ": Entry\?" iOS/meApp --type swift
rg "@Published.*\[Entry\]" iOS/meApp --type swift
rg "@Published.*Entry\?" iOS/meApp --type swift
```

### 6b. `EntryNotification` payload verification

If `EntryNotification` still carries `Entry`, switch its payload to
`EntrySnapshot`. Update every subscriber accordingly.

---

## Phase 7 — (Optional) Reconcile EntryData and EntrySnapshot

`EntryData` (in `SwiftDataWorker`) exists for progress math and DTO
conversion. `EntrySnapshot` exists for UI publishing. They have overlapping
but not identical field coverage:

| Field | EntryData | EntrySnapshot |
|---|---|---|
| `id` (PersistentIdentifier) | ✓ | — |
| `entryId` (UUID) | ✓ | ✓ (`id`) |
| `operationType` | ✓ | ✓ |
| Scale fields flattened | ✓ | Nested in `scaleEntry?` |
| Metric fields flattened | ✓ | Nested in `scaleEntryMetric?` |
| BPM fields | — | ✓ (nested in `bpmEntry?`) |
| Baby fields | — | ✓ (nested in `babyEntry?`) |
| `note`, `attempts`, `isFailedToSync` | — | ✓ |
| `toDTO()` | ✓ | — |

**Recommendation:** Leave both as-is for this migration. `EntryData` serves
a narrow internal purpose; `EntrySnapshot` serves the UI / publisher
purpose. Document the split at the top of `SwiftDataWorker.swift` so future
readers don't assume they should be merged.

---

## Files That Should NOT Change

These files work below the snapshot boundary — they legitimately use
`Entry` @Model internally:

| File | Reason |
|---|---|
| `Data/Storage/DB/EntryRepository.swift` | SwiftData CRUD on `@Model` |
| `Data/Services/EntryService.swift` (write methods) | Construction + sync — works with `@Model` internally, publishes snapshots externally |
| `Data/API/EntryRepositoryAPI.swift` | DTO-level API — no Entry @Model usage |
| `Core/Services/SwiftDataWorker.swift` | Uses `EntryData` projection — already safe |
| `Domain/Models/DB/Entry.swift` | The @Model itself |
| `Domain/Models/DB/BathScaleEntry.swift`, `BathScaleMetric.swift`, `BPMEntry.swift`, `BabyEntry.swift` | Child @Models themselves |

---

## Pattern Conversion Reference

```swift
// Before (Entry @Model)                      // After (EntrySnapshot)
entry.id                                      snapshot.id                        // identical
entry.accountId                               snapshot.accountId                 // identical
entry.entryTimestamp                          snapshot.entryTimestamp            // identical
entry.operationType                           snapshot.operationType             // identical
entry.entryType                               snapshot.entryType                 // identical
entry.note                                    snapshot.note                      // identical
entry.isSynced                                snapshot.isSynced                  // identical
entry.scaleEntry?.weight                      snapshot.scaleEntry?.weight        // identical
entry.scaleEntry?.bmi                         snapshot.scaleEntry?.bmi           // identical
entry.scaleEntryMetric?.bmr                   snapshot.scaleEntryMetric?.bmr     // identical
entry.scaleEntryMetric?.pulse                 snapshot.scaleEntryMetric?.pulse   // identical
entry.bpmEntry?.systolic                      snapshot.bpmEntry?.systolic        // identical
entry.babyEntry?.weight                       snapshot.babyEntry?.weight         // identical
entry.metricItems                             snapshot.metricItems               // identical
```

**The migration is almost entirely a type-annotation change.** Access paths
are preserved because the snapshot tree mirrors the @Model tree.

---

## Implementation Order

### Step 1: Phase 1 — Foundation (must be first)

Create all 3 new files. Build to verify they compile. No other files change.

### Step 2: Phase 2 — Service Layer

Add snapshot-returning methods to `EntryServiceProtocol` and `EntryService`.
Audit `EntryNotification` payload — switch to `EntrySnapshot` if it carries
a live `Entry`. Existing `Entry`-returning methods stay (deleted in Phase 6
once all consumers migrate).

### Step 3: Phase 3 — Publisher Migration (intentional breakage)

Switch `HistoryStore.entries` and `ContentViewModel.entries` to
`[EntrySnapshot]`. This intentionally breaks consumers — compiler reports
every file that needs updating.

### Step 4: Phase 4 — Fix Consumer Compile Errors (view layer)

Work through errors feature-by-feature:

**Suggested order by blast radius:**

1. `HistoryEntryItem.swift` (highest-traffic consumer)
2. Other history row / detail views
3. Preview blocks inside each of those views
4. `ContentViewModel`-dependent top-level chrome
5. Any remaining helpers / utilities

### Step 5: Phase 5 — Utilities & Helpers

Change `Entry`-accepting helpers to `EntrySnapshot`. Compiler catches all
call sites.

### Step 6: Phase 6 — Cleanup Audit

Run the grep-zero-hits verification. Remove any `Entry`-returning public
methods on `EntryService` that no longer have callers (optional — leaving
them is harmless since the @Model stays on the main actor below the
service boundary).

### Step 7: Phase 7 (Optional) — Document EntryData/EntrySnapshot split

Add a comment block at the top of `SwiftDataWorker.swift` describing when
to use which. No code change.

---

## Verification Checklist

```
Foundation:
  [ ] EntrySnapshot.swift compiles
  [ ] EntryChildSnapshots.swift compiles (4 structs)
  [ ] Entry+Snapshot.swift compiles — 5 toSnapshot() extensions
  [ ] EntrySnapshot.metricItems returns same values as Entry.metricItems
      for a fully populated Entry (manual spot-check)

Service Layer:
  [ ] EntryServiceProtocol has fetchEntrySnapshot(byId:)
  [ ] EntryServiceProtocol has fetchAllEntrySnapshots()
  [ ] EntryServiceProtocol has fetchEntrySnapshots(lastNDays:entryType:)
  [ ] EntryServiceProtocol has fetchEntrySnapshots(forMonth:entryType:)
  [ ] EntryServiceProtocol has fetchEntrySnapshots(forBabyProfile:)
  [ ] EntryServiceProtocol has deleteEntry(entryId:)
  [ ] EntryNotification payload uses EntrySnapshot (or is confirmed ID-only)

Publisher Migration:
  [ ] HistoryStore.entries type is [EntrySnapshot]
  [ ] ContentViewModel.entries type is [EntrySnapshot]
  [ ] All HistoryStore producer assignments use fetchEntrySnapshots*
  [ ] ContentViewModel populator uses fetchAllEntrySnapshots (or narrower)

View Layer:
  [ ] HistoryEntryItem takes EntrySnapshot
  [ ] All other entry-accepting views take EntrySnapshot
  [ ] All preview blocks construct EntrySnapshot (no Entry @Model in previews)

Utilities:
  [ ] All helper functions switched from Entry to EntrySnapshot
  [ ] No @Published [Entry] anywhere outside allowed files (grep-zero-hit)
  [ ] No `: Entry\?` outside allowed files (grep-zero-hit)
  [ ] No `: \[Entry\]` outside allowed files (grep-zero-hit)

Build & Run:
  [ ] Full build succeeds
  [ ] History list loads and renders correctly
  [ ] Month navigation works
  [ ] Delete entry flow works (id-based delete)
  [ ] Note editing works
  [ ] BPM history renders correctly
  [ ] Baby history renders correctly per profile
  [ ] Dashboard renders correctly (unchanged — already snapshot-safe)
  [ ] Sync (outbound and inbound) works without crashes
```

---

## Statistics Summary

| Category | Count |
|---|---|
| New files | 3 |
| Modified protocol files | 2 (`EntryServiceProtocol`, optionally `EntryRepositoryProtocol` if signatures grow) |
| Modified service files | 1 (`EntryService` — add snapshot methods, audit notification payload) |
| Modified stores / viewmodels | 2 (`HistoryStore`, `ContentViewModel`) |
| Modified views | ~6–10 (mechanical — access paths identical) |
| Modified utilities | 1–3 (helpers that accept `Entry`) |
| Mutation refactors required | 0 (all Entry mutations already live in `EntryService`) |
| Files that should NOT change | 6 (repository, API repo, worker, @Model definitions) |
| **Total production files** | **~18–22** |

**Zero critical mutation refactors.** Entry is the cleanest of the three
snapshot migrations — mutations are already centralized, construction
stays with the @Model internally, and only two publisher sites leak the
managed object to feature code.

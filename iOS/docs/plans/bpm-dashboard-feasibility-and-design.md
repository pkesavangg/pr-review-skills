# Blood Pressure Monitor (BPM) — Feasibility & Design Document

## 1. Overview

This document evaluates and plans the addition of Blood Pressure Monitor (BPM) support to the meApp iOS application. The scope covers:

- **BP data models** — extending the existing `Entry` / `BathScaleEntry` structs with BP fields and an `entryType` discriminator
- **Single EntryService** — no separate BPM service; the existing `EntryService` handles both weight (`wg`) and BPM (`bpm`) entries, filtered by `entryType`
- **BP dashboard** — chart rendering, AHA color coding, three-reading average, streaks
- **Multi-device dashboard** — snapshot cards when user has multiple product types
- **Product type switching** — header selector behavior across Dashboard, Manual Entry, and History
- **Manual entry** — BP-specific form with systolic/diastolic/pulse fields
- **History** — filtered entry list by `entryType`

**Key constraint:** meApp and bpmMobileApp4 are currently separate apps with separate APIs. The plan is to unify BP support into meApp. For now, the existing `Entry` struct is extended to accommodate BP properties for **local testing purposes**, with the understanding that the API will be updated in the future to support the combined payload.

**Key architectural decision:** Reuse the **existing** `DashboardStore`, managers, and graph infrastructure rather than creating BPM-specific duplicates. This ensures that when future product types are added (baby scale, glucose monitor, etc.), we don't need to create a separate store and manager hierarchy for each one.

**Reference implementations:**
- Angular BPM app (`bpmMobileApp4/`) — existing D3-based BP chart with three-series lines
- iOS meApp weight dashboard — SwiftUI Charts-based graph with extensive caching and selection logic
- Figma designs (links below)

**Figma references:**

| Screen | Link |
|--------|------|
| Dashboard Display Metrics | [Figma](https://www.figma.com/design/k0HO1SquDGrYOcoMSbrzA0/Me.Health-Mega-App-2.0?node-id=26501-378051&t=Eqa1tNy17ggW1bdx-4) |
| AHA Rating | [Figma](https://www.figma.com/design/k0HO1SquDGrYOcoMSbrzA0/Me.Health-Mega-App-2.0?node-id=26501-378159&t=Eqa1tNy17ggW1bdx-4) |
| Three Reading Average | [Figma](https://www.figma.com/design/k0HO1SquDGrYOcoMSbrzA0/Me.Health-Mega-App-2.0?node-id=26501-378230&t=Eqa1tNy17ggW1bdx-4) |
| Dashboard with Two Devices | [Figma](https://www.figma.com/design/k0HO1SquDGrYOcoMSbrzA0/Me.Health-Mega-App-2.0?node-id=26501-377351&t=Eqa1tNy17ggW1bdx-4) |
| Total Dashboard | [Figma](https://www.figma.com/design/k0HO1SquDGrYOcoMSbrzA0/Me.Health-Mega-App-2.0?node-id=26501-375865&t=Eqa1tNy17ggW1bdx-4) |

**BP entry operation format (target):**
```json
{
    "systolic": 123,
    "diastolic": 111,
    "pulse": 100,
    "meanArterial": null,
    "userId": "b77d6fca-8a07-42c6-bc55-8961402b4cdb",
    "entryTimestamp": "1641010621000",
    "opTimestamp": "1667449032977",
    "operation": "create",
    "type": "manual",
    "note": null
}
```

---

## 2. Current Architecture Assessment

### Current Dashboard Architecture (Weight)

The weight dashboard is built on a layered manager hierarchy coordinated by `DashboardStore`:

```
DashboardStore (~650 lines)
├── DashboardState (nested: UIState, MetricsState, StreakState, GraphState, GoalState, DataState)
├── Domain Managers:
│   ├── DashboardDataManager       — binds to EntryService publishers, pre-sorts data
│   ├── DashboardGraphManager      — orchestrates graph sub-managers
│   │   ├── GraphDataPreparer      — builds chart series from BathScaleWeightSummary
│   │   ├── GraphRenderingConfiguration — X-axis ticks, date formatting, scroll math
│   │   ├── GraphInteractionHandler — scroll position buffering, visible ops cache
│   │   └── GraphAnimationManager  — period transition debouncing
│   ├── DashboardMetricsManager    — body metrics grid (12 metric definitions)
│   ├── DashboardStreakManager     — streak data and visibility
│   ├── DashboardGoalManager       — goal weight, progress, delta
│   ├── DashboardMetricsCalculator — average weight, display weight
│   ├── DashboardDateRangeManager  — period-specific date ranges
│   └── DashboardSyncCoordinator   — API sync for dashboard config
├── Coordinating Managers:
│   ├── DashboardChartManager      — chart init, scroll end, Y-axis caching
│   ├── DashboardDisplayManager    — weight display, date labels, formatting
│   ├── DashboardGridEditingManager — edit mode, drag-drop, removals
│   └── DashboardLifecycleManager  — init sequence, entry lifecycle, save/reset
└── Views:
    ├── DashboardScreen → WeightTrendView → GraphView → BaseGraphView
    ├── DashboardMetricsSection → MetricCardView (draggable, editable)
    ├── StreakCardView, GoalProgressView
    └── Period wrappers: Week/Month/Year/TotalGraphView
```

### Current Graph Architecture (Weight)

```
GraphView (host — switches between 4 period views)
├── BaseGraphView<ViewModel: SectionViewModelProtocol>
│   ├── Swift Charts LineMark (weight line + optional metric line)
│   ├── Y-axis: cached domain/ticks, nice-scale algorithm (YAxisCalculator)
│   ├── X-axis: period-specific ticks (daily/weekly/monthly/data-driven)
│   ├── Selection crosshair overlay
│   ├── Goal chip overlay
│   ├── Scroll: paged behavior (PagedChartScrollBehavior)
│   └── Caching: chart series, plotted points, axis labels
├── BaseSectionViewModel (~853 lines) — shared across periods
│   ├── Selection state, scroll sync, Y-axis management
│   ├── Chart frame tracking, X-axis caching
│   ├── Point sizing, goal chip positioning
│   └── Configuration via configure(with: DashboardStore)
└── Period-specific ViewModels (4 thin subclasses):
    ├── WeekSectionViewModel  — day snapping, noon plotting
    ├── MonthSectionViewModel — section-based selection, Sunday ticks
    ├── YearSectionViewModel  — month-boundary snapping
    └── TotalSectionViewModel — padded domain, non-scrollable
```

### Key Graph Features to Replicate for BP

| Weight Graph Feature | How It Works | BP Equivalent |
|---------------------|--------------|---------------|
| **Scrollable by period** | `PagedChartScrollBehavior` with period-specific snap boundaries | Same — reuse paged scroll behavior |
| **Y-axis adapts to visible data** | `YAxisCalculator` computes nice-scale domain from visible operations; cached during scroll, refreshed on scroll-end | Same algorithm, but BP Y-axis range is ~40-200 instead of weight range |
| **Period switching** (Week/Month/Year/Total) | `SegmentedButtonView` → `updateSelectedPeriod()` → re-configures view models | Same — reuse period selector and view model switching |
| **Point selection (crosshair)** | Tap → `handleChartSelection()` → crosshair overlay + metric update | Same — but shows systolic/diastolic/pulse instead of weight/metric |
| **Average calculation** | `DashboardMetricsCalculator.getCurrentAverageWeight()` averages visible operations | BP: average systolic/diastolic/pulse from visible operations |
| **Weight interpolation (Hermite spline)** | `GraphDataPreparer.interpolatedDisplayWeight()` for crosshair on empty days | BP: show "no data" for empty days (no interpolation between readings) |
| **Metric cards (draggable, editable)** | `DashboardMetricsSection` + `MetricCardView` + `DragDropModifier` | BP: AHA rating card, three-reading average card, streak cards — all draggable/editable |
| **Skeleton loading** | `GraphSkeletonView`, `SkeletonMetricCardView` | Same — reuse skeleton components |
| **Edit mode (wiggle + drag-drop)** | `DashboardGridEditingManager` manages edit state, removals, order persistence | Same — reuse editing manager for BP card grid |
| **Single-entry handling** | Adds dummy point at period start for single entries | Same for BP |
| **Reference lines** | Goal weight horizontal line | BP: systolic=120 and diastolic=80 reference lines (**no goal line** — goal is weight-only) |

### Current Entry Model Structure

```
Entry (SwiftData @Model)
├── id: UUID
├── accountId: String
├── entryTimestamp: String
├── serverTimestamp: String?
├── opTimestamp: String?
├── operationType: String        // "create", "delete", "note"
├── deviceType: String           // "scale", "bgm", "babyScale"
├── isSynced: Bool
├── babyId: String?
├── attempts: Int
├── isFailedToSync: Bool
├── scaleEntry: BathScaleEntry?  // weight, bodyFat, muscleMass, water, bmi, source
└── scaleEntryMetric: BathScaleMetric?  // bmr, metabolicAge, pulse, boneMass, etc.
```

```
BathScaleWeightSummary (daily/monthly aggregate — NOT SwiftData)
├── period: String               // "YYYY-MM-DD" or "YYYY-MM"
├── date: Date
├── count: Int
├── weight: Double
├── bodyFat, muscleMass, water, bmi: Double?
├── bmr, metabolicAge, pulse, boneMass: Double?
└── skeletalMusclePercent, subcutaneousFatPercent, visceralFatLevel, proteinPercent, impedance: Double?
```

```
EntryService (single service)
├── @Published dailySummaries: [BathScaleWeightSummary]
├── @Published monthlySummaries: [BathScaleWeightSummary]
├── aggregateByDay(entries:accountId:) → [BathScaleWeightSummary?]
├── aggregateByMonth(entries:accountId:) → [BathScaleWeightSummary?]
├── saveNewEntry(_:), deleteEntry(_:), getAllEntries()
├── loadDashboardData()
└── getMonthsAll(), getEntries(forMonth:), getEntries(lastNDays:)
```

### What Already Exists (Infrastructure Ready)

| Component | Status | Location |
|-----------|--------|----------|
| `DeviceType.bpm` enum case | Defined | `Domain/Models/Domain/Entry/DeviceType.swift` |
| `ProductSelection.myBloodPressure` | Defined with display name, icon, titles | `Domain/Models/Domain/Product/ProductSelection.swift` |
| `ProductTypeStore` with BPM filtering | Present (`deviceType == "bpm"` check) | `Features/Common/Stores/ProductTypeStore.swift` |
| `ProductTypeSelectorSheet` | Renders any `ProductSelection` item | `Features/Common/Views/Components/ProductTypeSelectorSheet.swift` |
| `NavbarHeaderView` with chevron/title tap | Supports dropdown trigger | `Features/Common/Views/Components/NavBarHeaderView.swift` |
| `EntryStore` subscribes to product type changes | Logs product type change | `Features/Entry/Stores/EntryStore.swift` |
| `HistoryStore` subscribes to product type changes | Triggers reload on change | `Features/History/Stores/HistoryStore.swift` |
| `BodyMetric.pulse` (heart rate) | Already tracked as a scale metric | `Domain/Models/Domain/Entry/BodyMetric.swift` |
| `Entry.deviceType` field | Exists but currently only "scale"/"babyScale" used | `Domain/Models/DB/Entry.swift` |
| `BathScaleMetric.pulse` field | Already stores pulse/heart rate | `Domain/Models/DB/BathScaleMetric.swift` |
| `AhaPressureClass` enum | Full 5-level classification with colors | `Domain/Models/Domain/BP/AhaPressureClass.swift` |
| `BpmConstants` | Reference lines (120/80), validation ranges, Y-axis defaults | `Features/Dashboard/BPM/Enums/BpmConstants.swift` |
| `AhaRatingSheet` view | Modal with AHA classification chart | `Features/Dashboard/BPM/Views/Components/AhaRatingSheet.swift` |
| `GraphSeries` model | Generic `(date, value, series)` — already supports named series | `Features/Dashboard/Models/GraphSeries.swift` |
| `PagedChartScrollBehavior` | Paged scroll snapping for charts | `Features/Dashboard/Views/Modifiers/PagedChartScrollBehavior.swift` |
| `YAxisCalculator` | Nice-scale Y-axis with tick spacing | `Features/Dashboard/Models/YAxisCalculator.swift` |
| `BpmDashboardStrings` | BP-specific UI strings | `Features/Dashboard/BPM/Strings/BpmDashboardStrings.swift` |

### What Does NOT Exist (Gaps)

| Gap | Impact | Effort |
|-----|--------|--------|
| **`entryType` discriminator** on `Entry` — no way to distinguish weight vs BPM entries | Cannot filter by product type | Small |
| **BP fields** on `Entry` / `BathScaleEntry` — no systolic, diastolic, meanArterial, note | Cannot store BP readings | Small |
| **`entryType` filtering** in EntryService queries and aggregation | All queries return all entry types | Medium |
| **BP summary fields** on `BathScaleWeightSummary` — no systolic, diastolic averages | Cannot aggregate BP data for charts | Small |
| **BP API endpoints** | No create/read/sync for BPM data (future) | Backend-dependent |
| **Product-type-aware `DashboardStore`** | Store hardcoded to weight data only | Medium |
| **Product-type-aware `DashboardDataManager`** | Binds only to `dailySummaries`/`monthlySummaries` (weight) | Medium |
| **Product-type-aware `GraphDataPreparer`** | Only builds weight + metric series, no three-line BP series | Medium |
| **BP-specific graph rendering** in `BaseGraphView` | No reference lines, no three-line coloring | Medium |
| **BP display components** | No systolic/diastolic/pulse headline, no three-reading average | Medium |
| **BP metric cards** (draggable/editable like weight) | No BP-specific cards in DashboardMetricsSection | Medium |
| **BP manual entry form** | No systolic/diastolic/pulse form | Medium |
| **Dashboard header product selector** | Dashboard navbar has no product type selector (Manual Entry and History already have it) | Small |
| **`ProductSelection.dashboardTitle`** | No `dashboardTitle` computed property (unlike `entryTitle` and `historyTitle`) | Small |
| **`ProductTypeStrings.selectGraph`** | No "Select Graph" string for dashboard selector sheet title | Small |
| **Multi-device snapshot cards** | No summary card view for dashboard overview | Medium |

---

## 3. Feasibility

### Verdict: Feasible with Moderate Effort

The existing architecture can support BPM by **extending** the current `DashboardStore` and its managers to be product-type-aware. Key reasons:

1. **Unified Entry model** — `Entry` already has a `deviceType` field. Adding an `entryType` discriminator (`"wg"` / `"bpm"`) and BP-specific fields to `BathScaleEntry` means no new SwiftData models are needed.

2. **Single EntryService** — All CRUD, sync, and aggregation logic stays in one service. Adding `entryType` filtering to existing methods is straightforward. No separate `BpmEntryService`.

3. **Reusable DashboardStore** — Rather than creating a separate `BpmDashboardStore`, the existing `DashboardStore` gains a `productType` property that switches which data publishers it reads from, which display logic it uses, and which metric cards it shows. The manager hierarchy (15+ managers) is designed with protocols — making them product-type-aware is a focused change.

4. **Reusable Graph Infrastructure** — `BaseGraphView`, `BaseSectionViewModel`, and the 4 period-specific view models already support multiple named series via `GraphSeries.series`. Adding systolic/diastolic/pulse series and reference lines extends the existing renderer rather than replacing it.

5. **Reusable Metric Cards** — `DashboardMetricsSection`, `MetricCardView`, and `DashboardGridEditingManager` support arbitrary `MetricItem` arrays. BP cards (AHA rating, three-reading average, individual readings) can be modeled as `MetricItem` instances with the same drag-drop/edit infrastructure.

6. **Future-proof** — When a new product type is added (baby scale, glucose, etc.), it follows the same pattern: add an `entryType` case, extend the summary model, and configure the product-type-aware store — no new store/manager hierarchy needed.

### Key Risk: Backend API Readiness

The backend currently uses separate APIs for weight and BPM. The unified payload format will need API updates. For now, the data layer is built for **local testing** with the understanding that sync will be added when the API is ready.

---

## 4. Data Model Changes — Unified Entry Approach

### 4.1 New `EntryType` Enum

```swift
// Domain/Models/Domain/Entry/EntryType.swift
enum EntryType: String, Codable, Equatable, CaseIterable {
    case wg    // Weight / Scale entry
    case bpm   // Blood Pressure Monitor entry
}
```

### 4.2 Entry Model — Add `entryType`

**Current `Entry.swift`** fields remain unchanged. Add one field:

```swift
// Entry.swift — ADD this field
var entryType: String    // "wg" or "bpm" — discriminator for entry type
```

**Default value:** `"wg"` for backward compatibility. All existing entries are weight entries.

**Initialization:** Updated `init` adds `entryType` parameter with default `EntryType.wg.rawValue`.

### 4.3 BathScaleEntry — Add BP Fields

**Current fields** (unchanged):
- `weight: Int?`, `bodyFat: Int?`, `muscleMass: Int?`, `water: Int?`, `bmi: Int?`, `source: String?`

**New fields added:**

```swift
// BathScaleEntry.swift — ADD these fields
var systolic: Int?        // mmHg, e.g., 123
var diastolic: Int?       // mmHg, e.g., 111
var meanArterial: Int?    // mmHg, calculated: (systolic + 2*diastolic) / 3
var note: String?         // Free-text note for entry
```

**Why on `BathScaleEntry`?** The `BathScaleEntry` relationship already stores measurement data. Adding BP fields here keeps the model flat and avoids a new SwiftData relationship. The `entryType` on `Entry` determines which fields are relevant:
- `entryType == "wg"`: weight, bodyFat, muscleMass, water, bmi are populated
- `entryType == "bpm"`: systolic, diastolic, meanArterial are populated; `pulse` comes from `BathScaleMetric.pulse` (already exists)

### 4.4 BathScaleMetric — No Changes

`BathScaleMetric.pulse` already stores heart rate / pulse. BPM entries will use this existing field for pulse. No new fields needed.

### 4.5 BathScaleWeightSummary — Add BP Aggregates

**Current fields** (unchanged): weight, bodyFat, muscleMass, water, bmi, bmr, metabolicAge, pulse, etc.

**New fields added:**

```swift
// BathScaleWeightSummary — ADD these fields
var systolic: Double?       // Average systolic for period
var diastolic: Double?      // Average diastolic for period
var meanArterial: Double?   // Average mean arterial for period
var entryType: String?      // "wg" or "bpm" — indicates what type of summary this is
```

### 4.6 Complete Entry Structure After Changes

```
Entry (SwiftData @Model)
├── id, accountId, entryTimestamp, serverTimestamp, opTimestamp
├── operationType: String        // "create", "delete", "note"
├── deviceType: String           // "scale", "bpm", "babyScale"
├── entryType: String            // NEW — "wg" or "bpm"
├── isSynced, babyId, attempts, isFailedToSync
├── scaleEntry: BathScaleEntry?
│   ├── weight, bodyFat, muscleMass, water, bmi, source  (existing — for wg)
│   ├── systolic, diastolic, meanArterial                (NEW — for bpm)
│   └── note                                             (NEW — for bpm)
└── scaleEntryMetric: BathScaleMetric?
    ├── bmr, metabolicAge, proteinPercent, pulse          (existing — pulse reused for bpm)
    ├── skeletalMusclePercent, subcutaneousFatPercent     (existing — for wg)
    ├── visceralFatLevel, boneMass, impedance, unit       (existing — for wg)
    └── (no changes for bpm)
```

### 4.7 BP Entry Operation DTO Mapping

For API sync (future), the BP entry maps to this operation format:

```
Entry fields          →  Operation JSON field
─────────────────────────────────────────────
scaleEntry.systolic   →  "systolic": 123
scaleEntry.diastolic  →  "diastolic": 111
scaleEntryMetric.pulse →  "pulse": 100
scaleEntry.meanArterial → "meanArterial": null
accountId             →  "userId": "b77d6fca-..."
entryTimestamp        →  "entryTimestamp": "1641010621000"
opTimestamp           →  "opTimestamp": "1667449032977"
operationType         →  "operation": "create"
scaleEntry.source     →  "type": "manual"
scaleEntry.note       →  "note": null
```

---

## 5. EntryService Changes — Single Service, Dual Entry Types

### 5.1 No Separate BpmEntryService

**The existing `EntryService` handles both weight and BPM entries.** All CRUD, aggregation, and sync methods are extended with `entryType` filtering.

### 5.2 EntryServiceProtocol Changes

Add `entryType` parameter to key methods:

```swift
// EntryServiceProtocol.swift — MODIFIED signatures
func loadDashboardData(entryType: EntryType) async
func getMonthsAll(entryType: EntryType) async throws -> [HistoryMonth]
func getEntries(forMonth month: String, entryType: EntryType) async throws -> [Entry]
func getEntries(lastNDays: Int, entryType: EntryType) async throws -> [Entry]
func getMonthDetail(month: String, entryType: EntryType) async throws -> [Entry]
func getProgress(entryType: EntryType) async throws -> Progress
func getStreak(entryType: EntryType) async throws -> Streak
```

Methods that don't need filtering (operate on individual entries):
```swift
// These stay unchanged — entryType is read from the entry itself
func saveNewEntry(_ entry: Entry) async throws      // entryType already on entry
func deleteEntry(_ entry: Entry) async throws        // entryType already on entry
func getAllEntries() async throws -> [Entry]          // returns all (rarely used)
```

### 5.3 New Published Properties for BPM

```swift
// EntryService.swift — ADD alongside existing publishers
@Published var bpmDailySummaries: [BathScaleWeightSummary] = []
@Published var bpmMonthlySummaries: [BathScaleWeightSummary] = []
```

Existing publishers remain for weight:
```swift
@Published var dailySummaries: [BathScaleWeightSummary] = []      // weight (wg) only
@Published var monthlySummaries: [BathScaleWeightSummary] = []    // weight (wg) only
```

### 5.4 Aggregation Changes

The existing `aggregateByDay` and `aggregateByMonth` methods are extended:

```swift
// Before: aggregates weight-only from all entries
func aggregateByDay(entries: [Entry], accountId: String) -> [BathScaleWeightSummary?]

// After: filters by entryType, aggregates appropriate fields
func aggregateByDay(entries: [Entry], accountId: String, entryType: EntryType) -> [BathScaleWeightSummary?]
```

**For `entryType == .wg`:** Same as current — averages weight, bodyFat, muscleMass, etc.

**For `entryType == .bpm`:** Averages systolic, diastolic, pulse; sets `entryType = "bpm"` on summary. Weight fields are nil/zero.

### 5.5 loadDashboardData Changes

```swift
func loadDashboardData(entryType: EntryType = .wg) async {
    let allDTOs = try await getAllEntriesAsDTO()
    let filtered = allDTOs.filter { /* match entryType */ }

    switch entryType {
    case .wg:
        dailySummaries = aggregateByDayFromDTOs(filtered, accountId: accountId)
        monthlySummaries = aggregateByMonthFromDTOs(filtered, accountId: accountId)
    case .bpm:
        bpmDailySummaries = aggregateByDayFromDTOs(filtered, accountId: accountId)
        bpmMonthlySummaries = aggregateByMonthFromDTOs(filtered, accountId: accountId)
    }
}
```

### 5.6 Entry Filtering Strategy

All entry queries add a filter predicate:

```swift
// Weight entries: entryType == "wg" (or nil for backward compatibility with existing entries)
let isWeightEntry = entry.entryType == EntryType.wg.rawValue || entry.entryType == nil

// BPM entries: entryType == "bpm"
let isBpmEntry = entry.entryType == EntryType.bpm.rawValue
```

**Backward compatibility:** Existing entries without `entryType` field default to `"wg"`.

---

## 6. Recommended Approach — Extend Existing Store & Managers

### Strategy: Product-Type-Aware Existing Infrastructure

**Instead of creating a separate `BpmDashboardStore` with its own 15+ managers**, we make the existing `DashboardStore` and its managers product-type-aware. This avoids massive code duplication and ensures future product types (baby scale, glucose) follow the same pattern.

```
DashboardScreen
├── [Multiple products] → MultiDeviceSnapshotView
│   ├── WeightSnapshotCard → tap → select weight, show weight dashboard
│   └── BpmSnapshotCard → tap → select BPM, show BPM dashboard
├── [Weight selected] → Existing weight dashboard (DashboardStore with productType = .wg)
│   ├── WeightTrendView → GraphView → BaseGraphView (1 weight line + optional metric + goal line)
│   └── DashboardMetricsSection (12 body metrics + goal card + streaks)
└── [BPM selected] → BPM dashboard (DashboardStore with productType = .bpm)
    ├── BpmTrendView → GraphView → BaseGraphView (3 lines: systolic, diastolic, pulse + ref lines)
    ├── BpmDisplayView (systolic/diastolic/pulse headline with AHA colors)
    ├── BpmMetricsSection (AHA card, 3-reading avg, streak cards — draggable/editable, NO goal card)
    └── ThreeReadingAverageCard + BpmStreakSection
```

### Why Extend Rather Than Duplicate?

| Aspect | Separate BpmDashboardStore (old plan) | Product-type-aware DashboardStore (new plan) |
|--------|--------------------------------------|---------------------------------------------|
| **New managers** | 10+ new BPM managers duplicating patterns | 0 new managers — extend existing ones |
| **Code to maintain** | ~5000+ lines of near-duplicate code | ~500 lines of product-type branching |
| **Future product types** | Another full store + managers per type | Add `EntryType` case + configure existing managers |
| **Shared features** | Must re-implement: edit mode, drag-drop, skeleton, scroll, caching | All shared features work out of the box |
| **Bug fixes** | Must fix bugs in two places | Single fix propagates to all product types |
| **Testing** | Duplicate test suites per store | One test suite with product-type parameterization |

### What Changes in Each Existing Manager

| Manager | Current (Weight Only) | Change for Product-Type Awareness |
|---------|----------------------|-----------------------------------|
| **DashboardStore** | Reads `entryService.dailySummaries` | Adds `productType: EntryType` property; reads from `dailySummaries` (wg) or `bpmDailySummaries` (bpm) based on product type |
| **DashboardDataManager** | Binds to `entryService.$dailySummaries` | Binds to product-type-specific publishers; exposes same `state: DataState` interface |
| **DashboardGraphManager** | Delegates to `GraphDataPreparer` for 1-2 series | BP mode delegates to new `BpmGraphDataPreparer` for 3 series; same orchestration |
| **GraphDataPreparer** | `buildChartSeries()` → weight + optional metric | Add `buildBpmChartSeries()` → systolic + diastolic + pulse series |
| **DashboardDisplayManager** | Shows weight value + unit label | BP mode: shows systolic/diastolic + pulse headline with AHA color |
| **DashboardMetricsManager** | 12 weight body metrics | BP mode: AHA rating, 3-reading average, individual readings |
| **DashboardMetricsCalculator** | `getCurrentAverageWeight()` | BP mode: `getCurrentAverageBP()` → average systolic/diastolic/pulse |
| **DashboardChartManager** | Y-axis caching for weight range | Same — Y-axis calculator works for any numeric range |
| **DashboardStreakManager** | Weight streak data | BP mode: BP streak data (from same EntryService) |
| **DashboardGoalManager** | Weight goal tracking | **BP mode: completely disabled** — no goal card, no goal chip on chart, no goal horizontal line. `hasGoalSet` returns `false` for BP. Goal is a weight-only feature. |
| **DashboardGridEditingManager** | Edit mode for weight metric grid | Same — works with any `MetricItem` array |
| **DashboardLifecycleManager** | Init sequence for weight | Parameterized init with `entryType`; same lifecycle patterns |
| **BaseGraphView** | 1 weight line + optional metric line | BP mode: 3 lines + 2 reference lines (via product-type config) |
| **BaseSectionViewModel** | Crosshair shows weight value | BP mode: crosshair shows systolic/diastolic/pulse |

---

## 7. Dashboard Composition Strategy

### 7.1 Multi-Device Dashboard (Snapshot Cards)

When `productTypeStore.availableItems.count > 1`, the root `DashboardScreen` shows snapshot cards instead of a full dashboard.

**Snapshot Card Behavior (per Figma "Me. Dash - Two Devices"):**
- Each card shows a **non-interactive, static graph** of the last 7 days
- Weight snapshot: week average + mini weight trend line
- BPM snapshot: last reading (systolic/diastolic/pulse) + mini three-line graph
- Tap action: `productTypeStore.select(item)` then navigate to that product's full dashboard

**Implementation:**

```swift
// DashboardScreen.swift — top-level routing
var body: some View {
    if productTypeStore.availableItems.count > 1 && !isInProductDashboard {
        MultiDeviceSnapshotView(store: productTypeStore) { selectedItem in
            productTypeStore.select(selectedItem)
            isInProductDashboard = true
        }
    } else {
        // SAME DashboardStore — configured with the correct product type
        ProductDashboardView(dashboardStore: dashboardStore)
    }
}
```

**Snapshot card data sources (from same EntryService):**
- Weight snapshot: `entryService.dailySummaries` (already published, filtered by `entryType == .wg`)
- BPM snapshot: `entryService.bpmDailySummaries` (new publisher, filtered by `entryType == .bpm`)

### 7.2 Product Dashboard View (Unified)

A single `ProductDashboardView` renders differently based on the store's `productType`:

```swift
struct ProductDashboardView: View {
    @ObservedObject var dashboardStore: DashboardStore

    var body: some View {
        VStack(spacing: 0) {
            // Header — same for both
            NavbarHeaderView(...)

            ScrollView(.vertical) {
                switch dashboardStore.productType {
                case .wg:
                    WeightTrendView(store: dashboardStore)      // existing
                    DashboardMetricsSection(store: dashboardStore) // existing — weight metrics
                case .bpm:
                    BpmTrendView(store: dashboardStore)          // new — BP headline + graph
                    BpmMetricsSection(store: dashboardStore)     // new — AHA, 3-reading avg, streaks
                }
            }
        }
    }
}
```

### 7.3 BPM Dashboard Layout (per Figma)

```
┌─────────────────────────────────────────┐
│ NavbarHeaderView (< back | "My BP" ∨)   │
├─────────────────────────────────────────┤
│ BpmDisplayView                          │
│  mmhg          pulse                    │
│  111/77        65                       │
│  jun 29 - jul 5, 2025                  │
├─────────────────────────────────────────┤
│ GraphView (reused — configured for BP)  │
│  ┌─────────────────────────────┐ 140   │
│  │ ▬▬▬ systolic line (green)   │       │
│  │ ─── ref line @ 120         │ 110   │
│  │ ▬▬▬ diastolic line (green)  │       │
│  │ ─── ref line @ 80          │  80   │
│  │ ▬▬▬ pulse line (gray)      │       │
│  └─────────────────────────────┘  50   │
│  sun  mon  tue  wed  thu  fri  sat     │
│ [WEEK] [MONTH] [YEAR] [TOTAL]          │
├─────────────────────────────────────────┤
│ BpmMetricsSection (draggable/editable)  │
│  ┌──────────────────────────────────┐   │
│  │ ThreeReadingAverageCard          │   │
│  │  113/74  63  "three entry avg"   │   │
│  ├──────────────────────────────────┤   │
│  │ AhaRatingCard (tap → sheet)      │   │
│  ├──────────────────────────────────┤   │
│  │ BpmStreakSection                 │   │
│  │  ⚡ 1 day current  🔥 10 day     │   │
│  └──────────────────────────────────┘   │
│  (NO goal card — goal is weight-only)   │
└─────────────────────────────────────────┘
```

---

## 8. Product Type Switching Strategy

### 8.1 Current Flow (Already Working)

```
User taps header title → ProductTypeSelectorSheet opens
  → User taps item → ProductTypeStore.select(item) → sheet dismisses
  → All subscribers react:
    - DashboardScreen: switches dashboard view
    - ManualEntryScreen: switches form fields
    - HistoryStore: reloads with entryType-filtered data
```

### 8.2 DashboardStore Product Type Awareness

```swift
// DashboardStore.swift — ADD property
@Published var productType: EntryType = .wg

// When product type changes:
func switchProductType(to newType: EntryType) {
    guard productType != newType else { return }
    productType = newType
    // Re-configure data manager to bind to correct EntryService publishers
    dataManager.switchDataSource(to: newType)
    // Re-initialize dashboard for new product type
    Task { await lifecycleManager.initializeDashboard() }
}
```

### 8.3 DashboardDataManager Product Type Switching

```swift
// DashboardDataManager.swift — ADD method
func switchDataSource(to entryType: EntryType) {
    cancellables.removeAll()
    switch entryType {
    case .wg:
        entryService.$dailySummaries
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in self?.updateStateFromDailySummaries($0) }
            .store(in: &cancellables)
        entryService.$monthlySummaries
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in self?.updateStateFromMonthlySummaries($0) }
            .store(in: &cancellables)
    case .bpm:
        entryService.$bpmDailySummaries
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in self?.updateStateFromDailySummaries($0) }
            .store(in: &cancellables)
        entryService.$bpmMonthlySummaries
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in self?.updateStateFromMonthlySummaries($0) }
            .store(in: &cancellables)
    }
}
```

**Key insight:** The `DataState` structure is the same for both — `dailySummaries: [BathScaleWeightSummary?]`, `monthlySummaries: [BathScaleWeightSummary?]`. For BPM, the weight field is 0/nil, but systolic/diastolic are populated. The data manager doesn't need to know about product-specific fields — it just caches and pre-sorts.

### 8.4 Changes Needed

**`ProductTypeStore.swift`** — resolve merge conflict, remove sample data, connect to real device data.

**`NavbarHeaderView.swift`** — resolve merge conflict.

**`DashboardScreen.swift`** — **add product type header selector (currently missing)**:

The dashboard currently has an empty navbar with no product switching:
```swift
// CURRENT — no product type selector
private func navbarHeader() -> some View {
    NavbarHeaderView<EmptyView, EmptyView>(canShowBorder: false).zIndex(100)
}
```

It needs to match the pattern used in `ManualEntryScreen` and `HistoryListScreen`:
```swift
// TARGET — matches Manual Entry / History header pattern
@ObservedObject private var productTypeStore = ProductTypeStore.shared
@State private var isProductTypeSelectorPresented = false

private func navbarHeader() -> some View {
    VStack(spacing: 0) {
        NavbarHeaderView<EmptyView, EmptyView>(
            title: productTypeStore.availableItems.count > 1
                ? productTypeStore.selectedItem.dashboardTitle
                : nil, // No title when single product (shows logo instead)
            onTitleTap: productTypeStore.availableItems.count > 1 ? {
                isProductTypeSelectorPresented = true
            } : nil,
            canShowBorder: false,
            canShowTitleChevron: productTypeStore.availableItems.count > 1
        )
        .sheet(isPresented: $isProductTypeSelectorPresented) {
            ProductTypeSelectorSheet(
                store: productTypeStore,
                isPresented: $isProductTypeSelectorPresented,
                title: ProductTypeStrings.selectGraph
            )
        }
    }
    .zIndex(100)
}
```

**Note:** `ManualEntryScreen` uses `ProductTypeStrings.manualEntry` as the sheet title, `HistoryListScreen` uses `ProductTypeStrings.myHistory`, and the dashboard should use `ProductTypeStrings.selectGraph` (per Figma "Select Graph" sheet). A new `dashboardTitle` computed property should be added to `ProductSelection` (like the existing `entryTitle` and `historyTitle`).

**`DashboardScreen.swift`** — also add product type routing:
- Subscribe to `productTypeStore.selectedItem`
- When `.myBloodPressure`: configure `DashboardStore.productType = .bpm`
- When `.myWeight` or `.baby`: configure `DashboardStore.productType = .wg`
- When multiple items available and at root: show snapshot cards

**`ManualEntryScreen.swift`** — switch form based on product type:
```swift
switch productTypeStore.selectedItem {
case .myWeight, .baby:
    // Existing weight + body metrics form (EntryStore)
case .myBloodPressure:
    BpmManualEntryForm(store: bpmEntryStore)
    // bpmEntryStore uses same EntryService, creates Entry with entryType = .bpm
}
```

**`HistoryStore.swift`** — map product selection to entryType:
```swift
let entryType: EntryType = productTypeStore.selectedItem == .myBloodPressure ? .bpm : .wg
let months = try await entryService.getMonthsAll(entryType: entryType)
```

### 8.5 EntryStore / BpmEntryStore — Entry Creation

**Weight entry creation** (existing `EntryStore`, unchanged):
```swift
let entry = Entry(
    entryTimestamp: timestamp,
    accountId: accountId,
    operationType: "create",
    deviceType: DeviceType.scale.rawValue,
    isSynced: false
)
entry.entryType = EntryType.wg.rawValue
entry.scaleEntry = BathScaleEntry(weight: weightStored, bodyFat: bodyFat, ...)
entry.scaleEntryMetric = BathScaleMetric(bmr: bmr, pulse: pulse, ...)
try await entryService.saveNewEntry(entry)
```

**BP entry creation** (new `BpmEntryStore` — lightweight, reuses same `EntryService`):
```swift
let entry = Entry(
    entryTimestamp: timestamp,
    accountId: accountId,
    operationType: "create",
    deviceType: DeviceType.bpm.rawValue,
    isSynced: false
)
entry.entryType = EntryType.bpm.rawValue
entry.scaleEntry = BathScaleEntry(
    systolic: systolicValue,
    diastolic: diastolicValue,
    meanArterial: meanArterialValue,
    source: EntrySource.manual.rawValue,
    note: noteText
)
entry.scaleEntryMetric = BathScaleMetric(pulse: pulseValue)
try await entryService.saveNewEntry(entry)  // SAME EntryService
```

### 8.6 Switching Affects

| Screen | Weight Selected | BPM Selected |
|--------|----------------|-------------|
| Dashboard | `WeightTrendView` + 12-metric grid + **goal card** + streaks | `BpmTrendView` + AHA/3-reading avg + streaks (**no goal card** — goal is weight-only) |
| Manual Entry | Weight + 12 body metrics form (`EntryStore`) | Systolic + diastolic + pulse form (`BpmEntryStore`) |
| History | Weight entries (`entryType == .wg`) | BP entries (`entryType == .bpm`) |
| Header | "My Weight" or baby name | "My Blood Pressure" |
| Graph | 1 weight line + optional body metric | 3 lines: systolic, diastolic, pulse |
| Metric Cards | Body metrics (draggable, editable) | BP cards: AHA, 3-reading avg, streaks (draggable, editable) |
| EntryService data | `dailySummaries` / `monthlySummaries` | `bpmDailySummaries` / `bpmMonthlySummaries` |

---

## 9. BP Graph Strategy — Extending Existing Infrastructure

### 9.1 Three-Series Graph via GraphDataPreparer

The existing `GraphDataPreparer.buildChartSeries()` builds weight + optional metric series. Add a new method for BP:

```swift
// GraphDataPreparer.swift — ADD method
func buildBpmChartSeries(
    from operations: [BathScaleWeightSummary]
) -> [GraphSeries] {
    guard !operations.isEmpty else { return [] }

    var series: [GraphSeries] = []

    for op in operations {
        if let sys = op.systolic {
            series.append(GraphSeries(date: op.date, value: sys, series: "systolic"))
        }
        if let dia = op.diastolic {
            series.append(GraphSeries(date: op.date, value: dia, series: "diastolic"))
        }
        if let pulse = op.pulse {
            series.append(GraphSeries(date: op.date, value: pulse, series: "pulse"))
        }
    }

    return series
}
```

**Why this works:** `GraphSeries` already supports named series (`series: String`). The existing `BaseGraphView` already groups by series name for rendering. Adding three named series ("systolic", "diastolic", "pulse") integrates naturally.

### 9.2 DashboardGraphManager — Product-Type Routing

```swift
// DashboardGraphManager.swift — ADD product-type-aware series building
func buildChartSeriesForProductType(
    productType: EntryType,
    operations: [BathScaleWeightSummary],
    // ... weight-specific params for .wg
) -> [GraphSeries] {
    switch productType {
    case .wg:
        return dataPreparer.buildChartSeries(from: operations, ...) // existing
    case .bpm:
        return dataPreparer.buildBpmChartSeries(from: operations) // new
    }
}
```

### 9.3 BaseGraphView — Reference Lines & AHA Colors

Extend `BaseGraphView` to support product-type-specific rendering:

```swift
// BaseGraphView.swift — ADD conditional rendering
Chart {
    // Existing weight series rendering
    ForEach(groupedSeries.keys.sorted(), id: \.self) { seriesName in
        ForEach(groupedSeries[seriesName] ?? []) { point in
            LineMark(x: .value("Date", point.xDate), y: .value("Value", point.original.value))
                .foregroundStyle(lineColor(for: seriesName, dashboardStore: dashboardStore))
        }
    }

    // BP reference lines (only when productType == .bpm)
    if dashboardStore.productType == .bpm {
        RuleMark(y: .value("Systolic Ref", BpmConstants.normalSystolic))
            .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
            .foregroundStyle(theme.textSubheading.opacity(0.4))
        RuleMark(y: .value("Diastolic Ref", BpmConstants.normalDiastolic))
            .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
            .foregroundStyle(theme.textSubheading.opacity(0.4))
    }
}

// Line color function — product-type-aware
func lineColor(for series: String, dashboardStore: DashboardStore) -> Color {
    guard dashboardStore.productType == .bpm else {
        return existingWeightLineColor // existing logic
    }
    switch series {
    case "systolic", "diastolic":
        // AHA-classified color based on latest/selected reading
        let classification = AhaPressureClass.classify(
            systolic: Int(dashboardStore.displaySystolic),
            diastolic: Int(dashboardStore.displayDiastolic)
        )
        return classification.color(theme: theme)
    case "pulse":
        return theme.textSubheading // gray
    default:
        return theme.actionPrimary
    }
}
```

### 9.4 Y-Axis Scaling for BP

The existing `YAxisCalculator` computes a "nice scale" Y-axis from any numeric range. For BP, the input range is different:

```swift
// DashboardChartManager.swift — Y-axis calculation
func calculateYAxisDomain(productType: EntryType, visibleOperations: [BathScaleWeightSummary]) {
    switch productType {
    case .wg:
        // Existing: min/max of weight values + goal weight
        let range = existingWeightRangeCalculation(visibleOperations)
        yAxisDomain = YAxisCalculator.calculate(min: range.min, max: range.max)
    case .bpm:
        // New: min/max across systolic, diastolic, pulse
        let allValues = visibleOperations.flatMap { op -> [Double] in
            [op.systolic, op.diastolic, op.pulse].compactMap { $0 }
        }
        guard let minVal = allValues.min(), let maxVal = allValues.max() else {
            yAxisDomain = BpmConstants.defaultYMin...BpmConstants.defaultYMax
            return
        }
        yAxisDomain = YAxisCalculator.calculate(
            min: minVal - BpmConstants.yAxisPadding,
            max: maxVal + BpmConstants.yAxisPadding
        )
    }
}
```

### 9.5 Selection / Crosshair — Whole-Graph AHA Recoloring

**Critical Figma behavior:** When the user taps a point on the BP graph, the AHA color of that selected reading is applied to the **entire graph** — all systolic/diastolic lines, all plotted points, the headline values, and the focus circles all change to match the selected point's AHA classification. This is NOT per-point coloring; it's whole-graph recoloring on selection.

**Figma examples (from "Dashboard Display Metrics"):**
- Selected point 111/77 → Normal → **entire graph turns green** (lines, points, headline)
- Selected point 121/82 → Elevated → **entire graph turns dark yellow/olive** (lines, points, headline)
- Selected point 180/122 → Hypertensive Crisis → **entire graph turns red** (lines, points, headline)

**Pulse line is always gray** regardless of the selected AHA classification.

**Angular reference pattern** (`graph.service.ts:570-592`):
```typescript
// Angular app does exactly this in setLineColor():
private setLineColor(data: BP) {
    const pressureColor = getPressureClassForGraph(data.systolic, data.diastolic);
    // Recolor ALL systolic/diastolic lines
    systolicLine.style.stroke = pressureColor;
    diastolicLine.style.stroke = pressureColor;
    // Recolor ALL systolic/diastolic circles (plotted points)
    pressureCircles.forEach(circle => circle.style.fill = pressureColor);
    // Recolor ALL focus circles
    pressureFocusCircles.forEach(circle => circle.style.stroke = pressureColor);
}
```

**iOS implementation in `BaseGraphView`:**

The graph needs a `@State` (or store-driven) AHA classification that updates on every selection:

```swift
// DashboardStore / DashboardDisplayManager — ADD
@Published var currentBpmClassification: AhaPressureClass = .normal

func handleBpmPointSelection(_ point: BathScaleWeightSummary) {
    guard let sys = point.systolic, let dia = point.diastolic else { return }
    currentBpmClassification = AhaPressureClass.classify(
        systolic: Int(sys), diastolic: Int(dia)
    )
}
```

```swift
// BaseGraphView — line color driven by store's currentBpmClassification
func lineColor(for series: String) -> Color {
    guard dashboardStore.productType == .bpm else { return existingWeightColor }
    switch series {
    case "systolic", "diastolic":
        // ALL systolic/diastolic lines use the SAME AHA color
        // This color comes from the currently selected/focused point
        return dashboardStore.currentBpmClassification.color(theme: theme)
    case "pulse":
        return theme.textSubheading // always gray
    default:
        return theme.actionPrimary
    }
}
```

```swift
// BpmDisplayView — headline also uses currentBpmClassification
HStack {
    // mmhg section
    Text("\(selectedSystolic)")
        .foregroundColor(classification.color(theme: theme))  // AHA color
    Text("/")
        .foregroundColor(classification.color(theme: theme))
    Text("\(selectedDiastolic)")
        .foregroundColor(classification.color(theme: theme))

    // pulse section — always gray
    Text("\(selectedPulse)")
        .foregroundColor(theme.textSubheading)
}
```

**Default (no selection / initial load):** Uses the AHA classification of the **last entry** (most recent reading), matching the Angular app's `setBpGraphColor()` behavior which uses `lastEntry.systolic` / `lastEntry.diastolic` for the initial color.

**On point selection flow:**
1. User taps a point → `handleChartSelection()` finds nearest `BathScaleWeightSummary`
2. `handleBpmPointSelection(point)` updates `currentBpmClassification`
3. `BaseGraphView` re-renders: all systolic/diastolic `LineMark` + `PointMark` use the new AHA color
4. `BpmDisplayView` re-renders: headline systolic/diastolic values show in AHA color
5. Crosshair line and focus circles also use AHA color
6. Pulse line and pulse value remain gray throughout

**On period switch / scroll clear:**
When selection is cleared (scroll start, period change), the color reverts to the last-entry classification.

The `BaseSectionViewModel.handleChartSelection()` currently finds the nearest weight data point. For BP, it finds the nearest point and displays all three values:

```swift
// BaseSectionViewModel.swift or DashboardDisplayManager.swift
// Add product-type-aware selection display
func selectionDisplay(for point: BathScaleWeightSummary, productType: EntryType) -> SelectionData {
    switch productType {
    case .wg:
        return .weight(value: point.weight, unit: currentUnit) // existing
    case .bpm:
        return .bpm(
            systolic: Int(point.systolic ?? 0),
            diastolic: Int(point.diastolic ?? 0),
            pulse: Int(point.pulse ?? 0),
            classification: AhaPressureClass.classify(
                systolic: Int(point.systolic ?? 0),
                diastolic: Int(point.diastolic ?? 0)
            )
        )
    }
}
```

### 9.6 Average Calculation for BP

The existing `DashboardMetricsCalculator.getCurrentAverageWeight()` averages visible weight values. Add BP average:

```swift
// DashboardMetricsCalculator.swift — ADD method
func getCurrentAverageBP(from visibleOperations: [BathScaleWeightSummary]) -> BpmAverage? {
    let validOps = visibleOperations.filter { $0.systolic != nil }
    guard !validOps.isEmpty else { return nil }

    let avgSystolic = validOps.compactMap(\.systolic).reduce(0, +) / Double(validOps.count)
    let avgDiastolic = validOps.compactMap(\.diastolic).reduce(0, +) / Double(validOps.count)
    let avgPulse = validOps.compactMap(\.pulse).reduce(0, +) / Double(validOps.count)

    return BpmAverage(
        systolic: Int(round(avgSystolic)),
        diastolic: Int(round(avgDiastolic)),
        pulse: Int(round(avgPulse)),
        classification: AhaPressureClass.classify(
            systolic: Int(round(avgSystolic)),
            diastolic: Int(round(avgDiastolic))
        )
    )
}
```

### 9.7 Three-Reading Average Logic

From the Angular app pattern:

```swift
// DashboardMetricsCalculator.swift — ADD method
func getThreeReadingAverage(entryService: EntryServiceProtocol) async -> ThreeReadingAverage? {
    let entries = try? await entryService.getEntries(lastNDays: 365, entryType: .bpm)
    let lastN = Array((entries ?? []).suffix(BpmConstants.readingAverageCount))

    guard !lastN.isEmpty else { return nil }

    let avgSys = lastN.compactMap { $0.scaleEntry?.systolic }.map(Double.init).average()
    let avgDia = lastN.compactMap { $0.scaleEntry?.diastolic }.map(Double.init).average()
    let avgPulse = lastN.compactMap { $0.scaleEntryMetric?.pulse }.map(Double.init).average()

    return ThreeReadingAverage(
        systolic: Int(round(avgSys)),
        diastolic: Int(round(avgDia)),
        pulse: Int(round(avgPulse)),
        count: lastN.count,
        label: lastN.count == 3 ? "three entry average" :
               lastN.count == 2 ? "two entry average" : "last reading",
        readings: lastN  // for "Last 3 readings" display
    )
}
```

### 9.8 Scroll, Period Switching, and Caching

**No changes needed.** The following infrastructure works identically for BP:

- `PagedChartScrollBehavior` — paged scrolling with snap boundaries
- `GraphRenderingConfiguration` — X-axis ticks (day/week/month/data-driven), date formatting
- `GraphAnimationManager` — period transition debouncing, chart data throttle
- `GraphInteractionHandler` — scroll position buffering, visible ops cache
- `DashboardChartManager` — chart initialization, scroll end handling
- Period-specific ViewModels — selection rules, snap boundaries
- Y-axis caching during scroll — same pattern (cached during scroll, refreshed on scroll-end)
- Cache invalidation heuristics — same multi-key invalidation approach

### 9.9 Single-Entry Handling

The existing graph adds a dummy point at period start when there's only one entry. Same behavior applies to BP — each of the three series gets a dummy point at period start.

### 9.10 Weight Graph vs BP Graph — Feature Comparison

| Feature | Weight Graph | BP Graph | Implementation |
|---------|-------------|----------|----------------|
| **Series count** | 1 (weight) + 1 optional (metric) | 3 (systolic, diastolic, pulse) | `GraphDataPreparer.buildBpmChartSeries()` |
| **Line colors** | Single color (theme primary) | **Whole-graph AHA recoloring** — when a point is selected, ALL systolic/diastolic lines, points, headline values change to that point's AHA color (green→yellow→orange→red). Pulse always gray. Default = last entry's AHA color. | `currentBpmClassification` on store, drives `lineColor()` + `BpmDisplayView` |
| **Reference lines** | Goal weight horizontal line | Systolic=120, Diastolic=80 horizontal dashed lines (**no goal line**) | Conditional `RuleMark` in `BaseGraphView` |
| **Goal card / goal chip** | Goal progress card below chart + goal chip overlay on chart | **None** — goal is a weight-only feature; hidden entirely for BP | `DashboardGoalManager.hasGoalSet` returns `false` for BP |
| **Y-axis range** | Weight range (50-400 lbs / 20-180 kg) | BP range (40-200 mmHg) | `YAxisCalculator` with different inputs |
| **Selection display** | Weight value + unit | Systolic/Diastolic + Pulse + AHA color | Product-type-aware `selectionDisplay()` |
| **Average** | Weight average of visible period | Systolic/Diastolic/Pulse averages | `getCurrentAverageBP()` |
| **Interpolation** | Hermite spline for empty days | No interpolation (show "no data") | Skip for BP |
| **Scroll** | Same | Same | Reused as-is |
| **Period switching** | Same | Same | Reused as-is |
| **Skeleton loading** | Same | Same | Reused as-is |
| **Point selection** | Same crosshair UX | Same crosshair UX (3 values shown) | Extended selection handler |

---

## 10. Metric Cards Strategy — Draggable & Editable for BP

### 10.1 Reusing DashboardMetricsSection & MetricCardView

The existing metric card infrastructure supports any `[MetricItem]` array with drag-drop reordering. For BP, we define BP-specific metrics:

```swift
// DashboardMetricsManager.swift — ADD BP metric definitions
func getBpmMetrics() -> [MetricItem] {
    return [
        MetricItem(value: "113/74", label: "Blood Pressure", unit: "mmhg", icon: "heart"),
        MetricItem(value: "63", label: "Pulse", unit: "bpm", icon: "pulse"),
        MetricItem(value: "Normal", label: "AHA Rating", unit: "", icon: "aha"),
    ]
}
```

### 10.2 BPM Metric Cards Layout

Per the Figma designs, BP dashboard below the graph has:

| Card | Behavior | Implementation |
|------|----------|----------------|
| **Three-Reading Average** | Shows avg systolic/diastolic/pulse with AHA color. Tappable → opens detail sheet with last 3 readings. | `ThreeReadingAverageCard` — rendered as a full-width card |
| **AHA Rating Card** | Shows current AHA classification with color bar. Tappable → opens `AhaRatingSheet`. | `AhaRatingCard` — uses existing `AhaRatingSheet` |
| **Current/Longest Streak** | Same pattern as weight streaks. | Reused `StreakCardView` with BP streak data |
| **Last 3 Individual Readings** | Shown inside the three-reading average detail sheet. Each row shows systolic/diastolic + pulse with AHA color. | `BpmReadingCard` rows in a detail sheet |

### 10.3 Drag-Drop & Edit Mode

The existing `DashboardGridEditingManager` manages edit state for the metric grid. For BP:

- **Same edit toggle** — long press or edit button enters wiggle mode
- **Same drag-drop** — `DragDropModifier` + `ReorderDropDelegate` work with any `MetricItem`
- **Same removals** — tap to remove a card, save/reset/cancel flows
- **Order persistence** — `DashboardSyncCoordinator` saves card order to API (same endpoint, different metric labels for BP)

```swift
// DashboardMetricsManager.swift — product-type-aware metric loading
func loadMetrics(for productType: EntryType) {
    switch productType {
    case .wg:
        // Existing 12 weight body metrics
        state.metrics = getWeightMetrics()
    case .bpm:
        // BP-specific cards
        state.metrics = getBpmMetrics()
    }
}
```

---

## 11. State / Store Changes

### 11.1 Modified Stores

| Store | Change |
|-------|--------|
| `DashboardStore` | Add `productType: EntryType` property; `switchProductType()` method; product-type-aware computed properties |
| `DashboardState` | Add `productType: EntryType` to DataState for persistence; BP-specific display state (systolic, diastolic, pulse, AHA classification) |

### 11.2 Modified Managers (Existing — Extended)

| Manager | Change |
|---------|--------|
| `DashboardDataManager` | `switchDataSource(to: EntryType)` — rebinds to correct EntryService publishers |
| `DashboardGraphManager` | `buildChartSeriesForProductType()` — routes to weight or BP series builder |
| `GraphDataPreparer` | Add `buildBpmChartSeries()` method |
| `DashboardDisplayManager` | Product-type-aware `displayWeight` → `displayValue`, date labels, Y-axis tick formatting |
| `DashboardMetricsManager` | `loadMetrics(for: EntryType)` — weight or BP metric definitions |
| `DashboardMetricsCalculator` | Add `getCurrentAverageBP()`, `getThreeReadingAverage()` |
| `DashboardChartManager` | Product-type-aware Y-axis calculation |
| `DashboardStreakManager` | Product-type-aware streak data source |
| `DashboardGoalManager` | Return nil/disabled for BP (no BP goals yet) |
| `DashboardLifecycleManager` | Parameterized `initializeDashboard(productType:)` |
| `DashboardSyncCoordinator` | Product-type-aware API sync |

### 11.3 Modified Models

| Model | Change |
|-------|--------|
| `Entry.swift` | Add `entryType: String` field (default `"wg"`) |
| `BathScaleEntry.swift` | Add `systolic: Int?`, `diastolic: Int?`, `meanArterial: Int?`, `note: String?` |
| `BathScaleWeightSummary` | Add `systolic: Double?`, `diastolic: Double?`, `meanArterial: Double?`, `entryType: String?` |
| `DashboardState.swift` | Add product-type discriminator and BP display fields |
| `GraphSeries.swift` | No changes — already supports named series |

### 11.4 Modified Services

| Service | Change |
|---------|--------|
| `EntryServiceProtocol.swift` | Add `entryType` parameter to query methods; add `bpmDailySummaries` / `bpmMonthlySummaries` publishers |
| `EntryService.swift` | Add `entryType` filtering to aggregation + queries; add BPM publishers |

### 11.5 Modified Views

| View | Change |
|------|--------|
| `DashboardScreen.swift` | Add product type routing, snapshot view, product-type-aware store config |
| `BaseGraphView.swift` | Add BP reference lines (RuleMark), product-type-aware line colors |
| `BaseSectionViewModel.swift` | Product-type-aware selection display |
| `WeightTrendView.swift` | Refactor to be weight-specific; add sibling `BpmTrendView` |
| `DashboardMetricsSection.swift` | Product-type-aware metric array |
| `ManualEntryScreen.swift` | Switch form based on product type |
| `NavBarHeaderView.swift` | Resolve merge conflict |

### 11.6 New Files (Minimal — Only Product-Specific Views)

| Path | Type | Description |
|------|------|-------------|
| `Domain/Models/Domain/Entry/EntryType.swift` | Enum | `wg` / `bpm` discriminator |
| `Domain/Models/Domain/BP/BpmAverage.swift` | Model | Three-reading average data structure |
| `Features/Dashboard/BPM/Views/Screens/BpmTrendView.swift` | View | BP headline (systolic/diastolic/pulse) + GraphView + period selector |
| `Features/Dashboard/BPM/Views/Components/BpmDisplayView.swift` | View | Systolic/diastolic/pulse headline with AHA colors |
| `Features/Dashboard/BPM/Views/Components/BpmMetricsSection.swift` | View | BP-specific metric cards section (draggable/editable) |
| `Features/Dashboard/BPM/Views/Components/ThreeReadingAverageCard.swift` | View | 3-reading average display card |
| `Features/Dashboard/BPM/Views/Components/ThreeReadingAverageSheet.swift` | View | Detail sheet with "Why We Take an Average" + last 3 readings |
| `Features/Dashboard/BPM/Views/Components/AhaRatingCard.swift` | View | Compact AHA rating card (tappable → AhaRatingSheet) |
| `Features/Dashboard/BPM/Views/Components/BpmReadingCard.swift` | View | Individual BP reading row with AHA color |
| `Features/Dashboard/BPM/Views/Components/BpmSnapshotCard.swift` | View | Mini graph card for multi-device dashboard |
| `Features/Dashboard/Views/Screens/MultiDeviceSnapshotView.swift` | View | Snapshot cards container |
| `Features/Dashboard/Views/Components/WeightSnapshotCard.swift` | View | Mini weight graph card |
| `Features/Entry/BPM/Stores/BpmEntryStore.swift` | Store | BP manual entry form state (uses same EntryService) |
| `Features/Entry/BPM/Forms/BpmManualEntryForm.swift` | Form | Systolic/diastolic/pulse validation |
| `Features/Entry/BPM/Views/Screens/BpmManualEntryScreen.swift` | View | BP manual entry screen |
| `Features/History/BPM/Views/Components/BpmHistoryRow.swift` | View | BP entry row in history list |

**Note:** No new stores, no new managers, no new graph managers. All logic lives in the extended existing managers.

---

## 12. Navigation Changes

### 12.1 Multi-Device Dashboard → Product Dashboard

```
DashboardScreen
  ├─ [Multiple products] → MultiDeviceSnapshotView
  │   ├─ WeightSnapshotCard → tap → selectWeight() + show weight dashboard
  │   └─ BpmSnapshotCard → tap → selectBpm() + show BPM dashboard
  │       (back button returns to snapshot dashboard)
  │
  ├─ [Weight selected only] → WeightTrendView + DashboardMetricsSection (existing)
  └─ [BPM selected only] → BpmTrendView + BpmMetricsSection (new)
```

### 12.2 Header Behavior

| Context | Header Shows |
|---------|-------------|
| Multi-device snapshot dashboard | App logo (no product selector) |
| Weight dashboard (navigated from snapshot) | "< back" + "My Weight ∨" |
| BPM dashboard (navigated from snapshot) | "< back" + "My BP ∨" |
| Single product (only weight) | No dropdown, default title |
| Single product (only BPM) | No dropdown, "My Blood Pressure" |

---

## 13. Step-by-Step Implementation Plan

### Phase 1: Data Foundation (1-2 weeks)

**Goal:** BP data can be stored, queried, and aggregated locally using the existing Entry model and EntryService.

| Step | Task | Files |
|------|------|-------|
| 1.1 | Create `EntryType` enum (`wg`/`bpm`) | `Domain/Models/Domain/Entry/EntryType.swift` |
| 1.2 | Add `entryType: String` to `Entry` (default `"wg"`) | `Domain/Models/DB/Entry.swift` |
| 1.3 | Add `systolic`, `diastolic`, `meanArterial`, `note` to `BathScaleEntry` | `Domain/Models/DB/BathScaleEntry.swift` |
| 1.4 | Add `systolic`, `diastolic`, `meanArterial`, `entryType` to `BathScaleWeightSummary` | `Domain/Models/DB/DailyWeightSummary.swift` |
| 1.5 | Add `entryType` parameter to `EntryServiceProtocol` query methods | `Domain/Services/EntryServiceProtocol.swift` |
| 1.6 | Add `bpmDailySummaries`/`bpmMonthlySummaries` publishers to `EntryServiceProtocol` | `Domain/Services/EntryServiceProtocol.swift` |
| 1.7 | Implement `entryType` filtering in `EntryService` — aggregation, queries, dashboard data loading | `Data/Services/EntryService.swift` |
| 1.8 | Create `BpmAverage` model (systolic, diastolic, pulse, classification, count, label) | `Domain/Models/Domain/BP/BpmAverage.swift` |
| 1.9 | Update `Entry.toOperationDTO()` to include BP fields | `Domain/Models/DB/Entry.swift` |
| 1.10 | Update `BathScaleEntry.init(from dto:)` to handle BP fields | `Domain/Models/DB/BathScaleEntry.swift` |
| 1.11 | Write unit tests for `EntryType` filtering, BP aggregation, `AhaPressureClass` boundaries | `meAppTests/` |

**Exit criteria:** Can save a BP entry via existing `EntryService`, query BP entries by `entryType`, and get daily/monthly BP summaries with systolic/diastolic/pulse averages.

### Phase 2: Product-Type-Aware DashboardStore (1-2 weeks)

**Goal:** Existing `DashboardStore` and managers can operate in both weight and BPM modes.

| Step | Task | Files |
|------|------|-------|
| 2.1 | Add `productType: EntryType` property to `DashboardStore` | `Stores/DashboardStore.swift` |
| 2.2 | Add `switchProductType(to:)` method to `DashboardStore` | `Stores/DashboardStore.swift` |
| 2.3 | Add `switchDataSource(to:)` to `DashboardDataManager` — rebinds publishers | `Managers/DashboardDataManager.swift` |
| 2.4 | Add `buildBpmChartSeries()` to `GraphDataPreparer` | `Managers/Graph/GraphDataPreparer.swift` |
| 2.5 | Add product-type routing to `DashboardGraphManager.buildChartSeriesForProductType()` | `Managers/DashboardGraphManager.swift` |
| 2.6 | Add `getCurrentAverageBP()` and `getThreeReadingAverage()` to `DashboardMetricsCalculator` | `Managers/DashboardMetricsCalculator.swift` |
| 2.7 | Add product-type-aware Y-axis calculation to `DashboardChartManager` | `Managers/DashboardChartManager.swift` |
| 2.8 | Add product-type-aware display logic to `DashboardDisplayManager` | `Managers/DashboardDisplayManager.swift` |
| 2.9 | Add `loadMetrics(for:)` to `DashboardMetricsManager` — returns BP or weight metrics | `Managers/DashboardMetricsManager.swift` |
| 2.10 | Add product-type-aware streak to `DashboardStreakManager` | `Managers/DashboardStreakManager.swift` |
| 2.11 | Disable goal entirely for BP in `DashboardGoalManager` — `hasGoalSet` returns `false`, goal card hidden, goal chip hidden, goal horizontal line hidden. Goal is a weight-only feature. | `Managers/DashboardGoalManager.swift` |
| 2.12 | Parameterize `DashboardLifecycleManager.initializeDashboard()` with product type | `Managers/DashboardLifecycleManager.swift` |
| 2.13 | Write unit tests for product-type switching, data source rebinding | `meAppTests/` |

**Exit criteria:** `DashboardStore.switchProductType(.bpm)` correctly rebinds to BPM publishers, calculates BP averages, and returns BP metric items.

### Phase 3: BP Graph Rendering (1-2 weeks)

**Goal:** `BaseGraphView` renders three-line BP graph with reference lines and AHA colors.

| Step | Task | Files |
|------|------|-------|
| 3.1 | Add BP reference lines (RuleMark at 120/80) to `BaseGraphView` | `Views/Components/BaseGraphView.swift` |
| 3.2 | Add `currentBpmClassification` to `DashboardDisplayManager` — updated on point selection, defaults to last entry's AHA class | `Managers/DashboardDisplayManager.swift` |
| 3.3 | Add whole-graph AHA recoloring to `BaseGraphView` — all systolic/diastolic lines, points, and focus circles use `currentBpmClassification` color; pulse always gray. Color changes on every point tap. | `Views/Components/BaseGraphView.swift` |
| 3.4 | Add product-type-aware selection display to `BaseSectionViewModel` — calls `handleBpmPointSelection()` to update classification on tap | `ViewModels/BaseSectionViewModel.swift` |
| 3.5 | Disable Hermite interpolation for BP in `GraphDataPreparer` | `Managers/Graph/GraphDataPreparer.swift` |
| 3.6 | Create `BpmTrendView` (BP headline + GraphView + period selector) | `BPM/Views/Screens/BpmTrendView.swift` |
| 3.7 | Create `BpmDisplayView` — headline systolic/diastolic in AHA color, pulse in gray; color driven by `currentBpmClassification` | `BPM/Views/Components/BpmDisplayView.swift` |
| 3.8 | Test all 4 periods (week/month/year/total) with BP data; verify AHA recoloring on point selection across all periods | Manual testing + unit tests |

**Exit criteria:** Three-line BP graph renders in all periods with scroll, selection, reference lines, and AHA color coding.

### Phase 4: BP Dashboard Views & Metric Cards (1 week)

**Goal:** Full BP dashboard with draggable/editable metric cards.

| Step | Task | Files |
|------|------|-------|
| 4.1 | Create `ThreeReadingAverageCard` | `BPM/Views/Components/ThreeReadingAverageCard.swift` |
| 4.2 | Create `ThreeReadingAverageSheet` (detail with last 3 readings) | `BPM/Views/Components/ThreeReadingAverageSheet.swift` |
| 4.3 | Create `AhaRatingCard` (compact, tappable → existing `AhaRatingSheet`) | `BPM/Views/Components/AhaRatingCard.swift` |
| 4.4 | Create `BpmReadingCard` (individual reading row) | `BPM/Views/Components/BpmReadingCard.swift` |
| 4.5 | Create `BpmMetricsSection` (grid of BP cards — draggable/editable) | `BPM/Views/Components/BpmMetricsSection.swift` |
| 4.6 | Add product type routing to `DashboardScreen` (weight vs BPM dashboard) | `Views/Screens/DashboardScreen.swift` |
| 4.7 | Wire `DashboardGridEditingManager` for BP cards | Existing manager, no changes |
| 4.8 | Write unit tests for three-reading average, AHA rating display | `meAppTests/` |

**Exit criteria:** BPM dashboard shows graph + draggable/editable metric cards (AHA, 3-reading avg, streaks).

### Phase 5: BP Manual Entry (1 week)

**Goal:** User can manually enter systolic/diastolic/pulse readings.

| Step | Task | Files |
|------|------|-------|
| 5.1 | Create `BpmManualEntryForm` with validation (sys 60-250, dia 30-150 & < systolic, pulse 20-200) | `Features/Entry/BPM/Forms/BpmManualEntryForm.swift` |
| 5.2 | Create `BpmEntryStore` — saves via `entryService.saveNewEntry()` with `entryType = .bpm` | `Features/Entry/BPM/Stores/BpmEntryStore.swift` |
| 5.3 | Create `BpmManualEntryScreen` | `Features/Entry/BPM/Views/Screens/BpmManualEntryScreen.swift` |
| 5.4 | Modify `ManualEntryScreen` to switch form based on product type | `Features/Entry/Views/Screens/ManualEntryScreen.swift` |
| 5.5 | Write unit tests for form validation, store save flow | `meAppTests/Features/Entry/BPM/` |

**Exit criteria:** User can select "My Blood Pressure", enter BP readings, save them via existing EntryService.

### Phase 6: Product Type Switching & Multi-Device (1 week)

**Goal:** Seamless switching and snapshot cards.

| Step | Task | Files |
|------|------|-------|
| 6.1 | Resolve `ProductTypeStore` merge conflicts, connect to real device data | `ProductTypeStore.swift` |
| 6.2 | Resolve `NavBarHeaderView` merge conflict | `NavBarHeaderView.swift` |
| 6.3 | **Add product type header selector to `DashboardScreen`** — currently missing (Manual Entry and History already have it). Add `ProductTypeStore` observation, `NavbarHeaderView` with title/chevron/onTitleTap, and `ProductTypeSelectorSheet` with "Select Graph" title. Add `dashboardTitle` to `ProductSelection` and `selectGraph` to `ProductTypeStrings`. | `DashboardScreen.swift`, `ProductSelection.swift`, `ProductTypeStrings.swift` |
| 6.4 | Add `entryType` filtering in `HistoryStore` | `HistoryStore.swift` |
| 6.5 | Create `BpmHistoryRow` for BP entries in history list | `Features/History/BPM/Views/` |
| 6.6 | Create `WeightSnapshotCard` (mini weight trend) | `Features/Dashboard/Views/Components/` |
| 6.7 | Create `BpmSnapshotCard` (mini three-line graph) | `Features/Dashboard/BPM/Views/Components/` |
| 6.8 | Create `MultiDeviceSnapshotView` (card container) | `Features/Dashboard/Views/Screens/` |
| 6.9 | Add snapshot routing to `DashboardScreen` | `DashboardScreen.swift` |
| 6.10 | Write unit tests for product switching, history filtering, header selector | `meAppTests/` |

**Exit criteria:** Multi-device snapshot dashboard shows both products; tapping navigates correctly. Switching updates all screens.

### Phase 7: API Integration (1-2 weeks, backend-dependent)

**Goal:** BP data syncs with backend.

| Step | Task | Files |
|------|------|-------|
| 7.1 | Add BPM endpoints to `EndPoints.swift` | `Domain/Models/API/EndPoints.swift` |
| 7.2 | Update `BathScaleOperationDTO` to include BP fields | `Domain/Models/API/` |
| 7.3 | Add sync logic for BPM entries in `EntryService` | `Data/Services/EntryService.swift` |
| 7.4 | Add BPM device registration flow | Device setup feature |
| 7.5 | Write integration tests for API sync | `meAppTests/` |

**Exit criteria:** BP entries sync to/from backend. BPM device registration adds device to `ProductTypeStore`.

---

## 14. Testing Strategy

### 14.1 Unit Tests

**Models & Logic (target: 85% coverage):**

| Test Suite | Tests |
|------------|-------|
| `AhaPressureClassTests` | All 5 levels, boundary values (119/79 → normal, 120/79 → elevated, 130/80 → stage1, 140/90 → stage2, 181/121 → crisis) |
| `EntryTypeFilteringTests` | `entryType == .wg` returns only weight entries; `entryType == .bpm` returns only BP; nil entryType defaults to wg |
| `BpmAggregationTests` | Daily/monthly aggregation of systolic/diastolic/pulse averages from BathScaleWeightSummary |
| `BpmMetricsCalculatorTests` | Three-reading average (3, 2, 1 entries), rounding, empty state; `getCurrentAverageBP()` |
| `BpmManualEntryFormTests` | Range validation (sys 60-250, dia 30-150, pulse 20-200), systolic > diastolic, required fields |

**Stores (target: 80% coverage):**

| Test Suite | Tests |
|------------|-------|
| `DashboardStoreProductTypeSwitchingTests` | `switchProductType(.bpm)` rebinds data, returns BP metrics; switch back to `.wg` restores weight behavior |
| `BpmEntryStoreTests` | Save flow with correct `entryType`, form validation, reset, product type subscription |
| `ProductTypeStoreTests` | Switching, persistence, rebuild from devices, fallback behavior |
| `HistoryStoreTests` | `entryType` filtering on product switch, reload |

**EntryService (target: 85% coverage):**

| Test Suite | Tests |
|------------|-------|
| `EntryServiceBpmTests` | Save BP entry, load dashboard data with `entryType = .bpm`, aggregation with BP fields, `bpmDailySummaries` publisher emits correctly |
| `EntryServiceFilteringTests` | `getMonthsAll(entryType: .wg)` excludes BPM entries; `getMonthsAll(entryType: .bpm)` excludes weight entries; backward compat for nil entryType |

**Managers (target: 80% coverage):**

| Test Suite | Tests |
|------------|-------|
| `DashboardDataManagerSwitchingTests` | `switchDataSource(.bpm)` rebinds to BPM publishers; data flows through correctly |
| `GraphDataPreparerBpmTests` | `buildBpmChartSeries()` — three series, empty data, single point, year aggregation |
| `DashboardDisplayManagerBpmTests` | BP display values, AHA color mapping, date formatting, headline logic |
| `DashboardMetricsManagerBpmTests` | `loadMetrics(for: .bpm)` returns BP-specific MetricItem array |

### 14.2 UI Tests

| Test | Validates |
|------|----------|
| Product switching via header | Selector sheet opens, selection changes dashboard content |
| BPM manual entry | Form fields render, validation messages show, save navigates to dash |
| BPM dashboard rendering | Graph shows three lines, reference lines visible, metric cards visible |
| Metric card drag-drop (BP) | Cards can be reordered, removals persist, edit mode works |
| Snapshot card navigation | Cards render, tap navigates to correct dashboard |
| AHA rating sheet | Opens on tap, shows all 5 classification levels correctly |
| Three-reading average sheet | Opens on tap, shows "Why We Take an Average" + last 3 readings |
| Period switching | Week/month/year/total all render BP data |

### 14.3 Edge Case Tests

| Scenario | Expected Behavior |
|----------|-------------------|
| Only one product type exists | No dropdown, no snapshot cards, direct dashboard |
| BPM selected but no BP entries | Empty state with "no entries" message |
| Single BP entry in week | Chart shows single point with padding (all 3 series) |
| Very high readings (sys 200, dia 130) | AHA crisis color, Y-axis accommodates |
| Tapping different points with different AHA levels | Entire graph (all lines, points, headline) recolors to selected point's AHA class; pulse stays gray |
| Selection cleared (scroll/period switch) | Graph reverts to last entry's AHA color |
| Switching product type while scrolling | Clear selection, reset graph state, rebind data |
| Existing entries without `entryType` field | Default to `"wg"`, backward compatible |
| Mix of wg and bpm entries in same account | Correctly filtered by entryType in all screens |
| Three-reading average with < 3 entries | Shows "two entry average" or "last reading" |
| Diastolic > systolic in manual entry | Validation error shown |
| Rapid product type switching | No race conditions; last switch wins |
| Background/foreground with BPM selected | Restores BPM dashboard correctly |

---

## 15. Risks & Open Questions

### Risks

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Backend API not ready for unified format | Blocks Phase 7; limits to local-only | Build data layer for local testing first; API sync added when ready |
| SwiftData schema change (new fields on BathScaleEntry) | Lightweight migration needed | SwiftData handles adding optional fields automatically; test on devices with existing data |
| `ProductTypeStore` merge conflict complexity | May introduce bugs | Resolve carefully in Phase 6; add comprehensive tests |
| Performance with product-type switching | Chart re-rendering, data re-binding | Reuse existing cache invalidation patterns; throttle UI updates during switch |
| BaseGraphView complexity increase | Adding BP rendering to 900-line view | Use clean conditional blocks gated on `productType`; consider extracting `ChartContentBuilder` protocol |
| Backward compatibility for entries without `entryType` | Old entries must still work | Default nil `entryType` to `"wg"` in all filter predicates |

### Open Questions

| Question | Suggested Resolution |
|----------|---------------------|
| Should `entryType` be on `Entry` or derived from `deviceType`? | **On `Entry` directly** — `deviceType` maps to hardware (`scale`/`bpm`/`babyScale`), while `entryType` maps to measurement type (`wg`/`bpm`). Baby scale entries have `deviceType = babyScale` but `entryType = wg`. Keeping them separate is cleaner. |
| Should year/total BP use monthly averages or individual readings? | **Monthly averages** (same as weight) — consistent UX across product types |
| What happens to baby entries in the multi-device snapshot? | **One snapshot card per baby** — each baby is a separate `ProductSelection` item |
| Should `DashboardStore` create two instances (one per product type) or switch a single instance? | **Single instance, switch in place** — avoids duplicate memory; uses `switchProductType()` to rebind data |
| How should `meanArterial` be calculated? | **Client-side**: `(systolic + 2 * diastolic) / 3`, rounded. Stored for API compatibility but computed on save. |
| Should `BathScaleEntry` be renamed since it now holds BP data too? | **Not now** — renaming a SwiftData model is risky and unnecessary. The name is historical; the `entryType` field clarifies intent. |
| Should BP metric cards support the full 12-metric drag-drop grid? | **No** — BP has fewer cards (3-reading avg, AHA, streaks). Use simpler layout but same `DashboardGridEditingManager` for drag-drop. |

---

## 16. Tradeoffs

### Unified Entry Model vs. Separate BpmEntry Model

| Aspect | Unified (chosen) | Separate |
|--------|-------------------|----------|
| Schema changes | Add 4 optional fields to `BathScaleEntry` | New SwiftData model + relationship |
| Migration risk | Minimal (adding optional fields) | Moderate (new model registration) |
| EntryService | Single service with `entryType` filter | Two services with separate logic |
| Code duplication | Low — shared CRUD/sync/aggregation | High — duplicate save/delete/sync |
| Query complexity | One predicate filter per query | Separate query chains |
| API alignment | Matches target unified payload format | Would need mapping layer |
| Future flexibility | Easy to add more entry types (baby BPM, glucose, etc.) | Each new type needs a new model/service |

**Decision:** Unified model. Single `EntryService` with `entryType` filtering is simpler, aligns with the target unified API format, and scales to future entry types.

### Extend Existing DashboardStore vs. Create Separate BpmDashboardStore

| Aspect | Extend Existing (chosen) | Separate Store (old plan) |
|--------|--------------------------|--------------------------|
| New files | ~15 new views only | ~30+ new files (store + 15 managers + views) |
| Code duplication | ~500 lines of product-type branching | ~5000+ lines of duplicate manager code |
| Shared features | Edit mode, drag-drop, skeleton, scroll, caching — all free | Must re-implement or extract shared base classes |
| Future product types | Add `EntryType` case + configure | Another full store hierarchy |
| Testing | Parameterized tests | Duplicate test suites |
| Risk | Moderate — must carefully gate product-type branches | Low — isolated but heavy maintenance burden |
| Complexity per manager | Each gains a `switch productType` block | Each is clean but duplicated |

**Decision:** Extend existing. The weight dashboard has 15+ managers totaling ~5000 lines. Duplicating this for BPM (and again for each future product type) is unsustainable. The product-type branching adds moderate complexity but dramatically reduces maintenance.

---

## 17. Recommendation & Conclusion

### Recommendation

**Proceed with implementation** using the product-type-aware `DashboardStore` approach. This extends the existing infrastructure rather than duplicating it, ensuring that:

1. **BP dashboard** gets all weight graph features (scroll, Y-axis adaptation, period switching, selection, skeleton loading, metric card drag-drop/edit) for free.
2. **Future product types** (baby scale dashboard, glucose monitor) follow the same pattern with minimal new code.
3. **Bug fixes and performance improvements** to the graph/dashboard automatically benefit all product types.

### Estimated Timeline: 6-8 weeks

| Phase | Duration | Dependency |
|-------|----------|-----------|
| Phase 1: Data Foundation | 1-2 weeks | None |
| Phase 2: Product-Type-Aware Store | 1-2 weeks | Phase 1 |
| Phase 3: BP Graph Rendering | 1-2 weeks | Phase 2 |
| Phase 4: BP Dashboard Views & Cards | 1 week | Phase 3 |
| Phase 5: BP Manual Entry | 1 week | Phase 1 |
| Phase 6: Product Switching & Multi-Device | 1 week | Phase 2 |
| Phase 7: API Integration | 1-2 weeks | Backend ready |

Phases 5 and 6 can run in parallel with Phases 3-4.

### Key Architectural Decisions

1. **Unified `Entry` model** with `entryType` field (`"wg"` / `"bpm"`) — no separate BpmEntry model
2. **Single `EntryService`** — no separate BpmEntryService; all CRUD/aggregation in one place with `entryType` filtering
3. **Product-type-aware `DashboardStore`** — extend existing store and managers rather than creating BPM duplicates
4. **Extended `BaseGraphView`** — add reference lines and AHA colors to existing renderer
5. **Extended `GraphDataPreparer`** — add `buildBpmChartSeries()` for three-line rendering
6. **Reused metric card infrastructure** — `DashboardGridEditingManager`, `MetricCardView`, drag-drop all work as-is
7. **Backward compatibility** — entries without `entryType` default to `"wg"`

### Success Criteria

- User can switch between weight and BPM dashboards seamlessly
- BPM graph renders three lines (systolic, diastolic, pulse) with AHA color coding and reference lines
- BP graph is scrollable by period (week/month/year/total) with Y-axis adapting to visible data
- Point selection shows systolic/diastolic/pulse at crosshair
- Average calculation shows BP averages for visible period
- Three-reading average displays correctly with AHA colors
- BP metric cards (AHA rating, 3-reading avg, streaks) are draggable and editable
- Manual BP entry works with proper validation (sys > dia, ranges)
- History shows filtered entries per product type via `entryType`
- Multi-device snapshot dashboard shows both products with mini graphs
- All existing weight functionality remains unaffected
- All unit tests pass with 80%+ coverage on new code
- Single DashboardStore + managers handle both product types cleanly

---

*Last updated: 2026-03-23*

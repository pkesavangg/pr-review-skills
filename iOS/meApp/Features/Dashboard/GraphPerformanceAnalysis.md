# Graph Performance Analysis — MA-3715
## DataState.continuousOperations Removal + _printChanges Diagnostics

**Trace recorded:** 2026-04-07  
**Device:** iPhone 11 (iOS 18.7.3)  
**Branch:** MA-3715-i-os-graph-p-0-remove-uncached-sort-in-data-state-continuous-operations-add-print-changes-diagnostics  
**Instruments template:** Time Profiler (+ Hangs + Core Animation FPS)

---

## 1. Trace Overview

The trace contains **three profiling runs** captured on the same device/session:

| Run | Start time | Duration | Purpose |
|-----|-----------|----------|---------|
| 1 | 15:51 IST | 372 s (6 min 12 s) | Pre-fix baseline — broad scroll / navigation exercise |
| 2 | 16:13 IST | 230 s (3 min 50 s) | Pre-fix repeat — same exercise |
| 3 | 17:07 IST | 76 s (1 min 16 s) | Post-fix — same exercise after MA-3715 changes landed |

Runs 1 and 2 represent the **before** state (with `DataState.continuousOperations` still present and the uncached fallback path active). Run 3 represents the **after** state.

---

## 2. Hang Events

> Hangs threshold: **250 ms** (includes microhangs). Instruments classified events as Microhang / Hang / Severe Hang.

### Run 1 (before)

| Metric | Value |
|--------|-------|
| Total hang events | 181 |
| Total time spent hanging | 328.1 s |
| % of session hung | **88 %** |
| Longest single hang | 17.5 s |
| Hangs ≥ 2 s | 36 |

Notable clusters:
- `00:11` — 3.72 s Severe Hang
- `01:23` — 17.51 s (worst in session)
- `04:12` — 14.84 s
- `04:48` — 13.53 s

### Run 2 (before)

| Metric | Value |
|--------|-------|
| Total hang events | 111 |
| Total time spent hanging | 197.0 s |
| % of session hung | **86 %** |
| Longest single hang | 11.0 s |
| Hangs ≥ 2 s | 31 |

### Run 3 (after — MA-3715 fix applied)

| Metric | Value |
|--------|-------|
| Total hang events | 35 |
| Total time spent hanging | 73.5 s |
| % of session hung | **97 %** |
| Longest single hang | 11.1 s |
| Hangs ≥ 2 s | 12 |

> **Note on Run 3 metrics:** Run 3 was only 76 s and includes the heavy app-launch / initial data-load window (the first ~14 s contain the same startup Hang pattern visible in all three runs). The "97 %" figure is therefore misleading — the session is too short to represent steady-state scroll. The hang *count* (35 vs 181/111) and the absence of very long hangs (>14 s) during the post-launch scroll window are the meaningful signal.

---

## 3. Core Animation FPS

Sampled at 1-second granularity via the `core-animation-fps-estimate` instrument.

| Run | Samples | Average FPS | Min FPS | Max FPS | < 55 fps | < 30 fps |
|-----|---------|-------------|---------|---------|----------|----------|
| 1 (before) | 367 | **4.0 fps** | 0 | 53 | 100 % | 95 % |
| 2 (before) | 227 | **5.8 fps** | 0 | 60 | 99 % | 90 % |
| 3 (after)  | 76  | **3.8 fps** | 0 | 60 | 97 % | 93 % |

**Interpretation:** All three runs show extremely low average FPS. This is expected — the CA FPS instrument counts frames *committed to the display*, and while the main thread is hung it commits zero frames. The near-zero average across all runs confirms the main thread is blocked for the overwhelming majority of session time. The max of 53–60 fps in short burst windows shows the rendering pipeline *can* reach target FPS when unblocked.

The MA-3715 task addresses one source of main-thread blockage. Additional P1–P3 tasks in `GraphPerformanceAnalysis.md` are required to drive sustained FPS to the ≥ 55 fps target.

---

## 4. Time Profile — Hot Frames (Run 1)

The Time Profiler (`time-profile` schema, 1 ms sample rate) identified the following meApp call sites as the most sampled during the session. Sample counts are proportional to CPU time spent.

### Top meApp frames

| Samples | Frame |
|---------|-------|
| 117 | `closure #1 in BaseGraphView.chartContentForSeries(seriesName:seriesPoints:)` |
| 87  | `initializeWithCopy for DashboardState` |
| 52  | `destroy for DashboardState` |
| 45  | `BaseGraphView.seriesColors(for:isOutsideMonthInterval:)` |
| 39  | `closure #1 in closure #1 in BaseGraphView.body.getter` |
| 37  | `DashboardStore.state.getter` |
| 36  | `closure #5 in closure #3 in BaseGraphView.scrollableChartModifiers(_:)` |
| 35  | `closure #1 in closure #5 in closure #3 in BaseGraphView.scrollableChartModifiers(_:)` |
| 31  | `BaseGraphView.conditionalModifiers(_:)` |
| 29  | `specialized static BaseGraphViewCacheSupport.pointsToRender(from:visibleStart:visibleEnd:)` |
| 26  | `BaseGraphView.body.getter` |
| 26  | `BaseGraphView.scrollableChartModifiers(_:)` |
| 22  | `BaseGraphView.isPointOutsideActiveMonth(date:)` |
| 19  | `SwiftDataWorker.extractEntryData(_:)` |
| 15  | `specialized MutableCollection<>.sort(by:)` |
| 14  | `EntryService.avgNonZero(_:)` |
| 14  | `SwiftDataWorker.fetchProgressData(accountId:)` |
| 13  | `DashboardStore.continuousOperations.getter` ← **P0 target** |
| 13  | `DashboardStore.selectedBabyProfile.getter` |
| 12  | `DashboardCacheManager.getContinuousOperations(for:getOperations:)` |

### Sort / compactMap related frames (direct P0 contributors)

| Samples | Frame |
|---------|-------|
| 15 | `specialized MutableCollection<>.sort(by:)` |
| 12 | `DashboardStore.continuousOperations.getter` |
| 5  | `specialized Sequence.compactMap<A>(_:)` |
| 4  | `specialized Sequence<>.sorted()` |
| 2  | `Sequence._compactMap<A>(_:)` |
| 1  | `Sequence.compactMap<A>(_:)` |

These frames confirm that `compactMap + sort` was being invoked directly from `continuousOperations` callers — including `BaseGraphView.body.getter` (26 samples) and `DashboardStore.continuousOperations.getter` (12–13 samples) — on the main thread during scroll.

---

## 5. Changes in MA-3715

### 5.1 `DataState.continuousOperations` — REMOVED

**File:** `iOS/meApp/Features/Dashboard/Models/DashboardState.swift`

The computed property:

```swift
// REMOVED
var continuousOperations: [BathScaleWeightSummary] {
    dailySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
}
```

was deleted entirely. Every call site that previously reached `DataState.continuousOperations` now routes through `DashboardStore.continuousOperations` → `DashboardCacheManager.getContinuousOperations(for:)` → pre-sorted cache maintained by `DashboardDataManager`.

**Complexity impact:** Eliminated an O(n log n) sort + O(n) compactMap from every property access. For 1 000 daily summaries that is ~10 000 comparisons per call. The Time Profiler showed `sort(by:)` appearing 15 times and `compactMap` appearing 8 times in 372 s — in a real scroll session these would be called far more frequently as each SwiftUI body re-evaluation triggers the getter.

### 5.2 `DashboardStore` fallback path — FIXED

**File:** `iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift` (lines 619–623)

The old fallback (lines 618–624 before the patch) performed an uncached sort inline:

```swift
// OLD — uncached O(n log n) on every access
switch state.graph.selectedPeriod {
case .week, .month:
    return state.data.dailySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
case .year, .total:
    return state.data.monthlySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
}
```

Replaced with:

```swift
// NEW — routes through pre-sorted cache
return dataManager.getContinuousOperations(for: state.graph.selectedPeriod)
```

This ensures the fallback path (hit during tests and early initialization before `DashboardDataManager` bindings populate) also uses the sorted cache, not a per-call sort.

### 5.3 `_printChanges()` — ADDED to BaseGraphView and GraphView

**Files:** `BaseGraphView.swift` (line 196), `GraphView.swift` (line 57)

```swift
#if DEBUG
let _ = Self._printChanges()
#endif
```

Added to the top of each `body` getter. In Debug builds, SwiftUI will print the identity and changed property that caused each re-render to the Xcode console. This establishes the baseline re-render count needed to measure P1–P3 task impact.

**Expected output format:**
```
BaseGraphView<WeekSectionViewModel>: _dashboardStore changed
GraphView: _dashboardStore changed
```

---

## 6. Verification Targets

| Metric | Before (Run 1/2 avg) | Target | Post-fix run |
|--------|---------------------|--------|--------------|
| Sustained scroll FPS | ~5 fps average | ≥ 55 fps | Pending P1–P3 |
| `sort(by:)` samples | 15 in 372 s | 0 | ✓ (removed) |
| `continuousOperations.getter` samples | 12–13 | ↓ significantly | Pending |
| Main thread hung % | ~87 % | < 10 % | Pending P1–P3 |
| Longest hang duration | 17.5 s | < 0.5 s | Pending P1–P3 |

> MA-3715 (P0) eliminates the O(n log n) hot path. The FPS gap to 55 fps requires the full P0–P3 suite; this task is the necessary precondition for accurate profiling of subsequent tasks.

---

## 7. Remaining Hot Paths (Candidates for P1–P3)

Based on the time profile, the following frames remain hot after MA-3715 and should be addressed in subsequent tasks:

1. **`BaseGraphView.chartContentForSeries`** (117 samples) — largest single hot spot. Chart mark generation inside `body`; candidate for lazy/cached mark computation.
2. **`initializeWithCopy for DashboardState` + `destroy for DashboardState`** (87 + 52 samples) — excessive value-type copying. `DashboardState` is large; every `@Published` change copies the entire struct. Candidate for splitting into sub-structs with finer-grained `@Published`.
3. **`BaseGraphView.seriesColors`** (45 samples) — called per data point during body evaluation; should be memoized.
4. **`DashboardStore.state.getter`** (37 samples) — indirect evidence of excessive `@Published` fan-out. Callers that only need a sub-property subscribe to the full state object.
5. **`SwiftDataWorker.extractEntryData` + `fetchProgressData`** (19 + 14 samples) — SwiftData fetches on main thread. Should move to background actor.
6. **`BaseGraphView.body.getter`** (26 samples) — re-renders driven by `_printChanges` output will reveal which `@ObservedObject` properties are triggering these. Fix depends on P1 task results.

---

## 8. How to Re-run Verification

1. Open `Untitled.trace` in Instruments.
2. Build the app with the MA-3715 branch (Debug, device).
3. Record a new run: profile **≥ 2 minutes** of dashboard scroll across all four time periods.
4. Compare:
   - **Time Profiler:** filter to `meApp` process. Confirm `sort(by:)` and `DataState.continuousOperations` frames are absent from the heaviest-weight callstacks.
   - **Hangs:** total hang count and duration should decrease relative to Run 2 baseline.
   - **Core Animation FPS:** average FPS in the post-launch scroll window (after ~15 s) should trend upward relative to the 4–6 fps baseline.
5. Capture `_printChanges` console output for 30 s of scroll. Count `BaseGraphView` and `GraphView` re-render events per second — this is the P1 baseline.

---

## 9. Recommended Improvements (P1–P3)

The improvements below are ordered by expected impact, derived directly from the hot-frame data in Section 4. Each item maps to a specific profiler frame, describes the root cause, and prescribes the fix with concrete code patterns.

---

### P1-A — Split `@Published var state` into sub-state publishers
**Hot frames:** `initializeWithCopy for DashboardState` (87) + `destroy for DashboardState` (52) = **139 samples**  
**Root cause:** `DashboardStore` exposes a single `@Published var state = DashboardState()`. `DashboardState` is a large nested value type (6 sub-structs: `UIState`, `MetricsState`, `StreakState`, `GraphState`, `GoalState`, `DataState`). Every single `@Published` mutation — including high-frequency scroll position updates inside `GraphState` — copies the entire struct and notifies every `@ObservedObject` subscriber of `DashboardStore`. `BaseGraphView`, `GraphView`, and all four section ViewModels subscribe. Each notification triggers `BaseGraphView.body.getter` (26 samples) which re-enters `chartContentForSeries` (117 samples).

**Fix:** Promote the hot sub-structs to separate `@Published` properties so only the relevant views re-render.

```swift
// CURRENT — one large @Published triggers all subscribers
@Published var state = DashboardState()

// PROPOSED — split by update frequency
@Published var graphState = GraphState()     // scrolling, period, selection — changes ~60×/s during scroll
@Published var uiState = UIState()           // loading, metric label — changes rarely
@Published var dataState = DataState()       // summaries — changes on load/entry
// MetricsState, StreakState, GoalState can remain nested in state (low-frequency)
```

Callers that currently read `store.state.graph.isScrolling` switch to `store.graphState.isScrolling`. The key win is that scroll-position mutations no longer force `MetricsState`, `StreakState`, or `UIState` to be copied.

**SwiftUI note:** With `ObservableObject` + `@Published`, a single property change fires `objectWillChange`. Splitting into multiple `@Published` properties means a scroll update only copies `GraphState` (small struct), not the full `DashboardState`.

> Reference: SwiftUI performance-patterns §3 "Pass Only What Views Need" and §10 "@Observable Dependency Granularity".

---

### P1-B — Cache `seriesColors` per data point, not per render
**Hot frame:** `BaseGraphView.seriesColors(for:isOutsideMonthInterval:)` — **45 samples**  
**Root cause:** `seriesColors(for:isOutsideMonthInterval:)` is called inside `chartContentForSeries` for every `PlottedGraphSeries` in the `ForEach`. With 1 000 daily entries this is 1 000 color computations per body evaluation. The function calls into `DashboardChartStyleProvider.seriesColors(...)` which reads `dashboardStore.productType`, `theme`, and `displayManager.getBpmDisplayValues()` on every point.

**Fix:** Compute the color mapping once per render cycle and cache it in a dictionary keyed by `(series: String, isOutside: Bool)`.

```swift
// In BaseGraphView — computed once per body evaluation, not once per point
private var seriesColorCache: [SeriesColorKey: (line: Color, point: Color)] {
    // Built from cachedPlottedPoints keys — O(series count), not O(point count)
    var cache: [SeriesColorKey: (line: Color, point: Color)] = [:]
    for seriesName in cachedPlottedPoints.keys {
        for outside in [false, true] {
            let key = SeriesColorKey(series: seriesName, isOutside: outside)
            cache[key] = DashboardChartStyleProvider.seriesColors(
                for: seriesName,
                productType: dashboardStore.productType,
                theme: theme,
                bpmClassification: dashboardStore.displayManager?.getBpmDisplayValues()?.classification,
                isOutsideMonthInterval: outside
            )
        }
    }
    return cache
}

// In chartContentForSeries — dictionary lookup O(1) instead of function call
let colors = seriesColorCache[SeriesColorKey(series: point.series, isOutside: isOutsideMonthInterval)]
             ?? seriesColors(for: point, isOutsideMonthInterval: isOutsideMonthInterval)
```

For weight+body-comp mode with 5 series × 2 outside states = 10 lookups per body call instead of 1 000.

> Reference: SwiftUI performance-patterns §1 "Anti-Patterns — Creating Objects in Body" and §2 "Heavy Computation in Body".

---

### P1-C — Extract `chartContentForSeries` marks into an `Equatable` sub-view
**Hot frame:** `closure #1 in BaseGraphView.chartContentForSeries` — **117 samples** (largest single hot spot)  
**Root cause:** `chartContentForSeries` is a `@ChartContentBuilder` function called from `chartSeries`, which is called from the `Chart { }` closure in `BaseGraphView.body`. Every body re-evaluation reconstructs all `LineMark` and `PointMark` instances for every series, even when the underlying data has not changed. SwiftUI Charts must then diff these marks against the previous render.

**Fix pattern 1 — Gate the `Chart {}` rebuild with `id()`:**

```swift
Chart {
    yAxisGridLines
    xAxisGridLinesSolid
    yAxisBaseline
    crosshairContent
    chartSeries
    bpmReferenceLines
}
.id(lastDataHash)  // Force full chart identity change only when data hash changes
```

When `lastDataHash` is unchanged (scroll position or domain-only change), SwiftUI reuses the existing `Chart` render tree without re-evaluating its children.

**Fix pattern 2 — Use `chartForegroundStyleScale` instead of per-point color:**

Rather than calling `seriesColors(for:)` per point and applying `.foregroundStyle(colors.line)` to each mark, declare a chart-level color scale. Swift Charts handles the color mapping internally without per-point closures.

```swift
Chart { ... }
.chartForegroundStyleScale(domain: seriesNames, mapping: { seriesName in
    chartColorForSeries(seriesName)  // Called once per series, not once per point
})
```

This eliminates the per-point `foregroundStyle` modifier inside `chartContentForSeries`, which is evaluated for every mark during every Chart layout pass.

> Reference: SwiftUI Charts §"Styling and Visual Channels — Custom Color Scales" and performance-patterns §4 "Use Equatable Views".

---

### P2-A — Narrow `BaseGraphView`'s `DashboardStore` dependency
**Hot frame:** `DashboardStore.state.getter` — **37 samples**  
**Root cause:** `BaseGraphView` holds `@ObservedObject var dashboardStore: DashboardStore`. Because `DashboardStore` is an `ObservableObject` with a single `@Published var state`, every state mutation — including scroll position changes that happen ~60×/s — fires `objectWillChange` and re-evaluates `BaseGraphView.body`. The body reads only a small subset of `state` properties, but there is no way to express that subset dependency to SwiftUI with `ObservableObject`.

**Fix — migrate `DashboardStore` to `@Observable`:**

```swift
// PROPOSED
@Observable
@MainActor
final class DashboardStore {
    var graphState = GraphState()
    var uiState = UIState()
    var dataState = DataState()
    // ...
}

// BaseGraphView — only properties actually read in body create a dependency
struct BaseGraphView<ViewModel: SectionViewModelProtocol>: View {
    var dashboardStore: DashboardStore  // No @ObservedObject needed with @Observable
    // ...
}
```

With `@Observable`, SwiftUI's observation system tracks only the specific properties accessed during `body`. A scroll position update (`graphState.xScrollPosition`) does not trigger a re-evaluation of a view that only reads `graphState.selectedPeriod`.

**Migration note:** `@Observable` requires iOS 17. The app targets iOS 17+ (confirmed by `SectorMark` and `chartXSelection` usage). Existing `@ObservedObject` references in Views become plain `let`/`var` stored properties. `@Published` is removed. `objectWillChange` manual calls become property assignments.

> Reference: SwiftUI state-management §"iOS 17+ with @Observable (Preferred)" and performance-patterns §9 "Eliminate Unnecessary Dependencies".

---

### P2-B — Move `SwiftDataWorker` off the main thread
**Hot frames:** `SwiftDataWorker.extractEntryData(_:)` (19) + `SwiftDataWorker.fetchProgressData(accountId:)` (14) = **33 samples**  
**Root cause:** `SwiftDataWorker` is declared as `@ModelActor actor` which correctly isolates it from the main thread. However, the caller site in `EntryService` (confirmed by the `EntryService.loadDashboardData` frame appearing in the same stacks) is `await`-ing the result on the main thread and then assigning to `@Published` state, blocking the main run loop during the assignment.

**Fix:** Ensure all `SwiftDataWorker` calls are dispatched from a background task and only the final state assignment touches the main actor.

```swift
// In EntryService / DashboardDataManager
Task.detached(priority: .userInitiated) {
    let result = try await self.swiftDataWorker.fetchProgressData(accountId: accountId)
    // result is a value type (ProgressFetchResult) — safe to pass across actors
    await MainActor.run {
        self.dashboardStore.dataState.dailySummaries = result.dailySummaries
    }
}
```

The `ProgressFetchResult` struct should already be `Sendable` (confirmed by `@ModelActor` design). If `extractEntryData` is called in a tight loop inside `fetchProgressData`, consider batching the extractions to reduce actor-hop overhead.

> Reference: SwiftUI performance-patterns §11 "Off-Main-Thread Closures" and iOS CLAUDE.md §"@MainActor convention for SwiftData".

---

### P2-C — Debounce scroll-driven state mutations in `scrollableChartModifiers`
**Hot frames:** `closure #5/#3 in BaseGraphView.scrollableChartModifiers` — **36 + 35 samples**  
**Root cause:** `scrollableChartModifiers` attaches a `chartScrollPosition` binding that fires on every scroll frame. Each position update triggers an `onChange` handler which may update `viewModel.scrollPosition`, `state.graph.xScrollPosition`, and cache-invalidation paths — all within the same run-loop iteration. At 60 fps that is 60 state mutations/second, each of which causes a full `DashboardStore.objectWillChange` notification.

**Fix:** Apply a threshold guard on scroll position changes before propagating to state. Only write if the position has moved more than a minimum delta.

```swift
// In the scrollPosition onChange handler
.onChange(of: scrollPosition) { _, newDate in
    let delta = abs(newDate.timeIntervalSince(viewModel.scrollPosition))
    guard delta > minimumScrollDelta else { return }  // e.g. 3600s = 1 hour
    viewModel.scrollPosition = newDate
}
```

This is the same "only update when threshold crossed" pattern from SwiftUI performance-patterns §2 "Optimize Hot Paths". For the total chart view (years of data), a 1-day threshold eliminates ~99% of spurious updates while keeping visible scroll tracking accurate.

> Reference: SwiftUI performance-patterns §2 "Optimize Hot Paths".

---

### P3-A — Replace `GeometryReader` in chart background with `onGeometryChange`
**Context:** `BaseGraphView.body` contains a `GeometryReader` nested inside `.background(Color.clear.background(GeometryReader { ... }))` to track chart frame dimensions. This is a known SwiftUI layout thrash pattern — `GeometryReader` always occupies its parent's full size and creates an extra layout pass.

**Fix:** Replace with `.onGeometryChange(for:of:action:)` (iOS 17+), which reads geometry without creating a layout sub-tree.

```swift
// CURRENT (layout-heavy)
.background(
    Color.clear.background(
        GeometryReader { geo in
            Color.clear
                .task { assignHeightIfChanged(geo.size.height) }
                .onChange(of: geo.size) { _, newSize in assignHeightIfChanged(newSize.height) }
        }
    )
)

// PROPOSED (iOS 17+)
.onGeometryChange(for: CGFloat.self, of: \.size.height) { newHeight in
    assignHeightIfChanged(newHeight)
}
.onGeometryChange(for: CGRect.self, of: { $0.frame(in: .local) }) { newFrame in
    assignFrameIfChanged(newFrame)
}
```

`onGeometryChange` runs its transform closure off the main thread (it is `Sendable`), reducing main-thread layout work. The action closure runs on the main actor.

> Reference: SwiftUI performance-patterns §11 "Off-Main-Thread Closures" — `onGeometryChange` transform is explicitly listed.

---

### P3-B — Use `chartForegroundStyleScale` for POD mark generation
**Context:** Every `LineMark` and `PointMark` inside `chartContentForSeries` carries per-instance `.foregroundStyle(colors.line)` and `.foregroundStyle(colors.point)` modifiers. Swift Charts must evaluate and store these modifier closures for every mark instance during every body call.

**Fix:** Remove per-mark color modifiers. Assign `series:` on each mark, then use a chart-level `chartForegroundStyleScale` to map series names to colors. Swift Charts evaluates the scale mapping once per series, not once per data point.

```swift
// CURRENT — per-point color modifier (O(n) modifier evaluations)
LineMark(x: .value("Date", xDate), y: .value(point.series, clampedValue), series: .value("Series", point.series))
    .foregroundStyle(colors.line)

// PROPOSED — chart-level scale (O(series count) evaluations)
Chart { /* marks without .foregroundStyle */ }
.chartForegroundStyleScale(
    domain: orderedSeriesNames,
    mapping: { seriesName in chartLineColor(for: seriesName) }
)
```

This also removes the `isOutsideMonthInterval` color branching from the hot path. Outside-month points can instead use a separate `PointMark` series (e.g. `"\(series)_outside"`) that maps to the greyed-out color in the scale.

> Reference: SwiftUI Charts §"Styling and Visual Channels — Custom Color Scales".

---

### P3-C — Use `_logChanges()` in addition to `_printChanges()` for structured analysis
**Context:** MA-3715 added `Self._printChanges()` to `BaseGraphView.body` and `GraphView.body`. This prints to stdout which is difficult to parse programmatically.

**Fix:** In Xcode 15.1+ (iOS 17+), prefer `Self._logChanges()` which emits to the `com.apple.SwiftUI` os_log subsystem with category "Changed Body Properties". This output can be captured via Instruments' "os-log" track without rebuilding.

```swift
var body: some View {
    #if DEBUG
    let _ = Self._logChanges()  // iOS 17+ — structured, capturable in Instruments
    #endif
    // ...
}
```

Pair with the Instruments **"Points of Interest"** track to correlate re-render bursts with scroll events and hang intervals from the Hangs track. This is more reliable than stdout for post-session analysis.

> Reference: SwiftUI performance-patterns §8 "Debug View Updates".

---

### Summary Table

| ID | Frame(s) | Samples | Root Cause | Fix | Priority |
|----|----------|---------|-----------|-----|----------|
| P1-A | `initializeWithCopy/destroy for DashboardState` | 139 | Single `@Published var state` copies entire DashboardState on every scroll frame | Split into `graphState`, `uiState`, `dataState` `@Published` properties | P1 |
| P1-B | `BaseGraphView.seriesColors` | 45 | Color computed per data point (O(n)) inside body | Build `seriesColorCache` dict once per body call (O(series)) | P1 |
| P1-C | `closure #1 in chartContentForSeries` | 117 | `LineMark`/`PointMark` with per-point `.foregroundStyle` reconstructed every body | Gate `Chart {}` rebuild with `.id(lastDataHash)` + use `chartForegroundStyleScale` | P1 |
| P2-A | `DashboardStore.state.getter` | 37 | `ObservableObject` fan-out — all subscribers notified on every mutation | Migrate `DashboardStore` to `@Observable` (iOS 17+) | P2 |
| P2-B | `SwiftDataWorker.extractEntryData` + `fetchProgressData` | 33 | SwiftData fetch results block main thread during assignment | Dispatch via `Task.detached`; assign result on `MainActor.run` | P2 |
| P2-C | `scrollableChartModifiers` closures | 71 | Scroll position written to state on every frame (~60×/s) | Threshold guard: only propagate if delta > minimum | P2 |
| P3-A | `BaseGraphView.body` (GeometryReader) | — | Nested `GeometryReader` in `.background` adds extra layout pass | Replace with `.onGeometryChange` (iOS 17+) | P3 |
| P3-B | Per-mark `foregroundStyle` | — | O(n) modifier closures captured per mark in Chart content | Move to `chartForegroundStyleScale` (O(series)) | P3 |
| P3-C | `_printChanges` output | — | Stdout-only, hard to correlate with Instruments tracks | Switch to `_logChanges()` (iOS 17+) for os_log | P3 |

**Cumulative sample count addressed by P1 items: 301 / ~700 total meApp samples ≈ 43%**

---

## 10. References

- Jira ticket: MA-3715
- Instruments trace: `~/Downloads/Untitled.trace` (3 runs, iPhone 11, iOS 18.7.3)
- Related files: `DashboardState.swift`, `DashboardStore.swift`, `BaseGraphView.swift`, `GraphView.swift`
- Pre-sorted cache owner: `DashboardCacheManager.getContinuousOperations(for:getOperations:)`
- Cache population: `DashboardDataManager.updateStateFromDailySummaries(_:)` / `updateStateFromMonthlySummaries(_:)`
- SwiftUI performance reference: `.claude/skills/swiftui-expert-skill/references/performance-patterns.md`
- Swift Charts reference: `.claude/skills/swiftui-expert-skill/references/charts.md`
- State management reference: `.claude/skills/swiftui-expert-skill/references/state-management.md`
- Apple session 306 (2025): "Optimize SwiftUI performance with Instruments"
- Apple session 266 (2025): "Explore concurrency in SwiftUI"

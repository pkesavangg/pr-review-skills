# iOS Dashboard Graph — Hang Investigation

**Status:** Three fixes applied and **verified on-device (2026-04-28)** — Fix 2 (`.equatable()`), Fix 4 (`displayWeight` memoization), Fix 1a (`scrollPosition` demoted from `@Published`). Verification trace (`docs/tasks/trace_after_fix.trace`) shows **0 hangs reported, Thermal State Nominal, CPU bursty-not-sustained** across the full session — see §3.8. Diagnostic instrumentation (signposts, `Self._printChanges()`, `GraphSignpost.swift`, `*Impl` thin wrappers) was **removed 2026-04-28** after verification — see §6.2. Fix 1b (period-change observation narrowing) remains a *deferred* follow-up; current data does not show it as required.
**Date:** 2026-04-28
**Affected area:** [iOS/meApp/Features/Dashboard/](../../iOS/meApp/Features/Dashboard/) — `BaseGraphView`, `BaseSectionViewModel`, `DashboardStore`, `DashboardGraphManager`
**Reporter:** Kesavan
**Build profiled:** `release/5.0.0` branch, Debug config, on-device (iPhone, iOS 26.2)

---

## 1. Symptom

Users perceive a noticeable UI hang in the dashboard graph in two scenarios:

1. **Tab switch** — switching between Week / Month / Year / Total tabs.
2. **Scroll** — scrolling the chart inside Week or Month.

The hang is sub-second (typical microhang range ~250–500 ms) but consistent and recurring on each interaction.

---

## 2. Investigation Methodology

To attribute the hang to specific code rather than guess, we instrumented the suspect code paths with `os_signpost` and profiled the app on a real device using Instruments (Time Profiler + Hangs + Points of Interest).

### 2.1 Signpost helper added

A new helper file was added so any function can be wrapped in a signpost interval that surfaces in the Instruments **Points of Interest** track:

- File: [iOS/meApp/Core/Utilities/GraphSignpost.swift](../../iOS/meApp/Core/Utilities/GraphSignpost.swift)
- Subsystem: `com.dmd.meApp`
- Category: `PointsOfInterest` (so intervals appear directly in the built-in Points of Interest instrument)
- API: `GraphSignpost.measure("name") { ... }` for intervals; `GraphSignpost.event("name")` for one-off markers.

### 2.2 Instrumented call sites

| Path | Location | Signpost name |
|------|----------|---------------|
| Tab switch — onChange cascade | [GraphView.swift:99-189](../../iOS/meApp/Features/Dashboard/Views/Components/GraphView.swift#L99-L189) | `PeriodChanged` (event), `PeriodChange.clearSelection`, `PeriodChange.configureVM`, `PeriodChange.forceScrollSync`, `PeriodChange.updateYAxisCache`, `PeriodChange.autoSelectLatest` |
| Mount path | [BaseGraphView.swift:243](../../iOS/meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L243) | `BaseGraphView.onAppear` |
| Render hot loop | [BaseGraphView.swift:655](../../iOS/meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L655) | `BaseGraphView.getPointsToRender` |
| Cache rebuild | [BaseGraphView.swift:604](../../iOS/meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L604) | `BaseGraphView.updateCachedChartData` |
| Label precompute | [BaseGraphView.swift:779](../../iOS/meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L779) | `BaseGraphView.precomputeLabels` |
| Y-axis change cascade | [BaseGraphView.swift:325](../../iOS/meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L325) | `BaseGraphView.onChangeYAxisDomain` |
| Series generation | [DashboardGraphManager.swift:453](../../iOS/meApp/Features/Dashboard/Managers/DashboardGraphManager.swift#L453) | `GraphManager.generateChartDataWithYAxisDomain` |
| Visible window calc | [DashboardGraphManager.swift:966](../../iOS/meApp/Features/Dashboard/Managers/DashboardGraphManager.swift#L966) | `GraphManager.getVisibleOperations` |
| Bracketing operations | [DashboardGraphManager.swift:1119](../../iOS/meApp/Features/Dashboard/Managers/DashboardGraphManager.swift#L1119) | `GraphManager.getBracketingOperations` |

### 2.3 Body-recompute logging

Added `Self._printChanges()` (DEBUG-only) inside `BaseGraphView.body` at [BaseGraphView.swift:155-160](../../iOS/meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L155-L160) so the Xcode console reports which `@State` / `@ObservedObject` change triggered each body recompute.

### 2.4 Reproduction protocol

1. Profile build (`Cmd+I`), Time Profiler template, target **Sona's iPhone (iOS 26.2)**.
2. Add Time Profiler, Points of Interest, Thermal State, Hangs instruments.
3. Recording mode: Deferred. Time Profiler: High Frequency Sampling enabled.
4. Reproduce: Week → Month → Year → Total → Week (~1s pause each) then scroll vigorously on Week for ~5s.
5. Stop recording. Total trace ~22s.

---

## 3. Findings

### 3.1 Hangs detected

The Hangs instrument flagged **5 microhangs** on the Main Thread, all during the dashboard interaction window:

| # | Start | Duration | Type |
|---|-------|----------|------|
| 1 | 00:10.072 | **369.25 ms** | Microhang |
| 2 | 00:11.825 | 327.09 ms | Microhang |
| 3 | 00:14.599 | 391.80 ms | Microhang |
| 4 | 00:16.316 | 393.36 ms | Microhang |
| 5 | 00:17.228 | **407.41 ms** | Microhang |

**Total time hung:** ~1.89 s of the ~22 s session.

### 3.2 Signpost summary (Regions of Interest)

Across the entire 22s trace, the **total time spent inside our instrumented functions was only 17.64 ms**. None of them is responsible for the hangs.

| Signpost | Count | Total | Avg | Max |
|---------|-------|-------|-----|-----|
| `BaseGraphView.getPointsToRender` | 276 | 170.79 µs | 619 ns | 9.08 µs |
| `GraphManager.getVisibleOperations` | 158 | 912.54 µs | 5.78 µs | 49.96 µs |
| `GraphManager.getBracketingOperations` | 43 | 104.46 µs | 2.43 µs | 10.38 µs |
| `BaseGraphView.updateCachedChartData` | 14 | 3.23 ms | 230.84 µs | 572.96 µs |
| `GraphManager.generateChartDataWithYAxisDomain` | 12 | 1.89 ms | 157.42 µs | 347.46 µs |
| `BaseGraphView.onChangeYAxisDomain` | 7 | 75.42 µs | 10.77 µs | 19.08 µs |
| `BaseGraphView.precomputeLabels` | 7 | 3.62 ms | 516.55 µs | 1.91 ms |
| `BaseGraphView.onAppear` | 6 | 5.82 ms | 969.99 µs | 2.83 ms |
| `PeriodChange.clearSelection` | 5 | 230.33 µs | 46.07 µs | 94.58 µs |
| `PeriodChange.updateYAxisCache` | 5 | 236.59 µs | 47.32 µs | 80.25 µs |
| `PeriodChange.configureVM` | 5 | 395.08 µs | 79.02 µs | 103.58 µs |
| `PeriodChange.autoSelectLatest` | 5 | 933.71 µs | 186.74 µs | 443.83 µs |
| `PeriodChange.forceScrollSync` | 5 | 25.63 µs | 5.12 µs | 7.12 µs |
| **Total** | **548** | **17.64 ms** | — | — |

**Conclusion:** The hangs are not caused by the cost of any individual function we wrote. The work happens elsewhere — in code SwiftUI/Charts runs on top of our state changes.

### 3.3 Time Profiler attribution for the 407 ms hang

After Set Inspection Range + Invert Call Tree + Hide System Libraries on the worst hang (407.10 ms at 00:17.228):

| Function | Self Weight | % of hang | Notes |
|----------|-------------|-----------|-------|
| `main` (entry frame) | 337.80 ms | 82.9% | Un-symbolicated SwiftUI / Charts framework time |
| `DashboardGraphManager.interpolatedDisplayWeight(...)` | 17.10 ms | 4.2% | **Top app-code culprit** |
| `DashboardGoalManager.convertStoredWeightToDisplay(_:)` | 8.90 ms | 2.2% | Called by the above |
| `closure #1 in BaseGraphView.chartContentForSeries(seriesName:seriesPoints:)` | 5.10 ms | 1.3% | Per-Mark builder closure |
| `View.conditionalModifiers` closure cascade | ~2.6 ms | 0.6% | Modifier rebuild |
| `MetricCell.configure(...)` | 1.20 ms | 0.3% | 🚩 metric cards rebuilding during chart hang |
| `DashboardStore.formatYAxisTickLabel(_:)` | 900 µs | 0.2% | Format on every recompute |
| `DashboardStore.continuousOperations.getter` | 400 µs | 0.1% | Recomputed per body access |

Total app-code self time: ~36 ms. Remaining ~370 ms is in framework / un-symbolicated code triggered as a consequence of body invalidations.

### 3.4 `Self._printChanges` console output (the smoking gun)

During a single tab-switch + scroll session, `BaseGraphView` body invalidations counted by view type:

| View | Recomputes |
|------|-----------|
| `BaseGraphView<WeekSectionViewModel>` | **~35** |
| `BaseGraphView<YearSectionViewModel>` | ~25 |
| `BaseGraphView<TotalSectionViewModel>` | ~10 |
| `BaseGraphView<MonthSectionViewModel>` | ~8 |

Representative excerpt of the Week view's recompute storm:

```
BaseGraphView<WeekSectionViewModel>: _viewModel changed.
BaseGraphView<WeekSectionViewModel>: _viewModel changed.
BaseGraphView<WeekSectionViewModel>: _dashboardStore changed.
BaseGraphView<WeekSectionViewModel>: _viewModel changed.
BaseGraphView<WeekSectionViewModel>: _viewModel changed.
BaseGraphView<WeekSectionViewModel>: _dashboardStore changed.
BaseGraphView<WeekSectionViewModel>: _viewModel changed.
... (× ~25 more lines)
BaseGraphView<WeekSectionViewModel>: _isInScrollEndTransition changed.
BaseGraphView<WeekSectionViewModel>: _dashboardStore, _isInScrollEndTransition changed.
BaseGraphView<WeekSectionViewModel>: _lastDataHash, _cachedPlottedPoints, _cachedYAxisLabels changed.
```

Almost every entry is `_viewModel changed.` or `_dashboardStore changed.` — meaning a `@Published` mutation in the VM or store invalidates the entire `BaseGraphView` body.

### 3.5 Math

35 body recomputes × ~10–15 ms of framework work per rebuild ≈ **350–525 ms hang per Week interaction** — matches the observed 407 ms hang within a few percent.

### 3.6 Third profile (iPhone 12 mini, post-Fix-2 + Fix-4) — definitive attribution

A 25-second profile on iPhone 12 mini was exported using `xcrun xctrace export` and analyzed programmatically (script in `/tmp/analyze_trace.py`). The trace contained 5 hangs totaling 2.22 s. Per-hang attribution of time spent inside *instrumented* code vs. the rest:

| Hang | Type | Duration | Signposted | Un-attributed |
|------|------|---------:|-----------:|--------------:|
| #1 | Tab switch | 326 ms | 2.13 ms (0.7%) | **324.3 ms (99.3%)** |
| #2 | Tab switch | 547 ms | 7.08 ms (1.3%) | **540.5 ms (98.7%)** |
| #3 | Scroll | 467 ms | 0.03 ms (0.0%) | **466.9 ms (100%)** |
| #4 | Scroll | 322 ms | 0.01 ms (0.0%) | **321.9 ms (100%)** |
| #5 | Scroll | 559 ms | 0.05 ms (0.0%) | **558.6 ms (100%)** |

**Total signposted code across the entire 25-second trace: 27 ms (1.2%).**

The largest single signposted contributors during tab-switch hangs are `BaseGraphView.onAppear` (3.5 ms) and `BaseGraphView.precomputeLabels` (2.3 ms). During scroll hangs, the signposts are essentially silent.

Top global signposts across the whole trace:

| Total | Count | Signpost |
|------:|------:|----------|
| 8.88 ms | 8 | `BaseGraphView.precomputeLabels` |
| 7.69 ms | 5 | `BaseGraphView.onAppear` |
| 3.99 ms | 16 | `BaseGraphView.updateCachedChartData` |
| 2.35 ms | 11 | `GraphManager.generateChartDataWithYAxisDomain` |
| 1.27 ms | 4 | `PeriodChange.autoSelectLatest` |
| 1.20 ms | 135 | `GraphManager.getVisibleOperations` |
| 0.51 ms | 4 | `PeriodChange.configureVM` |
| 0.36 ms | 246 | `BaseGraphView.getPointsToRender` |
| <1 ms | (rest) | other intervals |

**Conclusions:**

1. **Fix 4 is working as intended at the leaf level.** `interpolatedDisplayWeight` and `displayWeight.getter` no longer dominate the call tree per hang because the cache is being hit. But the framework cascade above them is the dominant cost.
2. **Scroll hangs are essentially 100% framework work.** Almost no code we own runs during them.
3. **The framework work can only come from one place: SwiftUI re-evaluating bodies in response to `@Published` mutations**, plus Charts framework re-rendering downstream. Our `_printChanges` evidence (§3.4) corroborates this.
4. **There is no further leaf-level optimization that will move the needle.** The next 100+ ms of savings has to come from reducing the *number* of body invalidations — i.e. Fix 1.

### 3.7 Fourth profile (iPhone 12 mini, with Invert Call Tree visible) — new leaves exposed

A second 27-second profile on iPhone 12 mini was exported with the inverted call tree visible (system libraries kept on so we could see all symbols). The trace contains **7 hangs totaling 2,772 ms** — and crucially, three of them are *severe* (≥500 ms) **including one from scrolling**, contradicting the assumption that only tab-switch could push past 500 ms on this device:

| # | Type | Time | Duration | Trigger |
|---|------|------|---------:|---------|
| 1 | Microhang | 03.383 | 327 ms | initial app load |
| **2** | **Severe Hang** | **11.987** | **510 ms** | **tab switch** |
| 3 | Microhang | 14.270 | 298 ms | tab switch |
| **4** | **Severe Hang** | **16.521** | **504 ms** | **tab switch** |
| 5 | Microhang | 18.354 | 308 ms | scroll |
| 6 | Microhang | 21.615 | 318 ms | scroll |
| **7** | **Severe Hang** | **22.138** | **507 ms** | **scroll** |

**Per-hang signposted attribution (extracted via `xctrace export` + `/tmp/analyze_trace2.py`):**

| Hang | Duration | Signposted | Un-attributed |
|------|---------:|-----------:|--------------:|
| #1 (load) | 327 ms | 1.0 ms (0.3%) | 326 ms (99.7%) |
| #2 (tab) | 510 ms | 10.8 ms (2.1%) | 499 ms (97.9%) |
| #3 (tab) | 298 ms | 7.1 ms (2.4%) | 291 ms (97.6%) |
| #4 (tab) | 504 ms | 7.4 ms (1.5%) | 497 ms (98.5%) |
| #5 (scroll) | 308 ms | 0.08 ms (0.0%) | 308 ms (100%) |
| #6 (scroll) | 318 ms | 0.01 ms (0.0%) | 318 ms (100%) |
| #7 (scroll) | 507 ms | 0.02 ms (0.0%) | 507 ms (100%) |

**Roll-up across all 7 hangs: 26.5 ms (0.95%) signposted, 2,745 ms (99.05%) un-attributed.**

**New app-code self-time leaves visible in the inverted call tree for hang #2 (510 ms tab switch):**

| Self Weight | Function | Notes |
|------------:|----------|-------|
| 5.40 ms | `closure #1 in BaseGraphView.chartContentForSeries(seriesName:seriesPoints:)` | **Per-Mark builder closure — most expensive single app-code leaf during a tab-switch hang** |
| 1.90 ms | `DashboardStore.state.getter` | @Published state accessor — cheap per call but invoked many times |
| 1.80 ms | `MetricCell.configure(...)` | Confirms metric cards reconfigure during chart hang |
| 1.50 ms | `closure #1 in SegmentedButtonView.widestLabelWidth.getter` | **NEW: Tab strip recomputes label widths on every period change** |
| 1.10 ms | `BaseSectionViewModel.shouldShowSolidLine(for:)` | Per-Mark conditional |
| 1.10 ms | `MetricCell.updateDragState(_:)` | Drag state updates even when not dragging |
| 1.00 ms | `protocol witness for SectionViewModelProtocol.showCrosshair.getter` | |
| 800 µs | `DashboardStore.continuousOperations.getter` | |
| 300 µs | `MetricCell.layoutSubviews()` | UIKit layout work for the metric cells |
| <100 µs | `DashboardStore.currentDisplayWeightSignature.getter` | Fix 4's signature compute — confirmed cheap |

Sum of visible app-code self time during the 510 ms hang: **~16 ms**. The remaining ~494 ms is framework work (SwiftUI body diff/layout, Charts framework, UIKit cell layout, etc.) triggered by the `@Published` cascade above these leaves.

**Three findings the previous traces had hidden:**

1. **`chartContentForSeries` closure is the largest single app-code self-time leaf (5.4 ms).** It's the SwiftUI `ChartContentBuilder` closure called per series per body recompute — repeated invocations because body keeps invalidating.
2. **The `MetricCell` suite (`configure` + `updateDragState` + `layoutSubviews`) totals ~3.2 ms self.** A single state change in `DashboardStore` causes every metric card on the dashboard to reconfigure synchronously even though the chart-internal change has nothing to do with metric data.
3. **`SegmentedButtonView.widestLabelWidth` (1.5 ms self).** The period tab segmented control is doing label-width measurement during the chart hang — an independent reactive cascade we hadn't considered.

**Implication for the fix plan:** Fix 1 is no longer "scroll hangs only." The data shows two distinct hang families with different mitigations:

- **Scroll hangs (#5, #6, #7):** Driven entirely by per-tick `scrollPosition`/`isScrolling` mutations on `BaseSectionViewModel`. Fix is to decouple those from `@ObservedObject` (Fix 1a).
- **Tab-switch hangs (#2, #3, #4):** Driven by a single `selectedPeriod` change cascading through every `dashboardStore` observer on the dashboard simultaneously: `WeightTrendView`, `BaseGraphView`, `MetricCell` (×N), `SegmentedButtonView`, `DashboardMetricsSection`, `GoalStreakGridUIKitView`. Fix is to either narrow the observation surface or stage the cascade across multiple frames (Fix 1b).

### 3.8 Fifth profile (post-Fix-1a + Fix-2 + Fix-4 verification, 2026-04-28)

After applying Fix 1a + Fix 2 + Fix 4, an on-device profile (`docs/tasks/trace_after_fix.trace`, ~20 s session) was run reproducing the same Week → Month → Year → Total → Week + Week scroll protocol from §2.4.

| Signal | Before (§3.7 baseline) | After (this trace) |
|---|---|---|
| Hangs reported | 7 (3 severe ≥500 ms, 4 microhangs) totaling 2,772 ms | **0 — Hangs instrument shows "No Graphs"** |
| Thermal State | Nominal | Nominal (held throughout) |
| CPU usage pattern | Sustained bursts during scroll/tab-switch | **Bursty on user gesture, idle between** — characteristic of work happening only on input, not per-frame |
| Points of Interest signposts | Long bands during hangs | Short discrete ticks per `PeriodChange.*` phase (each phase well under 1 ms) |

The fix is verified to hold under the exact reproduction protocol that produced 510 ms severe hangs in §3.7. **Fix 1b is therefore deferred** — current data does not show it as required.

After this confirmation, the diagnostic instrumentation (signposts, `_printChanges()`, `GraphSignpost.swift`, the `*Impl` thin-wrapper extraction in `DashboardGraphManager` / `BaseGraphView`) was removed in the same session — see §6.2.

---

## 4. Root Cause

**The hangs are caused by SwiftUI body thrashing, not by the cost of any single function.**

Specifically:

1. `BaseGraphView` observes the entire `DashboardStore` and the entire active `SectionViewModel` via `@ObservedObject`.
2. Both objects have many `@Published` properties (`scrollPosition`, `isScrolling`, `yAxisDomain`, `yAxisTicks`, `selectedDate`, `selectedPoint`, plus settings, metrics, labels, etc.).
3. During a single tab switch or scroll gesture, **dozens of these properties mutate** — many at sub-frame intervals (especially `scrollPosition`).
4. Each mutation invalidates `BaseGraphView.body`, which in turn re-runs:
   - `chartContentForSeries` (per-series ChartContentBuilder closures)
   - `getPointsToRender` (the 276-call hot loop)
   - `interpolatedDisplayWeight` and `convertStoredWeightToDisplay` (the ~26 ms of app-code per hang)
   - SwiftUI's diff against the entire `Chart { ... }` tree (the un-attributed ~370 ms)
5. Because all four section view models are kept alive as `@StateObject` in `GraphView`, **all four `BaseGraphView<...>` types remain in the SwiftUI environment and react to the same store**, multiplying the invalidation cost.
6. `MetricCell.configure(...)` showing up in the chart-hang call tree confirms the dashboard's metric cards are also rebuilding because they share the same store — i.e. the entire dashboard is invalidated by chart-internal state.

`BaseGraphView` already declares `Equatable` conformance, but it is not wired up at any call site (`.equatable()` is never applied), so SwiftUI doesn't use it to short-circuit redundant rebuilds.

---

## 5. Fix Plan

Two-part fix, in priority order. Fix 1 is the bigger win and addresses the root cause directly. Fix 2 is a defense-in-depth measure that compounds the win.

### Fix 1 — Stop publishing chart-internal state through the shared store / VM **(REQUIRED — split into 1a + 1b)**

> **Status:** Required. Confirmed by the iPhone 12 mini trace analyses (§3.6, §3.7): ~99% of all hang time (across both scroll and tab-switch) is in framework work, not in any function we wrote. The only remaining lever is reducing the number of body invalidations that trigger that framework work — which means breaking the `@Published` cascade at the source. The §3.7 trace showed scroll hangs and tab-switch hangs have *different* causes, so the fix is split into two parts that can ship independently.

#### Fix 1a — Decouple per-tick scroll state from `BaseSectionViewModel` ✅ **IMPLEMENTED (2026-04-28)**

**Targets:** Scroll hangs (#5, #6, #7 in §3.7) — currently 308–507 ms.

**What was changed:** A single `@Published` was demoted to a plain `var` at [BaseSectionViewModel.swift:20-30](../../iOS/meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift#L20-L30):

```swift
// before
@Published var scrollPosition: Date = Date()
@Published var isScrolling: Bool = false

// after
/// Per-tick scroll position. Intentionally not @Published — see Fix 1a.
var scrollPosition: Date = Date()
@Published var isScrolling: Bool = false
```

**Why the change is this small (and not the larger `ChartScrollState` refactor originally planned):**

1. **`scrollPosition` is the only per-tick thrasher.** The §3.7 inverted call tree shows ~30 publisher firings during a single scroll gesture; `scrollPosition` accounts for all of them. `isScrolling` only fires twice per gesture (start/end).
2. **The chart binding at [BaseGraphView.swift:881-890](../../iOS/meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L881-L890) is already a manual `Binding(get:, set:)`** — it doesn't use `$viewModel.scrollPosition`, so demoting from `@Published` doesn't break it.
3. **The chart updates its visual position internally**, so the lack of `objectWillChange` firing doesn't make the chart stale during scroll. SwiftUI doesn't need to re-render `BaseGraphView` to keep the chart visually scrolled.
4. **The protocol** [SectionViewModelProtocol.swift:19-20](../../iOS/meApp/Features/Dashboard/Protocols/SectionViewModelProtocol.swift#L19-L20) only requires `var scrollPosition: Date { get set }` — no `@Published` constraint.
5. **`state.graph.xScrollPosition` on `DashboardStore` is already debounced** ([DashboardGraphManager.swift:78-85](../../iOS/meApp/Features/Dashboard/Managers/DashboardGraphManager.swift#L78-L85) only stores `latestScrollPosition` privately; the published store value updates only at scroll-end), so removing the VM-level publish doesn't lose any "real" tab-switch / programmatic-navigation invalidations — those still go through the store.
6. **`isScrolling` stays `@Published`.** When it flips false at scroll-end, the VM publishes once → `BaseGraphView.body` re-runs → reads the current `scrollPosition` (lazily, via direct property access) → all post-scroll cleanup (Y-axis recalc, label refresh, animation re-enable) runs as before. Demoting `isScrolling` would have broken the `.onChange(of: viewModel.isScrolling)` handler at [BaseGraphView.swift:284](../../iOS/meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L284) — and it's not a thrasher anyway.

| Property | Before | After Fix 1a | Why |
|----------|--------|--------------|-----|
| `scrollPosition` | `@Published var` on VM | plain `var` on VM | Per-tick mutations no longer publish. Body no longer invalidates per scroll tick. |
| `isScrolling` | `@Published var` on VM | unchanged | Fires only twice per gesture. Needed for scroll-end transition. |
| `selectedDate` / `selectedPoint` / `showCrosshair` | `@Published` | unchanged | Low-frequency. Not a thrasher. |
| `yAxisDomain` / `yAxisTicks` | plain `var` | unchanged | Already non-published. |

**Behavior implications to verify in re-profile:**

- Scroll-time body recomputes for `BaseGraphView<WeekSectionViewModel>` should drop from ~25–35 (per `Self._printChanges` in §3.4) to ≤2 per gesture (one for `isScrolling = true` at start, one for `isScrolling = false` at end).
- Scroll hangs (§3.7 #5, #6, #7) should drop from 308–507 ms to <100 ms each.
- Tab-switch hangs (§3.7 #2, #3, #4) may also shrink slightly because the VM publishes less overall during the tab-change cascade, but their dominant cost (the multi-observer cascade) remains — Fix 1b is the proper mitigation if they're still ≥300 ms.

**Build status:** `xcodebuild -scheme meApp -configuration Debug -destination "generic/platform=iOS"` succeeds.

#### Fix 1b — Stop the period-change cascade from invalidating the whole dashboard

**Targets:** Tab-switch hangs (#2, #3, #4 in §3.7) — currently 298–510 ms. Driven by `selectedPeriod` changing once and cascading through every `dashboardStore` observer simultaneously.

The §3.7 inverted call tree shows the cascade reaches:

- `WeightTrendView.body`
- `BaseGraphView.body` (active period) + 3 inactive-period `BaseGraphView` instances kept alive as `@StateObject`
- `MetricCell.configure` + `updateDragState` + `layoutSubviews` for every metric card
- `SegmentedButtonView.widestLabelWidth` (label-width recompute)
- `DashboardMetricsSection.body`
- `GoalStreakGridUIKitView.updateUIView`

All on the same frame. Two complementary mitigations, in order of preference:

1. **Narrow observation surface.** The metric cards / segmented button / streak grid observe the *whole* `DashboardStore` even though they only care about a small slice of state. Either:
   - Split `DashboardStore` into multiple narrowly-scoped objects (e.g. `MetricsViewState`, `ChartViewState`, `GoalsViewState`), or
   - Keep one store but expose `@Published` snapshots for each consumer surface and have each consumer observe only its snapshot. Mutating the chart state should not invalidate `MetricCell`.
2. **Stage the cascade across frames.** Where consumer separation isn't tractable, break the period-change cascade into chunks and yield to the run loop between them. Some of this exists in [GraphView.swift:99-189](../../iOS/meApp/Features/Dashboard/Views/Components/GraphView.swift#L99-L189) (`periodChangeTask` with a 50 ms async delay), but it could push more work later: defer non-essential updates (metric cards, streak grid, segmented-button label measurement) until after the chart settles.

**Estimated impact (Fix 1b):** Tab-switch hangs drop to <150 ms. Combined with 1a, the dashboard should feel hang-free in normal use on iPhone 12 mini.

#### Suggested rollout order

1. **Ship Fix 1a first.** Smaller blast radius, addresses scroll hangs, no store changes.
2. **Re-profile.** Confirm scroll hangs are gone. Tab-switch hangs may also shrink slightly because the VM publishes less overall.
3. **Decide on Fix 1b based on residual hang severity.** If tab switches are still ≥300 ms, ship Fix 1b. Otherwise defer.

### Fix 2 — Wire up `.equatable()` on `BaseGraphView` ✅ **IMPLEMENTED**

`BaseGraphView` already had a custom `static func ==` ([BaseGraphView.swift:130-153](../../iOS/meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L130-L153)) that hashes only the inputs that drive rendering:

```swift
hasher.combine(viewModel.yAxisTicks)
hasher.combine(viewModel.yAxisDomain.lowerBound)
hasher.combine(viewModel.yAxisDomain.upperBound)
hasher.combine(viewModel.timePeriod.rawValue)
hasher.combine(viewModel.goalWeight)
hasher.combine(viewModel.showCrosshair)
hasher.combine(viewModel.selectedDate?.timeIntervalSince1970 ?? 0)
hasher.combine(dashboardStore.state.ui.selectedMetricLabel)
```

Note what's deliberately **not** in the hash: `scrollPosition`, `isScrolling`, `selectedPoint`. These are the per-tick thrashers identified in §3.4 — by leaving them out, when only those properties fire the publisher, the struct's `==` returns true and SwiftUI skips the body.

The custom `==` was unused because `.equatable()` was never applied at any call site. Applied at all four:

- [WeekGraphView.swift:24](../../iOS/meApp/Features/Dashboard/Views/Components/WeekGraphView.swift#L24)
- [MonthGraphView.swift:24](../../iOS/meApp/Features/Dashboard/Views/Components/MonthGraphView.swift#L24)
- [YearGraphView.swift:24](../../iOS/meApp/Features/Dashboard/Views/Components/YearGraphView.swift#L24)
- [TotalGraphView.swift:24](../../iOS/meApp/Features/Dashboard/Views/Components/TotalGraphView.swift#L24)

```swift
BaseGraphView(viewModel: viewModel, dashboardStore: dashboardStore)
    .equatable()    // ← added
```

**Why this is more than secondary:** the audit of [BaseSectionViewModel.swift](../../iOS/meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift) showed only 5 `@Published` properties: `selectedPoint`, `selectedDate`, `showCrosshair`, `scrollPosition`, `isScrolling`. Of these, `scrollPosition` and `isScrolling` (the two excluded from the hash) are the ones that fire dozens of times per gesture. `yAxisDomain` and `yAxisTicks` are plain `var` — not published — so they only become observable to SwiftUI when one of the published properties fires. With `.equatable()` wired up, scroll-time publisher firings should now resolve to a no-op body recompute — solving most of the hang without the bigger refactor.

**Build status:** `xcodebuild -scheme meApp -configuration Debug -destination "generic/platform=iOS"` succeeds.

#### ⚠️ Post-implementation finding: Fix 2 alone is insufficient

After applying `.equatable()`, on-device re-profile showed body recomputes during Week scroll were still ~25 (vs. ~35 before — minor improvement, not the projected 10× reduction). The `Self._printChanges` console output still shows `_viewModel changed.` and `_dashboardStore changed.` lines firing per publisher tick.

**Why:** `EquatableView` (what `.equatable()` produces) only short-circuits when a *parent's body* produces a new instance. But `@ObservedObject` invalidation is intrinsic to the view that owns it — when `BaseGraphView`'s observed VM publishes, SwiftUI invalidates `BaseGraphView` directly, bypassing `EquatableView`'s `==` check. Apple's WWDC 2022 "Demystify SwiftUI" session covers this: `.equatable()` is a parent-driven optimization, not a publisher-driven one.

`.equatable()` is harmless and we leave it in place (it costs nothing and provides marginal protection in edge cases), but it does **not** solve the hang on its own.

**Conclusion:** Fix 1 (architectural change) is the actual fix. Fix 2's value drops from "primary fix" to "minor defense-in-depth."

### Fix 4 — Memoize `DashboardStore.displayWeight` ✅ **IMPLEMENTED**

A second on-device profile (clean trace, after app settled) revealed a hot path that the first trace had hidden:

```
WeightTrendView.body.getter
  → weightInfoSection(dashboardStore:)
    → DashboardStore.weightDisplayLabel.getter
      → DashboardStore.displayWeight.getter   ← 27.60 ms each call
        → DashboardGraphManager.calculateInterpolatedAverageForVisibleRange
          → DashboardGraphManager.interpolatedDisplayWeight
```

`WeightTrendView.body` is invalidated per scroll tick (it observes `dashboardStore`). Inside body, `weightDisplayLabel` reads `displayWeight`. `displayWeight` is a *computed property* that performs O(visibleOperations) interpolation costing ~27 ms per call. Multiplied by ~30 body recomputes during a Week scroll = ~800 ms cumulative work — matching the observed 405 ms hang plus framework overhead.

**Fix:** Added two-tier memoization at [DashboardStore.swift:602-697](../../iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift#L602-L697):

1. **Suspend during active scroll.** When `state.graph.isScrolling == true`, return the last cached value. The label briefly stops updating during the gesture and refreshes on scroll-end via the existing `updateYAxisCache` cycle. This eliminates the entire 800 ms of per-tick interpolation cost during scroll.
2. **Signature-based memoization.** When not scrolling, hash the inputs that drive `displayWeight` (selectedPoint, selectedXValue, xScrollPosition, selectedPeriod, weightless mode, anchor weight, operation count + first/last sample). If the signature matches the previous one, return the cached value. This handles repeated reads within a single render frame and back-to-back accesses with identical inputs — common during tab switch where the store re-publishes many times in quick succession with stable inputs.

The original computation body was extracted into a private `computeDisplayWeight()` method; the public `displayWeight` is now the cache-aware entry point.

**Build status:** `xcodebuild -scheme meApp -configuration Debug -destination "generic/platform=iOS"` succeeds.

**Expected impact:**
- Scroll-time hang: the per-tick 27ms interpolation is fully suppressed during active scroll. With ~30 ticks per gesture, ~800 ms of work eliminated. The remaining hang (framework diff/layout) should drop from ~405 ms to <100 ms.
- Tab-switch hang: signature-based memoization deduplicates repeated reads in the same render storm. Modest reduction.

### Fix 3 (cleanup) — Decouple `MetricCell` from chart state **(deferred — out of scope for this PR)**

`MetricCell.configure` showing up in the chart-hang call tree means metric cards are rebuilding when the chart updates. However, `MetricCell` is a `UICollectionViewCell` (UIKit) hosting a SwiftUI subtree via `UIHostingController`, not a SwiftUI view directly — so `.equatable()` doesn't apply. Fixing this requires UIKit-level cell-reuse work plus auditing what state the hosted SwiftUI tree observes.

Audit which `@Published` properties on `DashboardStore` `MetricCell` actually depends on, and either:
- split the store so metric cards observe only metrics-related state, or
- gate the SwiftUI subtree inside the host with its own `.equatable()`.

**Estimated impact:** removes the ~1.2 ms metric-card cost from each chart hang; more importantly stops invisible ripple updates across the dashboard.

---

## 6. Files Modified

### 6.1 Fixes applied 2026-04-28

**Fix 2 — `.equatable()`** (minor effect; left in place as defense-in-depth)

| File | Change |
|------|--------|
| [iOS/meApp/Features/Dashboard/Views/Components/WeekGraphView.swift](../../iOS/meApp/Features/Dashboard/Views/Components/WeekGraphView.swift) | Applied `.equatable()` modifier to `BaseGraphView`. |
| [iOS/meApp/Features/Dashboard/Views/Components/MonthGraphView.swift](../../iOS/meApp/Features/Dashboard/Views/Components/MonthGraphView.swift) | Applied `.equatable()` modifier to `BaseGraphView`. |
| [iOS/meApp/Features/Dashboard/Views/Components/YearGraphView.swift](../../iOS/meApp/Features/Dashboard/Views/Components/YearGraphView.swift) | Applied `.equatable()` modifier to `BaseGraphView`. |
| [iOS/meApp/Features/Dashboard/Views/Components/TotalGraphView.swift](../../iOS/meApp/Features/Dashboard/Views/Components/TotalGraphView.swift) | Applied `.equatable()` modifier to `BaseGraphView`. |

**Fix 4 — `displayWeight` memoization** (the actual hang-killer)

| File | Change |
|------|--------|
| [iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift](../../iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift) | Added `_cachedDisplayWeight` + `_cachedDisplayWeightSignature` storage. Refactored `displayWeight` getter to (a) reuse cached value during active scroll, (b) signature-memoize across stable-input reads, and (c) delegate the original computation to a new private `computeDisplayWeight()`. |

**Fix 1a — `scrollPosition` demoted from `@Published`** (per-tick body invalidation killer)

| File | Change |
|------|--------|
| [iOS/meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift](../../iOS/meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift) | Removed `@Published` from `var scrollPosition: Date` so per-tick chart-scroll mutations no longer fire `objectWillChange`. `isScrolling` left as `@Published` (low-frequency, body needs to react at scroll-end). Added comment explaining the rationale and pointing back to this doc. |

### 6.2 Diagnostic instrumentation — REMOVED 2026-04-28

The instrumentation below was added during the investigation and **removed after the §3.8 verification trace confirmed the fixes held**. The methodology is preserved in this doc as a template for future SwiftUI-perf investigations.

| File | What was added (and then removed) |
|------|-----------------------------------|
| `iOS/meApp/Core/Utilities/GraphSignpost.swift` | **New file** — `os_signpost` helper. **File deleted.** |
| [iOS/meApp/Features/Dashboard/Views/Components/GraphView.swift](../../iOS/meApp/Features/Dashboard/Views/Components/GraphView.swift) | Wrapped `.onChange(selectedPeriod)` cascade in 5 signpost intervals + 1 event marker. **All wrappers removed; original logic restored.** |
| [iOS/meApp/Features/Dashboard/Views/Components/BaseGraphView.swift](../../iOS/meApp/Features/Dashboard/Views/Components/BaseGraphView.swift) | Added `Self._printChanges()` in body (DEBUG); wrapped `onAppear`, `updateCachedChartData`, `getPointsToRender`, `precomputeLabels`, `.onChange(yAxisDomain)` in signposts. **All removed; `*Impl` thin wrappers inlined back into their public functions.** |
| [iOS/meApp/Features/Dashboard/Managers/DashboardGraphManager.swift](../../iOS/meApp/Features/Dashboard/Managers/DashboardGraphManager.swift) | Wrapped `generateChartDataWithYAxisDomain`, `getVisibleOperations`, `getBracketingOperations` in signposts via `*Impl` thin wrappers. **All removed; `*Impl` inlined back.** |

The `os_signpost` cost in release builds without an attached Instruments trace is near-zero, but the wrapper-function indirection adds noise. Removing both keeps the production code path clean and the call tree direct. To re-instrument for a future investigation: re-create `GraphSignpost.swift` from this doc's §2.1 description and re-wrap the same call sites listed in §2.2.

---

## 7. How to Reproduce / Re-Profile

1. Build Debug for device: `xcodebuild -project iOS/meApp.xcodeproj -scheme meApp -configuration Debug -destination "generic/platform=iOS" build`
2. In Xcode, **Product → Profile** (`Cmd+I`), pick **Time Profiler**.
3. Add the **Hangs** and **Points of Interest** instruments via **+ Instrument**.
4. In Time Profiler options, enable **High Frequency Sampling**. Recording mode: **Deferred**.
5. Hit Record. Wait for the dashboard to load. Switch tabs Week → Month → Year → Total → Week (~1s each). Then scroll on Week for ~5s.
6. Stop. In **Points of Interest**, switch the Summary dropdown to **Summary: Regions of Interest** to see our intervals.
7. Right-click any **Hang** bar → **Set Inspection Range and Zoom**.
8. Click the **Time Profiler** track. In the Call Tree panel, click the **Call Tree** button at the bottom and check **Invert Call Tree** + **Hide System Libraries**.
9. The top rows are the actual main-thread bottleneck for that hang.
10. In the Xcode console, look for `BaseGraphView<...>: ... changed.` lines to count body recomputes.

---

## 7.5 Second Profile (after Fix 2) — what it revealed

The Fix 2-only re-profile produced a clean trace (post-cold-start) showing a 405 ms microhang during a Week scroll. Body recompute counts were essentially unchanged (~32 for Week vs. ~35 before Fix 2), confirming `.equatable()`'s limitation. **However, that trace exposed a hot path the first profile had hidden behind un-symbolicated frames:** `WeightTrendView.body → weightDisplayLabel → displayWeight → interpolatedDisplayWeight` at 27 ms per call. This led directly to Fix 4.

Top app-code costs in the 405 ms hang (post-Fix-2, pre-Fix-4):

| Function | Self Weight | % |
|----------|-------------|---|
| `main` (un-symbolicated) | 315.20 ms | 77.8% |
| `DashboardGraphManager.interpolatedDisplayWeight` | 27.70 ms | 6.8% |
| `calculateInterpolatedAverageForVisibleRange` | 27.60 ms | 6.8% |
| `DashboardStore.displayWeight.getter` | 27.60 ms | 6.8% |
| `DashboardStore.weightDisplayLabel.getter` | 13.60 ms | 3.4% |
| `WeightTrendView.body.getter` | 13.60 ms | 3.4% |
| `convertStoredWeightToDisplay` | 13.40 ms | 3.3% |
| `chartContentForSeries` closure | 6.60 ms | 1.6% |
| `DashboardStore.displayUnitText.getter` | 6.20 ms | 1.5% |
| `MetricCell.configure` | 800 µs | 0.2% |

After Fix 4, the 27.7 ms per-call cost should be paid at most once per scroll gesture (not per tick), removing the dominant app-code item from the call tree.

---

## 8. Open Questions / Follow-ups

### Things to do (in order)

1. [x] **Fix 1a — `scrollPosition` demoted from `@Published`** (applied 2026-04-28). Implementation simpler than originally planned (single line change on `BaseSectionViewModel`); rationale documented in §5 / Fix 1a.

2. [x] **Re-profile after Fix 1a (2026-04-28)** per §2.4 reproduction protocol — see §3.8. Result: 0 hangs reported, Thermal Nominal, CPU bursty-not-sustained. Acceptance criteria met; Fix 1b not required at this time.

3. [ ] **(Deferred — not currently required) Fix 1b — narrow the period-change observation surface.** §3.8 verification did not show residual tab-switch hangs warranting this. Keep the plan below as a reference if regressions appear:
    - Audit which slices of `DashboardStore` each dashboard surface actually reads (chart vs. metric cards vs. segmented button vs. streak grid).
    - Either split `DashboardStore` into per-surface narrow `ObservableObject`s, **or** keep one store but expose per-surface `@Published` snapshots and have each consumer observe only its snapshot.
    - Specifically, `MetricCell` should not be invalidated by chart-state mutations. `SegmentedButtonView.widestLabelWidth` should not be recomputed inside the period-change critical path (cache it).
    - Consider deferring non-essential post-period-change work (metric-card reconfiguration, streak grid update) to a later run-loop frame using the existing `periodChangeTask` `await Task.sleep(...)` pattern in [GraphView.swift:99-189](../../iOS/meApp/Features/Dashboard/Views/Components/GraphView.swift#L99-L189).
    - **Acceptance criteria (if reopened):** tab-switch hangs drop to <150 ms.

### Cleanup once the fixes land

- [x] Removed `Self._printChanges()` from `BaseGraphView.body` (2026-04-28).
- [x] Removed `GraphSignpost.swift` and all `GraphSignpost.measure { ... }` / `GraphSignpost.event(...)` call sites (2026-04-28). Methodology retained inline in this doc — recreate from §2.1 / §2.2 if a future regression needs profiling.
- [x] Removed the `*Impl` thin-wrapper indirection in `DashboardGraphManager` (`generateChartDataWithYAxisDomainImpl`, `getVisibleOperationsImpl`, `getBracketingOperationsImpl`) and `BaseGraphView` (`updateCachedChartDataImpl`, `getPointsToRenderImpl`, `precomputeLabelsImpl`) (2026-04-28).
- [ ] Address Fix 3 (`MetricCell` UIKit-level host gating) in a follow-up — requires UIKit cell-reuse work. Currently not load-bearing per §3.8.

### Done ✅

- [x] Fix 2 — `.equatable()` on `BaseGraphView` at all four period call sites.
- [x] Fix 4 — `displayWeight` memoization on `DashboardStore` (suspend during scroll + signature-based caching).
- [x] **Fix 1a — `scrollPosition` demoted from `@Published`** on `BaseSectionViewModel`. Build passes.
- [x] Diagnostic instrumentation: `GraphSignpost`, `Self._printChanges()`, signposts at 13 sites across `GraphView`, `BaseGraphView`, `DashboardGraphManager`. **All removed 2026-04-28** after verification.
- [x] Four on-device profile runs analyzed; per-hang signpost attribution computed via `xctrace export` + Python.
- [x] **Verification trace (§3.8) on-device 2026-04-28:** 0 hangs, Thermal Nominal — fixes confirmed.
- [x] Investigation doc with §3.6, §3.7 hard-data attribution tables and §3.7 inverted-call-tree leaves.

## 9. Lessons Learned

- **`.equatable()` is not a substitute for proper state decoupling when high-frequency `@Published` mutations are involved.** `EquatableView` gates parent-driven re-renders, not publisher-driven ones.
- **`@ObservedObject` makes the entire view a subscriber.** Any `@Published` mutation on the observed object invalidates the view, regardless of whether the body actually depends on the changed property.
- **The `os_signpost` + Time Profiler + `Self._printChanges()` triad is the right diagnostic stack** for SwiftUI hangs. None of the three alone tells the full story; together they distinguish "expensive function" from "too many invocations of a cheap function" from "framework work triggered by churn."
- **Profile on a real device — and on the slowest device users have.** Simulator hides scroll perf and microhangs. iPhone 12 mini surfaced a severe (≥500 ms) scroll hang that newer devices didn't expose at all.
- **Beware confounding signals.** A trace's first ~10 seconds usually contains cold-start / sync spam that produces hangs unrelated to the interaction under test.
- **Always capture an inverted call tree with system libraries visible at least once.** Hiding system libraries is great for finding *your* hot leaves, but keeping them visible reveals the cascade pattern (e.g. `MetricCell.layoutSubviews`, `GoalStreakGridUIKitView.updateUIView`) that tells you *which observers* are firing — which is what matters for `@Published`-driven hangs.
- **`xctrace export` + a small XPath/Python script gives you per-hang signpost attribution that the GUI can't.** Set Inspection Range only filters the call tree; it doesn't sum signpost intervals across hang windows. A 100-line script does and produces hard "X% un-attributed" numbers that drive the right conclusion.
- **A single `@Published` mutation can trigger surprisingly distant work.** In our case, a `selectedPeriod` change cascaded into UIKit-side `MetricCell.layoutSubviews()` and `GoalStreakGridUIKitView.updateUIView()` — work that has nothing to do with the chart but ran synchronously on the same frame because of shared `DashboardStore` observation.

# MOB-518 — Weight-graph rebuild: implementation guide (file-by-file, phase-by-phase)

> **What this doc is.** The **executable how-to** for the weight-graph engine rebuild designed in
> [`MOB-518-chart-engine-rearchitecture.md`](MOB-518-chart-engine-rearchitecture.md). That doc says *why* and
> *what*; **this doc says exactly which files change, what the change is, and how to verify it** — per phase,
> in order. Follow it top to bottom.
>
> **Scope:** the **weight graph only.** Baby/BPM are deferred (see the standing constraints). iOS only, Dev
> config only, base `develop`, branch `MOB-518-chart-engine-scroll-hitch-multi-series`.

---

## 0. Standing constraints (do not violate)

1. **No test files until the very end.** Verify each phase on device + *temporary* `#if DEBUG` logging that is
   removed before commit. The whole automated suite lands in **Phase T**, after you sign off the weight graph.
2. **Do not touch baby / BPM behaviour.** When you edit shared code, drop a `// MULTI-SERIES:` comment noting
   what baby (5–10 percentile series, edge-to-edge grid range) and BPM (2 series + dashed reference lines)
   will need at that spot. Guide the later pass; don't implement it.
3. **Behaviour parity.** The user-visible result stays identical (same adaptive y-axis, same averages on
   lift, same crosshair, same week/month/year/total). We remove *wasted work*, not features.

### Conventions used below

- **Debug probe** (temporary, remove before commit):
  ```swift
  #if DEBUG
  LoggerService.shared.log(level: .debug, tag: "ChartParity",
      message: "period=\(viewModel.timePeriod) series=\(cachedOrderedSeriesNames) " +
               "yDomain=\(viewModel.yAxisDomain) ticks=\(viewModel.yAxisTicks.count)")
  #endif
  ```
- **MULTI-SERIES comment** format: `// MULTI-SERIES: <what baby/BPM needs here>`
- **Build (Dev only — never `-configuration Debug`, it silently builds Production):**
  ```bash
  cd iOS && xcodebuild build -project meApp.xcodeproj -scheme meApp \
    -destination 'generic/platform=iOS' -configuration Dev \
    -derivedDataPath /tmp/mob518-dd \
    CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
  ```
- **Device QA script (run after every phase):** scroll all four periods on a large weight account →
  (a) no dots-appear-then-line-draws-in on settle, (b) no rebuild flash, (c) finger tracks 1:1 with no
  coarse/sparse line mid-scroll, (d) crosshair snaps to a real entry, (e) average settles on lift, (f) y-axis
  matches the visible window in one clean transition, (g) initial open / tab-back / period switch land on the
  latest window with the latest point selected.

---

## Phase 0 — Parity reference + delete dead code (no behaviour change)

**Goal:** freeze a "known good" reference and remove one confirmed dead field.

| File | Change |
|------|--------|
| [`BaseGraphView.swift`](../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift) | Delete `@State private var chartRebuildToken = 0` (`:28`) and the `chartRebuildToken += 1` line in `handleScrollStateChange` (`:174`). It is incremented and **never read** (grep-verified) — S9. Leave the rest of `handleScrollStateChange` (the `isInScrollEndTransition` logic) intact. |
| [`BaseGraphView.swift`](../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift) | Add the temporary **debug probe** at the end of `refreshCachedChartData()` (`:226-249`) to log the parity tuple per period. |

**Verify:** build green; run the device QA script and **screenshot each period** (rest / mid-scroll /
selected). Save screenshots + console parity logs to the ticket (not the repo). These are the oracle for
Phases 1–5.

**Commit:** `MOB-518 Phase 0: drop dead chartRebuildToken, add temp parity probe`

---

## Phase 1 — Kill `.id(lastDataHash)` + one animation driver (S1, S5) ★ biggest win

**Goal:** stop rebuilding the whole `Chart` on every y-settle. This is what causes "points render, then the
line attaches," the settle jerk, and the rebuild hitch.

### 1.1 Remove the identity churn — [`BaseGraphView.swift`](../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift) `mainChartView` (`:543-588`)

- **Delete `.id(lastDataHash)` (`:556`).** The chart already reads its data from `cachedPlottedPoints` /
  `cachedOrderedSeriesNames` (via `chartSeriesContent`) and its scale from `.chartYScale(domain: safeYAxisDomain)`
  (`:557`), so an in-place data or y-axis change now updates the chart **without** a teardown. Period switches
  still remount correctly because each period is a **distinct generic type** (`BaseGraphView<WeekSectionViewModel>`
  vs `<MonthSectionViewModel>` …), so SwiftUI already replaces the view on period change — the `.id` was never
  what drove that.
- **Fallback if a same-count value edit doesn't visually refresh** (unlikely, but check add/delete/edit on
  device): add `.id(seriesValueFingerprint)` where the fingerprint is computed from **series values only, NOT
  the y-axis**. Add to `BaseGraphViewCacheManager`:
  ```swift
  // Value-only identity: refreshes marks on a real data change, but NOT on a y-axis resettle
  // (which must update in place — that was the S1 bug). MULTI-SERIES: include every series' endpoints.
  static func seriesValueFingerprint(orderedSeriesNames: [String],
                                     plotted: [String: [PlottedGraphSeries]]) -> Int { … count + first/last per series … }
  ```
  Prefer **no id at all** first; only add the value-only id if device testing proves it necessary.

### 1.2 One animation driver — [`BaseGraphView.swift`](../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift) (`:581-585`) + [`BaseSectionViewModel.swift`](../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift)

- Keep exactly **one** y-domain animation: `.animation(coordinatedChartAnimation, value: viewModel.yAxisDomain)`
  (`:581`). Leave `.animation(.none, value: scrollPosition)` / `.animation(.none, value: isScrolling)` (they
  *suppress* animation — correct).
- **Remove the competing `withAnimation(.easeInOut(0.15))` in `syncYAxisFromStore`** (`:749-754`): set
  `self.yAxisDomain = cachedDomain` plainly and let the `.animation(...)` modifier own the transition. Two
  animators on the same property is S5.
- Leave `updateYAxisConfiguration` as-is for now (`:404-408`) — its `Transaction(animation: nil)` on ticks is
  fine. The full settle-path collapse is Phase 4.

**MULTI-SERIES:** at the `.id` removal, note that baby/BPM add more series to `cachedPlottedPoints`; the
value-only fingerprint (if used) must fold in every series, and percentile series change only on
profile/DOB/unit — not on scroll.

**Verify (device):** run the QA script. Expect the dots-then-line effect and the settle flash to be **gone**;
finger tracking unchanged. This phase alone should visibly fix symptoms ①③④.

**Commit:** `MOB-518 Phase 1: remove Chart .id rebuild + single y-axis animation`

---

## Phase 2 — One decimation, native scroll (S2, S10)

**Goal:** stop the two stale windowing passes; hand Charts one bounded set and let it scroll natively so the
line/points are correct *while* scrolling (symptom ②).

### 2.1 Single full-domain decimation — [`BaseGraphViewCacheSupport.swift`](../meApp/Features/Dashboard/Managers/Graph/BaseGraphViewCacheSupport.swift)

- Replace `pointsToRender` (`:70-104`) + `sampledBufferPoints` (`:225-252`) — the visible + 30-left + 30-right
  buffer — with **one shape-preserving decimation of the full series**:
  ```swift
  // Decimate ONCE across the whole x-domain (not a moving window). Keep all points when small;
  // above `threshold`, min/max-bucket (or LTTB) to ~`target` points so the line SHAPE is preserved
  // and the crosshair still has real points to snap to.
  static func decimatedForDisplay(_ points: [PlottedGraphSeries],
                                  threshold: Int = 500, target: Int = 400) -> [PlottedGraphSeries]
  ```
  Weight series are daily/monthly aggregates (hundreds of points), so this usually returns the input
  unchanged; it only engages on long `total` ranges.

### 2.2 Stop scroll-window slicing in the view — [`BaseGraphChartContent.swift`](../meApp/Features/Dashboard/Views/Components/BaseGraphChartContent.swift) `visiblePoints` (`:53-69`)

- For the **weight/data series**, return the decimated full series directly — remove the
  `scrollPosition + visibleDomainLength` slicing. The whole (decimated) series is handed to Charts, which
  clips off-screen marks itself.
- **Keep the percentile branch unchanged** (`:60-62`, `PercentileChartWindowing.boundaryExtendedPoints`).
  `// MULTI-SERIES:` note that percentile curves still draw edge-to-edge across `visibleGridRange`, not the
  scroll window — do not route them through `decimatedForDisplay`.
- Drop the now-unused `scrollPosition` / `visibleDomainLength` inputs to `ChartSeriesContent` **for the weight
  path** (keep the struct fields if the percentile path still needs `visibleGridRange`).

### 2.3 Give Charts the full scrollable series — [`DashboardStore.swift`](../meApp/Features/Dashboard/Stores/DashboardStore.swift) `chartSeriesData` (`:755-824`)

- Today non-total periods window to a buffer around the **committed** scroll position
  (`getChartOperationsWithBuffer`, `:765-770`) — that's why you can't scroll past the initial window. **Build
  the display series from the full operations** (decimated) so the native scroll covers the whole range.
- Keep windowing **only** where it's genuinely needed for the *y-axis bracket* calculation (that stays a
  visible-window concern) — do not let it bound the *plotted* series.
- `.chartXVisibleDomain(length:)` and `.chartScrollableAxes(.horizontal)` in
  [`BaseGraphView+ChartModifiers.swift`](../meApp/Features/Dashboard/Views/Components/BaseGraphView+ChartModifiers.swift#L24-L27)
  stay — they define the *window size*, Charts handles the scroll within the full domain.

**MULTI-SERIES:** BPM builds its own 3-series set (`generateBpmChartData`) and baby adds percentile series;
both must also come through as *full* series (BPM aggregated per day/month already). Note it at the
`chartSeriesData` branch points.

**Verify (device):** scroll a long `total` weight range end-to-end — line stays smooth and full-resolution
(no sparse/coarse stretch), crosshair still snaps to real entries. Symptom ② gone.

**Commit:** `MOB-518 Phase 2: single decimation + native full-domain scroll`

---

## Phase 3 — `ChartModel` + off-main `ChartPrep` (S3, S7)

**Goal:** build the plot-ready data **once, off the main actor**, into one immutable value; delete the
six-layer cache tangle. This is the structural core.

### 3.1 New — [`Models/ChartModel.swift`](../meApp/Features/Dashboard/Models/) (create)

```swift
struct YAxisModel: Equatable, Sendable { let domain: ClosedRange<Double>; let ticks: [Double] }

struct ChartModel: Equatable, Sendable {
    let period: TimePeriod
    let productType: EntryType
    let orderedSeriesNames: [String]
    let seriesPoints: [String: [PlottedGraphSeries]]   // pre-sorted asc by xDate, plotXDate baked in, decimated
    let fullResolution: [String: [PlottedGraphSeries]]  // undecimated — for crosshair snapping/selection
    let yAxis: YAxisModel
    let xDomain: ClosedRange<Date>
    let visibleDomainLength: TimeInterval
    let dataFingerprint: Int                            // count + endpoints; EXCLUDES yAxis (this kills S1 for good)
}
```
`GraphSeries`/`PlottedGraphSeries` are already value structs → `ChartModel` is `Sendable` for free.

### 3.2 New — [`Managers/Graph/ChartPrep.swift`](../meApp/Features/Dashboard/Managers/Graph/) (create)

- A pure builder: takes a **Sendable input snapshot** (extract `date`/`weight`/`metric`/`bp` primitives from
  `[BathScaleWeightSummary]` **on the main actor first** — the class is not `Sendable`, respects
  `no_published_swiftdata_model` + `check-snapshot-boundary.sh`) plus config (period, unit, weightless flag,
  anchor, and **precomputed conversion** — pass the unit math as pure values, not a main-actor closure).
- Does the group + sort + `plotXDate` map (currently in `makeCacheUpdate`) + `decimatedForDisplay` + the
  y-axis calc (via `YAxisCalculator` fed pure inputs) → returns a `ChartModel`.
- Runs on a detached/background task; result published on the main actor.

### 3.3 Store owns the model — [`DashboardStore.swift`](../meApp/Features/Dashboard/Stores/DashboardStore.swift)

- Add `@Published private(set) var chartModel: ChartModel?`.
- Add `rebuildChartModel()` that: extracts the Sendable input on main → `await ChartPrep.build(...)` off main →
  assigns `chartModel` on main. Trigger it from the existing change signals (the `dataChangeSignature` /
  `settingsChangeSignature` hooks that `BaseGraphView` already watches, `:115-120`) and on period switch.
- Keep `continuousOperations` as the source; `chartSeriesData` becomes an internal step used by `ChartPrep`
  (or is absorbed into it).

### 3.4 View reads the model — [`BaseGraphView.swift`](../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift)

- **Delete the `@State` cache set** (`:32-41`): `cachedChartPoints`, `cachedGroupedPoints`, `lastDataHash`,
  `cachedPlottedPoints`, `cachedOrderedSeriesNames`, `cachedAllPlottedPoints`, `previousDataHash`,
  `lastDataChangeSignature`, etc. — and `refreshCachedChartData`/`refreshCachedChartDataThrottled`/
  `clearCachedChartData` (`:226-280`).
- `chartSeriesContent` (`:503-537`) builds `ChartSeriesContent` from `dashboardStore.chartModel` instead of the
  `@State` caches. `safeYAxisDomain` reads `chartModel?.yAxis.domain` (still routed through `ChartDomainSanitizer`).
- If the value-only id from Phase 1 was needed, switch it to `.id(chartModel?.dataFingerprint)`.

### 3.5 VM shrinks — [`BaseSectionViewModel.swift`](../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift)

- **Delete** `cachedSeriesData`, `cachedGroupedSeries`, `lastCacheUpdateHash`, `cachedPlotXDates`,
  `cachedChartSeriesData`, `cachedChartSeriesMetric` (`:188-205`), and `updateCachedSeriesData*` /
  `getCachedGroupedSeries` / `invalidateCache` / `getCachedSeriesData` (`:798-900`).
- **Keep:** `scrollPosition`, `isScrolling`, `selectedPoint`/`selectedDate`/`showCrosshair`, `chartFrame`,
  `getChartPosition` (`:595`), `plotXDate` (`:634` — but note plotXDate is now baked into the model at prep
  time; `getChartPosition` can read the model's xDate). The VM becomes UI-state only.
- `getChartPosition` selection math reads `chartModel.fullResolution` for snapping.

**MULTI-SERIES:** `ChartPrep` is where baby percentile + BPM series get built and added to
`seriesPoints`/`fullResolution`. Note that percentile series bypass `decimatedForDisplay` and that BPM adds
reference-line constants (handled in the view, not the model).

**Verify (device):** full QA script; confirm no main-thread hitch at settle (Time Profiler: `makeCacheUpdate`
is gone, prep is off-main). Console parity log must match the Phase-0 oracle.

**Commit:** `MOB-518 Phase 3: off-main ChartPrep + immutable ChartModel; delete cache tangle`

---

## Phase 4 — Single-event y-axis settle, adaptive (Y-B) (S4, S5)

**Goal:** the adaptive y-axis (unchanged behaviour) resettles **once** on finger-lift with **one** animation —
no timer cascade, no competing recompute paths.

### 4.1 Collapse the three settle paths into one

- [`DashboardGraphManager.swift`](../meApp/Features/Dashboard/Managers/DashboardGraphManager.swift)
  `handleScrollPhaseChange(.idle)` (`:60-76`) is the **single** settle trigger: commit the buffered scroll
  position (already there), set `isScrolling = false`, then fire **one** `rebuildChartModel()` (off-main; Y-B
  = recompute y-axis from the freshly-committed visible window + bracketing ops, exactly as
  `updateYAxisCache` does today).
- **Delete** the staggered cascade in
  [`DashboardChartManager.handleScrollEndOptimized`](../meApp/Features/Dashboard/Managers/DashboardChartManager.swift#L227-L264)
  (the +100/200/200/200ms tasks) and the VM's separate `handleScrollEnd` 200ms task
  ([`BaseSectionViewModel.swift:468-480`](../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift#L468-L480)).
  Fold the average/metrics update into the same single settle callback.

### 4.2 One animation

- The single `.animation(coordinatedChartAnimation, value: chartModel.yAxis.domain)` on the chart owns the
  transition. `coordinatedChartAnimation` already returns `nil` while `isScrolling` (`BaseGraphViewCacheManager:100`),
  so the domain only animates on the settle — one smooth easeInOut. Confirm no other `withAnimation` touches
  the domain (Phase 1 removed the `syncYAxisFromStore` one).

**MULTI-SERIES:** BPM uses a fixed clinical y-scale (`getBpmYAxisScale`) and baby uses percentile-driven
scaling (`BabyDashboardChartSupport.yAxisScale`) — the single settle must call the product-appropriate y-axis
builder. Note it in the settle callback.

**Verify (device):** stop scrolling → y-axis rescales to the visible window **once**, smoothly, immediately
(no late lurch, no double-step). Same adaptive behaviour as today.

**Commit:** `MOB-518 Phase 4: single-event adaptive y-axis settle (Y-B)`

---

## Phase 5 — Cleanup (S8, S9)

| File | Change |
|------|--------|
| [`BaseSectionViewModel.swift`](../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift) | Delete `visibleChartSeriesData` + `cachedVisibleSeriesData`/`lastVisibleScrollPosition`/`lastVisibleDataHash` (`:201-239`) — dead for rendering. |
| [`SectionViewModelProtocol.swift`](../meApp/Features/Dashboard/Protocols/SectionViewModelProtocol.swift) | Remove the `visibleChartSeriesData` protocol requirement. |
| [`BaseGraphView.swift`](../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift) | `makeChartDescriptor` (`:708-769`): sort `cachedAllPlottedPoints` once (or read the model's already-sorted `fullResolution`) instead of sorting twice (S8). |
| [`DashboardGraphManager.swift`](../meApp/Features/Dashboard/Managers/DashboardGraphManager.swift) | Remove now-dead `cachedChartSeries`/`canUseCachedChartSeries`/`cacheChartSeries` if `ChartPrep` replaced them. |
| [`GraphViewFlow.md`](../meApp/Features/Dashboard/GraphViewFlow.md) | Update the architecture reference to the stable-Chart + `ChartModel` design. |

Remove any leftover temporary `#if DEBUG` probes.

**Commit:** `MOB-518 Phase 5: remove dead visibleChartSeriesData, bound a11y sort, docs`

---

## Phase T — Tests (only after you sign off the weight graph)

Write the automated suite now (Swift Testing, `meAppTests/Features/Dashboard/…`), per the architecture doc §E:
golden point-set + y-axis parity per period; decimation preserves shape + keeps crosshair-selectable points;
scroll-end settles y-axis exactly once; `ChartPrep` runs 0× between scroll-start and scroll-end; and restore
the orphaned `visibleChartSeriesData` tests as deletions. Confirm all `#if DEBUG` probes are gone.

---

## Touch map — every file × phase

| File | 0 | 1 | 2 | 3 | 4 | 5 |
|------|:--:|:--:|:--:|:--:|:--:|:--:|
| `BaseGraphView.swift` | del dead + probe | **del `.id`**, 1 anim | drop scroll inputs | del cache `@State`, read model | anim confirm | a11y sort |
| `BaseGraphChartContent.swift` | — | — | weight uses full decimated set; keep percentile | — | — | — |
| `BaseGraphViewCacheSupport.swift` | — | — | `pointsToRender`→`decimatedForDisplay` | folded into `ChartPrep` | — | — |
| `BaseGraphViewCacheManager.swift` | — | (opt) value-only fingerprint | — | mostly deleted | — | — |
| `BaseSectionViewModel.swift` | — | del `withAnimation` in `syncYAxisFromStore` | — | **del cache set**, shrink to UI state | del `handleScrollEnd` timer | — |
| `DashboardStore.swift` | — | — | full-domain series | add `chartModel` + `rebuildChartModel()` | settle triggers rebuild | — |
| `DashboardChartManager.swift` | — | — | — | — | **del cascade**, single settle | — |
| `DashboardGraphManager.swift` | — | — | — | — | `.idle` = single settle | del dead series cache |
| `Models/ChartModel.swift` (new) | — | — | — | **create** | +YAxisModel use | — |
| `Managers/Graph/ChartPrep.swift` (new) | — | — | — | **create** | y-axis build path | — |
| `SectionViewModelProtocol.swift` | — | — | — | — | — | del `visibleChartSeriesData` |
| `GraphViewFlow.md` | — | — | — | — | — | update |
| `ChartDomainSanitizer.swift` | keep (W2) | keep | keep | keep | keep | keep |
| `GraphDataPreparer.swift` | keep | keep | keep | called by `ChartPrep` | y-axis inputs | keep |

*Line numbers verified 2026-07-08 on `develop` (post W1/W2). Re-grep symbols if they drift after commits.*

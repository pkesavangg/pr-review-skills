---
name: graph
description: Fix bugs or make enhancements in the Dashboard graph/chart layer (weight, blood pressure/BPM, and baby growth charts). Use whenever the task touches BaseGraphView, BaseSectionViewModel, GraphView, GraphState, PagedChartScrollBehavior, the BPM trend charts, the Baby growth-percentile charts, or chart rendering — for any reason: chart not updating, animation glitch, scroll wrong, Y-axis off, crosshair broken, goal chip wrong, data not showing, performance issue, graph enhancement, or graph refactor. Triggers on "graph bug", "chart issue", "fix graph", "bp chart", "bpm trend", "baby growth chart", "percentile chart", "chart animation", "scroll behavior", "y-axis", "crosshair", "goal chip", "graph not updating", "chart performance", or any mention of graph/chart files.
---

Fix a bug or make an enhancement in the Dashboard graph layer.

The task is: $ARGUMENTS

## Layer Map

Orient yourself before touching anything:

| File | Responsibility |
|------|---------------|
| `GraphView.swift` | Hosts the four `@StateObject` section VMs, switches between period views, handles period-change config |
| `BaseGraphView.swift` | All chart rendering: marks, overlays, label/data caching, animation guards, scroll-end transitions |
| `BaseSectionViewModel.swift` | Shared VM: Y-axis, scroll position, selection, data access, caching |
| `Week/Month/Year/TotalSectionViewModel.swift` | Per-period overrides: `plotXDate`, `handleChartSelection`, `handleScrollPositionChange` |
| `SectionViewModelProtocol.swift` | Full contract — check here when unsure what a VM must expose |
| `DashboardState.swift → GraphState` | Shared mutable state: `selectedPeriod`, `xScrollPosition`, `cachedYAxisDomain`, `isScrolling`, `isGraphReady` |
| `PagedChartScrollBehavior.swift` | Scroll paging + date alignment after drag ends |
| `GraphViewModifier.swift` | Consistent frame/padding for all graph views |

Read only the files relevant to your task.

The table above is the **weight** chart layer (the shared `BaseGraphView` / four-period section VMs). Phase 2 added **separate chart stacks** for the other products — use the matching one:

### Phase 2 — Blood Pressure (BPM) charts
| File | Responsibility |
|------|---------------|
| `Features/Dashboard/BPM/Views/Screens/BpmTrendView.swift` | BP trend chart screen |
| `Features/Dashboard/BPM/Views/Components/BpmMetricsSection.swift` | Systolic/diastolic/pulse metrics section |
| `Features/Dashboard/BPM/Views/Components/` | `BpmReadingCard`, `BpmSnapshotCard`, `ThreeReadingAverage*`, `AhaRating*`, `BpmSummaryCardView`, `BpmDisplayView` |

### Phase 2 — Baby growth charts (percentile)
| File | Responsibility |
|------|---------------|
| `Features/Dashboard/Baby/Views/Screens/BabyTrendView.swift` | Baby growth chart screen |
| `Features/Dashboard/ViewModels/BabyTrendViewModel.swift` | Baby trend VM |
| `Features/Dashboard/Baby/Utils/BabyDashboardChartStyle.swift`, `BabyDashboardChartSupport.swift` | Chart styling/support |
| `Features/Dashboard/Baby/Models/BabyPercentileChartPoint.swift`, `BabyPercentileLineEntry.swift` | Percentile data points |
| `Features/Dashboard/Baby/Utils/BabyWeightPercentileCalculator.swift`, `BabyPercentileGrowthReference.swift`, `BabyGrowthPercentileZTable.swift` | WHO/CDC percentile math |
| `Features/Dashboard/Baby/Environment/BabyGrowthChartEnvironment.swift`, `Enums/BabyPercentileLine.swift`, `Enums/BabyMetric.swift` | Chart environment + metric enums |

BP/baby charts do **not** reuse the weight `BaseGraphView` paging machinery — classify the problem against the right stack before applying the sections below (which are written for the weight graph).

---

## Step 1 — Classify the Problem

Pick the category, then follow its section:

- **A — Wrong data / chart blank** → data pipeline and cache
- **B — Crosshair / selection wrong** → selection handling
- **C — Y-axis wrong** → axis ticks, domain, labels
- **D — X-axis wrong** → tick values, labels, solid lines
- **E — Animation glitch** → animation guards
- **F — Scroll / paging wrong** → scroll position flow, `PagedChartScrollBehavior`
- **G — Goal chip wrong** → chip position calculation
- **H — Enhancement / refactor** → read relevant files, apply change conservatively
- **I — New chart section** → see bottom of this skill (rare)

---

## A — Wrong Data / Chart Blank

Data flows through:
```
DashboardStore.continuousOperations
  → BaseSectionViewModel.refreshData()
  → getCachedSeriesData()
  → BaseGraphView.updateCachedChartData()     ← rebuilds cachedChartPoints + cachedPlottedPoints
  → getPointsToRender()                        ← filters to visible window + 30pt buffer each side
  → chartSeries (SwiftUI Charts rendering)
```

**Triggers for refresh** are in `BaseGraphView.onChange(of: dataChangeSignature)`. That signature hashes `continuousOperations.count` + `selectedMetricLabel`. If your change isn't captured there, the cache won't invalidate — add it to the hash.

**Chart blank on first load:** check `GraphView.onChange(of: selectedPeriod)` — the active VM must be configured before `isGraphReady` flips to `true`.

---

## B — Crosshair / Selection Wrong

Selection flow:
```
User taps chart
  → chartXSelection binding setter (in BaseGraphView.conditionalModifiers)
  → viewModel.handleChartSelection(at:)        ← override per period VM
  → sets selectedDate + showCrosshair
  → BaseGraphView renders crosshairContent + selectionCallout
  → DashboardStore.chartManager.handleChartSelection(at:)
```

Each period VM controls its own snap logic. `WeekSectionViewModel` snaps to the nearest day tick and excludes the phantom trailing tick. Check the relevant period VM's `handleChartSelection` override if the snapping is wrong.

`showCrosshair = false` hides both the crosshair line and the callout. If the callout shows but the line doesn't (or vice versa), one of these is being set independently somewhere.

---

## C — Y-Axis Wrong

Y-axis pipeline:
```
DashboardStore.chartManager.updateYAxisCache()
  → writes GraphState.cachedYAxisDomain + cachedYAxisTicks
  → BaseGraphView.onChange(of: cachedYAxisDomain) → viewModel.syncYAxisFromStore()
  → BaseGraphView.yAxisMarks renders using viewModel.yAxisTicks + cachedYAxisLabels
```

| Symptom | Where to fix |
|---------|-------------|
| Wrong tick values | `updateYAxisCache()` in `DashboardChartManager` |
| Labels show wrong text | `precomputeLabels()` / `getCachedYAxisLabel(_:)` in `BaseGraphView` |
| Ticks animate when they shouldn't | `isDomainChangeOnly` logic — domain-only changes must set this flag to suppress animation |
| Ticks jump on scroll | `syncYAxisFromStore()` not being called, or called with stale values |

---

## D — X-Axis Wrong

X-axis ticks come from `BaseSectionViewModel.xAxisValues` (generated per period). Labels come from the period VM's `formatXAxisLabel(for:)`. `BaseGraphView.conditionalModifiers` renders them via `getCachedXAxisLabel`.

Solid vs. dashed grid lines: `shouldShowSolidLine(for:)` in the period VM. Month start lines are added in `conditionalModifiers` when `timePeriod == .month`.

If labels are stale: `invalidateXAxisCache()` → `precomputeLabels()` recomputes them.

---

## E — Animation Glitch

`BaseGraphView` has a layered animation suppression system. All four guards must be clear before any animation plays:

```
isScrolling             → nil  (hard block during scroll)
isInScrollEndTransition → nil  (5ms settle window after scroll ends)
isDomainChangeOnly      → nil  (domain changed but data hash didn't)
enableYAxisAnimation    → false on first render (prevents mount animation)
```

`coordinatedChartAnimation` computes the final value from all four. If animation fires when it shouldn't, one of these flags isn't being set. If animation is missing when it should play, one is stuck `true`.

`isDomainChangeOnly` is set in `onChange(of: viewModel.yAxisDomain)` by comparing `previousYAxisDomain` vs `previousDataHash`. If the hash comparison is wrong, this flag will be wrong.

For chart-layer concurrency patterns (deferred `@State` mutation, `DispatchWorkItem` throttling, short `Task.sleep` for frame settling) — see `/swift-concurrency` Section 3b.

---

## F — Scroll / Paging Wrong

Scroll position flow:
```
User drags
  → chartScrollPosition binding setter in conditionalModifiers
  → viewModel.handleScrollPositionChange(_:)    ← throttled at 16ms
  → period VM can override to quantize to boundaries (day/week/month start)
  → dashboardStore.state.graph.xScrollPosition updated
```

Scroll-end detection: `onChange(of: viewModel.isScrolling)` catches `true → false`. It sets `isInScrollEndTransition = true`, increments `chartRebuildToken`, then clears after 5ms. If scroll-end is causing a flicker or wrong state, check this handler.

`PagedChartScrollBehavior` controls snap destination after the gesture ends. Key parameters:
- `thresholdRatio` — fraction of visible width to trigger a page jump (default 1/3)
- `twoPageMultiplier` — multiplier over threshold to jump 2 pages
- `valueAlignedBehavior` — snaps to date components after the page calculation

These are set per period in `BaseGraphView.getChartScrollBehavior(for:)`.

---

## G — Goal Chip Wrong

Goal chip position: `getGoalChipPosition()` in `BaseSectionViewModel` maps the goal weight value through the chart coordinate system using `chartFrame`. The X position is fixed at `chartFrame.width - goalChipTrailingPadding`.

If the chip is at the wrong height, the Y coordinate mapping is wrong — check how the goal weight maps to a pixel position relative to `yAxisDomain` and `chartFrame.height`.

---

## H — Enhancement / Refactor

1. Read the files you'll touch (don't guess)
2. Check blast radius: `rg -l "<TypeOrMethod>" meApp -g '*.swift' | head -20`
3. If touching `SectionViewModelProtocol`, check all four period VMs for conformance impact
4. If touching `BaseGraphView`, check all four graph view wrappers still compile
5. If touching `DashboardStore.GraphState`, check `GraphView` and `BaseGraphView` for state consumers

---

## Build Verify

After any change:

```bash
xcodebuild build \
  -project meApp.xcodeproj \
  -scheme meApp \
  -configuration Dev \
  -destination 'generic/platform=iOS' \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

---

## Report

```
Category: Bug fix / Enhancement / Refactor
Files changed: <list>
Root cause (bug fix): <one sentence>
Regression test added: <TestClass.testMethod or "N/A — view layer only">
Next: /verify-tests if VM logic changed | /self-review before commit
```

---

## I — New Chart Section (rare)

Only follow this if you actually need a brand new `SectionViewModel` + `GraphView` pair.

**Create the SectionViewModel** extending `BaseSectionViewModel`, overriding only what differs:
- `timePeriod` — always required
- `plotXDate` — if points need X-shifting (e.g., week → local noon)
- `handleChartSelection` — if snapping differs
- `handleScrollPositionChange` — if scroll needs boundary quantization

**Create a thin GraphView wrapper** (10 lines — just passes VM + store into `BaseGraphView`).

**Wire into `GraphView.swift` in 4 places:**
1. `@StateObject` declaration
2. `isShowingSelectionCallout` switch
3. `chartView` switch
4. `onChange(of: selectedPeriod)` — clear, configure, and scroll-sync

**Add scroll behavior** in `BaseGraphView.getChartScrollBehavior(for:)` if scrollable.

**Add `TimePeriod` case** only if needed — update every exhaustive switch:
```bash
rg -n "case .week" meApp -g '*.swift'
```

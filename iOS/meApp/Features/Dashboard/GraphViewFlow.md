# Graph View – Architecture & Usage Guide

## Overview

The Dashboard graph module now supports both weight and BPM presentation across four time periods (week, month, year, total). It is built with **SwiftUI + Swift Charts** and follows a layered architecture:

- **BaseGraphView** — shared chart renderer for all four periods
- **Period wrappers** — thin SwiftUI views (`WeekGraphView`, `MonthGraphView`, `YearGraphView`, `TotalGraphView`)
- **ViewModels** — `BaseSectionViewModel` base class + four period-specific subclasses
- **Domain managers** — `DashboardGraphManager` (orchestrator) delegates to `GraphDataPreparer`, `GraphRenderingConfiguration`, `GraphInteractionHandler`, `GraphAnimationManager`
- **Coordinating managers** — `DashboardChartManager` (chart init/scroll/Y-axis), `DashboardDisplayManager` (weight/BPM display/labels/formatting)
- **State** — centralized `DashboardState` in `DashboardStore`, with nested `GraphState`, `DataState`, `GoalState`, `MetricsState`, `StreakState`, `UIState`
- **UI surrounding the chart** — shared trend shell plus product-specific top sections and metric sections
- **Multi-product dashboard shell** — `DashboardScreen` can show either the direct product dashboard or the multi-device snapshot chooser
- **Snapshot cards** — lightweight non-interactive week snapshots for weight and BPM, using shared chart rules but not `BaseGraphView`

### Design Priorities

1. Smooth, predictable selection with clear crosshair rules per period
2. Stable Y-axis during scroll — cached domain/ticks, minimal animation churn, refresh only on scroll-end
3. Consistent X-axis tick strategies per period, including "phantom" trailing ticks to prevent edge clipping
4. Shared chart behavior across weight and BPM where the rendering rules are the same
5. Efficient chart data generation with multi-level caching and downsampling during scroll
6. Coordinated animations that suppress during scroll/transitions and re-enable cleanly
7. Lightweight multi-device snapshots that visually match the full graph without reusing the interactive graph stack directly
8. Skeleton loading states for seamless perceived performance

---

## Complete File Map

### Views (SwiftUI)


| File                            | Lines | Responsibility                                                                                                    |
| ------------------------------- | ----- | ----------------------------------------------------------------------------------------------------------------- |
| `BaseGraphView.swift`           | ~900  | Shared chart renderer: axes, grid lines, series, overlays, caching, animation coordination, conditional modifiers |
| `GraphView.swift`               | ~200  | Host view — switches between period views, manages period transitions, skeleton overlay                           |
| `WeekGraphView.swift`           | ~35   | Thin wrapper → `BaseGraphView` with `WeekSectionViewModel`                                                        |
| `MonthGraphView.swift`          | ~35   | Thin wrapper → `BaseGraphView` with `MonthSectionViewModel`                                                       |
| `YearGraphView.swift`           | ~35   | Thin wrapper → `BaseGraphView` with `YearSectionViewModel`                                                        |
| `TotalGraphView.swift`          | ~35   | Thin wrapper → `BaseGraphView` with `TotalSectionViewModel`                                                       |
| `WeightDisplayView.swift`       | ~30   | Headline weight + unit label above chart                                                                          |
| `DashboardTrendView.swift`      | ~60   | Shared trend shell: top content slot + `GraphView` + `SegmentedButtonView`                                        |
| `WeightTrendView.swift`         | ~thin | Weight wrapper around `DashboardTrendView`                                                                        |
| `BpmTrendView.swift`            | ~thin | BPM wrapper around `DashboardTrendView`                                                                           |
| `BpmDisplayView.swift`          | varies | Headline BPM display (systolic/diastolic, pulse, AHA help affordance)                                            |
| `DashboardScreen.swift`         | ~260  | Root screen: multi-device snapshot chooser or product dashboard, navbar, metrics section, empty states            |
| `MultiDeviceSnapshotView.swift` | ~40   | Snapshot chooser list for available product types                                                                  |
| `WeightSnapshotCard.swift`      | ~190  | Lightweight week snapshot for weight                                                                               |
| `BpmSnapshotCard.swift`         | ~250  | Lightweight 3-line week snapshot for BPM                                                                           |
| `DashboardMetricsSection.swift` | ~165  | Body metrics grid + goal/streak section with skeleton states                                                      |
| `BpmMetricsSection.swift`       | ~180  | BPM metric cards: three-reading average + streak cards                                                            |
| `BpmSummaryCardView.swift`      | varies | Reusable summary card used by BPM sheets/cards                                                                    |
| `MetricCardView.swift`          | ~305  | Individual metric tile (value, label, edit mode, selection, drop target)                                          |
| `StreakCardView.swift`          | ~180  | Streak/progress tile (icon, value, label, R4 setup variants)                                                      |
| `GraphSkeletonView.swift`       | ~140  | Animated skeleton for chart area (grid lines + wavy line)                                                         |
| `SkeletonGoalCardView.swift`    | ~67   | Skeleton for goal progress card                                                                                   |
| `SkeletonMetricCardView.swift`  | ~58   | Skeleton for metric tiles                                                                                         |
| `SkeletonStreakCardView.swift`  | ~61   | Skeleton for streak tiles                                                                                         |
| `DragDropModifier.swift`        | ~64   | `ReorderDropDelegate<T>` for drag-and-drop grid reordering                                                        |


### ViewModels


| File                          | Lines | Responsibility                                                                                                                                     |
| ----------------------------- | ----- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `BaseSectionViewModel.swift`  | ~853  | Shared ViewModel: selection state, scroll sync, Y-axis management, chart frame tracking, X-axis caching, goal chip positioning, connected segments |
| `WeekSectionViewModel.swift`  | ~87   | Week overrides: noon plotting, day snapping, day-boundary scroll snap                                                                              |
| `MonthSectionViewModel.swift` | ~134  | Month overrides: section-based selection `[startTick, endTick)`, fallback to section start                                                         |
| `YearSectionViewModel.swift`  | ~70   | Year overrides: month-boundary snapping for scroll and selection                                                                                   |
| `TotalSectionViewModel.swift` | ~255  | Total overrides: padded domain calculation, non-scrollable, strict data-range selection                                                            |


### Domain Managers (Business Logic)


| File                                | Lines  | Responsibility                                                                                                            |
| ----------------------------------- | ------ | ------------------------------------------------------------------------------------------------------------------------- |
| `DashboardGraphManager.swift`       | ~540   | Orchestrator: delegates to sub-managers below, exposes scroll/selection/axis/data APIs                                    |
| `GraphDataPreparer.swift`           | ~540   | Pure data transforms: chart series building, weight/metric normalization, Hermite interpolation, windowing, binary search |
| `GraphRenderingConfiguration.swift` | ~467   | X-axis tick generation, date formatting, scroll position math (optimal/snap/clamp), sample dates                          |
| `GraphInteractionHandler.swift`     | ~193   | Scroll position buffering, visible-operations cache, X-axis cache with invalidation heuristics                            |
| `GraphAnimationManager.swift`       | ~75    | Period transition debouncing (0.15s), chart data throttle (100ms), cleanup                                                |
| `DashboardDataManager.swift`        | ~287   | Binds to `EntryService` publishers, pre-sorted daily/monthly caches, date bounds, analytics                               |
| `DashboardGoalManager.swift`        | ~376   | Goal data loading, progress calculation, weightless mode, formatting, validation                                          |
| `DashboardCacheManager.swift`       | varies | Multi-level cache: continuous ops, visible ops, chart series, label date range                                            |
| `DashboardDateRangeManager.swift`   | varies | Period-specific date range calculations, label formatting, operation filtering                                            |
| `DashboardSyncCoordinator.swift`    | varies | Entry sync, metrics save to API, configuration loading, API ↔ label mapping                                               |
| `DashboardMetricsCalculator.swift`  | varies | Average weight, display weight, synthetic entry creation                                                                  |
| `DashboardChartRules.swift`         | varies | Shared snapshot chart helpers: windowing, scale providers, style rules, plot border renderer                             |


### Coordinating Managers (Cross-cutting)


| File                                | Lines  | Responsibility                                                                                            |
| ----------------------------------- | ------ | --------------------------------------------------------------------------------------------------------- |
| `DashboardChartManager.swift`       | ~391   | Chart init, scroll handling (multi-phase end), Y-axis caching, selection resolution, period changes       |
| `DashboardDisplayManager.swift`     | ~524   | Weight/BPM display logic, date range labels, Y-axis tick formatting, metric info sheet, active month interval |
| `DashboardGridEditingManager.swift` | varies | Progress metrics, drag-drop, removal/toggle, edit mode, wiggle animations                                 |
| `DashboardLifecycleManager.swift`   | ~683   | Dashboard init sequence, entry lifecycle, settings changes, save/reset flows, scene phase handling        |


### Store & State


| File                   | Responsibility                                                                                           |
| ---------------------- | -------------------------------------------------------------------------------------------------------- |
| `DashboardStore.swift` | Central coordinator — owns `DashboardState`, all managers, product-type switching, reactive bindings, UI update batching         |
| `DashboardState.swift` | Nested state container: `UIState`, `MetricsState`, `StreakState`, `GraphState`, `GoalState`, `DataState` |
| `GraphSeries.swift`    | Data model — `GraphSeries` (date, value, series name) + `PlottedGraphSeries` (with precomputed xDate)    |


### Protocols


| File                                  | Key Protocols                                                                                                                                                                                              |
| ------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DashboardMangerProtocols.swift`      | `DashboardDataManaging`, `DashboardGoalManaging`, `DashboardGraphManaging`, `DashboardMetricsManaging`, `DashboardStreakManaging`, `DashboardDateRangeManagerProtocol`, `DashboardSyncCoordinatorProtocol` |
| `DashboardCoordinatorProtocols.swift` | `DashboardStateProviding`, `DashboardChartManaging`, `DashboardDisplayManaging`, `DashboardGridEditingManaging`, `DashboardLifecycleManaging`                                                              |
| `SectionViewModelProtocol.swift`      | Common ViewModel contract: published props, chart config, scroll/selection handlers, formatting                                                                                                            |
| `DashboardCacheManagerProtocol.swift` | Cache operations: continuous ops, visible ops, chart series, label date range                                                                                                                              |


### Utilities & Models


| File                       | Purpose                                                                                        |
| -------------------------- | ---------------------------------------------------------------------------------------------- |
| `YAxisCalculator.swift`    | Nice-scale Y-axis: `calculateYAxis()`, goal-centric fallback, edge buffering, tick enforcement |
| `DashboardConstants.swift` | Time intervals, metric types, UI constants, metric ranges, wiggle animation constants          |
| `DashboardStrings.swift`   | All UI strings: metric names, units, labels, placeholders                                      |
| `BpmDashboardStrings.swift` | BPM-specific dashboard strings                                                                 |
| `DashboardError.swift`     | Error hierarchy (80+ cases) with `LocalizedError`                                              |
| `DashboardModels.swift`    | `GoalCardData`, `StreakCardData`, `DashboardRow`, helper models                                |
| `MetricItem.swift`         | Metric value/label/unit/icon tuple                                                             |
| `MileStoneGridModel.swift` | Grid layout manager for goal/streak reordering                                                 |
| `TimePeriod.swift`         | Enum: `.week`, `.month`, `.year`, `.total`                                                     |
| `DashboardType.swift`      | Enum: `.dashboard4` (2-col), `.dashboard12` (3-col)                                            |


### Modifiers


| File                             | Purpose                                                                                       |
| -------------------------------- | --------------------------------------------------------------------------------------------- |
| `GraphViewModifier.swift`        | `.graphViewStyle()` — consistent frame sizing + padding                                       |
| `PagedChartScrollBehavior.swift` | Custom `ChartScrollTargetBehavior`: paging with 1/2 page jumps, edge snapping, date alignment |


---

## Full Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          DashboardScreen                                    │
│  ┌──────────────────────────────┐  ┌────────────────────────────────────┐   │
│  │ MultiDeviceSnapshotView      │  │ Product Dashboard                   │   │
│  │  • WeightSnapshotCard        │  │  ┌──────────────────────────────┐   │   │
│  │  • BpmSnapshotCard           │  │  │ DashboardTrendView           │   │   │
│  │  • Tap routes to product     │  │  │  topContent:                │   │   │
│  │    dashboard + product type  │  │  │   • WeightDisplayView       │   │   │
│  │                              │  │  │   • or BpmDisplayView       │   │   │
│  │                              │  │  │  GraphView                  │   │   │
│  │                              │  │  │   ┌──────────────────────┐   │   │   │
│  │                              │  │  │   │ BaseGraphView<VM>    │   │   │   │
│  │                              │  │  │   │ • series/grid/axes   │   │   │   │
│  │                              │  │  │   │ • crosshair/callout  │   │   │   │
│  │                              │  │  │   │ • BPM ref lines      │   │   │   │
│  │                              │  │  │   │ • goal chip (weight) │   │   │   │
│  │                              │  │  │   └──────────────────────┘   │   │   │
│  │                              │  │  │  SegmentedButtonView         │   │   │
│  │                              │  │  └──────────────────────────────┘   │   │
│  │                              │  │  ┌──────────────────────────────┐   │   │
│  │                              │  │  │ Below-graph section          │   │   │
│  │                              │  │  │ • DashboardMetricsSection    │   │   │
│  │                              │  │  │ • or BpmMetricsSection       │   │   │
│  │                              │  │  └──────────────────────────────┘   │   │
│  └──────────────────────────────┘  └────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Manager Dependency Graph

```
                        DashboardStore
                 ┌──────────┼──────────────────────────────┐
                 │          │                              │
           State Owner    Domain Managers          Coordinating Managers
           (DashboardState) │                              │
                 │    ┌─────┼─────────┬──────┐       ┌────┼────────┐
                 │    │     │         │      │       │    │        │
                 │  dataManager  graphManager │  chartManager displayManager
                 │    │     │         │      │       │    │        │
                 │    │  goalManager  │  streakManager   │  lifecycleManager
                 │    │              │      │       │    │        │
                 │    │       metricsManager │  gridEditingManager │
                 │    │              │      │                     │
                 │  cacheManager  dateRangeManager            syncCoordinator
                 │    │              │
                 │    │       metricsCalculator
                 │    │              │
                 │  formatter   editSessionManager

            DashboardGraphManager (orchestrator)
                 │
        ┌────────┼──────────┬────────────────┐
        │        │          │                │
 GraphDataPreparer  GraphRenderingConfig  GraphInteractionHandler  GraphAnimationManager
  (pure transforms)  (pure rendering)      (state/cache)           (timing/debounce)
```

---

## Entry Points

### DashboardScreen → Multi-Device Snapshot Or Product Dashboard

```swift
// DashboardScreen.swift
VStack(spacing: 0) {
    if store.availableProductItems.count > 1 && !isInProductDashboard {
        MultiDeviceSnapshotView(availableItems: store.availableProductItems) { selectedItem in
            store.switchProductType(to: ...)
            store.selectProductItem(selectedItem)
        }
    } else if store.productType == .bpm {
        BpmTrendView(dashboardStore: store)
        BpmMetricsSection(store: store)
    } else {
        WeightTrendView(dashboardStore: store)
        DashboardMetricsSection(store: store, parentView: .dashboard, ...)
        actionButtons()
    }
}
```

```swift
// DashboardTrendView.swift
VStack(alignment: .leading, spacing: 0) {
    topContent                                  // WeightDisplayView or BpmDisplayView
    GraphView(dashboardStore: store)           // Shared chart host
    SegmentedButtonView(segments: TimePeriod.allCases, selectedSegment: $localSelectedPeriod)
}
```

```swift
// GraphView.swift — switches active period
switch dashboardStore.state.graph.selectedPeriod {
case .week:  WeekGraphView(viewModel: weekSectionViewModel, dashboardStore: store)
case .month: MonthGraphView(viewModel: monthSectionViewModel, dashboardStore: store)
case .year:  YearGraphView(viewModel: yearSectionViewModel, dashboardStore: store)
case .total: TotalGraphView(viewModel: totalSectionViewModel, dashboardStore: store)
}
```

### Snapshot Cards

The multi-device snapshot cards do **not** reuse `BaseGraphView` directly.

They use lightweight purpose-built views:
- `WeightSnapshotCard`
- `BpmSnapshotCard`

But they share chart rules through `DashboardChartRules.swift`:
- snapshot week window / bracketing logic
- shared Y-axis scale providers
- shared series styling rules
- shared plot border rendering

This keeps snapshots visually aligned with the full graph without pulling in:
- scroll handling
- crosshair/selection
- goal chip overlays
- chart lifecycle/configuration side effects

### GraphView Period Transition

When `selectedPeriod` changes in `GraphView.onChange`:

1. **Immediate** — Cancel pending period change task; clear selection on all VMs
2. **Deferred (50ms)** — Configure only the active VM via `configure(with:)`
3. **Sync scroll** — Force active VM's scroll position to match store's calculated position
4. **Recalculate** — Update Y-axis cache for the new visible region

---

## Sections & Selection Rules

All periods share `BaseGraphView` rendering but differ in selection logic, X-axis ticks, scroll behavior, and plotting transforms. Product type then further affects:
- chart series generation (single weight line vs BPM systolic/diastolic/pulse)
- Y-axis scale calculation
- display/header values
- below-chart metric section
- goal chip visibility
- BPM reference lines and series colors

### Week (`WeekSectionViewModel`)


| Aspect        | Detail                                                                                                   |
| ------------- | -------------------------------------------------------------------------------------------------------- |
| Data source   | Daily summaries from `DashboardDataManager`                                                              |
| Plotting      | Each daily value at **local noon** via `plotXDate(for:)`                                                 |
| X-axis ticks  | 7 daily ticks + 1 phantom trailing tick (+1 day)                                                         |
| Scroll snap   | Day boundaries via `graphManager.snapScrollPosition()`                                                   |
| Selection     | Snap to nearest real tick (excluding phantom); crosshair only if snapped day ∈ `[firstPoint, lastPoint]` |
| Domain length | `DashboardConstants.TimeIntervals.week` (7.15 days — slightly wider for spacing)                         |


### Month (`MonthSectionViewModel`)


| Aspect               | Detail                                                                                                                                                                                                            |
| -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Data source          | Daily summaries                                                                                                                                                                                                   |
| X-axis ticks         | Sunday-based weekly ticks (1, 8, 15, 22, 29) + phantom trailing Sunday                                                                                                                                            |
| Grid lines           | Solid line at month boundaries, dashed lines at week boundaries                                                                                                                                                   |
| Selection            | **Section windowing**: determine `[startTick, endTick)` containing the tap → if points exist in section, select nearest; if empty, select section start. Crosshair only within `[first, last]` + ~50% right slack |
| Active month greying | Points outside the fully-visible month interval rendered with disabled opacity                                                                                                                                    |
| Domain length        | `DashboardConstants.TimeIntervals.month` (32 days)                                                                                                                                                                |


### Year (`YearSectionViewModel`)


| Aspect        | Detail                                                                                  |
| ------------- | --------------------------------------------------------------------------------------- |
| Data source   | Monthly summaries from `DashboardDataManager`                                           |
| X-axis ticks  | One tick per month + phantom trailing month                                             |
| Scroll snap   | Month boundaries                                                                        |
| Selection     | Snap to nearest month tick; crosshair only if snapped month ∈ `[firstMonth, lastMonth]` |
| Domain length | `DashboardConstants.TimeIntervals.year` (365 days)                                      |


### Total (`TotalSectionViewModel`)


| Aspect         | Detail                                                                                                                          |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Data source    | Monthly summaries                                                                                                               |
| Scrolling      | **None** — non-scrollable, shows entire dataset                                                                                 |
| X-axis         | Hidden (no X-axis labels or ticks)                                                                                              |
| Domain padding | Dynamic based on data span: 1 point → ±3 months; <1yr → ±3 months; 1-10yr → ±6 months; 10-50yr → ±30 months; 50+yr → ±60 months |
| Selection      | Strictly within `[firstPoint, lastPoint]` + small right slack; snap to nearest real data point                                  |
| Chart position | Uses full padded `dateRange` domain (no X-axis height deduction)                                                                |
| Lines/points   | Thinner lines, smaller point marks than scrollable periods                                                                      |


---

## BaseGraphView — Shared Renderer

File: `BaseGraphView.swift` (~900 lines)

### Chart Content Builders


| Builder               | What it renders                                                                                                  |
| --------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `yAxisGridLines`      | Horizontal grid lines for each Y-axis tick, with `adjustedTick()` nudging bottom/top ticks to avoid axis overlap |
| `xAxisGridLinesSolid` | Single vertical line at the trailing X-axis position (scrollable periods only)                                   |
| `yAxisBaseline`       | Leading + trailing vertical boundary lines (Total view only, inset by half-pixel to avoid edge clipping)         |
| `chartSeries`         | Iterates cached plotted points by series, calls `chartContentForSeries`                                          |
| `crosshairContent`    | Vertical `RuleMark` at selected date when `showCrosshair` is true                                                |


### Chart Series Rendering (`chartContentForSeries`)

For each plotted point:

- **LineMark** — connects points with `.monotone` interpolation; value clamped to Y-axis domain to prevent overflow
- **PointMark** — visible only when value is within Y-domain; enlarged when selected; greyed out if outside active month interval (month view)
- **Colors** — weight series → `theme.actionPrimary`; metric series → `theme.actionSecondary`; greyed variants for out-of-range points

### Multi-Level Caching (Performance)


| Cache                 | Purpose                                                | Invalidation                                        |
| --------------------- | ------------------------------------------------------ | --------------------------------------------------- |
| `cachedChartPoints`   | All `GraphSeries` from ViewModel                       | Data change signature (ops count + selected metric) |
| `cachedGroupedPoints` | Pre-grouped by series name, sorted by date             | Same as above                                       |
| `cachedPlottedPoints` | Pre-computed `plotXDate` for each point                | Same as above                                       |
| `cachedYAxisLabels`   | Formatted Y-axis tick strings                          | Y-axis domain change, settings change               |
| `cachedXAxisLabels`   | Formatted X-axis date strings                          | Data change, settings change                        |
| `lastDataHash`        | Hash of sampled points + Y-domain for change detection | Automatic                                           |


### Render-Time Downsampling (`getPointsToRender`)

For datasets > 200 points:

1. Separate into visible window, left buffer, right buffer
2. Keep **ALL** visible points (no loss)
3. Downsample each buffer to ~30 points (stride-based)
4. Always include boundary points (closest to visible window)
5. Final sort by date for correct line rendering

### Coordinated Animation Strategy


| State                               | Animation                                          |
| ----------------------------------- | -------------------------------------------------- |
| Scrolling                           | `nil` — no animation                               |
| Post-scroll transition (5ms window) | `nil` — data settling                              |
| Domain-only change (no data change) | `nil` — prevents unnatural metric stretching       |
| Normal + `shouldAnimateChartData`   | `.easeInOut(0.25)`                                 |
| Normal + Y-axis only                | `.easeInOut(0.3)`                                  |
| Scroll position                     | `.none` (always)                                   |
| First frame                         | `nil` (Y-axis animation enabled after first frame) |


### Change Detection (Consolidated)

Two signature hashes reduce from 4+ separate `onChange` handlers to 2:

- `**dataChangeSignature`** — hashes `continuousOperations.count` + `selectedMetricLabel` → triggers data refresh, cache invalidation, label recompute
- `**settingsChangeSignature**` — hashes `currentUnit` + `isWeightlessModeEnabled` → triggers settings change handler, cache update, label recompute

### Throttled Cache Updates

- Minimum interval: 50ms between cache updates during rapid changes
- If throttled, schedules a delayed update via `DispatchWorkItem`
- Prevents excessive CPU usage during Y-axis domain transitions

### Overlays

**Selection Callout:**

- Positioned relative to selected point's chart coordinate
- Left/right offset based on which half of chart the point is in
- X-clamped to prevent cropping at edges (40px min, width-100px max for scrollable, width-85px for non-scrollable)
- Shows formatted date label (e.g., "jun 15, 2025")

**Goal Chip:**

- Green capsule showing goal weight value
- Positioned at `chartFrame.width - 20` horizontally
- Y-position from `viewModel.getGoalChipPosition()` — maps goal weight into chart Y-coordinate
- Animated with `coordinatedChartAnimation`

### Conditional Modifiers

Applied via View extensions based on `isScrollable`:

**Scrollable periods (week/month/year):**

- `.chartXVisibleDomain(length:)`
- `.chartScrollableAxes(.horizontal)`
- `.chartScrollPosition(x:)` — bidirectional binding to VM's `scrollPosition`
- `.chartXAxis` — generates axis marks with phantom tick exclusion
- `.chartScrollTargetBehavior` — `PagedChartScrollBehavior` for paged scrolling
- `.chartXSelection` — updates `localSelectedXValue` for selection

**Non-scrollable (total):**

- `.chartXScale(domain:)` — static padded domain
- `.chartXAxis(.hidden)` — no X-axis
- `.chartXSelection` — selection within padded domain

### Scroll Sync (`conditionalScrollSyncing`)

For scrollable charts:

- Listens to `dashboardStore.state.graph.xScrollPosition` changes → syncs VM scroll position
- Listens to `dashboardStore.state.graph.isScrolling` → syncs VM scrolling state
- Listens to `dashboardStore.state.graph.cachedYAxisDomain/Ticks` → syncs VM Y-axis from store cache
- Selection forwarding: when `localSelectedXValue` changes and not scrolling → delegates to VM's `handleChartSelection(at:)` → if `showCrosshair`, forwards to `dashboardStore.chartManager.handleChartSelection(at:)`

---

## Section ViewModels — Deep Dive

### BaseSectionViewModel (~853 lines)

**Published Properties:**

- `selectedPoint: BathScaleWeightSummary?`, `selectedDate: Date?`, `showCrosshair: Bool`
- `scrollPosition: Date`, `isScrolling: Bool`
- `yAxisDomain: ClosedRange<Double>`, `yAxisTicks: [Double]`
- `chartFrame: CGRect`, `xAxisValues: [Date]`
- `shouldAnimateChartData: Bool`

**Configuration:**

```swift
func configure(with store: DashboardStore) {
    // 1. Store reference
    // 2. Snap initial scroll position (scrollable periods)
    // 3. Sync Y-axis from store cache
    // 4. Build initial chart series data
}
```

**Y-Axis Management:**

- `updateYAxisConfiguration()` — suppressed during scroll; uses visible + bracketing operations
- Syncs from store cache: `state.graph.cachedYAxisDomain` → `yAxisDomain`
- Avoids redundant updates via domain equality check

**Scroll Position Throttling:**

- 16ms minimum interval between position updates
- Buffers position in `GraphInteractionHandler`

**X-Axis Caching:**

- 1-second threshold before regeneration
- Invalidation: large scroll movement (> domainLength/10), near data boundaries, period change

**Goal Chip Positioning (`getGoalChipPosition`):**

```
1. Map goal weight to Y-coordinate within chartFrame height
2. Scrollable periods: subtract X-axis height (~18pt)
3. If goal outside domain: pin near top (if above) or bottom (if below) with offset
4. Return GoalChipPosition(yPosition, placement: .top/.middle/.bottom)
```

**Chart Position Calculation (`getChartPosition`):**

```
1. Map date to X within chartFrame using dateRange domain
2. Map value to Y within chartFrame using yAxisDomain
3. Scrollable: subtract X-axis height from available height
4. Total: use full height (no X-axis deduction)
```

### Period-Specific Behavior Summary


| Method                         | Week                          | Month                           | Year                            | Total                           |
| ------------------------------ | ----------------------------- | ------------------------------- | ------------------------------- | ------------------------------- |
| `plotXDate(for:)`              | Local noon                    | Pass-through                    | Pass-through                    | Pass-through                    |
| `handleScrollPositionChange()` | Snap to day                   | Base behavior                   | Snap to month                   | N/A (not scrollable)            |
| `handleChartSelection(at:)`    | Nearest day in `[first,last]` | Section windowing with fallback | Nearest month in `[first,last]` | Nearest point in `[first,last]` |
| `dateRange`                    | Computed from scroll + domain | Computed from scroll + domain   | Computed from scroll + domain   | Padded domain (dynamic)         |
| `hasXAxis`                     | `true`                        | `true`                          | `true`                          | `false`                         |
| `lineWidth`                    | 2.5                           | 2.5                             | 2.5                             | 2.0                             |
| `pointDiameter`                | 6.0                           | 6.0                             | 6.0                             | 5.0                             |


---

## DashboardGraphManager — Orchestrator

File: `DashboardGraphManager.swift` (~540 lines)

The graph manager is a thin orchestrator that delegates to four specialized sub-components:

### Sub-Components


| Component                     | Responsibility                                                                   |
| ----------------------------- | -------------------------------------------------------------------------------- |
| `GraphDataPreparer`           | Pure stateless data transforms — chart series building, interpolation, windowing |
| `GraphRenderingConfiguration` | Pure rendering config — X-axis ticks, date formatting, scroll math               |
| `GraphInteractionHandler`     | Stateful — scroll buffering, visible-ops cache, X-axis cache                     |
| `GraphAnimationManager`       | Timing — period transition debounce, chart data throttle                         |


### Key APIs

**Scroll Lifecycle:**

```swift
handleScrollStart()              // Set isScrolling, invalidate timer, clear selection
handleScrollEnd() async          // Debounced 0.3s — consume buffered position, set not scrolling
handleScrollEndOptimized(...)    // Multi-phase: updateWeightDisplay, recalculateYAxis, updateMetrics
handleScrollPhaseChange(_:)      // iOS 18+ ScrollPhase handling (idle/tracking/interacting/decelerating)
endScrollingImmediately()        // Force end — invalidate timers, clear caches
```

**Chart Data Generation:**

```swift
generateChartData(from:selectedMetric:isWeightlessMode:anchorWeight:convertWeight:) → [GraphSeries]
generateChartDataWithYAxisDomain(from:visibleOperations:selectedMetric:...yAxisDomain:) → [GraphSeries]
```

The second variant normalizes the selected metric line into the current Y-axis domain for visual alignment.

**Chart Series Cache:**

- Keyed on: `allOperations.count` + `yAxisDomain` + `selectedMetric`
- During scroll: returns cached data if key matches (domain tolerance: 10%)
- Cleared on period change, metric change, and scroll end

**Visible Operations:**

```swift
getVisibleOperations(from:) → [BathScaleWeightSummary]   // Within scroll window + buffers
getStrictVisibleOperations(from:) → [BathScaleWeightSummary]  // Strict window only
getBracketingOperations(from:) → [BathScaleWeightSummary]  // Immediately before/after window (for Y-axis)
```

**Y-Axis:**

```swift
getYAxisScale(from:goalWeight:isWeightlessMode:anchorWeight:convertWeight:chartHeight:) → YAxisScale
calculateAndCacheYAxisDomain(...)  // Computes and stores in state.cachedYAxisDomain/Ticks
```

---

## GraphDataPreparer — Data Transforms

File: `GraphDataPreparer.swift` (~540 lines)

Pure stateless utility — no side effects, no state mutations.

### Chart Series Building

```swift
buildChartSeries(from:selectedMetric:isWeightlessMode:anchorWeight:convertWeight:yAxisDomain:
                 visibleOperations:operationsForYAxis:period:) → [GraphSeries]
```

1. **Weight series** — converts stored int weights to display doubles (respects weightless mode: `displayWeight - anchorWeight`)
2. **Metric series** (optional) — if a secondary metric is selected (BMI, body fat, etc.):
  - Extract metric values from operations
  - Normalize into Y-axis domain with padding and safety margins
  - Clamp outliers to prevent bleeding outside chart area

### Hermite Interpolation (Fritsch-Carlson)

```swift
interpolatedDisplayWeight(at:from:isWeightlessMode:anchorWeight:convertWeight:period:) → Double?
```

Provides smooth weight curves between data points using monotone cubic interpolation. Used for:

- Crosshair weight display when selection falls between actual data points
- Visible-range average calculation

### Binary Search Helpers

```swift
binarySearchFirst(in:where:) → Int?   // O(log n) first match
binarySearchLast(in:where:) → Int?    // O(log n) last match
```

### Windowing

```swift
windowedOperations(from:scrollPosition:period:visibleDomainLength:) → [BathScaleWeightSummary]
```

For datasets > 2000 points: returns only operations within scroll window ± buffer.

### Metric Availability

```swift
canDisplay(_:in:) → Bool  // Requires ≥2 points and variance > 0.001
availableMetrics(in:) → [String]
metricValue(for:from:) → Double?
```

Extracts values for: BMI, body fat, muscle, water, heart rate, bone, visceral fat, subcutaneous fat, protein, skeletal muscle, BMR, metabolic age.

### Static Metric Ranges

Pre-defined fallback ranges for normalization:

- BMI: 18–35
- Heart rate: 40–200
- Percentages (body fat, muscle, water): 0–100
- Visceral fat: 1–30
- BMR: 800–3000
- Metabolic age: 12–100

---

## GraphRenderingConfiguration — Rendering Math

File: `GraphRenderingConfiguration.swift` (~467 lines)

Pure configuration — depends only on Calendar for timezone-correct calculations.

### X-Axis Tick Generation


| Period | Strategy                                                   | Phantom Tick       |
| ------ | ---------------------------------------------------------- | ------------------ |
| Week   | 7 daily ticks at noon, Sun–Sat                             | +1 day trailing    |
| Month  | Sunday-only ticks (1, 8, 15, 22, 29 of each visible month) | +1 trailing Sunday |
| Year   | 1st of each month, for each year in visible range          | +1 trailing month  |
| Total  | Yearly or quarterly ticks depending on data span           | None (no X-axis)   |


### Scroll Position Math

```swift
optimalScrollPosition(for:from:anchorDate:showingLatest:cachedBounds:) → Date
// Centers on anchor date or aligns to show latest data

snapScrollPosition(_:for:) → Date
// Aligns to period boundary (day for week, week for month, month for year)

clampScrollPosition(_:for:minDate:maxDate:) → Date
// Constrains within data bounds + padding
```

### Date Formatting


| Context       | Week                    | Month                   | Year                    | Total                 |
| ------------- | ----------------------- | ----------------------- | ----------------------- | --------------------- |
| X-axis label  | 3-letter day (sun, mon) | Day of month (1, 8, 15) | Month initial (j, f, m) | Hidden                |
| Selected date | "MMM d, yyyy"           | "MMM d, yyyy"           | "MMM yyyy"              | "MMM yyyy"            |
| Date range    | "MMM d – MMM d"         | "MMM d – MMM d"         | "MMM yyyy – MMM yyyy"   | "MMM yyyy – MMM yyyy" |


### Sample Dates

```swift
sampleDates(for:scrollPosition:) → [Date]
```

Generates evenly-spaced dates within visible window for interpolated average calculation.

---

## GraphInteractionHandler — Stateful Caching

File: `GraphInteractionHandler.swift` (~193 lines)

### Scroll Position Buffering

```swift
captureScrollPosition(_:)         // Buffer position during active scroll
consumeBufferedScrollPosition()   // Return & clear buffer at scroll end
```

### Visible Operations Cache

- Returns cached result if scroll position and period unchanged
- **Invalidation**: position moves > `domainLength/10`, latest entry scrolls past viewport, period change
- Falls back to fresh computation on cache miss

### X-Axis Cache

- Tracks: scroll position, period, cached tick values
- **Invalidation**: scroll moves > `domainLength/6`, OR near data boundary edges
- Re-computes when approaching first/last data points

---

## YAxisCalculator — Smart Y-Axis

File: `YAxisCalculator.swift` (~564 lines)

### Core Algorithm: `calculateYAxis()`

```
Input: operations, goalWeight?, isWeightlessMode, anchorWeight?, convertWeight, chartHeight, lastScale?
Output: YAxisScale { min, max, step, ticks: [Double], domain: ClosedRange<Double>, average }
```

**Decision tree:**

1. **Empty state** → goal-centric fallback if goal set, else default 0–100
2. **Small dataset (1-2 entries)** → tight padding (20% or 0.3 units minimum)
3. **Large dataset (3+ entries)** → improved nice-scale algorithm targeting 4 ticks

**Nice Numbers:** `[1, 2, 4, 5, 10, 15, 20, 25, 40, 50, 100, 200]`

**Key behaviors:**

- Edge buffering: extends ticks if data sits within 35% of top/bottom tick
- Tick limits: enforces 3–6 ticks by adjusting step size
- Weightless mode: allows negative ticks; normal mode forces 0 as lower bound
- Goal-centric fallback: symmetric ticks centered on goal weight (±2 steps)
- Last-scale hinting: uses previous scale to reduce jitter between recalculations

---

## DashboardChartManager — Chart Coordination

File: `DashboardChartManager.swift` (~391 lines)

### Chart Initialization

```swift
func initializeChart() {
    1. Calculate optimal scroll position for current period
    2. Set state.graph.xScrollPosition
    3. Trigger Y-axis recalculation
    4. Schedule 300ms delay → mark graph as ready (triggers skeleton → chart transition)
}
```

### Multi-Phase Scroll End

```swift
func handleScrollEndOptimized(updateWeightDisplay:, recalculateYAxis:, updateMetrics:) {
    // Phase 1: 100ms — stop scrolling flag
    // Phase 2: 200ms — Y-axis recalculation
    // Phase 3: 200ms — weight display update
    // Phase 4: 200ms — metrics update
}
```

### Period Change

```swift
func updateSelectedPeriod(_ period: TimePeriod, anchorDate: Date?) {
    1. Update graph state period
    2. Clear selection, caches, chart series
    3. Calculate optimal scroll position (using anchor date from old period midpoint)
    4. Re-initialize chart for new period
}
```

---

## DashboardDisplayManager — Weight & Labels

File: `DashboardDisplayManager.swift` (~524 lines)

### Weight Display Logic

```
Priority for displayWeight:
1. Selected point (crosshair tap) → exact weight
2. Interpolated weight at crosshair date → Hermite spline
3. Visible operations average → arithmetic mean
4. Latest entry weight → fallback
```

### Weight Label Logic


| State                        | Label                    |
| ---------------------------- | ------------------------ |
| Point selected               | "day average"            |
| No selection, period visible | "{period} average"       |
| No entries in period         | "no entries in {period}" |
| No entries at all            | "no entries"             |


### Active Month Interval

For month view: detects when a full calendar month is visible in the scroll window. Points outside this interval are greyed out in `BaseGraphView`.

### Metric Info Sheet Support

- `createEntryForMetricInfo(metricLabel:)` — synthesizes an `Entry` from current metric state for the info sheet
- `allowedMetricsForMetricInfo()` — returns 4 or 12 metrics based on `DashboardType`
- `validateMetricInfoSelection()` — ensures selected metric is in allowed set

---

## DashboardDataManager — Data Pipeline

File: `DashboardDataManager.swift` (~287 lines)

### Data Flow

```
EntryService.$dailySummaries → DashboardDataManager (pre-sort + cache) → DashboardState.data
EntryService.$monthlySummaries → DashboardDataManager (pre-sort + cache) → DashboardState.data
```

### Caching Strategy

- **Pre-sorted arrays**: Daily and monthly summaries sorted by date at write time, avoiding O(n log n) on every read
- **Cached date bounds**: min/max dates per period for O(1) lookup
- **Update order**: Caches updated BEFORE `@Published` state mutation fires, so subscribers see fresh data

### Period → Data Source Mapping


| Period | Source                      |
| ------ | --------------------------- |
| Week   | `dailySummaries` (sorted)   |
| Month  | `dailySummaries` (sorted)   |
| Year   | `monthlySummaries` (sorted) |
| Total  | `monthlySummaries` (sorted) |


---

## DashboardStore — Central Coordinator

File: `DashboardStore.swift` (~400+ lines)

### State Structure

```swift
struct DashboardState {
    var ui: UIState          // Loading, edit mode, drag state, grid layout, alerts
    var metrics: MetricsState // Dashboard type, active metrics, grid config
    var streak: StreakState   // Streak items, active count, visibility
    var graph: GraphState     // Period, scroll position, selection, Y-axis cache, chart height
    var goal: GoalState       // Goal type, weights, progress, delta
    var data: DataState       // Daily/monthly summaries, continuous ops, latest entry
}
```

### Manager Initialization

```swift
// Domain managers (created in init)
metricsManager, graphManager, goalManager, streakManager, dataManager
cacheManager, dateRangeManager, syncCoordinator, metricsCalculator, formatter, editSessionManager

// Coordinating managers (created via initializeCoordinatingManagers)
chartManager, displayManager, gridEditingManager, lifecycleManager
```

### Reactive Bindings

Managers publish state changes → Store observes via Combine → Merges into `DashboardState` → UI re-renders.

### UI Update Batching

Debounces rapid state changes to ~16ms (1 frame) to prevent excessive SwiftUI re-renders.

---

## Scrolling, Selection, and Animations — End-to-End

### 1. Scroll Start

```
User gesture begins
  → BaseGraphView detects scroll position change
  → VM.handleScrollPositionChange() → GraphInteractionHandler.captureScrollPosition()
  → VM.handleScrollStart() → graphManager.handleScrollStart()
  → state.isScrolling = true
  → VM.clearSelection() → crosshair removed
  → BaseGraphView.coordinatedChartAnimation = nil (all animations suppressed)
  → Chart data generation uses cached/downsampled paths
```

### 2. During Scroll

```
Continuous position updates (throttled at 16ms)
  → VM scroll position updated
  → X-axis values: reused from cache unless movement > domainLength/6 or near edges
  → Y-axis domain/ticks: frozen from store cache (no recompute)
  → Chart series: cached if key matches (ops count + domain + metric)
  → Render-time downsampling: ALL visible points kept, buffers downsampled to ~30 each
  → No animations on any chart element
```

### 3. Scroll End (Multi-Phase)

```
Scroll gesture ends
  → Debounce timer (0.3s default, 0.5s for optimized path)
  → Phase 1 (100ms): isScrolling = false, consume buffered position
  → BaseGraphView: isInScrollEndTransition = true (5ms) → no animation during transition
  → Phase 2 (200ms): Y-axis recalculation from visible + bracketing operations
  → Phase 3 (200ms): Weight display update (average for new window)
  → Phase 4 (200ms): Metrics update (selected point or visible average)
  → VM syncs Y-axis from store cache
  → Normal animations re-enabled
  → Chart series cache cleared (fresh generation for new window)
```

### 4. Selection (Tap)

```
User taps chart
  → .chartXSelection updates localSelectedXValue
  → BaseGraphView checks: not scrolling? → forwards to VM.handleChartSelection(at:)
  → VM applies period-specific rules:
    - Week: snap to nearest day in [first, last]
    - Month: find section [startTick, endTick), select nearest point or section start
    - Year: snap to nearest month in [first, last]
    - Total: snap to nearest real data point in [first, last]
  → VM sets selectedDate + showCrosshair
  → If showCrosshair: forward to dashboardStore.chartManager.handleChartSelection(at:)
  → ChartManager resolves closest BathScaleWeightSummary → updates metricsManager
  → BaseGraphView renders: crosshair RuleMark + selection callout + enlarged point mark
  → WeightDisplayView shows selected point's weight (or interpolated)
  → Weight label changes to "day average"
```

### 5. Selection Clear

```
Selection cleared when:
  → Scroll starts (immediate)
  → Period changes (immediate)
  → Tap outside data range (VM returns showCrosshair = false)
  → New scroll end completes (metrics revert to visible average)
```

---

## PagedChartScrollBehavior — Custom Scroll Paging

File: `PagedChartScrollBehavior.swift` (~190 lines)

Custom `ChartScrollTargetBehavior` providing iOS-paging-like behavior for chart scrolling.

### Behavior


| Drag Distance                    | Action                                   |
| -------------------------------- | ---------------------------------------- |
| < `freeLimit` (threshold × 0.75) | Free scroll — lands at proposed position |
| `freeLimit` – `threshold`        | Soft snap to nearest page boundary       |
| `threshold` – `twoPageThreshold` | Jump 1 page                              |
| > `twoPageThreshold`             | Jump 2 pages                             |


### Configuration

```swift
thresholdRatio: 1/3        // Base threshold as fraction of viewport
twoPageMultiplier: 15      // Multiplier for 2-page jump
freeScrollMultiplier: 1    // Below this fraction of threshold → free scroll
```

### Edge Handling

- Edge snapping: within 15% of viewport from edge → snap to edge
- Direction-aware: only snap when moving toward the edge
- Date alignment: optional `ValueAlignedChartScrollTargetBehavior` applied after paging (clamped to ±80% viewport to prevent extra page jumps)

---

## Goal System Integration

### Goal Data Flow

```
AccountService.activeAccount.goalSettings
  → DashboardGoalManager.loadGoalData()
  → GoalState (goalWeight, goalStartWeight, goalDelta, goalProgress)
  → DashboardDisplayManager.getGoalWeightForDisplay()
  → BaseSectionViewModel.goalWeight (published)
  → BaseGraphView.goalChipCallout()
```

### Goal Chip Positioning

```swift
func getGoalChipPosition() -> GoalChipPosition {
    let availableHeight = chartFrame.height - (hasXAxis ? 18 : 0)
    let normalized = (goalWeight - yAxisDomain.lowerBound) / (yAxisDomain.upperBound - yAxisDomain.lowerBound)
    let yPosition = chartFrame.height - (normalized * availableHeight)
    // Clamp and determine placement (.top, .middle, .bottom)
}
```

### Weightless Mode

When enabled: all weights displayed as deltas from anchor weight. Goal weight becomes `goalWeight - anchorWeight` (goal of 0 is valid = "maintain anchor weight").

---

## Skeleton Loading States

### Graph Skeleton (`GraphSkeletonView`)

Shown while `!state.graph.isGraphReady`:

- Simulated Y-axis ticks (5) + X-axis ticks (8)
- Horizontal + vertical grid lines
- Wavy line path simulating chart data
- Pulsing animation (1.2s ease-in-out, repeating)

### Metrics Skeleton

Shown while `shouldShowBodyMetricsSkeleton`:

- Grid of `SkeletonMetricCardView` matching dashboard type (4 or 12 cards)
- Same column count as real grid

### Progress Skeleton

Shown while `shouldShowProgressMetricsSkeleton`:

- `SkeletonGoalCardView` (progress bar placeholder)
- Grid of `SkeletonStreakCardView` (6 items, 2 columns)

### Transition

```
Skeleton (opacity: 1) ←→ Real content (opacity: 0/1)
Animation: .easeInOut(duration: 0.3) on isGraphReady
```

---

## Dashboard Metrics Grid

### MetricCardView


| Prop            | Purpose                                                         |
| --------------- | --------------------------------------------------------------- |
| `value`         | Display value (e.g., "24.5")                                    |
| `label`         | Metric name (e.g., "bmi")                                       |
| `icon`          | Optional SF Symbol or custom icon                               |
| `dashboardType` | `.dashboard4` or `.dashboard12`                                 |
| `isEditMode`    | Enables wiggle animation, drag-drop                             |
| `isRemoved`     | Greyed out state                                                |
| `isSelected`    | Highlighted with inverted colors                                |
| `isDropTarget`  | Dashed border + secondary background                            |
| `parentView`    | `.dashboard` or `.R4ScaleSetup` (affects label text and layout) |


### StreakCardView

Uses `NoteBox` container. Shows icon + value + label for streaks and progress metrics. In R4ScaleSetup context with streak items, shows icon + label only (no value).

### DashboardMetricsSection Layout

```
if shouldShowBodyMetricsSkeleton → skeletonMetricsGrid()
else if shouldShowBodyMetrics    → metricsGridSection() (MetricGridUIKitView)
if shouldShowDivider             → dividerSection()
if shouldShowProgressMetricsSkeleton → skeletonProgressMetrics()
else if shouldShowGoalStreakSection   → goalStreakSection() (GoalStreakGridUIKitView)
```

### Drag & Drop (`ReorderDropDelegate`)

- Generic over `T: Identifiable & Equatable`
- `dropEntered`: calculates correct destination index (forward vs backward move), applies immediately
- `performDrop`: resets drag state
- `dropUpdated`: returns `.move` proposal

---

## DashboardLifecycleManager — Initialization & Lifecycle

File: `DashboardLifecycleManager.swift` (~683 lines)

### Initialization Sequence

```
1. Set dashboard type from account settings
2. Load metrics from local account snapshot
3. Initialize data manager (bind to EntryService publishers)
4. Sync entries from API
5. Refresh streaks
6. Load configuration from API (metrics order, dashboard type)
7. Initialize chart (scroll position, Y-axis, mark ready after 300ms)
8. Update metrics display
9. Mark dashboard as initialized
```

### Entry Lifecycle

```swift
onEntryAdded() / onEntryUpdated() / onEntryDeleted()
  → handleEntryLifecycleChange()
    → Refresh streaks
    → Invalidate caches (visible ops, chart series, label range)
    → Recalculate Y-axis
    → Update selection if needed
    → Refresh weight display
```

### Settings Change Handlers


| Trigger                           | Actions                                               |
| --------------------------------- | ----------------------------------------------------- |
| Unit change                       | Refresh streaks, reload goal data, recalculate Y-axis |
| Goal change                       | Reload goal data, refresh streaks                     |
| Dashboard type change             | Update type, sync removal states, reconfigure grid    |
| Active account change             | Clear all caches, reset chart, full data refresh      |
| Scene phase (background/inactive) | Cancel edit mode                                      |


### Save & Reset

**Save:** Saves metric order + progress metric order to API via `syncCoordinator`.

**Reset:** Full reset flow — show loader, sync defaults from API, reload configuration, rebuild grid, dismiss loader.

---

## Performance Optimization Summary


| Technique                         | Where                                                       | Impact                                        |
| --------------------------------- | ----------------------------------------------------------- | --------------------------------------------- |
| Pre-sorted daily/monthly caches   | `DashboardDataManager`                                      | Avoids O(n log n) on every read               |
| Binary search for windowing       | `GraphDataPreparer`                                         | O(log n) instead of O(n) filter               |
| X-axis tick caching (1s TTL)      | `GraphInteractionHandler`                                   | Prevents regeneration during scroll           |
| Visible operations cache          | `GraphInteractionHandler`                                   | Avoids recomputation during scroll            |
| Chart series cache (keyed)        | `DashboardGraphManager`                                     | Returns cached during scroll if domain stable |
| Render-time downsampling          | `BaseGraphView.getPointsToRender`                           | Caps buffer points at ~30 each side           |
| Pre-computed plotted dates        | `BaseGraphView.cachedPlottedPoints`                         | Avoids per-frame `plotXDate` calls            |
| Pre-computed labels               | `BaseGraphView.cachedYAxisLabels/cachedXAxisLabels`         | No formatting during render pass              |
| Consolidated change detection     | `BaseGraphView.dataChangeSignature/settingsChangeSignature` | 2 handlers instead of 4+                      |
| Throttled cache updates (50ms)    | `BaseGraphView.updateCachedChartDataThrottled`              | Caps CPU during rapid domain changes          |
| Scroll position throttling (16ms) | `BaseSectionViewModel`                                      | Limits position updates to ~60fps             |
| UI update batching (16ms)         | `DashboardStore`                                            | Prevents excessive SwiftUI re-renders         |
| Deferred period change (50ms)     | `GraphView.periodChangeTask`                                | Only configures active VM                     |
| Multi-phase scroll end            | `DashboardChartManager`                                     | Staggers expensive operations                 |
| Domain-only animation suppression | `BaseGraphView.isDomainChangeOnly`                          | Prevents unnatural metric stretching          |
| Windowed operations (>2000 pts)   | `GraphDataPreparer.windowedOperations`                      | Limits data to scroll window                  |


---

## Testing Checklist

### Selection Behavior

- Week: selection snaps to days; crosshair hidden when tapping past last real day (phantom ignored)
- Month: section-based snapping works; empty sections fall back to section start tick
- Month: right-side slack (~50%) allows selecting last point without extreme precision
- Year: snap to nearest month only within `[firstMonth, lastMonth]`; phantom month rejects selection
- Total: selection strictly within real data range; single-point datasets show padded domain
- All periods: selection clears on scroll start
- All periods: crosshair RuleMark appears at correct X position with callout label

### Scrolling

- Scroll clears selection immediately
- Y-axis remains stable during scroll (cached domain/ticks used)
- Y-axis smoothly recalculates on scroll end
- Weight display updates to new window average after scroll end
- Paged scroll behavior: small swipe → 1 page, large swipe → 2 pages
- Edge snapping works when near first/last data
- Scroll position syncs between VM and store

### Chart Rendering

- Weight line renders with primary color, metric line with secondary
- Points greyed out outside active month interval (month view only)
- No line bridging across large temporal gaps (connected segments)
- Y-axis grid lines don't overlap with axis baselines (adjusted ticks)
- Total view: leading + trailing vertical baselines render correctly

### Metric Normalization

- Secondary metric line stays within Y-axis domain during scroll
- Metric switching updates series immediately
- Metric with < 2 data points or zero variance not displayed

### Goal Chip

- Vertically tracks Y-axis domain changes
- Correct X-axis height deduction in scrollable periods
- Pins near top/bottom when goal outside domain
- Weightless mode: goal of 0 is valid and displays correctly

### Skeleton Loading

- Graph skeleton shown during initial load, fades to chart when ready
- Metrics skeleton shown while body metrics loading
- Progress skeleton shown while streaks/goal loading
- Smooth opacity transition between skeleton and content

### Weight Display

- Shows selected point weight when crosshair active
- Shows interpolated weight when selection between data points
- Falls back to visible average when no selection
- Label changes appropriately (day average / period average / no entries)
- Weight label fades out when selection callout is visible

### Edit Mode

- Wiggle animation on metric cards
- Drag-drop reordering works
- R4ScaleSetup context stays in edit mode
- Edit mode cancels on: background, tab switch, outside tap
- Save persists order to API

---

## Troubleshooting


| Symptom                                       | Likely Cause                          | Fix                                                                                     |
| --------------------------------------------- | ------------------------------------- | --------------------------------------------------------------------------------------- |
| Crosshair shows at dataset edge after scroll  | Phantom trailing tick treated as real | Ensure VM snapping excludes phantom tick; check `handleChartSelection` in period VMs    |
| Y-axis flickers during scroll                 | Recomputing Y-scale while scrolling   | Only update Y on scroll end; verify `isScrolling` guard in `updateYAxisConfiguration()` |
| Secondary metric line bleeds outside chart    | Normalization not clamped to Y-domain | Check `buildNormalizedMetricSeriesWithDomain` clamps and safety margins                 |
| Last data point hugs right edge               | No phantom tick for period            | Confirm phantom tick logic in `GraphRenderingConfiguration`                             |
| Selection persists while scrolling            | Selection not cleared on scroll start | Verify `handleScrollStart()` calls `clearSelection()`                                   |
| Chart data doesn't update after period switch | Stale cache                           | Verify `clearChartSeriesCache()` called in `updateSelectedPeriod`                       |
| Goal chip overlaps X-axis labels              | X-axis height not deducted            | Check `getGoalChipPosition()` uses `hasXAxis` guard                                     |
| Skeleton never disappears                     | `isGraphReady` never set true         | Verify `initializeChart()` schedules 300ms delay to set ready flag                      |
| Metric stretches/elongates on Y-axis change   | Domain-only change animating          | Verify `isDomainChangeOnly` flag suppresses animation                                   |
| Weight display shows stale value after scroll | Multi-phase scroll end timing         | Check phases in `handleScrollEndOptimized` complete                                     |
| Chart blank after unit change                 | Cache not invalidated                 | Verify `settingsChangeSignature` triggers cache refresh                                 |
| Month points all same color during scroll     | Active month interval check skipped   | `isPointOutsideActiveMonth` should return false during scroll                           |
| Excessive CPU during scroll                   | Too many cache updates                | Verify throttle (50ms) in `updateCachedChartDataThrottled`                              |


---

## Enhancement Guide

### Adding a New Time Period

1. Add case to `TimePeriod` enum
2. Create `NewPeriodSectionViewModel` extending `BaseSectionViewModel`
  - Override: `plotXDate`, `handleChartSelection`, `handleScrollPositionChange` (if scrollable)
  - Set: `timePeriod`, `hasXAxis`, `lineWidth`, `pointDiameter`, `visibleDomainLength`
3. Create `NewPeriodGraphView` wrapper (thin, delegates to `BaseGraphView`)
4. Add case to `GraphView`'s switch and create `@StateObject`
5. Add tick generation in `GraphRenderingConfiguration`
6. Add data source mapping in `DashboardDataManager.getContinuousOperations(for:)`
7. Add formatting in `GraphRenderingConfiguration.formatXAxisLabel/formatSelectedDate`
8. Add domain length constant in `DashboardConstants.TimeIntervals`
9. Add period change handling in `DashboardChartManager.updateSelectedPeriod`

### Adding a New Secondary Metric

1. Add metric extraction in `GraphDataPreparer.metricValue(for:from:)`
2. Add display string in `DashboardStrings`
3. Add static fallback range in `DashboardConstants.MetricRanges` (if applicable)
4. Add metric to `availableMetrics(in:)` with availability check (≥2 points, variance > 0.001)
5. Normalization is automatic — `buildNormalizedMetricSeriesWithDomain` handles any numeric metric

### Adding a New Chart Overlay

1. Add overlay `@ViewBuilder` function in `BaseGraphView`
2. Position using `viewModel.getChartPosition(for:value:)` or manual chart frame math
3. Account for X-axis height in scrollable periods (`hasXAxis` check)
4. Add to `BaseGraphView.body` ZStack after chart
5. Gate on appropriate state (e.g., `viewModel.showCrosshair`, selection state)

### Modifying Y-Axis Behavior

- **Change tick count**: Adjust `desiredTickCount` in `YAxisCalculator.calculateYAxis()`
- **Change nice numbers**: Modify `niceNumbers` array in `YAxisCalculator`
- **Change edge buffer**: Adjust 35% threshold in `applyEdgeBufferToTicks()`
- **Goal-centric behavior**: Modify `buildGoalCentricFallback()` step count

### Modifying Scroll Behavior

- **Paging sensitivity**: Adjust `thresholdRatio`, `twoPageMultiplier` in `PagedChartScrollBehavior`
- **Edge snap distance**: Adjust `edgeSnapThreshold` (15% of viewport)
- **Scroll end debounce**: Adjust delay in `DashboardGraphManager.handleScrollEnd()` (0.3s default)
- **Multi-phase timing**: Adjust delays in `DashboardChartManager.handleScrollEndOptimized()`

### Modifying Selection Behavior

- Per-period rules are in the respective `SectionViewModel.handleChartSelection(at:)`
- Right-side slack in month view: adjust `~50%` multiplier in `MonthSectionViewModel`
- Total view data-range slack: adjust in `TotalSectionViewModel`

---

## Mini FAQ

**Q: Why is the last week/month not exactly at the right edge?**
A: Phantom trailing ticks keep the last real tick comfortably inside the visible domain. Prevents clipped labels/points and improves scroll-end UX.

**Q: Why don't Y-axis ticks animate?**
A: Tick label animations cause distracting jumping during frequent Y-scale updates. We animate domains and series only, suppress tick animations.

**Q: Why does selection disappear when scrolling starts?**
A: Selection competes with scroll gestures. Clearing immediately avoids jitter and keeps the UI responsive. Selection returns on post-scroll taps.

**Q: How does the secondary metric stay inside the chart?**
A: Values are normalized into the current Y-axis domain with padding, clamps, and epsilon safety margins. `isDomainChangeOnly` flag prevents unnatural stretching during Y-axis recalculation.

**Q: Why the multi-phase scroll end?**
A: Staggering Y-axis recalculation, weight display update, and metrics update across ~700ms total prevents a single CPU spike and keeps the UI responsive during settling.

**Q: Why are there two types of caching (ViewModel + BaseGraphView)?**
A: ViewModel caches (chart series, visible ops) reduce computation. BaseGraphView caches (plotted points, labels, grouped data) reduce per-frame rendering work. They serve different layers.

**Q: How does weightless mode affect the chart?**
A: All weights displayed as `currentWeight - anchorWeight`. Y-axis can go negative. Goal of 0 is valid ("maintain anchor weight"). Same rendering pipeline, different data transform in `GraphDataPreparer`.

**Q: Why UIKit collection views for metrics/streaks instead of pure SwiftUI?**
A: `MetricGridUIKitView` and `GoalStreakGridUIKitView` use UIKit `UICollectionView` for reliable drag-drop reordering with custom gesture handling that SwiftUI's native drag-drop doesn't support well (especially wiggle animations and boundary detection).

---

## Examples

```swift
// Embedding the full dashboard
DashboardScreen()
```

```swift
// Embedding just the chart section
VStack {
    WeightTrendView(dashboardStore: store)
    DashboardMetricsSection(store: store, parentView: .dashboard, openMetricInfoWithoutSelection: .constant(nil))
}
```

```swift
// Using a specific period's VM directly (preview/testing)
BaseGraphView(viewModel: WeekSectionViewModel(), dashboardStore: DashboardStore())
    .frame(height: 265)
```

```swift
// Programmatically changing period (preserving temporal context)
let anchorDate = store.graphManager.visibleMidpoint(for: .week)
store.chartManager.updateSelectedPeriod(.month, anchorDate: anchorDate)
```

```swift
// Forcing chart refresh after external data change
store.lifecycleManager.handleEntryLifecycleChange()
```

---

Last updated: {{23-MAR-2026}}

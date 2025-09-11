# Graph View – Architecture & Usage Guide

## Overview
The Graph module renders weight and secondary metrics across four time periods (week, month, year, total). It is built with SwiftUI + Charts and cleanly split into:

- Base chart view (`BaseGraphView`) that implements shared chart rendering, interaction, and overlays
- Period-specific SwiftUI wrappers (`WeekGraphView`, `MonthGraphView`, `YearGraphView`, `TotalGraphView`)
- A base section view model (`BaseSectionViewModel`) and four concrete view models for period-specific behavior
- A centralized `DashboardStore` (not in this file list) that coordinates state, and a `DashboardGraphManager` that provides X/Y axis generation, selection, chart data generation, and scrolling orchestration
- UI components surrounding the chart like `WeightDisplayView`, `DashboardMetricsSection`, metric/streak cards, and drag/drop infrastructure

The design prioritizes:

- Smooth, predictable selection behavior with clear crosshair rules per period
- Stable Y-axis during scroll with cached domain/ticks, minimal animation churn, and quick refresh on scroll-end
- Consistent X-axis tick strategies per period, including “phantom” trailing ticks to avoid edge clipping
- Efficient data generation and normalization for the selected secondary metric, with caching and downsampling during scroll

Key files:

- Views: `BaseGraphView.swift`, `GraphView.swift`, `WeightDisplayView.swift`, `WeekGraphView.swift`, `MonthGraphView.swift`, `YearGraphView.swift`, `TotalGraphView.swift`
- ViewModels: `BaseSectionViewModel.swift`, `WeekSectionViewModel.swift`, `MonthSectionViewModel.swift`, `YearSectionViewModel.swift`, `TotalSectionViewModel.swift`
- Managers: `DashboardGraphManager.swift`, `DashboardDataManager.swift`
- Dashboard UI: `DashboardMetricsSection.swift`, `MetricCardView.swift`, `StreakCardView.swift`, `DragDropModifier.swift`

---

## Entry Points
The chart is embedded by `GraphView`, which wires up all four period views and maintains the surrounding UI (current weight/label, empty state, etc.).

```swift
GraphView(dashboardStore: DashboardStore())
```

Within `GraphView`:

- StateObjects: one SectionViewModel per period
- Switch on `dashboardStore.state.graph.selectedPeriod` to show exactly one of: `WeekGraphView`, `MonthGraphView`, `YearGraphView`, or `TotalGraphView`
- Clears selection when period changes and reconfigures the active section view model
- Delegates scroll position and Y-axis cache updates to the store/graph manager

`BaseGraphView` is the shared renderer used by each period wrapper.

---

## Architecture
```
┌───────────────────┐   user gestures   ┌──────────────────────┐
│   SwiftUI Views   │ ─────────────────▶ │ Section ViewModel(s) │
│  (Week/Month/...) │   @ObservedObject │  (Base + per period) │
└─────────▲─────────┘                   └───────────┬──────────┘
          │   @State/@Published                         │
          │                                            ▼
          │                                ┌──────────────────────┐
          │                                │   DashboardStore     │
          │                                │  (selection, scroll) │
          │                                └───────────┬──────────┘
          │                                            ▼
          │                                ┌──────────────────────┐
          │                                │ DashboardGraphManager│
          │                                │  (X/Y, data, cache)  │
          │                                └───────────┬──────────┘
          │                                            ▼
          │                                ┌──────────────────────┐
          │                                │ DashboardDataManager │
          │                                │  (feeds summaries)   │
          │                                └──────────────────────┘
```

Responsibilities:

- Views: Render Charts, overlays (crosshair, goal chip), and apply Chart modifiers
- Section VMs: Per-period selection rules, X plotting rules, Y-axis update triggers, scroll sync, and connected-segment logic
- Store: Holds global graph UI state (scrolling, selection, xScrollPosition, cached Y-axis domain/ticks) and bridges selection to metrics/labels
- GraphManager: X-axis generation per period, Y-axis calculations with caching, selection orchestration, metric normalization, scroll lifecycle and data simplification
- DataManager: Supplies daily/monthly aggregated summaries from `EntryService`

---

## Sections & Selection Rules

All periods share common UI from `BaseGraphView`, but differ in selection logic, X-axis ticks, and plotting:

### Week
- VM: `WeekSectionViewModel`
- Plotting: each daily value is plotted at local noon via `plotXDate(for:)`
- X-axis: generated daily within buffer; a phantom trailing tick (+1 day) prevents the last real tick from hitting the right edge
- Selection: snap to nearest real tick (excluding the phantom); only show crosshair if snapped day is within the connected line bounds `[firstPoint, lastPoint]`

### Month
- VM: `MonthSectionViewModel`
- X-axis: optimized day/week ticks (1, 8, 15, 22, 29) and buffer
- Selection windowing: determine the active X “section” `[startTick, endTick)` that contains the tap; if there are points inside, select nearest; else select the section’s start tick. Crosshair only if touch is within [first, last] (+ small right slack for human error/UTC offsets)
- Gaps: selection rules tolerate holes by choosing section starts when no points are available

### Year
- VM: `YearSectionViewModel`
- X-axis: one tick per month, plus a phantom month to keep December visible
- Selection: snap to nearest month, but only if the snapped month lies within `[firstMonth, lastMonth]`

### Total
- VM: `TotalSectionViewModel`
- X-axis: hidden; the domain is padded by months to avoid cramped layout for single/few points
- Selection: only within the real data range `[firstPoint, lastPoint]` (with a small right-edge slack), snap to nearest real data point; no X-axis height adjustment needed

---

## BaseGraphView (shared renderer)
File: `BaseGraphView.swift`

Key behaviors:

- Renders weight plus optional selected metric series
- Draws custom Y-axis grid lines and baselines
- Applies per-period Chart modifiers via `conditionalModifiers` (scrollable vs non-scrollable)
- Delegates selection to the active section VM, which in turn coordinates with the `DashboardStore`
- Overlays: crosshair RuleMark, selection callout label, and goal chip
- Disables expensive animations during scroll; re-enables Y-axis and series animations post-mount and when data changes (not scrolling)

Important details:

- Y-grid overlap fix: `adjustedTick(_:)` nudges the lowest and highest ticks slightly to avoid visual overlap with axis baselines
- ForegroundStyle scale: weight uses primary color; other metric uses secondary
- Series splitting: `getConnectedSegments` prevents lines from bridging large temporal gaps
- Selection pipeline:
  - `.chartXSelection` updates VM via `handleChartSelection`
  - If `showCrosshair` remains true, forwards the VM-preferred date to `DashboardStore.handleChartSelection(at:)`
  - The store/graph manager then updates selected point and dependent metrics/labels
- Touch/scroll gating: selection handling is disabled while touch mode is determined as scrolling; selection is cleared on scroll start

Animation strategy:

- After first frame, enable a mild `.easeInOut` animation on Y-axis domain changes
- Suppress tick animations to keep tick labels stable
- Animate series data transitions using a lightweight `seriesAnimationToken` that hashes only a few sample points of the visible series (no work during scroll)

Overlays:

- Selection callout: shows the formatted X label near the selected point, clamped to keep inside chart bounds
- Goal chip: positioned by VM via `getGoalChipPosition()`, accounting for X-axis height in scrollable periods

Scroll sync:

- For scrollable charts, the view listens to `dashboardStore.state.graph` changes and updates the VM’s `scrollPosition`, `isScrolling`, and Y-axis cache
- On scroll start: clear selection immediately; on scroll end: the store recomputes visible operations and Y-axis, VM syncs from cache

---

## Section ViewModels
File: `BaseSectionViewModel.swift` (+ period subclasses)

Shared responsibilities:

- Hold transient selection state: `selectedDate`, `showCrosshair`, `selectedPoint`
- Keep `scrollPosition` and `isScrolling` in sync with the store
- Maintain `yAxisDomain` and `yAxisTicks` and update them from `DashboardGraphManager`
- Convert dates to plotting coordinates and compute overlay positions

Common APIs:

- `configure(with:)`: injects `DashboardStore`, snaps initial scroll position (for scrollable periods), and syncs Y-axis cache
- `updateYAxisConfiguration()`: recalculates Y-axis using the store’s graph manager. Suppressed during scroll; recalculated on scroll end
- `handleChartSelection(at:)`: period-specific selection rules; set `selectedDate` and `showCrosshair`
- `clearSelection()`: resets selection state (called at scroll start and period changes)
- `getConnectedSegments(from:)`: splits series to avoid bridging long gaps
- `plotXDate(for:)`: per-period plotting transform (e.g., week → local noon)
- `formatXAxisLabel(for:)` and `formatSelectedXAxisLabel()`: label helpers; selection formatting uses store’s graph manager

Period-specific overrides:

- Week: `plotXDate` (local noon), selection snaps to nearest real day within `[first,last]`
- Month: windowed selection per `[startTick,endTick)`, fallback to start tick when section has no data
- Year: snap to nearest month if between `[firstMonth,lastMonth]`
- Total: custom `dateRange` with month-padding; selection strictly within real data; custom `getChartPosition` (no X-axis height adjustment)

Sizing & stroke:

- Scrollable periods: thicker lines, larger points; Total: thinner lines, smaller points
- `symbolArea(forDiameter:)` converts visual sizes to Charts’ `symbolSize`

---

## DashboardGraphManager
File: `DashboardGraphManager.swift`

Core duties:

- X-axis generation with buffer and phantom ticks (week/day with +1-day phantom, year/month with +1-month phantom; month/total adjusters)
- Y-axis scale calculation via `YAxisCalculator` (not listed here), with last-scale hinting and caching (`cachedYAxisDomain`, `cachedYAxisTicks`)
- Selection orchestration and metric updates through the store
- Chart data generation:
  - `generateChartData` for simple cases
  - `generateChartDataWithYAxisDomain` to normalize the selected metric into the current Y-axis domain for consistent on-screen alignment
- Caching and performance:
  - Reuse chart data when metric and effective weight ranges are similar
  - Downsample operations while scrolling to cap points (~100)
  - Cache X-axis values during scroll and reuse until movement exceeds thresholds or edges are near
- Scroll lifecycle:
  - `handleScrollStart`/`handleScrollEnd` debounce and clear selection immediately
  - Update `xScrollPosition` at end and recompute visible operations and Y-axis cache

Metric normalization:

- When a metric is selected (e.g., BMI), gather its values over the relevant operations
- Compute a dynamic metric range with small padding; clamp outliers; map to the current Y-axis domain while maintaining safety margins so the secondary line never bleeds outside the visible range

Utilities:

- `getVisibleOperations` and strict variant to compute what’s on-screen
- `calculateOptimalScrollPosition` to align the initial window with X-axis values and show latest data sensibly
- Label helpers: `formatXAxisLabel`, `formatSelectedDate`, `formatDateRange`, and `fallbackTimeLabel`

---

## DashboardDataManager
File: `DashboardDataManager.swift`

- Listens to `EntryService` publishers for daily and monthly summaries, and mirrors them into `state`
- Exposes `getContinuousOperations(for:)` returning period-appropriate arrays:
  - Week/Month → daily summaries
  - Year/Total → monthly summaries
- Provides helpers to clear/validate caches and basic analytics

---

## Surrounding UI Components

### WeightDisplayView
File: `WeightDisplayView.swift`

- Shows the headline weight + unit above the chart
- Uses `.id(unitText)` to force redraw on unit changes
- Fades out the standard weight label in `GraphView` when the selection callout is visible

### DashboardMetricsSection, MetricCardView, StreakCardView
Files: `DashboardMetricsSection.swift`, `MetricCardView.swift`, `StreakCardView.swift`

- Render customizable metric grid and streak/goal area below the chart
- Support edit mode with wiggle animations; prevent exiting edit mode inside R4 scale setup context
- `MetricCardView` dynamically styles for selection/edit states and supports icon-first layout in R4 setup

### Drag & Drop
File: `DragDropModifier.swift`

- `ReorderDropDelegate` handles reordering with immediate visual feedback (no animation delay), correct index math, and robust drop lifecycle

---

## Scrolling, Selection, and Animations – End-to-End

1) Scroll start
- Gesture begins → store/graph manager sets `isScrolling = true`
- VM clears selection immediately; BaseGraphView removes crosshair and label
- Data generation switches to cached/downsampled paths

2) During scroll
- X-axis values reused until movement is large or near dataset edges
- Y-axis domain/ticks cached in the store; VMs sync from cache (no recompute)
- Animations suppressed for domain/tick churn; line animation disabled

3) Scroll end (debounced)
- Store updates `xScrollPosition`, recomputes visible operations, refreshes Y-axis scale and caches
- VMs sync Y-axis from store; normal animations re-enabled
- Weight display and dependent metrics recomputed for the new window

4) Selection
- `.chartXSelection` sends candidate date to the VM
- VM enforces period-specific snapping and visibility rules; sets `selectedDate`+`showCrosshair`
- If valid, forward preferred date to the store → graph manager finds closest summary and updates metrics; crosshair shows

---

## Goal Chip Positioning
File: `BaseSectionViewModel.swift`

- `getGoalChipPosition()` maps goal weight into Y coordinate within the chart’s available height
- Scrollable periods subtract X-axis height (~18pt) so the chip does not overlap labels
- If goal lies outside domain, chip pins near top/bottom to indicate direction

---

## Testing Checklist
- [ ] Week: selection snaps to days; crosshair hidden when tapping past the last real day (phantom ignored)
- [ ] Month: section-based snapping works; when a section has no points, start tick is selected
- [ ] Year: snap to nearest month only within `[firstMonth,lastMonth]`; phantom month does not permit selection
- [ ] Total: selection strictly within real data range; single-point datasets show padded domain and centered point
- [ ] Scrolling: selection clears on scroll start, Y-axis remains stable, re-computes smoothly at end
- [ ] Metric normalization: secondary line stays within Y-axis domain while scrolling and when switching metrics
- [ ] Goal chip: vertically tracks domain changes; respects X-axis height where applicable
- [ ] Weight label fades out when selection callout is visible; returns when cleared

---

## Troubleshooting
| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| Crosshair shows at dataset edge after scroll | Phantom trailing tick treated as real | Ensure VM snapping excludes phantom tick; see Week/Year VMs |
| Y-axis flickers during scroll | Recomputing Y-scale while scrolling | Only update Y on scroll end; rely on store cache during scroll |
| Secondary metric line bleeds outside chart | Normalization not clamped to Y-domain | Use `generateNormalizedMetricSeriesWithDomain` and safety clamps |
| Last data point hugs right edge | No phantom tick for period | Confirm phantom tick logic for week/year in graph manager |
| Selection persists while scrolling | Selection not cleared on scroll start | Verify VM/store set `isScrolling` and call `clearSelection()` |

---

## File Map & Key Responsibilities
- `BaseGraphView.swift`: Shared chart, axes, overlays, selection/scroll modifiers, animations
- `WeekGraphView.swift` / `MonthGraphView.swift` / `YearGraphView.swift` / `TotalGraphView.swift`: Thin wrappers around `BaseGraphView`
- `BaseSectionViewModel.swift`: Shared chart state, selection, Y-axis, scroll syncing, helpers
- `WeekSectionViewModel.swift`: Noon plotting; day snapping within data bounds
- `MonthSectionViewModel.swift`: Section-based selection with fallbacks
- `YearSectionViewModel.swift`: Month snapping within `[first,last]` months
- `TotalSectionViewModel.swift`: Padded domain; strict selection; custom positioning
- `DashboardGraphManager.swift`: X/Y axes, selection orchestration, normalization, caching, scroll lifecycle
- `DashboardDataManager.swift`: Feeds daily/monthly summaries; basic analytics and cache mgmt
- `GraphView.swift`: Hosts chart + weight label/empty view; period switching; clears selection on changes
- `WeightDisplayView.swift`: Headline weight + unit; redraw on unit changes
- `DashboardMetricsSection.swift`, `MetricCardView.swift`, `StreakCardView.swift`: Dashboard grid/streak UI with edit-mode semantics
- `DragDropModifier.swift`: Reordering support with instantaneous visual feedback

---

## Mini FAQ
- Q: Why is the last week/month not exactly at the right edge?
  - A: We add a phantom trailing tick so the last real tick is comfortably inside the visible domain. This avoids clipped labels/points and improves scroll-end UX.
- Q: Why don’t ticks animate?
  - A: Tick animations cause distracting label jumping during frequent Y-scale updates. We animate domains and series only, suppress tick animations.
- Q: Why does selection disappear when I start scrolling?
  - A: Selection competes with scroll gestures. We clear it on scroll start to avoid jitter and to keep the UI responsive; it returns on post-scroll selections.
- Q: How does the second metric stay inside the chart?
  - A: Its values are normalized into the current Y-axis domain, with clamps and epsilon safety so it never bleeds off the top/bottom.

---

## Examples
```swift
// Embedding in a dashboard view
VStack {
    GraphView(dashboardStore: dashboardStore)
    DashboardMetricsSection(store: dashboardStore, parentView: .dashboard, openMetricInfoWithoutSelection: .constant(nil))
}
```

```swift
// Using a specific period’s VM directly (preview/testing)
BaseGraphView(viewModel: WeekSectionViewModel(), dashboardStore: DashboardStore())
    .frame(height: 265)
```

---

Last updated: {{09-SEP-2025}}



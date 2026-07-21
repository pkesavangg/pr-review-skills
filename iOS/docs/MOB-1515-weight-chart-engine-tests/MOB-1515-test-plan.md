# MOB-1515 — v2 weight-chart engine, Phase T unit tests · test plan

**Ticket:** [MOB-1515](https://greatergoods.atlassian.net/browse/MOB-1515) · Task under **MOB-516** (5.1.0 Performance Hardening)
**Branch:** `MOB-1515-weight-chart-engine-unit-tests` (off `develop`)
**Goal:** close the unit-test gap flagged in the MOB-518 PR review — the v2 chart engine's scroll-commit / settle / selection-snap logic shipped untested. Views are coverage-excluded, so cover the logic at the **pure builders + store seams + extracted helpers**.

---

## AC name drift (read first)

The ticket's acceptance criteria were written against **MOB-518** symbol names. **MOB-1516 generalised the `Weight*` engine to product-neutral `Trend*`.** Mapping:

| AC (MOB-518) name | Current (MOB-1516) name | Where |
|---|---|---|
| `WeightChartHost` | `TrendChartHost` | `Chart/Views/TrendChartHost.swift` |
| `WeightChartView` | `TrendChartView` | `Chart/Views/TrendChartView.swift` |
| `DashboardStore.rebuildWeightChartModel` | `DashboardStore.rebuildChartModel(scrollPosition:)` | `Stores/DashboardStore.swift:936` |
| `DashboardStore.resettleWeightYAxis` | `DashboardStore.settleChart(scrollPosition:)` | `Stores/DashboardStore.swift:1000` |
| `DashboardStore.selectWeightPoint` | `DashboardStore.selectPoint(at:)` | `Stores/DashboardStore.swift:1089` |
| `WeightChartHost.nearestEntry` | `TrendChartHost.nearestEntry` (private) | `Chart/Views/TrendChartHost.swift:391` |
| `ChartPrep.fullXAxisValues` | **removed** — see "Obsolete AC" below | — |

Intent is unchanged; only the symbols moved.

---

## Status of each acceptance criterion

| AC | Current target | Status | Action |
|---|---|---|---|
| **#1** settle updates y-axis in place; co-plotted metric forces full rebuild; per-period golden `ChartModel` parity | `settleChart` / `rebuildChartModel`; `ChartModel.withYAxisAndTicks` | **Partial.** `withYAxisAndTicks` parity + `buildWeight` per-period shape/determinism already covered in `ChartPrepTests`. The **store-level dispatch** (weight→in-place, co-plot→rebuild, bpm→`settleBpm`, baby→rebuild) is **untested**. | Add store-level dispatch tests (§ File D). |
| **#2** `selectPoint` per-period snap (week→day, month→shown lines/entry, year→month-1st, total→nearest entry) | snap math lives in `TrendChartHost.snappedSelectionDate` (**private, in a View**); `DashboardStore.selectPoint(at:)` applies an already-snapped date | **Not testable today** — snap is buried in the View. | **Extract** snap → pure helper (§ Refactor), then test per-period (§ File C); add store `selectPoint` clear/set (§ File D). |
| **#3** extract `nearestEntry` into a pure, testable helper + assert nearest-entry snapping | `TrendChartHost.nearestEntry` (private) | **Not done.** | Fold into the § Refactor extraction; test in § File C. |
| **#4** decimation preserves shape/endpoints; full x-domain scroll-independent | `ChartDecimator.decimate`; `GraphRenderingConfiguration.fullXDomain` (used by `buildWeight` for `xDomain`) | **Partial.** `fullXDomain` insets covered in `GraphRenderingConfigurationTests`; **`ChartDecimator` has zero tests** and scroll-independence isn't asserted. | Add decimator tests (§ File A) + scroll-independence tests (§ File B). |
| **#5** coverage ≥ 80%; behaviour-parity; lands before MOB-516 closes | — | follows from #1–#4 | run device coverage after. |

### Obsolete AC item
AC #4 mentions **`fullXAxisValues` being scroll-independent**. That symbol no longer exists: MOB-1516 deliberately made the x-axis **ticks windowed / scroll-dependent** (`boundedXAxisValues`) for performance — rendering ~dozens of `AxisMarks` per window instead of ~1000 across the whole span was the actual scroll-hang fix. Only the **domain** (`fullXDomain`) stays scroll-independent, and `buildWeight` uses it for `ChartModel.xDomain`. So we cover the surviving invariant (domain is scroll-independent; the bounded window stays clamped inside it) and treat the "full tick set" clause as retired. → **flag on the ticket.**

---

## Production refactor (required by AC #2 + #3)

**Extract the selection-snap logic out of the `TrendChartHost` View into a pure, testable type.**

- New file: `Chart/Engine/TrendChartSelectionSnapper.swift` — `enum TrendChartSelectionSnapper` with `static` funcs:
  - `snappedDate(for raw: Date, in model: ChartModel, primarySeriesName: String, calendar: Calendar) -> Date?`
  - `nearestEntry(to date: Date, in model: ChartModel, primarySeriesName: String) -> PlottedGraphSeries?`
  - plus the moved private helpers `monthLineCandidates` / `nearestMonthStart` / `monthStart`.
- `TrendChartHost` keeps a **thin private wrapper** `snappedSelectionDate(for:in:)` that delegates to the snapper (passing `primarySeriesName` + `.current`). The only external call site (`TrendChartHost.swift:278`) is unchanged, so the refactor is behaviour- and compile-neutral. `monthLineCandidates` / `nearestMonthStart` / `monthStart` / `nearestEntry` are **moved** (used only by the snap logic).

This is the one production change; everything else is test-only.

---

## Test files to write

Ordered by ROI / risk. Tier 1 = pure, zero-harness, guaranteed. Tier 2 = store-level (needs the data-load harness).

### File A — `ChartDecimatorTests.swift` (NEW · Tier 1) — AC #4
Direct tests of `ChartDecimator.decimate(_:threshold:target:)` (build `PlottedGraphSeries` inline; override `threshold`/`target` to force decimation on small arrays).
- below `threshold` → returns the input **unchanged** (identity)
- above `threshold` → count reduced toward `target` (bounded, `< input.count`)
- **first & last points always preserved** (endpoints)
- per-bucket **min *and* max both retained** — a single spike and a single dip both survive (shape preservation)
- output stays **monotonically ascending in `xDate`** (extremes emitted in index order)
- `target < 4` guard → returns input unchanged

### File B — `GraphDomainScrollIndependenceTests.swift` (NEW · Tier 1) — AC #4
- `fullXDomain(for:from:)` returns an **identical** range across repeated calls (pure, no scroll input)
- `ChartPrep.buildWeight` produces the **same `model.xDomain`** for several different `scrollPosition` values (domain is scroll-independent)
- `boundedXDomain(around:)` stays **within `fullXDomain`** for scroll positions across the span (window never exceeds the full domain)
- empty operations → `fullXDomain` falls back to the period's empty-state domain (ordered, non-degenerate)

### File C — `TrendChartSelectionSnapperTests.swift` (NEW · Tier 1) — AC #2 + #3
Build a `ChartModel` via `ChartPrep.buildWeight`, then assert `TrendChartSelectionSnapper`:
- **week** → snaps a mid-day tap to the nearest **day** (midnight), clamped to the data's day range
- **month** → snaps to a **shown line** (Sunday / month-1st) or a real entry day, clamped in range
- **year** → snaps to the nearest **1st-of-month**, clamped to the data's month range
- **total** → snaps to the nearest **real entry** date (`nearestEntry`)
- taps **before the first / after the last** reading clamp to the first / last (never past the data)
- `nearestEntry`: exact hit returns that entry; equidistant tap resolves deterministically; empty series → `nil`

### File D — `DashboardStoreChartEngineTests.swift` (NEW · Tier 2) — AC #1 + #2
`DashboardStore(lightweight: true)` (+ `DashboardManagerTestSupport.makeStore(daily:monthly:)` to seed `continuousOperations`):
- `selectPoint(at: nil)` → clears the graph selection (`state.graph.selectedPoint == nil`)
- `selectPoint(at: entryDate)` → sets `selectedPoint` to that entry + refreshes metric tiles
- `settleChart` weight-only (no co-plotted metric) → `seriesPoints` / `xDomain` / `visibleDomainLength` / `dataFingerprint` **byte-identical** to the pre-settle model; only `yAxis` + `xAxisTicks` may change
- `settleChart` with a co-plotted metric (`state.ui.selectedMetricLabel = bodyFat`) → falls back to a **full rebuild** (second series appears)
- `rebuildChartModel` dispatch: `.baby` with no selected profile → `chartModel == nil` (cheap dispatch assertion without heavy baby fixtures)

---

## Out of scope / intentionally not covered
- **UI-layer views** (`TrendChartHost` / `TrendChartView` rendering, gestures, animation) — coverage-excluded per project rule.
- **Already covered, not duplicated:** `buildWeight` shape/determinism/empty/single-point, co-plotted normalization, `weightYAxis`, `plotXDate`, `withYAxisAndTicks` parity (all in `ChartPrepTests`); `fullXDomain` insets + `xAxisValues` per-period (in `GraphRenderingConfigurationTests`); BPM/baby builders (in `ChartPrepBpmBabyTests`).

## Verification
- Compile: `xcodebuild build-for-testing -scheme meAppTests -configuration Dev -destination generic/platform=iOS` (local Xcode type-checks; CI is Xcode 16.4).
- Run on a **connected physical device** (hard project rule — never simulator); then a coverage pass to confirm ≥ 80% on the touched store/engine seams.

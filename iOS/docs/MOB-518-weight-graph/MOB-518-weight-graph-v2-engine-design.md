# MOB-518 — Weight-graph v2 engine (greenfield, strangler rebuild)

> **What this doc is.** The design for a **brand-new weight-chart engine built from scratch**, living
> **alongside** the current graph, that we flip weight over to **only once it reaches parity on device**.
> It supersedes the *delivery mechanism* of the in-place migration in
> [`MOB-518-weight-graph-implementation-guide.md`](MOB-518-weight-graph-implementation-guide.md) (Phases 2–5)
> — we no longer morph `BaseGraphView` step by step; we replace it. The *diagnosis* and *target architecture*
> in [`MOB-518-chart-engine-rearchitecture.md`](MOB-518-chart-engine-rearchitecture.md) still hold — this is
> that target architecture delivered as new code instead of an incremental edit.
>
> **Decision on record (2026-07-09, Kesavan):** after Phase 1 (the `.id` removal) shipped and device testing
> still showed "empty space then data pops in," missing points, and other hard-to-describe glitches, Kesavan
> chose **"new view, reuse the domain math"** over continuing to patch the existing tangle.

**Scope:** iOS only · weight (`EntryType.scale`) only · **Base:** `develop` ·
**Branch:** `MOB-518-chart-engine-scroll-hitch-multi-series` (still ONE PR) · **Build:** Dev config only.

---

## 0. Why greenfield (and why NOT a blank slate)

The existing weight chart is not slow because the *math* is wrong — it's slow because the **plumbing** is
tangled: a `@MainActor` view + view-model + three managers + the store all mutate chart state, feeding **six
overlapping caches**, **two windowing passes** (store-committed vs view-live), and **three racing scroll-end
settle paths**. Phase 1 removed the worst symptom (the `.id`-driven full teardown), but the residual glitches
("blank then render," missing points, loose window) are rooted in that prep/windowing pipeline — exactly the
part Phases 2–3 would have had to *reshape in place*, fighting the tangle the whole way.

Building the pipeline fresh is faster to a *correct* result than un-tangling it. **But "from scratch" means a
new view + data-flow only — we REUSE the hard-won domain math, we do not rewrite it.** The domain layer is
already substantially pure and correct (see the reuse map, §3). Rewriting it would reintroduce months of
solved edge-case behaviour (4 periods, metric switching, adaptive y-axis, goal chips, crosshair snapping,
empty/single-value weeks, unit conversion, HealthKit).

**Non-negotiables that make this safe:**
- The **old graph stays live and untouched** for the whole build. Baby/BPM keep using it. Weight keeps using
  it until the new engine is signed off on device.
- We flip weight to the new engine behind a **single `productType == .scale` branch** in the host — one line
  to switch, one line to roll back.
- **Behaviour parity is the bar.** Same adaptive y-axis, same averages on lift, same crosshair, same
  week/month/year/total, same scroll-to-latest / period anchor. We remove wasted work, not features.

---

## 1. Target architecture (one direction, no rebuild, no re-window)

```
        (off main, once per data / period / unit / metric / scroll-settle change)
[BathScaleWeightSummary] ──► extract Sendable input (on main) ──► ChartPrep (off main) ──► ChartModel
                                                                                              │
                                                                        @Published on DashboardStore (value type)
                                                                                              ▼
                                                                       ┌────────────────────────────────┐
                                                                       │  WeightChartView (stable Chart) │
                                                                       │  • no .id churn                 │
                                                                       │  • Swift Charts owns the scroll │
                                                                       │  • reads ChartModel, derives 0  │
                                                                       └────────────────────────────────┘
                                                                          ▲                    ▲
                                       local @State scrollX  ─────────────┘                    └──── local @State selection
                                     (native; synced to store at boundaries only)             (guarded by isScrolling)
```

Principles (unchanged from the rearchitecture doc §C):
1. **Immutable model in, stable view out.** The view *reads* a prepared `ChartModel`; it never *derives* plot
   data, sorts, groups, or windows.
2. **Prep is off-main and event-driven.** Rebuilt only when inputs actually change; **never during scroll**.
3. **Swift Charts owns the scroll** over one bounded, decimated, full-domain series. No live re-windowing.
4. **One settle event, one animation** (adaptive y-axis, Y-B — same behaviour as today).
5. **Selection/scroll are local `@State`**, synced to the store only at boundaries.

---

## 2. New types & files (all new; none replace domain logic)

| New file | Role |
|----------|------|
| `Features/Dashboard/Models/ChartModel.swift` | Immutable `Sendable` value type — everything the Chart needs (see §2.1). Plus `YAxisModel`. |
| `Features/Dashboard/Managers/Graph/ChartPrep.swift` | Pure/off-main builder: Sendable input snapshot → `ChartModel`. Calls the reused domain pieces (§3). |
| `Features/Dashboard/Managers/Graph/ChartDecimator.swift` | Shape-preserving downsample (min/max bucket or LTTB) for long `total` ranges. No-op for small series. |
| `Features/Dashboard/Views/Components/WeightChartView.swift` | The new stable-`Chart` renderer. Reads `ChartModel`; local `@State` scroll + selection. |
| `Features/Dashboard/Views/Components/WeightChartHost.swift` | Period-aware host inserted at the `productType == .scale` branch; owns the store-published model + boundary sync. |

### 2.1 `ChartModel` (draft — finalized against the pipeline map)

```swift
struct YAxisModel: Equatable, Sendable {
    let domain: ClosedRange<Double>     // routed through ChartDomainSanitizer.finiteWidth before use
    let ticks: [Double]
}

struct ChartModel: Equatable, Sendable {
    let period: TimePeriod
    let productType: EntryType                         // .scale for now; .bpm/.baby later
    let orderedSeriesNames: [String]
    let seriesPoints: [String: [PlottedGraphSeries]]   // pre-sorted asc by xDate, plotXDate baked in, decimated
    let fullResolution: [String: [PlottedGraphSeries]] // undecimated — crosshair snapping / selection
    let xDomain: ClosedRange<Date>                     // full scrollable domain
    let visibleDomainLength: TimeInterval              // from GraphRenderingConfiguration
    let xAxisTicks: [Date]                             // from GraphRenderingConfiguration
    let yAxis: YAxisModel
    let dataFingerprint: Int                           // count + endpoints; EXCLUDES yAxis (kills S1 by construction)
}
```

`GraphSeries`/`PlottedGraphSeries` are already value structs, so `ChartModel` is `Sendable` for free.
`dataFingerprint` deliberately **excludes** the y-axis so a y-settle updates `yAxis` in place without changing
series identity — the structural fix that made Phase 1 necessary is now guaranteed by the type.

---

## 3. Reuse map — KEEP vs REPLACE

**KEEP / REUSE (call from `ChartPrep`; do not rewrite):**

| Piece | Why it's safe to reuse |
|-------|------------------------|
| `GraphDataPreparer.buildWeightSeries` (+ siblings) | Builds `[GraphSeries]` from summaries; weight value independent of y-domain. |
| `GraphRenderingConfiguration` | **Pure, stateless** — x-axis ticks, `visibleDomainLength`, scroll/snap/clamp math, date/range formatting. Perfect for off-main. |
| Y-axis calculator (adaptive domain + ticks; `updateYAxisCache` math) | The adaptive bracket logic is correct — Y-B keeps it. Fed pure inputs. |
| `PercentileChartWindowing` / `SortedArrayIndex` (committed `813f0f98a`) | Baby/BPM percentile path, already binary-searched + tested. Untouched now; reused when multi-series lands. |
| `ChartDomainSanitizer` (W2, committed) | Guards every domain handed to Swift Charts. |
| `TimePeriod`, `GraphSeries`, `PlottedGraphSeries`, `EntryType` | Value types / enums. |

**REPLACE (do not carry forward):**

| Replaced | By |
|----------|-----|
| `BaseGraphView` (weight path only) | `WeightChartView` + `WeightChartHost` |
| The 6 caches (VM `cachedSeriesData`/`cachedPlotXDates`/…, view `cachedPlottedPoints`/`cachedAllPlottedPoints`, manager `cachedChartSeries`) | one immutable `ChartModel` |
| Two windowing passes (store buffer + view `pointsToRender` 30/30) | one prep-time full-domain decimation + native scroll |
| Three scroll-end settle paths (`handleScrollEndOptimized` cascade + `handleScrollPhaseChange` + VM `handleScrollEnd`) | one settle on `isScrolling → false` |
| Section-VM chart caches | VM (if kept at all for weight) shrinks to UI state; likely replaced by local `@State` in `WeightChartView` |

**CARRY-OVER learnings already on the branch:** Phase 1 (`no .id`), W1 (de-`@Published` scrollPosition), W2
(`ChartDomainSanitizer`). The new engine bakes all three in from line one.

---

## 4. Integration seam (one branch; old path untouched)

In [`GraphView.chartView`](../../meApp/Features/Dashboard/Views/Components/GraphView.swift#L212) (the `else`
branch of the baby-empty check), branch on product type:

```swift
if dashboardStore.productType == .scale {
    WeightChartHost(dashboardStore: dashboardStore,          // NEW engine — weight only
                    week: weekSectionViewModel, month: monthSectionViewModel,
                    year: yearSectionViewModel, total: totalSectionViewModel)
} else {
    // EXISTING per-period switch (baby/BPM) — untouched
    switch dashboardStore.state.graph.selectedPeriod { case .week: WeekGraphView(...) … }
}
```

`WeightChartHost` reuses the four existing `@StateObject` section VMs for period identity + boundary state, so
the period switch, teardown, and initial-select wiring in `GraphView` keep working unchanged. Rolling back is
deleting the `if` branch.

> **MULTI-SERIES:** when weight is signed off, baby/BPM move to the same engine by dropping the `else` branch
> and having `ChartPrep` build their series (percentile via the existing `PercentileChartWindowing`, BPM as
> 3 series + reference lines). Until then, leave the `else` path exactly as-is.

---

## 5. Build order (commits within the one PR; verify each on device)

| Step | What | Shippable behind the flag? | Device check |
|------|------|:--:|--------------|
| **V1 ✅ DONE (2026-07-09)** | `ChartModel` + `ChartPrep` + `ChartDecimator`, pure. No UI yet; unreferenced → zero risk to the live app. **Built on the MAIN actor, once per change** (not off-main): the win is "once per change, not per frame" + no `.id` teardown; aggregated summaries are small, so off-main is a later isolated optimization. Reuses `GraphDataPreparer` / `YAxisCalculator` / `GraphRenderingConfiguration` verbatim. | n/a (compile-verified) | build green; parity of the built model is checked when the view lands in V2. |
| **V2 ✅ DONE (2026-07-09)** | `WeightChartView` (stable Chart, no `.id`, native scroll, adaptive y-axis, S6 clamp) + `WeightChartHost` (local `@State` scroll, rebuilds the model only on data/period/settle via `DashboardStore.makeWeightChartModel`). Wired behind a **DEBUG A/B toggle** ("MOB-518 · new weight engine") in `GraphView` at `productType == .scale`. All four periods (host is period-aware); model built on-demand, not `@Published` yet. Read-only (no crosshair/average/goal — V4). | ✅ (build green) | flip the toggle on a weight account: no blank-then-pop, points+line together, finger 1:1, y-axis adapts once on lift; flip off to compare. |
| **V3** | X-axis label/tick parity per period + tune `.chartXVisibleDomain`/scroll extent for month/year/total; confirm long `total` decimation stays full-resolution + smooth. | ✅ | all four periods match the old x-axis; long `total` smooth. |
| **V4** | Selection/crosshair + goal chip/line + adaptive single-event y-axis settle (Y-B). | ✅ | crosshair callout, average-on-lift, goal overlays, one clean settle — all at parity. |
| **V5** | Initial-select / scroll-to-latest / period-switch anchor / tab-back parity via the existing store paths. | ✅ | cold open, tab-back, period switch land on latest window + latest point selected. |
| **V6** | Flip weight fully to the new engine; delete the now-dead weight-only code in `BaseGraphView` and the weight caches/settle paths. **Baby/BPM stay on the old engine.** | ✅ | full regression pass; Animation Hitches trace < ~5 ms/s, no frame > 16.7 ms on a large weight account. |
| **Phase T** | Tests — only after Kesavan signs off the weight graph. Golden model parity per period, decimation-preserves-shape + crosshair-selectable, settle-once, prep 0× during scroll. Remove all `#if DEBUG` probes. | n/a | — |

Standing constraints (unchanged): **no committed tests until Phase T**; **do not touch baby/BPM behaviour**
(carry `// MULTI-SERIES:` notes); **behaviour parity**.

---

## 6. Parity gate (device — Kesavan runs it)

Weight is "done" when, on a large weight account across week/month/year/total:
- No empty-space-then-data; no points-before-line; no rebuild flash.
- Finger tracks 1:1; no coarse/sparse line mid-scroll; long `total` stays full-resolution.
- Crosshair snaps to a real entry; window average settles on finger-lift; y-axis adapts to the visible window
  in **one** smooth transition (same as today).
- Cold open / tab-back / period switch land on the latest window with the latest point selected.
- Instruments (Profile build, physical device): steady-state scroll < ~5 ms/s, no frame > 16.7 ms.

---

## 7. Open items (fill from the pipeline map / decide inline)

- [x] **RESOLVED — `buildWeightSeries` takes `convertWeight: (Double) -> Double`** so unit + weightless
      handling is reused as-is. V1 builds on main, so no actor boundary to cross yet; when we move off-main,
      pre-convert weights and extract `BathScaleWeightSummary` primitives into a Sendable struct first.
- [x] **RESOLVED — y-axis entry point: `YAxisCalculator.calculateYAxis(...)`** fed the visible-window ops
      (`GraphDataPreparer.strictlyVisibleOperations`, bracket fallback), matching today's adaptive Y-B.
- [x] **RESOLVED — decimation 800/600** (`ChartDecimator.decimate` min/max bucket); no-op for the usual
      few-hundred-point weight series, engages only on long `total`. Undecimated kept in `fullResolution`.
- [ ] Does `WeightChartView` keep the section VM for scroll/selection, or fully own them as local `@State`
      (preferred)? Decide at V2 based on what the store boundary sync needs.

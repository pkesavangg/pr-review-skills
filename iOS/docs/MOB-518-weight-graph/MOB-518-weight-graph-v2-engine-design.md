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
- [→] **MOVED — "does the view own scroll/selection or keep the section VM?" is now the explicit decision
      V-A4 in §9.** It must be decided before A1–A3 (see §8).

---

## 8. Architectural backlog (current v2 state — 2026-07-09)

V1/V2 deliberately cut corners to get an A/B preview on screen fast: the new view is currently a
**self-contained island** driven by view-side heuristics, not wired to the store. These are the high-level
issues to fix **before** any rendering-parity work (vertical gridlines, x-axis labels, point hiding — those
are V3 cosmetics). Highest priority first.

| ID | Architectural issue | Pri | Fix direction |
|----|---------------------|-----|---------------|
| **V-A1** | New view is disconnected from the store's scroll/selection/lifecycle. `WeightChartHost` keeps a purely local `scrollX`; never writes `state.graph.xScrollPosition`, never sets `isScrolling`, never clears selection, never triggers average/metrics. → wrong header average, no crosshair, no goal chip, no period anchor. | **P0** | Boundary contract (§C6/C7): view reports gestures → store commits / settles / selects. View becomes a pure renderer + reporter. |
| **V-A2** | Rebuild cadence = a hand-rolled `dataSettingsKey` hash + a `Task.sleep(150ms)` settle **in the view**. The hash can miss real data changes (→ stale graph); the timer is a **new instance of the S4 timer-settle anti-pattern**, not the real `isScrolling → false` event. | **P0** | The **store** rebuilds from its real change signals (`dataChangeSignature`/`settingsChangeSignature`) + the real scroll-end event. |
| **V-A3** | No single source of truth: `makeWeightChartModel(...)` is a method the *view* calls into local `@State`, not `@Published` on the store. → the store's own state (VM caches, `cachedYAxisDomain`, average) and the new model are two parallel truths that diverge (the 32.4-vs-61 header). | **P0** | `@Published private(set) var chartModel` on `DashboardStore`; one builder; view observes. |
| **V-A4** | Two owners of period/scroll state — the section VMs **and** the host's local `@State` — unreconciled. | **P1 (decide FIRST)** | **See the decision in §9.** A1–A3 hinge on it. |
| **V-A5** | Scroll geometry (x-domain, visible window, initial position, snapping) is ad-hoc in the new view (`xDomain = data.min…max`, Total's visible length hacked), not sourced from `GraphRenderingConfiguration` (`axisRange` + buffers + `optimalScrollPosition` + `snapScrollPosition`). → extent / start-at-latest / month-year snap / x-tick alignment not at parity. | **P1** | The model carries the real scroll geometry, not the simplified range. |
| **V-A6** | A settle rebuilds the **whole** model (series + decimation + y-axis) when only the y-axis window changed — the `dataFingerprint`-vs-`yAxis` split exists precisely to update the axis in place. | **P2** | Split "data rebuild" from "y-axis resettle," keyed off the respective signals. |

> **Already solved in v2 (do not re-litigate):** S1 (no `.id`), S2/S10 (single decimation + native scroll),
> S6 (line/point plot the same value). The remaining architecture is all **store integration + lifecycle**
> = A1–A4. Off-main `ChartPrep` (S3) folds into V-A3 when the store owns the model; not urgent (small data).
>
> **Through-line:** *the store owns a published `ChartModel` and the scroll/selection lifecycle; the view is
> a dumb renderer that reports gestures back through the store's boundary contract.* Crosshair, average, goal,
> anchor, and even the gridlines all fall out once that's true.

---

## 9. Decision needed — V-A4: who owns scroll + selection?  (pick one below)

Today the four `Week/Month/Year/Total` section VMs own scroll/selection/`isScrolling` **and** drive the
store's scroll-end commit, average recompute, y-axis resettle, selection validation, and period anchor.
`WeightChartHost` currently ignores all of it and keeps its own local scroll. This must be resolved before
A1–A3, because it decides *where* the boundary wiring lives.

**Option A — View owns scroll + selection (local `@State`); store owns lifecycle + published model.**
The new view holds `@State scrollX` + `@State selection`; reports to the **store** at boundaries only
(start → `isScrolling` + clear selection; end → commit + settle; tap → store validated selection). The four
section VMs are **not used for weight** (kept for baby/BPM until they migrate).
- Pros: the design's stated direction (§C6/C7); pure-renderer view; sheds the `scrollAdoptToken`/binding
  dance; single truth via the store; **A1–A3 fall out cleanly.**
- Cons: wire the host's boundary events to existing store/manager entry points (re-pointing, not rewriting —
  the lifecycle logic already lives in the managers); must confirm header average/goal still driven at settle.
- Effort: medium · Regression risk: medium.

**Option B — Section VM keeps scroll + selection; the new view is a renderer bound to it.**
`WeightChartView` binds scroll/selection to the active `BaseSectionViewModel` (like the old `BaseGraphView`),
reusing all existing scroll-end/average/selection/anchor wiring.
- Pros: reuse everything that works → fastest to crosshair/average/goal parity, lowest *behaviour* regression.
- Cons: re-couples to the VM the rebuild set out to shed; drags the plain-var-scroll + `scrollAdoptToken`
  re-render dance and `@ObservedObject` publish invalidations into the new view; still needs the store to
  publish `ChartModel` (A3) anyway → half-in/half-out, carrying old complexity forward.
- Effort: low–medium · Architecture-debt risk: high.

> **Recommendation (Claude): Option A** — the only option that actually removes the coupling; the "extra
> wiring" is just calling existing store/manager methods from the host's boundary events. B is faster to a
> demo but re-imports the tangle we're escaping.

**Pick one (edit the boxes):**

- [x] **Option A** — view owns scroll/selection (local `@State`); store owns lifecycle + published model *(Recommended)* — **CHOSEN (Kesavan, 2026-07-09).**
- [ ] **Option B** — section VM keeps scroll/selection; new view is a renderer bound to the VM

**Sequence (one at a time, device-verify each):**
- **A3 — store owns the published model. ✅ DONE (2026-07-09).** `DashboardStore.chartModel` is now
  `@Published private(set)`, built by `rebuildWeightChartModel(scrollPosition:)`; `WeightChartHost` observes it
  instead of holding a local `@State` copy. (Triggers are still the view's for now — that's A2.)
- **A1 — scroll-boundary wiring. ✅ DONE (2026-07-09).** `WeightChartHost` now feeds the gesture into the
  store's existing lifecycle: scroll-start → `chartManager.handleScrollStart()` (`isScrolling` + clear
  selection); during → `handleScrollPositionChange` (buffers); end (150 ms) → `handleScrollEndOptimized()`
  (commit + header average + y-axis) + rebuild the model at the landed window. An `isAdopting` guard keeps
  programmatic moves (init/period) from tripping the gesture path. **Scope:** scroll boundary only —
  **tap-selection/crosshair rides with V4** (there's no crosshair binding yet to route). *Known A1 limit:*
  month doesn't visually snap the scroll to the window yet (V-A5); the header commit lags by the store's
  scroll-end debounce (collapsed in Phase 4).
- **A2 — rebuild on the store's real signals. ✅ DONE (2026-07-09).** Killed the view's 150 ms `Task.sleep`
  settle + `dataSettingsKey` endpoint hash. Scroll start/commit/end now come from the **native
  `.onScrollPhaseChange`** routed to `chartManager.handleScrollPhaseChange(to:)` — the same real signal the
  legacy graph uses via `ScrollDetectionModifier` — which commits the landed window (month-snapped) into
  `state.graph.xScrollPosition` and flips `isScrolling`, with **no `handleScrollEndOptimized` /
  `isProcessingScrollEnd` cascade** (that was the source of #3). The host rebuilds the model once when
  `isScrolling → false`, and on data/settings change via the store's canonical
  `dataChangeSignature`+`settingsChangeSignature`(+goal) — `dataChangeRevision` bumps on every real mutation,
  so it can't go stale like the endpoint hash could. *Known A2 limit:* month still doesn't **visually** snap
  the scroll (V-A5); the chartManager's `.idle` 50 ms settle (updateYAxisCache/updateWeightDisplay/metrics)
  still runs but the new engine doesn't read it — trimmed in V-A4/Phase 4.
- **Phase 4 — single-event settle (in-place y-axis). ✅ DONE (2026-07-09).** *Root-cause correction:* the
  ~1 s scroll-lock (#3) was **not** the `handleScrollEndOptimized` cascade (A2 removed that with no effect) —
  it was the scroll-END **full model rebuild** re-emitting scroll-dependent x-geometry (`visibleDomainLength`
  is per-month for `.month`; `xAxisTicks` is windowed for large spans), which forced Swift Charts to rebuild
  its scroll view on every stop. Fix: split the settle from the rebuild. `ChartPrep.weightYAxis(...)` now
  computes just the adaptive y-axis; `ChartModel.withYAxis(_:)` returns a copy with ONLY `yAxis` swapped
  (series + `xDomain`/`visibleDomainLength`/`xAxisTicks`/`dataFingerprint` byte-identical); the store's
  `resettleWeightYAxis(scrollPosition:)` calls it and `WeightChartHost` invokes that on `isScrolling → false`
  instead of a full rebuild. Result: the scroll region never re-lays-out on settle — only `.chartYScale`
  animates (one clean Y-B transition). This is V-A6 delivered. *Still deferred:* the chartManager's `.idle`
  50 ms legacy settle (`updateYAxisCache`/`updateWeightDisplay`/metrics) still fires but the new engine
  ignores it — dropped in **V-A4** (route the new host's phase straight to `graphManager`).
- **V-A4 — drop legacy machinery for weight. ✅ DONE (2026-07-09).** Two cuts: (1) `GraphView`'s
  period-switch handler now `guard !usesNewWeightEngine`s out **before** any section-VM work (clear ×4 /
  tearDown / configure / forceScrollPositionUpdate / updateYAxisCache) — the host owns period handling, so
  that was pure waste on every switch (#2). (2) `WeightChartHost` routes its scroll phase + position straight
  to `graphManager` (not `chartManager`), dropping the chartManager `.idle` 50 ms legacy settle
  (`updateYAxisCache`/`updateWeightDisplay`/metrics) the new engine ignores. Safe because the legacy graph's
  `BaseGraphView.handleOnAppear` reconfigures its VM on mount, so toggling the A/B back to legacy re-mounts +
  reconfigures. Single owner now: store publishes model + owns scroll lifecycle; host renders + reports.
- **Next: V-A5** (real scroll geometry — visual month snap + start-at-latest + x-domain/extent), then **V3**
  rendering parity.

---

## 10. Remaining roadmap — the single ordered list (2026-07-09)

> The docs carry three overlapping numbering schemes (V1–V6 build order, V-A1–V-A6 architecture backlog,
> Phase 0–T from the old in-place guide). **This section is the one canonical execution order for what's
> left** — every remaining item, in the order we do it, with the device issue(s)/feature(s) each closes.
> When they disagree, this list wins.

**✅ Done:** Phase 0 (dead code) · Phase 1 (`.id` removal, old engine) · V1 (`ChartModel` + `ChartPrep` +
`ChartDecimator`) · V2 (`WeightChartView` + `WeightChartHost` + DEBUG A/B toggle) · **A3** (store-published
model) · **A1** (scroll-boundary wiring) · **A2** (real rebuild signals — native scroll-phase + canonical
data/settings signals; view timer + hash deleted) · **Phase 4 / V-A6** (in-place y-axis settle — scroll-end
resettles only `yAxis`, x-geometry frozen, no scroll-view rebuild) · **V-A4** (drop legacy machinery for
weight — period-switch guard + scroll phase → `graphManager`). All uncommitted (holding for Kesavan's commit
command).

**Remaining — in order:**

| # | Step | What it does | Closes |
|---|------|--------------|--------|
| 1 | ~~**A2 — real rebuild signals**~~ ✅ **DONE** | Store rebuilds from its own change/scroll signals; view's 150 ms timer + `dataSettingsKey` hash deleted; native `.onScrollPhaseChange` drives start/commit/end (no `handleScrollEndOptimized` cascade). | V-A2 |
| 2 | ~~**Phase 4 — single-event settle (in-place y-axis)**~~ ✅ **DONE** | Scroll-end resettles ONLY `yAxis` (`resettleWeightYAxis` → `ChartModel.withYAxis`); series + x-geometry byte-identical → Swift Charts never rebuilds its scroll view on settle. Real cause of #3 (rebuild re-emitting scroll-dependent x-geometry). | **#3** (verify on device) |
| 3 | ~~**V-A4 — drop legacy machinery for weight**~~ ✅ **DONE** | GraphView period-switch `guard !usesNewWeightEngine`; host scroll phase → `graphManager` (no chartManager `.idle` settle). Single owner. | **#2** (verify on device) |
| 4 | **V-A5 — real scroll geometry** *(next)* | Snap-to-window (week/month/year), correct "start at latest" position, x-domain/extent from `GraphRenderingConfiguration` (not `data.min…max`). | month **visual** snap, initial position |
| 5 | **V4 — selection + header + goal + weightless + metrics** (sub-steps 5a–5f) | 5a tap-selection + crosshair · 5b header value + label + average-on-lift · 5c goal chip + line · 5d weightless label · 5e metric co-plot + switching · 5f active-month greying. | **#4** selection |
| 6 | **V3 — rendering parity (cosmetics)** | Vertical gridlines (solid at boundaries / dashed between), x-axis labels + ticks per period, selected-point sizes. | **#1** vertical lines |
| 7 | **V6 — flip + delete old weight path** | Make the new engine the default (retire the DEBUG toggle); delete the now-dead weight-only `BaseGraphView` code + caches + cascade. **Baby/BPM stay on the old engine.** | — |
| 8 | **Sweep + verify** | Walk the [known-issues log](MOB-518-weight-graph-known-issues.md) (all closed?), run the full [feature-spec parity gate](MOB-518-weight-graph-feature-spec.md) on device + Instruments Animation Hitches (< 5 ms/s, no frame > 16.7 ms) on a large account. | all |
| 9 | **(Optional) off-main `ChartPrep`** | Only if step 8's trace shows a main-thread hit at settle — extract Sendable snapshot + hop off-main (S3). Likely unnecessary (data is small). | perf tail |
| 10 | **Phase T — tests** | After sign-off: golden model parity per period, decimation-preserves-shape, settle-once, prep runs 0× during scroll. Remove `#if DEBUG` probes. | — |
| 11 | **Commit + raise PR** | Fold the held working-tree changes into the phased commits; single PR to `develop`. | — |

**Shorthand:** 1–4 = architecture + perf finish (closes #2/#3) · 5 = behaviour parity (closes #4) ·
6 = visual parity (closes #1) · 7 makes it the real graph · 8–11 verify, test, ship.

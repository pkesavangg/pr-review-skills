# MOB-518 — Chart engine re-architecture (weight graph first, multi-series ready)

> **What this doc is.** The **deep, engine-rebuild plan** for the dashboard chart, scoped from the
> weight graph outward. It is the successor design to the incremental W1/W2 work already on
> `MOB-518-chart-engine-scroll-hitch-multi-series`. Where `MOB-518-weight-graph-focus.md` fixed the
> two runtime *warnings* (the `@Published scrollPosition` storm + the invalid-frame flood), **this doc
> targets the structural reasons the weight chart still hitches, renders points-before-lines, and
> jerks at scroll-end** — and lays out the clean architecture + phased migration to fix them for good,
> including making the 5.1.0 BPM (2-series) and baby percentile (5–10-series) graphs land on a fast engine.
>
> **Decision on record (2026-07-08, Kesavan):** *full engine rebuild*, delivered as a **phased,
> parity-gated migration** — not a big-bang rewrite of a shipping graph.
>
> **Companions:** `MOB-518-weight-graph-focus.md` (W1/W2 root-cause + shipped fixes),
> `MOB-516-implementation-plan.md` (epic execution log), `performance-analysis-5.1.0.md` (Instruments
> evidence), `meApp/Features/Dashboard/GraphViewFlow.md` (current architecture reference).

**Scope:** iOS only · **Base:** `develop` · **Build:** Dev config only. All file:line refs verified against
the working tree on **2026-07-08** (post W1/W2).

---



## 0. Executive summary

**Diagnosis in one paragraph.** The chart is **frame/settle-driven where the spec is event-driven**, and
it expresses "the data settled" by *throwing away and rebuilding the whole SwiftUI* `Chart`. The single
load-bearing mistake is `.id(lastDataHash)` **on the** `Chart` (`[BaseGraphView.swift:556](../../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L556)`),
whose hash **includes the y-axis domain + ticks**. Every scroll-end re-settles the y-axis → the id
changes → SwiftUI destroys and re-mounts the entire chart → it re-runs its mount animation (Swift Charts
pops `PointMark`s at final positions while the `.monotone` `LineMark` path *draws in* → "dots first, line
follows") and re-lays-out every mark on the main actor (→ the hitch/jerk). Layered on top: **two competing
windowing passes** that both go stale during a native drag, a **timer-cascade y-axis settle** with three
overlapping recompute paths, **competing animations**, and **six+ caches** with divergent invalidation.

**Important scale correction.** The chart plots **aggregated daily/monthly summaries**
(`continuousOperations: [BathScaleWeightSummary]`), **not** the raw 10k `Entry` rows. Point count is bounded
by *distinct days/months* (hundreds–low thousands), and the raw-10k pressure is the *data-load* story
(MOB-1433 / Task 1), not the chart. This means the rebuild cost is very tractable once we stop rebuilding
the world — we do **not** need to render 10k marks.

**Target in one paragraph.** One **stable** `Chart` (no identity churn) that renders from a single,
**immutable, pre-sorted, plot-ready** `ChartModel` built **once per data/period/unit/metric change, off the
main actor**, and never during scroll. Let **Swift Charts own the native scroll** over a bounded,
scroll-cooperative decimation of that model — no per-frame or per-settle re-derivation of the window. Settle
the y-axis on **one** `isScrolling → false` event with **one** animation (or make it fixed-per-period and
remove the jerk entirely — see §C5, a product decision). Selection/crosshair stays local `@State`. The four
period charts and the three product types (weight / BPM / baby) all render through the same clean path.

> **Update (2026-07-10) — a second, independent scroll cost surfaced on device after the v2 flip.** Beyond the
> `.id`-teardown hitch above, the biggest post-flip scroll hang (Week/Month/Total; Year smooth) was the
> **x-axis tick COUNT** — a full-dataset x-domain feeds Swift Charts ~1000 `AxisMarks`, which it evaluates per
> value even off-screen. It was **not** the scroll-canvas width (that hypothesis was tested and ruled out).
> Fix: keep the full domain, hand Charts **windowed ticks** only. See the
> [known-issues deep dive](MOB-518-weight-graph-known-issues.md#scroll-hang-deep-dive-2026-07-09--2026-07-10)
> and [v2 design §10](MOB-518-weight-graph-v2-engine-design.md#10-remaining-roadmap--the-single-ordered-list-2026-07-09).

---



## Part A — How the current engine works



### A.1 The pipeline (data → marks)

```
continuousOperations: [BathScaleWeightSummary]        // aggregated daily/monthly summaries (NOT raw entries)
        │  (DashboardStore.chartSeriesData :755 — windows to a buffer around the COMMITTED scroll position)
        ▼
[GraphSeries]  buildWeightSeries (GraphDataPreparer :62)   // weight value = convertWeight(summary.weight)
        │  (BaseSectionViewModel.cachedSeriesData :798 — own hash)
        ▼
BaseGraphView.refreshCachedChartData :226
        │  makeCacheUpdate (BaseGraphViewCacheSupport :22): re-group + re-SORT (O(n log n)) + re-map
        │  → [PlottedGraphSeries] per series + a full-duplicate allPlottedPoints + dataHash
        ▼
ChartSeriesContent.body (BaseGraphChartContent :93-129)
        │  visiblePoints → pointsToRender (:70): SECOND windowing → visible + 30-left + 30-right sampled
        ▼
per point: ONE LineMark + ONE PointMark   ← rendered together, but see S6
```



### A.2 Two scroll positions (deliberately split — keep this idea)

- **VM** `scrollPosition` — plain `var` (post-W1), read live by the `.chartScrollPosition` binding
(`[BaseSectionViewModel.swift:30](../../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift#L30)`).
- **Store** `state.graph.xScrollPosition` — the *committed* position, written only at scroll-end
(`[DashboardGraphManager.swift:66-72](../../meApp/Features/Dashboard/Managers/DashboardGraphManager.swift#L66-L72)`).



### A.3 Y-axis is frozen during scroll, resettled on a timer cascade

`updateYAxisConfiguration` guards `!isScrolling` (`[BaseSectionViewModel.swift:363](../../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift#L363)`).
Resettle fires through **three overlapping paths**: `handleScrollPhaseChange(.idle)` (+50ms),
`handleScrollEndOptimized` (+100/200/200/200ms cascade, `[DashboardChartManager.swift:227-288](../../meApp/Features/Dashboard/Managers/DashboardChartManager.swift#L227-L264)`),
and the VM's own `handleScrollEnd` (+200ms). They race; the axis visibly lands late → the jerk.

### A.4 Host / lifecycle

`GraphView` (`[GraphView.swift](../../meApp/Features/Dashboard/Views/Components/GraphView.swift)`) owns four
`@StateObject` section VMs (`Week/Month/Year/Total`), renders only the active one, and tears down the
inactive ones on period switch. Each period wrapper is a thin `BaseGraphView` host.

---



## Part B — Root-cause structural inventory (why we rebuild)

Ordered by impact. IDs referenced by the migration in Part D.


| ID      | Structural issue                                                                                                                                                                                                                                                             | Evidence                                                                                                                                                                                                                                                         | Symptom it drives                    |
| ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------ |
| **S1**  | `.id(lastDataHash)` **ties** `Chart` **view identity to a hash that includes y-domain+ticks** → full teardown/rebuild on every y-settle                                                                                                                                      | `[BaseGraphView.swift:556](../../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L556)`; `cacheHash` `[BaseGraphViewCacheSupport.swift:190-206](../../meApp/Features/Dashboard/Managers/Graph/BaseGraphViewCacheSupport.swift#L190-L206)`                | ① points-then-lines, ③ stuck, ④ jerk |
| **S2**  | **Two windowing passes, both stale during a drag.** Store windows by *committed* position; view re-decimates by *stale live* position with a 30/30 buffer cap too small to scroll natively                                                                                   | store `[DashboardStore.swift:755-770](../../meApp/Features/Dashboard/Stores/DashboardStore.swift#L755-L770)`; view `pointsToRender` `[BaseGraphViewCacheSupport.swift:70-104](../../meApp/Features/Dashboard/Managers/Graph/BaseGraphViewCacheSupport.swift#L70-L104)` | ② coarse/wrong marks while scrolling |
| **S3**  | **Entire chart-prep pipeline runs on** `@MainActor` — group + O(n log n) sort + `plotXDate` map + full-duplicate `allPlottedPoints`, re-run on every hash change                                                                                                             | `makeCacheUpdate` `[BaseGraphViewCacheSupport.swift:33-58](../../meApp/Features/Dashboard/Managers/Graph/BaseGraphViewCacheSupport.swift#L33-L58)`                                                                                                                  | ③ main-thread spike at settle        |
| **S4**  | **Y-axis settle is timer/delay-driven with 3 overlapping recompute paths** racing on staggered sleeps                                                                                                                                                                        | `[DashboardChartManager.swift:227-288](../../meApp/Features/Dashboard/Managers/DashboardChartManager.swift#L227)`, `DashboardGraphManager.handleScrollEnd`*, `BaseSectionViewModel.handleScrollEnd` :468                                                            | ④ late, non-deterministic jerk       |
| **S5**  | **Competing animations** on the same properties (`.animation` modifier ×3 + `withAnimation` in `syncYAxisFromStore` + `.transaction{animation=nil}`)                                                                                                                         | `[BaseGraphView.swift:581-585](../../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L581-L585)`; `[BaseSectionViewModel.swift:751](../../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift#L751)`                                          | ④ non-deterministic motion           |
| **S6**  | **LineMark plots** `clampedValue`**, PointMark plots raw value** → dot/line diverge at the domain edge, worse when the frozen domain snaps                                                                                                                                   | `[BaseGraphChartContent.swift:99-118](../../meApp/Features/Dashboard/Views/Components/BaseGraphChartContent.swift#L99-L118)`                                                                                                                                        | ①/④ dot vs line mismatch             |
| **S7**  | **Six+ overlapping caches** with divergent invalidation (VM `cachedSeriesData`/`cachedChartSeriesData`/`cachedPlotXDates`/dead `cachedVisibleSeriesData`; view `cachedPlottedPoints`/`cachedAllPlottedPoints`; manager `cachedChartSeries`; `cacheManager`; store windowing) | across the files above                                                                                                                                                                                                                                           | desync bugs, maintenance trap        |
| **S8**  | **A11y descriptor sorts the full point set twice per request** (unbounded)                                                                                                                                                                                                   | `[BaseGraphView.swift:716,743](../../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L716)`                                                                                                                                                           | latent cost on large sets            |
| **S9**  | **Dead / vestigial code:** `chartRebuildToken` (incremented [:174], never read); `visibleChartSeriesData` (only unit tests read it — MOB-516 "Step 2b" open item); triple scroll-end path                                                                                    | `[BaseGraphView.swift:28,174](../../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L174)`; `[BaseSectionViewModel.swift:209-239](../../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift#L209)`                                            | confusion, wasted effort             |
| **S10** | **Post-W1 the render window can't track a live drag at all** — `scrollPosition` is a plain var read in `body`, refreshed to the chart only via `scrollAdoptToken` on *programmatic* moves                                                                                    | `[BaseGraphView.swift:44-51,80](../../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L44-L51)`                                                                                                                                                       | ② (the deep reason)                  |


> **The through-line:** S1 makes "settle" == "rebuild"; S2/S10 make "scroll" == "scroll a stale slice";
> S3/S4/S5 make the rebuild land late and heavy. Fix the *cadence* and *identity* and the symptoms go with them.

---



## Part C — Target architecture



### C1. Layers & data flow (one direction, no rebuild)

```
        (off main, once per data/period/unit/metric change)
[BathScaleWeightSummary] ──► ChartPrep worker ──► ChartModel (immutable, Sendable, pre-sorted, plot-ready)
                                                        │
                                                        ▼  @Published on the store (value type; cheap to diff)
                                            ┌───────────────────────────┐
                                            │   Stable Chart (no .id)   │  ← renders straight from ChartModel
                                            │   Swift Charts owns scroll│
                                            └───────────────────────────┘
                                                   ▲            ▲
                          local @State scroll  ────┘            └──── local @State selection/crosshair
                        (native; boundary-synced,               (guarded by isScrolling; already close)
                         never per-frame @Published)
```

Principles:

1. **Immutable model in, stable view.** The view never *derives* plot data; it *reads* a prepared model.
2. **Prep is off-main and event-driven.** Rebuilt only when inputs actually change; never on scroll.
3. **Charts owns the scroll.** We hand it a bounded, scroll-cooperative set once and stop re-windowing.
4. **One settle event, one animation.** No timer cascades, no competing `.animation`s.



### C2. The `ChartModel` (new value type)

A single `Sendable` struct that is *everything the* `Chart` *needs* — replacing the VM caches (S7) and the
view `@State` caches:

```swift
struct ChartModel: Equatable, Sendable {          // built by ChartPrep, published by the store
    let period: TimePeriod
    let productType: EntryType
    let orderedSeriesNames: [String]
    let seriesPoints: [String: [PlottedGraphSeries]]   // pre-sorted ascending by xDate; plotXDate baked in
    let dataFingerprint: Int                            // cheap change token (count + endpoints), NOT y-axis
    let yAxis: YAxisModel                               // domain + ticks (see C5)
    let xDomain: ClosedRange<Date>                      // full scrollable domain
    let visibleDomainLength: TimeInterval
}
```

- `GraphSeries`/`PlottedGraphSeries` are already value structs → the model is trivially `Sendable`.
- `dataFingerprint` **deliberately excludes the y-axis** (unlike today's `cacheHash`). Y-axis changes update
`yAxis` in place without changing series identity → **this is what lets S1 die.**
- Built by a `ChartPrep` type (pure functions + a `@ModelActor`/detached hop for the group+sort), mirroring
the MOB-1433 off-main pattern. Input is a small `Sendable` snapshot extracted from
`[BathScaleWeightSummary]` on the main actor *before* the hop (the class is not `Sendable`).



### C3. Stable `Chart` — kill the identity churn (S1)

- **Remove** `.id(lastDataHash)`**.** The `Chart` keeps one identity for the life of the period view. Swift
Charts diffs marks by their `.value`/`id` and animates *in place* — this alone removes the
points-then-lines re-mount and the settle rebuild.
- Series content becomes a pure function of `ChartModel.seriesPoints` (a `ForEach` over stable ids).
- **Plot the same value in** `LineMark` **and** `PointMark` (fix S6): clamp both, or clamp neither and rely on
the y-scale to clip. No divergence.
- Keep the `.equatable()` short-circuit on the outer view; with a value-typed model the equality check is a
cheap fingerprint compare.



### C4. Windowing that cooperates with native scroll (S2, S10)

Replace the *two* stale windowing passes with **one decimation done at prep time**, sized so the *entire*
scrollable series is smooth to scroll natively:

- Decimate each series **once** to a bounded density across the full x-domain (target ≈ the plottable pixel
width per visible window, e.g. min/max or Largest-Triangle-Three-Buckets so line **shape** is preserved),
not a 30-point buffer around a moving window. Given the data is daily/monthly aggregates, most weight
series are already ≤ a few hundred points and need *no* decimation; decimation only engages on long
`total` ranges.
- Hand Charts the decimated full set + a fixed `.chartXVisibleDomain(length:)` and let it scroll natively.
**No per-frame or per-settle re-windowing.** This removes the "scroll a stale slice" defect at the source
and makes the crosshair still land on real points (full-resolution kept for selection via the model).
- Because there is no live re-window, `scrollPosition` no longer needs to feed the render at all during a
drag — S10's tension disappears. The local `@State` scroll (Apple's canonical `.chartScrollPosition($state)`
pattern) syncs to the store **only at boundaries** (scroll-end commit / programmatic move).



### C5. Y-axis policy — the one real product decision

Today's y-axis is **adaptive** (rescales to the visible window) and *frozen during scroll*, so it must
resettle when the finger lifts — **that resettle is the jerk (④), and it is inherent to adaptive+frozen.**
Two clean targets:

- **Option Y-A — Fixed domain per period (recommended for smoothness).** Compute one y-domain per
period from the whole dataset at prep time; it never changes during scroll, so **there is no scroll-end
rescale and no jerk at all.** Simplest, fastest, most stable. Cost: a tall outlier compresses the visible
line on short windows.
- **Option Y-B — Adaptive but settle in one event.** Keep window-adaptive scaling, but recompute **once**
on `isScrolling → false` (collapse S4's three paths into one) with **one** animation (collapse S5). Jerk
becomes a single, smooth, deterministic transition instead of a late multi-source lurch.

> **DECIDED (Kesavan, 2026-07-08): Y-B — keep it adaptive, exactly as it behaves today.**
>
> - [ ] Y-A — fixed per-period domain
> - [x] **Y-B — adaptive (rescales to the values in the visible window), but collapse the resettle to ONE
>   `isScrolling → false` event with ONE animation.** Behaviour is unchanged from today — the y-axis still
>   zooms to the window's values when the finger lifts — we only remove the *multi-source, late, competing*
>   resettle (S4/S5) that makes it lurch. Same look, one clean transition.



### C6. Scroll lifecycle — one event

- **Start:** `.interacting` → `isScrolling = true`, clear selection (already correct,
`[DashboardGraphManager.swift:79-84](../../meApp/Features/Dashboard/Managers/DashboardGraphManager.swift#L79)`).
- **During:** nothing recomputes. Charts scrolls natively over the prepared model.
- **End (**`.idle`**):** commit the local scroll position to the store **once**; if Y-B, kick **one** off-main
y-axis recompute → publish new `ChartModel.yAxis` → **one** animated in-place update. Delete the
`handleScrollEndOptimized` cascade and the VM's separate `handleScrollEnd` timer (S4/S9).



### C7. Selection / crosshair

Keep the local-`@State` selection binding (already `guard !isScrolling`-gated,
`[BaseGraphView+ChartModifiers.swift:110-134](../../meApp/Features/Dashboard/Views/Components/BaseGraphView+ChartModifiers.swift#L110-L134)`).
Crosshair reads the full-resolution model for snapping so taps land on real entries even where the line is
decimated. No change to the store selection contract.

### C8. Multi-series readiness (weight + BPM + baby) — free once C1–C4 land

The percentile path (H1) is already binary-searched and unit-tested (`PercentileChartWindowing`, committed
`813f0f98a`). On the new engine, BPM (2 series) and baby (5–10 series) are **just more entries in**
`ChartModel.seriesPoints` built off-main — no new hot path, no `.id` rebuild multiplier. This is the
epic's stated prerequisite for shipping those graphs (`performance-analysis-5.1.0.md` §6) satisfied by
construction.

---



## Part D — Migration (phased, parity-gated; ONE PR on this branch)

> **Delivery (Kesavan, 2026-07-08): the entire weight-graph rebuild ships as a SINGLE PR** on
> `MOB-518-chart-engine-scroll-hitch-multi-series`. Phases are ordered, independently-revertable **commits
> within that one PR** — not separate PRs. Nothing merges to `develop` until the whole weight graph is signed
> off. (Baby/BPM come later, on their own branch, once weight is approved.)

> **Strategy:** land the highest-leverage, lowest-risk structural fix first (S1), prove parity on device,
> then progressively hollow out the caches behind the new model. **No big-bang.** Every phase keeps the
> visible output identical, verified on device.
>
> **Two standing constraints (Kesavan, 2026-07-08):**
> - **No test cases are written until every weight-graph change is done and signed off.** During the
>   migration, verify with the on-device QA script (§E) + *temporary* debug logging (a computed-count /
>   point-dump behind `#if DEBUG`, removed before commit) — **not** committed `XCTest`/Swift Testing files.
>   All automated tests land together in the final **Phase T**.
> - **Baby / BPM graphs are not touched until the weight graph is signed off.** While editing shared code,
>   leave `// MULTI-SERIES:` comments at each decision point noting what baby (5–10 percentile series) and
>   BPM (2 series + reference lines) will need there — so the later multi-series pass is guided, not
>   re-discovered. Do not change baby/BPM behaviour in these phases.

**Phase 0 — Manual parity reference (before touching the engine).**
No test file. Capture the current behaviour as the parity oracle **by hand**: screenshot each period
(week/month/year/total) at rest + mid-scroll + on a selected point, and add a *temporary* `#if DEBUG` dump of
`(orderedSeriesNames, seriesPoints.count per series, yAxisDomain, yAxisTicks)` logged via `LoggerService` so
later phases can diff against it in the console. Delete dead `chartRebuildToken` (S9). Keep the screenshots/logs
in the ticket, not the repo.

**Phase 1 — Kill** `.id(lastDataHash)` **(S1). ★ biggest single win, smallest diff. ✅ DONE (2026-07-09,**
`439b3b6fb`**).**
Remove the `.id` so the `Chart` keeps one stable identity and Swift Charts diffs/animates marks in place
instead of tearing down + re-mounting on every y-settle. Marks stay correct because `chartSeriesContent` is a
pure function of the caches; period switches still remount via the distinct generic specialization.
**Animations were deliberately left untouched** — reading the code showed the y-domain is *not* double-animated
today (`isDomainChangeOnly` mutes the `.animation` modifier so `syncYAxisFromStore`'s `withAnimation(0.15)` is
the *sole* settle animator; the `.id` teardown was what stopped it easing). Removing `.id` lets that existing
animation ease the settle for free; removing the `withAnimation` too would leave the muted modifier → the axis
would *snap*. **So the S5 animation-owner collapse moved to Phase 4** (done with the settle-path collapse, where
it can be reasoned about whole). *Shippable on its own; expected to resolve symptoms ①③④ substantially.*

**Phase 2 — One decimation, native scroll (S2, S10).**
Replace `pointsToRender`'s 30/30 buffer + the store's re-window with a single prep-time decimation over the
full domain; hand Charts the full decimated set + fixed visible domain. Confirm scrolling a long `total`
range stays smooth and the crosshair still snaps to real points. Resolves symptom ②.

**Phase 3 — Introduce** `ChartModel` **+ off-main** `ChartPrep` **(S3, S7).**
Build the immutable model off-main (extract Sendable input on main first — respects the
`no_published_swiftdata_model` / `check-snapshot-boundary.sh` rule). Point the stable `Chart` at the model;
delete the view `@State` cache set and the VM `cachedSeriesData`/`cachedPlotXDates`/`cachedVisibleSeriesData`.
The VM shrinks to UI state (selection, scroll, isScrolling) only.

**Phase 4 — Single-event y-axis settle (S4), Y-B (S5).**
Collapse `handleScrollEndOptimized` + `handleScrollPhaseChange(.idle)` + VM `handleScrollEnd` into one
settle on `isScrolling → false`. Keep the adaptive rescale (Y-B, §C5) — same behaviour as today — driven by a
single animation. Delete the redundant recompute paths and competing `.animation`/`withAnimation` calls.

**Phase 5 — Cleanup.** Remove `visibleChartSeriesData` (S9, dead for rendering), bound the a11y descriptor
sort (S8), delete now-dead manager/cache paths, update `GraphViewFlow.md`. (Its orphaned tests go in Phase T.)

**Phase T — Tests (only after the weight graph is signed off).** Now write the automated suite: golden
point-set + y-axis parity per period, decimation-preserves-shape + crosshair-selectable, scroll-end settles
once, no re-derivation during scroll. Remove every temporary `#if DEBUG` probe added in Phases 0–4.


| Phase | Fixes                      | Risk     | Shippable alone? | Resolves            |
| ----- | -------------------------- | -------- | ---------------- | ------------------- |
| 0     | manual parity ref + S9 (partial) ✅ | none | n/a          | —                   |
| 1     | S1 ✅                       | Low–Med  | ✅                | ①③④ (most)          |
| 2     | S2, S10                    | Med      | ✅                | ②                   |
| 3     | S3, S7                     | Med–High | ✅                | ③ (residual), scale |
| 4     | S4, S5 (Y-B)               | Med      | ✅                | ④ (residual)        |
| 5     | S8, S9                     | Low      | ✅                | maintainability     |
| T     | all tests                  | Low      | n/a (final)      | regression lock-in  |


---



## Part E — Risks, tests, done-when

**Regression risks (the only real risk is execution, not feature loss):**

1. **Decimation shape / crosshair** — keep full-resolution in the model for selection; parity-test the
  rendered point set preserves line shape.
2. **Initial position / period-switch anchor / scroll-to-latest** — the programmatic-adopt path
  (`scrollAdoptToken` today) must survive the stable-Chart change; device-verify all four.
3. **Y-axis behaviour** — Y-B keeps today's adaptive rescale, so no *behavioural* change to sign off; the
  only visible delta is the settle becoming one smooth transition instead of a late lurch.

**Verification DURING the migration is on-device + temporary debug logging only — no committed tests until
Phase T** (Kesavan's constraint). Per-phase device QA script:

- Scroll all periods (week/month/year/total) on a weight account: **no** points-then-lines on settle, **no**
  rebuild flash, finger tracks 1:1, no coarse/sparse line mid-scroll.
- Crosshair still snaps to a real entry; window average settles on finger-lift; y-axis matches the visible
  window (adaptive, as today) in one clean transition.
- Initial open + tab-back + period switch land on the latest window with the latest point selected.

**The automated suite (Phase T only):** golden point-set + y-axis parity per period × product; decimation
preserves shape + keeps crosshair-selectable points; scroll-end settles y-axis exactly once; prep runs 0×
between scroll-start and scroll-end.

**Done when (epic gate,** `performance-analysis-5.1.0.md` **§7):** Animation Hitches trace (Profile build,
physical device) on a large weight account **and** a baby percentile account shows steady-state scroll
**< ~5 ms/s, no frame > 16.7 ms**; no points-then-lines on settle; y-axis settles once; crosshair snaps to a
real entry across week/month/year/total.

---



## Part F — Open decisions (edit inline)

1. ✅ **RESOLVED — Y-axis policy: Y-B (adaptive, unchanged behaviour, single settle event).** See §C5.
2. ✅ **RESOLVED — one single working PR** on `MOB-518-chart-engine-scroll-hitch-multi-series` for the whole
  weight-graph rebuild. Phases are ordered commits within it; nothing merges to `develop` until the full
  weight graph is signed off. (No per-phase PRs / child tickets.)
3. ✅ **RESOLVED — Baby/BPM wait until the weight graph is signed off by Kesavan.** They are NOT touched in
  Phases 0–5; shared-code edits carry `// MULTI-SERIES:` notes so the later pass is guided. Once weight is
  approved, the multi-series pass rides on top of the finished stable-Chart engine.

---



## Appendix — file inventory


| File                                                                     | Fate in the rebuild                                                                  |
| ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------ |
| `Views/Components/BaseGraphView.swift`                                   | Slims down: stable `Chart`, reads `ChartModel`, drops the `@State` cache set + `.id` |
| `Views/Components/BaseGraphChartContent.swift`                           | Keep marks; fix S6; `PercentileChartWindowing` stays                                 |
| `Managers/Graph/BaseGraphViewCacheSupport.swift` / `…CacheManager.swift` | Folded into `ChartPrep` / `ChartModel`; most removed                                 |
| `ViewModels/BaseSectionViewModel.swift`                                  | Shrinks to UI state (selection/scroll/isScrolling); series/y-axis caches removed     |
| `Managers/DashboardChartManager.swift` / `DashboardGraphManager.swift`   | Scroll-end cascade → single settle; y-axis prep moves off-main                       |
| **New** `Managers/Graph/ChartPrep.swift` + `Models/ChartModel.swift`     | Off-main prep + immutable model                                                      |
| `Managers/Graph/ChartDomainSanitizer.swift`                              | Keep (W2 guard)                                                                      |
| `Managers/Graph/GraphDataPreparer.swift`                                 | Keep series builders; called by `ChartPrep`                                          |


*Line numbers verified 2026-07-08 on* `develop` *(post W1/W2). Re-grep symbols if they drift after later commits.*
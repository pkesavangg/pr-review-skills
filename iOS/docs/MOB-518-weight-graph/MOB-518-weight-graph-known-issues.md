# MOB-518 — Weight graph v2: device observations / deferred-issues log

> **What this is.** A running punch-list of issues seen on device while building the new weight engine.
> We deliberately **defer** most of them because a planned step will address them — this log makes sure none
> is forgotten. **After all the architecture + parity work lands, we sweep this list and fix/verify each.**
> Append as new observations come in. Nothing here is committed-code — it's the checklist.

**Legend — Status:** `deferred` (a planned step will fix it) · `verify` (should be fixed by a step we did/plan
— confirm on device) · `investigate` (cause unconfirmed) · `fixed` (done + confirmed).

---

## Observations (as of 2026-07-09, after A3 + A1 + A2 + Phase 4 + V-A4)

| # | Observation (device) | Suspected cause | Planned fix / step | Status |
|---|----------------------|-----------------|--------------------|--------|
| 1 | **Vertical gridlines not shown** (old graph has vertical rules at period/week boundaries; new one only draws horizontal y-gridlines) | `WeightChartView` only added `AxisGridLine()` on the y-axis; no x-axis gridlines. Needed full-domain ticks first (V-A5a). | **V3 (done)** — `WeightChartView.chartXAxis` now draws vertical gridlines over `gridTicks` (solid at week-start/month-1st/Jan-1 via `isPeriodBoundary`, light between) + labels over `labelTicks` (month chip), parity with the legacy `gridTicks`/`adjustedLabelTicks`. | **verify on device** (vertical rules + labels should match the old graph) |
| 2 | **Section (period) switch feels heavy / stuck** | On every period switch `GraphView` configured all 4 section VMs + `forceScrollPositionUpdate` + `updateYAxisCache` (the legacy machinery) **even though the new engine was active** — wasted work alongside the new host's own `adopt()`+rebuild. Plus the chartManager's `.idle` 50 ms settle ran on every scroll-end. Dual ownership. | **V-A4 (done)** — GraphView's period-switch handler now `guard !usesNewWeightEngine`s out of the section-VM machinery; the host routes scroll phase straight to `graphManager` (no chartManager `.idle` settle). Single owner. | **verify on device** (switch should feel instant now) |
| 3 | **After a scroll stops, can't scroll again for ~1 s** | **Root cause found (A2's cascade theory was WRONG — removing it didn't help).** The scroll-END **model rebuild** re-emitted scroll-dependent x-geometry — `visibleDomainLength` (per-month for `.month`) and `xAxisTicks` (windowed for large spans) both change with scroll position — so Swift Charts **rebuilt its whole scroll view** every time a scroll stopped, blocking the next scroll. | **Phase 4 (done)** — scroll-end now calls `resettleWeightYAxis` → `ChartModel.withYAxis`, which recomputes ONLY the adaptive y-axis and leaves series + x-geometry byte-identical → no scroll-view rebuild, one y animation. | **verify on device** (re-scroll immediately after a stop should be instant now) |
| 4 | **Selection / crosshair doesn't work** (tap does nothing; no crosshair, header doesn't switch to the tapped point) | Not built yet — the new view had no `.chartXSelection` binding and no crosshair marks. | **V4 (done, 6a–6f)** — `.chartXSelection` + crosshair `RuleMark` + enlarged point (6a); header value/label + tab-switch-latest via the shared store-driven header (6b); goal chip (6c); weightless (6d); metric co-plot + switching (6e); active-month greying (6f). | **verify on device** — tap → crosshair + header switches; goal chip; metric overlay; month greying |

*(“…and some more” — append below as you hit them.)*

| # | Observation (device) | Suspected cause | Planned fix / step | Status |
|---|----------------------|-----------------|--------------------|--------|
| 5 | **Scroll hangs / hitches badly on Week & Month (and Total); Year alone is smooth.** Dragging stutters, finger doesn't track. | **Root cause = x-axis TICK COUNT, not canvas width.** With the full-dataset x-domain, `GraphRenderingConfiguration.fullXAxisValues` generated ~1000 `AxisMarks` (weekly ticks across a multi-year span). Swift Charts evaluates the `AxisMarks` closure per value even for off-screen ticks → the hang. Year was smooth only because it plots monthly data (few points **and** few ticks). See the deep-dive below for how canvas-width was ruled out. | **Full domain KEPT** (continuous scroll, no walls/jumps) + **windowed ticks**: `GraphRenderingConfiguration.boundedXAxisValues(±`ChartPrep.tickWindowRadius`=10 windows, clamped to data span)` → ~dozens of ticks. Refreshed **in place** at scroll-end via `ChartModel.withYAxisAndTicks` (`DashboardStore.settleWeightChart`) so `xDomain`/`visibleDomainLength`/`seriesPoints` stay byte-identical → no scroll-view rebuild (no #3), no jump. | **Week verified smooth on device (2026-07-10).** Month improved (see #6). |
| 6 | **Month "jerk to the wrong month" on release.** From e.g. May 2026, a small scroll expecting April lands on **March** with a visible jerk. Week does not do this. | New `WeightChartView` **lacked the legacy native scroll snapping** (`PagedChartScrollBehavior`). It free-scrolled, then the shared manual `DashboardGraphManager.snapScrollPosition` (month → 1st) yanked `scrollX` **after** release. Week hid it (day-grained snap = tiny nudge); month's coarse month-1st snap could jump ~2 weeks → the jerk/overshoot. | Add `.chartScrollTargetBehavior(scrollBehavior(for:))` to `WeightChartView` (per-period `PagedChartScrollBehavior`, copied from legacy `BaseGraphView.getChartScrollBehavior`) so the scroll lands ON a boundary **during deceleration**; and **remove** the V-A5b manual `scrollX = committed` reflect in `WeightChartHost` that fought it. | **FIXED + verified on device (2026-07-10).** Final shape: correctly-configured `ValueAlignedChartScrollTargetBehavior` (period-grid `matching`) lands on the boundary in one motion; midnight gridlines/labels close the noon/edge gap. See the "Snap rework" section below. |

---

## Scroll-hang deep dive (2026-07-09 → 2026-07-10) — how we found the real cause

The hang looked like the canvas-width problem (full-dataset x-domain ÷ tiny visible window = a scroll canvas ~150× the viewport). It was **not**. The evidence:

1. **Clamp the domain only** (canvas ~8×) but leave ticks full (~1000) → **still hung.**
2. **Clamp domain + ticks** (canvas ~8×, ~50 ticks) → **smooth.**
   → the only difference was the tick count, so **the ~1000 `AxisMarks` were the cost, not the canvas.**
3. **Full domain + windowed ticks** (canvas back to ~150×, ~50 ticks) → **still smooth** — the clean isolation: the *original hanging config* was full-domain + full-ticks, and changing only the ticks fixed it.

**Discarded approach (do not retry): bounded domain + re-center at scroll-end.** Capping the x-domain to ±N windows and re-centering the window when the finger lifts near an edge *did* fix the hang, but it made the domain edge a **hard wall mid-drag** and the re-center **jerked** the position on release. Full-domain + windowed-ticks is strictly better (no walls, no re-center jerk) and is the shipped shape.

### Files changed (2026-07-10)
- `Managers/Graph/GraphRenderingConfiguration.swift` — new `boundedXDomain(...)` + `boundedXAxisValues(...)` (windowed, clamped to data span).
- `Managers/Graph/ChartPrep.swift` — `buildWeight` uses `fullXDomain` (kept) + `boundedXAxisValues` (windowed); new `tickWindowRadius = 10` constant.
- `Models/ChartModel.swift` — new `withYAxisAndTicks(_:ticks:)` in-place settle (y-axis + ticks only; scroll geometry frozen).
- `Stores/DashboardStore.swift` — `settleWeightChart(scrollPosition:)` replaces the plain `resettleWeightYAxis` call at scroll-end (in-place y-axis + windowed-tick refresh; full rebuild only when a metric is co-plotted).
- `Views/Components/WeightChartView.swift` — `.chartScrollTargetBehavior(scrollBehavior(for:))` (native paged/aligned landing per period).
- `Views/Components/WeightChartHost.swift` — dropped the V-A5b manual `scrollX = committed` reflect (fought native paging).

### Still open (handle later — 2026-07-10)
- [x] ~~Remove the temporary `#if DEBUG` `🟣 MOB518-clamp` / `🟣 MOB518-settle` `print`s in `DashboardStore`~~ — **DONE (2026-07-10)**, removed before commit.
- [x] ~~**Month visual-vs-store position mismatch**~~ — **RESOLVED (2026-07-10).** `commitWeightScroll` commits the (snapped) native-landed position as the single source of truth AND the visual reflect targets the same boundary, so `scrollX` == `xScrollPosition`. Header / active-month greying / y-axis all read the on-screen window. See the "Snap rework" section.
- [ ] **Windowed ticks are finite (±10 windows):** a very long single fling can outrun the gridlines until the scroll-end refresh. Confirm acceptable or widen.
- [ ] **`boundedXDomain` is now only used internally by `boundedXAxisValues`** (the domain path uses `fullXDomain`) — keep or inline.
- [ ] **Full device parity pass** across all four periods + Instruments Animation Hitches (< 5 ms/s, no frame > 16.7 ms) on a large account.

---

## Snap rework (2026-07-10, second pass) — Apple-Health-style boundary landing ✅ verified on device

**Goal.** Match Apple Health: the scroll DECELERATES onto the period boundary (Sunday / 1st / Jan 1) as one
continuous motion — not "rest anywhere, then correct after." Verified on device 2026-07-10 (week + month).

**How we got there (the diagnostic trail, so we don't repeat the dead ends):**
1. **Window width is not the lever.** Set the week window to exactly 7 days (dropped the 7.15 "UX spacing",
   `DashboardConstants.TimeInterval.week`) so the window == the weekly stride. The scroll STILL landed on
   arbitrary weekdays → commensurate windows don't help.
2. **`ValueAlignedChartScrollTargetBehavior` was MISCONFIGURED, not a no-op.** Per Swift Charts (Majid / Apple
   docs), `matching` is the FINE alignment grid and `majorAlignment` the COARSE boundary a swipe lands on.
   The old config had them backwards — it put the *week boundary* (`weekday: 1, hour: 12`) in `matching`
   (which should be the daily grid) with an `hour: 12` that isn't even on the day grid. That's why it never
   aligned and we (wrongly, at first) concluded the API was dead.
3. **Coarse `matching` also works — and is what we want.** Setting `matching` to the *period* grid itself
   (weekly for week, monthly for month, at `hour: 0`) makes EVERY release snap straight to the boundary in
   one motion. A daily `matching` only snapped to the nearest day, which then needed a second host move to
   reach the boundary → a visible two-stage motion (rejected).
4. **Noon vs midnight gap.** Value-alignment only lands on midnight/day boundaries (`hour: 12` is a device-
   proven no-op). But the tick generators place ticks at NOON, so the Sunday gridline sat ~12 h (≈7% of a
   7-day window) right of the left edge → a visible left gap on week only (month/year: <2%, invisible).
   Fixed by drawing the **gridlines at the day boundary (midnight)** to coincide with where the scroll lands,
   and snapping the **week labels to that same boundary** so each day label sits ON its rule (like month's
   "1"). Month/year labels stay at the noon tick (their offset is invisible), so they're untouched.

**Shipped shape (files):**
- `Views/Components/WeightChartView.swift` — `scrollBehavior(for:)` returns a correctly-configured
  `ValueAlignedChartScrollTargetBehavior` (`matching` == the period boundary grid == `majorAlignment`, all at
  `hour: 0`); `.chartScrollTargetBehavior(scrollBehavior(for:))`. `gridTicks` drawn at `startOfDay` (midnight);
  week `labelTicks` also snapped to `startOfDay` so labels sit on the rules.
- `GraphRenderingConfiguration.snapWeightScrollPosition(_:for:)` — weight-only snap to the NEAREST boundary
  (week → nearest Sunday-midnight, month → nearest 1st, **round not the legacy floor** → no backward jerk).
  Legacy `snapScrollPosition` untouched → baby/BPM unaffected.
- `DashboardStore.commitWeightScroll(landedAt:) -> Date` — snaps the landed `scrollX`, commits it as the ONE
  scroll position, settles the y-axis + windowed ticks in place. Returns the snapped value.
- `WeightChartHost` scroll-end — `commitWeightScroll(landedAt: scrollX)` + a distance-aware `easeOut` reflect
  that is a **safety net**: correction ≈ 0 (so it doesn't fire) when native aligned; it only glides the scroll
  onto the boundary if the OS ever fails to align. Store == visual → header / greying / y-axis all match.
- `DashboardConstants.TimeInterval.week` — 7.15 → 7 days.

Resolves the **"Month visual-vs-store position mismatch"** open item above and the month **release jerk** (#6).
The temporary `🟠 MOB518-snap` debug probe was removed before commit.

> **Superseded by the third pass below (2026-07-10).** This second pass used a COARSE `matching` (the period
> grid in *both* `matching` and `majorAlignment`) plus a `snapWeightScrollPosition` + a distance-aware animated
> reflect. Device testing showed that force-snapped EVERY release to the boundary (no mid-period resting) and
> drifted a unit on release / on leave-and-return. The third pass replaces it with a FINE `matching` +
> boundary `majorAlignment`, committed verbatim (no re-snap, no reflect).

---

## Snap rework — third pass (2026-07-10): fine-grid resting, no post-release move, trailing frame ✅ built

Device feedback on the second pass surfaced three behaviours + two cosmetics. All fixed in the working tree
(`WeightChartView` · `WeightChartHost` · `DashboardStore` · `GraphRenderingConfiguration`). **Verify on device
across all four periods.**

1. **Could not place the window mid-period (issue #1).** The second pass put the *period boundary* in BOTH
   `matching` and `majorAlignment`, so EVERY release force-snapped to Sunday / the 1st — you couldn't rest a
   week on Wed→Wed or a month mid-month. Apple Health only pages to a clean window on a *fling*; a slow drag
   rests wherever you place it. **Fix:** `matching` = the FINE grid (`hour: 0` = any day for week/month;
   `day: 1, hour: 0` = any month-1st for year); `majorAlignment` = the period boundary (week start / 1st /
   Jan 1). A fling decelerates onto the boundary in one motion; a slow drag rests on any day/month.
   (`WeightChartView.scrollBehavior`.)

2. **One-unit drift on release / on leave-and-return.** After the fine-grid change, the window jumped one day
   (week/month) or one month (year) when the scroll ended, and again when you left and returned. **Cause:**
   `commitWeightScroll` re-snapped the landed position (`snapWeightScrollPosition`, nearest grid unit) and the
   host then *animated the visual scroll* to that snapped value — moving the window after release; re-adopting
   the snapped value on return jumped it again. **Fix:** native value-alignment already rests on the fine
   grid, so **commit the landed position VERBATIM** (no re-snap) and **drop the animated reflect**. Stored ==
   visible → nothing moves after release, and re-adopting on return never jumps. `snapWeightScrollPosition`
   deleted (now dead; a NOTE in `GraphRenderingConfiguration` records why there is no weight-specific snap).
   (`DashboardStore.commitWeightScroll`, `WeightChartHost` scroll-end handler.)

3. **Year didn't auto-select the latest on section switch (issue #2).** Week/month selected the latest point
   on a period switch; year/total didn't. **Cause:** the period-switch auto-select
   (`DashboardChartManager.updateSelectedPeriod`) reads `dataManager.getContinuousOperations`, which lacks the
   store's monthly-summary fallback that the *model* is built from — so the store selection didn't resolve
   against the year/total model. **Fix:** `WeightChartHost.selectLatestIfNeeded()` force-selects the latest
   point **from the model it plots** (`fullResolution`) whenever the current selection doesn't resolve to a
   crosshair — a no-op for week/month, self-consistent for year/total. Called on period switch + graph-ready
   only (never on scroll-end, where an empty selection is intended).

4. **Selection dropped on finger-lift (issue #3).** Tapping a point then lifting cleared the crosshair.
   **Cause:** Swift Charts resets `.chartXSelection` to `nil` when the scrub gesture ends, and
   `handleSelectionChange(nil)` cleared the store. **Fix:** ignore the `nil` (persist) — Apple Health keeps
   the last-tapped point highlighted until the next scroll, and the store already clears its own selection on
   scroll-start (`.interacting`). (`WeightChartHost.handleSelectionChange`.)

5. **Trailing frame + y-axis gap (cosmetics).** (a) The last window looked "open" on the right — added a
   fixed 1 pt vertical **closing rule** at the plot's trailing edge via a `.chartPlotStyle` trailing overlay
   (mirrors the leading boundary rule; `.chartPlotStyle` styles the fixed viewport so it stays put as the
   content scrolls). (b) The y-axis numbers hugged the screen edge — centered each in a fixed 40 pt box
   (`yAxisLabelWidth`, parity with the legacy `BaseGraphView`) so they sit off the edge with a gap.
   (`WeightChartView`.)

Net: the second pass's coarse-`matching` + `snapWeightScrollPosition` + distance-aware reflect are all gone;
positioning is now pure native value-alignment (fine `matching` + boundary `majorAlignment`) committed
verbatim, with a closed trailing frame.

---

## Section-switch feel + selection date callout (2026-07-10, fourth pass) ✅ built

1. **Section switch "felt like it scrolled to the recent window."** With a single persistent `WeightChartView`
   across periods, a switch changed the model (domain / window / points / y-axis) AND the scroll position on
   the SAME chart, so Swift Charts animated the scroll + y from the old period into the new one — a visible
   "scroll to recent." **Fix:** `.id(model.period)` on the chart so a period switch **remounts a fresh
   instance** (the per-period view identity the legacy engine had via distinct generic types). A fresh chart
   lands directly at the new period's latest window with no cross-period scroll/y animation. The id is keyed
   ONLY on period — never on scroll / y-settle — so it does NOT reintroduce S1 (a y-settle within a period
   still animates in place, no teardown). The model is built synchronously (in `adopt` → `rebuild`) before the
   remount renders, so the fresh chart mounts WITH data (no blank-then-pop). (`WeightChartHost.chartContent`.)

2. **Selected date now floats ABOVE the line, in the gap over the plot.** The selected date was only in the
   header; the ask was a floating callout above the selected line, like Health, that stays on-screen at the
   edges. **Two dead ends first, then the fix:**
   - A mark `.annotation(position: .top)` on the full-height crosshair `RuleMark` was **clipped** — its top
     anchor is the plot's top edge, and with no headroom there the label rendered outside the chart frame
     and never showed.
   - Reserving headroom with `.chartPlotStyle { $0.padding(.top,) }` made it visible but **compressed the
     plot** inside the fixed 265 pt frame → cramped x-axis labels + section buttons ("no gap").
   - **Shipped fix:** don't touch the plot. A `.chartBackground` publishes the selected point's clamped
     x-position as a `PreferenceKey`; an `.overlayPreferenceValue` renders the date label positioned at
     `y: -12` so it **overflows the chart's top edge into the header gap** (verified no ancestor clips the
     chain, so it renders instead of being cut). The x is clamped ≥ 55 pt from each plot edge so the label
     stays fully visible at the leading/trailing edges (the "handle the left/right edge" ask). Date via
     `GraphRenderingConfiguration.formatSelectedDate` ("jul 9, 2026" / "jul 2026"), lowercased. No plot
     compression, no frame change. (`WeightChartView` + `WeightChartHost.selectionDateLabel`.)

**Verify on device:** section switch lands instantly on the latest window (no scroll animation); selecting a
point shows the date in the gap ABOVE the plot (not inside it), the plot stays full-size with normal spacing
to the day labels + section buttons, and the label stays fully visible when the point is near the left/right
edge. *(If `y: -12` sits slightly high/low, it's a one-number tweak.)*

---

## Sweep plan (do at the end, before sign-off)

1. After **A2** + **Phase 4** (single-event settle) → re-check **#3** (scroll-lock) and **#2** (switch heaviness).
2. After **V-A4/V6** (drop the legacy weight machinery) → re-confirm **#2**.
3. **V3** → **#1** (vertical gridlines + x-axis label parity).
4. **V4** → **#4** (selection/crosshair/header/goal).
5. Anything still open → investigate individually against the [feature spec](MOB-518-weight-graph-feature-spec.md).

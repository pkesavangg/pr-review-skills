# MOB-518 — Weight graph: complete feature spec (parity checklist)

> **What this doc is.** The full behavioural spec of the **weight** dashboard graph — every feature the new
> v2 engine must reproduce to reach parity. This is the oracle: if it's listed here, the rebuilt graph must do
> it. Derived from the code (not memory); where `GraphViewFlow.md` is stale it's corrected here.
>
> **Scope:** weight (`EntryType.scale`) only. BPM/baby share the same `BaseGraphView` engine but are
> **out of scope** until weight is signed off — noted where relevant so the rebuild doesn't break them.
>
> **v2 coverage key:** ✅ done in the new engine · ◑ partial · ✗ not yet. (Ties each feature to the §8/§9
> architecture backlog in the v2 engine design doc.)
>
> **⚠️ Authoritative status lives elsewhere.** The per-section ✅/◑/✗ markers below are refreshed opportunistically
> and can lag the code. For the canonical remaining-work list see
> [v2-engine-design §10](MOB-518-weight-graph-v2-engine-design.md#10-remaining-roadmap--the-single-ordered-list-2026-07-09)
> and the [known-issues log](MOB-518-weight-graph-known-issues.md); where they disagree with a marker here, they win.
> (Updated 2026-07-11: V-A1…A5, V3, V4, V6 are all done; the remaining work is device sign-off + Phase T tests.)

---

## 1. Structure & periods

- **Four period sections:** Week · Month · Year · Total (`TimePeriod`). A segmented control switches them. ✅
- **Scrollable horizontally** in Week / Month / Year; **Total is NOT scrollable** — it shows the entire
  dataset in one non-paged view, X-axis hidden. ✅
- **Per-period visible window ("domain length"):** Week = **7 days** (weight engine — exactly the Sun→Sun
  value-alignment stride, via `DashboardConstants.weightWeekWindow`; the shared `week` stays 7.15 for legacy
  baby/BPM) · Month = the containing calendar month · Year = 365 days · Total = full padded span. ✅
- **X-axis present** for Week/Month/Year; **hidden** for Total. ◑ (labels not at parity yet — V3)
- Only the active period renders; inactive section VMs are torn down on switch.

## 2. Data source & aggregation

- Chart plots **aggregated summaries** (`BathScaleWeightSummary`), never raw entries. Week/Month use **daily**
  summaries; Year/Total use **monthly** summaries. ✅
- **One point per day (or month).** For each day:
  - **Latest day → the day's most-recent reading** (not an average).
  - **Earlier days → the average** of that day's readings (`EntrySummaryBucket`).  ✅ (reused verbatim)
- Stored weight is **tenths of a pound**; converted at plot time: **lb = stored/10**, **kg = stored/22.0462**.
  Unit follows the account's `weightUnit`. ✅
- Data arriving after first paint (async load) must appear without a manual refresh. ◑ (rebuild-signal — V-A2)

## 3. Plotting (marks)

- **Plot-x per period** (`plotXDate`): Week/Month → that day's **local start-of-day (midnight)**; Year →
  **1st-of-month at midnight**; Total → the raw date. Points sit **ON** the day/month gridline (a Wednesday
  reading is on the "Wed" line, not centered between Wed and Thu), aligned with the value-aligned scroll's
  midnight rest positions. The incoming `BathScaleWeightSummary.date` is already the **local** day (aggregation
  converts the entry's UTC timestamp via `TimeZone.current`), so `startOfDay` is timezone- and DST-correct.
  ✅ (2026-07-11; earlier the v2 engine offset week/month to local **noon** so points centered between the
  midnight gridlines — changed so they land on the line. The legacy section VMs keep their noon convention;
  the two engines draw gridlines at different times — v2 midnight, legacy noon — so each plots on its own grid.)
- **One line + one point per summary.** Line uses **`.monotone`** (Fritsch–Carlson) interpolation. ✅
- **Colors:** weight line + points = brand blue (`theme.weightScaleColor`). ✅
- **Line width:** 3 pt for Week/Month/Year, 2 pt for Total. **Point diameter:** 8 pt (W/M/Y) / 4 pt (Total);
  **selected point** 16 pt / 8 pt. *(Code values — GraphViewFlow.md's 2.5/6 are stale.)* ✅ (base) · ✗ (selected — V4)
- Line and point plot the **same (clamped) value** so they never diverge at the domain edge (old S6 fix). ✅

## 4. X-axis

- **Ticks per period:** Week = 7 daily ticks + 1 phantom trailing (+1 day); Month = a **continuous
  Sunday-anchored 7-day grid** that never resets at the 1st (… may 17, 24, 31, jun 7 …; via
  `monthlyWeeklyTicks`), so labels read every 7 days like Apple Health — NOT the old per-month `1, 8, 15, 22,
  29` reset that bunched `29`/`1` at the boundary; Year = one tick/month + phantom trailing month; Total =
  yearly (same-era) or quarterly ticks, **no labels**. ✅ (2026-07-11 for month; V3 for the rest)
- **Grid lines:** solid vertical rule at period boundaries (week start / year start); the **month divider**
  (1st of each month) is drawn as a separate **gridline-only** mark (`monthBoundaryTicks`, no tick/label) so it
  can't hide the Sunday label sitting beside it; light rule at intermediate ticks; horizontal rules at
  y-ticks; plus a fixed 1 pt **trailing closing rule** at the plot's right edge so the last window reads as a
  closed frame. ✅ (V3 + third pass + 2026-07-11 month divider)
- **Y-axis label gap:** each y-axis number is centered in a fixed 40 pt box so it sits off the trailing screen
  edge with a gap (parity with the legacy `yAxisLabelWidth`). ✅
- **Label formats:** Week = weekday letters (or dates); Month = day numbers; Year = month initials;
  Total = none. Labels may repeat/condense on dense ranges. ✗ (V3)

## 5. Y-axis (adaptive — "Y-B")

- **Adaptive to the visible window:** recomputed from the operations in the current window **∪ the bracketing
  operations** (one before + one after), deduped by `entryTimestamp`. Total uses the whole dataset. ✅
- **Frozen during a drag; resettles once when the finger lifts** (this is the intended behaviour). ◑ (resettles
  via a 150 ms view timer today — must move to the real scroll-end event — V-A1/V-A2)
- **Nice ticks:** step chosen from `[1,2,4,5,10,15,20,25,40,50,100,200]`, **3–6 ticks**, targeting ~4. ✅
- **Edge buffer:** extends a tick if data sits within 35 % of the top/bottom tick (headroom). ✅
- **Non-negative** in normal mode (lower bound clamped ≥ 0); **negative ticks allowed** in weightless mode. ✅
- **Small dataset (1–2 entries):** tight padding (20 % or 0.3 units min). **Empty:** goal-centric ticks if a
  goal is set, else 0–100 default. ✅
- **Last-scale hinting:** uses the previous scale to reduce tick jitter between recomputes. ✗ (not passed yet)
- **Settle animation:** the y-domain change animates once (ease); no animation during the drag. ◑

## 6. Scroll behaviour

- **Native horizontal scroll** over the full series (no per-frame re-windowing). ✅
- **Snap / paging** (Apple-Health-style; 2026-07-10, third pass): a **fling** decelerates onto the period
  boundary (Week → week start / Sunday; Month → 1st; Year → Jan 1) via
  `ValueAlignedChartScrollTargetBehavior`'s `majorAlignment`; a **slow drag** rests on the fine `matching`
  grid (any day for week/month, any month-1st for year), so the window can be placed **mid-period** (Wed→Wed,
  mid-month) exactly like Health. The landed position is committed **verbatim** by
  `DashboardStore.commitWeightScroll` — no re-snap, no animated reflect — so stored == visible: nothing moves
  after release and leaving/returning re-adopts the exact window. Gridlines/labels are drawn at the day
  boundary (midnight) so the leading boundary rule sits flush at the left edge with no gap. Total → n/a
  (not scrollable). ✅ (Earlier passes used a coarse `matching` that force-snapped every release to the
  boundary + a `snapWeightScrollPosition` reflect that drifted a unit — both removed; see the known-issues
  "Snap rework — third pass".)
- **Start position = latest window** ("scroll to latest") on first open. ◑ (seeds from store, snap/anchor not
  at parity — V-A5)
- Committed scroll position is written to the store **only at scroll-end**, not per frame. ✅
  (`WeightChartHost` scroll-end → `commitWeightScroll`; V-A1 done)
- Paging/deceleration should feel 1:1 with the finger. ✅ (native)

## 7. Selection / crosshair

- **Tap to select**, per-period snap rule — a tap snaps to a **gridline (or real entry)**, and an *empty*
  gridline (a day/month with no reading) is a valid selection whose value is **Hermite-interpolated** (§8), so
  you can select the in-between lines, not just entry points. All clamped to the data range `[first, last]`, so
  the crosshair never lands past the first/last reading or on the phantom trailing tick (`WeightChartHost.snappedSelectionDate`):
  - Week → nearest **day** gridline (every day has a line; excludes phantom).
  - Month → nearest **shown line** (every Sunday + each month's 1st — the weekly stride, NOT every day) **or**
    a real entry day, whichever is closer (`monthLineCandidates` mirrors the drawn `gridTicks` + `monthBoundaryTicks`).
  - Year → nearest **1st-of-month**.
  - Total → nearest **real data point** (no continuous daily grid → no interpolation there).
  ✅ (2026-07-11; earlier the v2 host snapped every tap to the nearest *real entry*, so gap days couldn't be
  selected and the Hermite path never ran — the legacy behaviour was lost. Now restored + refined for the
  month weekly grid.)
- **Crosshair** = vertical rule at the selected x (+ callout). For an in-between selection it sits **on** the
  day/month gridline (`crosshairDate` falls back to `ChartPrep.plotXDate` when no real point matches). Hidden
  outside the drawn line's range. ✅
- **Selection clears when a scroll starts** (store clears on `.interacting`), and **persists after
  finger-lift** — Swift Charts resets `.chartXSelection` to `nil` on gesture-end but the host ignores that so
  the crosshair stays until the next scroll, like Apple Health (2026-07-10, third pass). ✅
- **Selection callout** shows the selected date **above the line**, floated in the gap over the plot (a
  `.chartBackground` preference feeds the selected x to an `.overlayPreferenceValue` that overflows the
  chart's top edge — so it doesn't compress the plot), x-clamped to stay fully visible at the left/right
  edges; the value stays in the header. The **redundant date/range line under the big weight** is hidden on
  selection (`GraphView.isShowingSelectionCallout` reads the store's `showCrosshair` for the new engine) so
  the date isn't shown twice; the period-range label returns when nothing is selected. ✅ (2026-07-10)
- Tapping an in-between line snaps to that gridline (interpolated value); tapping near a real entry snaps to
  the entry (exact value). Never a phantom point. ✅ (2026-07-11)

## 8. Header value & label (above the chart)

- **Displayed weight priority:** (1) selected point → its exact weight (day average on week/month); (2)
  **interpolated weight at the crosshair date (Hermite/Fritsch–Carlson)** when the selected gridline has no
  reading; (3) **visible-window average** (arithmetic mean of window ops); (4) latest entry (fallback). Driven
  by `DashboardMetricsCalculator.calculateDisplayWeight`, read reactively from store state. ✅ (interpolated
  path now actually exercised by in-between selection — §7 — 2026-07-11)
- **Label states:** point selected → **"day average"**; no selection → **"{period} average"** (e.g. "week
  average"); no entries in period → "no entries in {period}"; none at all → "no entries". ✅
- **Window average** is an **interpolated** average over the visible range (Hermite samples), and **settles on
  finger-lift** (not recomputed mid-drag). ✅
- Unit suffix (kg/lb) follows the account. ✅

## 9. Goal

- **Goal chip callout** rendered at the goal weight's y-position, floated **ON the trailing y-axis marks**
  (not inside the plot) when a goal is set: a `.chartBackground` preference feeds the clamped goal y-position
  to an `.overlayPreferenceValue` that `.position`s `GoalWeightChipView` at `width − yAxisLabelWidth/2` (over
  the y-axis number column), matching the legacy `chartFrame.width − 20` placement. Clamped into the y-domain
  so it stays on-screen when the goal is far outside the visible range. ✅ (2026-07-10; earlier V4 pass used a
  `RuleMark(y:).annotation(position: .trailing)` that pinned it to the plot's inner edge, left of the axis —
  replaced.)
- Goal influences the **y-axis** (goal-centric fallback ticks when there's no data; goal kept in view). ◑
  (calculateYAxis already receives `goalWeightForDisplay`) 
- Goal weight shown in **display units**. ✅ (chip label via `displayManager.formatWeightDisplayText`)

## 10. Weightless mode

- When the account has weightless on, values are plotted/shown as the **difference from an anchor weight**
  (not absolute), with anchor in display units. ◑ (ChartPrep passes `isWeightlessMode`/`anchorWeight` through
  to the reused builders; header/label not wired — V4)
- **Negative y-ticks allowed** in this mode (normal mode forbids them). ✅ (via calculateYAxis)
- Weightless label/formatting differs (shows ± difference). ✗ (V4)

## 11. Metrics (below-chart + co-plot)

- **Below-chart metric tiles:** bmi, body fat %, muscle % (+ more available: water, heart bpm, bone, visceral
  fat, subcutaneous fat, protein, skeletal muscle, bmr, metabolic age — 4 or 12 by dashboard type). Rendered by
  `MetricCardView` / `MetricGridUIKitView` (shared chrome below the chart, NOT `WeightSnapshotCard`). ✅
- **Tiles track the selection:** selecting a point updates the tiles to that point's **per-point** values
  (`BathScaleWeightSummary` carries them); with no selection they show the **visible-window average**; a
  crosshair on an empty day shows placeholders. The v2 engine drives this from `DashboardStore.selectWeightPoint`
  + `commitWeightScroll` → `displayManager.updateMetricsForCurrentView()` (guarded/de-duped), matching the
  legacy `handleCompleteChartSelection`. ✅ (2026-07-11 — earlier the v2 selection path only updated the header,
  leaving the tiles stale.)
- **Metric switching:** selecting a metric co-plots it as a **second line normalized into the weight y-domain**
  (`buildNormalizedMetricSeriesWithDomain`, toggled by `selectedMetricLabel`); the co-plotted line uses a
  neutral high-contrast color. ✅ (wired in `ChartPrep.buildWeight`; the earlier "✗ V4" was stale)
- Metric availability is filtered to metrics with ≥2 distinct values; metric-info sheet supported. ✅

## 12. Month "active month" greying

- In Month view, when a full calendar month is visible, points **outside** that month interval render with
  **disabled opacity** (greyed) — `WeightChartView.isOutsideActiveMonth` + the muted "outside" colors, driven
  by `displayManager.activeMonthInterval` (suppressed while scrolling). ✅ (V4-6f)

## 13. Lifecycle & initial state

- **Skeleton loader** shows until the graph is ready (`isGraphReady`, ~300 ms after init). ◑ (still driven by
  the old path)
- **Cold start / tab-back / period switch:** auto-select the **latest entry** so the header + crosshair show
  the most recent point — for **all four periods**. The host's `selectLatestIfNeeded()` seeds the latest point
  from the model it plots whenever the current selection doesn't resolve to a crosshair, which closed the gap
  where **year/total** stayed unselected (the shared auto-select read a different operations source than the
  model; 2026-07-10, third pass). ✅
- **Period switch:** lands **instantly** on the new period's latest window — the chart is keyed `.id(period)`
  so it remounts fresh per section (no cross-period "scroll to recent" scroll/y animation), reseeding scroll +
  y-axis + latest selection from the store. ✅ (2026-07-10)
- Inactive section VMs are torn down on switch; product/baby-profile change tears down all. ✅ (unchanged)

## 14. Empty / edge states

- No entries → empty label + fallback/goal y-axis. Single or two entries → tight y-scale, still renders. ◑
- Single-value window (flat line) → non-degenerate y-domain (min≠max) via edge buffer + `ChartDomainSanitizer`. ✅
- Baby with no real readings → empty grid (handled before the weight path; unaffected). ✅ (untouched)

## 15. Rendering safety (already structural)

- Every domain handed to Swift Charts is finite / positive-width (`ChartDomainSanitizer`) — no invalid-frame
  flood (W2). ✅
- Stable `Chart` identity — no teardown/rebuild on a y-settle (S1). ✅
- One prep-time decimation; native scroll over the full series (S2/S10). ✅

---

## Parity gap summary (what the new engine still owes)

**The behavioural/rendering rebuild is feature-complete on `develop`.** The store-integration architecture
(V-A1…V-A4), scroll geometry + snap + initial position (V-A5), selection/header/goal/weightless/metrics/
active-month greying (V4), and x-axis rendering parity (V3) are all **done** and flipped on for weight (V6).
See [v2-engine-design §10](MOB-518-weight-graph-v2-engine-design.md#10-remaining-roadmap--the-single-ordered-list-2026-07-09)
for the itemised status.

What genuinely remains before the epic closes:

1. **Device sign-off** — full parity pass across week/month/year/total + Instruments Animation Hitches
   (< ~5 ms/s, no frame > 16.7 ms) on a large account. Some known-issues items are still "verify on device."
2. **Phase T — automated tests** for the new engine core (golden model parity, decimation-preserves-shape,
   settle-once, prep 0× during scroll). Only the shared pure helpers are covered so far.
3. **Minor deferred:** last-scale y-axis hinting (`lastScale` passed `nil`); optional off-main `ChartPrep`;
   the wholesale legacy `BaseGraphView` delete (waits for baby/BPM to migrate — separate epic).

*Marker staleness note: the per-section ✅/◑/✗ above may still lag; §10 of the design doc + the known-issues
log are the authoritative status. Last spec pass 2026-07-11.*

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

---

## 1. Structure & periods

- **Four period sections:** Week · Month · Year · Total (`TimePeriod`). A segmented control switches them. ✅
- **Scrollable horizontally** in Week / Month / Year; **Total is NOT scrollable** — it shows the entire
  dataset in one non-paged view, X-axis hidden. ✅
- **Per-period visible window ("domain length"):** Week ≈ 7.15 days · Month = 32 days (actually the containing
  calendar month) · Year = 365 days · Total = full padded span. ◑ (Total span hacked, see V-A5)
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

- **Plot-x per period** (`plotXDate`): Week/Month → that day's **local noon**; Year → **month-1st at noon**;
  Total → the raw date. Keeps points aligned to the x-axis ticks. ✅
- **One line + one point per summary.** Line uses **`.monotone`** (Fritsch–Carlson) interpolation. ✅
- **Colors:** weight line + points = brand blue (`theme.weightScaleColor`). ✅
- **Line width:** 3 pt for Week/Month/Year, 2 pt for Total. **Point diameter:** 8 pt (W/M/Y) / 4 pt (Total);
  **selected point** 16 pt / 8 pt. *(Code values — GraphViewFlow.md's 2.5/6 are stale.)* ✅ (base) · ✗ (selected — V4)
- Line and point plot the **same (clamped) value** so they never diverge at the domain edge (old S6 fix). ✅

## 4. X-axis

- **Ticks per period:** Week = 7 daily ticks + 1 phantom trailing (+1 day); Month = Sunday-based weekly ticks
  (1, 8, 15, 22, 29) + phantom trailing Sunday; Year = one tick/month + phantom trailing month; Total =
  yearly (same-era) or quarterly ticks, **no labels**. ◑ (ticks generated; labels/format not at parity — V3)
- **Grid lines:** solid vertical rule at period boundaries (week/month/year start), dashed/faint at
  intermediate ticks; horizontal rules at y-ticks. ✗ (new view draws y-gridlines only — the "vertical lines
  missing" you saw; V3)
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
- **Snap** on release: Week → **week boundary (Sunday)**; Month → 1st of the month; Year → Jan; Total → n/a. ✅
  (2026-07-10, verified on device: a correctly-configured `ValueAlignedChartScrollTargetBehavior` — `matching`
  set to the *period* grid, at `hour: 0` — decelerates ONTO the boundary in one Apple-Health-style motion;
  gridlines/labels drawn at the day boundary (midnight) so the boundary rule + label sit flush at the left
  edge with no gap. `DashboardStore.commitWeightScroll` commits the landed boundary as the single source of
  truth (visual == store), with a distance-aware reflect kept only as a safety net. Note: week now snaps to
  the *week start*, not just any *day* boundary — closer to Apple Health than the original spec wording.)
- **Start position = latest window** ("scroll to latest") on first open. ◑ (seeds from store, snap/anchor not
  at parity — V-A5)
- Committed scroll position is written to the store **only at scroll-end**, not per frame. ✅
  (`WeightChartHost` scroll-end → `commitWeightScroll`; V-A1 done)
- Paging/deceleration should feel 1:1 with the finger. ✅ (native)

## 7. Selection / crosshair

- **Tap to select**, per-period snap rule:
  - Week → nearest real day tick (excludes phantom); crosshair shown only if the day ∈ `[firstPoint, lastPoint]`.
  - Month → **section windowing**: find `[startTick, endTick)` around the tap; select nearest point inside, or
    the section start if empty; crosshair only within `[first, last]` + ~50 % right slack.
  - Year → nearest month tick within `[firstMonth, lastMonth]`.
  - Total → nearest real data point within `[firstPoint, lastPoint]` + small right slack.  ✗ (all — V4)
- **Crosshair** = vertical rule at the selected x (+ callout). Hidden outside the drawn line's range. ✗ (V4)
- **Selection clears when a scroll starts.** ✗ (V-A1)
- **Selection callout** shows the selected date + value near the point. ✗ (V4)
- Tapping the middle/approx area snaps to the nearest real entry (never a phantom point). ✗ (V4)

## 8. Header value & label (above the chart)

- **Displayed weight priority:** (1) selected point → its exact weight; (2) interpolated weight at the crosshair
  date (Hermite); (3) **visible-window average** (arithmetic mean of window ops); (4) latest entry (fallback). ✗ (V4)
- **Label states:** point selected → **"day average"**; no selection → **"{period} average"** (e.g. "week
  average"); no entries in period → "no entries in {period}"; none at all → "no entries". ✗ (V4)
- **Window average** is an **interpolated** average over the visible range (Hermite samples), and **settles on
  finger-lift** (not recomputed mid-drag). ✗ (V4)
- Unit suffix (kg/lb) follows the account. ✗ (V4)

## 9. Goal

- **Goal chip callout** rendered at the goal weight's y-position (trailing edge), when a goal is set. ✗ (V4)
- Goal influences the **y-axis** (goal-centric fallback ticks when there's no data; goal kept in view). ◑
  (calculateYAxis already receives `goalWeightForDisplay`) 
- Goal weight shown in **display units**. ✗ (chip — V4)

## 10. Weightless mode

- When the account has weightless on, values are plotted/shown as the **difference from an anchor weight**
  (not absolute), with anchor in display units. ◑ (ChartPrep passes `isWeightlessMode`/`anchorWeight` through
  to the reused builders; header/label not wired — V4)
- **Negative y-ticks allowed** in this mode (normal mode forbids them). ✅ (via calculateYAxis)
- Weightless label/formatting differs (shows ± difference). ✗ (V4)

## 11. Metrics (below-chart + co-plot)

- **Below-chart metric tiles:** bmi, body fat %, muscle % (+ more available: water, heart bpm, bone, visceral
  fat, subcutaneous fat, protein, skeletal muscle, bmr, metabolic age — 4 or 12 by dashboard type). ✗ (owned by
  `WeightSnapshotCard`, outside the graph view — unaffected by the rebuild, keep working)
- **Metric switching:** selecting a metric co-plots it as a **second line normalized into the weight y-domain**
  (`buildNormalizedMetricSeriesWithDomain`); the co-plotted line uses a neutral high-contrast color. ✗ (V4 —
  ChartPrep currently builds the weight series only)
- Metric availability is filtered to metrics with ≥2 distinct values; metric-info sheet supported. ✗ (V4)

## 12. Month "active month" greying

- In Month view, when a full calendar month is visible, points **outside** that month interval render with
  **disabled opacity** (greyed). ✗ (V4)

## 13. Lifecycle & initial state

- **Skeleton loader** shows until the graph is ready (`isGraphReady`, ~300 ms after init). ◑ (still driven by
  the old path)
- **Cold start / tab-back:** auto-select the **latest entry** so the header + crosshair show the most recent
  point. ✗ (V-A1/V4)
- **Period switch:** anchors the new period around the old period's visible midpoint, recomputes scroll +
  y-axis, clears the previous selection. ◑ (host reseeds from store; anchor parity — V-A5)
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

Most gaps are **selection + header + goal + metrics (V4)** and **x-axis rendering parity (V3)** — and both are
blocked by the **store-integration architecture (V-A1…V-A3)** and the **scroll-geometry source (V-A5)**. In
priority order that's exactly §8/§9 of the v2 design doc:

1. **V-A4 decision** (who owns scroll/selection) → then **A3 → A1 → A2** (store owns the published model +
   lifecycle).
2. **V-A5** (real scroll geometry: snap, initial position, x-domain).
3. **V4** (selection/crosshair, header value+label, window average, goal chip, weightless label, metric co-plot,
   active-month greying).
4. **V3 cosmetics** (vertical gridlines, x-axis labels, selected-point size).

*Values verified against the working tree 2026-07-09. Line/point sizes taken from `BaseSectionViewModel`
(code), not the stale `GraphViewFlow.md` table.*

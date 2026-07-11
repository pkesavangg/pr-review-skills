# MOB-518 — Weight graph, how it actually works (a guided tour)

> **What this doc is.** A **teaching walkthrough** of the new (v2) weight-graph engine: the whole flow end to
> end, how it compares to the legacy engine, *why* it's faster, and how each individual feature works —
> scrolling, y-axis, section switch, selection/crosshair, header average, goal chip, weightless mode, metric
> tiles, the month x-axis, points-on-the-line. Written to be read top-to-bottom with **analogies + ASCII flow
> diagrams**, so you (or a new teammate) can hold the whole thing in your head.
>
> **Scope:** the **weight** graph only (`EntryType.scale`). Baby/BPM still use the legacy engine and are called
> out where it matters. **Companion docs:** the [v2 engine design](MOB-518-weight-graph-v2-engine-design.md)
> (the plan + architecture backlog), the [feature spec](MOB-518-weight-graph-feature-spec.md) (parity
> checklist), the [known-issues log](MOB-518-weight-graph-known-issues.md) (every device fix, in order), and
> the [re-architecture doc](MOB-518-chart-engine-rearchitecture.md) (root-cause diagnosis S1–S10).

---

## 0. The one-paragraph mental model

The weight graph is a **printed poster slid behind a fixed window**. We build the poster **once** (all the
points, the line, the gridlines) whenever the *data* changes, hand it to Swift Charts, and then just **slide
it left/right** behind a fixed-size viewport. We do **not** redraw the poster while you scroll, and we do
**not** throw it away and reprint it when the y-axis rescales — we only *repaint the ruler on the right edge*.
Everything else (selection, the floating date, the goal chip) is a **transparent sticker on the glass**, not
part of the poster. That single discipline — *build once, slide freely, never reprint* — is the whole design,
and it's why it's fast.

The legacy engine, by contrast, **tore down and rebuilt the entire chart every time anything changed** —
including every time the y-axis rescaled at the end of a scroll (and its "poster" was only a small *windowed
slice* of points, not the whole dataset — see the [FAQ](#17-common-doubts--clarifications-faq)). That teardown
is what you saw as "the dots appear, then the line draws in," the hitch, and the jerk.

---

## 1. The cast — who owns what

Three objects collaborate. Keep these roles straight and everything else follows.

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  DashboardStore            "the brain / source of truth"                           │
│   • owns the DATA (aggregated summaries) + all lifecycle state                     │
│   • builds and @Publishes the ChartModel (the poster)                              │
│   • owns selection, scroll-commit, y-axis settle, metrics                          │
└──────────────────────────────────────────────────────────────────────────────────┘
                    │ @Published chartModel (immutable value)         ▲ reports gestures
                    ▼                                                 │ (tap, scroll-end)
┌──────────────────────────────────────────────────────────────────────────────────┐
│  WeightChartHost           "the stagehand"                                         │
│   • period-aware wrapper; observes the store's chartModel                          │
│   • holds LOCAL @State scrollX + selectedX (the live gesture values)               │
│   • translates raw gestures ↔ store calls at BOUNDARIES only                       │
└──────────────────────────────────────────────────────────────────────────────────┘
                    │ passes the model + bindings down
                    ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│  WeightChartView           "the dumb renderer"                                     │
│   • one Swift Charts `Chart`, reads the ChartModel, derives NOTHING                │
│   • Swift Charts owns the native scroll                                            │
│   • draws marks, gridlines, crosshair; floats the date + goal chip as overlays     │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Analogy.** The **store** is the *author* who prints the poster and decides what's on it. The **host** is the
*stagehand* who takes the poster from the author, hangs it, and whispers back to the author when the audience
does something ("they tapped here", "they stopped scrolling there"). The **view** is the *frame on the wall* —
it just displays what it's handed and never invents content.

> **Why this split matters:** the view *deriving* plot data was the legacy engine's original sin. Here the view
> is provably dumb — it can't go stale, because it has nothing of its own to keep in sync.

---

## 2. The data pipeline — from a weigh-in to a dot on screen

```
   Scale reading (UTC instant, ISO8601 string)
        │
        │  EntryService.aggregateByDay / aggregateByMonth
        │  • convert UTC → LOCAL day (TimeZone.current)
        │  • one bucket per local day/month; bucket.date = LOCAL start-of-day (midnight)
        ▼
   [BathScaleWeightSummary]              ← "continuousOperations" on the store
        │                                   (one point per day for week/month, per month for year/total)
        │  ChartPrep.buildWeight(...)     ← the poster printer (pure; reuses the domain math)
        │  • convert weight (lb = stored/10, kg = stored/22.0462)
        │  • plotXDate  → where each point sits on the x-axis (see §7)
        │  • decimate    → only for very long "total" ranges (no-op for normal data)
        │  • x-geometry  → domain, visible window length, windowed ticks
        │  • y-axis      → adaptive domain + "nice" ticks
        ▼
   ChartModel  (immutable, Sendable value type)   ← "the poster"
        │  @Published private(set) on DashboardStore
        ▼
   WeightChartView reads it and draws.
```

### The `ChartModel` — the poster, as a value

```swift
struct ChartModel: Equatable, Sendable {
    let period: TimePeriod                             // week / month / year / total
    let productType: EntryType                         // .scale (weight)
    let orderedSeriesNames: [String]                   // weight, + a metric line if co-plotted
    let seriesPoints: [String: [PlottedGraphSeries]]   // pre-sorted, plotXDate baked in, decimated
    let fullResolution: [String: [PlottedGraphSeries]] // undecimated — for crosshair snapping
    let xDomain: ClosedRange<Date>                     // the FULL scrollable range (the whole poster width)
    let visibleDomainLength: TimeInterval              // the window width (the frame opening)
    let xAxisTicks: [Date]                             // windowed gridline/label positions
    let yAxis: YAxisModel                              // domain + ticks
    let goalWeight: Double?                            // the goal chip value
    let dataFingerprint: Int                           // count + endpoints — EXCLUDES yAxis  ← key!
}
```

**The single most important design decision** lives in that last line. `dataFingerprint` **deliberately
excludes the y-axis.** So when the y-axis rescales at scroll-end, the *series identity doesn't change* — Swift
Charts updates the ruler in place instead of reprinting the poster. In the legacy engine the equivalent hash
*included* the y-axis, which is exactly what forced the full teardown (problem **S1**).

**Analogy.** The poster's "fingerprint" is based on *what's drawn on it* (the dots and line), not on *the ruler
printed down the side*. Change the ruler → same poster, just re-inked ruler. Change the data → new poster.

---

## 3. Legacy vs v2 — the same picture, a completely different machine

### What the legacy engine did (and why it hurt)

| # | Legacy behaviour | What you felt |
|---|------------------|---------------|
| **S1** | The `Chart` had `.id(dataHash)` where the hash **included the y-axis**. Every scroll-end rescaled the y-axis → id changed → SwiftUI **destroyed and rebuilt the whole chart**, replaying its mount animation. | "Dots appear, then the line draws in"; a flash; the settle **jerk**. |
| **S2 / S10** | **Two** windowing passes — the store sliced a buffer around the *committed* position, the view re-sliced around a *stale live* position (±30 points). Neither could track a live drag. | Coarse/sparse/wrong line **while** scrolling. |
| **S3** | The whole prep (group + sort + map + duplicate) ran **on the main thread on every hash change**. | Main-thread **spike** at settle. |
| **S4 / S5** | Y-axis resettle fired through **three racing timers** (+50/100/200 ms…) with **competing animations**. | The axis landed **late** and **lurched**. |
| **S7** | **Six+ caches** with different invalidation rules. | Desync bugs; a maintenance trap. |

**The through-line:** *"settle" meant "rebuild," "scroll" meant "scroll a stale slice," and the rebuild landed
late and heavy.*

### What v2 does instead

```
LEGACY  (frame/settle-driven — rebuild the world)          v2  (event-driven — build once, slide)
─────────────────────────────────────────────────         ─────────────────────────────────────────────
every scroll-end → new y-axis → new .id → TEAR DOWN         every scroll-end → swap yAxis IN PLACE
   + REBUILD the whole Chart (re-mount animation)              (dataFingerprint unchanged → no rebuild)

drag → view re-derives a ±30 window each frame             drag → Swift Charts slides the prepared poster
   (stale, coarse)                                            (nothing recomputes; full-res, native)

prep on main thread on every hash change                   prep ONCE per real data/period/unit/metric change

3 timers + competing animations settle the y-axis          1 event (isScrolling → false) + 1 animation

6 caches, divergent invalidation                           1 immutable ChartModel, one owner
```

### Where the speed actually comes from

1. **No teardown on settle (S1 killed by construction).** The y-axis is swapped in place
   (`ChartModel.withYAxisAndTicks`), so Swift Charts never re-mounts. **This removes the dots-then-line replay,
   the flash, and the biggest per-settle cost.**
2. **Nothing runs during a drag.** The poster is prepared before the scroll starts; Swift Charts slides it
   natively. Zero per-frame work → the finger tracks 1:1.
3. **Windowed ticks (a subtle but huge one).** A full multi-year x-domain would hand Swift Charts **~1000
   `AxisMarks`**, which it evaluates *per value even off-screen* — that alone hung Week/Month/Total. We keep the
   full scroll domain but hand it only **~dozens of ticks** (±10 windows around the scroll position), refreshed
   in place at scroll-end. (Proven on device: same canvas + ~50 ticks = smooth; same canvas + ~1000 ticks =
   hang. It was the tick *count*, not the canvas *width*.)
4. **Prep once, not per frame/settle.** Group + sort + map happen a handful of times (data/period/unit/metric
   change), not continuously.
5. **The gate:** steady-state scroll **< ~5 ms/s, no frame > 16.7 ms** on a large account (Instruments Animation
   Hitches, Profile build). That's the "done" bar for the whole rebuild.

**Analogy for the whole comparison.** Legacy was a **whiteboard** you wiped and redrew every time someone
asked a question. v2 is a **printed poster on a track**: you print it once and slide it; when the labels on the
edge need to change you *re-letter the edge*, you don't reprint the poster.

---

## 4. Scrolling — how the poster slides

Swift Charts owns the scroll natively. Three ingredients:

- **`.chartScrollableAxes(.horizontal)` + `.chartXVisibleDomain(length:)`** — "the frame is this wide; the
  poster is wider; let the user slide it."
- **`.chartScrollPosition(x: $scrollX)`** — a *local* `@State` in the host. During a drag Swift Charts writes
  this cheaply; it does **not** publish to the store, so nothing re-renders per frame. (This is the W1 fix —
  the legacy `@Published scrollPosition` re-entered the view-update loop every frame.)
- **`.chartScrollTargetBehavior(...)`** — how it decelerates. This is the Apple-Health feel:

```
ValueAlignedChartScrollTargetBehavior(
    matching:        <FINE grid>       // where a SLOW drag can rest
    majorAlignment:  <COARSE boundary> // where a FLING lands
)
```

| Period | Fine grid (`matching`) — slow drag rests on… | Boundary (`majorAlignment`) — a fling lands on… |
|--------|----------------------------------------------|-------------------------------------------------|
| Week   | any day (`hour 0`)                           | the week start (Sunday) |
| Month  | any day (`hour 0`)                           | the 1st |
| Year   | any month-1st (`day 1, hour 0`)              | Jan 1 |

**Analogy.** Think of a **filmstrip with sprocket holes**. A *gentle* nudge lets it rest on any frame (any
day); a *flick* runs it to the next reel marker (the week start / 1st / Jan 1). Apple Health does exactly this
— you can park mid-week, but a flick pages cleanly.

**Commit "verbatim."** Wherever the native scroll rests, the store records that *exact* position at scroll-end
(`commitWeightScroll`) — no re-snapping, no animated "reflect" afterward. Stored == visible, so nothing hops
after you lift your finger, and leaving-and-returning re-opens the same window. (Earlier passes re-snapped and
animated a correction; that caused a one-unit drift, now gone — see the known-issues "third pass".)

```
finger down ──► isScrolling = true, clear selection
   drag       ──► Swift Charts slides the poster (NOTHING recomputes)
finger up  ──► native scroll decelerates onto the grid
            ──► isScrolling = false ──► commitWeightScroll(landedAt: scrollX)
                     • store records scrollX verbatim (the one source of truth)
                     • settle the y-axis + windowed ticks IN PLACE (§5)
                     • refresh the metric tiles for the new window (§10)
```

---

## 5. The y-axis — adaptive, but it never reprints the poster

**What you see:** when you stop scrolling, the y-axis (20/40/60…) rescales to fit the values in the visible
window, in one smooth ease. During the drag it's frozen.

**How it works (the clever part):**

- During a drag the y-axis is frozen (nothing recomputes — see §4).
- On `isScrolling → false`, the store recomputes **only** the adaptive domain + "nice" ticks for the new window
  and calls `ChartModel.withYAxisAndTicks(...)`, which returns a copy of the poster with **only `yAxis` and
  `xAxisTicks` swapped**. `seriesPoints`, `xDomain`, `visibleDomainLength`, and `dataFingerprint` are
  **byte-identical**.
- Because the series identity didn't change, Swift Charts **animates `.chartYScale` in place** — no scroll-view
  rebuild, no teardown, one clean transition.

**Analogy.** The dots and line are painted on the poster. The numbers 20/40/60 are on a **separate transparent
ruler clipped to the right edge**. Rescaling swaps the *ruler*, not the poster. (This is why S1 is dead "by
construction" — the type literally can't change series identity during a y-settle.)

**The "nice" ticks:** step chosen from `[1,2,4,5,10,15,20,25,40,50,100,200]`, 3–6 ticks (~4 target), with a 35%
edge buffer so a point near the top/bottom gets headroom. Non-negative in normal mode; **negative ticks allowed
in weightless mode** (§9).

---

## 6. Section change (Week ↔ Month ↔ Year ↔ Total) — a fresh poster, instantly

**What you see:** tap MONTH → the month graph appears immediately at the latest window, with the latest point
selected. No "scroll-to-recent" animation.

**How it works:** the chart is keyed `.id(model.period)`. A period switch **remounts a fresh `Chart`** for the
new period, which lands directly at that period's latest window — no cross-period scroll/y animation.

```
tap MONTH
   │  store rebuilds the ChartModel for .month (synchronously, before the remount renders)
   ▼
.id(model.period) changes  ──►  Swift Charts mounts a FRESH chart, already showing month data
```

**Why `.id(period)` is safe but `.id(dataHash)` was not:** this id changes *only* on a period switch — never on
a scroll or a y-settle. So it gives us the clean per-period identity the legacy engine got "for free" from its
four distinct generic view types, **without** reintroducing S1 (a y-settle within a period still animates in
place, no teardown).

**Analogy.** Four separate posters (one per period) on a carousel. Switching periods **rotates a whole new
poster into the frame** — you don't erase and redraw the current one.

There's also a `guard !usesNewWeightEngine` that skips all the *legacy* per-switch machinery (configuring four
section view-models, forcing scroll updates, etc.) for weight — that was pure wasted work once the host owns
period handling.

---

## 7. Points on the day line (not between the lines)

**What you see:** a Wednesday reading sits **on** the "Wed" gridline — not floating between Wed and Thu.

**How it works:** `ChartPrep.plotXDate` places each point at its **local start-of-day (midnight)**:

| Period | Point sits at… |
|--------|----------------|
| Week / Month | that day's midnight (on the day gridline) |
| Year | the 1st-of-month at midnight (on the month gridline) |
| Total | the raw summary date (already midnight) |

The gridlines, week labels, and the scroll's rest positions are **all** at midnight, so point + line + scroll
all coincide. Because aggregation already converted the UTC timestamp to the **local** day, `startOfDay` is
timezone- and DST-correct.

> Earlier the point was offset to **noon** (so it centered *between* the midnight gridlines) — that's the
> "between Wed and Thu" look, now fixed. The legacy engine keeps its noon convention (its gridlines are also at
> noon), so the two engines each draw on their own internally-consistent grid — we deliberately don't unify
> them.

**Analogy.** A calendar where an appointment is pinned to the **start line of the day column**, not floated in
the middle of the cell.

---

## 8. Selection & the floating date — stickers on the glass

**What you see:** tap a point → a vertical crosshair, the point enlarges, the header shows that reading, and
the **date floats above the line** ("jul 9, 2026"). It stays until you scroll.

**How it works:**

```
tap ─► Swift Charts .chartXSelection writes raw x  ─► host snaps to the NEAREST REAL point
                                                       (from fullResolution, so taps always hit a real entry)
    ─► store.selectWeightPoint(at: realDate)
         • sets selectedPoint / selectedXValue / showCrosshair
         • refreshes the metric tiles (§10)
    ─► the VIEW derives the crosshair from the store's validated selection (crosshairDate)
```

Two important details:

- **It persists after you lift your finger.** Swift Charts resets its selection to `nil` on gesture-end; the
  host **ignores that nil** so the crosshair stays (like Apple Health). The store clears the selection on the
  *next* scroll-start instead.
- **The floating date doesn't compress the graph.** It's not a chart annotation (those get clipped or steal
  plot height). Instead a `.chartBackground` publishes the selected x through a `PreferenceKey`, and an
  `.overlayPreferenceValue` draws the label **above the chart's top edge** (`y: -12`), x-clamped so it stays
  on-screen at the edges. And the old redundant date under the big weight number is hidden while selected, so
  the date shows once.

**Analogy.** The poster underneath never changes when you point at it. The crosshair and the floating date are
**transparent stickers placed on the glass** over the poster — put them anywhere, remove them, and the poster
is untouched.

---

## 9. The header value, label & the "average window"

The big number + label above the chart (e.g. **"month average · 32.7 kg"**) is driven entirely by **store
state**, read reactively by the header view. Priority:

1. A point is selected → **that point's exact weight** (label: "day average").
2. No selection → the **visible-window average** (label: "{period} average", e.g. "week average").
3. No entries in the window → "no entries in {period}"; none at all → "no entries".

The window average is an **interpolated** mean across the visible range (Hermite samples over the committed
window), recomputed **on finger-lift**, not mid-drag. Because it's read reactively from the store, tapping a
point or committing a scroll updates it automatically — no separate wiring in the view.

**Analogy.** The header is a **live caption** that reads the author's notes (store state), not something the
frame paints on its own.

---

## 10. Metric tiles + the co-plot line

Two different things share the word "metric":

**(a) The tiles below the chart** (bmi / body fat % / muscle % …). They now **follow the selection**:

```
select a point ─► store pushes THAT point's per-point metrics into the tiles
no selection   ─► tiles show the visible-window average
scroll away    ─► tiles refresh to the new window (in sync with the header)
```

Every plotted point (`BathScaleWeightSummary`) carries its own bmi/fat/muscle values, so the tiles can show the
selected reading. This is wired from `DashboardStore.selectWeightPoint` + `commitWeightScroll` →
`updateMetricsForCurrentView()` (guarded so it runs once per real change, not per frame). *This was a v2 gap —
the header updated on tap but the tiles didn't, because the tiles are an imperatively-updated array, not a
reactive read.*

**(b) The co-plot line.** Tapping a *tile* co-plots that metric as a **second line**, normalized into the
weight y-axis (`buildNormalizedMetricSeriesWithDomain`), toggled by `selectedMetricLabel`. This is orthogonal to
selecting a *point*.

**Analogy.** The tiles are a **read-out panel** wired to whatever the crosshair is pointing at. The co-plot line
is a **second transparency** you can lay over the poster to compare a metric against weight.

---

## 11. The month x-axis — a continuous 7-day stride

**What you see:** month labels read **… 17, 24, 31, 7 …** — a clean 7-day stride, never resetting to "1", with
a solid divider at the month start (Apple Health style).

**How it works:** `monthlyWeeklyTicks` generates a **continuous Sunday-anchored 7-day grid** across the whole
domain (it never restarts at the 1st). The **solid month divider** is drawn separately as a *gridline-only*
mark (`monthBoundaryTicks`) with no tick/label — so it can't sit on top of a Sunday label and hide it.

> The old generator restarted at the 1st every month (`1, 8, 15, 22, 29`), so `29 → 1` was only 2–3 days apart
> and a "1" sat on every boundary. A first fix that *injected* the 1st into the tick array accidentally hid the
> adjacent Sunday label (a tick landed on it), leaving a gap — hence the separate gridline-only divider.

**Analogy.** A tape measure that keeps counting every 7 units straight across a seam, with a **bold line** at
the seam — instead of resetting the numbers at every seam.

---

## 12. Goal chip, weightless, active-month greying (the rest of the parity set)

- **Goal chip (the green "60").** Floated **on the trailing y-axis marks** at the goal's height — a
  `.chartBackground` publishes the clamped goal y-position, and an overlay `.position`s the chip over the y-axis
  number column (`width − yAxisLabelWidth/2`). Clamped into the domain so it stays on-screen when the goal is
  far off. (Earlier it was a `RuleMark` annotation pinned to the plot's inner edge — *left* of the axis
  numbers; now it's on the axis, matching legacy.) *Same overlay technique as the floating date — a sticker on
  the glass.*
- **Weightless mode.** When on, values are plotted as the **difference from an anchor weight**, and negative
  y-ticks are allowed. This flows entirely through the **reused domain math** (`buildWeightSeries` /
  `calculateYAxis` fed `isWeightlessMode`/`anchorWeight`) — no weightless-specific chart code. Parity by reuse.
- **Active-month greying.** In Month view (when not scrolling), points whose date falls *outside* the focused
  calendar month render dimmed — a pure render-time decision from `activeMonthInterval`.

---

## 13. The full lifecycle, start to finish

```
App opens the dashboard
   │  ServiceRegistry / store load account + summaries
   ▼
Skeleton loader shows            (until isGraphReady)
   │
   ▼
Store builds the ChartModel  ──►  WeightChartHost observes it  ──►  WeightChartView draws
   │
   │  Cold start / tab-back / period switch:
   │     selectLatestIfNeeded() seeds the LATEST point as selected (all four periods)
   ▼
User interacts:
   • scroll  → §4 (native slide → commit verbatim → in-place y settle → tiles refresh)
   • tap     → §8 (snap to real point → store selection → crosshair + header + tiles)
   • switch  → §6 (fresh poster via .id(period))
   • tap tile→ §10 (co-plot line)  |  select goal in settings → §12 (goal chip)
```

**Analogy for the whole show:** the store is a **printing press + stage manager**; the host is the **stagehand**
listening to the audience; the view is the **framed poster on a sliding track** with a few transparent stickers
(crosshair, date, goal chip) on the glass. The press prints rarely; the poster slides freely; the stickers move
without touching the print.

---

## 14. The "why it's fast" invariants (memorize these five)

1. **Immutable model in, dumb view out.** The view never derives, sorts, groups, or windows — so it can't go
   stale and can't do work on the hot path.
2. **Prep is event-driven, never per-frame.** The poster is printed on data/period/unit/metric change, not
   during a scroll.
3. **Swift Charts owns the scroll** over one prepared, full-domain (decimated) series — no live re-windowing.
4. **One settle event, one animation.** The y-axis rescales once on `isScrolling → false`, in place
   (`dataFingerprint` unchanged → no rebuild).
5. **Selection/scroll are local `@State`,** synced to the store only at boundaries — no `@Published` write on
   the per-frame path.

If a future change violates one of these (e.g. re-deriving data in the view, or making the y-axis part of the
series fingerprint again), the old symptoms — dots-then-line, hitch, jerk — will come straight back.

---

## 15. Where each thing lives (file cheat-sheet)

| Concern | File(s) | Key symbols |
|---------|---------|-------------|
| Poster type (the model) | `Models/ChartModel.swift` | `ChartModel`, `YAxisModel`, `withYAxisAndTicks` |
| Poster printer | `Managers/Graph/ChartPrep.swift` | `buildWeight`, `plotXDate` |
| Domain math (reused) | `Managers/Graph/GraphRenderingConfiguration.swift` | `fullXDomain`, `boundedXAxisValues`, `monthlyWeeklyTicks`, `formatSelectedDate` |
| Y-axis calc / decimation | `Models/YAxisCalculator.swift`, `Managers/Graph/ChartDecimator.swift` | `calculateYAxis`, `decimate` |
| Safety guard | `Managers/Graph/ChartDomainSanitizer.swift` | `finiteWidth`, `orderedDates`, `positiveLength` |
| Brain / source of truth | `Stores/DashboardStore.swift` | `chartModel`, `selectWeightPoint`, `commitWeightScroll`, `settleWeightChart` |
| Selection lifecycle | `Managers/DashboardGraphManager.swift` | `applyChartSelectionSync`, `handleScrollPhaseChange` |
| Header / metrics | `Managers/DashboardDisplayManager.swift`, `DashboardMetricsManager.swift` | `displayWeight`, `updateMetricsForCurrentView` |
| Stagehand | `Views/Components/WeightChartHost.swift` | `handleSelectionChange`, `crosshairDate`, `selectionDateLabel` |
| The frame (renderer) | `Views/Components/WeightChartView.swift` | `scrollBehavior`, `gridTicks`/`labelTicks`/`monthBoundaryTicks`, the overlays |
| Host seam | `Views/Components/GraphView.swift` | `usesNewWeightEngine` (`productType == .scale`) |

---

## 16. Glossary

- **Poster** — the `ChartModel`: everything drawn, prepared once, immutable.
- **Sticker on the glass** — an overlay drawn *over* the chart (crosshair, floating date, goal chip) that
  doesn't touch the model.
- **Fingerprint** — `dataFingerprint`: a cheap change token from the *series* (count + endpoints), **excluding**
  the y-axis, so a y-settle doesn't look like a data change.
- **Windowed ticks** — only ~dozens of x-axis ticks (±10 windows) handed to Swift Charts at a time, the fix for
  the ~1000-`AxisMarks` scroll hang.
- **Commit verbatim** — recording the native scroll's resting position exactly, with no re-snap/animation, so
  stored == visible.
- **In-place settle** — swapping only `yAxis`/`xAxisTicks` on the model at scroll-end, so Swift Charts re-inks
  the ruler instead of reprinting the poster.
- **Legacy engine** — `BaseGraphView` + the four section view-models; still used by baby/BPM, dead-for-weight
  but not deletable until they migrate too.

---

## 17. Common doubts / clarifications (FAQ)

**Q1. "Before, the y-axis recalculated every time I scrolled; now only at scroll-end?"**
Not quite — in *both* engines the y-axis is **frozen during the drag** and recomputed only when you lift your
finger. The difference is what that recompute *does*, and how much other work happened during the drag:

| | During the drag | At scroll-end (y-axis recompute) |
|---|---|---|
| **Legacy** | re-rendered **every frame** (the `@Published scrollPosition` storm + re-slicing a ±30 window) | recompute changed the `.id` hash → **tore down + rebuilt the whole `Chart`** (via 3 racing timers) → dots-then-line, flash, jerk |
| **v2** | **nothing** (Swift Charts slides the prepared poster) | swaps **only** `yAxis`+`xAxisTicks` in the model (`dataFingerprint` unchanged) → re-inks the ruler **in place**, one animation, **no rebuild** |

So "reprint" = *tear down and re-mount the whole `Chart` view*. Legacy did it on every scroll-end; v2 never
does it on a y-settle.

**Q2. "If the poster is built once, when does it actually get rebuilt — add / delete / unit / weightless?"**
"Build once" means **once per real *input* change, not per frame/scroll.** A real change *does* reprint the
whole model (a fresh `ChartModel` from `ChartPrep`) — that's fine because it's rare and the data is small. The
store watches `rebuildSignal`; these flip it:

| You do this… | Result |
|---|---|
| Add / edit / delete an entry | full rebuild (new points) |
| Change unit (kg ↔ lb) | full rebuild (weights re-converted) |
| Toggle weightless mode | full rebuild (values re-expressed as ±diff from anchor) |
| Change goal / co-plot metric | full rebuild (chip / 2nd line) |
| Switch period | full rebuild **+** `.id(period)` remount |
| **Scroll** | **not** a rebuild — in-place ruler swap only |
| **Drag frame / select / goal chip** | nothing / sticker on the glass |

The trick: the *frequent* events (drag, y-settle) don't rebuild; only the *rare* events do.

**Q3. "1000 entries → do all 1000 points re-render on every rebuild?"**
Two corrections:
- **Entries ≠ points.** The chart plots **aggregated summaries — one point per day** (week/month) or **per
  month** (year/total), never raw entries. 1000 weigh-ins over 2 years → a few hundred daily points at most, a
  couple dozen monthly. Even a 10k-entry account is bounded by *distinct days/months*.
- **A rebuild composes a new model (cheap) and Swift Charts *diffs* it (cheaper).** Composing = a sort+map over
  those few-hundred aggregated points (sub-millisecond, and only on the rare events in Q2). Displaying = Swift
  Charts reconciles the new model against the one on screen and updates only the changed marks — **no teardown,
  no re-mount, no dots-then-line** (because the data change does *not* change `.id`; only a period switch does).
  So adding one entry updates the single new dot in place; it does not repaint 1000 marks.

**Q4. "Legacy 'reprinted the entire poster' — but didn't it only render a windowed slice of points?"**
Right — that's two separate legacy problems. **S1** = the `.id` teardown (rebuilding the whole *view*). **S2/S10**
= the windowing: legacy only kept a ±30-point slice around a *stale* position, so scrolling ran **off the edge**
into coarse/missing points (the "gap"). v2 fixes both: it prints the **full poster — every point across the
whole date range** — and lets Swift Charts slide over the complete thing, so **there's no data gap.** The only
windowing left in v2 is the **x-axis ticks** (±10 windows, a *performance* fix for the ~1000-`AxisMarks`
hang) — that's gridline/label positions, not data. (A very long single fling can briefly outrun the gridlines
until the scroll-end refresh, but the points/line are always present.)

```
              POINTS / line                x-axis TICKS
LEGACY        windowed ±30 → gap           (same tangle)
v2            FULL domain  → no gap         windowed ±10 (perf only, not the data)
```

---

*Grounded in the working tree as of 2026-07-11 (after the goal-chip-on-axis, month-stride, points-on-line,
metric-tile, and dead-code-cleanup changes). If a symbol drifts, re-grep — the invariants in §14 are the stable
part.*

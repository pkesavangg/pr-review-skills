# MOB-1516 — How the unified graph works (weight · BPM · baby, one engine)

> **What this doc is.** A **teaching walkthrough** of the dashboard graph *after* MOB-1516, when **all three
> products — weight, blood pressure (BPM), and baby growth — render through ONE v2 engine** and the legacy
> `BaseGraphView` stack is gone. It explains the whole flow end to end, how the three products *share* the
> engine, and exactly *where each one differs* — data, scrolling, y-axis, selection, colours, reference
> lines/curves, overlays, headers. Written to be read top-to-bottom with analogies + ASCII diagrams.
>
> **Companion:** [MOB-518-weight-graph-how-it-works.md](../MOB-518-weight-graph/MOB-518-weight-graph-how-it-works.md)
> is the deep dive on *why* the v2 engine is fast (the "build once, slide" model + the 5 invariants). This doc
> assumes that and focuses on **how the three products plug into it.** For the migration itself see
> [MOB-1516-implementation-guide.md](MOB-1516-implementation-guide.md).
>
> Grounded in the working tree as of **2026-07-15** (post Phase D). If a symbol drifts, re-grep — the
> invariants in §12 are the stable part.

---

## 0. The one-paragraph mental model

The graph is **one printed poster slid behind a fixed window** (the MOB-518 model), now with **three kinds of
poster**. The store *prints* the right poster for whichever product is active — weight, BPM, or baby — as a
single immutable `ChartModel`, hands it to one dumb renderer, and the renderer slides it. The poster type is
**the same value for all three products**; only its *contents* differ (one line vs three lines vs a line plus
seven reference curves). Everything product-specific is decided **once, at print time**, inside one function
per product (`ChartPrep.buildWeight` / `buildBpm` / `buildBaby`). The renderer never asks "which product is
this?" for the core marks — it just draws the series it's given with the styles baked into the model. That's
the whole idea: **one engine, three recipes.**

---

## 1. The cast — same three objects, now product-agnostic

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│  DashboardStore              "the brain / press"                                    │
│   • owns the DATA (aggregated summaries) for the active product                     │
│   • builds + @Publishes ONE `chartModel` (weight OR bpm OR baby)                    │
│   • owns selection, scroll-commit, y-axis settle, metrics                           │
│   • dispatches on `productType` in rebuildChartModel / settleChart / selectPoint    │
└───────────────────────────────────────────────────────────────────────────────────┘
                    │ @Published chartModel (immutable value)     ▲ reports gestures
                    ▼                                             │
┌───────────────────────────────────────────────────────────────────────────────────┐
│  TrendChartHost              "the stagehand"  (was WeightChartHost)                 │
│   • period-aware wrapper; observes the store's chartModel                           │
│   • holds LOCAL @State scrollX + selectedX                                          │
│   • product-parameterizes: primarySeriesName, selection snap, baby presentation,    │
│     bpmClassification, chart height                                                 │
└───────────────────────────────────────────────────────────────────────────────────┘
                    │ passes model + product-driven inputs down
                    ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│  TrendChartView              "the dumb renderer"  (was WeightChartView)             │
│   • one Swift Charts `Chart`, reads the ChartModel, derives NOTHING                 │
│   • loops orderedSeriesNames (data + reference) + draws referenceLines              │
│   • Swift Charts owns the native scroll; overlays for callouts/goal/crosshair       │
└───────────────────────────────────────────────────────────────────────────────────┘
```

**What changed in MOB-1516:** the host and view lost the word "Weight" and learned three product-neutral
concepts — **reference series** (baby percentile curves), **reference lines** (BPM 120/80), and a few
**injected product inputs** (`bpmClassification`, `horizontalCrosshairValue`, `percentileCalloutText`,
`chartHeight`). The store's four v2 methods were renamed to product-neutral and now `switch` on `productType`.

---

## 2. The seam — how a product reaches the engine

```
DashboardScreen
├─ weight → WeightTrendView ─┐
├─ bpm    → BpmTrendView   ──┤ (each = header/toggle/cards scaffold)
└─ baby   → BabyTrendView  ─┘        │
                                     ▼
                          DashboardTrendView → GraphView.chartView
                                     │
      ┌──────────────────────────────┴───────────────────────────────┐
      │ if isBabySelection && !hasBabyEntries → BabyEmptyGraphView()  │  (baby with no readings)
      │ else → TrendChartHost(dashboardStore:)                        │  (weight / BPM / baby-with-entries)
      └───────────────────────────────────────────────────────────────┘
```

- `GraphView` ([GraphView.swift](../../meApp/Features/Dashboard/Views/Components/GraphView.swift)) is now
  tiny: a skeleton, the under-graph label, and the two-way `chartView` above. The old four `@StateObject`
  section VMs + the per-period `switch` + the legacy `else` branch are **gone** (Phase D).
- `usesNewEngine` is `true` for `.scale`/`.bpm`/`.baby` — i.e. always (it's the vestige of the A/B era; every
  product now uses the engine).
- The **product wrappers** (`WeightTrendView` / `BpmTrendView` / `BabyTrendView`) are unchanged by the
  migration — they still provide the header, metric toggle, and cards *around* the chart; the chart inside is
  now always `TrendChartHost`.

---

## 3. The data pipeline — from readings to a poster (per product)

All three products aggregate their raw entries into the **same value type**, `[BathScaleWeightSummary]`
(one summary per local day for week/month, per month for year/total). The store exposes them as
`continuousOperations`. Then `ChartPrep` prints the product-specific poster:

```
[BathScaleWeightSummary]  (continuousOperations — carries weight, OR systolic/diastolic/pulse, OR baby weight/length)
        │
        │  DashboardStore.rebuildChartModel(scrollPosition:)   ← switches on productType
        ▼
   ┌───────────────────────────── ChartPrep ─────────────────────────────┐
   │  .scale → buildWeight   1 data series ("weight")                     │
   │  .bpm   → buildBpm      3 data series (systolic/diastolic/pulse)     │
   │                         + 2 reference LINES (120 / 80)               │
   │  .baby  → buildBaby     1 data series (weight|baby_height)           │
   │                         + up to 7 reference CURVES (percentiles)     │
   └──────────────────────────────────────────────────────────────────────┘
        │  each builder: convert values → bake plotXDate → sort → decimate →
        │                compute the product's y-axis → windowed x-ticks → fingerprint
        ▼
   ChartModel  (immutable, Sendable)  ── @Published private(set) on DashboardStore ──►  TrendChartView draws
```

Every builder reuses the **same pure domain layer** — `GraphDataPreparer` (series builders),
`GraphRenderingConfiguration` (x-geometry + ticks), `ChartDecimator`, `ChartDomainSanitizer`, and the
per-product scale in `DashboardChartRules` — so the output is byte-identical to what the legacy engine drew;
only the plumbing is unified.

**Files:** [ChartPrep.swift](../../meApp/Features/Dashboard/Managers/Graph/ChartPrep.swift) ·
[DashboardStore.swift](../../meApp/Features/Dashboard/Stores/DashboardStore.swift) (`rebuildChartModel`).

---

## 4. The `ChartModel` — one poster type for all three

```swift
struct ChartModel: Equatable, Sendable {
    let period: TimePeriod
    let productType: EntryType                          // .scale / .bpm / .baby
    let orderedSeriesNames: [String]                    // draw order, back-to-front
    let seriesPoints:  [String: [PlottedGraphSeries]]   // DISPLAY points (decimated)
    let fullResolution:[String: [PlottedGraphSeries]]   // undecimated — selection snapping
    let xDomain: ClosedRange<Date>                      // full scrollable range
    let visibleDomainLength: TimeInterval               // one window's width
    let xAxisTicks: [Date]                              // windowed gridline/label positions
    let goalWeight: Double?                             // weight goal chip (nil for BPM/baby)
    let seriesStyle: [String: ChartSeriesStyle]         // ← MOB-1516: per-series role/width/points
    let referenceLines: [ChartReferenceLine]            // ← MOB-1516: BPM 120/80 (empty for weight/baby)
    let yAxis: YAxisModel                               // domain + ticks + window average
    let dataFingerprint: Int                            // change token, EXCLUDES yAxis (kills S1)
}
```

The two MOB-1516 additions are what make one type serve three products:

- **`ChartSeriesStyle` per series** — `role` (`.data` = line + dots + snap target; `.reference` = line only,
  no dots, not a snap target), `lineWidth`, `showsPoints`. Weight/BPM series are all `.data`; baby percentile
  curves are `.reference`. The renderer loops uniformly and reads the style — it doesn't branch on product.
- **`referenceLines`** — fixed horizontal rules (BPM's AHA 120/80), drawn behind the data. Empty for
  weight/baby.

The MOB-518 fingerprint rule still holds: `dataFingerprint` **excludes the y-axis**, so a scroll-end y-settle
swaps `yAxis`+`xAxisTicks` in place (`withYAxisAndTicks`) without changing series identity → no teardown.

**File:** [ChartModel.swift](../../meApp/Features/Dashboard/Models/ChartModel.swift).

---

## 5. The three products at a glance (what differs)

| Concern | **Weight** (`.scale`) | **BPM** (`.bpm`) | **Baby** (`.baby`) |
|---|---|---|---|
| Data series | 1 (`weight`) | 3 (`systolic`/`diastolic`/`pulse`) | 1 (`weight` or `baby_height`) |
| Reference series | — | — | up to 7 percentile curves (`.reference`) |
| Reference lines | — | 120 / 80 dashed | — |
| Y-axis | adaptive (goal-aware) | adaptive clinical (`bpmScale`, pad 10, step-of-10) | reference-driven, window-adaptive (contains the curve band) |
| Y-settle on scroll | in-place (`withYAxisAndTicks`) | in-place (`settleBpm`) | full rebuild (cheap; no metric co-plot) |
| Colours | brand blue (+ neutral co-plot metric) | sys/dia = AHA-class colour, pulse = neutral | data = baby purple, curves = one neutral |
| Selection snap | day/month gridline (+ Hermite on gaps) | nearest aggregated point | nearest real reading |
| On-select extras | goal chip, active-month greying, metric co-plot | header + sys/dia recolour by AHA class | horizontal crosshair + "NN%" callout |
| Chart height | 265 | 265 | 498 |
| Header (outside chart) | `WeightTrendView` (displayWeight) | `BpmDisplayView` (sys/dia/pulse + AHA) | `BabyTrendView` + percentiles sheet |

Everything in that table is decided in the product's `ChartPrep.build*` (data/curves/axis/styles/refs) or in
`TrendChartHost` (selection snap + injected inputs) — never in the core render loop.

---

## 6. Rendering — one loop, styles baked in

`TrendChartView.body` builds one `Chart {}` ([TrendChartView.swift](../../meApp/Features/Dashboard/Views/Components/TrendChartView.swift)):

```
Chart {
  1. referenceLines  → dashed RuleMark(y:) per line     (BPM only; drawn FIRST = behind)
  2. ForEach orderedSeriesNames:
        style = model.style(for: name)                  // .data or .reference
        colors = DashboardChartStyleProvider.seriesColors(for: name, productType:, bpmClassification:)
        ForEach points:
            LineMark(.monotone, lineWidth: style.lineWidth)
            if style.showsPoints { PointMark(...) }      // reference curves skip this → line only
  3. vertical crosshair RuleMark(x:)                     (when something is selected)
  4. horizontal crosshair RuleMark(y:) + "NN%" annotation (baby only)
}
.chartYScale / .chartXScale / .chartXAxis (windowed ticks) / .chartScrollableAxes / .chartScrollPosition
.overlay: floating date callout (above plot) · goal chip (on the y-axis column, weight only)
.frame(height: chartHeight)                              // 498 for baby, else 265
```

**The colour brain is [DashboardChartRules.swift](../../meApp/Features/Dashboard/Models/DashboardChartRules.swift)** —
`DashboardChartStyleProvider.seriesColors(for:productType:…)` is the single place product colours live:
- `.bpm` → `pulse` neutral; `systolic`/`diastolic` = the selected reading's `AhaPressureClass.color` (passed
  in as `bpmClassification`, so they recolour on selection with **no model rebuild**).
- `.baby` → percentile curves (`isPercentileSeries`) = `statusUtilityPrimary`; the data series = `babyScaleColor` (purple).
- else (weight) → brand blue for `weight`, neutral for the co-plotted metric line.

**Analogy.** The renderer is a print head that draws whatever series list it's handed, in whatever colour the
"colour brain" says for that series name + product. It has no idea what a "blood pressure" is — it just draws
"systolic" in the colour the rules return.

---

## 7. Scrolling + the y-axis settle

**Scrolling is shared and identical for all three** — Swift Charts owns it natively over the full
(decimated) domain, with Apple-Health value-aligned deceleration (`.chartScrollTargetBehavior`), committed
verbatim at scroll-end. Nothing recomputes during a drag. (This is the MOB-518 mechanism, unchanged.)

**The y-axis settle differs per product** (it's the only scroll-position-dependent output):

```
finger up → isScrolling → false → TrendChartHost commits scrollX → DashboardStore.settleChart(scrollPosition:)
     ├─ .scale → recompute adaptive weight y-axis → withYAxisAndTicks (in place)     [+ full rebuild if a metric is co-plotted]
     ├─ .bpm   → settleBpm: recompute bpmScale for the window → withYAxisAndTicks (in place)
     └─ .baby  → full rebuild (buildBaby at the landed window): cheap, no co-plot; curves are full-domain so
                 seriesPoints come out identical → Swift Charts still doesn't rebuild the scroll view
```

In every case the *series + x-geometry stay stable*, so the scroll region never re-lays-out on settle
(the "can't scroll for ~1 s" hitch the legacy engine had). Weight/BPM swap only the axis in place; baby
reprints the (identical) poster with a new axis, which diffs to the same marks.

**Files:** `DashboardStore.settleChart` / `settleBpm` · `ChartPrep.weightYAxis` / `bpmYAxis` ·
`TrendChartHost` scroll-end handler.

---

## 8. Selection — snap, then the store decides

```
tap → Swift Charts .chartXSelection writes raw x → TrendChartHost.snappedSelectionDate(for:in:)
     ├─ .scale → nearest day/month gridline (empty gap ⇒ Hermite-interpolated value in the header)
     ├─ .bpm   → nearest aggregated point (every period)
     └─ .baby  → nearest real reading (every period)
   → DashboardStore.selectPoint(at: snapped)
        • applyChartSelectionSync → sets state.graph.selectedPoint / selectedXValue / showCrosshair
        • .bpm  → displayManager.handleBpmPointSelection(point)   (updates AHA class → sys/dia + header recolour)
        • updateMetricsForCurrentView()                            (tiles / header track the selection)
   → the VIEW derives its crosshair from the store's validated selection (crosshairDate), so it reflects
     taps AND programmatic auto-select, and clears when the store clears (scroll-start)
```

- **The crosshair is store-driven** for all three — `TrendChartHost.crosshairDate` finds the plotted point
  matching the store's `selectedXValue` in `fullResolution[primarySeriesName]` (weight → `weight`, BPM →
  `systolic`, baby → `weight`/`baby_height`), falling back to the gridline for an empty-gap weight selection.
- **Selection persists after finger-lift** (Swift Charts' gesture-end `nil` is ignored) and clears on the
  next scroll-start — Apple-Health behaviour, shared.

**File:** [TrendChartHost.swift](../../meApp/Features/Dashboard/Views/Components/TrendChartHost.swift)
(`snappedSelectionDate`, `crosshairDate`, `babyPresentation`).

---

## 9. Product-specific overlays & headers (stickers on the glass)

None of these touch the poster — they're overlays/annotations driven by store state:

- **Weight** — the **goal chip** floats over the trailing y-axis column at the goal's height
  (`goalWeight` + `goalLabel`, weight-only); **active-month greying** dims out-of-month points in month view;
  a **co-plotted metric** line can be normalized into the weight axis. Header = `WeightTrendView` reading
  `displayManager.displayWeight`.
- **BPM** — no on-chart value callout; the **header `BpmDisplayView`** shows systolic/diastolic (coloured by
  `AhaPressureClass`) + pulse, reading `displayManager.getBpmDisplayValues()` reactively off the store
  selection. `BpmMetricsSection` below the chart (last-3 average + streaks) is **independent of chart
  selection**. The chart's sys/dia lines recolour via the injected `bpmClassification`.
- **Baby** — a **horizontal crosshair** at the selected reading's value + a floating **"NN%" percentile**
  annotation, driven by `TrendChartHost.babyPresentation` → `graphManager.resolveBabySelectionPresentation`
  (interpolated value + WHO/CDC z-score percentile). Header = `BabyTrendView` + the `BabyGrowthPercentilesSheet`.

**Analogy.** The poster underneath is the same for a given product; the goal chip, the crosshair, the "NN%"
tag, and the floating date are transparent stickers on the glass. Different products bring different stickers.

---

## 10. When does the poster get reprinted?

`TrendChartHost.rebuildSignal` (built from `ChartRebuildSignature` + goal) fires a full `rebuildChartModel`
only on **real input changes** — never per frame, never per scroll:

| You do this… | Result |
|---|---|
| Add / edit / delete an entry | full rebuild (`dataChangeRevision` bumps) |
| Switch product (weight↔BPM↔baby) or baby profile | full rebuild (`productType` / `selectedProductItem`) |
| Switch baby metric (weight↔height) or co-plot a weight metric | full rebuild (`selectedMetricLabel`) |
| Change unit / weightless / goal | full rebuild |
| Switch period | full rebuild + `.id(period)` remount |
| **Scroll** | **not** a rebuild — in-place y-axis/tick swap (baby: cheap identical rebuild) |
| Drag frame / tap / goal chip | nothing / sticker on the glass |

**File:** [ChartRebuildSignature.swift](../../meApp/Features/Dashboard/Managers/Graph/ChartRebuildSignature.swift)
(the tokens; relocated here in Phase D so the engine has no legacy dependency).

---

## 11. Where each thing lives (file cheat-sheet)

| Concern | File | Key symbols |
|---|---|---|
| Poster type | `Models/ChartModel.swift` | `ChartModel`, `ChartSeriesStyle`, `ChartReferenceLine`, `YAxisModel` |
| Poster printers (per product) | `Managers/Graph/ChartPrep.swift` | `buildWeight`, `buildBpm`, `buildBaby`, `weightYAxis`, `bpmYAxis` |
| Rebuild tokens | `Managers/Graph/ChartRebuildSignature.swift` | `dataChangeSignature`, `settingsChangeSignature` |
| Colours + scales (per product) | `Models/DashboardChartRules.swift` | `DashboardChartStyleProvider.seriesColors`, `DashboardChartScaleProvider.{weight,baby,bpm}Scale` |
| Domain math (reused) | `Managers/Graph/GraphRenderingConfiguration.swift`, `GraphDataPreparer.swift` | ticks, `visibleDomainLength`, `buildWeightSeries`/`buildBpmChartSeries`, baby via `BabyDashboardChartSupport` |
| Baby percentile math | `Baby/Utils/BabyPercentileGrowthReference.swift`, `BabyDashboardChartSupport.swift` | WHO/CDC z-score curves, `percentileSeries`, `yAxisScale` |
| Baby selection presentation | `Managers/Graph/GraphSelectionPresentationResolver.swift` | `babySelectionPresentation` (value + percentile) |
| Brain / source of truth | `Stores/DashboardStore.swift` | `chartModel`, `rebuildChartModel`, `settleChart`, `settleBpm`, `commitScroll`, `selectPoint` |
| Stagehand | `Views/Components/TrendChartHost.swift` | `primarySeriesName`, `snappedSelectionDate`, `crosshairDate`, `babyPresentation`, `bpmClassification`, `chartHeight` |
| Renderer | `Views/Components/TrendChartView.swift` | series loop, `referenceLines`, crosshairs, overlays |
| Seam | `Views/Components/GraphView.swift` | `chartView` (BabyEmpty / `TrendChartHost`) |
| Product scaffolds | `WeightTrendView` · `BPM/Views/Screens/BpmTrendView` · `Baby/Views/Screens/BabyTrendView` | headers / toggles / cards |

*(The legacy `BaseGraphView` + four `*SectionViewModel`s + `BaseGraphChartContent` + cache managers are
**deleted** — see the migration guide's Phase D.)*

---

## 12. The invariants (memorize these — they keep it fast AND multi-product)

1. **Immutable model in, dumb view out.** The view derives nothing; it reads a prepared `ChartModel`. This is
   what lets one renderer serve three products — it never branches on product for the core marks.
2. **Product logic lives at print time, in one function each** (`buildWeight`/`buildBpm`/`buildBaby`), plus
   the colour/scale rules in `DashboardChartRules`. Add a product = add a builder + a rules branch, not a new
   view.
3. **Prep is event-driven, never per-frame** (`rebuildSignal`). Scrolling recomputes nothing but the axis.
4. **One settle, in place** (`dataFingerprint` excludes the y-axis) — series identity is stable across a
   y-settle, so no teardown, for every product.
5. **Selection/scroll are local `@State`, synced to the store at boundaries only** — the store owns the
   validated selection; the view derives its crosshair from it.

If a future change makes the *view* branch on `productType` for core marks, or folds the y-axis back into the
fingerprint, the old symptoms (dots-then-line, hitch, jerk) and the multi-product cleanliness both regress.

---

## 13. FAQ

**Q1. "How does one renderer draw 1 line (weight), 3 lines (BPM), and 8 series (baby) without branching?"**
It loops `orderedSeriesNames` and reads `seriesStyle[name]` for each. Weight's list has one `.data` name;
BPM's has three; baby's has seven `.reference` curves + one `.data` line. The loop is identical; the *list*
and *styles* differ, and those are baked into the model at print time.

**Q2. "BPM lines recolour when I tap — isn't that a data change (rebuild)?"**
No. The AHA colour is injected into the view as `bpmClassification` (read from the store's selected reading).
It's a cheap colour swap on the same poster — like the date callout or active-month greying — not a model
rebuild. Only the things in §10's table reprint the poster.

**Q3. "Baby has 7 reference curves across the whole date range — how do they scroll smoothly?"**
`buildBaby` samples the WHO/CDC curves across the **full x-domain** (scroll-independent) and marks them
`.reference` (line only). Swift Charts slides them with everything else; nothing re-samples on scroll. (The
one thing to device-verify is week-zoom smoothness at the ~150-point sampling cap — see the migration guide.)

**Q4. "Where did the four period view-models go?"**
Deleted in Phase D. Period is now just `.id(model.period)` on the chart (a fresh mount per period) +
`ChartPrep` building the right window — no per-period classes. All periods for all products flow through the
one `TrendChartHost` + `TrendChartView`.

**Q5. "Baby's y-axis does a full rebuild on settle but weight/BPM swap in place — why the difference?"**
Baby's axis is *reference-driven* (it must contain the percentile band for the window) and baby has no
co-plotted metric, so a full rebuild is both necessary-ish and cheap. Because the curves are full-domain, the
rebuilt `seriesPoints` are identical, so Swift Charts still doesn't tear down the scroll view — only the axis
animates. Weight/BPM have a simpler adaptive axis, so they swap just the axis in place.

---

*If a symbol drifts, re-grep; the §12 invariants are the stable part. Not yet device-verified as of 2026-07-15
— see the migration guide's status table.*

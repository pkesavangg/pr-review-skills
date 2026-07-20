# MOB-1516 — Migrate Baby + BPM graphs to the v2 chart engine, delete legacy `BaseGraphView`

> **What this doc is.** The **implementation-grade** how-to for converging the Blood-Pressure (BPM) and Baby
> growth graphs onto the same v2 chart engine the weight graph already uses (MOB-518), then **deleting the
> legacy `BaseGraphView`** stack. It gives the concrete type changes, function skeletons, and exact per-file
> steps — per phase, top to bottom. Follow it in order.
>
> **Decision on record (Kesavan, 2026-07-15):** goal = **performance + scalability + zero duplication**, so we
> **generalize ONE engine** (not a third parallel renderer). Baby *and* BPM move onto the v2 engine; the legacy
> `BaseGraphView` + four section view-models are then removed.
>
> **Tests are DEFERRED (Kesavan, 2026-07-15):** do **not** write the automated suite as part of this work —
> verify each phase on device + temporary `#if DEBUG` probes. Phase T is a stub at the end for later.
>
> **Companions:** the MOB-518 weight docs in [`../MOB-518-weight-graph/`](../MOB-518-weight-graph/) —
> especially [how-it-works](../MOB-518-weight-graph/MOB-518-weight-graph-how-it-works.md) (engine mental model +
> the "5 invariants") and [v2-engine-design §10](../MOB-518-weight-graph/MOB-518-weight-graph-v2-engine-design.md).

**Scope:** iOS · **Base:** `develop` · **Build:** Dev config only ·
**Branch:** `MOB-1516-me-app-i-os-migrate-baby-bpm-graphs-to-the-v-2-chart-engine-delete-legacy-base-graph-view`.
All file:line refs verified against the working tree on **2026-07-15**. Re-grep symbols if they drift.

---

## STATUS — as built (2026-07-15)

| Phase | State | Commit |
|-------|-------|--------|
| Doc | ✅ committed | `4655272d6` |
| **G0** — generalize engine | ✅ build Dev + `swiftlint --strict` clean | `c17625810` |
| **B** — BPM on v2 engine | ✅ build Dev + `--strict` clean | `fd0cdcabe` |
| **Y** — Baby on v2 engine | ✅ build Dev + `--strict` clean | `9bce32a31` |
| **D** — delete legacy | ✅ app + test target build; `--strict` clean | this commit |
| **T** — tests | deferred (Kesavan) | — |

**Phase D as-built:** deleted 17 legacy source files (`BaseGraphView`(+ChartModifiers), `BaseGraphChartContent`,
the 4 `*GraphView` wrappers, `BaseSectionViewModel` + 4 `*SectionViewModel`s, `SectionViewModelProtocol`,
`BaseGraphViewCacheSupport`/`Manager`, `PagedChartScrollBehavior`, `GraphViewModifier`) + 9 orphaned test
files; relocated the two still-used signature helpers to **`ChartRebuildSignature.swift`** (the only v2→legacy
code dep), salvaged their test → `ChartRebuildSignatureTests`, dropped one section-VM test method, unwired
`GraphView` (now just `BabyEmptyGraphView` / `TrendChartHost`), and banner-superseded `GraphViewFlow.md`.
Managers (`DashboardChartManager`/`GraphManager`) were kept — the v2 host depends on them; their legacy
references were comment-only.

**⚠️ Not device-verified yet** — build + lint pass ≠ behaviour verified. The device parity pass (all three
products; baby needs the most scrutiny) is still owed and gates final sign-off.

### As-built deviations from the plan below (all deliberate, lower-risk)
- **Kept the `goalWeight` field name** (did *not* rename to `goalValue`) — purely cosmetic, avoids churning
  `ChartPrepTests`; BPM/baby pass `nil`.
- **No `assemble()` core extraction** — `buildBpm`/`buildBaby` mirror `buildWeight`'s structure directly
  (each is self-contained + guarded with `swiftlint:disable function_body_length`). Less indirection, same output.
- **Renamed `weightYAxisOperations` → `yAxisWindowOperations`** (shared window-ops helper, reused by weight +
  BPM). Added `ChartPrep.bpmYAxis` for the BPM in-place settle.
- **BPM y-axis is adaptive** (window `bpmScale`, in-place settle via `settleBpm`) — corrected from the earlier
  "fixed clinical" assumption.
- **Baby y-axis: window-adaptive via a full rebuild on settle** (not an in-place swap) — cheap (no metric
  co-plot; curves are full-domain), keeps legacy window-adaptive behaviour. Baby percentile **curves sampled
  across the full domain** at the existing ~150-pt cap (no baby-math change) — ⚠️ verify week-zoom smoothness.
- **Baby "NN%" callout** = a mark `.annotation` on the horizontal crosshair rule (not a floating overlay) —
  ⚠️ verify position on device; nudge if needed.
- `usesNewWeightEngine` → **`usesNewEngine`** (`.scale`/`.bpm`/`.baby`). The legacy `else` branch in
  `GraphView.chartView` is now **unreachable** (Phase D removes it).
- `BabyDashboardChartSupport.heightSeriesName` made non-`private` so `ChartPrep`/`TrendChartHost` can name it.
- Added a **baby branch to `DashboardChartStyleProvider.seriesColors`** (curves → `statusUtilityPrimary`,
  data → `babyScaleColor`); the legacy baby renderer colours via `BaseGraphChartContent.resolveColors`, so it
  is unaffected.

**Build verify (after every phase):**
```bash
cd iOS && xcodebuild build -project meApp.xcodeproj -scheme meApp \
  -destination 'generic/platform=iOS' -configuration Dev -derivedDataPath /tmp/mob1516-dd \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

### MOB-1591 follow-up — empty-baby state unified onto the engine (2026-07-18)

Device verification of the baby chart surfaced that a baby with **no readings** still forked to the
hand-rolled `BabyEmptyGraphView`, which was period-blind (drew `sun…sat` in every section) and skipped the
engine's framing. Fixed by routing the empty state through the engine too (details in the how-it-works doc
§5.1). Three code changes + one follow-on pair of render bugs:

1. **`ChartPrep.buildEmpty(productType:period:scrollPosition:)`** — an empty-skeleton `ChartModel` (no data
   series, no percentile curves, period-correct x-geometry, `yAxis = .placeholder`). x-geometry is
   byte-identical to `buildWeight` with no operations.
2. **`DashboardStore.rebuildChartModel`** — `.baby` + `!hasBabyEntries` dispatches to `buildEmpty(.baby)`
   (does NOT plot the dummy `continuousOperations` summaries or the WHO/CDC curves).
3. **`GraphView.chartView`** — dropped the `BabyEmptyGraphView` fork; it is now unconditionally
   `TrendChartHost`. (`BabyEmptyGraphView` stays for `BabySnapshotCard`, which has no period sections.)
   The empty baby then inherits `hidesYAxis` (reserved y-column, transparent placeholder numbers, hidden
   horizontal gridlines), the 4-edge closed box, the leading inset, and the total label-row reservation —
   i.e. identical framing to an empty weight/BPM chart (only taller, 498).

Two render bugs found in the same pass and fixed:
- **Phantom crosshair on empty baby.** A period-tab switch runs `DashboardChartManager.updateSelectedPeriod`,
  which auto-selects the latest op — and the empty baby's ops are dummies, so the store held a phantom
  `showCrosshair`/selection that the engine drew. Added **`ChartModel.hasReadings`** (a real `.data` series
  exists) and gated the crosshair (`TrendChartHost.crosshairDate`/`babyPresentation` via `modelHasReadings`)
  **and** the under-graph label hide (`GraphView.isShowingSelectionCallout`) on it.
- **Percentile curves bleeding past the trailing rule** into the y-axis label column (the curves are
  full-domain + an out-of-range boundary point; the chart-level `HorizontalEdgeClip` clips to *view* bounds).
  Fixed by applying `HorizontalEdgeClip` a second time **inside `.chartPlotStyle`** so marks clip to the
  *plot* area's L/R edges while the y-axis labels (outside the plot) stay visible.

Build (Dev) + `swiftlint --strict` clean. ⚠️ Device re-verify: empty baby shows no crosshair in any section
with correct per-period axes; populated baby curves stop at the trailing rule with y-labels intact; populated
baby crosshair + "NN%" still work on tap.

---

## 0. Prerequisite — weight sign-off

The v2 weight engine is **live on `develop`** (V6 flip) but **not yet device-signed-off** and its Phase T
suite is incomplete. This migration builds on and hardens it — every generalization step is parity-gated
against current weight behaviour, so weight gets device-verified *as part of this work*. **Do not begin
Phase D (legacy delete) until weight + BPM + baby have all passed device parity** — the delete removes the
fallback.

---

## 1. Where we are today (the seam)

```
GraphView.chartView  (Views/Components/GraphView.swift:244-280)
├─ isBabySelection && !hasBabyEntries → BabyEmptyGraphView()                  (:246-249)
├─ usesNewWeightEngine (productType == .scale, :32-34) → WeightChartHost      (:250-253)   ← v2 engine
└─ else → Week/Month/Year/TotalGraphView → BaseGraphView<SectionVM>           (:254-278)   ← LEGACY (baby + BPM)
```

- **Weight** → v2 engine: [ChartModel](../../meApp/Features/Dashboard/Models/ChartModel.swift) +
  [ChartPrep](../../meApp/Features/Dashboard/Managers/Graph/ChartPrep.swift) +
  [WeightChartView](../../meApp/Features/Dashboard/Views/Components/WeightChartView.swift) +
  [WeightChartHost](../../meApp/Features/Dashboard/Views/Components/WeightChartHost.swift). Store owns
  `@Published private(set) var chartModel` + the scroll/selection lifecycle.
- **Baby + BPM** → shared legacy `BaseGraphView<ViewModel>` (over the four `*SectionViewModel`s) →
  `BaseGraphChartContent`, branched at runtime on `productType == .bpm` / `selectedBabyProfile != nil`.

**Config layer already exists** — [DashboardChartRules.swift](../../meApp/Features/Dashboard/Models/DashboardChartRules.swift):
`DashboardChartStyleProvider.seriesColors(for:productType:theme:bpmClassification:isOutsideMonthInterval:)` (:117-144)
already dispatches colours by product+series; `DashboardChartScaleProvider.weightScale`/`babyWeightScale`/`bpmScale`
(:42-115) already gives the per-product axis. And [WeightChartView:236-264](../../meApp/Features/Dashboard/Views/Components/WeightChartView.swift#L236)
already loops `model.orderedSeriesNames` pulling colour from that provider. **So the engine is ~70% generic;**
the work is (a) drop the "Weight" naming, (b) teach the model+renderer about reference series/lines (baby+BPM),
(c) parameterize the host's selection/snap and the store's `*Weight*` methods by product.

---

## 2. Target architecture — one product-agnostic engine

Preserve the weight engine's **five invariants** (why it's fast — they must survive):
1. **Immutable model in, dumb view out** — the view derives nothing; reads a prepared `ChartModel`.
2. **Prep is event-driven, never per-frame** — rebuilt only on data/period/unit/metric/settle change.
3. **Swift Charts owns the scroll** over one prepared, full-domain (decimated) series set — no live re-windowing.
4. **One settle, one animation** — y-axis rescales once on `isScrolling → false`, in place (`dataFingerprint`
   unchanged → no rebuild).
5. **Selection/scroll are local `@State`**, synced to the store only at boundaries.

```
                 (once per data/period/unit/metric/settle change — NEVER per frame)
[BathScaleWeightSummary]* ─► ChartPrep.build*(…) ─► ChartModel (multi-series + reference series + ref lines)
        │                       • buildWeight  1 data series
        │                       • buildBpm     3 data series + 2 ref lines
        │                       • buildBaby    1 data series + 7 reference curves
        ▼
   DashboardStore.chartModel  (@Published; ONE model, any product)
        ▼
   TrendChartHost  (period-aware; local @State scroll+selection; product-parameterized snap/select)
        ▼
   TrendChartView  (ONE stable Chart; loops orderedSeriesNames + referenceLines; NO product name in the type)
```
\* All three products already aggregate into `[BathScaleWeightSummary]` daily/monthly summaries — prep input
type is **unchanged**.

---

## 3. Concrete API & type changes

### 3.1 `ChartModel.swift` — reference series + reference lines (weight leaves them empty → byte-identical)

```swift
/// How a named series draws. `.data` = real readings (line + dots, decimated, a snap target).
/// `.reference` = an analytic overlay curve (line only, full-domain sampled, NOT a snap target). Baby percentile.
enum ChartSeriesRole: String, Sendable { case data, reference }

struct ChartSeriesStyle: Equatable, Sendable {
    let role: ChartSeriesRole
    let lineWidth: CGFloat        // data → period width (3/2); reference → 1.0
    let showsPoints: Bool         // reference → false
    static let data = ChartSeriesStyle(role: .data, lineWidth: 3, showsPoints: true)
}

/// Small colour role → theme token, resolved in the view (keeps ChartModel free of SwiftUI.Color / theme).
enum ChartReferenceLineColor: Sendable { case bpmReference }   // extend if baby ever needs one

/// A fixed horizontal rule (BPM 120/80). weight/baby → [].
struct ChartReferenceLine: Equatable, Sendable {
    let value: Double
    let dashed: Bool
    let color: ChartReferenceLineColor
}

struct ChartModel: Equatable, Sendable {
    // …existing fields (period, productType, orderedSeriesNames, seriesPoints, fullResolution,
    //   xDomain, visibleDomainLength, xAxisTicks, yAxis, dataFingerprint)…

    let goalValue: Double?                              // RENAMED from goalWeight (nil for BPM/baby)
    let seriesStyle: [String: ChartSeriesStyle]         // missing name → .data
    let referenceLines: [ChartReferenceLine]            // [] for weight/baby

    func style(for name: String) -> ChartSeriesStyle { seriesStyle[name] ?? .data }

    // withYAxisAndTicks(_:ticks:) — add the three new fields to the copy (they don't change on a settle).
}
```
> **Rename note:** `goalWeight` → `goalValue` touches `ChartPrep.buildWeight`, `WeightChartView` (`clampedGoalValue`,
> `goalChipY`), and `ChartModel.withYAxisAndTicks`. Pure rename, no behaviour change.

### 3.2 `ChartPrep.swift` — shared core + `buildBpm` + `buildBaby`

Extract the parts `buildWeight` already does into a shared helper so all three builders share the x-geometry /
fingerprint / decimation exactly (parity + no duplication):

```swift
/// Shared assembly used by every product builder. `dataSeries` are real readings (sorted, plotXDate baked,
/// decimated, snap targets). `referenceSeries` are analytic overlays (already sampled across the full domain).
private static func assemble(
    period: TimePeriod, productType: EntryType, scrollPosition: Date,
    operations: [BathScaleWeightSummary],
    dataSeries: [(name: String, points: [PlottedGraphSeries], width: CGFloat)],
    referenceSeries: [(name: String, points: [PlottedGraphSeries])],
    referenceLines: [ChartReferenceLine],
    yAxis: YAxisModel, goalValue: Double?,
    config: GraphRenderingConfiguration
) -> ChartModel {
    // ordered names = data (in given order) + reference; seriesStyle per role;
    // full = data(full) + reference; decimated = ChartDecimator.decimate on each;
    // xDomain = config.fullXDomain(...); visibleLength = config.visibleDomainLength(for:);
    // xAxisTicks = config.boundedXAxisValues(...); fingerprint over DATA series only (reference curves are
    //   deterministic from profile/unit, already covered by rebuildSignal → keep them out of the identity token).
}
```

**BPM** (`buildBpm`) — reuse verbatim:
```swift
static func buildBpm(operations: [BathScaleWeightSummary], period: TimePeriod, scrollPosition: Date,
                     calendar: Calendar = .current, config: GraphRenderingConfiguration = .init()) -> ChartModel {
    let preparer = GraphDataPreparer()
    // buildBpmChartSeries already aggregates (daily wk/mo, monthly yr/total) + emits systolic/diastolic/pulse.
    let raw = preparer.buildBpmChartSeries(from: operations, period: period)   // GraphDataPreparer.swift:106
    let byName = Dictionary(grouping: raw, by: \.series)
    let plotCal = localGregorian(from: calendar)
    func plotted(_ name: String) -> [PlottedGraphSeries] {
        (byName[name] ?? []).map { PlottedGraphSeries(original: $0, xDate: plotXDate($0.date, period: period, calendar: plotCal)) }
                            .sorted { $0.xDate < $1.xDate }
    }
    let scale = DashboardChartScaleProvider.bpmScale(from: operations)          // DashboardChartRules.swift:83
    return assemble(
        period: period, productType: .bpm, scrollPosition: scrollPosition, operations: operations,
        dataSeries: [("systolic", plotted("systolic"), 3),                     // draw order PINNED here
                     ("diastolic", plotted("diastolic"), 3),
                     ("pulse", plotted("pulse"), 3)],
        referenceSeries: [],
        referenceLines: [.init(value: Double(BpmConstants.normalSystolic), dashed: true, color: .bpmReference),
                         .init(value: Double(BpmConstants.normalDiastolic), dashed: true, color: .bpmReference)],
        yAxis: YAxisModel(domain: scale.domain, ticks: scale.ticks, average: scale.average),
        goalValue: nil, config: config)
}
```

**Baby** (`buildBaby`) — reuse the percentile math; **sample curves across the FULL domain** (not the visible
window) so they slide with the poster (replaces the legacy per-scroll windowing):
```swift
static func buildBaby(operations: [BathScaleWeightSummary], period: TimePeriod, scrollPosition: Date,
                      babyProfile: BabyProfile, metric: BabyMetric,
                      convertWeight: @escaping (Double) -> Double,
                      convertDecigramsToDisplay: @escaping (Int) -> Double,
                      calendar: Calendar = .current, config: GraphRenderingConfiguration = .init()) -> ChartModel {
    let plotCal = localGregorian(from: calendar)
    guard let fullDomain = config.fullXDomain(for: period, from: operations) else { /* empty model */ }
    let curveRange = fullDomain            // ⚠️ full domain, NOT babyChartVisibleDateRange() — see callout below

    // data series + reference curves + y-axis, per metric:
    let (dataName, dataRaw, refRaw, scale): (String, [GraphSeries], [GraphSeries], YAxisScale)
    switch metric {
    case .weight:
        dataName = DashboardStrings.weight
        dataRaw  = /* reuse the weight builder: preparer.buildWeightSeries(...) OR generateChartData path */
        refRaw   = BabyDashboardChartSupport.percentileSeries(for: babyProfile, dateRange: curveRange,
                                                              convertDecigramsToDisplay: convertDecigramsToDisplay)
        scale    = BabyDashboardChartSupport.yAxisScale(for: operations, babyProfile: babyProfile,
                     dateRange: curveRange, convertStoredWeightToDisplay: { convertWeight(Double($0)) },
                     convertDecigramsToDisplay: convertDecigramsToDisplay)
    case .height:
        dataName = "baby_height"
        dataRaw  = BabyDashboardChartSupport.heightSeries(from: operations)
        refRaw   = BabyDashboardChartSupport.heightPercentileSeries(for: babyProfile, dateRange: curveRange)
        scale    = BabyDashboardChartSupport.heightYAxisScale(for: operations, babyProfile: babyProfile, dateRange: curveRange)
    }
    func plot(_ s: [GraphSeries]) -> [PlottedGraphSeries] {
        s.map { PlottedGraphSeries(original: $0, xDate: plotXDate($0.date, period: period, calendar: plotCal)) }
         .sorted { $0.xDate < $1.xDate }
    }
    let refByName = Dictionary(grouping: refRaw, by: \.series)   // 7 curves: "baby_percentile_5th"…"95th"
    let refStyle  = ChartSeriesStyle(role: .reference, lineWidth: 1, showsPoints: false)
    return assemble(
        period: period, productType: .baby, scrollPosition: scrollPosition, operations: operations,
        dataSeries: [(dataName, plot(dataRaw), 3)],
        referenceSeries: BabyPercentileLine.allCases.compactMap {
            let name = "baby_percentile_\($0.rawValue)"
            return refByName[name].map { (name, plot($0)) }        // [] when isSexWithheld → no curves
        },
        referenceLines: [],
        yAxis: YAxisModel(domain: scale.domain, ticks: scale.ticks, average: scale.average),
        goalValue: nil, config: config)
    // NB: give reference series the refStyle in `assemble` (role .reference, width 1, no points).
}
```

> **⚠️ Baby curve-sampling fidelity (the one baby-specific design call).** Legacy sampled the curves across the
> *visible window* (`babyChartVisibleDateRange`), so zoomed-in weeks got dense curves. v2 samples across the
> *full domain* so the curves are scroll-independent (invariant #3). `BabyPercentileGrowthReference` internally
> caps to ~150 points **per dateRange** — across a multi-year full domain that under-samples a week. **Fix:**
> raise the sampling cadence to a fixed step (e.g. one point per day-of-life, or per week for wk/mo and per
> month for yr/total) since the curves are cheap analytic values; then `ChartDecimator` trims for the plot.
> Device-verify a week view still shows smooth curves. This is the item most likely to need a tweak — build it
> first when you start Phase Y.

### 3.3 `DashboardStore.swift` — rename the 4 v2 methods + dispatch by product

Current bodies (weight-only): `rebuildWeightChartModel` :899, `settleWeightChart` :926, `commitWeightScroll`
:965, `selectWeightPoint` :978. Generalize:

```swift
func rebuildChartModel(scrollPosition: Date) {
    switch productType {
    case .scale: chartModel = ChartPrep.buildWeight(/* existing args, :900-910 */)
    case .bpm:   chartModel = ChartPrep.buildBpm(operations: continuousOperations,
                                                 period: state.graph.selectedPeriod, scrollPosition: scrollPosition)
    case .baby:  guard let p = selectedBabyProfile else { chartModel = nil; return }
                 chartModel = ChartPrep.buildBaby(operations: continuousOperations,
                     period: state.graph.selectedPeriod, scrollPosition: scrollPosition,
                     babyProfile: p, metric: selectedBabyMetric,
                     convertWeight: goalManager.convertWeightToDisplay,
                     convertDecigramsToDisplay: convertBabyDecigramsToDisplay)
    default: break
    }
}

func settleChart(scrollPosition: Date) {
    // weight: existing in-place path (:926-952). BPM: same in-place path (adaptive bpmScale, no metric co-plot)
    //   → factor the weight body to take a `yAxisProvider` closure so BPM reuses it. baby: reference-driven
    //   axis can shift with the window too → recompute via buildBaby's y-axis and withYAxisAndTicks (or full
    //   rebuild — baby has no metric co-plot, curves are full-domain, so a rebuild is cheap and safe).
}

func commitScroll(landedAt landed: Date) {           // product-agnostic; body identical to commitWeightScroll
    graphManager.updateScrollPosition(to: landed)
    settleChart(scrollPosition: landed)
    displayManager.updateMetricsForCurrentView()
}

func selectPoint(at date: Date?) {
    guard let date else { graphManager.state.clearSelection(); displayManager.updateMetricsForCurrentView(); return }
    graphManager.applyChartSelectionSync(at: date, operations: continuousOperations)  // shared for all products
    if productType == .bpm { displayManager.handleBpmPointSelection(state.graph.selectedPoint) }  // AHA class swap
    displayManager.updateMetricsForCurrentView()
}
```
Keep `@Published private(set) var chartModel` (:48). Add `rebuildSignal` inputs for BPM/baby if any are missing
(baby metric label + profile id are already folded into `dataChangeSignature` / `summaryContentToken`).

### 3.4 `WeightChartView.swift` → `TrendChartView.swift`

- **Rename** the type + file. Update the a11y descriptor title/axis-name strings to per-product
  (`accWeightChartLabel` → switch on `model.productType`).
- **Honour `seriesStyle`** in the series loop (currently :236-264):
  ```swift
  let style = model.style(for: name)
  LineMark(…).lineStyle(StrokeStyle(lineWidth: style.lineWidth))…
  if style.showsPoints { PointMark(…)… }        // reference curves draw NO points
  ```
- **Render `referenceLines`** (port `BpmReferenceLines`) inside the `Chart {}`:
  ```swift
  ForEach(Array(model.referenceLines.enumerated()), id: \.offset) { _, line in
      RuleMark(y: .value("ref", line.value))
          .lineStyle(StrokeStyle(lineWidth: 1, dash: line.dashed ? [4,4] : []))
          .foregroundStyle(theme.textSubheading.opacity(0.4))   // map line.color → token
  }
  ```
- **New optional inputs** (all `nil` for weight, wired by the host):
  - `bpmClassification: AhaPressureClass?` → pass into `DashboardChartStyleProvider.seriesColors(…, bpmClassification:)`.
  - `horizontalCrosshairValue: Double?` → baby: a horizontal `RuleMark(y:)` crosshair.
  - `percentileCalloutText: String?` → baby: the floating "NN%" callout (reuse the same overlay-preference
    technique as the date callout, :349-387).
- `goalLabel`/`activeMonthInterval` already `nil`-guarded → BPM/baby pass `nil`.

### 3.5 `WeightChartHost.swift` → `TrendChartHost.swift`

- **Rename** the type + file; update `GraphView` refs.
- **`primarySeriesName`** from `model.productType` (replaces the hardcoded `DashboardStrings.weight` at :151,
  :210, :284, :298): weight→`DashboardStrings.weight`; BPM→`"systolic"`; baby→`selectedBabyMetric == .height ? "baby_height" : DashboardStrings.weight`.
- **Store calls** → the renamed product-agnostic ones (`rebuildChartModel`/`commitScroll`/`selectPoint`, §3.3).
- **Selection dispatch** in `handleSelectionChange` / `snappedSelectionDate`:
  - weight — unchanged (day/month gridline snap + Hermite).
  - BPM — snap to nearest aggregated point (the store's `applyChartSelectionSync` already hit-tests; the host
    just needs the nearest plotted x from `model.fullResolution["systolic"]`).
  - baby — snap to nearest plotted point; then read
    `graphManager.resolveBabySelectionPresentation(...)` → `BabyGraphSelectionPresentation { crosshairDate,
    crosshairValue, percentile }` and pass `horizontalCrosshairValue = crosshairValue`,
    `percentileCalloutText = percentile.map { "\($0)%" }` into the view.
- **Baby chrome:** `chartContent` frame height 498 (vs 265) when `.baby`; suppress interior grids (a view flag).
- Forward `bpmClassification = displayManager.getBpmDisplayValues()?.classification` when `.bpm`.

### 3.6 `GraphView.swift` — the seam

```swift
private var usesNewEngine: Bool {                       // was usesNewWeightEngine (:32-34)
    switch dashboardStore.productType { case .scale, .bpm: return true
    case .baby: return dashboardStore.hasBabyEntries                 // empty baby → BabyEmptyGraphView
    default: return false }
}
// chartView (:244-280): BabyEmpty (no entries) → TrendChartHost (scale/bpm/baby-with-entries).
// The period-switch guard already `guard !usesNewWeightEngine` (:121) → widen to !usesNewEngine.
```
Re-point `BabyTrendView` / `BpmTrendView` scaffolds (headers/toggles/sheets) at the host — they wrap
`DashboardTrendView → GraphView`, so no change beyond `usesNewEngine` picking them up.

---

## 4. Per-product parity checklists

### 4.1 BPM (easy)
- 3 series `systolic`/`diastolic`/`pulse` via `buildBpmChartSeries`
  ([GraphDataPreparer.swift:106](../../meApp/Features/Dashboard/Managers/Graph/GraphDataPreparer.swift#L106)),
  daily wk/mo · monthly yr/total. **Pin order** sys→dia→pulse.
- Colours: sys+dia = selected/window reading's `AhaPressureClass.color`; pulse = neutral. Already in
  `seriesColors` (:125-131).
- **Adaptive** y-axis via `bpmScale` (:83-114) — pad 10, step-of-10, 40–200 empty fallback; re-settles on scroll
  (in-place). No `lastScale`, ignores `chartHeight`.
- 2 dashed reference lines 120/80 (`BpmConstants`), width 1, dash [4,4], `textSubheading.opacity(0.4)`.
- **No** goal/weightless/greying/metric co-plot.
- Selection: crosshair + date callout on chart; header (`BpmDisplayView`) numbers + sys/dia colour swap via
  `getBpmDisplayValues()` reacting to `state.graph.selectedPoint`. `BpmMetricsSection` (last-3 + streaks) is
  **independent of selection** — leave it.
- **Out of scope, keep compiling:** `BpmSnapshotCard` mini chart (uses `bpmScale` directly, not `BaseGraphView`).

### 4.2 Baby (hard — drives §3.1)
- 8 series: 1 data (`weight`/`baby_height`) + 7 reference curves (`baby_percentile_5th`…`95th`,
  `BabyPercentileLine.allCases`). Curves: line-only, width 1, single colour `statusUtilityPrimary`. No per-curve
  labels (don't add unless asked).
- Curves **full-domain, scroll-independent** (see §3.2 ⚠️ sampling callout). Replaces
  `PercentileChartWindowing` + `SortedArrayIndex` + `babyChartVisibleDateRange()`.
- Percentile math reused verbatim: `BabyPercentileGrowthReference` (weight, real WHO/CDC JSON + z-table), modeled
  height curves, `BabyGrowthPercentileZTable`. `isSexWithheld` → no curves.
- Reference-driven dual y-axis: `BabyDashboardChartSupport.yAxisScale` (weight, widened by `expandedWeightScale`)
  / `heightYAxisScale` (height, 5-inch steps). Baked into `model.yAxis`.
- Dual metric via shared `state.ui.selectedMetricLabel` (`"Height"` ↔ nil) → rebuilds the model.
- Baby-only overlays: horizontal crosshair + "NN%" callout (non-total) via `resolveBabySelectionPresentation`.
- Chrome: 498 pt height, baby purple (`ColorTokens.babyScale`), no interior y-grid / no solid x-grid,
  `babyGrowthChartCalloutDateStyle` env flag. `BabyTrendView` scaffold + `BabyGrowthPercentilesSheet` stay.

---

## 5. Phased plan (ordered commits on the ONE branch; device-verify each)

> Single working PR on this branch. **No committed tests until Phase T (deferred).** Verify each phase on device
> + temporary `#if DEBUG` probes (remove before commit). Each phase keeps visible output identical (parity).

### Phase G0 — Generalize the engine (weight stays byte-identical) ★ riskiest, first · ✅ DONE (`c17625810`)
Files: `ChartModel.swift` (§3.1), `ChartPrep.swift` (extract `assemble` core, §3.2), `WeightChartView`→`TrendChartView`
(§3.4), `WeightChartHost`→`TrendChartHost` (§3.5, rename + `primarySeriesName` plumbing only), `DashboardStore`
(rename 4 methods, weight dispatch only, §3.3), `GraphView` refs.
**Gate:** full weight QA (all four periods; no dots-then-line; finger 1:1; one settle; crosshair; goal chip;
greying) **indistinguishable from `develop`.**
Commit: `MOB-1516 Phase G0: generalize v2 engine (Trend* rename, reference series/lines, store dispatch)`

### Phase B — BPM onto the engine · ✅ DONE (`fd0cdcabe`)
Files: `ChartPrep.buildBpm` (§3.2), `DashboardStore` `.bpm` dispatch (§3.3), `TrendChartHost` BPM branch (§3.5),
`TrendChartView` `referenceLines` + `bpmClassification` (§3.4), `GraphView.usesNewEngine` includes `.bpm` (§3.6).
**Gate:** wk/mo/yr/total on a BPM account — 3 lines, 120/80 dashed, tap recolours header + sys/dia, adaptive axis
settles once, scroll 1:1, `BpmMetricsSection` unchanged.
Commit: `MOB-1516 Phase B: BPM chart on the v2 engine`

### Phase Y — Baby onto the engine · ✅ DONE (`9bce32a31`)
Files: `ChartPrep.buildBaby` (§3.2 — **build the curve sampling first**), `DashboardStore` `.baby` dispatch (§3.3),
`TrendChartHost` baby branch + overlays + 498 pt (§3.5), `TrendChartView` horizontal crosshair + "%" callout +
no-grid flag (§3.4), `GraphView` `.baby` routing (§3.6), re-point `BabyTrendView`.
**Gate:** wk/mo/yr/total on a baby account, **both metrics** — 7 curves edge-to-edge + correct values, data line
purple, tap → horizontal crosshair + percentile, sex-withheld → no curves, y-axis contains the band, metric
switch rebuilds cleanly.
Commit: `MOB-1516 Phase Y: Baby growth chart on the v2 engine`

### Phase D — Delete the legacy engine (the branch-name payoff) · ✅ DONE
Only after G0/B/Y device-verified. `grep -rn <symbol> meApp` to confirm zero refs before deleting each:
`BaseGraphView.swift`, `BaseGraphView+ChartModifiers.swift`, `BaseGraphChartContent.swift`,
`BaseGraphViewCacheSupport.swift`, `BaseGraphViewCacheManager.swift`, `Week/Month/Year/TotalGraphView.swift`,
`Week/Month/Year/TotalSectionViewModel.swift`, `BaseSectionViewModel.swift`, `SectionViewModelProtocol.swift`,
`PagedChartScrollBehavior.swift`, `PercentileChartWindowing`+`SortedArrayIndex`, `babyChartVisibleDateRange`,
dead manager paths (`generateBpmChartData`/`generateBabyChartData`/`generateChartDataWithYAxisDomain`,
`DashboardChartManager` cascade + `xAxisValuesWithBuffer`, the 6 caches). Collapse `GraphView.chartView` to
`BabyEmpty`/`TrendChartHost` only (drop the `else` switch + 4 `@StateObject` section VMs + `tearDownAllViewModels`).
Update `meApp/Features/Dashboard/GraphViewFlow.md`.
Commit: `MOB-1516 Phase D: delete legacy BaseGraphView engine`

### Phase T — Tests · **DEFERRED (handle later, per Kesavan)**
Not part of this work. When picked up: golden model parity per period × product (incl. the 7 baby curve values +
edge-to-edge, BPM 3-series + ref lines, reference-driven baby axis); decimation-preserves-shape; settle-once;
`ChartPrep` runs 0× during scroll. Remove all `#if DEBUG` probes then.

| Phase | Risk | Gate |
|-------|------|------|
| G0 generalize | **High** (shipped weight) | weight byte-identical |
| B BPM | Med | BPM parity |
| Y Baby | Med–High | baby parity (both metrics) |
| D delete legacy | Med | full regression, all products |
| T tests | — | **deferred** |

---

## 6. Risks & parity gates
1. **G0 regressing shipped weight** — highest risk. Rename + additive fields only; weight prep output unchanged;
   device-diff vs `develop` before B/Y.
2. **Baby curve fidelity** — the full-domain sampling (§3.2 ⚠️) must render smooth at week zoom AND match legacy
   values; `isSexWithheld` → empty. Build + verify this first in Phase Y.
3. **BPM selection colour** — sys/dia recolour must stay a cheap injected colour swap (no model rebuild), or
   invariant #4 breaks.
4. **`BpmSnapshotCard` + `BpmMetricsSection`** are not the trend chart — keep them untouched/compiling.
5. **Done-when (per product):** wk/mo/yr/total on a large account — no dots-then-line, finger 1:1, one clean
   settle, correct crosshair/overlays, Instruments Animation Hitches **< ~5 ms/s, no frame > 16.7 ms** (now also
   required on a baby percentile account).

---

## 7. Appendix — legacy branch inventory (what G0/B/Y reproduce, then D removes)

**BPM `.bpm` seams:** `DashboardStore` :803 (series), :857-858 (y-axis); `DashboardGraphManager` :246-248,
:366-373; `DashboardChartManager` :295-303 (hit-test), :313-317 (`handleBpmPointSelection`);
`DashboardDisplayManager` :54/:62-65/:68-99; `GraphDataPreparer` :106-124/:550-618; `DashboardChartRules`
:83-114/:122-131; `BaseGraphView` :104/:531/:537-539/:552/:784/:792; `BaseGraphChartContent` :34/:148/:213-235.

**Baby seams:** `DashboardStore` :97-117/:746-752/:815-835/:861-879/:1000-1005/:579-592/:596-603/:1032-1042;
`DashboardGraphManager` :251-290/:551-570; `GraphView` :246-249; `BaseGraphView`
:92-102/:309-336/:411-424/:489-534/:654-663; `BaseGraphChartContent`
:36/:60-61/:78-82/:123-126/:136-142/:166/:195-208/:247-322; `BaseGraphViewCacheSupport` :162/:176;
`DashboardChartRules` :57-81.

**Baby-only files** (`meApp/Features/Dashboard/Baby/`): KEPT & reused (percentile math, styles, models,
`BabyTrendView`/`BabyTrendViewModel`, `GraphSelectionPresentationResolver`); only their plumbing into
`BaseGraphView` is re-pointed at `TrendChartHost`.

*Line numbers verified 2026-07-15 on `develop`. Re-grep symbols if they drift after commits.*

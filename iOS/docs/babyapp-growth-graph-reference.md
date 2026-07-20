# babyApp baby growth graph — how it works (source reference for the meApp iOS port)

> **What this doc is.** A ground-truth reference for how the **baby growth graph works in the legacy
> `babyApp` (Ionic + Angular + D3)** — the percentile math, the growth standard + reference data it's built
> on, how the percentile curves are plotted, the data pipeline, the scales/axes, and the interactions. It
> exists so the **meApp iOS port** (SwiftUI + Swift Charts; see the MOB-1516 / MOB-1591 docs) can be checked
> against the original for clinical + behavioural parity.
>
> **Source:** `~/babyApp` (Ionic/Angular). All file:line refs are to that repo unless noted. Captured
> **2026-07-18**.
>
> **Companion (target):** [`MOB-1516-baby-bpm-graph-migration/`](MOB-1516-baby-bpm-graph-migration/) — the
> iOS unified chart engine the baby graph is being migrated onto. §12 maps babyApp → meApp symbols; **§13
> records the meApp port's percentile-parity status and the MOB-1591 correctness fixes** (weight, the
> now-real length data, and the birthday/edge-age guards).

---

## 0. One-paragraph mental model

The baby graph plots a child's **weight** (or **length**) readings over time as a smooth line, laid over
**seven WHO/CDC-style percentile reference curves** (5th → 95th). Both the reading's *value axis* and the
percentile curves are driven by per-**day-of-life** reference tables, split by **sex** and **metric**. A
tapped point shows its **percentile** ("62 %"), computed by turning the reading into a **z-score** against
that day's mean+SD and looking the z-score up in a standard-normal table. Three Angular services
collaborate: **`ChartDataService`** (data pipeline), **`PercentileService`** (the standard + all math),
**`GraphService`** (the D3 renderer). Units are stored in **decigrams** (weight) and **millimetres**
(length) and converted only for display.

---

## 1. The three services (+ one helper)

| Service | File | Responsibility |
|---|---|---|
| `GraphService` | `src/app/services/graph.service.ts` | The **D3 renderer** — builds the SVG, scales, axes, the data line, the 7 percentile curves, the crosshair/focus, touch handling, transitions. |
| `PercentileService` | `src/app/services/percentile.service.ts` | The **growth standard + all stats** — loads the reference CSVs, computes a reading's percentile (z-score → z-table), and produces the percentile-curve data. |
| `ChartDataService` | `src/app/services/chart-data.service.ts` | The **data pipeline** — pulls per-day entries from SQLite, prepends the birth entry, tracks the active baby DOB, and holds the current chart type/range + the focused (selected) point. |
| `UnitConversionService` | `src/app/services/unit-conversion.service.ts` | Base ↔ display unit conversions (decigrams↔kg/lb, mm↔cm/in). |

`GraphService` depends on all three (constructor, `graph.service.ts:106-110`).

---

## 2. The growth standard & reference data (the clinical core)

### 2.1 Two data sets, four files each

The reference data lives in `src/assets/data/` as **CSV**, split **by sex × metric** — `Girl`/`Boy` ×
`Weight`/`Length`:

**A. Measurement tables — `assets/data/measurements/` (used to compute a reading's percentile)**
`GirlWeightDecigrams.csv`, `GirlLengthMM.csv`, `BoyWeightDecigrams.csv`, `BoyLengthMM.csv`
Columns: **`Day, M, SD`** — for each day-of-life, the population **mean (M)** and **standard deviation (SD)**.

```
Day,M,SD          ← GirlWeightDecigrams.csv (weight in DECIGRAMS)
0,32322,4580
8,33388,4875
15,35693,5118
23,38352,5392
30,40987,5658
```

**B. Percentile-line tables — `assets/data/percentileLines/` (used to draw the 7 curves)**
`GirlWeightDecigramsLine.csv`, `GirlLengthMMLine.csv`, `BoyWeightDecigramsLine.csv`, `BoyLengthMMLine.csv`
Columns: **`Day, 5th, 10th, 25th, 50th, 75th, 90th, 95th`** — pre-computed percentile values per day-of-life.

```
Day,5th,10th,25th,50th,75th,90th,95th   ← BoyLengthMMLine.csv (length in MILLIMETRES)
0,468.750,475.622,487.112,499.890,512.668,524.158,531.030
8,479.846,486.754,498.305,511.150,523.995,535.546,542.454
15,492.008,498.948,510.554,523.460,536.366,547.972,554.912
```

**C. Z-table — `assets/data/measurements/ZTABLE.ts`**
A standard-normal **cumulative-distribution lookup table**: rows are z to one decimal (`-3.4`…`3.4`),
columns are the second decimal (`0.09`…`0.00`), cells are the cumulative probability Φ(z).

### 2.2 Key facts about the data

- **Base units:** weight = **decigrams** (1 dg = 0.1 g), length = **millimetres**. Conversion to kg/lb or
  cm/in happens only at display time.
- **X key is `Day` = day-of-life** (days since birth), sampled at ~**weekly** resolution (0, 8, 15, 23, 30…),
  hence `PercentileService.dataResolution = 7` (`percentile.service.ts:23`).
- **Model is NORMAL (Gaussian), not full LMS.** The measurement tables carry only **mean + SD** — the
  percentile math assumes a normal distribution at each age (z-score → Φ). This is a **simplification of the
  WHO/CDC LMS method** (which adds a skewness parameter **L** via a Box-Cox transform). For weight, which is
  right-skewed in infancy, this is slightly less accurate in the tails than true LMS. *(The exact provenance
  — WHO vs CDC — isn't stated in the code; the day-of-life granularity and 0–~24-month range are consistent
  with WHO child growth standards.)*
- **`sex === 'private'` disables everything percentile:** no point percentile, no curves
  (`percentile.service.ts:40, 55`). Only `'male'` / `'female'` resolve to a data file.

### 2.3 How the four files are indexed

Both loaders read the four files into a fixed-order array (`percentile.service.ts:105-127`):

```
index 0 = Girl Weight     index 1 = Girl Length
index 2 = Boy  Weight     index 3 = Boy  Length
```

Selection is `sex → metric → index` (e.g. `getPercentileFromCSVData` :191-206, `getPercentileLineData`
:67-82).

---

## 3. Computing a reading's percentile (the "62 %" label)

Entry point: `PercentileService.calcMeasurementPercentile(value, type, timestamp)` (`percentile.service.ts:39`).

```
value (decigrams / mm), type ('weight'|'length'), timestamp
        │
        ▼
getPercentileFromCSVData(value, type, timestamp)                       :185
   1. daysSinceBirth = floor(|birthdate − localISO(timestamp)| / 1 day)  (getDaysSinceBirth :47)
   2. pick the sex×metric measurement table (M/SD rows)
   3. find the row(s) whose Day is within ±dataResolution(7) of daysSinceBirth   :209-212
   4. if TWO rows bracket the day → linearly interpolate M and SD between them
      weight = (daysSinceBirth − rowA.Day) / 7 ; M = lerp(Ma,Mb,weight) ; SD = lerp(SDa,SDb,weight)   :214-222
      if ONE row → use it directly ; if NONE → percentile = −1 (unknown)         :223-227
        │
        ▼
calculatePercentile(value, {M, SD})                                    :232
   z = (value − M) / SD                          (getZScore :143)
        │
        ▼
checkZScoreAgainstZTable(z)                                            :147
   • z >  3.49 → 100 ;  z < −3.49 → 0            (clamp the tails)
   • round z to 2 dp, split into row key (0.1) + column key (0.01), look up Φ(z) in ZTABLE
   • percentile% = round((z>0 ? 1−Φ : Φ) · 10000)/100, as an integer                (truncatePercentileDecimal :180)
        │
        ▼
getPercentileSuffix(pct)                                              :243
   • <1 → "< 1st" ; >99 → "> 99th" ; else pct + ordinal suffix (st/nd/rd/th)
```

So the label shown next to a focused point (`graph.service.ts:357-361`) is e.g. `"62 %"` where 62 came from
`62nd`-percentile math (the graph appends `" %"`, the service returns the ordinal string).

**Interpolation detail:** M and SD are linearly interpolated *between the two nearest weekly rows*
(`getAdjustedValue` :237) so a reading on day 11 uses a blend of the day-8 and day-15 tables. This is a
straight-line interpolation of the *parameters*, then a single z-score — not an interpolation of two
percentiles.

---

## 4. Plotting the 7 percentile curves

Renderer: `GraphService.drawPercentileLines(totalDomain, range, type)` (`graph.service.ts:482`), fed by
`PercentileService.getPercentileLineData(...)` (`percentile.service.ts:53`).

### 4.1 Which data, over what date span

- The 7 keys are fixed: **`['5th','10th','25th','50th','75th','90th','95th']`** (`graph.service.ts:492`).
- The curves are sampled over the **total** date span, not the visible window:
  `percentileDomain = getXTimeDomain(sortedData, 'total')` (`graph.service.ts:235`), then
  `getPercentileLineData(now, diff, type, Math.max(120, diff + 60))` (`:498`) — `diff` is the domain width in
  days; the 4th arg (`datePadding`, default 8) extends the curve `datePadding` days past the last reading so
  the lines don't stop short.
- `getPercentileLineData` (`percentile.service.ts:53-92`):
  - guards: no data / no baby / `sex === 'private'` → `[]`.
  - `endDaysSinceBirth = getDaysSinceBirth(endDate) + datePadding`; keep every `*Line.csv` row with
    `Day ≤ endDaysSinceBirth` (`rangeFilter` :63-65).
  - each row's `Day` → a real **date**: `date = birthday + Day·86400000` (:86-89), so the curve's x maps onto
    the calendar.

### 4.2 Turning rows into 7 SVG paths

For each `*Line.csv` row (a `PercentileLinesWeek`), for each of the 7 keys, push a point
`{ x: xScale(week.date), y: yScale(week[key]), key }` (`graph.service.ts:500-508`). Result = 7 arrays of
`{x,y}`, one per percentile.

- **Curve shape:** `d3.line().curve(d3.curveBasis)` — a smooth B-spline (`graph.service.ts:487`). *(The data
  line uses `curveCatmullRom.alpha(0.5)` instead — see §6.)*
- **Draw order:** the curves are `insert('path', '.data-line')` — **inserted behind** the data line
  (`:519`), so readings render on top of the reference band.
- **Styling** (`src/global.scss:467-470`): **all 7 lines share one colour** `--ion-color-tertiary`, stroke
  `2px`. There are per-key CSS classes (`perc-line 5th` … `95th`, `:520/:528`) but they aren't given distinct
  colours — the band reads as one neutral colour, not a rainbow.
- **Hidden when there's nothing to anchor to:** if `y.domain[0]` is `NaN` (no readings) the curves are
  hidden (`graph.service.ts:232-233` → `hidePercentileLines` :534); likewise `sex === 'private'` → `[]` → no
  curves.

---

## 5. The data pipeline (readings → line)

`ChartDataService` (`chart-data.service.ts`):

- **Source = local SQLite** via `getAllItems` (`:150-160`): one row **per local calendar day**
  (`GROUP BY strftime('%Y-%m-%d', localtime)`), taking `MAX(entryTimestamp)` and the metric column
  (`babyDisplayWeightDecigrams` for weight, `babyLengthMillimeters` for length — `getTypeString` :172-178).
  Future rows (> now + 1 h) are excluded.
- **Birth entry** is prepended if not already present (`updateData` :107-123) from the baby record
  (`birthWeightDecigrams` / `birthLengthMillimeters` / `birthDate`, `getBirthEntries` :162-170) with
  `entryId: 'birth'`.
- **`activeBabyDob`** is stored (offset to local time, :92-95) and used by both the graph (domain clamping)
  and the percentile service (day-of-life).
- **Focus (selected point)** is per metric (`weightFocus` / `lengthFocus`); default focus = the **latest**
  entry (`:125-129`). The graph reads/writes it via `getFocus`/`setFocus` (`:70-77`).
- Model type `LineGraphItem { entryId, value, timestamp, percentile?, type? }` (`typings.d.ts:157-163`);
  chart enums `ChartRange = 'week' | 'month' | 'total'`, `ChartType = 'weight' | 'length'` (`typings.d.ts:5,7`).

---

## 6. Scales, axes & domains (`GraphService`)

**Layout:** D3 standard margin convention, margins `{top:65, right:25, bottom:15, left:100}`
(`graph.service.ts:75-80`) — note the **y-axis is on the RIGHT** (`d3.axisRight`, `:643`); the large left
margin is the plot inset.

**X — time scale** (`scaleTime`, `setScales` :615-620, `getXTimeDomain` :702-741):
- **week** → last 7 days (`offset 6`); **month** → last 30 days (`offset 29`); **total** → the data extent.
- If the computed start is **before the baby's DOB**, the domain is clamped to `[DOB, DOB+offset]` (:721-729)
  — so a newborn's graph starts at birth, not before.
- **total** floors: < 60 days of data → pad to 60 days; < 4 entries → pad the end by 6 days (:730-737).
- Points are **snapped to the day** (`d3.timeDay.floor`, `getXValue` :674-677).

**Y — linear scale** (`scaleLinear`, `:622-625`):
- domain = `extent` of the **visible** data's values, then **padded** by `addYDomainPadding` (:830-850): the
  padding grows with the baby's age (min 3-way split, max 12) so a tight cluster of readings doesn't blow up
  to fill the axis.
- **Y ticks** are stepped by the domain span (`getYTickValues` :743-781) — step ranges from 0.25 up to 150
  display-units depending on `diff`; labels show integers only, non-integers render as `'•'`
  (`formatYAxis` :783-792).

**X ticks** are effectively hidden in production (`tickValues([])`, `setAxes` :632-634); the
`getXTickValues` helper (:680-700) exists but is left for debugging.

---

## 7. The data line, points & focus/crosshair

- **Data line** (`drawDataLine` :437-480): `d3.line().curve(curveCatmullRom.alpha(0.5))` — a smoother, more
  "organic" curve than the percentile band's basis spline; `stroke-width 4.5` (5.5 in week view), coloured
  `--gg-weight` / `--gg-length` (`global.scss:441-465`).
- **Points** (`drawPoints` :538-596): one circle per reading; radius by range — **week 6, month 4, total 1**
  (`setPointRadius` :598-613); non-selectable (out-of-range) points shrink to `r=1`.
- **Focus / crosshair** (`focusPoint` :288-377): the selected point enlarges (focus size **12**, or **6** in
  total), and four `focusData` elements update: a vertical line (`focusLineX`), a horizontal line
  (`focusLineY`), a **date label** (`MM/DD/YY`, :343-355) and the **percentile label** (`"NN %"`, :357-370).
  If no point is focused, all crosshair elements hide (:373-374).
- **Touch** (`addTouchListener` :392-435): a transparent full-size rect; **`touchmove`** focuses the nearest
  point by **x-distance only** (scrub), **`touchstart`/tap** focuses the nearest point by **x+y Euclidean
  distance** (:401-424).
- On (re)draw, focus resolves to the previously-focused entry if its timestamp still matches, else the latest
  point, else a `'none'` placeholder (`drawLineGraph` :259-280).

---

## 8. Ranges & transitions

- **week / month** filter `sortedData` to the trailing 6 / 30-day window for *selectable* points, but the
  **line + curves are drawn from the full sorted set** (`drawLineGraph` :168-221). **total** = everything.
- Transition durations adapt: first load `0`, chart-type change `200 ms`, otherwise `150 ms`
  (`drawLineGraph` :197-203); D3 `easeLinear`.

---

## 9. Units (base ↔ display)

- **Base (stored/plotted):** weight = decigrams, length = mm. `normalizeYScale` (:852-860) round-trips metric
  length through cm to normalize.
- **Display:** `convertValueToDisplay` / `convertValueToBase` (:794-828) → kg or lb (decimal) for weight, cm
  or in for length, per `accountMeasurementUnits` (`'metric' | 'imperialLbDecimal' | 'imperialLbOz'`). The
  y-unit label ("kg"/"lb"/"cm"/"in") is set in `setAxes` (:652-670).

---

## 10. Gotchas / notable design choices

1. **Normal model, not LMS** — see §2.2. Any port that must be *clinically identical* to a WHO LMS chart
   would differ in the tails; a port that must match **babyApp** must reproduce the **M/SD + z-table** path.
2. **Percentile curves use the *total* domain**, then are sampled/clipped — not the visible window
   (§4.1). (meApp's v2 engine independently arrived at full-domain curves — see §12.)
3. **`private` sex hides all percentile output** — both the point label and the 7 curves.
4. **Weekly (7-day) reference resolution** with linear M/SD interpolation between rows — not per-day tables.
5. **All 7 curves are one colour** (`--ion-color-tertiary`) — the per-percentile CSS classes exist but are
   unused for colour.
6. **Birth entry is synthetic** (from the baby record), inserted at `entryId: 'birth'` if no reading matches.

---

## 11. File map (babyApp)

| Concern | File | Key symbols |
|---|---|---|
| D3 renderer | `src/app/services/graph.service.ts` | `drawLineGraph`, `drawPercentileLines`, `drawDataLine`, `drawPoints`, `focusPoint`, `setScales`, `getXTimeDomain`, `addYDomainPadding`, `getYTickValues` |
| Percentile math + standard | `src/app/services/percentile.service.ts` | `calcMeasurementPercentile`, `getPercentileFromCSVData`, `getPercentileLineData`, `getDaysSinceBirth`, `getZScore`, `checkZScoreAgainstZTable`, `calculatePercentile` |
| Data pipeline | `src/app/services/chart-data.service.ts` | `updateData`, `getAllItems`, `getBirthEntries`, `getFocus`/`setFocus`, `activeBabyDob` |
| Reference data | `src/assets/data/measurements/*.csv` (`Day,M,SD`) · `src/assets/data/percentileLines/*Line.csv` (`Day,5th…95th`) · `src/assets/data/measurements/ZTABLE.ts` | — |
| Types | `src/typings.d.ts` | `ChartRange`, `ChartType`, `LineGraphItem`, `PercentileLinesWeek` |
| Styling | `src/global.scss` | `.perc-line`, `.data-line`, `.point`, `.focusData` |

---

## 12. Mapping babyApp → meApp iOS (for the port)

The iOS unified engine already mirrors most of this (see the MOB-1516 / MOB-1591 docs). Rough correspondence:

| babyApp (Ionic/D3) | meApp iOS (SwiftUI/Swift Charts) |
|---|---|
| `GraphService` (D3 SVG renderer) | `TrendChartView` (renderer) + `TrendChartHost` (host) |
| `ChartDataService` (SQLite per-day entries, focus) | `DashboardStore` + `EntryService` baby daily/monthly summaries; `hasBabyEntries`; store selection |
| `PercentileService` (standard + math) | `BabyPercentileGrowthReference` (+ `BabyGrowthPercentileZTable`), `BabyWeightPercentileCalculator`, `BabyDashboardChartSupport` |
| Measurement `*.csv` (`Day,M,SD`) — weight **and length** + `ZTABLE.ts` | `BabyPercentileGrowthReference` weight **+ length** measurement entries + z-table (`weightPercentile` / `lengthPercentile`) — see §13 |
| Percentile `*Line.csv` (`Day,5th…95th`) — weight **and length** | `BabyDashboardChartSupport.percentileSeries` (weight) / `heightPercentileSeries` (length) — both real WHO curves (§13) |
| 7 keys `5th…95th` | `BabyPercentileLine.allCases` (`baby_percentile_5th…95th`) |
| `ChartType 'weight'|'length'` | `BabyMetric.weight` / `.height` |
| `curveCatmullRom` (data) / `curveBasis` (curves) | `.interpolationMethod(.monotone)` for both |
| `focusPoint` crosshair + `"NN %"` label | store selection → `TrendChartHost.babyPresentation` → horizontal crosshair + `"NN%"` callout |
| Percentile curves over the **total** domain | `ChartPrep.buildBaby` samples curves across the **full x-domain** |
| `sex === 'private'` → no percentiles/curves | `isSexWithheld` → no curves |
| decigrams / mm base units | same base units; `convertDecigramsToDisplay` / weight+length converters |

**Parity items this doc pins down** (all now verified in meApp — see §13):
- Same **M/SD + standard-normal z-table** path for the point percentile (not full LMS), incl. the **±7-day
  linear M/SD interpolation** and the tail clamps (`>3.49 → 100`, `<−3.49 → 0`).
- The **7 percentile keys** and the **single neutral colour** for all curves.
- **Day-of-life** as the reference key (`birthday + Day·day`), and the **DOB clamp** on the x-domain.
- Curves **drawn behind** the data line; hidden when there are no readings or sex is private.

---

## 13. meApp parity status & MOB-1591 audit fixes (2026-07-18)

A data-correctness audit of the meApp port against this reference found **weight** faithful but
**length/height** fabricated. Both are now at parity on the MOB-1591 branch:

**Weight — full parity (already correct).** Real WHO 2006 M/SD tables + `*Line` curves + a z-table
(`ZTable.json`, byte-identical to Smart Baby's `ZTABLE.ts`), sex-split, day-of-life keyed, linear M/SD
interpolation, `private`/nil sex → no output.

**Length / height — now full parity (FIXED).** meApp previously shipped **weight-only** reference data and
*modeled* the height curves from the baby's birth length (hard-coded growth rates + arbitrary per-percentile
inch offsets) — not WHO length-for-age, and it wouldn't match Smart Baby. Fix:
- Converted Smart Baby's `{Boy,Girl}LengthMM.csv` + `{Boy,Girl}LengthMMLine.csv` → JSON (mm, Int-rounded,
  962 rows, day 0–7305) into `Resources/Data/BabyGrowthPercentile/{measurements,percentileLines}/`.
- `BabyPercentileGrowthReference` gained **`lengthPercentileChartPoints`** (curves) + **`lengthPercentile`**
  (point %), mirroring the weight path exactly — mm→inches for the curves, inches→mm before the z-score.
  Weight + length now share one `chartPoints` core.
- Deleted the modeled helpers (`referenceHeightInchesP50`, `percentileHeightInches`, `heightPercentileOffsets`,
  `resolvedBirthLengthInches`, `interpolatedPercentile`, `percentileValue`).
- Validated: each length **curve** value round-trips to its own percentile (5th→5% … 95th→95%) across ages,
  and the sex split behaves correctly (same length → higher % for girls).

**Missing-birthday guard (FIXED).** `resolvedBirthday` falls back to `today − 84d`; since percentiles are
age-driven, that fabricated a plausible-but-wrong %. Added **`BabyDashboardChartSupport.canResolveGrowthPercentiles(for:)`**
(`birthday != nil && !isSexWithheld`) gating every curve + point-% path → **no curves / no % without a real
birthday**, matching Smart Baby (whose `NaN` day-of-life produces the same). The reading line/value still show.

**Edge-age guard (FIXED).** `interpolatedMeasurement` now returns `nil` once the age is outside
`[firstDay − 7, lastDay + 7]` (Smart Baby's `dataResolution` window) instead of extrapolating off the last
row. No practical effect (tables span ~20y) — a faithfulness/safety guard; applies to weight + length.

**P1 (known, not a bug — pending backend).** meApp stores weight in **lbs**, so the tapped weight % round-trips
lb→kg→decigrams before the z-score (a rounding step Smart Baby avoids by using the raw stored decigrams).
To be simplified once the backend stores weight in decigrams.

**Files:** `Baby/Utils/BabyPercentileGrowthReference.swift` · `Baby/Utils/BabyDashboardChartSupport.swift` ·
`Managers/Graph/GraphSelectionPresentationResolver.swift` · `Baby/Views/Components/BabySnapshotCard.swift` ·
`Resources/Data/BabyGrowthPercentile/**/*LengthMM*.json`.

## 14. MOB-1591 baby-graph UI/behaviour fixes (2026-07-19)

A second pass fixed five behaviour/UI gaps found on-device:

1. **Height tab showed percentile curves with no data.** A baby with only weight readings has no length data,
   yet the Height graph still drew the length percentile curves floating over an empty plot. Now gated per
   metric: `DashboardStore.rebuildChartModel` uses **`hasBabyReadings(for:)`** (weight → any `weight > 0`;
   height → any `babyLengthInches > 0`) instead of the metric-agnostic `hasBabyEntries`, so a metric with no
   real readings renders the empty skeleton (`ChartPrep.buildEmpty`) — parity with Smart Baby, which hides the
   curves when the length dataset is empty (its `NaN` y-domain → `hidePercentileLines()`).

2. **Selected-point % format / capping.** The graph crosshair showed a bare `100%` for a value above the 95th
   (e.g. a 20 lb 3-month-old). Now uses Smart Baby's ordinal + capped form — **`95th %` / `> 99th %` / `< 1st %`**
   — via `BabyWeightPercentileCalculator.percentileDisplayText` (the stray `"95 th"` space was fixed to `"95th"`).
   The growth-percentiles sheet (`BabyTrendViewModel.percentileText`) was routed through the same formatter so
   the graph + sheet agree in-app and both match babyApp (whose focus label AND history list use this format).
   Caps map to Smart Baby's: z-clamp ±3.49 → pct 0/100 → `< 1st` / `> 99th`.

3. **Graph didn't update on add/delete (only on section switch).** Baby summaries live in
   `entryService.$babyDailySummariesByProfile` / `$babyMonthlySummariesByProfile`, which `DashboardDataManager`
   never observes (it binds only the weight/BPM summaries), so `dataChangeRevision` — and thus
   `TrendChartHost.rebuildSignal` — never ticked for baby. Added a `DashboardStore` subscription on those two
   published dictionaries that (baby only) invalidates the ops cache, ticks `dataChangeRevision`, and republishes
   → the v2 chart rebuilds immediately, symmetric with how weight flows through `dataManager.$state`.

4. **Percentile % appeared in a future window.** The `NN%` was an `.annotation(alignment: .trailing)` on the
   horizontal crosshair `RuleMark(y:)`, which spans the FULL scroll domain — so it pinned to the far-right of all
   history, off-screen until the user scrolled there. Now floated at the LEADING edge of the *visible* plot at the
   crosshair's y (parity with the Figma "6%" placement) via `PercentileCalloutPointKey` + an overlay in
   `TrendChartView`, so it stays in the current window at any scroll position.

5. **Segment control (WEEK/MONTH/YEAR/TOTAL) below the fold.** The baby graph height (`babyHeight = 498`,
   faithful to Figma node 26501-377606's ~491 pt) + header + empty-state footer overflowed the viewport, pushing
   the period control off-screen. `DashboardChartLayout.babyHeight(forAvailableHeight:)` now makes the height
   **adaptive** — 498 stays the max on tall phones, shrinking (floored at 320) on shorter viewports so the segment
   control stays visible without scrolling. The viewport height is published from `DashboardScreen` via the
   `dashboardViewportHeight` environment value and read by `TrendChartHost` (live chart) + `GraphView` (skeleton).

**Files:** `Stores/DashboardStore.swift` · `Views/Components/{TrendChartHost,TrendChartView,GraphView}.swift` ·
`Models/DashboardChartLayout.swift` · `Views/Screens/DashboardScreen.swift` ·
`Baby/Utils/BabyWeightPercentileCalculator.swift` · `ViewModels/BabyTrendViewModel.swift`.

## 15. MOB-1591 baby-graph follow-up round 2 (2026-07-19)

On-device testing of §14 surfaced four more issues:

1. **Section switch landed on the wrong week window (baby-only).** Switching Month→Week did not land on the
   week containing the latest entry (e.g. entry Sat Jul 18 → landed on a Jul 18-start window, labels off by one,
   instead of the Sun Jul 12 – Sat Jul 18 week). Weight/BPM were correct. Root cause:
   `DashboardChartManager.updateSelectedPeriod` (and `selectLatestEntryIfNeeded`) computed the scroll position
   from `dataManager.getContinuousOperations(for:)`, which for a baby-only account is the **empty weight cache**
   → `optimalScrollPosition` fell back to `Date()` (today). Fix: a baby-aware period accessor
   `DashboardStore.continuousOperations(for:)` (baby → `babySummaries(for:period:)`, else `dataManager`),
   injected into the manager as `getContinuousOperationsForPeriod` and used at both period-switch sites.
   **This was necessary but not sufficient** (round 3): baby's WEEK render window was the shared `week` (7.15 d),
   whereas weight uses the exact `weightWeekWindow` (7.0 d) — the flat 7 is what makes the value-aligned scroll
   rest on a clean Sunday boundary, so the 0.15-d surplus let week windows start on Saturday (Jun 27–Jul 3 etc.)
   and the section-switch landing still looked off. Fixed by giving `ChartPrep.buildBaby` + `buildEmpty` the
   `weightWeekWindow` for `.week` (matching `buildWeight`). Baby now lands + scrolls exactly like weight/BPM.
   (`initializeChart` was already baby-aware, which is why cold start landed right but a section switch didn't.)

2. **Baby graph height under the tab bar / gap tuning.** `babyHeight(forAvailableHeight:)`'s `reservedChrome`
   went 300 (too big a gap under WEEK/MONTH/YEAR/TOTAL) → 250 (segment control pushed partly UNDER the tab bar)
   → **285** (segments fully visible with a small gap). The viewport extends under the bottom tab bar, so the
   reserve must cover it too. Single tunable dial (higher = shorter graph).

3. **Percentile % placement.** Iterated: §14 leading-edge (drifted off-screen left when scrolled — a frame+offset
   coordinate-space bug); round-2 selected-point (worked but not the desired look); **final (round 3): the LEFT
   edge of the VISIBLE plot, on the horizontal crosshair line** (parity with the Figma "6%"). `percentileCalloutPoint`
   returns `plot.minX + 30` (visible leading edge) at the crosshair y, consumed via `.position` — the same
   mechanism the date callout / goal chip use, which is what keeps it in the current window at any scroll position.

4. **Year/total percentile ≠ week/month for the same reading (real correctness bug).** Percentiles are
   age-driven; year/total plot monthly aggregates collapsed to the 1st, so an Apr-30-DOB baby's 8 lb Jun 24
   reading resolved at age 32 d (Jun 1 → 18th) in year/total but 55 d (Jun 24 → 3rd) in week/month. The real
   entry date IS retained on the monthly summary's `entryTimestamp` (only `.date` is collapsed). Fix: compute
   the percentile against the reading's real date, threaded as a dedicated `percentileDate` so the crosshair
   x-position / value / axis snapping are untouched. Graph path: `TrendChartHost.babyPresentation` derives it
   from `selectedPoint.entryTimestamp` → `resolveBabySelectionPresentation` → `GraphSelectionPresentationResolver`
   (uses `percentileDate ?? crosshairDate`). Sheet path (`BabyTrendViewModel`): `selectedPercentileDate` (selected)
   and `percentileDate(for:)` (per-summary average) — `selectedDate` stays for value lookup. Single-entry months
   are exact. **Multi-reading months: DELIBERATELY use the last entry's age** (the aggregate's `entryTimestamp`
   is the month's max timestamp), decided 2026-07-19 — NOT a bug, do not "fix" it to mean-age / per-entry-%.
   Alternatives considered and rejected: mean-of-entries age (percentile of the shown avg weight at the avg age)
   and average-of-per-entry-percentiles; last-entry age was chosen for simplicity (no aggregation/model change).
   The approximation only affects a month with multiple readings; single-entry months (the common case) are exact.

**Files:** `Managers/DashboardChartManager.swift` · `Managers/DashboardGraphManager.swift` ·
`Managers/Graph/{GraphSelectionPresentationResolver,ChartPrep}.swift` · `Stores/DashboardStore.swift` ·
`Views/Components/{TrendChartHost,TrendChartView}.swift` · `Models/DashboardChartLayout.swift` ·
`ViewModels/BabyTrendViewModel.swift`.

## 16. MOB-1591 empty-state consistency: snapshots + BPM reference lines (2026-07-19)

Empty (no-reading) states were made consistent across the multi-device snapshot cards and the BPM main graph,
using the **baby snapshot as the reference** (its empty state is `BabyEmptyGraphView` — a clean grid with NO
y-axis numbers):

- **Weight & BPM snapshot cards — hide the y-axis numbers when empty.** Both drew default-tick numbers when
  empty (`weightScale`/`bpmScale` fall back to default tick arrays for empty operations), which read as real
  data. The y-axis label is now drawn transparent (`.opacity(0)`, so the reserved column width is unchanged)
  when the card has no readings — `cachedChartPoints.isEmpty` (BPM), `cachedWeekAverage == "--" && no goal`
  (weight; the goal-set case keeps the axis so the goal chip has context, mirroring the main graph's
  `hidesYAxis`). Baby snapshot already hid it, so it's unchanged.
- **BPM main graph — no 120/80 AHA reference lines when empty.** `ChartPrep.buildBpm` now emits
  `referenceLines: []` when there are no series (`orderedNames.isEmpty`); two lone horizontal rules over an
  otherwise-empty skeleton (y-axis already hidden via `hidesYAxis`) read as phantom data. They return with the
  first reading.

**Files:** `Managers/Graph/ChartPrep.swift` · `BPM/Views/Components/BpmSnapshotCard.swift` ·
`Views/Components/WeightSnapshotCard.swift`.

## 17. MOB-1591 snapshot empty-state polish: BPM date range, baby grid insets, shared date label (2026-07-19)

Three follow-ups on the empty (no-reading) snapshot cards, from the stacked weight → BPM → baby empty-state mock:

- **BPM snapshot — always show the week range.** The BPM card had been swapping the date-range slot for
  "no entries" when empty, so the current week (e.g. "jul 19 - jul 25, 2026") never appeared — unlike the
  weight/baby cards, which always show the range. The 00/00 sys/dia + pulse headline already signals empty,
  so the slot now always renders `cachedDateRangeLabel`.
- **Baby empty grid — match the sibling plot insets.** The baby empty state rendered `BabyEmptyGraphView`
  (a hand-drawn grid, leading `spacingSM` / trailing 0, no reserved y-axis column) while weight/BPM render a
  real `Chart` (`.padding(.horizontal, spacingXS)` + a reserved trailing y-axis column). The baby grid was
  pushed right and ran flush to the right edge. Replaced with a new `emptySnapshotChart` — an empty `Chart`
  (single invisible anchor mark, no percentile curves / no data points, transparent y-axis numbers) built from
  the **same** axis + `SnapshotChartPlotBorderView` machinery as the weight/BPM empties, so the leading margin
  and weekday axis line up across all three cards. Uses the raw week bounds (not baby's ±30 min `weekXDomain`
  padding) so the weekday marks match exactly (the padding surfaced an 8th label). `BabyEmptyGraphView` (its
  only remaining caller) was deleted. *(The empty-state y-axis column handling was refined in §18.)*
- **Date label — repeat the month, single-sourced.** The same-month branch collapsed to "jul 19 - 25, 2026";
  it now always repeats the month → "jul 19 - jul 25, 2026" (cross-month/year branches were already correct).
  The `weekDateRangeLabel` formatter had been triplicated across the three cards; it's now one shared
  `DashboardSnapshotLabel.weekRange(start:displayEnd:)` in `DashboardChartRules.swift`, so the three cards
  can't drift.

**Files:** `Models/DashboardChartRules.swift` (new `DashboardSnapshotLabel`) ·
`BPM/Views/Components/BpmSnapshotCard.swift` · `Views/Components/WeightSnapshotCard.swift` ·
`Baby/Views/Components/BabySnapshotCard.swift` (new `emptySnapshotChart`) ·
`Baby/Views/Components/BabyEmptyGraphView.swift` (**deleted**).

## 18. MOB-1591 snapshot empty-state: equal widths + weight always-week label (2026-07-20)

Three more on the stacked empty snapshot cards:

- **Empty graphs were different widths — reserve one identical y-axis column (all three cards).** The empty
  fallback y-scales carry different-digit tick labels — weight `0,25,…,100` ("100"), BPM `40,…,200` ("200"),
  baby `0,10,20,30` ("30") — and the reserved trailing y-axis column is sized to the widest label, so even
  with the numbers hidden baby's 2-digit column was narrower and its plot ran visibly wider. The wanted look
  keeps the column/gap (it frames the plot), just equal across cards. Each empty card now renders a fixed
  transparent 3-digit placeholder — `DashboardSnapshotStyle.emptyYAxisPlaceholder` ("000", `.opacity(0)`) —
  for every y-tick, so the reserved column is byte-identical across the three and **the empty gap matches**.
  Weight keeps the real axis when a goal is set (goal-chip context); numbers return with the first reading.
  *(Supersedes the brief `.chartYAxis(.hidden)` "remove the column" attempt — that equalized width but dropped
  the gap the design wants. Also supersedes the per-card `.opacity(0)` on real ticks from §16/§17, which left
  the columns unequal.)*
- **Tightened the headline→date spacing.** The gap between the headline value and the date-range label was
  `spacingXS` (8); it's now `Layout.rangeLabelTopSpacing` (2) on all three cards, so "no entries" / value /
  date sit closer together.
- **Weight snapshot showed the month/year label — it's a week-only card.** `WeightSnapshotCard` took the
  dashboard's `selectedPeriod` and switched its range label on it (`.month` → "jul 2026"), even though its
  graph is always a 7-day window and its headline is a hard-coded "week average" (BPM/baby take no period).
  So on a month/year dashboard the weight snapshot read "jul 2026" over a week graph. `selectedPeriod` was
  removed from the whole snapshot chain (`DashboardScreen` → `MultiDeviceSnapshotView` → `WeightSnapshotCard`)
  and the card now always renders the week range, matching BPM/baby.

**Files:** `Models/DashboardChartRules.swift` (new `DashboardSnapshotStyle`) ·
`Views/Components/WeightSnapshotCard.swift` · `BPM/Views/Components/BpmSnapshotCard.swift` ·
`Baby/Views/Components/BabySnapshotCard.swift` · `Views/Screens/MultiDeviceSnapshotView.swift` ·
`Views/Screens/DashboardScreen.swift`.

---

*Reference only — babyApp is the legacy source. If a babyApp symbol drifts, re-read the files in §11. For the
iOS target behaviour, the MOB-1516 / MOB-1591 docs are authoritative; §13 is the meApp percentile-parity record.*

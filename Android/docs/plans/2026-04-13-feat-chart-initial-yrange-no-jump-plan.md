---
title: "feat: Eliminate Y-axis range jump on chart initial load"
type: feat
status: active
date: 2026-04-13
origin: docs/brainstorms/2026-04-13-chart-initial-range-jump-fix-brainstorm.md
---

# feat: Eliminate Y-axis range jump on chart initial load

## Overview

On the first frame of chart rendering, Vico's `ScrollAwareRangeProvider` has not yet built
its entry cache (that requires a Canvas draw to emit `ScrollInfo`). During this gap, `getMinY`
and `getMaxY` fall through to the full-dataset extremes — the Y-axis briefly shows a range
spanning all historical data before snapping to the correct visible-window range. Users see
a noticeable axis "jump" on every chart load.

The fix: add `initialMinY`/`initialMaxY` hint fields to `ScrollAwareRangeProvider`. Computed
synchronously at composition time from `segmentState.data` filtered to the initial visible
window, these values seed `getMinY`/`getMaxY` on frame 0 — before the first Canvas draw ever
fires.

## Problem Statement

```
Frame 0:  isCacheReady = false, currentMinY = NaN
          → getMinY() falls through to model.minY (ALL data)
          → Y-axis renders: e.g., 150–220 lbs (entire history)

Frame 1:  LaunchedEffect(model) fires → buildCache → scrollUpdates.first()
          → computeVisibleEntries → generateNiceScale → snapTo(correctRange)
          → Y-axis corrects to: e.g., 170–185 lbs (this week)
                                                    ↑ visible jump here
```

Root cause confirmed in `CartesianChartHost.kt`:
> "No alpha hiding — v3 approach. Brief flash of default range is acceptable."

It is not acceptable. See brainstorm for full three-approach comparison.

## Proposed Solution

Two-part change across the Vico workspace and the app:

**Part 1 — Vico (`ScrollAwareRangeProvider`):** Add `initialMinY`/`initialMaxY` public fields.
Update `getMinY`/`getMaxY` to check them before the Vico intrinsic fallback. Add matching
parameters to `rememberScrollAwareRangeProvider()`.

**Part 2 — App (`ChartConfig` + `ProductChart.kt`):** Add a `getInitialYValues` lambda to
`ChartConfig`. In `rememberProductChart`, compute the initial visible window entries using the
same `GraphUtil.getRollingWindowStart` + `it.getTimeStamp()` filtering pattern already
established in `BaseDashboardViewModel`. Run `generateNiceScale` on those entries synchronously.
Pass the result as `initialMinY`/`initialMaxY`.

## Technical Approach

### Priority chain in `getMinY`/`getMaxY` (after fix)

```
1. isCacheReady && !currentMinY.isNaN()  → animated value   (post-first-scroll, normal operation)
2. !initialMinY.isNaN()                  → hint from app     (frame 0, before cache is built)
3. fallthrough to minY                   → Vico intrinsic    (empty chart, no hint provided)
```

### Vico changes — `ScrollAwareRangeProvider.kt`

```kotlin
// New public fields (alongside existing xRangeMin / xRangeMax)
public var initialMinY: Double = Double.NaN
public var initialMaxY: Double = Double.NaN

// Updated getters
override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
    if (isCacheReady && !currentMinY.isNaN()) currentMinY
    else if (!initialMinY.isNaN()) initialMinY
    else minY

override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
    if (isCacheReady && !currentMaxY.isNaN()) currentMaxY
    else if (!initialMaxY.isNaN()) initialMaxY
    else maxY
```

Updated `rememberScrollAwareRangeProvider` signature:

```kotlin
@Composable
public fun rememberScrollAwareRangeProvider(
    paddingEntries: Int = 1,
    debounceMs: Long = 100L,
    animDurationMs: Int = 300,
    minX: Double = Double.NaN,
    maxX: Double = Double.NaN,
    initialMinY: Double = Double.NaN,   // NEW
    initialMaxY: Double = Double.NaN,   // NEW
    onVisibleEntries: (visibleEntries: List<List<Pair<Double, Double>>>,
                       visibleXRange: ClosedRange<Double>) -> Pair<ClosedRange<Double>, List<Double>>,
): ScrollAwareRangeProvider {
    val callbackRef = rememberUpdatedState(onVisibleEntries)
    val provider = remember(paddingEntries, debounceMs, animDurationMs) {
        ScrollAwareRangeProvider(paddingEntries, debounceMs, animDurationMs) { entries, xRange ->
            callbackRef.value(entries, xRange)
        }
    }
    provider.xRangeMin = minX
    provider.xRangeMax = maxX
    provider.initialMinY = initialMinY   // NEW
    provider.initialMaxY = initialMaxY   // NEW
    return provider
}
```

### App changes — `ChartConfig.kt`

Add the extraction lambda and top-level companion vals for Compose stability:

```kotlin
// Top-level (outside data class) — stable references, no lambda allocation per recomposition
private val weightInitialYValues: (List<PeriodSummary>) -> List<Double> = { data ->
    data.filterIsInstance<PeriodBodyScaleSummary>()
        .map { it.weight }
        .filter { it.isFinite() && it > 0.0 }
}

private val bpInitialYValues: (List<PeriodSummary>) -> List<Double> = { data ->
    data.filterIsInstance<PeriodBpmSummary>()
        .flatMap { listOf(it.avgSystolic.toDouble(), it.avgDiastolic.toDouble(), it.avgPulse.toDouble()) }
        .filter { it > 0.0 }
}

private val babyInitialYValues: (List<PeriodSummary>) -> List<Double> = { data ->
    data.filterIsInstance<PeriodBabySummary>()
        .mapNotNull { it.avgWeightDecigrams?.toDouble() }
        .filter { it > 0.0 }
}

data class ChartConfig(
    val lines: List<LineSpec>,
    val goal: Goal? = null,
    val goalWeight: Double? = null,
    val isWeightlessMode: Boolean = false,
    val hasSecondaryLayer: Boolean = false,
    val useAllSeriesForYRange: Boolean = false,
    val secondaryLineColor: Color? = null,
    val hasPercentileLayer: Boolean = false,
    val percentileBandColor: Color? = null,
    // NEW — null = no pre-warming (graceful fallback to Vico intrinsic range)
    val getInitialYValues: ((List<PeriodSummary>) -> List<Double>)? = null,
)
```

Wire up in `rememberChartConfig` per product branch:
- `MyWeight` → `getInitialYValues = weightInitialYValues`
- `BloodPressure` → `getInitialYValues = bpInitialYValues`
- `Baby` → `getInitialYValues = babyInitialYValues`

> **Note:** `PeriodBodyScaleSummary.weight` is already unit-converted by the time it reaches
> `segmentState.data` (conversion happens upstream in ViewModel). No additional conversion needed.

### App changes — `ProductChart.kt`

Insert before `rememberScrollAwareRangeProvider` call (currently at line 83):

```kotlin
// ── Initial visible-window Y range (eliminates frame-0 jump) ──
// Same timestamp window logic as GraphView.kt initialStartX.
// Established filter pattern: BaseDashboardViewModel line 155.
val initialRange = remember(segmentState.data, segment, config) {
    val getY = config.getInitialYValues ?: return@remember null
    val endTs = segmentState.endTimestamp ?: return@remember null
    val startMs: Long = GraphUtil.getRollingWindowStart(segment, endTs)
        ?: GraphUtil.getStartRange(segment, endTs)
        ?: return@remember null

    val windowEntries = segmentState.data.filter { it.getTimeStamp() in startMs..endTs }
    val yValues = getY(windowEntries)
    if (yValues.isEmpty()) return@remember null

    generateNiceScale(
        minValue = yValues.min(),
        maxValue = yValues.max(),
        goalWeight = config.goalWeight ?: 0.0,
        isWeightLessMode = config.isWeightlessMode,
        targetTickCount = 4,
    )
}

// Updated call — add initialMinY / initialMaxY
val scrollAwareRange = rememberScrollAwareRangeProvider(
    minX = segmentState.chartMinX ?: Double.NaN,
    maxX = segmentState.chartMaxX ?: Double.NaN,
    initialMinY = initialRange?.min ?: Double.NaN,
    initialMaxY = initialRange?.max ?: Double.NaN,
) { visibleSeriesEntries, visibleXRange ->
    // ... existing callback body unchanged
}
```

## Files Changed

### Vico workspace (`/Users/selvakumar/Projects/vico`)

| File | Change |
|------|--------|
| `vico/compose/src/commonMain/.../data/ScrollAwareRangeProvider.kt` | Add `initialMinY`/`initialMaxY` fields + update `getMinY`/`getMaxY` + add params to `rememberScrollAwareRangeProvider` |

### App (`/Users/selvakumar/Projects/meApp/Android`)

| File | Change |
|------|--------|
| `app/src/main/java/.../chart/config/ChartConfig.kt` | Add 3 top-level lambda vals + `getInitialYValues` field to data class + wire per product in `rememberChartConfig` |
| `app/src/main/java/.../chart/ProductChart.kt` | Add `initialRange` remember block before `rememberScrollAwareRangeProvider`; pass `initialMinY`/`initialMaxY` |

## System-Wide Impact

- **`getMinY`/`getMaxY` priority order** changes: new middle tier added. When `initialMinY`
  is `NaN` (default), behaviour is identical to today — no regression for callers that don't
  provide initial hints.
- **`rememberScrollAwareRangeProvider` signature** gains two optional params with `Double.NaN`
  defaults — fully backwards-compatible; all existing call sites compile unchanged.
- **`ChartConfig` data class** gains a nullable function field. Existing construction sites
  without `getInitialYValues` compile fine (default `null`). The `data class` `equals()`
  implementation will compare lambda references — but since the lambdas are top-level `val`s,
  they are singletons, so equality holds correctly.
- **TOTAL segment**: `getRollingWindowStart(TOTAL, ...)` returns `null` → `getStartRange`
  also returns `null` → `initialRange = null` → NaN passed → no change in behaviour.
- **Empty chart / no data in window**: `yValues.isEmpty()` guard → `null` → NaN → falls
  through to Vico intrinsic (existing behaviour). No regression.
- **Subsequent scroll / metric switch**: `ScrollAwareRangeEffect.LaunchedEffect(model)` still
  runs `buildCache` + `snapTo`. Because `snapTo` uses the same `generateNiceScale` on the same
  initial-window data, the transition from `initialMinY` → `currentMinY` is either a no-op
  (values identical) or a tiny instant snap (no tween), invisible to the user.

## Acceptance Criteria

- [ ] Weight chart: no visible Y-axis jump on tab switch or initial app open
- [ ] Blood pressure chart: no visible Y-axis jump (all 3 series correctly seeded)
- [ ] Baby chart: no visible Y-axis jump (percentile + weight data)
- [ ] TOTAL segment unaffected (no `getRollingWindowStart` result → NaN hint → existing behaviour)
- [ ] Empty chart (no data) shows no regression
- [ ] Scroll animation after initial load still works correctly
- [ ] Metric switch (e.g., lbs → kg) still animates correctly (model change triggers `LaunchedEffect(model)` reset)
- [ ] `rememberScrollAwareRangeProvider` without `initialMinY`/`initialMaxY` args compiles and behaves identically to today
- [ ] Build passes: `./gradlew assembleDebug` (app) + Vico workspace builds

## Dependencies & Risks

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| `PeriodBodyScaleSummary.weight` is 0.0 for some entries | Low | `.filter { it > 0.0 }` in lambda |
| `generateNiceScale` called twice per segment change (once for initial, once in callback) | Low | Both are O(n) on visible window only — imperceptible |
| Lambda in `ChartConfig` data class breaks Compose stability inference | Low | Top-level `val` lambdas are stable; `@Stable` annotation on `ChartConfig` if needed |
| `segmentState.endTimestamp` null on very first load before data arrives | Low | `?: return@remember null` guard → NaN hint → graceful fallback |
| Vico upstream merge conflict in `ScrollAwareRangeProvider.kt` | Very low | Only adds new fields at the end of class, no structural change to existing methods |

## Sources & References

### Origin
- **Brainstorm:** [docs/brainstorms/2026-04-13-chart-initial-range-jump-fix-brainstorm.md](../brainstorms/2026-04-13-chart-initial-range-jump-fix-brainstorm.md)
  — Key decisions carried forward: (1) `initialMinY`/`initialMaxY` API extension preferred
  over `alpha(0f)` hide or internal pre-warming; (2) `getInitialYValues` lambda on `ChartConfig`
  for product-specific Y extraction; (3) `isCacheReady` remains the cache gate, initial values
  are hints only.

### Internal References
- `ScrollAwareRangeProvider.kt` — `/Users/selvakumar/Projects/vico/vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/cartesian/data/ScrollAwareRangeProvider.kt`
- `CartesianChartHost.kt` — `ScrollAwareRangeEffect` at line 428; jump comment at line 117
- `ProductChart.kt` — `rememberScrollAwareRangeProvider` call at line 83
- `ChartConfig.kt` — `rememberChartConfig` factory at line 27; `useAllSeriesForYRange` flag
- `BaseDashboardViewModel.kt` — established filter pattern: `data.filter { it.getTimeStamp() in adjMin..adjMax }` at line 155
- `GraphUtil.kt` — `getRollingWindowStart` line 676; `getStartRange` line 626
- `PeriodSummary.kt` — interface + `PeriodBodyScaleSummary`, `PeriodBpmSummary`, `PeriodBabySummary` concrete types
- `docs/brainstorms/FEATURE-1-SCROLL-AWARE-RANGE.md` — original alpha(0f) design that was removed

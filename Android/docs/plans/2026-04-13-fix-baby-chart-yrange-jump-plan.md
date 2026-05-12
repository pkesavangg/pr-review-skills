---
title: "fix: Eliminate Y-axis range jump on baby chart initial load and segment switch"
type: fix
status: completed
date: 2026-04-13
origin: docs/brainstorms/2026-04-13-baby-chart-yrange-jump-fix-brainstorm.md
---

# fix: Eliminate Y-axis range jump on baby chart initial load and segment switch

## Overview

The baby chart is the only product still showing a Y-axis range flash after the MA-3490
`seedMinY`/`seedMaxY` fix. Two independent root causes must be fixed together — fixing only
one still leaves a flash. Both changes are surgical and confined to two files.

## Problem Statement

```
frame 0: segmentState.seedMinY = null → seedMinY = Double.NaN
         Vico falls through to live model Y range
         → shared axis = union(scrollAwareRange, percentileLayer.liveRange)
         → displayed range: 0–40 lbs (all ages, full WHO chart) → FLASH

frame 1: onVisibleEntries fires → correct window range computed
         → Y axis snaps to e.g. 10–16 lbs → visible jump
```

## Root Cause Analysis

(see brainstorm: `docs/brainstorms/2026-04-13-baby-chart-yrange-jump-fix-brainstorm.md`)

### Root Cause 1 — No seed in `updateBabySegmentRanges`

`BabyDashboardViewModel` uses its own `updateBabySegmentRanges()` method rather than the
base `updateSegmentRanges()` because `chartMinX = birthDate` (not first data timestamp).
This custom method was **never updated** to compute or store `seedMinY`/`seedMaxY`, so
`segmentState.seedMinY` is always `null` and `ScrollAwareRangeProvider` falls through to
the Vico intrinsic on frame-0.

### Root Cause 2 — Percentile layer contaminates the shared Y axis

The percentile layer uses `CartesianLayerRangeProvider.fixed(minX, maxX)` with
`alwaysUseLiveRange = true`. The `alwaysUseLiveRange` flag bypasses the range provider
entirely and forces the live model Y range (all 7 WHO percentile bands across all ages).
Vico computes the shared `Axis.Position.Vertical.End` range as the **union** of all
contributing layers — so even with a correct seed on the primary layer, the percentile
layer's wide live range (e.g. 0–40 lbs for ages 0–3 years) expands the displayed axis.

```
displayed Y = union(scrollAwareRange.getMinY..getMaxY, percentileLayer.liveModelMinY..Max)
            = union(10..16 lbs visible window, 0..40 lbs all ages)
            = 0..40 lbs  ← WRONG on frame 0
```

## Proposed Solution

Two targeted changes (see brainstorm **Approach A**):

**Part 1 — `BabyDashboardViewModel.updateBabySegmentRanges`**: Compute
`seedMinY`/`seedMaxY` from `filteredTarget` using `_state.value.selectedMetric` and
`generateNiceScale`, then persist them in `SegmentState`.

**Part 2 — `ProductChart.kt` percentile layer**: Replace the `fixed()` range provider
with a thin `CartesianLayerRangeProvider` delegate that mirrors `scrollAwareRange`'s X/Y
without sharing the `scrollAwareRange` object. Remove `alwaysUseLiveRange = true`.

## Why Not Share `scrollAwareRange` Directly on Percentile Layer

(see brainstorm: resolved questions section)

`ScrollAwareRangeProvider.buildCache` is called with ALL model series at chart level.
Baby pushes percentile bands as layer 0 (7 series) and data as layer 1 (1 series). With
`config.useAllSeriesForYRange = false`, `onVisibleEntries` takes `firstOrNull()` — which
gives the **3rd percentile band**, not baby measurements → wrong Y range. The Y-delegate
avoids this by delegating only Y computation to `scrollAwareRange.getMinY/getMaxY`,
without touching the `onVisibleEntries` series selection logic.

## Technical Approach

### Part 1: Seed in `BabyDashboardViewModel.updateBabySegmentRanges`

Inside the existing `for (segment in segments)` loop, after `filteredTarget` is computed,
add seed calculation mirroring the pattern in `BaseDashboardViewModel.updateSegmentRanges`
(lines 112–124):

```kotlin
// BabyDashboardViewModel.kt — inside updateBabySegmentRanges, in the for(segment) loop
// after: val filteredTarget = entries.filter { ... }

val yValues: List<Double> = when (_state.value.selectedMetric) {
  BabyMetric.WEIGHT -> filteredTarget.mapNotNull { e ->
    e.avgWeightDecigrams?.let { it / 283.495 / 16.0 }
  }
  BabyMetric.HEIGHT -> filteredTarget.mapNotNull { e ->
    e.avgLengthMillimeters?.let { it / 25.4 }
  }
}.filter { it.isFinite() && it > 0.0 }

val seed: Pair<Double, Double>? = if (yValues.isNotEmpty()) {
  val scale = generateNiceScale(
    minValue = yValues.min(),
    maxValue = yValues.max(),
    goalWeight = 0.0,
    isWeightLessMode = false,
    targetTickCount = 4,
  )
  scale.min to scale.max
} else null
```

Then in the `updateSegmentState { it.copy(...) }` block, add:

```kotlin
seedMinY = seed?.first ?: it.seedMinY,
seedMaxY = seed?.second ?: it.seedMaxY,
```

Required import (not yet present in `BabyDashboardViewModel.kt`):

```kotlin
import com.dmdbrands.gurus.weight.features.common.helper.ImprovedNiceScaleCalculator.generateNiceScale
```

### Part 2: Y-delegate range provider in `ProductChart.kt`

Inside the `if (config.hasPercentileLayer && config.percentileBandColor != null)` block,
replace the `percentileRangeProvider` remember block and remove `alwaysUseLiveRange`:

**Before (lines 159–172):**

```kotlin
val percentileRangeProvider = remember(segmentState.chartMinX, segmentState.chartMaxX) {
  CartesianLayerRangeProvider.fixed(
    minX = segmentState.chartMinX ?: Double.NaN,
    maxX = segmentState.chartMaxX ?: Double.NaN,
  )
}
rememberLineCartesianLayer(
  lineProvider = remember(bandLines) { LineCartesianLayer.LineProvider.series(bandLines) },
  verticalAxisPosition = Axis.Position.Vertical.End,
  rangeProvider = percentileRangeProvider,
  markerTargetsEnabled = false,
  alwaysUseLiveRange = true,    // ← REMOVE
)
```

**After:**

```kotlin
val percentileRangeProvider = remember(scrollAwareRange) {
  object : CartesianLayerRangeProvider {
    override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) =
      scrollAwareRange.xRangeMin.takeIf { !it.isNaN() } ?: minX
    override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) =
      scrollAwareRange.xRangeMax.takeIf { !it.isNaN() } ?: maxX
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) =
      scrollAwareRange.getMinY(minY, maxY, extraStore)
    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
      scrollAwareRange.getMaxY(minY, maxY, extraStore)
  }
}
rememberLineCartesianLayer(
  lineProvider = remember(bandLines) { LineCartesianLayer.LineProvider.series(bandLines) },
  verticalAxisPosition = Axis.Position.Vertical.End,
  rangeProvider = percentileRangeProvider,
  markerTargetsEnabled = false,
  // alwaysUseLiveRange removed — delegate mirrors scrollAwareRange Y exactly
)
```

Note: `ExtraStore` import is already present via `CartesianLayerRangeProvider` usage.

## System-Wide Impact

- **`rebuildAllProducers` on metric switch**: When the user switches `WEIGHT → HEIGHT`,
  `rebuildAllProducers()` fires but `updateBabySegmentRanges` does NOT re-run —
  `seedMinY`/`seedMaxY` are stale for one frame until `onYRangeSettled` fires from
  `onVisibleEntries`. This is an acceptable single-frame transition (same behaviour as
  weight/BP unit changes noted in the segment-switch plan).
- **Percentile Y delegate `remember(scrollAwareRange)`**: `scrollAwareRange` is the result
  of `rememberScrollAwareRangeProvider(...)` — its object identity is stable across
  recompositions (Vico creates it once via `remember { ScrollAwareRangeProvider() }`).
  The delegate is created once and reads `getMinY`/`getMaxY` dynamically per frame.
- **`alwaysUseLiveRange` removal**: Removing it was the right call — it was added as a
  workaround to ensure percentile bands span the birth-to-now X range, but the delegate's
  `getMinX`/`getMaxX` delegation to `scrollAwareRange.xRangeMin`/`xRangeMax` (which equal
  `chartMinX`/`chartMaxX`) already provides that.
- **No other products affected**: `config.hasPercentileLayer` is only `true` for Baby.
  Weight and BP charts do not enter the percentile layer branch.
- **`onYRangeSettled` continues to update seed**: The existing `UpdateSeedYRange` dispatch
  from `GraphView` keeps `seedMinY`/`seedMaxY` current after every `onVisibleEntries` fire,
  so segment revisits get the up-to-date settled range.

## Acceptance Criteria

- [ ] Baby chart initial load: no Y-axis range flash — axis shows data-window range immediately
- [ ] Baby chart segment switch (WEEK → MONTH → YEAR → TOTAL): no Y-axis jump for any segment
  that was previously visited; first visit falls back to seed-from-VM or Vico intrinsic
- [ ] WEIGHT metric and HEIGHT metric both behave correctly on initial load and segment switch
- [ ] Metric switch (WEIGHT → HEIGHT): one-frame transition acceptable; no persistent jump
- [ ] Weight chart and Blood Pressure chart: no regression from this change
- [ ] `./gradlew assembleDebug` passes
- [ ] No `!!` operators introduced (detekt enforces this)
- [ ] No `alwaysUseLiveRange = true` remains on the percentile layer

## Dependencies & Risks

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Stale seed on metric switch (WEIGHT → HEIGHT) | Low | `onYRangeSettled` re-fires on model change → seed updated; single-frame gap acceptable |
| `scrollAwareRange` identity changes across recompositions | Very low | Vico's `rememberScrollAwareRangeProvider` wraps object in `remember { }` — identity stable |
| `xRangeMin`/`xRangeMax` not yet populated when delegate is called | Very low | Fallback `?: minX` / `?: maxX` handles the NaN case |
| `rebuildAllProducers` doesn't update seed for metric switch | Known | `onYRangeSettled` keeps seed current; same trade-off as weight unit switch |

## Implementation Checklist

- [ ] **`BabyDashboardViewModel.kt`**: Add `import com.dmdbrands.gurus.weight.features.common.helper.ImprovedNiceScaleCalculator.generateNiceScale`
- [ ] **`BabyDashboardViewModel.kt`**: In `updateBabySegmentRanges` — add `yValues` extraction after `filteredTarget` (metric-aware, unit-converted)
- [ ] **`BabyDashboardViewModel.kt`**: In `updateBabySegmentRanges` — add `seed` computation via `generateNiceScale` (same pattern as `BaseDashboardViewModel` lines 112–124)
- [ ] **`BabyDashboardViewModel.kt`**: In `updateSegmentState { it.copy(...) }` — add `seedMinY = seed?.first ?: it.seedMinY` and `seedMaxY = seed?.second ?: it.seedMaxY`
- [ ] **`ProductChart.kt`**: In `hasPercentileLayer` block — replace `CartesianLayerRangeProvider.fixed(...)` with Y-delegate anonymous `CartesianLayerRangeProvider`
- [ ] **`ProductChart.kt`**: Remove `alwaysUseLiveRange = true` from `rememberLineCartesianLayer` for percentile layer
- [ ] Run `./gradlew assembleDebug` and verify build passes

## Files to Change

| File | Change |
|------|--------|
| `app/src/main/java/.../viewmodel/baby/BabyDashboardViewModel.kt` | `updateBabySegmentRanges` — add Y seed computation (metric-aware) + `seedMinY`/`seedMaxY` in state copy |
| `app/src/main/java/.../chart/ProductChart.kt` | Percentile layer — replace `fixed()` with Y-delegate; remove `alwaysUseLiveRange = true` |

## Sources & References

### Origin

- **Brainstorm document:** [docs/brainstorms/2026-04-13-baby-chart-yrange-jump-fix-brainstorm.md](../brainstorms/2026-04-13-baby-chart-yrange-jump-fix-brainstorm.md)
  — Key decisions carried forward: (1) both root causes must be fixed together; (2) Y-delegate
  pattern (not sharing `scrollAwareRange`) to avoid `onVisibleEntries` series pollution;
  (3) `alwaysUseLiveRange = true` removed — it was the direct cause of Y contamination

### Internal References

- `BabyDashboardViewModel.kt` — `updateBabySegmentRanges` (lines 106–138), `toWeightSeries` (line 186–194), `toHeightSeries` (lines 197–206)
- `BaseDashboardViewModel.kt` — seed computation pattern (lines 112–124) to replicate
- `ProductChart.kt` — percentile layer block (lines 149–172), `scrollAwareRange` definition (lines 86–122)
- `BaseDashboardState.kt` — `SegmentState.seedMinY`/`seedMaxY` fields (added in MA-3490 segment-switch plan)
- Prior plan: [docs/plans/2026-04-13-fix-chart-yrange-jump-segment-switch-plan.md](2026-04-13-fix-chart-yrange-jump-segment-switch-plan.md) — established `seedMinY`/`seedMaxY` pattern for Weight and BP

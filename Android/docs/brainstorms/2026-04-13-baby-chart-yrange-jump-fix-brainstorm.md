---
date: 2026-04-13
topic: baby-chart-yrange-jump-fix
origin: docs/brainstorms/2026-04-13-chart-seed-yrange-in-state-brainstorm.md
---

# Baby Chart Y-Axis Range Jump — Root Cause & Fix

## What We're Building

A targeted fix for the remaining Y-axis range jump on the baby chart (initial load + segment
switch), after the general `seedMinY`/`seedMaxY` fix was applied to Weight and BP charts in
MA-3490.

## Root Cause Analysis

There are **two independent root causes**, both of which must be fixed.

### Root Cause 1 — No seed in `updateBabySegmentRanges`

`BabyDashboardViewModel` uses its own `updateBabySegmentRanges()` method (not the base
`updateSegmentRanges`) because baby's `chartMinX = birthDate` rather than the first data
timestamp. This custom method **never computes or stores `seedMinY`/`seedMaxY`**. As a result:

```
frame 0: segmentState.seedMinY = null → Double.NaN → ScrollAwareRangeProvider
         falls through to Vico intrinsic (full model Y range)
         → axis shows full percentile band range (e.g., 0–40 lbs for all ages) → FLASH
```

The base `updateSegmentRanges` now computes seeds via `getYValuesForSeed`, but `updateBabySegmentRanges` was not updated.

### Root Cause 2 — Percentile layer contaminates the shared Y axis

The baby chart has **two layers sharing `Axis.Position.Vertical.End`**:

| Layer | Range provider | Y range behaviour |
|-------|---------------|-------------------|
| `percentileLayer` | `CartesianLayerRangeProvider.fixed(minX, maxX)` + `alwaysUseLiveRange = true` | Y falls through to live model — spans ALL ages/percentiles (e.g., 3rd–97th across 0–3 years: ~5–40 lbs) |
| `primaryLayer` | `scrollAwareRange` | Y governed by `ScrollAwareRangeProvider` — correct visible-window range |

Vico computes the shared axis Y range as the **union** of all contributing layers.
Even with a correct `seedMinY`/`seedMaxY` on the primary layer, the percentile layer's
wide live range (full WHO chart) expands the displayed axis to cover all ages → flash persists.

```
displayed Y range = union(scrollAwareRange.getMinY..getMaxY,
                          percentileLayer.liveModelMinY..liveModelMaxY)
                 = union(12..16 lbs visible window,  0..40 lbs all ages)
                 = 0..40 lbs  ← WRONG on frame 0
```

## Why "Use scrollAwareRange directly on percentile layer" Is Wrong

Sharing the `scrollAwareRange` object on the percentile layer breaks `onVisibleEntries`:

`ScrollAwareRangeProvider.buildCache` is called at **chart level** with ALL model series.
For baby the producer pushes percentile bands as layer 0 (7 series) then data as layer 1
(1 series). With `config.useAllSeriesForYRange = false`, `onVisibleEntries` takes
`firstOrNull()` — which gives the **3rd percentile band**, not the baby measurements →
wrong Y range computation → the fix defeats itself.

## Why This Approach

### Approach A — Delegate Y range on percentile layer (chosen)

**Part 1:** Add Y-seed computation to `updateBabySegmentRanges` based on `selectedMetric`.

**Part 2:** Replace the percentile layer's `rangeProvider` with a thin
`CartesianLayerRangeProvider` delegate that:
- Returns X from `scrollAwareRange.xRangeMin`/`xRangeMax` (already = `chartMinX`/`chartMaxX`)
- Returns Y by calling `scrollAwareRange.getMinY()`/`getMaxY()` — mirrors the primary
  layer's animated Y range without touching the `onVisibleEntries` series list
- Remove `alwaysUseLiveRange = true`

`scrollAwareRange` stays on `primaryLayer` only. No series confusion in `onVisibleEntries`.
The percentile layer's Y range contribution to the shared axis now exactly matches the
primary layer's range → no union expansion → no flash.

```kotlin
// ProductChart.kt — inside hasPercentileLayer block
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
```

**Pros:** Fixes both causes. Minimal change to `ProductChart.kt`. Clean delegation pattern.
**Cons:** None.

### Approach B — Fix only root cause 1 (seed in VM)

**Rejected:** Flash persists once percentile model loads asynchronously. The Y-range
contamination from `alwaysUseLiveRange = true` is independent of the seed.

### Approach C — Separate vertical axis for percentile layer

Give percentile layer `Axis.Position.Vertical.Start` (hidden). **Rejected:** The percentile
bands and baby data are in the same unit (lbs / inches) and must share the same Y scale to
render at correct relative positions. Separate axes would break visual alignment.

## Key Decisions

- **Both causes must be fixed together** — fixing only one still leaves a flash
- **Y-delegate pattern instead of sharing `scrollAwareRange`** — avoids polluting
  `onVisibleEntries` with 7 percentile-band series
- **`scrollAwareRange.xRangeMin`/`xRangeMax` are public mutable fields** updated every
  composition — safe to read from the delegate without re-creating it per `chartMinX` change
- **`updateBabySegmentRanges` seeds based on `_state.value.selectedMetric`** at data-arrival
  time. Metric switch (WEIGHT → HEIGHT) may stale the seed for one frame until `onYRangeSettled`
  re-fires — an acceptable single-frame transition
- **`alwaysUseLiveRange = true` removed** — it was the workaround that caused Y contamination;
  the delegate now provides the correct Y range without it

## Files to Change

| File | Change |
|------|--------|
| `BabyDashboardViewModel.kt` | `updateBabySegmentRanges` — add `seedMinY`/`seedMaxY` computation from `filteredTarget` using `_state.value.selectedMetric` |
| `ProductChart.kt` | Percentile layer — replace `CartesianLayerRangeProvider.fixed()` block with Y-delegate provider; remove `alwaysUseLiveRange = true` |

## Resolved Questions

- **Why can't we share `scrollAwareRange` directly?** `onVisibleEntries` takes `firstOrNull()`
  for baby — that's the 3rd percentile band, not the baby data. Computed Y range would be wrong.
- **Does `alwaysUseLiveRange = true` bypass the range provider?** Yes — that's exactly why
  removing it (and replacing with the delegate) fixes the Y contamination.
- **Does the baby `onYRangeSettled` callback already work?** Yes — `GraphView` dispatches
  `UpdateSeedYRange` for all products. The VM seed covers frame 0; `onYRangeSettled` keeps
  it current thereafter.

## Next Steps

→ `/ce:plan` for implementation details

---
date: 2026-04-13
topic: chart-initial-range-jump-fix
---

# Chart Initial Y-Range Jump Fix

## What We're Building

Eliminating the visible "jump" that occurs when the weight/BP chart first loads. On frame 0,
Vico renders with a Y-axis range covering **all historical data** (e.g., 150–220 lbs across all
time). On frame 1, after `ScrollAwareRangeEffect` fires and computes the correct visible-window
range, the Y-axis snaps to the narrow range (e.g., 170–185 lbs this week). This produces a
noticeable visual flicker.

## Root Cause

The jump is a two-frame race condition baked into `CartesianChartHost`:

```
Frame 0:  animatedMinY = NaN, animatedMaxY = NaN
          → hasValidAnimatedRange = false
          → ranges = initialRanges (Vico default = full data min/max)
          → Chart renders with wide Y-axis
          → Canvas emits ScrollInfo to scrollUpdates flow

Frame 1:  LaunchedEffect(model) fires
          → buildCache(series) → scrollUpdates.first() collects Frame 0 info
          → computeVisibleEntries() filters to visible window
          → generateNiceScale() → correct narrow range
          → animMinY.snapTo(correctMin) → onAnimatedRange fires
          → hasValidAnimatedRange = true
          → Chart re-renders with correct narrow Y-axis   ← jump visible here
```

The original FEATURE-1 design doc explicitly described `alpha(0f)` on frame 0 to prevent this.
That was removed in "v3" with the comment:

> "Brief flash of default range is acceptable."

It is not acceptable for this product.

## Why This Approach: initialMinY/initialMaxY API Extension

Three approaches were considered:

| Approach | Vico change | App change | Flash risk |
|----------|-------------|------------|------------|
| A: Restore alpha(0f) | CartesianChartHost | None | Zero (invisible frame) |
| B: Pre-warm provider at composition | None (internal) | ProductChart computes range | Zero |
| C: initialMinY/maxY API param (chosen) | ScrollAwareRangeProvider + remember fn | ProductChart computes + passes | Zero |

**Why C**: Cleanest API contract. The `ScrollAwareRangeProvider` states its intent
explicitly — "here is what I want on frame 0, update me after." Option A hides the symptom
without fixing the data-flow. Option B mutates internal provider state from outside, which is
fragile. Option C is declarative and self-documenting.

## Key Decisions

- **`initialMinY`/`initialMaxY` in `ScrollAwareRangeProvider`**: Used in `getMinY`/`getMaxY`
  when `!isCacheReady`. Falls through to Vico's intrinsic range (full data) only if also NaN.
  After first `snapTo` completes, `currentMinY`/`currentMaxY` take over.

- **Compute initial window in `rememberProductChart`**: Same timestamp logic as `GraphView.kt`'s
  `initialStartX`. Filter `segmentState.data` entries to the initial visible window, extract Y
  values, run `generateNiceScale()`. All synchronous — available before frame 0.

- **Y-value extraction is config-driven**: `ChartConfig` gains a
  `getInitialYValues: (List<PeriodSummary>) -> List<Double>` lambda. Each product sets this
  in their config builder: weight uses first series Y, BP uses all three. This mirrors the
  existing `useAllSeriesForYRange` flag but for domain objects.

- **`remember()` key**: `remember(segmentState.data, segment, config)` — recomputes if data
  changes (e.g., account switch) or segment changes.

- **Fallback**: If `segmentState.data` is empty or initial window has no entries, pass `NaN`
  — existing behaviour (Vico intrinsic range) applies. No regression.

- **No `isCacheReady = true` pre-warming**: The initial values are hints, not a built cache.
  `ScrollAwareRangeEffect` still builds the cache and fires `snapTo`. Because `snapTo`
  transitions from initial→computed synchronously (both likely identical since they use the same
  `generateNiceScale` call on the same data), no animation is visible.

## Files Changed

### Vico workspace (`/Users/selvakumar/Projects/vico`)

| File | Change |
|------|--------|
| `vico/compose/.../data/ScrollAwareRangeProvider.kt` | Add `initialMinY`/`initialMaxY` fields; update `getMinY`/`getMaxY` |
| `vico/compose/.../data/ScrollAwareRangeProvider.kt` | Add params to `rememberScrollAwareRangeProvider()` |

### App (`/Users/selvakumar/Projects/meApp/Android`)

| File | Change |
|------|--------|
| `features/common/components/chart/config/ChartConfig.kt` | Add `getInitialYValues` lambda |
| `features/common/components/chart/ProductChart.kt` | Compute initial visible window + range; pass `initialMinY`/`initialMaxY` |
| Per-product config builders | Wire `getInitialYValues` lambda |

## Implementation Sketch

### 1. ScrollAwareRangeProvider (Vico)

```kotlin
// New fields
public var initialMinY: Double = Double.NaN
public var initialMaxY: Double = Double.NaN

// Updated getters — priority: animated current → initial hint → Vico intrinsic
override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
    if (isCacheReady && !currentMinY.isNaN()) currentMinY
    else if (!initialMinY.isNaN()) initialMinY
    else minY

override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
    if (isCacheReady && !currentMaxY.isNaN()) currentMaxY
    else if (!initialMaxY.isNaN()) initialMaxY
    else maxY
```

### 2. rememberScrollAwareRangeProvider (Vico)

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
    onVisibleEntries: ...
): ScrollAwareRangeProvider {
    ...
    provider.initialMinY = initialMinY
    provider.initialMaxY = initialMaxY
    return provider
}
```

### 3. ChartConfig (App)

```kotlin
data class ChartConfig(
    ...
    // Returns Y values from domain entries for initial range computation.
    // Null = no pre-warming (falls back to Vico intrinsic range on frame 0).
    val getInitialYValues: ((List<PeriodSummary>) -> List<Double>)? = null,
)
```

### 4. ProductChart.kt (App)

```kotlin
// Before rememberScrollAwareRangeProvider:
val initialRange = remember(segmentState.data, segment, config) {
    val getY = config.getInitialYValues ?: return@remember null
    val endTs = segmentState.endTimestamp ?: return@remember null
    val startX = GraphUtil.getRollingWindowStart(segment, endTs)
        ?: GraphUtil.getStartRange(segment, endTs)
        ?: return@remember null
    val xStep = GraphUtil.calculateXStep(segment)
    val visibleCount = segment.visibleLabelsCount()
    val endX = startX + xStep * visibleCount

    val windowEntries = segmentState.data.filter { entry ->
        val ts = DateTimeConverter.isoToTimestamp(entry.entryTimestamp)
        ts.toDouble() in startX..endX
    }
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

val scrollAwareRange = rememberScrollAwareRangeProvider(
    minX = segmentState.chartMinX ?: Double.NaN,
    maxX = segmentState.chartMaxX ?: Double.NaN,
    initialMinY = initialRange?.min ?: Double.NaN,
    initialMaxY = initialRange?.max ?: Double.NaN,
) { visibleSeriesEntries, visibleXRange -> ... }
```

## Open Questions

_None — resolved during brainstorm._

## Resolved Questions

- **Why not restore alpha(0f)?** Alpha hides the symptom; this approach fixes the data flow.
  The chart renders correctly on frame 0 instead of being hidden.
- **What if initial range is slightly different from computed range?** The `snapTo` transition
  is instantaneous (no tween), so a small difference (e.g., rounding) would not be visible.
  In practice they will be identical since both use `generateNiceScale` on the same data.
- **Does this affect TOTAL segment?** TOTAL doesn't use `ScrollAwareRangeProvider` for its
  Y range in the same way (no scrolling). `getInitialYValues` returns null → NaN → no change.

## Next Steps

→ `/ce:plan` for implementation details

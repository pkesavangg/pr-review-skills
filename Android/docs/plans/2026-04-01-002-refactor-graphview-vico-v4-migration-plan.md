---
title: "refactor: Migrate GraphView to vico v4 (vico-gg:4.0.1)"
type: refactor
status: active
date: 2026-04-01
---

# refactor: Migrate GraphView to vico v4

## Overview

Migrate meApp's chart components from vico v3 (`vico-compose:3.0.2`) to v4 (`vico-gg:4.0.1`). Update import paths, remove deprecated APIs, replace callback patterns with reactive flows, and eliminate unnecessary recompositions.

## Files to Change

| File | Changes |
|------|---------|
| `libs.versions.toml` | Replace vico dependency with `vico-gg:4.0.1` |
| `GraphView.kt` | Import paths, scroll state params, onScrollStopped → snapshotFlow, remove dead state |
| `GraphChart.kt` | Import paths, axis setup |
| `GraphMarker.kt` | Import `InterpolationType` from compose package |
| `HorizontalItemPlacer.kt` | Import `CartesianDrawingContext` from compose package |
| `VerticalAxis.kt` | Import paths, verify `Size.scroll` → `Size.Scroll` |
| `PrimaryLayer.kt` | Fix unused animation states, verify monotone interpolator |
| `build.gradle.kts` (app) | Update dependency reference |

## Step-by-Step Changes

### 1. Update dependency (`libs.versions.toml` + `build.gradle.kts`)

```toml
# libs.versions.toml
vico = "4.0.1"

[libraries]
vico-gg = { group = "com.dmdbrands.lib", name = "vico-gg", version.ref = "vico" }
```

Remove old vico-compose, vico-core, vico-compose-m3 dependencies. Replace with single `vico-gg`.

### 2. Import path changes (all chart files)

| Old (v3) | New (v4) |
|----------|----------|
| `com.patrykandpatrick.vico.core.cartesian.InterpolationType` | `com.patrykandpatrick.vico.compose.cartesian.InterpolationType` |
| `com.patrykandpatrick.vico.core.cartesian.Scroll` | `com.patrykandpatrick.vico.compose.cartesian.Scroll` |
| `com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext` | `com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext` |
| `com.patrykandpatrick.vico.core.cartesian.axis.*` | `com.patrykandpatrick.vico.compose.cartesian.axis.*` |
| `com.patrykandpatrick.vico.core.cartesian.marker.*` | `com.patrykandpatrick.vico.compose.cartesian.marker.*` |
| `com.patrykandpatrick.vico.core.common.*` | `com.patrykandpatrick.vico.compose.common.*` |

### 3. GraphView.kt — API changes

#### 3a. `rememberVicoScrollState` params

```kotlin
// OLD (v3)
val scrollState = rememberVicoScrollState(
  scrollEnabled = ...,
  initialScroll = initialScroll,
  snapBehaviorConfig = SnapBehaviorConfig(...),
  scrollStartPaddingXStep = startPaddingXStep,  // REMOVED
  key = segment,  // REMOVED
)

// NEW (v4)
val scrollState = rememberVicoScrollState(
  scrollEnabled = ...,
  initialScroll = initialScroll,
)
// Snap behavior is separate via rememberChartSnapFlingBehavior
// startPaddingXStep handled via xWithPadding on initial scroll
// key: use Compose key() if needed
```

#### 3b. `onScrollStopped` → `LaunchedEffect` + `snapshotFlow`

```kotlin
// OLD (v3) — callback on CartesianChartHost
onScrollStopped = { range ->
  if (range != null && segment != GraphSegment.TOTAL) {
    val min = range.visibleXRange.start.toLong()
    val max = range.visibleXRange.endInclusive.toLong()
    onScrollUpdate(min, max)
  }
}

// NEW (v4) — reactive flow, zero recomposition
LaunchedEffect(scrollState, segment) {
  snapshotFlow { scrollState.value }
    .debounce(100)
    .collect {
      if (segment == GraphSegment.TOTAL) return@collect
      val range = scrollState.visibleXRange ?: return@collect
      val min = range.start.toLong()
      val max = range.endInclusive.toLong()
      val relativeMin = GraphUtil.getRelativeStart(segment, min)
      val relativeMax = GraphUtil.getRelativeEnd(segment, max)
      val clipRange = GraphUtil.clipRangeForGraph(segment, relativeMin, relativeMax)
      onScrollUpdate(clipRange.startMillis, clipRange.endMillis)
    }
}
```

#### 3c. Remove `scrollState.visibleRange` (line 181)

Dangling statement — does nothing. Remove.

#### 3d. `getInterpolatedYValues` — already on VicoScrollState in v4

```kotlin
// Same API, just verify import
val fallbackValues = scrollState.getInterpolatedYValues(
  xValues = visibleLabels,
  interpolationType = InterpolationType.MONOTONE,
)
```

### 4. VerticalAxis.kt — `Size.scroll` → `Size.Scroll`

```kotlin
// OLD (v3)
size = BaseAxis.Size.scroll(8.dp, isLabelsScrollable = true)

// NEW (v4)
size = BaseAxis.Size.Scroll(8.dp, isLabelsScrollable = true)
```

### 5. PrimaryLayer.kt — Fix unused animation states

Lines 46-47 create `mutableStateOf` for min/max Y but the animated values (lines 81, 91) are never used. Either:
- Remove the dead animation code
- Or wire it to the range provider

### 6. GraphMarker.kt — Import path only

```kotlin
// OLD
import com.patrykandpatrick.vico.core.cartesian.InterpolationType
// NEW
import com.patrykandpatrick.vico.compose.cartesian.InterpolationType
```

## Recomposition Optimizations

| Issue | Fix |
|-------|-----|
| `onScrollStopped` lambda captures state → recomposition | Replace with `LaunchedEffect` + `snapshotFlow` |
| `remember(state.markerIndex)` for chartHeight | Use `remember(currentDeviceType)` — height doesn't depend on marker |
| `scrollState.visibleRange` dangling read | Remove |
| `snapToLabelFunction` lambda recreated on recomposition | Already wrapped in `remember` — OK |

## Acceptance Criteria

- [ ] App builds with `vico-gg:4.0.1` dependency
- [ ] All `com.patrykandpatrick.vico.core.*` imports replaced with `compose.*`
- [ ] `onScrollStopped` replaced with `snapshotFlow` pattern
- [ ] `scrollStartPaddingXStep` and `key` params removed from scroll state
- [ ] `Size.scroll` → `Size.Scroll` in VerticalAxis
- [ ] No unused states or dead code
- [ ] Chart scrolling, snapping, range animation all work
- [ ] Marker scrub/tap works
- [ ] Dashboard snapshot charts render correctly

## Sources

- vico v4 branch: `dmdbrands/vico` version-4.0.0
- Published artifact: `com.dmdbrands.lib:vico-gg:4.0.1`
- Feature docs: `vico/docs/FEATURE-*.md`

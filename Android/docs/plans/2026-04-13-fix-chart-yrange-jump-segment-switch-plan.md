---
title: "fix: Eliminate Y-axis range jump on chart segment switch"
type: fix
status: completed
date: 2026-04-13
origin: docs/brainstorms/2026-04-13-chart-seed-yrange-in-state-brainstorm.md
---

# fix: Eliminate Y-axis range jump on chart segment switch

## Overview

An extension of MA-3490 (initial-load Y-range pre-warming). When the user switches chart
segments (WEEK → MONTH → YEAR → TOTAL), the newly composed chart starts with a blank
`ScrollAwareRangeProvider` — `isCacheReady = false`, `seedMinY = NaN` — causing the same
frame-0 flash that MA-3490 fixed for first load.

The fix: once `ScrollAwareRangeProvider` settles on a computed Y range (via its
`onVisibleEntries` callback), persist that `(seedMinY, seedMaxY)` in `SegmentState`. On every
subsequent composition of that segment the stored seed is immediately available — no data
filtering or computation required at composition time.

## Problem Statement

```
Segment switch:
  Frame 0: new ScrollAwareRangeProvider, isCacheReady = false, seedMinY = NaN
           → getMinY falls through to Vico intrinsic (all-data extremes)
           → Y-axis briefly shows e.g. 150–220 lbs (full history)

  Frame 1: LaunchedEffect(model) fires → buildCache → scrollUpdates.first()
           → snapTo(correctRange, rangeMin=170..rangeMax=185)
           → Y-axis jumps → visible flash
```

The `initialRange` computed from `segmentState.data` in `ProductChart.kt` (MA-3490) only helps
when data is already populated at composition time. On segment switch the data may not be loaded
yet, and even when it is, `isCacheReady` resets on model change — recreating the gap.

## Proposed Solution

Persist the settled Y range per segment in `SegmentState`:

1. **`SegmentState`** gains `seedMinY: Double? = null` + `seedMaxY: Double? = null`
2. **`BaseGraphIntent.UpdateSeedYRange`** (new) triggers the update
3. **`BaseGraphReducer`** handles it via the existing `copyBaseFields` / segment-update pattern
4. **`rememberProductChart`** gets an `onYRangeSettled` callback, called inside `onVisibleEntries`
   after computing `rangeMinY`/`rangeMaxY`
5. **`GraphView`** passes the callback and dispatches `UpdateSeedYRange`
6. **Vico `ScrollAwareRangeProvider`**: rename `initialMinY`/`initialMaxY` → `seedMinY`/`seedMaxY`

## Technical Approach

### Architecture Impact

Pure MVI — state flows one direction:

```
onVisibleEntries fires (main thread, ScrollAwareRangeEffect)
  → computes (rangeMinY, rangeMaxY) via generateNiceScale
  → calls onYRangeSettled(rangeMinY, rangeMaxY)         [new ProductChart param]
  → GraphView dispatches UpdateSeedYRange(segment, min, max)
  → BaseGraphReducer stores in SegmentState.seedMinY / seedMaxY
  → Next composition reads segmentState.seedMinY → seeds provider
  → Frame 0: correct range, no flash
```

### Seed value priority in `rememberScrollAwareRangeProvider`

```kotlin
seedMinY = segmentState.seedMinY?.toDouble() ?: initialRange?.min ?: Double.NaN,
seedMaxY = segmentState.seedMaxY?.toDouble() ?: initialRange?.max ?: Double.NaN,
```

State-stored seed takes priority. Data-filter `initialRange` remains as a belt-and-suspenders
fallback for the absolute first composition of a segment that has never been visited.

### Vico param rename

`initialMinY`/`initialMaxY` → `seedMinY`/`seedMaxY` in:
- `ScrollAwareRangeProvider` public fields
- `rememberScrollAwareRangeProvider()` params and body

The priority chain in `getMinY`/`getMaxY` is unchanged — only the field names change.

## Files Changed

### Vico workspace — `/Users/selvakumar/Projects/vico`

| File | Change |
|------|--------|
| `vico/compose/src/commonMain/.../data/ScrollAwareRangeProvider.kt` | Rename `initialMinY`/`initialMaxY` → `seedMinY`/`seedMaxY` (fields + `rememberScrollAwareRangeProvider` params) |

### App — `/Users/selvakumar/Projects/meApp/Android`

| File | Change |
|------|--------|
| `app/src/main/java/.../viewmodel/base/BaseDashboardState.kt` | Add `seedMinY: Double? = null`, `seedMaxY: Double? = null` to `SegmentState` |
| `app/src/main/java/.../viewmodel/base/BaseGraphIntent.kt` | Add `UpdateSeedYRange` data class to `BaseGraphIntent`; add handler in `BaseGraphReducer.reduceBaseIntent` |
| `app/src/main/java/.../chart/ProductChart.kt` | Add `onYRangeSettled` param; call it inside `onVisibleEntries`; update seed priority logic |
| `app/src/main/java/.../chart/GraphView.kt` | Pass `onYRangeSettled` callback to `rememberProductChart` |

## System-Wide Impact

- **Interaction graph**: `onVisibleEntries` fires on the main thread from `ScrollAwareRangeEffect`
  (a `LaunchedEffect(model)` inside `CartesianChartHost`) — no threading concerns. The resulting
  `UpdateSeedYRange` dispatch is synchronous; the reducer writes to `SegmentState` on the next
  MVI cycle. No race condition possible since both read and write occur on the main thread.
- **State lifecycle risks**: `seedMinY`/`seedMaxY` are per-segment and scoped to the ViewModel
  lifetime. They reset with the ViewModel (app process death, screen exit). This is the correct
  behaviour — the seed is a display hint, not durable data.
- **API surface parity**: `onYRangeSettled` is a new no-op-defaulted param. No existing
  `rememberProductChart` call sites require changes.
- **Metric switch (lbs → kg)**: When the user changes units the model changes, triggering
  `LaunchedEffect(model)` to rebuild the cache and call `onVisibleEntries` again — the seed
  gets overwritten with the correct unit-converted range. One brief jump on the transition frame
  is acceptable.
- **Vico rename backward compat**: `initialMinY`/`initialMaxY` were added in this session (MA-3490)
  and have exactly one call site in `ProductChart.kt`. The rename updates that call site atomically.

## Acceptance Criteria

- [ ] Switching between WEEK / MONTH / YEAR / TOTAL shows no Y-axis jump for any segment that
  was previously visited
- [ ] First visit to a segment: seed is null → falls back to `initialRange` (data-filter) or Vico
  intrinsic; no regression from MA-3490
- [ ] Weight chart, blood pressure chart, and baby chart all behave correctly
- [ ] TOTAL segment: `seedMinY`/`seedMaxY` stored after first load; subsequent returns show no jump
- [ ] `UpdateSeedYRange` dispatch does NOT fire on every scroll — only when `onVisibleEntries`
  produces a new settled range (i.e., on segment load/reload, not continuous scroll)
- [ ] Vico param rename: `rememberScrollAwareRangeProvider` called with `seedMinY`/`seedMaxY`;
  build passes with no "no parameter with name" errors
- [ ] `./gradlew assembleDebug` passes (app + composite Vico build)
- [ ] No `!!` operators introduced (detekt enforces this)

## Dependencies & Risks

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| `onYRangeSettled` fires on every scroll tick (not just settle) | Low | `onVisibleEntries` already debounced via `ScrollAwareRangeEffect`; callback only fires when range computation runs |
| Stale seed after unit change (lbs → kg) | Low | Model change triggers `LaunchedEffect(model)` reset → `onVisibleEntries` re-fires → seed updated; one frame acceptable |
| `SegmentState` `@Stable` annotation affected by new `Double?` fields | Very low | `Double?` is a stable type; Compose stability unaffected |
| Vico composite build not active | Very low | `settings.gradle.kts` already has `includeBuild` uncommented from MA-3490; verify before building |

## Implementation Checklist

- [ ] **Vico**: Rename `initialMinY` → `seedMinY`, `initialMaxY` → `seedMaxY` in `ScrollAwareRangeProvider.kt` (fields + `rememberScrollAwareRangeProvider` params + body)
- [ ] **`BaseDashboardState.kt`**: Add `seedMinY: Double? = null`, `seedMaxY: Double? = null` to `SegmentState`
- [ ] **`BaseGraphIntent.kt`**: Add `data class UpdateSeedYRange(val segment: GraphSegment, val minY: Double, val maxY: Double) : BaseGraphIntent`
- [ ] **`BaseGraphIntent.kt`**: Handle `UpdateSeedYRange` in `BaseGraphReducer.reduceBaseIntent` — same pattern as `UpdateIsEmptyGraph`
- [ ] **`ProductChart.kt`**: Add `onYRangeSettled: (Double, Double) -> Unit = {}` param; call `onYRangeSettled(rangeMinY, rangeMaxY)` inside `onVisibleEntries` after computing range; update `rememberScrollAwareRangeProvider` to pass `seedMinY`/`seedMaxY` with fallback
- [ ] **`GraphView.kt`**: Pass `onYRangeSettled = { minY, maxY -> handleGraphIntent(BaseGraphIntent.UpdateSeedYRange(segment, minY, maxY)) }` to `rememberProductChart`
- [ ] Run `./gradlew assembleDebug` and verify build passes
- [ ] Remove `Log.d("CHECKING", ...)` debug line from `ProductChart.kt` (added in MA-3490 session)

## Code Sketches

### `BaseDashboardState.kt` — SegmentState additions

```kotlin
// app/src/main/java/.../viewmodel/base/BaseDashboardState.kt
data class SegmentState(
  // ... existing fields unchanged ...
  val visibleMin: Long? = null,
  val visibleMax: Long? = null,
  /** Last settled Y range from ScrollAwareRangeProvider — seeds frame-0 on segment switch. */
  val seedMinY: Double? = null,
  val seedMaxY: Double? = null,
)
```

### `BaseGraphIntent.kt` — new intent + reducer case

```kotlin
// BaseGraphIntent interface — add:
data class UpdateSeedYRange(
  val segment: GraphSegment,
  val minY: Double,
  val maxY: Double,
) : BaseGraphIntent

// BaseGraphReducer.reduceBaseIntent — add case before `else -> state`:
is BaseGraphIntent.UpdateSeedYRange -> {
  val current = state.segmentStates[intent.segment] ?: SegmentState()
  copyBaseFields(
    state,
    segmentStates = state.segmentStates + (intent.segment to current.copy(
      seedMinY = intent.minY,
      seedMaxY = intent.maxY,
    )),
  )
}
```

### `ProductChart.kt` — onYRangeSettled param + seed priority

```kotlin
// Signature addition:
fun rememberProductChart(
  // ... existing params ...
  onYRangeSettled: (Double, Double) -> Unit = {},
): CartesianChart {

  // Inside onVisibleEntries callback, after computing rangeMinY/rangeMaxY:
  onYRangeSettled(rangeMinY, rangeMaxY)
  (rangeMinY..rangeMaxY) to ticks

  // Updated rememberScrollAwareRangeProvider call:
  val scrollAwareRange = rememberScrollAwareRangeProvider(
    minX = segmentState.chartMinX ?: Double.NaN,
    maxX = segmentState.chartMaxX ?: Double.NaN,
    seedMinY = segmentState.seedMinY ?: initialRange?.min ?: Double.NaN,
    seedMaxY = segmentState.seedMaxY ?: initialRange?.max ?: Double.NaN,
  ) { visibleSeriesEntries, visibleXRange -> /* unchanged */ }
}
```

### `GraphView.kt` — callback wiring

```kotlin
// features/common/components/chart/GraphView.kt — rememberProductChart call:
val chart = rememberProductChart(
  config = chartConfig,
  graphState = state,
  segmentState = segmentState,
  defaultMarker = defaultMarker,
  segment = segment,
  horizontalItemPlacer = horizontalItemPlacer,
  fadingEdges = fadingEdges,
  scrubController = scrubController,
  onYRangeSettled = { minY, maxY ->
    handleGraphIntent(BaseGraphIntent.UpdateSeedYRange(segment, minY, maxY))
  },
)
```

### Vico `ScrollAwareRangeProvider.kt` — rename only

```kotlin
// Replace throughout the file:
public var seedMinY: Double = Double.NaN   // was initialMinY
public var seedMaxY: Double = Double.NaN   // was initialMaxY

override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
  if (isCacheReady && !currentMinY.isNaN()) currentMinY
  else if (!seedMinY.isNaN()) seedMinY        // was initialMinY
  else minY

// rememberScrollAwareRangeProvider params:
public fun rememberScrollAwareRangeProvider(
  // ...
  seedMinY: Double = Double.NaN,    // was initialMinY
  seedMaxY: Double = Double.NaN,    // was initialMaxY
  // ...
): ScrollAwareRangeProvider {
  // ...
  provider.seedMinY = seedMinY      // was initialMinY
  provider.seedMaxY = seedMaxY      // was initialMaxY
```

## Sources & References

### Origin

- **Brainstorm document:** [docs/brainstorms/2026-04-13-chart-seed-yrange-in-state-brainstorm.md](../brainstorms/2026-04-13-chart-seed-yrange-in-state-brainstorm.md)
  — Key decisions carried forward: (1) persist seed in `SegmentState`, not computed at composition
  time; (2) `onYRangeSettled` callback routes through `rememberProductChart` → `GraphView` → MVI;
  (3) rename `initialMinY`/`initialMaxY` → `seedMinY`/`seedMaxY` in Vico

### Internal References

- `BaseDashboardState.kt` — `SegmentState` data class
- `BaseGraphIntent.kt` — `BaseGraphIntent` interface + `BaseGraphReducer` (colocated, lines 36–80+)
- `ProductChart.kt` — `rememberProductChart`, `onVisibleEntries` callback, `rememberScrollAwareRangeProvider` call
- `GraphView.kt` — `rememberProductChart` call at line ~230; `handleGraphIntent` dispatch patterns
- `ScrollAwareRangeProvider.kt` — `/Users/selvakumar/Projects/vico/vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/cartesian/data/ScrollAwareRangeProvider.kt` — fields `initialMinY`/`initialMaxY` (to be renamed)
- `settings.gradle.kts` — `includeBuild` for local Vico substitution (must remain uncommented)
- Prior plan: [docs/plans/2026-04-13-feat-chart-initial-yrange-no-jump-plan.md](2026-04-13-feat-chart-initial-yrange-no-jump-plan.md)

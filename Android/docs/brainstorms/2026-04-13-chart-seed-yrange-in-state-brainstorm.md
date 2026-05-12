---
date: 2026-04-13
topic: chart-seed-yrange-in-state
origin: docs/brainstorms/2026-04-13-chart-initial-range-jump-fix-brainstorm.md
---

# Chart Y-Range Seed: Persist Settled Range in SegmentState

## What We're Building

An extension of the initial Y-range pre-warming fix (MA-3490) to cover segment switches.

The first fix computed the initial Y range synchronously from `segmentState.data` at
composition time, eliminating the frame-0 flash on first load. However, on **segment switch**,
the new segment's data may not be loaded yet — so `data` is empty, `initialRange = null`,
and the same two-frame race condition fires again.

The solution: once `ScrollAwareRangeProvider` settles on a computed range (via its
`onVisibleEntries` callback), persist that `(seedMinY, seedMaxY)` in the `SegmentState` data
class. On any subsequent composition of that segment (tab switch, screen return, metric change),
the stored seed is immediately available to `rememberScrollAwareRangeProvider` — no data filtering
or computation required.

## Root Cause

```
Segment switch / screen return:
  Frame 0: new ScrollAwareRangeProvider, isCacheReady = false, seedMinY = NaN
           → getMinY falls through to Vico intrinsic (all-data extremes)
           → Y-axis flash shows wrong range

  Frame 1: LaunchedEffect(model) → buildCache → scrollUpdates.first()
           → snapTo(correctRange)
           → Y-axis corrects → visible jump
```

The `initialRange` remember block in `ProductChart.kt` only helps when `segmentState.data`
is already populated at composition time. For segment switches where data was previously loaded
but the ViewModel's `SegmentState` hasn't stored the settled range, we have no seed.

## Why This Approach

### Approach A (chosen): Persist settled range in `SegmentState` (state-persistence)

Once `onVisibleEntries` returns a computed range, emit a `BaseGraphIntent.UpdateSeedYRange`
intent → reducer stores `seedMinY`/`seedMaxY` in `SegmentState` → next composition reads it
directly.

**Pros:**
- Correct on every re-composition: first load, segment switch, tab return, metric switch
- No data filtering at composition time (no `windowEntries` scan on switch)
- Per-segment isolation — each segment stores its own last-known range
- Falls back gracefully via NaN for truly first loads (if state was never populated)
- Clean MVI: state flows one direction, no callback leakage

**Cons:**
- Requires new intent + reducer case + state fields
- Settled range may be slightly stale if user changes unit (lbs → kg) between visits — but
  the first frame jump is only ~1 frame before the animation corrects, so this is acceptable

### Approach B (rejected): Keep data-filter computation only

Re-run `windowEntries` filter + `generateNiceScale` on every segment transition.
**Rejected** because `segmentState.data` is often empty during segment switch (data loads
asynchronously), so this has the same failure mode — produces `null` / NaN seed.

### Approach C (rejected): Alpha-hide frame 0

Set `alpha = 0f` on `CartesianChartHost` until `isCacheReady`. **Rejected** — removed from
Vico v4 intentionally; restoring it causes a blank-frame flash and hides legitimate loading
state.

## Key Decisions

- **`SegmentState` gains `seedMinY: Double? = null` and `seedMaxY: Double? = null`** — nullable
  to distinguish "never settled" (null → NaN → Vico intrinsic) from "settled at 0.0"
- **New intent `BaseGraphIntent.UpdateSeedYRange(segment: GraphSegment, minY: Double, maxY: Double)`**
  — dispatched from `GraphView` when `onYRangeSettled` fires
- **`rememberProductChart` gains `onYRangeSettled: (Double, Double) -> Unit = {}`** — called
  inside the existing `onVisibleEntries` callback immediately after computing `rangeMinY`/`rangeMaxY`
- **Vico param rename: `initialMinY`/`initialMaxY` → `seedMinY`/`seedMaxY`** — more accurate
  name; "initial" implies first-load only, "seed" conveys "last known good value for any load"
- **Fallback ordering in Vico unchanged** (priority: animated current → seed → intrinsic) — only
  the field names change
- **Existing `initialRange` computation in `ProductChart.kt` removed** — superseded by the
  state-stored seed. On truly first load the seed is null → NaN → Vico intrinsic (acceptable,
  short-lived until first `onVisibleEntries` fires and `snapTo` corrects the range). Alternatively,
  keep the data-filter fallback as a belt-and-suspenders for the absolute first composition
  when SegmentState has never been seeded.

## Implementation Plan

### 1. Vico workspace — rename params

File: `vico/compose/src/commonMain/kotlin/.../data/ScrollAwareRangeProvider.kt`

- Rename `initialMinY` → `seedMinY`, `initialMaxY` → `seedMaxY` (public fields + `rememberScrollAwareRangeProvider` params)
- `getMinY`/`getMaxY` priority unchanged — just field name update

### 2. App — `SegmentState`

File: `features/dashboard/viewmodel/base/BaseDashboardState.kt`

```kotlin
data class SegmentState(
  // ... existing fields ...
  val seedMinY: Double? = null,
  val seedMaxY: Double? = null,
)
```

### 3. App — `BaseGraphIntent`

File: `features/dashboard/viewmodel/base/BaseGraphIntent.kt`

```kotlin
sealed class BaseGraphIntent {
  // ... existing intents ...
  data class UpdateSeedYRange(
    val segment: GraphSegment,
    val minY: Double,
    val maxY: Double,
  ) : BaseGraphIntent()
}
```

### 4. App — `BaseGraphReducer`

Handle `UpdateSeedYRange` by updating the matching segment's `SegmentState`:

```kotlin
is BaseGraphIntent.UpdateSeedYRange -> {
  val updated = state.segments[intent.segment]?.copy(
    seedMinY = intent.minY,
    seedMaxY = intent.maxY,
  ) ?: return state
  state.copy(segments = state.segments + (intent.segment to updated))
}
```

### 5. App — `rememberProductChart`

File: `features/common/components/chart/ProductChart.kt`

- Remove `initialRange` remember block (or keep as secondary fallback)
- Add `onYRangeSettled: (Double, Double) -> Unit = {}` param
- Inside `onVisibleEntries` callback, after computing `rangeMinY`/`rangeMaxY`:
  ```kotlin
  onYRangeSettled(rangeMinY, rangeMaxY)
  ```
- Update `rememberScrollAwareRangeProvider` call:
  ```kotlin
  seedMinY = segmentState.seedMinY?.toDouble() ?: Double.NaN,
  seedMaxY = segmentState.seedMaxY?.toDouble() ?: Double.NaN,
  ```
  (Already `Double?` — convert to `Double` with NaN sentinel for Vico)

### 6. App — `GraphView`

File: `features/common/components/chart/GraphView.kt`

Wire callback to `rememberProductChart`:
```kotlin
rememberProductChart(
  // ... existing params ...
  onYRangeSettled = { minY, maxY ->
    handleGraphIntent(BaseGraphIntent.UpdateSeedYRange(segment, minY, maxY))
  },
)
```

## Data Flow

```
onVisibleEntries callback fires (ScrollAwareRangeEffect, main thread)
  → computes rangeMinY / rangeMaxY via generateNiceScale
  → calls onYRangeSettled(rangeMinY, rangeMaxY)
  → GraphView dispatches UpdateSeedYRange(segment, minY, maxY)
  → BaseGraphReducer stores in SegmentState.seedMinY / seedMaxY
  → Next composition reads segmentState.seedMinY → seeds ScrollAwareRangeProvider
  → Frame 0 uses correct range → no jump
```

## Fallback Behaviour

| Scenario | Seed value | Result |
|----------|-----------|--------|
| First ever load of a segment | `null` → `NaN` | Falls through to Vico intrinsic; brief jump on first visit only |
| Return to previously visited segment | stored `(min, max)` | Frame 0 already correct — no jump |
| Segment switch before data loads | stored `(min, max)` from prior visit | Frame 0 correct; may be stale by ~1 frame if range changed |
| Metric switch (lbs → kg) | prior unit's seed (stale) | One brief jump until `onVisibleEntries` fires + updates seed |

## Open Questions

_None — resolved in conversation._

## Resolved Questions

- **Should we keep the data-filter `initialRange` computation?** → Remove it; the state-stored
  seed covers all cases. The absolute first visit (seed = null) produces one jump, which is
  acceptable and already existed before this fix. Belt-and-suspenders complexity not worth it.
- **Should `seedMinY`/`seedMaxY` be `Double` or `Double?`?** → `Double?` with null = "never set"
  sentinel, mapped to `Double.NaN` when passed to Vico. Avoids confusion between "0.0" and
  "not set".
- **Rename scope?** → Rename in Vico workspace only (public API change); app just updates call
  sites. No other callers of `initialMinY`/`initialMaxY` in the app (added in this session).

## Next Steps

→ `/ce:plan` for implementation details and acceptance criteria

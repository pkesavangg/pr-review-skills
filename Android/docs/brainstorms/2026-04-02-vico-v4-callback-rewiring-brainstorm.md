---
title: "Vico v4 callback rewiring: snap, range, pointSpacing"
date: 2026-04-02
status: active
---

# Vico v4 Callback Rewiring

## What We're Building

Three chart callbacks were removed or incorrectly adapted during the vico v3 → v4 migration. The meApp calculation logic (GraphSnapHelper, GraphUtil, visibleLabelsCount) is unchanged — only the wiring to vico's API needs fixing.

### Gap 1: Snap Behavior (removed entirely)

**v3:** `SnapBehaviorConfig` passed directly to `rememberVicoScrollState` with `snapToLabelFunction: (Double?, Boolean, Boolean) -> Double`.

**v4:** `SnapBehaviorConfig` still exists but is now a separate `FlingBehavior` created via `rememberChartSnapFlingBehavior(scrollState, config)` and passed to `CartesianChartHost.flingBehavior`. The callback signature changed to `snapToLabel: (currentXLabel: Double?, projectedXLabel: Double?, isDrag: Boolean, isForward: Boolean) -> Double`.

**Fix:**
1. In `GraphView.kt`, create `SnapBehaviorConfig` with the existing `GraphSnapHelper` logic, adapting to the 4-param callback (ignore `projectedXLabel` or use it for fling accuracy).
2. Call `rememberChartSnapFlingBehavior(scrollState, config)` to get a `FlingBehavior`.
3. Pass it to `CartesianChartHost(flingBehavior = ...)`.

### Gap 2: Range / Scroll Events (snapshotFlow trigger)

**v3:** `onScrollStopped` callback on `CartesianChartHost` — fired once after scroll settles, with full range info.

**v4:** We replaced with `LaunchedEffect + snapshotFlow { scrollState.value }.debounce(100)`. The `scrollState.value` is a raw Float pixel offset — it may miss range updates when layout changes without scroll.

**Fix:**
- Keep the `snapshotFlow` pattern but verify `scrollState.visibleXRange` is non-null before processing.
- Add `scrollStartPaddingXStep` handling via `Scroll.Absolute.xWithPadding` (already done for initial scroll — verify it's still correct).
- The actual range clipping logic (`GraphUtil.getRelativeStart/End`, `clipRangeForGraph`) is unchanged.

### Gap 3: Visible Count → pointSpacing (wrong conversion)

**v3:** `visibleLabelsCount: Double` passed to `rememberCartesianChart` — told the chart how many data points fit in the visible window, affecting scroll range calculation.

**v4:** `visibleLabelsCount` removed. Replaced by `pointSpacing: Dp` or `pointSpacingProvider: (availableWidth: Float) -> Float` on `LineCartesianLayer`. The provider receives canvas width in pixels and returns spacing in pixels.

**Current (wrong):** `pointSpacingProvider = { availableWidth -> availableWidth / visibleLabelsCount }`. This divides available width by label count, but vico adds `maxPointSize` to the result, so the actual spacing = `maxPointSize + (availableWidth / count)`, which is too wide.

**Fix:**
The correct formula: `spacing = (availableWidth / visibleLabelsCount) - maxPointSize`. Since `maxPointSize` is internal to vico, approximate it or use a small fixed offset. Alternatively, set `pointSpacing` to a fixed Dp value per segment that matches the v3 density.

## Why This Approach

All three fixes are wiring-only changes — the meApp business logic (GraphSnapHelper, GraphUtil, segment calculations) stays exactly the same. We're just adapting how these values are passed to vico v4's restructured API.

## Key Decisions

1. **Snap:** Use `rememberChartSnapFlingBehavior` + existing `GraphSnapHelper` functions
2. **Range:** Keep `snapshotFlow` pattern (reactive, zero-recomposition) — just verify correctness
3. **pointSpacing:** Switch from `pointSpacingProvider` callback to fixed `pointSpacing: Dp` per segment, derived from known segment widths

## Resolved Questions

- **ScrollAwareRangeProvider:** YES — adopt it to replace `CartesianLayerRangeProvider.fixed()` in PrimaryLayer. This gives iOS Health-like dynamic Y-axis animation as the user scrolls. The `onVisibleEntries` callback receives visible data points and returns the Y range + step values, which aligns with the existing `GraphViewModel.handleRenormalizationOnYAxisChange` logic.

# DashboardChartManager Test Coverage

## Overview

Unit tests for `DashboardChartManager`, covering chart-series generation, visible-operation caching, Y-axis caching and invalidation, initialization, scroll handling, selection clearing, X-axis delegations, and latest-entry visibility behavior.

Current measured coverage (as of 2026-03-10): **90.69%**

**File:** `DashboardChartManagerTests.swift`
**Target Class:** `DashboardChartManager` (`iOS/meApp/Features/Dashboard/Managers/DashboardChartManager.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **20** |
| **Measured Coverage** | **90.69%** |

### Covered Areas

- chart-series generation for weight-only and selected-metric modes
- empty chart data handling with no operations
- `updateSelectedPeriod(...)` for year and total transitions, cache clearing, and metric refresh behavior
- `getVisibleOperations()` cache-manager integration and scrolling-state forwarding
- `yAxisDomain`, `yAxisTicks`, `getYAxisScale()`, and `updateYAxisCache(force:)`
- chart-series cache invalidation when the Y-axis domain changes
- `initializeChart()` first-load and already-initialized paths
- scroll handling via `handleScrollPositionChange`, `handleScrollStart`, `handleScrollEndOptimized`, and idle scroll-phase updates
- `clearAllCaches()`
- `handleChartSelection(at:)` nil-clear flow
- X-axis delegation helpers (`xAxisValuesWithBuffer`, `xLabelString`)
- `selectEntry(...)` and `ensureLatestEntriesVisible()`

### Methods Not Directly Tested

- some small logging-only branches remain indirectly covered by higher-level state assertions rather than log inspection

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardChartManagerTests
xcrun xccov view --report --json --archive <result-bundle>
```

# DashboardGraphManager Test Coverage

## Overview

Unit tests for `DashboardGraphManager`, covering chart-series building, period changes, scroll state transitions, chart selection behavior, visible-operation helpers, axis math, interpolation/stat helpers, metric helpers, formatting, and trigger utilities.

Current measured coverage (as of 2026-03-10): **98.19%**

**File:** `DashboardGraphManagerTests.swift`
**Target Class:** `DashboardGraphManager` (`iOS/meApp/Features/Dashboard/Managers/DashboardGraphManager.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **26** |
| **Measured Coverage** | **98.19%** |

### Covered Areas

- `generateChartData(...)` and `generateChartDataWithYAxisDomain(...)`
- scrolling-cache reuse, metric-change rebuilds, and period-change cache invalidation
- `handleCompleteChartSelection(...)` exact match, missing point, year/month granularity, update failure, and scrolling suppression
- `handleChartSelection(at:)`, `updateSelectedPoint(...)`, `handleScrollStart()`, `handleScrollEnd()`, `handleScrollEndOptimized(...)`, and `handleScrollPhaseChange(...)`
- `endScrollingImmediately()`
- visible-operation helpers: visible, strict-visible, bracketing, and latest-entry visibility
- X-axis helpers: value generation, optimal/clamped/snapped scroll positions
- interpolated display weight, current average, and weightless display calculations
- metric extraction, metric availability, and metric-display checks
- formatting helpers for X-axis labels, selected dates, range labels, and fallback labels
- midpoint/sample-date helpers
- chart-buffer and trigger/update helper methods

### Methods Not Directly Tested

- remaining uncovered lines are minimal cache bookkeeping / internal structural branches rather than separate dashboard behaviors

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardGraphManagerTests
xcrun xccov view --report --json --archive <result-bundle>
```

# DashboardDisplayManager Test Coverage

## Overview

Unit tests for `DashboardDisplayManager`, covering weight-display decisions, period labels, visible label-range operations, weightless mode, interpolation fallbacks, metric-info entry preparation, formatting helpers, and metric update flows tied to dashboard state.

Current measured coverage (as of 2026-03-10): **96.87%**

**File:** `DashboardDisplayManagerTests.swift`
**Target Class:** `DashboardDisplayManager` (`iOS/meApp/Features/Dashboard/Managers/DashboardDisplayManager.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **43** |
| **Measured Coverage** | **96.87%** |

### Covered Areas

- `displayWeight` for selected points, selected dates, total-period weightless mode, and interpolated-average fallbacks across week, month, year, and total
- `weightLabel` for empty state, selected point, selected entry, invalid selected-entry date fallback, and all period label strategies
- `weightDisplayLabel` for no-data, crosshair day/month average modes, and default goal-label behavior
- `getOperationsForLabelDateRange()` cache requests and visible label-window filtering
- `getCurrentAverageWeight()`, `displayUnitText`, and `updateVisibleDataAfterScroll()` including weightless visible-weight handling
- `activeMonthInterval` and period-dependent month exposure
- formatting helpers (`formatWeightDisplayText`, `formatYAxisTickLabel`, `formatChartDate`, `roundedGoalWeight`, `formattedMetricValue`)
- metric-info helpers (`metricInfoDateLabel`, `allowedMetricsForMetricInfo`, `validateMetricInfoSelection`, `getBodyMetric`)
- `createEntryForMetricInfo(...)` for selected-point, selected-date interpolation, and empty-visible-range interpolated-average paths
- `createEntryForMetricInfoAsync(...)` non-crashing dashboard-entry creation
- `updateMetricsForCurrentView()` for selected point, selected crosshair, empty label range, visible-average loading, and reset/config early returns
- `updateMetricsWithVisibleRegionAverage()` and `resetMetricsToLatestEntry()`

### Methods Not Directly Tested (Tested Indirectly)

- private period label/date-range helpers are exercised through `weightLabel` and `displayWeight`
- internal entry-construction closures are exercised through `createEntryForMetricInfo(...)`
- async reset behavior is exercised through dispatch coverage rather than repository outcome assertions

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardDisplayManagerTests
xcrun xccov view --report --json --archive <result-bundle>
```

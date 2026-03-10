# DashboardDateRangeManager Test Coverage

## Overview

Unit tests for `DashboardDateRangeManager`, covering label windows per period, date-range filtering, summary-bound handling, cache reuse, and empty-state labels.

Current measured coverage (as of 2026-03-10): **90.00% (369/410)** from the focused dashboard range/formatter/calculator coverage run.

**File:** `DashboardDateRangeManagerTests.swift`
**Target Class:** `DashboardDateRangeManager` (`iOS/meApp/Features/Dashboard/Managers/DashboardDateRangeManager.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **15** |
| **Measured Coverage** | **90.00% (369/410)** |
| **Target Minimum** | **90%** |

### Covered Areas

- year, month, week, and total label-range calculation
- fully-contained month detection and inclusive end handling
- total/min-max label bounds and gridline label formatting
- empty-state labels per period
- range filtering by timestamp and by day
- cached and uncached `getOperationsForLabelDateRange(...)` flows
- week-range formatting across same-month, cross-month, and cross-year windows

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -resultBundlePath /tmp/DashboardRangeFormatterCoverage.xcresult -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardDateRangeManagerTests -only-testing:meAppTests/DashboardFormatterTests -only-testing:meAppTests/DashboardMetricsCalculatorTests
xcrun xccov view --report /tmp/DashboardRangeFormatterCoverage.xcresult
```

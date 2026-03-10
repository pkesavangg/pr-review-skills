# DashboardFormatter Test Coverage

## Overview

Unit tests for `DashboardFormatter`, covering weight, date, metric-info, streak/body-metric display text, and nil or invalid input handling.

Current measured coverage (as of 2026-03-10): **99.18% (121/122)** from the focused dashboard range/formatter/calculator coverage run.

**File:** `DashboardFormatterTests.swift`
**Target Class:** `DashboardFormatter` (`iOS/meApp/Features/Dashboard/Managers/DashboardFormatter.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **12** |
| **Measured Coverage** | **99.18% (121/122)** |
| **Target Minimum** | **90%** |

### Covered Areas

- Y-axis weight formatting and rounding
- chart date and metric-info date formatting across periods
- history, selected-point, crosshair, and fallback metric-info labels
- ISO date parsing with fractional timestamps, plain timestamps, and invalid input
- dashboard-source detection
- body-metric placeholder and prelabel formatting
- helper label and selection-prefix formatting

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -resultBundlePath /tmp/DashboardRangeFormatterCoverage.xcresult -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardDateRangeManagerTests -only-testing:meAppTests/DashboardFormatterTests -only-testing:meAppTests/DashboardMetricsCalculatorTests
xcrun xccov view --report /tmp/DashboardRangeFormatterCoverage.xcresult
```

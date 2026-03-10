# DashboardMetricsManager Test Coverage

## Overview

Unit tests for `DashboardMetricsManager`, covering metric-card setup, visible-period averages, metric list visibility, binding updates, and fallback cache reuse/invalidation.

Current measured coverage (as of 2026-03-10): **91.64% (1074/1172)** from the integrated dashboard coverage run.

**File:** `DashboardMetricsManagerTests.swift`
**Target Class:** `DashboardMetricsManager` (`iOS/meApp/Features/Dashboard/Managers/DashboardMetricsManager.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **12** |
| **Measured Coverage** | **91.64% (1074/1172)** |

### Covered Areas

- default metric-card setup with 12 placeholders
- visible-average updates for week/month/year/empty-total operation windows
- metric list filtering for edit mode versus normal mode
- binding updates for long-press and async metric-info selection
- metric visibility toggling, reordering, and removed-label derivation
- fallback cache reuse and explicit invalidation via `clearFallbackCache()`
- utility helpers such as placeholder resets, summary lookup, and grid-column derivation

### Methods Not Directly Tested (Residual Gaps)

- API save and store-orchestration branches are covered primarily by broader dashboard store/lifecycle suites
- repository-refetch and logger-only branches remain mostly indirect

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -resultBundlePath /tmp/DashboardManagersBroadCoverage5.xcresult -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardMetricsManagerTests -only-testing:meAppTests/DashboardGoalManagerTests -only-testing:meAppTests/DashboardStreakManagerTests -only-testing:meAppTests/DashboardStoreTests -only-testing:meAppTests/DashboardDisplayManagerTests -only-testing:meAppTests/DashboardLifecycleManagerTests -only-testing:meAppTests/DashboardGridEditingManagerTests -only-testing:meAppTests/DashboardSyncCoordinatorTests -only-testing:meAppTests/MetricInfoSheetWrapperTests
xcrun xccov view --report /tmp/DashboardManagersBroadCoverage5.xcresult
```

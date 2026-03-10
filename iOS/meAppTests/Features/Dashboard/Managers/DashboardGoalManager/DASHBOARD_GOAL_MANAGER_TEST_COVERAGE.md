# DashboardGoalManager Test Coverage

## Overview

Unit tests for `DashboardGoalManager`, covering goal-state loading, target/current calculations, progress formatting, weightless display behavior, and hidden/no-goal flows.

Current measured coverage (as of 2026-03-10): **90.17% (312/346)** from the integrated dashboard coverage run.

**File:** `DashboardGoalManagerTests.swift`
**Target Class:** `DashboardGoalManager` (`iOS/meApp/Features/Dashboard/Managers/DashboardGoalManager.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **20** |
| **Measured Coverage** | **90.17% (312/346)** |

### Covered Areas

- `loadGoalData()` success, no-account, no-goal-settings, and latest-entry-failure branches
- goal target/current/progress recomputation through `updateGoalProgress(currentWeight:)`
- weightless goal calculations with and without configured goal settings
- `getGoalWeightForDisplay(...)` fallback and weightless rendering behavior
- `refreshGoalDataForUnitChange()` using the current account unit
- validation paths for missing account, missing settings, and invalid gain goals
- goal analytics, weight formatting, display labels, and visible-scroll update helpers

### Methods Not Directly Tested (Residual Gaps)

- generic error-wrapping branches are still lighter than the main guard/logic paths
- placeholder analytics methods (`daysToGoal`, `weeklyTarget`, `currentTrend`) are asserted through the public analytics result rather than direct private tests

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -resultBundlePath /tmp/DashboardManagersBroadCoverage5.xcresult -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardMetricsManagerTests -only-testing:meAppTests/DashboardGoalManagerTests -only-testing:meAppTests/DashboardStreakManagerTests -only-testing:meAppTests/DashboardStoreTests -only-testing:meAppTests/DashboardDisplayManagerTests -only-testing:meAppTests/DashboardLifecycleManagerTests -only-testing:meAppTests/DashboardGridEditingManagerTests -only-testing:meAppTests/DashboardSyncCoordinatorTests -only-testing:meAppTests/MetricInfoSheetWrapperTests
xcrun xccov view --report /tmp/DashboardManagersBroadCoverage5.xcresult
```

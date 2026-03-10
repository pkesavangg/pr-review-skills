# DashboardStreakManager Test Coverage

## Overview

Unit tests for `DashboardStreakManager`, covering placeholder setup, streak refreshes, progress-to-card mapping, visibility management, and failure handling.

Current measured coverage (as of 2026-03-10): **93.81% (303/323)** from the integrated dashboard coverage run.

**File:** `DashboardStreakManagerTests.swift`
**Target Class:** `DashboardStreakManager` (`iOS/meApp/Features/Dashboard/Managers/DashboardStreakManager.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **13** |
| **Measured Coverage** | **93.81% (303/323)** |

### Covered Areas

- init/setup placeholder streak-card creation
- `updateStreakItems(with:)` for both pound and kilogram labels/values
- active-count preservation after the first real data update
- `refreshStreakData()` reading `Progress` from the entry service
- error wrapping through `DashboardError.dataLoadingFailed`
- reset, unit-refresh, visibility, reordering, and removed-state helpers
- streak analytics and formatting helper coverage
- grid-column and grid-visibility helper coverage

### Methods Not Directly Tested (Residual Gaps)

- logger-only branches remain indirect
- service-internal progress generation is covered through integration with `EntryService` rather than isolated mocks of every internal path

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -resultBundlePath /tmp/DashboardManagersBroadCoverage5.xcresult -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardMetricsManagerTests -only-testing:meAppTests/DashboardGoalManagerTests -only-testing:meAppTests/DashboardStreakManagerTests -only-testing:meAppTests/DashboardStoreTests -only-testing:meAppTests/DashboardDisplayManagerTests -only-testing:meAppTests/DashboardLifecycleManagerTests -only-testing:meAppTests/DashboardGridEditingManagerTests -only-testing:meAppTests/DashboardSyncCoordinatorTests -only-testing:meAppTests/MetricInfoSheetWrapperTests
xcrun xccov view --report /tmp/DashboardManagersBroadCoverage5.xcresult
```

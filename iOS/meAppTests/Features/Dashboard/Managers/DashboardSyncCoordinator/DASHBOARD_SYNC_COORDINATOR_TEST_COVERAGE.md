# DashboardSyncCoordinator Test Coverage

## Overview

Unit tests for `DashboardSyncCoordinator`, covering entry sync dispatch, save-flow ordering and error handling, progress-metric serialization, dashboard-metric loading from account state, reload orchestration, refresh orchestration, and API-value mapping helpers.

Current measured coverage (as of 2026-03-10): **99.55%**

**File:** `DashboardSyncCoordinatorTests.swift`
**Target Class:** `DashboardSyncCoordinator` (`iOS/meApp/Features/Dashboard/Managers/DashboardSyncCoordinator.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **36** |
| **Measured Coverage** | **99.55%** |

### Covered Areas

- `syncEntries()` dispatch through the entry service
- `saveChanges(...)` success ordering plus failure behavior when metric save or progress-metric save throws, including loader dismissal behavior
- `saveProgressMetricsToAPI(...)` for:
  - no active account
  - goal-card insertion
  - clamped goal-card positioning
  - removed streak filtering
  - all-progress-metrics-removed persistence
  - removed goal-card handling
  - invalid/unmappable streak filtering
- `loadDashboardConfigurationFromAPI(...)` success callback ordering, error propagation, and refresh-account tolerance
- `loadProgressMetricsFromAccount(...)` for:
  - no-account fallback
  - missing saved progress metrics fallback
  - all-removed saved state
  - saved goal-card restoration and streak-order restoration
  - empty streak fallback
- `loadMetricsFromLocalAccount(...)` dashboard-type propagation, saved dashboard-order loading, and fallback setup paths
- `reloadDashboardConfiguration(...)` for load scheduling, optional metric refresh, and optional full-refresh behavior
- `refreshAll(...)` sync-first orchestration
- API/streak mapping helpers (`mapAPIValueToStreakLabel`, `mapStreakLabelToAPI`)

### Methods Not Directly Tested (Residual Gaps)

- only a small number of defensive logger branches remain unasserted directly
- most notification/logger behavior is still validated through flow outcomes instead of dedicated interaction-only assertions

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -resultBundlePath /tmp/DashboardLifecycleSyncCoverage7.xcresult -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardLifecycleManagerTests -only-testing:meAppTests/DashboardSyncCoordinatorTests
xcrun xccov view --report /tmp/DashboardLifecycleSyncCoverage7.xcresult
```

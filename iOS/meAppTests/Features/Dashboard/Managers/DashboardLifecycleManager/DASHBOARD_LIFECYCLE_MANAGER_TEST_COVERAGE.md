# DashboardLifecycleManager Test Coverage

## Overview

Unit tests for `DashboardLifecycleManager`, covering dashboard initialization and on-appear flows, account-change reset behavior, dashboard-type propagation, entry lifecycle hooks, settings and unit-change coordination, dashboard-load error fallbacks, refresh helpers, save/reset flows, alert handling, and metric-info bindings.

Current measured coverage (as of 2026-03-10): **91.58%**

**File:** `DashboardLifecycleManagerTests.swift`
**Target Class:** `DashboardLifecycleManager` (`iOS/meApp/Features/Dashboard/Managers/DashboardLifecycleManager.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **35** |
| **Measured Coverage** | **91.58%** |

### Covered Areas

- `initializeDashboard()` reset-state handling, local/account-backed metric setup, progress-metric loading, and final UI reset
- `onAppearActions()` latest-entry load, goal-card load, dashboard-config refresh, removal-state sync, chart-ready update handling, and UI update scheduling
- `handleActiveAccountChanged()` cache clearing, chart-init reset, graph-ready reset, and graph-selection clearing
- `handleDashboardTypeChange()` propagation for `dashboard4`, `dashboard12`, unknown dashboard types, and no-account fallback
- entry lifecycle hooks (`onEntryAdded`, `onEntryUpdated`, `onEntryDeleted`) for cache invalidation, streak-grid regeneration, and graph-selection reconciliation
- `handleSettingsChange(...)` and `handleUnitChange()` coordination paths, including refreshed streak/goal data
- `loadDashboardConfigurationFromAPI()` error fallback behavior for loaded flags, initial metrics, and initial streak setup
- `refreshDashboardState()` and `refreshAll()` orchestration paths
- `saveChanges()` success cleanup, snapshot clearing, and selected-metric reset
- `saveProgressMetricsToAPI()` ordering persistence through the lifecycle manager boundary
- `resetDashboard()` / `resetDashboardEnhanced()` success and failure cleanup flows
- `showResetDashboardAlert()` alert presentation and primary-action wiring
- metric-info selection and dismissal handlers

### Methods Not Directly Tested (Residual Gaps)

- remaining gaps are mostly logger-only branches, private helper guard paths, and a few asynchronous fallback permutations that are already exercised indirectly through public methods
- notification and logger side effects are covered primarily through stateful outcomes rather than exhaustive interaction assertions

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -resultBundlePath /tmp/DashboardLifecycleSyncCoverage7.xcresult -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardLifecycleManagerTests -only-testing:meAppTests/DashboardSyncCoordinatorTests
xcrun xccov view --report /tmp/DashboardLifecycleSyncCoverage7.xcresult
```

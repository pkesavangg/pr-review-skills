# DashboardGridEditingManager Test Coverage

## Overview

Unit tests for `DashboardGridEditingManager`, covering edit-mode entry/reset, progress-metric loading, grid reorder and removal flows, goal-card positioning, drag/binding state, save/cancel integration, and empty or invalid-input edge cases.

Current measured coverage (as of 2026-03-10): **95.29%**

**File:** `DashboardGridEditingManagerTests.swift`
**Target Class:** `DashboardGridEditingManager` (`iOS/meApp/Features/Dashboard/Managers/DashboardGridEditingManager.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **22** |
| **Measured Coverage** | **95.29%** |

### Covered Areas

- `toggleEditMode()` entering edit mode and resetting the active edit session when already editing
- `loadProgressMetricsFromAccount()` for no-account defaults, empty/all-removed saved state, saved goal-card restoration, and streak-order restoration
- `resetProgressMetricsToDefaults()` default goal-card and streak-grid recovery
- `regenerateStreakGridOrderAfterRefresh(...)` with saved order preservation and empty-order fallback
- `syncRemovalStateFromMetricsManager()`, `syncRemovalStateFromStreakManager()`, and `debouncedSyncRemovalState()`
- reordered-array removal helpers for metrics and streaks, including removal-state queries
- UIKit removal helpers for metrics and streaks, including remove and restore paths
- goal-card remove/restore, position clamping, and validation behavior
- direct metric and streak reorder methods plus `moveMetric(from:to:)` visible-item reordering
- invalid `moveMetric(from:to:)` inputs and empty/single-item safety behavior
- drag-state APIs, drag-end cleanup, selection state, bindings, grid-layout reset, and wiggle-reset helpers
- save/cancel integration through `DashboardStore`, including snapshot restoration on cancel and persisted dashboard/progress payloads on save

### Methods Not Directly Tested (Residual Gaps)

- Some defensive `guard let stateProvider else { return }` branches are only partially exercised because the manager is normally created with a live store/state provider.
- Logger-only branches are covered functionally where practical, but not asserted via log-entry inspection.

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardGridEditingManagerTests
xcrun xccov view --report --archive <result-bundle>
```

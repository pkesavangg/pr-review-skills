# DashboardEditSessionManager Test Coverage

## Overview

Unit tests for `DashboardEditSessionManager`, covering snapshot creation, snapshot replacement rules, snapshot clearing, and unsaved-change detection across metrics, streaks, goal card state, ordering, and removal sets.

Current measured coverage (as of 2026-03-10): **100.00%**

**File:** `DashboardEditSessionManagerTests.swift`
**Target Class:** `DashboardEditSessionManager` (`iOS/meApp/Features/Dashboard/Managers/DashboardEditSessionManager.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **9** |
| **Measured Coverage** | **100.00%** |

### Covered Areas

- `takeSnapshot(_:)` storing the first snapshot and refusing to overwrite an existing one
- `updateSnapshot(_:)` no-op behavior without an existing snapshot and replacement behavior once a snapshot exists
- `clearSnapshot()` removing the stored edit-session state
- `hasSnapshot` / `snapshot` state transitions across take, update, and clear flows
- `hasUnsavedChanges(current:)` when no snapshot exists
- `hasUnsavedChanges(current:)` when current state exactly matches the snapshot
- metric change detection for label changes, ordering changes, and active-count or removal-set differences
- streak change detection for label changes, order changes, active-count changes, and removed-streak differences
- goal-card change detection for removed/restored state, position changes, and combined snapshot-field comparisons

### Methods Not Directly Tested

- None. All public logic in `DashboardEditSessionManager.swift` is covered by `DashboardEditSessionManagerTests.swift`.

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardEditSessionManagerTests
xcrun xccov view --report --archive <result-bundle>
```

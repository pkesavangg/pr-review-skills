# AppSyncTabStore Test Coverage

## Overview
- Target file: `meApp/Features/AppSync/Stores/AppSyncTabStore.swift`
- Test file: `meAppTests/Features/AppSync/AppSyncTabStoreTests.swift`
- Coverage focus: scan validation, save flow, edit flow, and failure handling.

## Scenario Coverage
- Valid scan handling:
  - Presents confirmation modal for valid scan payload.
  - Converts scan into internal metrics and keeps data available for action handlers.
- Invalid scan rejection:
  - Rejects non-positive weights.
  - Rejects out-of-range weights (`< 1kg` or `> 450kg`).
  - Shows error toast and does not present modal.
- Save action behavior:
  - Save action updates tab routing to dashboard.
  - Save action triggers persistence flow and creates `Entry` with expected metadata.
  - Shows loader and success toast on successful save.
- Edit action navigation state:
  - Edit action sets `pendingAppSyncEditMetrics`.
  - Edit action switches tab to manual entry.
- Error cases:
  - Missing active account prevents save.
  - Save failure shows error toast and dismisses loader/modal.

## Estimated Coverage
- Current coverage (as of 2026-03-04): **~96%**
- Estimated line coverage for `AppSyncTabStore.swift`: **~96%**
- Uncovered/partially-covered areas:
  - UI rendering internals inside `AppSyncEntryCardView` itself.
  - Log-message text details (behavior is covered; string contents are not asserted).

## Test Assets
- Fixtures:
  - `meAppTests/Features/AppSync/Fixtures/AppSyncTabStoreTestFixtures.swift`
- Mocks:
  - `meAppTests/Features/AppSync/Mocks/MockAppSyncTabStoreDependencies.swift`

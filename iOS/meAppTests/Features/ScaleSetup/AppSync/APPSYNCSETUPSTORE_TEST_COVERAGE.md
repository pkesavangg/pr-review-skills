# AppSyncSetupStore Test Coverage

## Overview
- Target file: `meApp/Features/ScaleSetup/AppSync/Stores/AppSyncSetupStore.swift`
- Test file: `meAppTests/Features/ScaleSetup/AppSync/AppSyncSetupStoreTests.swift`
- Coverage focus: step configuration, navigation, permission-driven behavior, completion/save behavior, and error handling.

## Scenario Coverage
- Step configuration by scale type:
  - Body composition scales keep `.addInfo`.
  - Weight-only scales remove `.addInfo`.
  - Camera-enabled state removes `.permissions`.
- Next/previous navigation:
  - Forward navigation skips `.permissions` when camera is enabled.
  - Back navigation skips `.permissions` when camera is enabled.
- Permission-driven behavior:
  - Permissions step disables next when camera is not granted.
  - Camera permission handler is triggered at permissions step.
  - Revoked camera permission while on `.appSync` returns flow to `.permissions`.
- Setup completion behavior:
  - Final-step completion saves scale, syncs remote, dismisses loader, and exits flow.
  - Successful completion clears `isSetupInProgress`.
- Error handling:
  - Missing active account prevents save.
  - Missing setup data prevents save.
  - Save failure shows toast and clears `isSetupInProgress`.
  - Exit flow alert is validated and primary action dismisses.

## Estimated Coverage
- Current coverage (as of 2026-03-04): **~93%**
- Estimated line coverage for `AppSyncSetupStore.swift`: **~93%**
- Uncovered/partially-covered areas:
  - SwiftUI view rendering internals inside step views.
  - Log-message string content assertions.
  - NotificationCenter side-effect assertion details.

## Test Assets
- Fixtures:
  - `meAppTests/Features/ScaleSetup/AppSync/Fixtures/AppSyncSetupStoreTestFixtures.swift`
- Tests:
  - `meAppTests/Features/ScaleSetup/AppSync/AppSyncSetupStoreTests.swift`

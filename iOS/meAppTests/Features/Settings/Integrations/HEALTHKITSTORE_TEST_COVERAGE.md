# HealthKitStore Test Coverage Guide

## Purpose
This document explains how `HealthKitStore` is tested, what Health integration regressions are covered, and the current measured coverage.

## Files Involved
- Store under test:
  - `meApp/Features/Settings/Integrations/Stores/HealthKitStore.swift`
- Main test suite:
  - `meAppTests/Features/Settings/Integrations/HealthKitStoreTests.swift`
- Fixtures:
  - `meAppTests/Features/Settings/Integrations/Fixtures/HealthKitStoreTestFixtures.swift`
- Mocks:
  - `meAppTests/Features/Settings/Integrations/Mocks/MockHealthKitStoreDependencies.swift`
  - `meAppTests/Support/Mocks/Services/MockAccountService.swift`
  - `meAppTests/Features/GoalAlert/Mocks/MockNotificationHelperService.swift`
  - `meAppTests/Features/HealthKit/Mocks/MockKvStorageService.swift`

## Test Strategy
`HealthKitStore` tests use protocol-based injection:
1. Inject `AccountServiceProtocol`, `IntegrationServiceProtocol`, `HealthKitServiceProtocol`, `EntryServiceProtocol`, `NotificationHelperServiceProtocol`, and `LoggerServiceProtocol` through the test DI container.
2. Inject `KvStorageServiceProtocol` via initializer to control first-time modal behavior.
3. Validate both state transitions and side effects (alerts, toasts, loader, service call counts, persisted integration state).
4. Cover success and failure paths for authorization, sync, conflict handling, and app-foreground permission checks.

## Covered Flows
- Integration status loading and active-account mapping.
- Modal state transition notifications (`presented`/`dismissed`).
- Row-tap branching:
  - first-time flow
  - previously integrated flow by permission count
  - conflict branch and conflict-check error fallback
  - previously seen connect-screen continuation branches.
- Primary action handling by state:
  - `permissionsNotAllowed`
  - `permissionsAllowed`
  - `integrationComplete`
  - `integrationFailed`
  - `userConflict`.
- Permission-based return flows after opening Apple Health:
  - permissions granted -> complete
  - no permissions -> dismiss
  - conflict -> user conflict
  - conflict-check error fallback.
- Sync flow branches:
  - sync prompt cancel action
  - full sync success path
  - full sync failure path.
- Remove integration flow:
  - clear success
  - clear failure.
- Out-of-sync browser/foreground handling:
  - resolved on return (toast)
  - still out of sync (no toast).
- Error cases:
  - authorization failure
  - integration conflict
  - persist failure
  - sync failure
  - integration conflict-check failures.

## Run Tests
From repo root (`iOS`):

```bash
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppTests \
  -destination 'platform=iOS,id=<DEVICE_ID>' \
  -only-testing:meAppTests/HealthKitStoreTests \
  -enableCodeCoverage YES
```

## Check Coverage
```bash
xcrun xccov view --report <PATH_TO_XCRESULT> | rg 'HealthKitStore.swift'
```

## Team Expectation
- Keep `HealthKitStore.swift` coverage at **90%+**.
- Any Health integration behavior change must include regression tests for permission return flow, modal state transitions, sync prompt actions, and conflict/error mapping.

## Current Coverage
- `HealthKitStore.swift`: **95.54%** (664/695)
- Source: `/tmp/HealthKitStoreTests-device-7.xcresult` (March 3, 2026)

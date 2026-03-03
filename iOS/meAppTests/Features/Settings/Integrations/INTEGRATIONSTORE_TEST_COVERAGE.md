# IntegrationStore Test Coverage Guide

## Purpose
This document explains how `IntegrationStore` is tested, what integration-flow regressions are covered, and the current measured coverage.

## Files Involved
- Store under test:
  - `meApp/Features/Settings/Integrations/Stores/IntegrationStore.swift`
- Main test suite:
  - `meAppTests/Features/Settings/Integrations/IntegrationStoreTests.swift`
- Fixtures:
  - `meAppTests/Features/Settings/Integrations/Fixtures/IntegrationStoreTestFixtures.swift`
- Mocks:
  - `meAppTests/Features/Settings/Integrations/Mocks/MockIntegrationStoreService.swift`
  - `meAppTests/Support/Mocks/Services/MockAccountService.swift`
  - `meAppTests/Support/Mocks/Services/MockNetworkMonitor.swift`
  - `meAppTests/Features/GoalAlert/Mocks/MockNotificationHelperService.swift`

## Test Strategy
`IntegrationStore` tests use protocol-based injection:
1. Inject `AccountServiceProtocol`, `IntegrationServiceProtocol`, `NotificationHelperServiceProtocol`, and `LoggerServiceProtocol` through the test DI container.
2. Inject `NetworkMonitoring` via the `IntegrationStore` initializer to deterministically test online/offline paths.
3. Validate both state mapping and side effects (browser state, alerts, loader, service call counts, refresh handling).
4. Cover success and failure paths for connect/disconnect plus retry actions.

## Covered Flows
- Initial account-to-integration-list state mapping.
- Account publisher updates after account refresh/change.
- Connect path browser state handling (URL + presentation).
- No-network connect fallback alert path.
- Remove-confirmation flow and disconnect execution path.
- Remove failure branches:
  - no network (`HTTPError.noInternet`)
  - non-network failure with retry alert.
- Pending action result handling after `refreshAccounts()`:
  - connect success/failure
  - disconnect failure verification.
- Retry button behavior for both connect and disconnect failure alerts.
- Invalid integration detection flow:
  - disable-all action removing invalid providers
  - offline skip
  - one-time check per store lifecycle.
- Link-open error alert copy-link action path.

## Run Tests
From repo root (`iOS`):

```bash
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppTests \
  -destination 'platform=iOS,id=<DEVICE_ID>' \
  -only-testing:meAppTests/IntegrationStoreTests \
  -enableCodeCoverage YES
```

## Check Coverage
```bash
xcrun xccov view --report <PATH_TO_XCRESULT> | rg 'IntegrationStore.swift'
```

## Team Expectation
- Keep `IntegrationStore.swift` coverage at **90%+**.
- Any connect/disconnect behavior change must include regression tests for pending-action verification and retry handling.

## Current Coverage
- `IntegrationStore.swift`: **93.56%** (363/388)
- Source: `/tmp/IntegrationStoreTests-device-6.xcresult` (March 3, 2026)

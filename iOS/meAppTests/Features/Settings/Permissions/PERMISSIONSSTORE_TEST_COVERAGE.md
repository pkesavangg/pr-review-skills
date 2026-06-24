# PermissionsStore Test Coverage Guide

## Purpose
This document explains how `PermissionsStore` is tested, what behaviors are covered, and the coverage expectation for CI safety.

## Files Involved
- Store under test:
  - `meApp/Features/Settings/Permissions/Stores/PermissionsStore.swift`
- Main test suite:
  - `meAppTests/Features/Settings/Permissions/PermissionsStoreTests.swift`
- Fixtures:
  - `meAppTests/Features/Settings/Permissions/Fixtures/PermissionsStoreTestFixtures.swift`
- Mocks:
  - `meAppTests/Features/Settings/Permissions/Mocks/MockPermissionsService.swift`

## Test Strategy
`PermissionsStore` tests use protocol-based injection:
1. Inject `PermissionsServiceProtocol` to drive permission/required-category publishers.
2. Inject `LoggerServiceProtocol` through shared test DI setup.
3. Assert state updates for:
   - required category changes
   - Bluetooth authorization state
   - Bluetooth switch state
   - async request handlers (`handleBluetoothAuthorization`, `handleBluetoothSwitch`)
   - tap wrappers (`handleBluetoothAuthorizationTap`, `handleBluetoothSwitchTap`)
4. Cover denied and unavailable cases (`.DISABLED` and missing permission map).

## Covered Flows
- Initial Bluetooth state calculation during store init.
- Manual Bluetooth state refresh via `updateBluetoothPermissions()`.
- Required-category publisher updates.
- Permission-map publisher updates triggering Bluetooth refresh.
- Bluetooth authorization request handling.
- Bluetooth switch request handling.
- Tap-handler async dispatch behavior for both authorization and switch.
- Error/edge outcomes where permission is denied or unavailable.

## Run Tests
From repo root (`iOS`):

```bash
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme 'meAppTests 1' \
  -destination 'id=<YOUR_DEVICE_ID>' \
  -only-testing:meAppTests/PermissionsStoreTests
```

## Check Coverage
```bash
xcrun xccov view --report <PATH_TO_XCRESULT> | rg 'PermissionsStore.swift'
```

## Team Expectation
- Keep `PermissionsStore.swift` coverage at **95%+** (minimum acceptable: **90%+**).
- Any logic change to permission-state mapping or request handlers must include matching test updates.

## Current Coverage
- `PermissionsStore.swift`: **98.78%** (81/82)
- Source: `Test-meAppTests 1-2026.03.03_13-04-52-+0530.xcresult` (March 3, 2026)

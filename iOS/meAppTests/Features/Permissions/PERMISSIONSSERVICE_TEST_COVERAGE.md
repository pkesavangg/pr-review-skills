# PermissionsService Test Coverage Guide

## Purpose
This document summarizes test coverage for `PermissionsService`, the mock strategy, and how to run the suite.

## Files Covered
- Service under test:
  - `meApp/Data/Services/PermissionsService.swift`
- Protocol:
  - `meApp/Domain/Services/PermissionsServiceProtocol.swift`
- Tests:
  - `meAppTests/Features/Permissions/PermissionsServiceTests.swift`
- Fixtures:
  - `meAppTests/Features/Permissions/Fixtures/PermissionsTestFixtures.swift`
- Mocks:
  - `meAppTests/Features/Permissions/Mocks/MockNotificationHelperService.swift`
  - `meAppTests/Features/Permissions/Mocks/MockPermissionSDKClient.swift`
  - `meAppTests/Features/Bluetooth/Mocks/MockScaleService.swift`
  - `meAppTests/Support/Mocks/Services/MockLoggerService.swift`

## Coverage Summary
- `PermissionsService.swift`: 93.45% (514/550)
- `PermissionsServiceTests.swift`: 100% (239/239)

## Flows Covered

### 1) Permission State Cache
- `setPermissions`: stores full permission map
- `updatePermission`: creates map if nil, merges updates
- `getPermissionState`: reads cached state
- `permissionsPublisher`: emits updates on map changes

### 2) Permission Requesting
- `permissionRequest`: maps SDK enabled/disabled results
- Unknown/unavailable SDK result defaults to `.DISABLED`

### 3) Central Permission Handler
- `handlePermission(.notification)` ignore path
- `handlePermission(.bluetoothSwitch)` exit path
- `handlePermission(.bluetooth)` permissions-request path
- `handlePermission(.locationSwitch)` includes `why` flow and re-prompt handling
- `handlePermission(.location)` permissions-request path
- `handlePermission(.camera)` allow path
- `handlePermission(.wifiSwitch)` and `.internet` share Wi-Fi flow

### 4) Required Categories
- Initial required categories derived from scale types
- Required categories cleared when no scales remain
- Scale type handling covered for bluetooth/wifi/appsync/btWifiR4

### 5) Settings Navigation
- `navigateToWifiSettings` delegates to SDK client with `.WIFI_SWITCH`

## Edge Cases Covered
- Denied permission states returned from cache
- Unknown/unavailable request response mapped to disabled
- Recursive location "Why" prompt flow resolves correctly

## How To Run
Run from repo root (`meApp`):

```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'platform=iOS,id=<DEVICE_ID>' \
  -only-testing:meAppTests/PermissionsServiceTests
```

## Team Expectation
- Keep `PermissionsService` coverage >= 85%
- Add regression tests whenever permission request/state logic changes

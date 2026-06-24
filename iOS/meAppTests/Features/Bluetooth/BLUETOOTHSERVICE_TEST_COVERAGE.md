# BluetoothService Test Coverage Guide

## Purpose
This document explains how `BluetoothService` is tested, what flows are covered, and how to extend tests safely.

## Files Involved
- Service under test:
  - `meApp/Data/Services/BluetoothService.swift` (main type)
  - `meApp/Data/Services/BluetoothServiceCoreOperations.swift`
  - `meApp/Data/Services/BluetoothServiceScanEventPipeline.swift`
  - `meApp/Data/Services/BluetoothServiceEventAlerts.swift`
  - `meApp/Data/Services/BluetoothServiceDeviceProfileUtils.swift`
  - `meApp/Data/Services/BluetoothServiceDeviceInfo.swift`
  - `meApp/Data/Services/BluetoothServiceHelpers.swift`
- Protocol:
  - `meApp/Domain/Services/BluetoothServiceProtocol.swift`
- Main test suite:
  - `meAppTests/Features/Bluetooth/BluetoothServiceTests.swift`
- Bluetooth-specific mocks:
  - `meAppTests/Features/Bluetooth/Mocks/MockBLEDiscoveryManager.swift`
  - `meAppTests/Features/Bluetooth/Mocks/MockScaleService.swift`
- Shared test support:
  - `meAppTests/Support/Mocks/Services/MockAccountService.swift`
  - `meAppTests/Support/Mocks/Services/MockEntryService.swift`
  - `meAppTests/Support/Mocks/Services/MockLoggerService.swift`
  - `meAppTests/Features/Account/Fixtures/AccountTestFixtures.swift` (for account model)

## Coverage Strategy
`BluetoothService` is a large service split across multiple extension files. Coverage is improved by testing each method with:
1. Success path
2. Guard/validation failures (no active account, invalid broadcast ID, device not connected)
3. Error handling (BluetoothServiceError cases)
4. State transitions (skip devices, blocked broadcast IDs, scan state)

## Flows Covered

### 1) Discovery and Scan Control
- `stopScan`: delegates to discovery manager, clears scan state
- `clearDevices`: delegates to discovery manager, clears skip devices
- `pauseSmartScan`: delegates to discovery manager
- `resumeSmartScan`: delegates to discovery manager with clearOnlyPairing flag
- `scanForPairing`: delegates to discovery manager
- `startBluetoothOperations`: no active account early return, clears devices when account present
- `startSmartScan`: no active account throws error

### 2) Account Lifecycle Integration
- Account cleared while scan started: service stops scan automatically
- Account update handler: stops scan when account becomes nil

### 3) Device Pairing and Management
- `confirmSmartPair`: invalid broadcast ID returns error
- `disconnectDevice`: success adds to skip list and blocks broadcast ID, sets modal visibility
- `reapplySkipDevicesExcludingPaired`: removes paired device IDs from skip list

### 4) Device Information
- `getDeviceInfo`: device not connected returns deviceNotConnected error

## Flows Not Yet Covered (Future Work)

### Core Operations
- `scan`: success, error handling
- `addNewDevice`: success, duplicate check, error handling
- `deleteDevice`: success, disconnect flag, error handling
- `deleteCurrentUserFromScaleIfPossible`: success, error handling

### Wi-Fi Operations
- `getWifiList`: success, device not connected, error handling
- `setupWifi`: success, error handling
- `getConnectedWifiSSID`: success, error handling
- `getWifiMacAddress`: success, error handling

### Live Measurement
- `startLiveMeasurement`: success, error handling
- `stopLiveMeasurement`: success, error handling

### Settings and Firmware
- `updateSetting`: success, error handling
- `updateFirmware`: success, error handling
- `clearData`: success, error handling

### User Management
- `updateUserProfileForR4Scales`: success, error handling
- `updateAccount`: success, error handling
- `getScaleUserList`: success, skip connection check, error handling
- `deleteUserByToken`: success, disconnect flag, error handling
- `deleteScaleByBroadcastId`: success, disconnect flag, error handling

### Device Info and Logs
- `getDeviceLogs`: success, error handling
- `getMeasurementLiveData`: success, error handling
- `updateWeightOnlyMode`: success, error handling
- `clearScaleDiscoveredInfo`: success
- `disconnectConnectedScales`: success
- `deleteR4Scales`: success, error handling

### Event Handling
- `handleDeviceEventAlert`: duplicate user, reconnect scenarios
- `handleWeightOnlyModeAlertDismissed`: success
- Smart scan event pipeline: NEW_DEVICE, entries, connect/disconnect, alerts, WiFi, weight-only

## How `makeSUT` Works
`makeSUT` in `BluetoothServiceTests` builds `BluetoothService` with mock dependencies:
- Account service mock
- Scale service mock
- Entry service mock
- Logger service mock
- Discovery manager mock

All dependencies are optional with sensible defaults, allowing tests to focus on specific scenarios by injecting only the mocks needed.

## How To Add New BluetoothService Tests
1. Identify the method/flow to test (check protocol and implementation files)
2. Create or extend mocks if needed (e.g., `MockBLEDiscoveryManager`, `MockScaleService`)
3. Configure mock state/results for the scenario
4. Call method under test
5. Assert:
   - Returned result or thrown error
   - State transitions (skipDevices, blockedBroadcastIds, scan state)
   - Side effects (discovery manager call counts, scale service updates)

### Error Assertion Pattern
When validating BluetoothServiceError cases:
```swift
switch result {
case .success:
    Issue.record("Expected failure")
case .failure(let error):
    guard case .invalidBroadcastId = error else {
        Issue.record("Expected invalidBroadcastId, got \(error)")
        return
    }
}
```

For throwing methods:
```swift
do {
    try await sut.startSmartScan()
    Issue.record("Expected noActiveAccount")
} catch {
    guard case .noActiveAccount = error as? BluetoothServiceError else {
        Issue.record("Expected noActiveAccount, got \(error)")
        return
    }
}
```

## Run and Check Coverage
Run from **repo root** (`meApp`).

**Simulator:**
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:meAppTests/BluetoothServiceTests
```

**Physical device:**
```bash
export DEVICE_ID=$(xcrun xctrace list devices 2>/dev/null | grep -E "iPhone|iPad" | head -1 | sed -n 's/.*(\([^)]*\)).*/\1/p')
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination "platform=iOS,id=$DEVICE_ID" \
  -only-testing:meAppTests/BluetoothServiceTests
```
Device must be connected, unlocked, and trusted.

Coverage in Xcode:
1. Test Report (`Cmd+9`)
2. Open latest run
3. Coverage tab
4. Expand target/file list and inspect:
   - `meApp/Data/Services/BluetoothService.swift`
   - `meApp/Data/Services/BluetoothServiceCoreOperations.swift`
   - `meApp/Data/Services/BluetoothServiceScanEventPipeline.swift`
   - `meApp/Data/Services/BluetoothServiceEventAlerts.swift`
   - `meApp/Data/Services/BluetoothServiceDeviceProfileUtils.swift`
   - `meApp/Data/Services/BluetoothServiceDeviceInfo.swift`
5. Confirm coverage is at least 80%

## Team Expectation
- Keep BluetoothService coverage at least **80%**
- For device discovery and connection flows, aim for **85%+**
- Every bug fix in BluetoothService should add a regression test
- Focus on critical paths: pairing, device management, Wi-Fi setup, firmware updates

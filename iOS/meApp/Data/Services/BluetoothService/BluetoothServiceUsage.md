# BluetoothService Usage Guide

## Overview

The `BluetoothService` is split into a main type in `Data/Services/BluetoothService/BluetoothService.swift` and several **extensions** in the same folder. The protocol is `BluetoothServiceProtocol` in `Domain/Services/BluetoothServiceProtocol.swift`. This doc describes the implementation and each extension file.

## Extension File Map

| File | Responsibility |
|------|----------------|
| **BluetoothService.swift** | Core type: state, publishers, subjects, init, `startBluetoothOperations`, scale/account handlers, `stopScan`, `clearDevices`, DI. |
| **BluetoothServiceCoreOperations.swift** | Scan control, sync, CRUD (add/pair/delete), Wi‑Fi, live measurement, settings, firmware, profile, user list. |
| **BluetoothServiceScanEventPipeline.swift** | Smart scan start, `handleSmartScaleData` (NEW_DEVICE, entries, connect/disconnect, alerts, WiFi, weight‑only), entry save, weight‑only alert. |
| **BluetoothServiceHelpers.swift** | Mapping (`mapToGGBTDevice`, `mapToGGPreference`, `mapDeviceDetailsToDevice`), parsing (WiFi, permission), hex/int, timeout, protocol/scale type. |
| **BluetoothServiceEventAlerts.swift** | Device event alerts: reconnect, duplicate user, `findUserToDelete`, `deleteUserByToken` / `deleteScaleByBroadcastId`. |
| **BluetoothServiceDeviceProfileUtils.swift** | Scale type, disconnect deleted scales, age/height, `createScanData`, `getProfileInfo`, weight-by-protocol, `disconnectDevice`, `reapplySkipDevicesExcludingPaired`. |
| **BluetoothServiceDeviceInfo.swift** | Device info, logs, live data, weight‑only setting update, `clearScaleDiscoveredInfo`, `disconnectConnectedScales`, `deleteR4Scales`. |

## Architecture

```
┌─────────────────┐    ┌─────────────────────────────────────────────────────────┐    ┌─────────────────────┐
│   ViewModels    │───▶│ BluetoothService (BluetoothServiceProtocol)                │───▶│ GGBluetoothSDK      │
│                 │    │ ├ BluetoothService.swift (state, init, subscriptions)     │    │                     │
│                 │    │ ├ CoreOperations (scan/sync/CRUD/WiFi/live/settings/OTA)  │    │                     │
│                 │    │ ├ ScanEventPipeline (scan callback → events, entries)      │    │                     │
│                 │    │ ├ Helpers, EventAlerts, DeviceProfileUtils, DeviceInfo    │    │                     │
└─────────────────┘    └─────────────────────────────────────────────────────────┘    └─────────────────────┘
```

## Key Features

- Swift-native models, no SDK type leakage
- Combine publishers for reactive UI
- Async/await for operations
- Device discovery, pairing, Wi‑Fi, firmware, profile sync, live measurement
- Error handling via `BluetoothServiceError` and `Result<_, BluetoothServiceError>`

## API Convention

All async operations return `Result<T, BluetoothServiceError>` (no `throws`). Use `switch result` or `if case .success(let value) = result` to handle.

## Basic Usage

### 1. Initialize the Service

```swift
// Prefer DI; legacy singleton still available
let bluetoothService = BluetoothService.shared
bluetoothService.initialize()
```

*Defined in:* `BluetoothService.swift` (init, `initialize()`).

### 2. Subscribe to Device Discovery

```swift
bluetoothService.deviceDiscoveredPublisher
    .sink { event in
        switch (event.protocolType, event.isNew) {
        case (.A6, true):  self.handleNewA6Scale(event.device)
        case (.A6, false): self.handleKnownA6ScaleDuringSetup(event.device)
        case (.A3, true):  self.handleNewA3Scale(event.device)
        case (.R4, true):  self.handleNewSmartWifiScale(event.device)
        case (.R4, false): self.handleKnownSmartScaleDuringSetup(event.device)
        default: break
        }
    }
    .store(in: &cancellables)
```

*Implemented in:* `BluetoothServiceScanEventPipeline` — `handleNewDevice` sends to `deviceDiscoveredSubject`. Main class exposes `deviceDiscoveredPublisher`.

### 3. Handle New Entries

```swift
bluetoothService.newEntryReceivedPublisher
    .sink { entry in
        self.processNewEntry(entry)
    }
    .store(in: &cancellables)
```

*Implemented in:* `BluetoothServiceScanEventPipeline` — `saveEntries` / `convertGGEntry`; sends `EntryNotification` via `newEntryReceivedSubject`.

### 4. Monitor Weight-Only Mode Alerts

```swift
bluetoothService.showWeightOnlyModeAlertPublisher
    .sink { shouldShow in
        if shouldShow { self.showWeightOnlyModeAlert() }
    }
    .store(in: &cancellables)
```

*Implemented in:* `BluetoothServiceScanEventPipeline` — `checkCanShowWeightOnlyModeAlert()`, `handleWeightOnlyModeAlertDismissed()`; `BluetoothServiceDeviceProfileUtils` / `DeviceInfo` for disconnect and status updates.

### 5. Live Measurement Streaming

```swift
_ = await bluetoothService.startLiveMeasurement(for: device)
if case .success(let liveData) = await bluetoothService.getMeasurementLiveData(broadcastId: device.broadcastId ?? "") {
    print("Weight: \(liveData.weight) kg")
}
_ = await bluetoothService.stopLiveMeasurement(for: device)
```

*Implemented in:* `BluetoothServiceCoreOperations` (start/stop live measurement); `BluetoothServiceDeviceInfo` (`getMeasurementLiveData`); live events from SDK in `BluetoothServiceScanEventPipeline` (LIVE_MEASUREMENT → `liveMeasurementSubject`).

## Device Management

### Adding a New Device

```swift
let result = await bluetoothService.addNewDevice(discoveredDevice, metaData: nil, false)
switch result {
case .success(let savedDevice):
    print("Device saved: \(savedDevice.deviceName ?? "Unknown")")
case .failure(let error):
    print("Failed to add device: \(error.localizedDescription)")
}
```

*Implemented in:* `BluetoothServiceCoreOperations.addNewDevice`.

### Pairing

```swift
let result = await bluetoothService.confirmSmartPair(
    device: device, token: token, displayName: displayName, userNumber: nil
)
switch result {
case .success(let response):
    switch response {
    case .creationCompleted: print("Pairing successful!")
    case .memoryFull: print("Device memory is full")
    case .duplicateUserError: print("User already exists on device")
    default: print("Pairing result: \(response)")
    }
case .failure(let error):
    print("Pairing error: \(error.localizedDescription)")
}
```

*Implemented in:* `BluetoothServiceCoreOperations.confirmSmartPair`.

### Deleting a Device

```swift
let result = await bluetoothService.deleteDevice(device, disconnect: true)
switch result {
case .success(let response):
    if response == .success { print("Device deleted successfully") }
case .failure(let error):
    print("Failed to delete device: \(error.localizedDescription)")
}
```

*Implemented in:* `BluetoothServiceCoreOperations.deleteDevice`. Event-driven delete by token: `BluetoothServiceEventAlerts.deleteScaleByBroadcastId` / `deleteUserByToken`.

## Wi-Fi Configuration

```swift
// Get networks: Result<[WifiDetails], BluetoothServiceError>
let listResult = await bluetoothService.getWifiList(for: device)
if case .success(let networks) = listResult { self.availableNetworks = networks }

// Setup: Result<WifiSetupResponse, BluetoothServiceError>
let config = WifiConfig(ssid: ssid, password: password)
let setupResult = await bluetoothService.setupWifi(on: device, config: config)
```

- `getWifiList(for:)`, `setupWifi(on:config:)` → `BluetoothServiceCoreOperations`
- `cancelWifi(on:)`, `getConnectedWifiSSID(broadcastId:)`, `getWifiMacAddress(for:)` → `BluetoothServiceCoreOperations`
- WiFi status updates from scan: `BluetoothServiceScanEventPipeline.handleWifiStatusUpdate`.

## Device Settings & Firmware

- `updateSetting(on:settings:)` → `Result<Void, BluetoothServiceError>`
- `updateFirmware(on:timestamp:)` → `Result<Void, BluetoothServiceError>` (progress via `firmwareUpdateProgressPublisher`)
- `clearData(on:dataType:)` → `BluetoothServiceCoreOperations`

## Scanning Control

- `scan()` (fire-and-forget), `resyncAndScan()` → `Result<Void, BluetoothServiceError>` in `BluetoothServiceCoreOperations`
- `pauseSmartScan()`, `resumeSmartScan(clearOnlyPairing:)`, `scanForPairing()` → `BluetoothServiceCoreOperations`
- `stopScan()`, `clearDevices()` → `BluetoothService.swift`

Scan callback and event routing: `BluetoothServiceScanEventPipeline.startSmartScan`, `handleSmartScaleData`. Discovery lifecycle: `BLEDiscoveryManager` used from main class.

## Error Handling

All async methods return `Result<_, BluetoothServiceError>`; none throw. Use `BluetoothServiceError` in switch/case or `if case .failure(let error)`.

## Extension Summary Table

| Extension | Main APIs / Concepts |
|-----------|------------------------|
| **CoreOperations** | `scan`, `resyncAndScan`, `pauseSmartScan`, `resumeSmartScan`, `scanForPairing`, `syncDevices`, `addNewDevice`, `confirmSmartPair`, `deleteDevice`, `deleteCurrentUserFromScaleIfPossible`, `getWifiList`, `setupWifi`, `cancelWifi`, `getConnectedWifiSSID`, `getWifiMacAddress`, `startLiveMeasurement`, `stopLiveMeasurement`, `updateSetting`, `updateFirmware`, `clearData`, `updateUserProfileForR4Scales`, `updateAccount`, `getScaleUserList` |
| **ScanEventPipeline** | `startSmartScan`, `handleSmartScaleData` (NEW_DEVICE, SINGLE_ENTRY, MULTI_ENTRIES, DEVICE_CONNECTED/DISCONNECTED, DEVICE_MEMORY_FULL, DEVICE_DUPLICATE_USER, WIFI_STATUS_UPDATE, DEVICE_INFO_UPDATE, PERMISSION_STATUS, LIVE_MEASUREMENT), `handleNewDevice`, `saveEntries`, `convertGGEntry`, `checkCanShowWeightOnlyModeAlert`, `handleWeightOnlyModeAlertDismissed`, weight-only status sync on connect/disconnect |
| **Helpers** | `mapToGGBTDevice`, `mapToGGPreference`, `parseWifiStatus`, `parsePermissionStatus`, `convertHexToInt`, `convertIntToHex`, `roundMetric`, `mapProtocolToScaleType`, `mapDeviceDetailsToDevice`, `withTimeout` |
| **EventAlerts** | `handleDeviceEventAlert`, `findUserToDelete`, `deleteUserByToken`, `deleteScaleByBroadcastId` |
| **DeviceProfileUtils** | `getSafeScaleType`, `disconnectDeletedScales`, `createScanData`, `getProfileInfo`, `getWeightByProtocolType`, `disconnectDevice`, `reapplySkipDevicesExcludingPaired` |
| **DeviceInfo** | `getDeviceInfo`, `getDeviceLogs`, `getMeasurementLiveData`, `updateWeightOnlyMode(on:)`, `clearScaleDiscoveredInfo`, `disconnectConnectedScales`, `deleteR4Scales` |

## Best Practices & Testing

- Use `BluetoothServiceProtocol` for mocks; publishers emit on main thread; call async methods from appropriate context.
- For unit tests, mock the protocol; implementation details are spread across the main file and extensions but the public API is unchanged.

## Implementation Notes

- **Public API**: `BluetoothServiceProtocol`; call sites use the protocol.
- **Internal**: Logic is split by concern into the extension files above; `BluetoothService.swift` holds state, subjects, and high-level flow (`startBluetoothOperations`, account/scale handling, `stopScan`, `clearDevices`).

## Troubleshooting

**Common issues:** device not appearing (permissions, pairing mode), connection failures, Wi‑Fi setup, firmware failures. Use `LoggerService` and the `tag` used in each extension for logs (all use the same `tag` from the main class).

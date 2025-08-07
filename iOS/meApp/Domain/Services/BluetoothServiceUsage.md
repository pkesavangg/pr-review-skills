# BluetoothService Usage Guide

## Overview

The `BluetoothService` provides a comprehensive interface for managing Bluetooth-enabled smart scales. It's designed with a clean architecture that separates the app's business logic from the underlying SDK implementation.

## Key Features

- ✅ Swift-native models with no SDK type leakage
- ✅ Combine publishers for reactive UI updates
- ✅ Async/await support for modern Swift concurrency
- ✅ Comprehensive error handling
- ✅ Device discovery and pairing
- ✅ Wi-Fi configuration for smart scales
- ✅ Firmware updates with progress tracking
- ✅ User profile synchronization
- ✅ Real-time live measurement streaming

## Architecture

```
┌─────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│   ViewModels    │───▶│ BluetoothService    │───▶│ GGBluetoothSDK      │
│                 │    │ (Protocol)          │    │                     │
└─────────────────┘    └─────────────────────┘    └─────────────────────┘
        │                        │                          │
        ▼                        ▼                          ▼
┌─────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│   SwiftUI Views │    │ App Models          │    │ SDK Models          │
│                 │    │ (BluetoothModels)   │    │                     │
└─────────────────┘    └─────────────────────┘    └─────────────────────┘
```

## Basic Usage

### 1. Initialize the Service

```swift
let bluetoothService = BluetoothService.shared

// Initialize the service (typically in your app's startup)
bluetoothService.initialize()
```

### 2. Subscribe to Device Discovery

```swift
class ScaleSetupViewModel: ObservableObject {
    private var cancellables = Set<AnyCancellable>()
    
    func setupSubscriptions() {
        bluetoothService.deviceDiscoveredPublisher
            .sink { event in
                switch (event.protocolType, event.isNew) {
                case (.A6, true):
                    self.handleNewA6Scale(event.device)
                case (.A6, false):
                    self.handleKnownA6ScaleDuringSetup(event.device)
                case (.A3, true):
                    self.handleNewA3Scale(event.device)
                case (.R4, true):
                    self.handleNewSmartWifiScale(event.device)
                case (.R4, false):
                    self.handleKnownSmartScaleDuringSetup(event.device)
                default:
                    break
                }
            }
            .store(in: &cancellables)
    }
}
```

### 3. Handle New Entries

```swift
func subscribeToNewEntries() {
    bluetoothService.newEntryReceivedPublisher
        .sink { entry in
            // Handle new weight measurements
            self.processNewEntry(entry)
        }
        .store(in: &cancellables)
}
```

### 4. Monitor Weight-Only Mode Alerts

```swift
func subscribeToWeightOnlyModeAlerts() {
    bluetoothService.showWeightOnlyModeAlertPublisher
        .sink { shouldShow in
            if shouldShow {
                self.showWeightOnlyModeAlert()
            }
        }
        .store(in: &cancellables)
}
```

### 5. Live Measurement Streaming

```swift
func streamLiveMeasurement(for device: Device) async {
    // Start live measurement
    _ = await bluetoothService.startLiveMeasurement(for: device)

    // Fetch the latest snapshot (optional)
    if case let .success(liveData) = await bluetoothService.getMeasurementLiveData(broadcastId: device.broadcastId) {
        print("Weight: \(liveData.weight) kg")
    }

    // Stop live measurement when finished
    _ = await bluetoothService.stopLiveMeasurement(for: device)
}
```

## Device Management

### Adding a New Device

```swift
func addDevice(_ discoveredDevice: Device) async {
    do {
        let savedDevice = try await bluetoothService.addNewDevice(
            discoveredDevice, 
            metaData: nil
        )
        print("Device saved: \(savedDevice.deviceName ?? "Unknown")")
    } catch {
        print("Failed to add device: \(error.localizedDescription)")
    }
}
```

### Pairing with a Device

```swift
func pairWithDevice(_ device: Device, token: String, displayName: String) async {
    do {
        let result = try await bluetoothService.confirmSmartPair(
            device: device,
            token: token,
            displayName: displayName,
            userNumber: nil
        )
        
        switch result {
        case .creationCompleted:
            print("Pairing successful!")
        case .memoryFull:
            print("Device memory is full")
        case .duplicateUserError:
            print("User already exists on device")
        default:
            print("Pairing failed: \(result)")
        }
    } catch {
        print("Pairing error: \(error.localizedDescription)")
    }
}
```

### Deleting a Device

```swift
func removeDevice(_ device: Device) async {
    do {
        let result = try await bluetoothService.deleteDevice(device, disconnect: true)
        if result == .success {
            print("Device deleted successfully")
        }
    } catch {
        print("Failed to delete device: \(error.localizedDescription)")
    }
}
```

## Wi-Fi Configuration

### Getting Available Networks

```swift
func getWifiNetworks(for device: Device) async {
    do {
        let networks = try await bluetoothService.getWifiList(for: device)
        self.availableNetworks = networks
    } catch {
        print("Failed to get Wi-Fi networks: \(error.localizedDescription)")
    }
}
```

### Setting up Wi-Fi

```swift
func setupWifi(on device: Device, ssid: String, password: String) async {
    let wifiConfig = WifiConfig(ssid: ssid, password: password)
    
    do {
        try await bluetoothService.setupWifi(on: device, config: wifiConfig)
        print("Wi-Fi setup initiated")
    } catch {
        print("Wi-Fi setup failed: \(error.localizedDescription)")
    }
}
```

## Device Settings

### Updating Settings

```swift
func enableBodyMetrics(on device: Device) async {
    let settings = [
        DeviceSetting(key: "SESSION_IMPEDANCE", value: .bool(true))
    ]
    
    do {
        try await bluetoothService.updateSetting(on: device, settings: settings)
        print("Body metrics enabled")
    } catch {
        print("Failed to update settings: \(error.localizedDescription)")
    }
}
```

### Firmware Updates

```swift
func updateFirmware(on device: Device) async {
    let timestamp = UInt32(Date().timeIntervalSince1970)
    
    // Subscribe to progress updates
    bluetoothService.firmwareUpdateProgressPublisher
        .sink { status in
            self.updateProgress = status.progress
            if status.isComplete {
                print("Firmware update completed!")
            }
        }
        .store(in: &cancellables)
    
    do {
        try await bluetoothService.updateFirmware(on: device, timestamp: timestamp)
    } catch {
        print("Firmware update failed: \(error.localizedDescription)")
    }
}
```

## Scanning Control

### Manual Scanning Control

```swift
// Pause scanning (e.g., when showing pairing UI)
bluetoothService.pauseSmartScan()

// Resume scanning
bluetoothService.resumeSmartScan(clearOnlyPairing: false)

// Scan specifically for pairing
bluetoothService.scanForPairing()

// Stop all scanning
bluetoothService.stopScan()
```

### Resync and Scan

```swift
func refreshDevices() async {
    do {
        try await bluetoothService.resyncAndScan()
        print("Devices resynced successfully")
    } catch {
        print("Resync failed: \(error.localizedDescription)")
    }
}
```

## Error Handling

The service uses a comprehensive `BluetoothServiceError` enum:

```swift
func handleBluetoothError(_ error: Error) {
    if let bluetoothError = error as? BluetoothServiceError {
        switch bluetoothError {
        case .noActiveAccount:
            // Prompt user to log in
            showLoginPrompt()
        case .invalidBroadcastId:
            // Device ID is invalid
            showDeviceErrorAlert()
        case .scanFailed(let underlyingError):
            // Scanning failed
            print("Scan failed: \(underlyingError.localizedDescription)")
        case .bluetoothUnavailable:
            // Bluetooth is off
            showBluetoothSettingsPrompt()
        default:
            // Handle other errors
            showGenericErrorAlert(bluetoothError.localizedDescription)
        }
    }
}
```

## Best Practices

### 1. Memory Management

```swift
class MyViewModel: ObservableObject {
    private var cancellables = Set<AnyCancellable>()
    
    deinit {
        // Cancellables are automatically cleaned up
        // No explicit cleanup needed
    }
}
```

### 2. UI Thread Safety

All publishers emit on the main thread, but async methods should be called from background queues when appropriate:

```swift
func performHeavyOperation() {
    Task {
        // This runs on a background queue
        do {
            let result = try await bluetoothService.getDeviceInfo(for: device)
            
            // UI updates automatically happen on main thread via publishers
            await MainActor.run {
                self.deviceInfo = result
            }
        } catch {
            // Handle error
        }
    }
}
```

### 3. Error Recovery

```swift
func retryableOperation() async {
    let maxRetries = 3
    var retryCount = 0
    
    while retryCount < maxRetries {
        do {
            try await bluetoothService.resyncAndScan()
            return // Success
        } catch {
            retryCount += 1
            if retryCount >= maxRetries {
                // Final failure
                handleFinalError(error)
                return
            }
            
            // Wait before retry
            try? await Task.sleep(nanoseconds: 2_000_000_000) // 2 seconds
        }
    }
}
```

## Testing

The `BluetoothServiceProtocol` makes unit testing easy:

```swift
class MockBluetoothService: BluetoothServiceProtocol {
    var isScanning: Bool = false
    var canShowScaleDiscoveredModal: Bool = true
    var isSetupInProgress: Bool = false
    
    // Implement other protocol requirements with test data
    
    func addNewDevice(_ device: Device, metaData: DeviceMetaData?) async throws -> Device {
        // Return test device
        return device
    }
    
    // ... other mock implementations
}
```

## Migration from TypeScript Service

If you're migrating from the TypeScript service:

1. Replace `BehaviorSubject` with `@Published` properties or `PassthroughSubject`
2. Convert callback-based APIs to async/await
3. Replace any UI alerts/toasts with publisher emissions
4. Use Swift-native error handling instead of try/catch with generic errors

## Troubleshooting

### Common Issues

1. **Device not appearing**: Check Bluetooth permissions and ensure device is in pairing mode
2. **Connection failures**: Verify device is not already connected to another app
3. **Wi-Fi setup issues**: Ensure device supports Wi-Fi and is in range of network
4. **Firmware update failures**: Check device battery level and connection stability

### Debug Logging

Enable debug logging to troubleshoot issues:

```swift
// The logger service will show detailed Bluetooth operations
// Check console output for detailed error information
``` 

import Foundation
import Combine


/// Protocol defining the service interface for managing Bluetooth-enabled scales and related operations.
///
/// This protocol is a Swift counterpart to `bluetooth.service.ts` in the Angular codebase.
/// It focuses on high-level orchestration and business logic, leaving the underlying Bluetooth /
/// plugin implementation to concrete service classes (e.g. `BluetoothService`).
///
/// Similar to `AccountServiceProtocol`, the API surface is grouped into logical sections for clarity.
@MainActor
protocol BluetoothServiceProtocol {

    // MARK: - State
    /// Indicates whether a smart scan is currently in progress.
    var isScanning: Bool { get }

    /// Flag that determines if UI modals can be shown for newly discovered scales.
    var canShowScaleDiscoveredModal: Bool { get }

    // MARK: - Publishers
    /// Publisher for unified device discovery events containing device, protocol type, and isNew flag.
    var deviceDiscoveredPublisher: AnyPublisher<DeviceDiscoveryEvent, Never> { get }

    /// Publisher for device metadata updates.
    var deviceInfoUpdatedPublisher: AnyPublisher<DeviceInfo, Never> { get }

    /// Publisher for weight-only mode alert visibility.
    var showWeightOnlyModeAlertPublisher: AnyPublisher<Bool, Never> { get }

    /// Publisher for new entry events.
    var newEntryReceivedPublisher: AnyPublisher<Entry, Never> { get }

    /// Indicates whether a setup is currently in progress.
    var isSetupInProgress: Bool { get set }

    // MARK: - Lifecycle / Initialisation
    /// Initializes the Bluetooth service.
    func initialize()

    /// Stops all ongoing Bluetooth operations and scanning.
    func stopScan()

    /// Clears all devices from the underlying Bluetooth plugin / cache.
    func clearDevices()

    // MARK: - Scanning & Pairing

    /// Pauses the current smart scan without tearing down the session.
    func pauseSmartScan()

    /// Resumes a previously paused smart scan.
    /// - Parameter clearOnlyPairing: When true, clears only pairing-mode devices before resuming.
    func resumeSmartScan(clearOnlyPairing: Bool)

    /// Performs a dedicated scan intended for scale pairing.
    func scanForPairing()

    // MARK: - Device Synchronisation
    /// Forces a re-sync of locally stored devices with the Bluetooth plugin and re-starts scanning.
    func resyncAndScan() async throws

    /// Synchronises the provided device list with the Bluetooth plugin.
    /// - Parameter devices: The devices to sync. Passing an empty array clears the list.
    func syncDevices(_ devices: [Device])

    // MARK: - Device CRUD
    /// Adds a newly discovered scale to persistent storage and returns the saved model.
    func addNewDevice(_ device: Device, metaData: DeviceMetaData?) async throws -> Device

    /// Deletes a scale from storage (and optionally from the physical device).
    /// - Parameters:
    ///   - device: The device to delete.
    ///   - disconnect: Whether to actively disconnect before deletion.
    func deleteDevice(_ device: Device, disconnect: Bool) async throws -> UserDeletionResponse

    /// Disconnects the specified device without deleting it from storage.
    /// - Parameter broadcastId: The broadcast ID of the device to disconnect.
    func disconnectDevice(broadcastId: String) async throws

    // MARK: - WiFi Configuration
    /// Retrieves the available Wi-Fi networks from the given device.
    func getWifiList(for device: Device) async throws -> [WifiDetails]

    /// Configures Wi-Fi on the given device.
    func setupWifi(on device: Device, config: WifiConfig) async throws

    /// Cancels a pending Wi-Fi configuration.
    func cancelWifi(on device: Device) async throws

    /// Retrieves the currently connected Wi-Fi SSID for an R4 scale.
    func getConnectedWifiSSID(broadcastId: String) async throws -> String

    // MARK: - Settings & Firmware
    /// Updates a list of settings on the device.
    func updateSetting(on device: Device, settings: [DeviceSetting]) async throws

    /// Initiates a firmware update on the device.
    func updateFirmware(on device: Device, timestamp: UInt32) async throws

    /// Clears stored data on the device (e.g., history, user).
    func clearData(on device: Device, dataType: DeviceClearType) async throws

    // MARK: - Profile / Account Operations
    /// Updates the user profile (height, weight, age, etc.) on all connected R4 scales.
    func updateUserProfileForR4Scales() async throws -> Bool

    /// Updates account-specific preferences (display name, metrics, etc.) on the device.
    func updateAccount(on device: Device, preference: R4ScalePreference) async throws -> UserCreationResponse

    // MARK: - Device Information
    /// Retrieves generic device information (model, serial, firmware, …).
    func getDeviceInfo(for device: Device) async throws -> DeviceInfo

    /// Retrieves the Wi-Fi MAC address for an R4 scale.
    func getWifiMacAddress(for device: Device) async throws -> String

    /// Retrieves live measurement data while a user is on the scale.
    func getMeasurementLiveData(broadcastId: String) async throws -> MeasurementLiveData

    /// Retrieves the list of users stored on the scale (R4 only).
    func getScaleUserList(for device: Device) async throws -> [DeviceUser]

    // MARK: - Alerts & Utility
    /// Triggers the in-app alert required when weight-only mode is enabled by another user.
    func updateWeightOnlyMode(on device: Device?) async throws
}

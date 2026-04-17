import Combine
import Foundation
import GGBluetoothSwiftPackage

/// Protocol defining the service interface for managing Bluetooth-enabled scales and related operations.
///
/// This protocol is a Swift counterpart to `bluetooth.service.ts` in the Angular codebase.
/// It focuses on high-level orchestration and business logic, leaving the underlying Bluetooth /
/// plugin implementation to concrete service classes (e.g. `BluetoothService`).
///
@MainActor
protocol BluetoothServiceProtocol {

    // MARK: - State

    /// Flag that determines if UI modals can be shown for newly discovered scales.
    var canShowScaleDiscoveredModal: Bool { get }

    /// Indicates whether a setup is currently in progress.
    var isSetupInProgress: Bool { get set }
    var skipDevices: [String] { get }
    var onOpenScaleSetup: ((DeviceSnapshot, DeviceDiscoveryEvent?, Bool, Bool) -> Void)? { get set }

    // MARK: - Publishers
    /// Publisher for unified device discovery events containing device, protocol type, and isNew flag.
    var deviceDiscoveredPublisher: AnyPublisher<DeviceDiscoveryEvent, Never> { get }

    /// Publisher for device metadata updates.
    var deviceInfoUpdatedPublisher: AnyPublisher<DeviceInfo, Never> { get }

    /// Publisher for weight-only mode alert visibility.
    var showWeightOnlyModeAlertPublisher: AnyPublisher<Bool, Never> { get }

    /// Publisher for new entry events.
    /// Uses EntryNotification (Sendable) to safely pass data across actor boundaries.
    var newEntryReceivedPublisher: AnyPublisher<EntryNotification, Never> { get }

    /// Publisher for firmware update progress.
    var firmwareUpdateProgressPublisher: AnyPublisher<FirmwareUpdateStatus, Never> { get }

    /// Publisher for live measurement data while a user is on the scale.
    var liveMeasurementPublisher: AnyPublisher<GGWeightEntry, Never> { get }

    /// Publisher for new BPM reading events received from a blood pressure monitor.
    var newBpmReadingReceivedPublisher: AnyPublisher<BpmMeasurement, Never> { get }

    /// Publisher for setup progress changes so cross-cutting services can defer disruptive UI until setup exits.
    var isSetupInProgressPublisher: AnyPublisher<Bool, Never> { get }

    // MARK: - Lifecycle / Initialisation
    /// Initializes the Bluetooth service and subscribes to account changes.
    func initialize()

    /// Stops all ongoing Bluetooth operations and scanning.
    func stopScan()
    func startBluetoothOperations() async
    func disconnectConnectedScales() async
    func reapplySkipDevicesExcludingPaired()
    func handleWeightOnlyModeAlertDismissed()

    /// Clears all devices from the underlying Bluetooth plugin / cache.
    func clearDevices()

    // MARK: - BPM Operations

    /// Starts a scan specifically targeting BPM (Blood Pressure Monitor) devices.
    func scanForBpm()

    /// Connects to a BPM device by its broadcast ID.
    /// - Parameters:
    ///   - broadcastId: The broadcast ID of the BPM device to connect.
    ///   - userNumber: The user slot selected in the app (1 or 2). Used by the SDK to detect user mismatch.
    /// - Returns: Result<UserCreationResponse, BluetoothServiceError>
    func connectBpm(broadcastId: String, userNumber: Int, replaceUser: Bool, pairedSKUMonitors: [DeviceSnapshot]) async -> Result<UserCreationResponse, BluetoothServiceError>

    /// Requests the latest BPM reading from the connected device.
    /// The reading is delivered via `newBpmReadingReceivedPublisher`.
    /// - Parameter broadcastId: The broadcast ID of the BPM device.
    /// - Returns: Result<Void, BluetoothServiceError>
    func receiveBpmReading(broadcastId: String) async -> Result<Void, BluetoothServiceError>

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
    /// - Returns: Result<Void, BluetoothServiceError>
    func resyncAndScan() async -> Result<Void, BluetoothServiceError>
    /// Synchronises the provided device snapshot list with the Bluetooth plugin.
    /// - Parameter devices: The devices to sync. Passing an empty array clears the list.
    func syncDevices(_ devices: [DeviceSnapshot])

    // MARK: - Device CRUD
    /// Adds a newly discovered scale to persistent storage and returns the saved model.
    /// - Returns: Result<Device, BluetoothServiceError>
    func addNewDevice(_ device: Device, metaData: DeviceMetaData?, _ skipDuplicateCheck: Bool?) async -> Result<Device, BluetoothServiceError>
    /// Confirms a smart pairing operation with the specified device.
    /// - Returns: Result<UserCreationResponse, BluetoothServiceError>
    func confirmSmartPair(device: Device, token: String, displayName: String, userNumber: Int?) async -> Result<UserCreationResponse, BluetoothServiceError>
    /// Deletes a scale's user slot from the physical device (and optionally disconnects).
    /// Looks up the persisted token from the in-memory bluetoothScales list.
    /// - Returns: Result<UserDeletionResponse, BluetoothServiceError>
    func deleteDevice(broadcastId: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError>
    /// Deletes a user slot on the scale by broadcastId and token, without mutating any @Model object.
    func deleteUserByToken(broadcastId: String, token: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError>
    /// Deletes the current app user's slot on the BT WiFi (R4) scale when possible.
    /// Attempts to use the persisted token; otherwise fetches users and matches by preference/display name.
    /// - Returns: Result<UserDeletionResponse, BluetoothServiceError>
    func deleteCurrentUserFromScaleIfPossible(broadcastId: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError>
    /// Disconnects the specified device without deleting it from storage.
    /// - Returns: Result<Void, BluetoothServiceError>
    func disconnectDevice(broadcastId: String, considerForSession: Bool) async -> Result<Void, BluetoothServiceError>

    // MARK: - WiFi Configuration
    /// Retrieves the available Wi-Fi networks from the given device.
    /// - Returns: Result<[WifiDetails], BluetoothServiceError>
    func getWifiList(broadcastId: String) async -> Result<[WifiDetails], BluetoothServiceError>
    /// Configures Wi-Fi on the given device.
    /// - Returns: Result<Void, BluetoothServiceError>
    func setupWifi(broadcastId: String, config: WifiConfig) async -> Result<WifiSetupResponse, BluetoothServiceError>
    /// Cancels a pending Wi-Fi configuration.
    /// - Returns: Result<Void, BluetoothServiceError>
    func cancelWifi(broadcastId: String) async -> Result<Void, BluetoothServiceError>
    /// Retrieves the currently connected Wi-Fi SSID for an R4 scale.
    /// - Returns: Result<String, BluetoothServiceError>
    func getConnectedWifiSSID(broadcastId: String) async -> Result<String, BluetoothServiceError>

    // MARK: - Settings & Firmware
    /// Updates a list of settings on the device.
    /// - Returns: Result<Void, BluetoothServiceError>
    func updateSetting(broadcastId: String, settings: [DeviceSetting]) async -> Result<Void, BluetoothServiceError>
    /// Initiates a firmware update on the device.
    /// - Returns: Result<Void, BluetoothServiceError>
    func updateFirmware(broadcastId: String, timestamp: UInt32) async -> Result<Void, BluetoothServiceError>
    /// Clears stored data on the device (e.g., history, user).
    /// - Returns: Result<Void, BluetoothServiceError>
    func clearData(broadcastId: String, dataType: DeviceClearType) async -> Result<Void, BluetoothServiceError>

    // MARK: - Profile / Account Operations
    /// Updates the user profile (height, weight, age, etc.) on all connected R4 scales.
    /// - Returns: Result<[String], BluetoothServiceError>
    func updateUserProfileForR4Scales() async -> Result<[String], BluetoothServiceError>
    /// Updates account-specific preferences (display name, metrics, etc.) on the device.
    /// Fetches the preference from the local store; the call only requires the broadcast ID.
    /// - Returns: Result<UserCreationResponse, BluetoothServiceError>
    func updateAccount(broadcastId: String) async -> Result<UserCreationResponse, BluetoothServiceError>

    // MARK: - Device Information
    /// Retrieves generic device information (model, serial, firmware, …).
    /// - Returns: Result<DeviceInfo, BluetoothServiceError>
    func getDeviceInfo(broadcastId: String, skipConnectionCheck: Bool) async -> Result<DeviceInfo, BluetoothServiceError>
    /// Retrieves the Wi-Fi MAC address for an R4 scale.
    /// - Returns: Result<String, BluetoothServiceError>
    func getWifiMacAddress(broadcastId: String) async -> Result<String, BluetoothServiceError>

    /// Starts live measurement for the given device.
    /// - Returns: Result<Void, BluetoothServiceError>
    func startLiveMeasurement(broadcastId: String) async -> Result<Void, BluetoothServiceError>

    /// Stops live measurement for the given device.
    /// - Returns: Result<Void, BluetoothServiceError>
    func stopLiveMeasurement(broadcastId: String) async -> Result<Void, BluetoothServiceError>

    /// Retrieves live measurement data while a user is on the scale.
    /// - Returns: Result<MeasurementLiveData, BluetoothServiceError>
    func getMeasurementLiveData(broadcastId: String) async -> Result<MeasurementLiveData, BluetoothServiceError>
    /// Retrieves the list of users stored on the scale (R4 only).
    /// - Returns: Result<[DeviceUser], BluetoothServiceError>
    func getScaleUserList(broadcastId: String, skipConnectionCheck: Bool) async -> Result<[DeviceUser], BluetoothServiceError>
    /// Retrieves device logs from the scale.
    /// - Returns: Result<DeviceLogs, BluetoothServiceError>
    func getDeviceLogs(broadcastId: String) async -> Result<DeviceLogs, BluetoothServiceError>

    // MARK: - Alerts & Utility
    /// Triggers the in-app alert required when weight-only mode is enabled by another user.
    /// Pass nil to apply to all currently connected scales.
    /// - Returns: Result<Void, BluetoothServiceError>
    func updateWeightOnlyMode(broadcastId: String?) async -> Result<Void, BluetoothServiceError>

    /// Deletes all connected R4 scales from the device and disconnects them.
    /// This method is typically called during account deletion to clean up scale connections.
    /// - Returns: Result<Void, BluetoothServiceError>
    func deleteR4Scales() async -> Result<Void, BluetoothServiceError>

    func convertHexToInt(_ hex: String) -> Int64
}

extension BluetoothServiceProtocol {
    var isSetupInProgressPublisher: AnyPublisher<Bool, Never> {
        Just(isSetupInProgress).eraseToAnyPublisher()
    }

    func getDeviceInfo(broadcastId: String) async -> Result<DeviceInfo, BluetoothServiceError> {
        await getDeviceInfo(broadcastId: broadcastId, skipConnectionCheck: false)
    }

    func getScaleUserList(broadcastId: String) async -> Result<[DeviceUser], BluetoothServiceError> {
        await getScaleUserList(broadcastId: broadcastId, skipConnectionCheck: false)
    }

    func disconnectDevice(broadcastId: String) async -> Result<Void, BluetoothServiceError> {
        await disconnectDevice(broadcastId: broadcastId, considerForSession: true)
    }
}

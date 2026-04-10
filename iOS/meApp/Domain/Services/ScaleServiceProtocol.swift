import Combine
import Foundation

/// Protocol for business logic and orchestration related to paired-scale management.
///
/// This protocol defines high-level operations for paired scales, including listing, creating,
/// editing, deleting, updating scale meta and preferences, connection management, and pairing logic.
protocol ScaleServiceProtocol: DeviceServiceProtocol {
    func clearAllData() async

    /// The scales managed by the service.
    ///
    /// This is a BehaviorSubject that emits the current list of scales.
    /// The service will automatically update this subject when scales are added,
    /// edited, or deleted.
    ///
    /// The subject is updated on the main thread.
    var scalesPublisher: AnyPublisher<[Device], Never> { get }
    var scales: [Device] { get }

    /// Updates scale meta data.
    /// - Parameters:
    ///   - scaleId: The ID of the scale.
    ///   - metaData: The meta data to update.
    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws

    /// Updates scale preference.
    /// - Parameter preference: The R4ScalePreference to update.
    func updateScalePreference(_ deviceId: String, _ preference: R4ScalePreference) async throws

    /// Updates scale preference from a DTO (safe for async boundaries — no @Model crossing required).
    func updateScalePreference(_ deviceId: String, fromDTO dto: R4ScalePreferenceDTO) async throws

    // swiftlint:disable:next function_parameter_count
    func createR4Scale(
        scaleId: String,
        accountId: String,
        displayName: String,
        token: String,
        mac: String?,
        broadcastIdString: String?,
        broadcastId: Int64?,
        sku: String?,
        deviceName: String?,
        wifiMac: String?,
        deviceMetadata: DeviceMetaData?,
        isWifiConfigured: Bool,
        isConnected: Bool,
        skipDuplicateCheck: Bool
    ) async throws -> Device

    // swiftlint:disable:next function_parameter_count
    func createBluetoothScale(
        device: Device,
        sku: String?,
        userNumber: String,
        accountId: String,
        deviceMetadata: DeviceMetaData?,
        skipDuplicateCheck: Bool,
        deviceType: DeviceType
    ) async throws -> Device

    func createA6Scale(
        device: Device,
        sku: String?,
        accountId: String,
        deviceMetadata: DeviceMetaData?,
        skipDuplicateCheck: Bool
    ) async throws -> Device

    /// Updates the status of a scale.
    /// - Parameters:
    ///   - scales: The scales to update.
    func updateAllScalesStatus(_ scales: [Device]?) async throws
    func createScaleInLocal(_ device: Device) async throws -> Device
    /// Deletes a single device entry by its ID without removing other entries for the same
    /// physical device (e.g. a different user slot on the same BPM monitor).
    func deleteSingleDeviceEntry(_ deviceId: String) async throws

    func syncAllScalesWithRemote() async
    func pushLocalChangesToServer() async
    func getDevice(by deviceId: String) async throws -> Device?

    /// Updates connected device information including connection status and WiFi configuration.
    /// - Parameters:
    ///   - device: The device data (can be dictionary or GGDeviceDetails).
    ///   - isConnected: Whether the device is connected.
    func updateConnectedDevices(device: Any, isConnected: Bool) async

    /// Updates WiFi configuration status for a connected device.
    /// - Parameters:
    ///   - broadcastId: The broadcast ID of the device.
    ///   - isConfigured: Whether WiFi is configured.
    func updateConnectedDeviceWifiStatus(broadcastId: String, isConfigured: Bool) async

    /// Updates the weight-only mode status for a connected device.
    /// - Parameters:
    ///   - broadcastId: The broadcast ID of the device.
    ///   - isWeightOnlyModeEnabledByOthers: Whether weight-only mode is enabled by other users.
    func updateConnectedDeviceWeightOnlyMode(broadcastId: String, isWeightOnlyModeEnabledByOthers: Bool) async

    /// Fetches an attached R4 scale preference by its scale ID from the repository.
    /// - Parameter id: The scale/preference ID.
    /// - Returns: The attached `R4ScalePreference` if found, otherwise nil.
    func fetchAttachedPreference(by id: String) async -> R4ScalePreference?

    /// Synchronous variant to fetch an attached R4 scale preference by its scale ID.
    /// Must be called on main actor. Intended for synchronous call sites.
    func fetchAttachedPreferenceSync(by id: String) -> R4ScalePreference?
}

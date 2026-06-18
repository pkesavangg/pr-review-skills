import Foundation

/// Protocol for business logic and orchestration related to paired-scale management.
///
/// This protocol defines high-level operations for paired scales, including listing, creating,
/// editing, deleting, updating scale meta and preferences, connection management, and pairing logic.
protocol DeviceServiceProtocol {
    /// Fetches the latest paired scales for the current user.
    /// - Returns: An array of DeviceSnapshot (value-type, safe across async boundaries).
    func getDevices() async throws -> [DeviceSnapshot]

    /// Fetches the currently connected scales (by broadcastId).
    /// - Returns: A dictionary of broadcastId to device details.
    func getConnectedDevices() async -> [String: Any]

    /// Updates the connection status of a scale device.
    /// - Parameters:
    ///   - device: The device details (type erased for protocol).
    ///   - isConnected: Whether the device is connected.
    func updateConnectedDevices(device: Any, isConnected: Bool) async

    /// Updates the WiFi configuration status of a connected scale.
    /// - Parameters:
    ///   - broadcastId: The broadcast ID of the scale.
    ///   - isConfigured: Whether WiFi is configured.
    func updateConnectedDeviceWifiStatus(broadcastId: String, isConfigured: Bool) async

    /// Synchronizes all scales (local and remote) and updates state.
    /// - Parameter tempDevice: Optionally, a temporary device to sync.
    func syncDevices(tempDevice: Device?) async throws

    /// Creates a new paired scale.
    /// - Parameter scale: The scale to create.
    /// - Parameter skipDuplicateCheck: Whether to skip checking for duplicates (default false).
    /// - Returns: The created Scale.
    func createDevice(_ device: Device, _ skipDuplicateCheck: Bool) async throws -> Device

    /// Edits a paired scale's properties (e.g., nickname).
    /// - Parameters:
    ///   - scaleId: The ID of the scale to edit.
    ///   - properties: The properties to update (nickname, etc.).
    /// - Returns: The updated Scale.
    func editDevice(_ deviceId: String, properties: [String: Any]) async throws -> Device

    /// Deletes a paired scale.
    /// - Parameters:
    ///   - scaleId: The ID of the scale to delete.
    ///   - showToast: Whether to show a toast/notification (optional, default true).
    func deleteDevice(_ deviceId: String, showToast: Bool) async throws

    // MARK: - Unified Device API (Me App 2.0 — /v3/paired-device/)

    /// Lists paired devices of any product type from the unified `/paired-device/` endpoint.
    /// - Parameter deviceType: Optional filter; pass `nil` to fetch every device type.
    /// - Returns: The remote `PairedDeviceResponse` objects.
    func listPairedDevices(deviceType: DeviceType?) async throws -> [PairedDeviceResponse]

    /// Pairs a new device of any product type via the unified `/paired-device/` endpoint.
    /// The server auto-adds the corresponding product to the account's `productTypes`.
    /// - Parameter request: The unified device-pairing request.
    /// - Returns: The created `PairedDeviceResponse`.
    func createPairedDevice(_ request: PairedDeviceRequest) async throws -> PairedDeviceResponse

    /// Updates a paired device's nickname via the unified `/paired-device/:deviceId` endpoint.
    /// - Parameters:
    ///   - deviceId: The ID of the device to update.
    ///   - nickname: The new nickname.
    /// - Returns: The updated `PairedDeviceResponse`.
    func updatePairedDevice(_ deviceId: String, nickname: String) async throws -> PairedDeviceResponse

    /// Unpairs a device via the unified `/paired-device/:deviceId` endpoint (204 No Content).
    /// - Parameter deviceId: The ID of the device to unpair.
    func deletePairedDevice(_ deviceId: String) async throws
}

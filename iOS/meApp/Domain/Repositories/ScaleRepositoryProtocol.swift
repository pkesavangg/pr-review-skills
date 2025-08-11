import Foundation

/// Protocol for abstracting all paired-scale data access and operations (local or remote).
///
/// This protocol defines the contract for interacting with paired-scale data, including listing, creating,
/// editing, deleting, and updating scale meta and preferences. Implementations may use local storage or remote API.
protocol ScaleRepositoryProtocol {
    /// Deletes all scales from local storage.
    func clearAllData() async throws

    /// Fetches the list of paired scales for a specific user.
    /// - Parameter accountId: The account ID to filter scales by.
    /// - Returns: An array of Device objects for the user.
    func listScales(forAccountId accountId: String) async throws -> [Device]
    
    /// Fetches all scales (legacy method).
    /// - Returns: An array of all Device objects.
    func listScales() async throws -> [Device]

    /// Gets a device by its ID.
    /// - Parameter deviceId: The ID of the device to fetch.
    /// - Returns: The Device if found, nil otherwise.
    func getDevice(_ deviceId: String) async throws -> Device?

    /// Updates a device in the local storage.
    /// - Parameter device: The device to update.
    func updateDevice(_ device: Device) async throws

    /// Gets all devices that haven't been synced with the API.
    /// - Returns: An array of unsynced devices.
    func getUnsyncedDevices() async throws -> [Device]

    /// Creates a new paired scale. (POST /paired-scale)
    /// - Parameter scale: The scale to create.
    /// - Returns: The created Scale.
    func createScale(_ scale: Device) async throws -> Device

    /// Edits a paired scale's properties (e.g., nickname). (PATCH /paired-scale/:scaleId)
    /// - Parameters:
    ///   - scaleId: The ID of the scale to edit.
    ///   - properties: The properties to update (nickname, etc.).
    /// - Returns: The updated Scale.
    func editScale(_ scaleId: String, properties: [String: Any]) async throws -> Device

    /// Deletes a paired scale. (DELETE /paired-scale/:scaleId)
    /// - Parameter scaleId: The ID of the scale to delete.
    func deleteScale(_ scaleId: String) async throws

    /// Updates scale meta data. (PATCH /paired-scale/:scaleId/info)
    /// - Parameters:
    ///   - scaleId: The ID of the scale.
    ///   - metaData: The meta data to update.
    func patchScaleMeta(_ scaleId: String, metaData: DeviceMetaData) async throws

    /// Updates scale preference. (PATCH /scale-r4/preference)
    /// - Parameter preference: The R4ScalePreference to update.
    func patchScalePreference(_ scaleId: String,_ preference: R4ScalePreference) async throws

    // MARK: - Replace-All Sync Methods

    /// Replaces all local devices for the given account with fresh devices from server.
    /// This implements the "replace-all" sync policy for clean, predictable state management.
    /// Preserves unsynced local devices to avoid losing local changes.
    /// - Parameters:
    ///   - accountId: The account ID to filter devices by.
    ///   - serverDevices: Array of fresh Device objects from the server.
    ///   - preserveUnsynced: Array of unsynced local devices to preserve.
    func replaceAllDevicesForAccount(_ accountId: String, with serverDevices: [Device], preserveUnsynced unsyncedDevices: [Device]) async throws

    /// Legacy method for backward compatibility - replaces all devices without preserving unsynced.
    func replaceAllDevicesForAccount(_ accountId: String, with serverDevices: [Device]) async throws

    /// Marks a device as deleted locally (for server sync).
    /// - Parameter deviceId: The ID of the device to mark as deleted.
    func markDeviceAsDeleted(_ deviceId: String) async throws

    /// Gets all devices marked for deletion that need to be synced.
    /// - Returns: An array of devices marked as deleted and unsynced.
    func getDevicesMarkedForDeletion() async throws -> [Device]

    /// Permanently removes a device from local storage (after successful server deletion).
    /// - Parameter deviceId: The ID of the device to permanently remove.
    func permanentlyRemoveDevice(_ deviceId: String) async throws

    /// Checks if a device is purely local (never synced to server).
    /// - Parameter deviceId: The ID of the device to check.
    /// - Returns: True if the device is purely local, false otherwise.
    func isDevicePurelyLocal(_ deviceId: String) async throws -> Bool
}

import Foundation

/// Protocol for business logic and orchestration related to paired-scale management.
///
/// This protocol defines high-level operations for paired scales, including listing, creating,
/// editing, deleting, updating scale meta and preferences, connection management, and pairing logic.
protocol ScaleServiceProtocol: DeviceServiceProtocol {
    /// Updates scale meta data.
    /// - Parameters:
    ///   - scaleId: The ID of the scale.
    ///   - metaData: The meta data to update.
    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws

    /// Updates scale preference.
    /// - Parameter preference: The R4ScalePreference to update.
    func updateScalePreference(_ preference: R4ScalePreference) async throws

    /// Gets a scale by peripheral identifier and user number.
    /// - Parameters:
    ///   - peripheralIdentifier: The peripheral identifier.
    ///   - userNumber: The user number.
    /// - Returns: The matching Scale, or nil.
    func getScale(peripheralIdentifier: String, userNumber: Int) -> Device?

    /// Gets a scale by SKU.
    /// - Parameter sku: The SKU string.
    /// - Returns: The matching Scale, or nil.
    func getScaleBySKU(sku: String) -> Device?

    /// Initiates pairing logic for a scale by SKU.
    /// - Parameter sku: The SKU string.
    func canPairScale(sku: String)


    /// Gets the scale marked for deletion (if any).
    /// - Returns: The scale to delete, or nil.
    func getScaleToDelete() -> Device?
}

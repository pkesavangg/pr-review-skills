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
}

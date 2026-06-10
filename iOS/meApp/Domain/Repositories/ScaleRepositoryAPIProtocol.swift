import Foundation

/// Protocol for abstracting all paired-scale data access and operations (local or remote).
///
/// This protocol defines the contract for interacting with paired-scale data, including listing, creating,
/// editing, deleting, and updating scale meta and preferences. Implementations may use local storage or remote API.
protocol ScaleRepositoryAPIProtocol {
    /// Fetches the list of paired scales for the current user. (GET /paired-scale)
    /// - Returns: An array of Scale.
    func listScales() async throws -> [ScaleDTO]

    /// Creates a new paired scale. (POST /paired-scale)
    /// - Parameter scale: The scale to create.
    /// - Returns: The created Scale.
    func createScale(_ scale: ScaleDTO) async throws -> ScaleDTO

    /// Edits a paired scale's properties (e.g., nickname). (PATCH /paired-scale/:scaleId)
    /// - Parameters:
    ///   - scaleId: The ID of the scale to edit.
    ///   - properties: The properties to update (nickname, etc.).
    /// - Returns: The updated Scale.
    func editScale(_ scaleId: String, properties: ScaleDTO) async throws -> ScaleDTO

    /// Deletes a paired scale. (DELETE /paired-scale/:scaleId)
    /// - Parameter scaleId: The ID of the scale to delete.
    func deleteScale(_ scaleId: String) async throws

    /// Updates scale meta data. (PATCH /paired-scale/:scaleId/info)
    /// - Parameters:
    ///   - scaleId: The ID of the scale.
    ///   - metaData: The meta data to update.
    func patchScaleMeta(_ scaleId: String, metaData: ScaleMetaDataDTO) async throws

    /// Updates scale preference. (PATCH /scale-r4/preference)
    /// - Parameter preference: The R4ScalePreference to update.
    func patchScalePreference(_ preference: R4ScalePreferenceDTO) async throws

    // MARK: - Unified Device API (Me App 2.0)

    /// Fetches all paired devices for the current account. (GET /paired-device/)
    /// - Parameter deviceType: Optional server `deviceType` filter (`weight_scale`/`baby_scale`/`bpm`).
    ///   Pass `nil` to return every device type.
    /// - Returns: An array of `PairedDeviceResponse`.
    func listPairedDevices(deviceType: String?) async throws -> [PairedDeviceResponse]

    /// Pairs a new device of any type. (POST /paired-device/)
    /// - Parameter request: The unified device-pairing request.
    /// - Returns: The created `PairedDeviceResponse`.
    func createPairedDevice(_ request: PairedDeviceRequest) async throws -> PairedDeviceResponse

    /// Updates a paired device (e.g. nickname). (PATCH /paired-device/:deviceId)
    /// - Parameters:
    ///   - deviceId: The ID of the device to update.
    ///   - request: The fields to update.
    /// - Returns: The updated `PairedDeviceResponse`.
    func updatePairedDevice(_ deviceId: String, _ request: PairedDeviceUpdateRequest) async throws -> PairedDeviceResponse

    /// Deletes a paired device. (DELETE /paired-device/:deviceId — 204 No Content)
    /// - Parameter deviceId: The ID of the device to delete.
    func deletePairedDevice(_ deviceId: String) async throws

    // MARK: - Unified Review API (Me App 2.0)

    /// Submits an app/scale/monitor review. (POST /review/ — 204 No Content)
    /// Replaces the legacy `POST /review/app` and `POST /review/scale` endpoints.
    /// - Parameter request: The unified review request.
    func submitReview(_ request: ReviewRequest) async throws
}

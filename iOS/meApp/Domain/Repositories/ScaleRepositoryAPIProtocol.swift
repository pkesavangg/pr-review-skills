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
    func editScale(_ scaleId: String, properties: [String: Any]) async throws -> ScaleDTO

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
}

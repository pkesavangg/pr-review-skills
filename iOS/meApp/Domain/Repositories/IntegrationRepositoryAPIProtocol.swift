// swiftlint:disable function_parameter_count
import Foundation

/*
 SwiftLint exception:
 The `logHealthIntegration` function intentionally has many parameters to match the backend API contract and provide clear, explicit parameter names for all health integration data fields. Refactoring to a struct would add indirection and reduce clarity. We therefore disable `function_parameter_count` for this file.
 */
/// Protocol for abstracting all integration data access and operations (local or remote).
///
/// This protocol defines the contract for interacting with integration data sources
/// (e.g., local database, cache, or remote API). It includes CRUD operations, sync,
/// and status management for integrations.
protocol IntegrationRepositoryAPIProtocol {
    // MARK: - CRUD

    /// Removes a specific integration for the given account.
    /// - Parameters:
    ///   - accountId: The account/user ID.
    ///   - provider: The integration provider to remove.
    func removeIntegration(accountId: String, provider: IntegrationType) async throws

    /// Adds or updates a Health Connect/HealthKit integration.
    /// - Parameters:
    ///   - deviceId: The unique device identifier.
    ///   - type: The integration type (healthconnect or healthkit).
    ///   - preferences: Integration preferences as dynamic JSON.
    /// - Returns: The integration response containing device info and timestamps.
    func createHealthIntegration(deviceId: String, type: IntegrationType, preferences: [String: AnyCodable]) async throws -> HealthIntegrationResponse

    /// Logs Health Connect/HealthKit measurement data.
    /// - Parameters:
    ///   - type: The integration type (healthconnect or healthkit).
    ///   - sentAt: The ISO-8601 timestamp indicating when the data was sent.
    ///   - timestamp: The ISO-8601 timestamp of the measurement.
    ///   - weight: Weight in smallest unit (e.g., lbs * 100) – optional.
    ///   - bodyFat: Body-fat percentage – optional.
    ///   - muscleMass: Muscle mass – optional.
    ///   - water: Water percentage – optional.
    ///   - bmi: BMI – optional.
    ///   - data: Raw dynamic payload forwarded to the backend.
    /// - Returns: The log response with all fields echoed back by the API.
    func logHealthIntegration(type: IntegrationType, sentAt: String, timestamp: String, weight: Int?, bodyFat: Int?, muscleMass: Int?, water: Int?, bmi: Int?, data: [String: AnyCodable]) async throws -> HealthIntegrationLogResponse

    /// Deletes the Health Connect/HealthKit integration associated with the given **device identifier**.
    /// - Parameter deviceId: The unique health-integration device identifier.
    func deleteHealthIntegration(deviceId: String) async throws

    /// Submits a user's request for a new integration.
    /// - Parameter suggestion: The free-text description of the requested integration.
    func requestNewIntegration(suggestion: String) async throws

}
// swiftlint:enable function_parameter_count

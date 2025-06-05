import Foundation

/// Protocol for abstracting all integration data access and operations (local or remote).
///
/// This protocol defines the contract for interacting with integration data sources (e.g., local database, cache, or remote API).
/// It includes CRUD operations, sync, and status management for integrations, matching the business flows in integration.service.ts and backend /integrations endpoints.
protocol IntegrationRepositoryAPIProtocol {
    // MARK: - CRUD


    /// Removes a specific integration for the given account.
    /// - Parameters:
    ///   - accountId: The account/user ID.
    ///   - provider: The integration provider to remove.
    func removeIntegration(accountId: String, provider: IntegrationType) async throws

}

import Foundation

/// Protocol defining the service interface for managing integrations, including CRUD, sync, device/entry operations, and state management.
///
/// This protocol matches the business logic and flows in integration.service.ts, orchestrating repository calls and business rules for integrations.
protocol IntegrationServiceProtocol {
    // MARK: - CRUD

    /// Gets the integration URL for the given provider.
    /// - Parameter provider: The integration provider.
    /// - Returns: The integration URL.
    func getIntegrationUrl(_ provider: IntegrationType) async throws -> String


    /// Removes a specific integration for the active account.
    /// - Parameter provider: The integration provider to remove.
    func removeIntegration(_ provider: IntegrationType) async throws


    // MARK: - Status/Utility

    /// Gets the stored integration data for the current device/account.
    /// - Returns: The stored IntegratedDeviceInfo, if any.
    func getStoredIntegrationData() async throws -> IntegrationInfo?

    /// Sets the stored integration data for the current device/account.
    /// - Parameter info: The device info to store.
    func setStoredIntegrationData(_ info: IntegrationInfo?) async throws

    /// Checks if the integration is already used by another device/account.
    /// - Parameter type: The integration type to check.
    /// - Returns: True if available, false if conflict.
    func checkIfIntegrationIsAlreadyUsed(type: IntegrationType) async throws -> Bool

    /// Clears the integration status for the active account (e.g., on account deletion).
    func clearIntegrationStatus() async throws
}

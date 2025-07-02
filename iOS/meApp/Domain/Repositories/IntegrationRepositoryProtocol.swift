import Foundation

protocol IntegrationRepositoryProtocol {
    /// Gets the stored integration data for the current device/account.
    /// - Parameter accountId: The account/user ID.
    /// - Returns: The stored IntegratedDeviceInfo, if any.
    func getIntegrationData(accountId: String) async throws -> IntegrationInfo?

    /// Sets the stored integration data for the current device/account.
    /// - Parameters:
    ///   - accountId: The account/user ID.
    ///   - info: The device info to store.
    func setIntegrationData(accountId: String, info: IntegrationInfo?) async throws

    /// Checks if the integration is already used by another device/account.
    /// - Parameters:
    ///   - accountId: The account/user ID.
    ///   - type: The integration type to check.
    /// - Returns: True if available, false if conflict.
    func isIntegrationAlreadyUsed(accountId: String, type: IntegrationType) async throws -> Bool

    /// Clears the integration status for the given account (e.g., on account deletion).
    /// - Parameter accountId: The account/user ID.
    func clearIntegrationStatus(accountId: String) async throws
}

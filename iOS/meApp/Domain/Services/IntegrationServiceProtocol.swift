import Foundation

/// Protocol defining the service interface for managing integrations, including CRUD, sync, device/entry operations, and state management.
///
/// This protocol matches the business logic and flows in integration.service.ts, orchestrating repository calls and business rules for integrations.
@MainActor
protocol IntegrationServiceProtocol {
    // MARK: - CRUD

    /// Gets the integration URL for the given provider.
    /// - Parameter provider: The integration provider.
    /// - Returns: The integration URL.
    func getIntegrationUrl(_ provider: IntegrationType) async throws -> String

    /// Removes a specific integration for the active account.
    /// - Parameter provider: The integration provider to remove.
    func removeIntegration(_ provider: IntegrationType) async throws

    /// Submits a request for a new integration.
    /// - Parameter text: The user-provided description of the requested integration.
    func requestNewIntegration(text: String) async throws

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
    func isIntegrationAlreadyUsed(type: IntegrationType) async throws -> Bool

    /// Clears the integration status for the active account (e.g., on account deletion).
    /// - Parameter integrationType: The integration type to clear.
    ///  - Throws: An error if the operation fails.
    func clearIntegrationStatus(integrationType: IntegrationType) async throws
    
    // MARK: - Entry Sync Operations
    
    /// Syncs a new entry to the integrated health service (e.g., HealthKit) if integration is active.
    /// - Parameter entry: The entry to sync to the health service.
    func syncNewEntry(_ entry: Entry) async throws

    /// Syncs a new entry to the integrated health service using an EntryNotification.
    /// Use this overload when the source data is not a MainActor-bound `Entry` — e.g.,
    /// the remote-sync merge path that creates entries off the main actor.
    /// - Parameter notification: The Sendable notification carrying the entry data.
    func syncNewEntry(notification: EntryNotification) async throws

    /// Deletes an entry from the integrated health service (e.g., HealthKit) if integration is active.
    /// - Parameter entry: The entry to delete from the health service.
    func deleteEntry(_ entry: Entry) async throws

    /// Deletes an entry from the integrated health service using an EntryNotification.
    /// Use this overload when the local row is already gone — e.g. the batched
    /// remote-sync merge deletes rows off the main actor and hands back
    /// notifications extracted before deletion (MOB-1433).
    /// - Parameter notification: The Sendable notification carrying the entry data.
    func deleteEntry(notification: EntryNotification) async throws
    
    /// Clears all integration data if integration is active (used during account deletion).
    func clearIntegration() async throws
    func logHealthEntry(notification: EntryNotification) async
}

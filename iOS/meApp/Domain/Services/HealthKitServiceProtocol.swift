import Foundation

/// Protocol defining the service interface for managing HealthKit integration and data sync on iOS.
@MainActor
protocol HealthKitServiceProtocol {
    /// Integrates or de-integrates HealthKit for the current user.
    /// - Parameter turnOn: If true, enables integration; if false, disables it.
    /// - Returns: True if integration was successful, false otherwise.
    func integrate(turnOn: Bool) async throws -> Bool

    /// Syncs all HealthKit data for the current user.
    /// - Returns: Void (or throws on error).
    func syncAllData() async throws

    /// Syncs new HealthKit data for a specific entry.
    /// - Parameter entry: The entry to sync.
    func syncNewData(entry: Entry) async throws

    /// Syncs new HealthKit data using a notification payload.
    /// - Parameter notification: The entry notification to sync.
    func syncNewData(notification: EntryNotification) async throws

    /// Opens the Apple Health application. Useful for directing the user to Health app settings.
    func openAppleHealth()

    /// Checks whether the user has granted the required HealthKit permissions.
    /// - Returns: `true` if authorization has been granted for at least one required permission, `false` otherwise.
    func checkAuthorizationStatus() -> Bool

    /// Retrieves the list of approved HealthKit permission identifiers.
    /// - Returns: An array of permission identifiers that have been granted.
    func getApprovedPermissionList() -> [String]

    /// Deletes a specific entry from HealthKit.
    /// - Parameter entry: The entry to delete.
    /// - Returns: True if deletion was successful, false otherwise.
    func deleteEntry(entry: Entry) async throws -> Bool

    /// Deletes a specific notification payload from HealthKit.
    /// - Parameter notification: The entry notification to delete.
    /// - Returns: True if deletion was successful, false otherwise.
    func deleteEntry(notification: EntryNotification) async throws -> Bool

    /// Clears all HealthKit data for the current user (if integrated).
    func clearHealthKit() async throws

    /// Determines whether the app should present any Apple Health integration modal on launch.
    /// - Returns: A `HKIntegrationModalState` value indicating which modal should be displayed,
    ///            or `nil` when no modal is necessary.
    func shouldShowHKIntegrationModal() async throws -> HKIntegrationModalState?
    
    /// Sets a flag indicating we're waiting for permissions to be restored after out-of-sync.
    /// Called when user taps "OPEN APPLE HEALTH" from the out-of-sync modal.
    func setWaitingForPermissionsRestored()
    
    /// Clears the flag indicating we're waiting for permissions to be restored.
    func clearWaitingForPermissionsRestored()
    
    /// Checks if permissions were restored after being out of sync.
    /// Returns `true` if we were waiting for permissions and they are now restored.
    /// This should be called on app launch to show the success toast.
    func checkIfPermissionsRestoredAfterOutOfSync() async -> Bool
}

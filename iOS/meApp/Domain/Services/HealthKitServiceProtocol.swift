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

    /// Clears all HealthKit data for the current user (if integrated).
    func clearHealthKit() async throws
}

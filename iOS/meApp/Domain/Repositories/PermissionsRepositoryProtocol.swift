import Foundation

/// Protocol for abstracting all local persistence of permission-related viewed statuses in user defaults.
protocol PermissionsRepositoryProtocol {
    /// Gets the viewed status for all permissions for the given account.
    /// - Parameter accountId: The account/user ID.
    /// - Returns: PermissionsViewedStatus object.
    func getPermissionsViewedStatus(accountId: String) async throws -> PermissionsViewedStatus

    /// Sets the viewed status for all permissions for the given account.
    /// - Parameters:
    ///   - accountId: The account/user ID.
    ///   - status: The PermissionsViewedStatus object to store.
    func setPermissionsViewedStatus(accountId: String, status: PermissionsViewedStatus) async throws

    /// Clears all permission viewed statuses for the given account.
    /// - Parameter accountId: The account/user ID.
    func clearPermissionsViewedStatus(accountId: String) async throws
}

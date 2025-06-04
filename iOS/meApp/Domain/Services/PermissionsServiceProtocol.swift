import Foundation

/// Protocol defining the service interface for managing app permissions (Bluetooth, Location, Notifications, Camera, etc.) for iOS.
///
/// This protocol matches the business logic and flows in permissions.service.ts, exposing all public methods for permission checks, alerts, and requests that are relevant to iOS.
protocol PermissionsServiceProtocol {

    /// Gets the current permission status.
    func getPermissionStatus() -> PermissionStatus?

    /// Sets the current permission status.
    func setPermissionStatus(_ permissionStatus: PermissionStatus)

    /// Checks and displays permission alerts as needed.
    /// - Parameter permissionSets: The required permission sets.
    func checkPermissions(permissionSets: DisplayPermissionSets?) async throws

    /// Gets the required permission sets for the given devices.
    /// - Parameter devices: The list of devices.
    /// - Returns: The required permission sets.
    func getRequiredPermissionSets(devices: [Device]) -> DisplayPermissionSets

    /// Requests push notification permission.
    func requestPushNotificationPermission() async throws

    /// Requests a specific permission.
    /// - Parameter permissionType: The type of permission to request.
    func requestPermission(_ permissionType: PermissionType) async throws
}

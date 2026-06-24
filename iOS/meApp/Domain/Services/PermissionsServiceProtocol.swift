import Combine
import Foundation
import GGBluetoothSwiftPackage

/// Protocol defining the service interface for managing and requesting various app permissions.
///
/// This mirrors the public surface area of `PermissionsService`, allowing the concrete
/// implementation to be injected and mocked during testing.
@MainActor
protocol PermissionsServiceProtocol: AnyObject {
    // MARK: - Published Properties
    /// Latest permission status keyed by permission type. `nil` until first update from SDK.
    var permissions: [GGPermissionType: GGPermissionState]? { get }
    var permissionsPublisher: AnyPublisher<[GGPermissionType: GGPermissionState]?, Never> { get }
    var requiredCategories: Set<PermissionCategory> { get }
    var requiredCategoriesPublisher: AnyPublisher<Set<PermissionCategory>, Never> { get }

    // MARK: - Mutation
    /// Updates the cached permission map with the latest values from the SDK.
    /// - Parameter permissions: A dictionary keyed by `GGPermissionType` containing the latest `GGPermissionState`.
    func setPermissions(_ permissions: [GGPermissionType: GGPermissionState])

    // MARK: - Permission Requests
    /// Requests or toggles the permission represented by `type` via the GG SDK and returns the resulting state.
    /// - Parameter type: The permission type to request or enable.
    /// - Returns: The resulting `GGPermissionState`.
    @discardableResult
    func permissionRequest(_ type: GGPermissionType) async -> GGPermissionState

    // MARK: - Permission Dispatcher
    /// Centralised permission handler that returns the resulting `GGPermissionState`.
    /// - Parameter type: The permission type that should be handled.
    /// - Returns: The latest `GGPermissionState` for the given permission.
    @discardableResult
    func handlePermission(_ type: PermissionType) async -> GGPermissionState

    // MARK: - Helpers
    /// Checks the current permission state for a given type.
    /// - Parameter type: The permission type to query.
    /// - Returns: The cached `GGPermissionState` if available.
    func getPermissionState(_ type: GGPermissionType) -> GGPermissionState?

    /// Opens iOS Wi-Fi settings.
    func navigateToWifiSettings()
}

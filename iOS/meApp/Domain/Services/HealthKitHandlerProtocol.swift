import Foundation
import ggHealthKitPackage

/// Protocol abstracting the HealthKit package for availability, authorization, and read/write operations.
/// Allows injecting a mock in tests without touching the real Apple Health stack.
@MainActor
protocol HealthKitHandlerProtocol {
    func available() -> Bool
    func requestAuthorization() async -> Bool
    func getApprovedPermissionList() -> [String]
    /// Updates the HealthKit permission set based on the paired device types.
    /// - Parameter deviceTypes: Set of `DeviceType.rawValue` strings (e.g. "scale", "bpm").
    func updateAppType(for deviceTypes: Set<String>)
    func saveData(_ data: [HealthKitData]) async throws
    func deleteEntry(_ data: [HealthKitData]) async throws
    func deleteAllData() async throws
    func openAppleHealth() async
}

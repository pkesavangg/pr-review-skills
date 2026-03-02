import Foundation
import ggHealthKitPackage

/// Production implementation of HealthKitHandlerProtocol that forwards to the shared package handler.
@MainActor
final class AppleHealthHandlerAdapter: HealthKitHandlerProtocol {
    private let handler = AppleHealthHandler.shared

    init() {
        handler.setAppType(appType: .WEIGHT_GURUS)
    }

    func available() -> Bool {
        handler.available()
    }

    func requestAuthorization() async -> Bool {
        await handler.requestAuthorization()
    }

    func getApprovedPermissionList() -> [String] {
        handler.getApprovedPermissionList()
    }

    func saveData(_ data: [HealthKitData]) async throws {
        try await handler.saveData(data)
    }

    func deleteEntry(_ data: [HealthKitData]) async throws {
        try await handler.deleteEntry(data)
    }

    func deleteAllData() async throws {
        try await handler.deleteAllData()
    }

    func openAppleHealth() async {
        _ = await handler.openAppleHealth()
    }
}

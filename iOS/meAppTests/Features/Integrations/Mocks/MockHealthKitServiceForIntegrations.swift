import Foundation
@testable import meApp

@MainActor
final class MockHealthKitServiceForIntegrations: HealthKitServiceProtocol {
    var integrateResult = false
    var syncAllDataError: Error?
    var syncNewDataEntryError: Error?
    var syncNewDataNotificationError: Error?
    var deleteEntryEntryError: Error?
    var deleteEntryNotificationError: Error?
    var clearHealthKitError: Error?
    var shouldShowModalResult: HKIntegrationModalState?
    var permissions: [String] = []
    var checkIfPermissionsRestoredResult = false

    private(set) var syncNewDataNotificationCalls = 0
    private(set) var deleteEntryNotificationCalls = 0
    private(set) var clearHealthKitCalls = 0
    private(set) var getApprovedPermissionListCalls = 0

    func integrate(turnOn: Bool) async throws -> Bool {
        integrateResult
    }

    func syncAllData() async throws {
        if let syncAllDataError { throw syncAllDataError }
    }

    func syncNewData(entry: Entry) async throws {
        if let syncNewDataEntryError { throw syncNewDataEntryError }
    }

    func syncNewData(notification: EntryNotification) async throws {
        syncNewDataNotificationCalls += 1
        if let syncNewDataNotificationError { throw syncNewDataNotificationError }
    }

    func openAppleHealth() {}

    func checkAuthorizationStatus() -> Bool {
        !permissions.isEmpty
    }

    func isHKOutOfSync() async -> Bool {
        false
    }

    func getApprovedPermissionList() -> [String] {
        getApprovedPermissionListCalls += 1
        return permissions
    }

    func deleteEntry(entry: Entry) async throws -> Bool {
        if let deleteEntryEntryError { throw deleteEntryEntryError }
        return true
    }

    func deleteEntry(notification: EntryNotification) async throws -> Bool {
        deleteEntryNotificationCalls += 1
        if let deleteEntryNotificationError { throw deleteEntryNotificationError }
        return true
    }

    func clearHealthKit() async throws {
        clearHealthKitCalls += 1
        if let clearHealthKitError { throw clearHealthKitError }
    }

    func shouldShowHKIntegrationModal() async throws -> HKIntegrationModalState? {
        shouldShowModalResult
    }

    func setWaitingForPermissionsRestored() {}

    func clearWaitingForPermissionsRestored() {}

    func checkIfPermissionsRestoredAfterOutOfSync() async -> Bool {
        checkIfPermissionsRestoredResult
    }

    var expectedPermissionCountResult = 5
    func requestAdditionalPermissionsIfNeeded() async {}
    func expectedPermissionCount() async -> Int { expectedPermissionCountResult }
    var brandNameResult = "Weight Gurus"
    func getBrandName() async -> String { brandNameResult }
}

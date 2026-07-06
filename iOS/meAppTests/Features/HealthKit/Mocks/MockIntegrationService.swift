import Foundation
@testable import meApp

@MainActor
final class MockIntegrationService: IntegrationServiceProtocol {
    var getStoredIntegrationDataResult: IntegrationInfo?
    var getStoredIntegrationDataError: Error?
    var setStoredIntegrationDataError: Error?
    var isIntegrationAlreadyUsedResult = false
    var isIntegrationAlreadyUsedError: Error?
    var clearIntegrationStatusError: Error?
    var syncNewEntryError: Error?
    var deleteEntryError: Error?
    var clearIntegrationError: Error?

    private(set) var getStoredIntegrationDataCalls = 0
    private(set) var setStoredIntegrationDataCalls = 0
    private(set) var isIntegrationAlreadyUsedCalls = 0
    private(set) var clearIntegrationStatusCalls = 0
    private(set) var syncNewEntryCalls = 0
    private(set) var deleteEntryCalls = 0
    private(set) var clearIntegrationCalls = 0
    private(set) var lastSetStoredIntegrationDataInfo: IntegrationInfo?
    private(set) var deletedNotifications: [EntryNotification] = []
    private(set) var syncedNotifications: [EntryNotification] = []

    func getIntegrationUrl(_ provider: IntegrationType) async throws -> String {
        ""
    }

    func removeIntegration(_ provider: IntegrationType) async throws {}

    func getStoredIntegrationData() async throws -> IntegrationInfo? {
        getStoredIntegrationDataCalls += 1
        if let error = getStoredIntegrationDataError { throw error }
        return getStoredIntegrationDataResult
    }

    func setStoredIntegrationData(_ info: IntegrationInfo?) async throws {
        setStoredIntegrationDataCalls += 1
        lastSetStoredIntegrationDataInfo = info
        if let error = setStoredIntegrationDataError { throw error }
    }

    func isIntegrationAlreadyUsed(type: IntegrationType) async throws -> Bool {
        isIntegrationAlreadyUsedCalls += 1
        if let error = isIntegrationAlreadyUsedError { throw error }
        return isIntegrationAlreadyUsedResult
    }

    func clearIntegrationStatus(integrationType: IntegrationType) async throws {
        clearIntegrationStatusCalls += 1
        if let error = clearIntegrationStatusError { throw error }
    }

    func syncNewEntry(_ entry: Entry) async throws {
        syncNewEntryCalls += 1
        if let syncNewEntryError { throw syncNewEntryError }
    }

    func deleteEntry(_ entry: Entry) async throws {
        deleteEntryCalls += 1
        if let deleteEntryError { throw deleteEntryError }
    }

    func deleteEntry(notification: EntryNotification) async throws {
        deleteEntryCalls += 1
        deletedNotifications.append(notification)
        if let deleteEntryError { throw deleteEntryError }
    }
    func clearIntegration() async throws {
        clearIntegrationCalls += 1
        if let clearIntegrationError { throw clearIntegrationError }
    }
    func syncNewEntry(notification: EntryNotification) async throws {
        syncNewEntryCalls += 1
        syncedNotifications.append(notification)
        if let syncNewEntryError { throw syncNewEntryError }
    }
    func logHealthEntry(notification: EntryNotification) async {}
    func requestNewIntegration(text: String) async throws {}
}

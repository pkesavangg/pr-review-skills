import Combine
import Foundation
@testable import meApp

enum HealthKitStoreTestError: Error, Equatable {
    case authorizationFailed
    case integrationConflict
    case loadFailed
    case syncFailed
}

@MainActor
final class MockHealthKitStoreIntegrationService: IntegrationServiceProtocol {
    var getStoredIntegrationDataResult: IntegrationInfo?
    var getStoredIntegrationDataError: Error?
    var setStoredIntegrationDataError: Error?
    var isIntegrationAlreadyUsedResult = false
    var isIntegrationAlreadyUsedError: Error?

    private(set) var getStoredIntegrationDataCalls = 0
    private(set) var setStoredIntegrationDataCalls = 0
    private(set) var isIntegrationAlreadyUsedCalls = 0
    private(set) var logHealthEntryCalls = 0
    private(set) var lastSetStoredInfo: IntegrationInfo?

    func getIntegrationUrl(_ provider: IntegrationType) async throws -> String { "" }
    func removeIntegration(_ provider: IntegrationType) async throws {}

    func getStoredIntegrationData() async throws -> IntegrationInfo? {
        getStoredIntegrationDataCalls += 1
        if let getStoredIntegrationDataError { throw getStoredIntegrationDataError }
        return getStoredIntegrationDataResult
    }

    func setStoredIntegrationData(_ info: IntegrationInfo?) async throws {
        setStoredIntegrationDataCalls += 1
        lastSetStoredInfo = info
        if let setStoredIntegrationDataError { throw setStoredIntegrationDataError }
    }

    func isIntegrationAlreadyUsed(type: IntegrationType) async throws -> Bool {
        isIntegrationAlreadyUsedCalls += 1
        if let isIntegrationAlreadyUsedError { throw isIntegrationAlreadyUsedError }
        return isIntegrationAlreadyUsedResult
    }

    func clearIntegrationStatus(integrationType: IntegrationType) async throws {}
    func syncNewEntry(_ entry: Entry) async throws {}
    func deleteEntry(_ entry: Entry) async throws {}
    func clearIntegration() async throws {}

    func logHealthEntry(notification: EntryNotification) async {
        logHealthEntryCalls += 1
    }
}

@MainActor
final class MockHealthKitStoreHealthKitService: HealthKitServiceProtocol {
    var integrateResult: Result<Bool, Error> = .success(true)
    var syncAllDataError: Error?
    var approvedPermissionList: [String] = []
    var isHKOutOfSyncResult = false
    var clearHealthKitError: Error?

    private(set) var integrateCalls: [Bool] = []
    private(set) var syncAllDataCalls = 0
    private(set) var openAppleHealthCalls = 0
    private(set) var clearHealthKitCalls = 0

    func integrate(turnOn: Bool) async throws -> Bool {
        integrateCalls.append(turnOn)
        return try integrateResult.get()
    }

    func syncAllData() async throws {
        syncAllDataCalls += 1
        if let syncAllDataError { throw syncAllDataError }
    }

    func syncNewData(entry: Entry) async throws {}
    func syncNewData(notification: EntryNotification) async throws {}

    func openAppleHealth() {
        openAppleHealthCalls += 1
    }

    func checkAuthorizationStatus() -> Bool {
        !approvedPermissionList.isEmpty
    }

    func isHKOutOfSync() async -> Bool {
        isHKOutOfSyncResult
    }

    func getApprovedPermissionList() -> [String] {
        approvedPermissionList
    }

    func deleteEntry(entry: Entry) async throws -> Bool { true }
    func deleteEntry(notification: EntryNotification) async throws -> Bool { true }

    func clearHealthKit() async throws {
        clearHealthKitCalls += 1
        if let clearHealthKitError { throw clearHealthKitError }
    }

    func shouldShowHKIntegrationModal() async throws -> HKIntegrationModalState? { nil }
    func setWaitingForPermissionsRestored() {}
    func clearWaitingForPermissionsRestored() {}
    func checkIfPermissionsRestoredAfterOutOfSync() async -> Bool { false }
}

@MainActor
final class MockHealthKitStoreEntryService: EntryServiceProtocol {
    let entrySaved = PassthroughSubject<EntryNotification, Never>()
    let entryDeleted = PassthroughSubject<EntryNotification, Never>()

    var entryCountResult: Result<Int, Error> = .success(0)
    var latestEntry: Entry?

    private(set) var getEntryCountCalls = 0
    private(set) var getLatestEntryCalls = 0

    func syncAllEntriesWithRemote() async {}
    func migrateFromSQLiteIfNeeded() async {}
    func loadDashboardData() async {}
    func clearAllData() async {}
    func clearLastSyncTimestamp() async throws {}
    func saveNewEntry(_ entry: Entry) async throws {}
    func saveNewEntries(_ entries: [Entry]) async throws {}
    func deleteEntry(_ entry: Entry) async throws {}
    func getAllEntries() async throws -> [Entry] { [] }
    func getAllEntriesAsDTO() async throws -> [BathScaleOperationDTO] { [] }
    func checkEntryTimestampExists(_ entryTimestamp: String) async throws -> Bool { false }

    func getEntryCount() async throws -> Int {
        getEntryCountCalls += 1
        return try entryCountResult.get()
    }

    func getOldestEntry() async throws -> Entry? { nil }

    func getLatestEntry() async throws -> Entry? {
        getLatestEntryCalls += 1
        return latestEntry
    }

    func getEntries(lastNDays: Int) async throws -> [Entry] { [] }
    func getEntries(forMonth month: String) async throws -> [Entry] { [] }
    func getMonthsAll() async throws -> [HistoryMonth] { [] }
    func getMonthDetail(month: String) async throws -> [Entry] { [] }
    func getMonthYear() async throws -> [HistoryMonth] { [] }

    func getProgress() async throws -> meApp.Progress {
        meApp.Progress(
            count: 0,
            currentStreak: 0,
            initYear: nil,
            initMonth: nil,
            initWeek: nil,
            initWt: 0,
            latest: nil,
            longestStreak: 0,
            month: 1,
            percent: nil,
            total: nil,
            week: 1,
            year: 2024
        )
    }

    func getStreak() async throws -> Streak { Streak(current: 0, max: 0) }
    func exportCSV() async throws {}
    func createBpmEntry(_ dto: BpmOperationDTO) async throws {}
    func fetchBpmEntries() async throws -> [BpmOperationDTO] { [] }
    func deleteBpmEntry(entryTimestamp: String) async throws {}
    func exportBpmCSV() async throws {}
}

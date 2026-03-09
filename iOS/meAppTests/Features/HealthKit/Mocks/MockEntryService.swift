import Combine
import Foundation
@testable import meApp

@MainActor
final class MockEntryService: EntryServiceProtocol {
    let entrySaved = PassthroughSubject<EntryNotification, Never>()
    let entryDeleted = PassthroughSubject<EntryNotification, Never>()
    var getMonthsAllResult: Result<[HistoryMonth], Error> = .success([])
    var exportCSVResult: Result<Void, Error> = .success(())
    var getLatestEntryResult: Result<Entry?, Error> = .success(nil)
    var getEntryCountResult: Result<Int, Error> = .success(0)

    private(set) var getMonthsAllCalls = 0
    private(set) var exportCSVCalls = 0
    private(set) var getLatestEntryCalls = 0
    private(set) var clearAllDataCalls = 0
    private(set) var saveNewEntryCalls = 0
    private(set) var saveNewEntriesCalls = 0
    private(set) var deleteEntryCalls = 0
    private(set) var getEntryCountCalls = 0

    func syncAllEntriesWithRemote() async {}
    func migrateFromSQLiteIfNeeded() async {}
    func loadDashboardData() async {}
    func clearAllData() async { clearAllDataCalls += 1 }
    func clearLastSyncTimestamp() async throws {}
    func saveNewEntry(_ entry: Entry) async throws { saveNewEntryCalls += 1 }
    func saveNewEntries(_ entries: [Entry]) async throws { saveNewEntriesCalls += 1 }
    func deleteEntry(_ entry: Entry) async throws { deleteEntryCalls += 1 }
    func getAllEntries() async throws -> [Entry] { [] }
    func getAllEntriesAsDTO() async throws -> [BathScaleOperationDTO] { [] }
    func checkEntryTimestampExists(_ entryTimestamp: String) async throws -> Bool { false }
    func getEntryCount() async throws -> Int {
        getEntryCountCalls += 1
        return try getEntryCountResult.get()
    }
    func getOldestEntry() async throws -> Entry? { nil }
    func getLatestEntry() async throws -> Entry? {
        getLatestEntryCalls += 1
        return try getLatestEntryResult.get()
    }
    func getEntries(lastNDays: Int) async throws -> [Entry] { [] }
    func getEntries(forMonth month: String) async throws -> [Entry] { [] }
    func getMonthsAll() async throws -> [HistoryMonth] {
        getMonthsAllCalls += 1
        return try getMonthsAllResult.get()
    }
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
    func exportCSV() async throws {
        exportCSVCalls += 1
        _ = try exportCSVResult.get()
    }
}

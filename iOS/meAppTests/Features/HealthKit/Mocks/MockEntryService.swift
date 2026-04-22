import Combine
import Foundation
@testable import meApp

@MainActor
final class MockEntryService: EntryServiceProtocol {
    let entrySaved = PassthroughSubject<EntryNotification, Never>()
    let entryDeleted = PassthroughSubject<EntryNotification, Never>()
    var latestEntry: Entry?
    private(set) var savedEntries: [Entry] = []
    private(set) var deletedEntries: [Entry] = []
    var getMonthsAllResult: Result<[HistoryMonth], Error> = .success([])
    var getMonthDetailResult: Result<[Entry], Error> = .success([])
    var getAllEntriesResult: Result<[Entry], Error> = .success([])
    var fetchAllEntrySnapshotsResult: Result<[EntrySnapshot], Error> = .success([])
    var fetchEntrySnapshotsForMonthResult: Result<[EntrySnapshot], Error> = .success([])
    var fetchEntrySnapshotByIdResult: Result<EntrySnapshot?, Error> = .success(nil)
    var exportCSVResult: Result<Void, Error> = .success(())
    var getLatestEntryResult: Result<Entry?, Error> = .success(nil)
    var getEntryCountResult: Result<Int, Error> = .success(0)

    private(set) var getMonthsAllCalls = 0
    private(set) var getMonthDetailCalls = 0
    private(set) var getMonthDetailLastMonth: String?
    private(set) var fetchAllEntrySnapshotsCalls = 0
    private(set) var fetchEntrySnapshotsForMonthCalls = 0
    private(set) var fetchEntrySnapshotsForMonthLast: String?
    private(set) var fetchEntrySnapshotByIdCalls = 0
    private(set) var deleteEntryByIdCalls = 0
    private(set) var deletedEntryIds: [UUID] = []
    private(set) var syncAllEntriesWithRemoteCalls = 0
    private(set) var loadDashboardDataCalls = 0
    private(set) var getAllEntriesCalls = 0
    private(set) var exportCSVCalls = 0
    private(set) var getLatestEntryCalls = 0
    private(set) var clearAllDataCalls = 0
    private(set) var saveNewEntryCalls = 0
    private(set) var saveNewEntriesCalls = 0
    private(set) var deleteEntryCalls = 0
    private(set) var getEntryCountCalls = 0
    private(set) var loadBabyDashboardDataCalls = 0
    private(set) var lastLoadedBabyDashboardId: String?

    func syncAllEntriesWithRemote() async { syncAllEntriesWithRemoteCalls += 1 }
    func migrateFromSQLiteIfNeeded() async {}
    func loadDashboardData(entryType: EntryType) async {
        loadDashboardDataCalls += 1
    }
    func loadBabyDashboardData(babyId: String) async {
        loadBabyDashboardDataCalls += 1
        lastLoadedBabyDashboardId = babyId
    }
    func clearAllData() async { clearAllDataCalls += 1 }
    func clearLastSyncTimestamp() async throws {}
    func saveNewEntry(_ entry: Entry) async throws {
        savedEntries.append(entry)
        latestEntry = entry
        entrySaved.send(EntryNotification(from: entry))
    }
    func saveNewEntries(_ entries: [Entry]) async throws {
        savedEntries.append(contentsOf: entries)
        latestEntry = entries.last ?? latestEntry
        for entry in entries {
            entrySaved.send(EntryNotification(from: entry))
        }
    }
    func deleteEntry(_ entry: Entry) async throws {
        deleteEntryCalls += 1
        deletedEntries.append(entry)
        entryDeleted.send(EntryNotification(from: entry))
    }
    func deleteEntry(entryId: UUID) async throws {
        deleteEntryByIdCalls += 1
        deletedEntryIds.append(entryId)
    }
    func assignBabyEntry(entryId: UUID, babyId: String) async throws {}
    func fetchEntrySnapshot(byId id: UUID) async throws -> EntrySnapshot? {
        fetchEntrySnapshotByIdCalls += 1
        return try fetchEntrySnapshotByIdResult.get()
    }
    func fetchAllEntrySnapshots() async throws -> [EntrySnapshot] {
        fetchAllEntrySnapshotsCalls += 1
        return try fetchAllEntrySnapshotsResult.get()
    }
    func fetchEntrySnapshots(forMonth month: String, entryType: EntryType) async throws -> [EntrySnapshot] {
        fetchEntrySnapshotsForMonthCalls += 1
        fetchEntrySnapshotsForMonthLast = month
        return try fetchEntrySnapshotsForMonthResult.get()
    }
    func getAllEntries() async throws -> [Entry] {
        getAllEntriesCalls += 1
        return try getAllEntriesResult.get()
    }
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
    func getEntries(lastNDays: Int, entryType: EntryType) async throws -> [Entry] { [] }
    func getEntries(forMonth month: String, entryType: EntryType) async throws -> [Entry] { [] }
    func getMonthsAll(entryType: EntryType) async throws -> [HistoryMonth] {
        getMonthsAllCalls += 1
        return try getMonthsAllResult.get()
    }
    func getMonthDetail(month: String, entryType: EntryType) async throws -> [Entry] {
        getMonthDetailCalls += 1
        getMonthDetailLastMonth = month
        return try getMonthDetailResult.get()
    }
    func getMonthYear() async throws -> [HistoryMonth] { [] }
    func getProgress(entryType: EntryType) async throws -> meApp.Progress {
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
    func getStreak(entryType: EntryType) async throws -> Streak { Streak(current: 0, max: 0) }
    func exportCSV() async throws {
        exportCSVCalls += 1
        _ = try exportCSVResult.get()
    }

    func createBabyEntry(babyId: String, weight: Int, length: Int, note: String, entryTimestamp: String) async throws {}
    private(set) var createBpmEntryCalls = 0
    private(set) var fetchBpmEntriesCalls = 0
    private(set) var deleteBpmEntryCalls = 0
    private(set) var exportBpmCSVCalls = 0
    var fetchBpmEntriesResult: Result<[BpmOperationDTO], Error> = .success([])

    func createBpmEntry(_ dto: BpmOperationDTO) async throws { createBpmEntryCalls += 1 }
    func fetchBpmEntries() async throws -> [BpmOperationDTO] {
        fetchBpmEntriesCalls += 1
        return try fetchBpmEntriesResult.get()
    }
    func deleteBpmEntry(entryTimestamp: String) async throws { deleteBpmEntryCalls += 1 }
    func exportBpmCSV() async throws { exportBpmCSVCalls += 1 }
    func migrateBabyEntriesToDecigrams() async {}
    func getEntry(byId id: UUID) async throws -> Entry? { nil }
    func createBabyEntry(babyId: String, weight: Int, length: Int, note: String, entryTimestamp: String, source: String?) async throws {}
}

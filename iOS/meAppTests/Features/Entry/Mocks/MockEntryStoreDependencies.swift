import Combine
import Foundation
@testable import meApp

@MainActor
final class MockEntryStoreEntryService: EntryServiceProtocol {
    let entrySaved = PassthroughSubject<EntryNotification, Never>()
    let entryDeleted = PassthroughSubject<EntryNotification, Never>()

    var saveResult: Result<Void, Error> = .success(())
    var shouldSuspendSave = false
    var saveDelayNanoseconds: UInt64 = 0
    private var saveContinuation: CheckedContinuation<Void, Never>?

    private(set) var saveNewEntryCalls = 0
    private(set) var lastSavedEntry: Entry?

    func saveNewEntry(_ entry: Entry) async throws {
        saveNewEntryCalls += 1
        lastSavedEntry = entry

        if saveDelayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: saveDelayNanoseconds)
        }

        if shouldSuspendSave {
            await withCheckedContinuation { continuation in
                saveContinuation = continuation
            }
        }
        try saveResult.get()
    }

    func releaseSave() {
        saveContinuation?.resume(returning: ())
        saveContinuation = nil
    }

    func syncAllEntriesWithRemote() async {}
    func migrateFromSQLiteIfNeeded() async {}
    func loadDashboardData(entryType: EntryType) async {}
    func loadBabyDashboardData(babyId: String) async {}
    func clearAllData() async {}
    func clearLastSyncTimestamp() async throws {}
    func saveNewEntries(_ entries: [Entry]) async throws {}
    func deleteEntry(_ entry: Entry) async throws {}
    func deleteEntry(entryId: UUID) async throws {}
    func assignBabyEntry(entryId: UUID, babyId: String) async throws {}
    func remapBabyId(from oldId: String, to newId: String) async {}
    func fetchEntrySnapshot(byId id: UUID) async throws -> EntrySnapshot? { nil }
    func fetchAllEntrySnapshots() async throws -> [EntrySnapshot] { [] }
    func fetchEntrySnapshots(forMonth month: String, entryType: EntryType) async throws -> [EntrySnapshot] { [] }
    private(set) var createBabyEntryCalls = 0
    // Captured call args; labeled tuple is clearer than a one-off struct.
    // swiftlint:disable:next large_tuple
    private(set) var lastBabyEntry: (babyId: String, weight: Int, length: Int, note: String)?
    var createBabyEntryError: Error?
    private(set) var createBpmEntryCalls = 0
    private(set) var lastBpmEntry: BpmOperationDTO?
    var createBpmEntryError: Error?

    func getAllEntries() async throws -> [Entry] { [] }
    func getAllEntriesAsDTO() async throws -> [BathScaleOperationDTO] { [] }
    func checkEntryTimestampExists(_ entryTimestamp: String) async throws -> Bool { false }
    func getEntryCount() async throws -> Int { 0 }
    func getOldestEntry() async throws -> Entry? { nil }
    func getLatestEntry() async throws -> Entry? { nil }
    func getEntries(lastNDays: Int, entryType: EntryType) async throws -> [Entry] { [] }
    func getEntries(forMonth month: String, entryType: EntryType) async throws -> [Entry] { [] }
    func getMonthsAll(entryType: EntryType) async throws -> [HistoryMonth] { [] }
    func getMonthDetail(month: String, entryType: EntryType) async throws -> [Entry] { [] }
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
    func exportCSV(category: String?, babyId: String?) async throws {}
    func fetchEntriesPage(cursor: String?, limit: Int, category: String?, babyId: String?) async throws -> EntriesPage { .empty }
    func createBpmEntry(_ dto: BpmOperationDTO) async throws {
        createBpmEntryCalls += 1
        lastBpmEntry = dto
        if let createBpmEntryError { throw createBpmEntryError }
    }
    func fetchBpmEntries() async throws -> [BpmOperationDTO] { [] }
    func deleteBpmEntry(entryTimestamp: String) async throws {}
    func exportBpmCSV() async throws {}
    func migrateBabyEntriesToDecigrams() async {}
    func getEntry(byId id: UUID) async throws -> Entry? { nil }
    // swiftlint:disable:next function_parameter_count
    func createBabyEntry(babyId: String, weight: Int, length: Int, note: String, entryTimestamp: String, source: String?) async throws {
        createBabyEntryCalls += 1
        lastBabyEntry = (babyId, weight, length, note)
        if let createBabyEntryError { throw createBabyEntryError }
    }
}

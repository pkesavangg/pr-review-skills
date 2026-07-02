//
//  MockHelpStoreEntryService.swift
//  meAppTests
//

import Combine
import Foundation
@testable import meApp

@MainActor
final class MockHelpStoreEntryService: EntryServiceProtocol {
    let entrySaved = PassthroughSubject<EntryNotification, Never>()
    let entryDeleted = PassthroughSubject<EntryNotification, Never>()

    private(set) var clearAllDataCalls = 0
    private(set) var clearLastSyncTimestampCalls = 0
    private(set) var syncAllEntriesWithRemoteCalls = 0
    var clearLastSyncTimestampError: Error?

    func clearAllData() async { clearAllDataCalls += 1 }
    func clearLastSyncTimestamp() async throws {
        clearLastSyncTimestampCalls += 1
        if let error = clearLastSyncTimestampError { throw error }
    }
    func removeAccountFromDevice(accountId: String) async throws {}
    func syncAllEntriesWithRemote() async { syncAllEntriesWithRemoteCalls += 1 }
    func migrateFromSQLiteIfNeeded() async {}
    func loadDashboardData(entryType: EntryType) async {}
    func loadBabyDashboardData(babyId: String) async {}
    func saveNewEntry(_ entry: Entry) async throws {}
    func saveNewEntries(_ entries: [Entry]) async throws {}
    func deleteEntry(_ entry: Entry) async throws {}
    func deleteEntry(entryId: UUID) async throws {}
    func assignBabyEntry(entryId: UUID, babyId: String) async throws {}
    func fetchEntrySnapshot(byId id: UUID) async throws -> EntrySnapshot? { nil }
    func fetchAllEntrySnapshots() async throws -> [EntrySnapshot] { [] }
    func fetchEntrySnapshots(forMonth month: String, entryType: EntryType) async throws -> [EntrySnapshot] { [] }
    func getAllEntries() async throws -> [Entry] { [] }
    func getAllEntriesAsDTO() async throws -> [BathScaleOperationDTO] { [] }
    func getAllEntriesAsSnapshots() async throws -> [EntrySnapshot] { [] }
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
    func createBpmEntry(_ dto: BpmOperationDTO) async throws {}
    func createBabyEntry(babyId: String, weight: Int, length: Int, note: String, entryTimestamp: String) async throws {}
    func fetchBpmEntries() async throws -> [BpmOperationDTO] { [] }
    func deleteBpmEntry(entryTimestamp: String) async throws {}
    func exportBpmCSV() async throws {}
    func migrateBabyEntriesToDecigrams() async {}
    func getEntry(byId id: UUID) async throws -> Entry? { nil }
    // swiftlint:disable:next function_parameter_count
    func createBabyEntry(babyId: String, weight: Int, length: Int, note: String, entryTimestamp: String, source: String?) async throws {}
}

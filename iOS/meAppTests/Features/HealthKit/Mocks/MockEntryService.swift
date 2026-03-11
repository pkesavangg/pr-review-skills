import Combine
import Foundation
@testable import meApp

@MainActor
final class MockEntryService: EntryServiceProtocol {
    let entrySaved = PassthroughSubject<EntryNotification, Never>()
    let entryDeleted = PassthroughSubject<EntryNotification, Never>()
    var latestEntry: Entry?

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
    func getEntryCount() async throws -> Int { 0 }
    func getOldestEntry() async throws -> Entry? { nil }
    func getLatestEntry() async throws -> Entry? { latestEntry }
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
}

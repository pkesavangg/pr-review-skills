import Foundation
@testable import meApp

@MainActor
final class MockEntryService: EntryServiceProtocol {

    // MARK: - saveNewEntry
    var saveNewEntryCallCount = 0
    var saveNewEntryError: Error?
    var lastSavedEntry: Entry?

    func saveNewEntry(_ entry: Entry) async throws {
        saveNewEntryCallCount += 1
        lastSavedEntry = entry
        if let error = saveNewEntryError { throw error }
    }

    // MARK: - saveNewEntries
    func saveNewEntries(_ entries: [Entry]) async throws {}

    // MARK: - deleteEntry
    var deleteEntryCallCount = 0
    func deleteEntry(_ entry: Entry) async throws {
        deleteEntryCallCount += 1
    }

    // MARK: - sync / clear
    func syncAllEntriesWithRemote() async {}
    func clearAllData() async {}
    func clearLastSyncTimestamp() async throws {}

    // MARK: - query stubs
    func getAllEntries() async throws -> [Entry] { [] }
    func getAllEntriesAsSnapshots() async throws -> [EntrySnapshot] { [] }
    func checkEntryTimestampExists(_ entryTimestamp: String) async throws -> Bool { false }
    func getEntryCount() async throws -> Int { 0 }
    func getOldestEntry() async throws -> Entry? { nil }
    func getLatestEntry() async throws -> Entry? { nil }
    func getEntries(lastNDays: Int) async throws -> [Entry] { [] }
    func getEntries(forMonth month: String) async throws -> [Entry] { [] }
    func getMonthsAll() async throws -> [HistoryMonth] { [] }
    func getMonthDetail(month: String) async throws -> [Entry] { [] }
    func getMonthYear() async throws -> [HistoryMonth] { [] }

    // MARK: - progress/stats stubs
    func getProgress() async throws -> Progress {
        throw NSError(domain: "mock", code: 0)
    }
    func getStreak() async throws -> Streak {
        throw NSError(domain: "mock", code: 0)
    }
    func exportCSV() async throws {}

    // MARK: - helpers
    func reset() {
        saveNewEntryCallCount = 0
        saveNewEntryError = nil
        lastSavedEntry = nil
        deleteEntryCallCount = 0
    }
}

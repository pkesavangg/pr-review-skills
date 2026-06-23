//
//  MockEntryService.swift
//  meAppTests
//

import Foundation
@testable import meApp

@MainActor
final class MockEntryService: EntryServiceProtocol {

    // MARK: - saveNewEntry
    var saveNewEntryError: Error?
    var saveNewEntryCallCount = 0
    var lastSavedEntry: Entry?

    func saveNewEntry(_ entry: Entry) async throws {
        saveNewEntryCallCount += 1
        lastSavedEntry = entry
        if let error = saveNewEntryError { throw error }
    }

    // MARK: - saveNewEntries
    var saveNewEntriesError: Error?

    func saveNewEntries(_ entries: [Entry]) async throws {
        if let error = saveNewEntriesError { throw error }
    }

    // MARK: - deleteEntry
    var deleteEntryError: Error?

    func deleteEntry(_ entry: Entry) async throws {
        if let error = deleteEntryError { throw error }
    }

    // MARK: - getAllEntries
    var getAllEntriesResult: [Entry] = []
    var getAllEntriesError: Error?

    func getAllEntries() async throws -> [Entry] {
        if let error = getAllEntriesError { throw error }
        return getAllEntriesResult
    }

    // MARK: - getAllEntriesAsSnapshots
    var getAllEntriesAsSnapshotsResult: [EntrySnapshot] = []

    func getAllEntriesAsSnapshots() async throws -> [EntrySnapshot] {
        return getAllEntriesAsSnapshotsResult
    }

    // MARK: - checkEntryTimestampExists
    var checkEntryTimestampExistsResult = false

    func checkEntryTimestampExists(_ entryTimestamp: String) async throws -> Bool {
        return checkEntryTimestampExistsResult
    }

    // MARK: - getEntryCount
    var getEntryCountResult = 0

    func getEntryCount() async throws -> Int {
        return getEntryCountResult
    }

    // MARK: - getOldestEntry
    var getOldestEntryResult: Entry?

    func getOldestEntry() async throws -> Entry? {
        return getOldestEntryResult
    }

    // MARK: - getLatestEntry
    var getLatestEntryResult: Entry?

    func getLatestEntry() async throws -> Entry? {
        return getLatestEntryResult
    }

    // MARK: - getEntries(lastNDays:)
    var getEntriesLastNDaysResult: [Entry] = []

    func getEntries(lastNDays: Int) async throws -> [Entry] {
        return getEntriesLastNDaysResult
    }

    // MARK: - getEntries(forMonth:)
    var getEntriesForMonthResult: [Entry] = []

    func getEntries(forMonth month: String) async throws -> [Entry] {
        return getEntriesForMonthResult
    }

    // MARK: - getMonthsAll
    var getMonthsAllResult: [HistoryMonth] = []

    func getMonthsAll() async throws -> [HistoryMonth] {
        return getMonthsAllResult
    }

    // MARK: - getMonthDetail
    var getMonthDetailResult: [Entry] = []

    func getMonthDetail(month: String) async throws -> [Entry] {
        return getMonthDetailResult
    }

    // MARK: - getMonthYear
    var getMonthYearResult: [HistoryMonth] = []

    func getMonthYear() async throws -> [HistoryMonth] {
        return getMonthYearResult
    }

    // MARK: - getProgress
    var getProgressResult: Progress?
    var getProgressError: Error?

    func getProgress() async throws -> Progress {
        if let error = getProgressError { throw error }
        if let result = getProgressResult { return result }
        throw NSError(domain: "MockEntryService", code: -1, userInfo: [NSLocalizedDescriptionKey: "No progress result configured"])
    }

    // MARK: - getStreak
    var getStreakResult: Streak?
    var getStreakError: Error?

    func getStreak() async throws -> Streak {
        if let error = getStreakError { throw error }
        return getStreakResult ?? Streak(current: 0, max: 0)
    }

    // MARK: - syncAllEntriesWithRemote
    func syncAllEntriesWithRemote() async {}

    // MARK: - clearAllData
    func clearAllData() async {}

    // MARK: - clearLastSyncTimestamp
    func clearLastSyncTimestamp() async throws {}

    // MARK: - exportCSV
    func exportCSV() async throws {}
}

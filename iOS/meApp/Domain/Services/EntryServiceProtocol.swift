import Combine
import Foundation

/// Protocol defining the service interface for managing user entries, including CRUD, queries, and progress/statistics.
@MainActor
protocol EntryServiceProtocol {
    var entrySaved: PassthroughSubject<EntryNotification, Never> { get }
    var entryDeleted: PassthroughSubject<EntryNotification, Never> { get }

    /// Syncs all entries with the remote backend.
    func syncAllEntriesWithRemote() async
    func migrateFromSQLiteIfNeeded() async
    func loadDashboardData() async

    /// Clears all entry-related data from the service (memory/cache/state).
    func clearAllData() async
    
    /// Clears the last sync timestamp for the current user.
    func clearLastSyncTimestamp() async throws

    // MARK: - CRUD

    /// Saves a new entry to the data store and/or syncs it with the backend.
    /// - Parameter entry: The entry to be saved.
    func saveNewEntry(_ entry: Entry) async throws

    /// Saves multiple new entries to the data store and/or syncs them with the backend.
    /// - Parameter entries: The entries to be saved.
    func saveNewEntries(_ entries: [Entry]) async throws

    /// Deletes a specific entry from the data store and/or backend.
    /// - Parameter entry: The entry to be deleted.
    func deleteEntry(_ entry: Entry) async throws

    // MARK: - Query

    /// Retrieves all entries for the current user.
    /// - Returns: An array of all entries.
    func getAllEntries() async throws -> [Entry]
    func getAllEntriesAsDTO() async throws -> [BathScaleOperationDTO]

    /// Checks if an entry with the given timestamp exists for the current user.
    /// - Parameter entryTimestamp: The timestamp to check for.
    /// - Returns: True if an entry exists, false otherwise.
    func checkEntryTimestampExists(_ entryTimestamp: String) async throws -> Bool

    /// Gets the total count of entries for the current user.
    /// - Returns: The number of entries.
    func getEntryCount() async throws -> Int

    /// Retrieves the oldest entry for the current user.
    /// - Returns: The oldest entry, or nil if none exist.
    func getOldestEntry() async throws -> Entry?

    /// Retrieves the latest entry for the current user.
    /// - Returns: The latest entry, or nil if none exist.
    func getLatestEntry() async throws -> Entry?

    /// Retrieves entries from the last N days for the current user.
    /// - Parameter lastNDays: The number of days to look back.
    /// - Returns: An array of entries from the last N days.
    func getEntries(lastNDays: Int) async throws -> [Entry]

    /// Retrieves entries for a specific month for the current user.
    /// - Parameter month: The month in 'YYYY-MM' format.
    /// - Returns: An array of entries for the specified month.
    func getEntries(forMonth month: String) async throws -> [Entry]

    /// Retrieves summary data for all months (e.g., for history or charting).
    /// - Returns: An array of HistoryMonth objects representing each month.
    func getMonthsAll() async throws -> [HistoryMonth]

    /// Retrieves detailed entries for a specific month.
    /// - Parameter month: The month in 'YYYY-MM' format.
    /// - Returns: An array of entries for the specified month.
    func getMonthDetail(month: String) async throws -> [Entry]

    /// Retrieves summary data for the last year, grouped by month.
    /// - Returns: An array of HistoryMonth objects for the last year.
    func getMonthYear() async throws -> [HistoryMonth]

    // MARK: - Progress/Stats

    /// Calculates and retrieves the user's progress statistics (e.g., weight change, streaks).
    /// - Returns: A Progress object containing progress data.
    func getProgress() async throws -> Progress

    /// Calculates and retrieves the user's current streak (consecutive days with entries).
    /// - Returns: The current streak count.
    func getStreak() async throws -> Streak
        
    // MARK: - Export
    /// Exports all entries to a CSV file.
    func exportCSV() async throws
}

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
    func migrateBabyEntriesToDecigrams() async
    func loadDashboardData(entryType: EntryType) async

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

    /// Deletes a specific entry by its UUID. Prefer this over `deleteEntry(_:)` from
    /// feature code so the @Model never leaves the service boundary.
    /// - Parameter entryId: The UUID of the entry to delete.
    func deleteEntry(entryId: UUID) async throws

    // MARK: - Snapshot Queries

    /// Retrieves a single entry snapshot by its UUID.
    /// Snapshots are `Sendable` value types — safe to hold across await boundaries.
    /// - Parameter id: The UUID of the entry.
    /// - Returns: The snapshot if found, nil otherwise.
    func fetchEntrySnapshot(byId id: UUID) async throws -> EntrySnapshot?

    /// Retrieves all entry snapshots for the current user.
    /// - Returns: An array of all entry snapshots.
    func fetchAllEntrySnapshots() async throws -> [EntrySnapshot]

    /// Retrieves entry snapshots for a specific month.
    /// - Parameters:
    ///   - month: The month in 'YYYY-MM' format.
    ///   - entryType: Filter by entry type. Defaults to `.scale`.
    /// - Returns: An array of entry snapshots for the specified month.
    func fetchEntrySnapshots(forMonth month: String, entryType: EntryType) async throws -> [EntrySnapshot]

    // MARK: - Query

    /// Retrieves a single entry by its UUID.
    /// - Parameter id: The UUID of the entry.
    /// - Returns: The entry if found, nil otherwise.
    func getEntry(byId id: UUID) async throws -> Entry?

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
    /// - Parameters:
    ///   - lastNDays: The number of days to look back.
    ///   - entryType: Filter by entry type. Defaults to `.scale`.
    /// - Returns: An array of entries from the last N days.
    func getEntries(lastNDays: Int, entryType: EntryType) async throws -> [Entry]

    /// Retrieves entries for a specific month for the current user.
    /// - Parameters:
    ///   - month: The month in 'YYYY-MM' format.
    ///   - entryType: Filter by entry type. Defaults to `.scale`.
    /// - Returns: An array of entries for the specified month.
    func getEntries(forMonth month: String, entryType: EntryType) async throws -> [Entry]

    /// Retrieves summary data for all months (e.g., for history or charting).
    /// - Parameter entryType: Filter by entry type. Defaults to `.scale`.
    /// - Returns: An array of HistoryMonth objects representing each month.
    func getMonthsAll(entryType: EntryType) async throws -> [HistoryMonth]

    /// Retrieves detailed entries for a specific month.
    /// - Parameters:
    ///   - month: The month in 'YYYY-MM' format.
    ///   - entryType: Filter by entry type. Defaults to `.scale`.
    /// - Returns: An array of entries for the specified month.
    func getMonthDetail(month: String, entryType: EntryType) async throws -> [Entry]

    /// Retrieves summary data for the last year, grouped by month.
    /// - Returns: An array of HistoryMonth objects for the last year.
    func getMonthYear() async throws -> [HistoryMonth]

    // MARK: - Progress/Stats

    /// Calculates and retrieves the user's progress statistics (e.g., weight change, streaks).
    /// - Parameter entryType: Filter by entry type. Defaults to `.scale`.
    /// - Returns: A Progress object containing progress data.
    func getProgress(entryType: EntryType) async throws -> Progress

    /// Calculates and retrieves the user's current streak (consecutive days with entries).
    /// - Parameter entryType: Filter by entry type. Defaults to `.scale`.
    /// - Returns: The current streak count.
    func getStreak(entryType: EntryType) async throws -> Streak
        
    // MARK: - Export
    /// Exports all entries to a CSV file.
    func exportCSV() async throws

    // MARK: - BPM Entry CRUD

    /// Creates a new BPM entry and persists it locally.
    /// - Parameter dto: The BPM operation data to save.
    func createBpmEntry(_ dto: BpmOperationDTO) async throws

    /// Fetches all BPM entries for the current user as DTOs.
    /// - Returns: An array of BpmOperationDTO objects.
    func fetchBpmEntries() async throws -> [BpmOperationDTO]

    /// Deletes a BPM entry by its entry timestamp.
    /// - Parameter entryTimestamp: The timestamp identifying the BPM entry to delete.
    func deleteBpmEntry(entryTimestamp: String) async throws

    // MARK: - Baby Entry CRUD

    /// Creates a new baby entry, persists it locally.
    func createBabyEntry(babyId: String, weight: Int, length: Int, note: String, entryTimestamp: String, source: String?) async throws

    /// Loads baby dashboard data (daily/monthly summaries) for a specific baby profile.
    func loadBabyDashboardData(babyId: String) async
    
}

// MARK: - Default Parameter Values

extension EntryServiceProtocol {
    func loadDashboardData() async { await loadDashboardData(entryType: .scale) }
    func getEntries(lastNDays: Int) async throws -> [Entry] { try await getEntries(lastNDays: lastNDays, entryType: .scale) }
    func getEntries(forMonth month: String) async throws -> [Entry] { try await getEntries(forMonth: month, entryType: .scale) }
    func getMonthsAll() async throws -> [HistoryMonth] { try await getMonthsAll(entryType: .scale) }
    func getMonthDetail(month: String) async throws -> [Entry] { try await getMonthDetail(month: month, entryType: .scale) }
    func fetchEntrySnapshots(forMonth month: String) async throws -> [EntrySnapshot] {
        try await fetchEntrySnapshots(forMonth: month, entryType: .scale)
    }
    func getProgress() async throws -> Progress { try await getProgress(entryType: .scale) }
    func getStreak() async throws -> Streak { try await getStreak(entryType: .scale) }
    func createBabyEntry(babyId: String, weight: Int, length: Int, note: String, entryTimestamp: String) async throws {
        try await createBabyEntry(babyId: babyId, weight: weight, length: length, note: note, entryTimestamp: entryTimestamp, source: nil)
    }
}

import Foundation

/// Protocol for abstracting all local (or general) entry data access and operations.
///
/// This protocol defines the contract for interacting with entry data sources (e.g., local database, cache).
/// It includes CRUD operations, queries by user/month, progress/streak calculations, and sync support.
protocol EntryRepositoryProtocol {
    // MARK: - CRUD

    /// Fetches an entry by its unique ID.
    /// - Parameter id: The ID of the entry to fetch.
    /// - Returns: The Entry object, or nil if not found.
    func fetchEntry(byId id: String) async throws -> Entry?

    /// Fetches all entries stored locally.
    /// - Returns: An array of all Entry objects.
    func fetchAllEntries() async throws -> [Entry]

    /// Saves a new entry to the local data store.
    /// - Parameter entry: The Entry object to save.
    func saveEntry(_ entry: Entry) async throws

    /// Updates an existing entry in the local data store.
    /// - Parameter entry: The updated Entry object.
    func updateEntry(_ entry: Entry) async throws

    /// Deletes an entry by its unique ID.
    /// - Parameter id: The ID of the entry to delete.
    func deleteEntry(byId id: String) async throws

    /// Deletes all entries from the local data store.
    func deleteAllEntries() async throws

    // MARK: - Query

    /// Fetches all entries for a specific user.
    /// - Parameter userId: The user ID to filter entries by.
    /// - Returns: An array of Entry objects for the user.
    func fetchEntries(forUserId userId: String) async throws -> [Entry]

    /// Fetches all entries for a specific month and user.
    /// - Parameters:
    ///   - month: The month in 'YYYY-MM' format.
    ///   - userId: The user ID to filter entries by.
    /// - Returns: An array of Entry objects for the month and user.
    func fetchEntries(forMonth month: String, userId: String) async throws -> [Entry]

    /// Fetches the latest entry for a specific user.
    /// - Parameter userId: The user ID to filter entries by.
    /// - Returns: The latest Entry object, or nil if none exist.
    func fetchLatestEntry(forUserId userId: String) async throws -> Entry?

    /// Fetches entries from the last N days for a specific user.
    /// - Parameters:
    ///   - lastNDays: The number of days to look back.
    ///   - userId: The user ID to filter entries by.
    /// - Returns: An array of Entry objects from the last N days.
    func fetchEntries(lastNDays: Int, userId: String) async throws -> [Entry]

    /// Gets the total count of entries for a specific user.
    /// - Parameter userId: The user ID to filter entries by.
    /// - Returns: The number of entries for the user.
    func fetchEntryCount(forUserId userId: String) async throws -> Int

    /// Fetches the oldest entry for a specific user.
    /// - Parameter userId: The user ID to filter entries by.
    /// - Returns: The oldest Entry object, or nil if none exist.
    func fetchOldestEntry(forUserId userId: String) async throws -> Entry?

    // MARK: - Sync

    /// Syncs new and deleted entries with the local data store.
    /// - Parameters:
    ///   - newEntries: Entries to create.
    ///   - deleteOps: Entries to delete.
    func syncEntries(newEntries: [Entry]) async throws

}

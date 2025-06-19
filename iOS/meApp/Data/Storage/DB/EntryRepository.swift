import Foundation
import SwiftData

/// Concrete implementation of EntryRepositoryProtocol for local storage using SwiftData.
/// Handles CRUD operations for Entry entities in a thread-safe manner.
///
@MainActor
final class EntryRepository: EntryRepositoryProtocol {

    // MARK: - Properties
    private let context: ModelContext = PersistenceController.shared.context

    // MARK: - CRUD

    /// Fetches an entry by its unique UUID string.
    /// - Parameter id: The UUID string of the entry to fetch.
    /// - Returns: The Entry object, or nil if not found.
    func fetchEntry(byId id: String) async throws -> Entry? {
        guard let uuid = UUID(uuidString: id) else { return nil }
        let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.id == uuid })
        return try context.fetch(descriptor).first
    }

    /// Fetches all entries stored locally.
    /// - Returns: An array of all Entry objects.
    func fetchAllEntries() async throws -> [Entry] {
        let descriptor = FetchDescriptor<Entry>()
        return try context.fetch(descriptor)
    }

    /// Saves a new entry to the local data store.
    /// - Parameter entry: The Entry object to save.
    func saveEntry(_ entry: Entry) async throws {
        context.insert(entry)
        try context.save()
    }

    /// Updates an existing entry in the local data store.
    /// - Parameter entry: The updated Entry object.
    /// - Note: SwiftData tracks changes automatically; just save context after making changes.
    func updateEntry(_ entry: Entry) async throws {
        try context.save()
    }

    /// Deletes an entry by its unique UUID string.
    /// - Parameter id: The UUID string of the entry to delete.
    func deleteEntry(byId id: String) async throws {
        if let entry = try await fetchEntry(byId: id) {
             context.delete(entry)
            try context.save()
        }
    }

    /// Deletes all entries from the local data store.
    func deleteAllEntries() async throws {
        let all = try await fetchAllEntries()
        for entry in all {
            entry.operationType = "delete"
        }
        try context.save()
    }

    // MARK: - Query

    /// Fetches all entries for a specific user.
    /// - Parameter userId: The user ID to filter entries by.
    /// - Returns: An array of Entry objects for the user.
    func fetchEntries(forUserId userId: String, operationType: String? = nil) async throws -> [Entry] {
        let descriptor: FetchDescriptor<Entry>
        if let opType = operationType {
            descriptor = FetchDescriptor<Entry>(predicate: #Predicate {
                $0.accountId == userId && $0.operationType == opType
            })
        } else {
            descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.accountId == userId })
        }
        return try context.fetch(descriptor)
    }

    /// Fetches all entries for a specific user and timestamp.
    /// - Parameters:
    ///   - userId: The user ID to filter entries by.
    ///   - timestamp: The timestamp to filter entries by.
    /// - Returns: An array of Entry objects for the user and timestamp.
    func fetchEntriesOfTimestamp(forUserId userId: String, timestamp: String) async throws -> [Entry] {
        let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.accountId == userId && $0.entryTimestamp == timestamp })
        return try context.fetch(descriptor)
    }

    /// Fetches all entries for a specific month and user.
    /// - Parameters:
    ///   - month: The month in 'YYYY-MM' format (e.g., "2025-05").
    ///   - userId: The user ID to filter entries by.
    /// - Returns: An array of Entry objects for the month and user.
    /// - Note: entryTimestamp is in ISO8601 format (e.g., "2025-05-30T09:52:43.548Z").
    ///   This method filters entries whose entryTimestamp starts with the given month prefix.
    func fetchEntries(forMonth month: String, userId: String) async throws -> [Entry] {
        // month: "2025-05"
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM"
        guard let startDate = dateFormatter.date(from: month) else { return [] }
        var comps = DateComponents()
        comps.month = 1
        guard let endDate = Calendar.current.date(byAdding: comps, to: startDate) else { return [] }
        let isoFormatter = ISO8601DateFormatter()
        let startString = isoFormatter.string(from: startDate)
        let endString = isoFormatter.string(from: endDate)
        let descriptor = FetchDescriptor<Entry>(predicate: #Predicate {
            $0.accountId == userId &&
            $0.entryTimestamp >= startString &&
            $0.entryTimestamp < endString
        })
        return try context.fetch(descriptor)
    }

    /// Fetches all unsynced entries from the local data store.
    /// - Returns: An array of Entry objects that are not synced.
    func fetchUnsyncedEntries(forUserId userId: String) async throws -> [Entry] {
        let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.accountId == userId && $0.isSynced == false })
        return try context.fetch(descriptor)
    }

    /// Fetches the latest entry for a specific user.
    /// - Parameter userId: The user ID to filter entries by.
    /// - Returns: The latest Entry object, or nil if none exist.
    func fetchLatestEntry(forUserId userId: String) async throws -> Entry? {
        let descriptor = FetchDescriptor<Entry>(
            predicate: #Predicate { $0.accountId == userId },
            sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .reverse)]
        )
        return try context.fetch(descriptor).first
    }

    /// Fetches entries from the last N days for a specific user.
    /// - Parameters:
    ///   - lastNDays: The number of days to look back.
    ///   - userId: The user ID to filter entries by.
    /// - Returns: An array of Entry objects from the last N days.
    func fetchEntries(lastNDays: Int, userId: String) async throws -> [Entry] {
        let calendar = Calendar.current
        let now = Date()
        guard let earliest = calendar.date(byAdding: .day, value: -lastNDays, to: now) else { return [] }
        let earliestString = ISO8601DateFormatter().string(from: earliest)
        let nowString = ISO8601DateFormatter().string(from: now)
        let descriptor = FetchDescriptor<Entry>(predicate: #Predicate {
            $0.accountId == userId && $0.entryTimestamp >= earliestString && $0.entryTimestamp <= nowString
        })
        return try context.fetch(descriptor)
    }

    /// Gets the total count of entries for a specific user.
    /// - Parameter userId: The user ID to filter entries by.
    /// - Returns: The number of entries for the user.
    func fetchEntryCount(forUserId userId: String) async throws -> Int {
        let entries = try await fetchEntries(forUserId: userId)
        return entries.count
    }

    /// Fetches the oldest entry for a specific user.
    /// - Parameter userId: The user ID to filter entries by.
    /// - Returns: The oldest Entry object, or nil if none exist.
    func fetchOldestEntry(forUserId userId: String) async throws -> Entry? {
        let descriptor = FetchDescriptor<Entry>(
            predicate: #Predicate { $0.accountId == userId },
            sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .forward)]
        )
        return try context.fetch(descriptor).first
    }

    /// Checks if an entry with a specific timestamp exists for a user.
    /// - Parameters:
    ///   - userId: The user ID to filter entries by.
    ///   - entryTimestamp: The timestamp to check for.
    /// - Returns: True if the entry exists, false otherwise.
    func checkEntryTimestampExists(forUserId userId: String, entryTimestamp: String) async throws -> Bool {
        let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.accountId == userId && $0.entryTimestamp == entryTimestamp })
        return try context.fetch(descriptor).first != nil
    }

    // MARK: - Sync

    /// Syncs new and deleted entries with the local data store.
    /// - Parameters:
    ///   - newEntries: Entries to create.
    func syncEntries(newEntries: [Entry]) async throws {
        for entry in newEntries {
            context.insert(entry)
        }
        try context.save()
    }
}

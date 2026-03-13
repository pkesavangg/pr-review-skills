// This repository intentionally aggregates all Entry CRUD operations to maintain
// a single source of truth for data access patterns. Splitting would fragment
// the SwiftData context management and reduce maintainability.

import Foundation
import SwiftData

/// Concrete implementation of EntryRepositoryProtocol for local storage using SwiftData.
/// Handles CRUD operations for Entry entities in a thread-safe manner.
///
/// - Note: This repository uses background contexts for all operations to avoid blocking the main thread.
///   Methods that need MainActor access are explicitly marked.
@MainActor
final class EntryRepository: EntryRepositoryProtocol {

    // MARK: - Properties
    private let container: ModelContainer

    init(container: ModelContainer? = nil) {
        self.container = container ?? PersistenceController.shared.container
    }
    
    /// Executes work on a background ModelContext to avoid blocking the main actor.
    /// - Parameter work: Closure that performs the work using the provided background context.
    /// - Returns: The result of the work.
    private func performBackgroundTask<T>(_ work: (ModelContext) throws -> T) async throws -> T {
        let backgroundContext = ModelContext(container)
        return try work(backgroundContext)
    }

    // MARK: - CRUD

    /// Fetches an entry by its unique UUID string.
    /// - Parameter id: The UUID string of the entry to fetch.
    /// - Returns: The Entry object, or nil if not found.
    func fetchEntry(byId id: String) async throws -> Entry? {
        guard let uuid = UUID(uuidString: id) else { return nil }
        return try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.id == uuid })
            return try ctx.fetch(descriptor).first
        }
    }

    /// Fetches all entries stored locally.
    /// - Returns: An array of all Entry objects.
    func fetchAllEntries() async throws -> [Entry] {
        return try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<Entry>()
            return try ctx.fetch(descriptor)
        }
    }

    /// Saves a new entry to the local data store.
    /// - Parameter entry: The Entry object to save.
    func saveEntry(_ entry: Entry) async throws { // swiftlint:disable:this function_body_length
        // Extract all data from entry before crossing actor boundary
        let id = entry.id
        let accountId = entry.accountId
        let entryTimestamp = entry.entryTimestamp
        let serverTimestamp = entry.serverTimestamp
        let operationType = entry.operationType
        let deviceType = entry.deviceType
        let isSynced = entry.isSynced
        let isFailedToSync = entry.isFailedToSync
        let attempts = entry.attempts

        // Extract relationship data
        let scaleEntryData = entry.scaleEntry.map { scaleEntry in
            ScaleEntryData(
                weight: scaleEntry.weight,
                bodyFat: scaleEntry.bodyFat,
                muscleMass: scaleEntry.muscleMass,
                water: scaleEntry.water,
                bmi: scaleEntry.bmi,
                source: scaleEntry.source
            )
        }
        let scaleEntryMetricData = entry.scaleEntryMetric.map { metric in
            ScaleMetricData(
                bmr: metric.bmr,
                metabolicAge: metric.metabolicAge,
                proteinPercent: metric.proteinPercent,
                pulse: metric.pulse,
                skeletalMusclePercent: metric.skeletalMusclePercent,
                subcutaneousFatPercent: metric.subcutaneousFatPercent,
                visceralFatLevel: metric.visceralFatLevel,
                boneMass: metric.boneMass,
                impedance: metric.impedance,
                unit: metric.unit
            )
        }

        try await performBackgroundTask { ctx in
            let newEntry = Entry(
                id: id,
                entryTimestamp: entryTimestamp,
                accountId: accountId,
                operationType: operationType,
                serverTimestamp: serverTimestamp,
                deviceType: deviceType,
                isSynced: isSynced
            )
            newEntry.isFailedToSync = isFailedToSync
            newEntry.attempts = attempts

            // Recreate relationships in background context
            if let data = scaleEntryData {
                newEntry.scaleEntry = BathScaleEntry(
                    weight: data.weight,
                    bodyFat: data.bodyFat,
                    muscleMass: data.muscleMass,
                    water: data.water,
                    bmi: data.bmi,
                    source: data.source
                )
            }

            if let data = scaleEntryMetricData {
                newEntry.scaleEntryMetric = BathScaleMetric(
                    bmr: data.bmr,
                    metabolicAge: data.metabolicAge,
                    proteinPercent: data.proteinPercent,
                    pulse: data.pulse,
                    skeletalMusclePercent: data.skeletalMusclePercent,
                    subcutaneousFatPercent: data.subcutaneousFatPercent,
                    visceralFatLevel: data.visceralFatLevel,
                    boneMass: data.boneMass,
                    impedance: data.impedance,
                    unit: data.unit
                )
            }

            ctx.insert(newEntry)
            try ctx.save()
            return ()
        }
    }

    /// Updates an existing entry in the local data store.
    /// - Parameter entry: The updated Entry object.
    /// - Note: SwiftData tracks changes automatically; just save context after making changes.
    func updateEntry(_ entry: Entry) async throws {
        // Capture scalar values to avoid cross-context model usage in the predicate and assignments
        let id = entry.id
        let accountId = entry.accountId
        let entryTimestamp = entry.entryTimestamp
        let serverTimestamp = entry.serverTimestamp
        let operationType = entry.operationType
        let deviceType = entry.deviceType
        let isSynced = entry.isSynced
        let isFailedToSync = entry.isFailedToSync
        let attempts = entry.attempts

        try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.id == id })
            if let existing = try ctx.fetch(descriptor).first {
                existing.accountId = accountId
                existing.entryTimestamp = entryTimestamp
                existing.serverTimestamp = serverTimestamp
                existing.operationType = operationType
                existing.deviceType = deviceType
                existing.isSynced = isSynced
                existing.isFailedToSync = isFailedToSync
                existing.attempts = attempts
                try ctx.save()
            }
            return ()
        }
    }

    /// Updates only the sync-related fields of an entry by its UUID string.
    /// Use this instead of mutating @Model directly then calling updateEntry (R7/R9).
    func updateEntrySyncStatus(entryId: String, isSynced: Bool, isFailedToSync: Bool, attempts: Int) async throws {
        guard let uuid = UUID(uuidString: entryId) else { return }
        try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.id == uuid })
            if let existing = try ctx.fetch(descriptor).first {
                existing.isSynced = isSynced
                existing.isFailedToSync = isFailedToSync
                existing.attempts = attempts
                try ctx.save()
            }
            return ()
        }
    }

    /// Deletes an entry by its unique UUID string.
    /// - Parameter id: The UUID string of the entry to delete.
    func deleteEntry(byId id: String) async throws {
        try await performBackgroundTask { ctx in
            guard let uuid = UUID(uuidString: id) else { return () }
            let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.id == uuid })
            if let entry = try ctx.fetch(descriptor).first {
                ctx.delete(entry)
                try ctx.save()
            }
            return ()
        }
    }

    /// Deletes all entries from the local data store.
    func deleteAllEntries() async throws {
        try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<Entry>()
            let all = try ctx.fetch(descriptor)
            for entry in all {
                ctx.delete(entry)
            }
            try ctx.save()
            return ()
        }
    }

    // MARK: - Query

    /// Fetches all entries for a specific user.
    /// - Parameter userId: The user ID to filter entries by.
    /// - Returns: An array of Entry objects for the user.
    func fetchEntries(forUserId userId: String, operationType: String? = nil) async throws -> [Entry] {
        return try await performBackgroundTask { ctx in
            let descriptor: FetchDescriptor<Entry>
            if let opType = operationType {
                descriptor = FetchDescriptor<Entry>(predicate: #Predicate {
                    $0.accountId == userId && $0.operationType == opType
                })
            } else {
                descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.accountId == userId })
            }
            return try ctx.fetch(descriptor)
        }
    }

    /// Fetches all entries for a specific user and timestamp.
    /// - Parameters:
    ///   - userId: The user ID to filter entries by.
    ///   - timestamp: The timestamp to filter entries by.
    /// - Returns: An array of Entry objects for the user and timestamp.
    func fetchEntriesOfTimestamp(forUserId userId: String, timestamp: String) async throws -> [Entry] {
        return try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.accountId == userId && $0.entryTimestamp == timestamp })
            return try ctx.fetch(descriptor)
        }
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
        return try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<Entry>(predicate: #Predicate {
                $0.accountId == userId &&
                $0.entryTimestamp >= startString &&
                $0.entryTimestamp < endString
            })
            return try ctx.fetch(descriptor)
        }
    }

    /// Fetches all entries for a specific day and user.
    /// - Parameters:
    ///   - day: The day in 'YYYY-MM-DD' format (e.g., "2025-05-30").
    ///   - userId: The user ID to filter entries by.
    /// - Returns: An array of Entry objects for the day and user.
    func fetchEntries(forDay day: String, userId: String) async throws -> [Entry] {
      let dateFormatter = DateFormatter()
      dateFormatter.dateFormat = "yyyy-MM-dd"
      guard let startDate = dateFormatter.date(from: day) else { return [] }
      var comps = DateComponents()
      comps.day = 1
      guard let endDate = Calendar.current.date(byAdding: comps, to: startDate) else { return [] }
      let isoFormatter = ISO8601DateFormatter()
      let startString = isoFormatter.string(from: startDate)
      let endString = isoFormatter.string(from: endDate)
      return try await performBackgroundTask { ctx in
          let descriptor = FetchDescriptor<Entry>(predicate: #Predicate {
            $0.accountId == userId &&
            $0.entryTimestamp >= startString &&
            $0.entryTimestamp < endString
          })
          return try ctx.fetch(descriptor)
      }
    }

    /// Fetches all unsynced entries from the local data store.
    /// - Returns: An array of Entry objects that are not synced.
    func fetchUnsyncedEntries(forUserId userId: String) async throws -> [Entry] {
        return try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.accountId == userId && $0.isSynced == false })
            return try ctx.fetch(descriptor)
        }
    }

    /// Fetches the latest entry for a specific user.
    /// - Parameter userId: The user ID to filter entries by.
    /// - Returns: The latest Entry object, or nil if none exist.
    func fetchLatestEntry(forUserId userId: String) async throws -> Entry? {
        return try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<Entry>(
                predicate: #Predicate { $0.accountId == userId },
                sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .reverse)]
            )
            return try ctx.fetch(descriptor).first
        }
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
        return try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<Entry>(predicate: #Predicate {
                $0.accountId == userId && $0.entryTimestamp >= earliestString && $0.entryTimestamp <= nowString
            })
            return try ctx.fetch(descriptor)
        }
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
        return try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<Entry>(
                predicate: #Predicate { $0.accountId == userId },
                sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .forward)]
            )
            return try ctx.fetch(descriptor).first
        }
    }

    /// Checks if an entry with a specific timestamp exists for a user.
    /// - Parameters:
    ///   - userId: The user ID to filter entries by.
    ///   - entryTimestamp: The timestamp to check for.
    /// - Returns: True if the entry exists, false otherwise.
    func checkEntryTimestampExists(forUserId userId: String, entryTimestamp: String) async throws -> Bool {
        return try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.accountId == userId && $0.entryTimestamp == entryTimestamp })
            return try ctx.fetch(descriptor).first != nil
        }
    }

    // MARK: - Thread-Safe Fetch Methods (Return DTOs or Identifiers)

    /// Fetches entries and returns DTOs with all relationship data extracted.
    /// This is the preferred method for background fetches where you need relationship data.
    /// - Parameters:
    ///   - userId: The user ID to filter entries by.
    ///   - operationType: Optional operation type filter.
    /// - Returns: Array of BathScaleOperationDTO with all data extracted.
    func fetchEntriesAsDTO(forUserId userId: String, operationType: String? = nil) async throws -> [BathScaleOperationDTO] {
        return try await performBackgroundTask { backgroundContext in
            let descriptor: FetchDescriptor<Entry>
            if let opType = operationType {
                descriptor = FetchDescriptor<Entry>(
                    predicate: #Predicate { $0.accountId == userId && $0.operationType == opType },
                    sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .reverse)]
                )
            } else {
                descriptor = FetchDescriptor<Entry>(
                    predicate: #Predicate { $0.accountId == userId },
                    sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .reverse)]
                )
            }
            let entries = try backgroundContext.fetch(descriptor)
            // Extract ALL data including relationships INSIDE the background context
            return entries.map { $0.toOperationDTO() }
        }
    }

    /// Fetches entry identifiers only for later MainActor refetch.
    /// Use this when you need to modify entries after fetching.
    /// - Parameters:
    ///   - userId: The user ID to filter entries by.
    ///   - operationType: Optional operation type filter.
    /// - Returns: Array of PersistentIdentifier for later refetch on MainActor.
    func fetchEntryIdentifiers(forUserId userId: String, operationType: String? = nil) async throws -> [PersistentIdentifier] {
        return try await performBackgroundTask { backgroundContext in
            let descriptor: FetchDescriptor<Entry>
            if let opType = operationType {
                descriptor = FetchDescriptor<Entry>(
                    predicate: #Predicate { $0.accountId == userId && $0.operationType == opType },
                    sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .reverse)]
                )
            } else {
                descriptor = FetchDescriptor<Entry>(
                    predicate: #Predicate { $0.accountId == userId },
                    sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .reverse)]
                )
            }
            let entries = try backgroundContext.fetch(descriptor)
            return entries.map { $0.persistentModelID }
        }
    }

    /// Fetches a single entry and returns its DTO with all relationship data.
    /// - Parameter id: The UUID string of the entry.
    /// - Returns: BathScaleOperationDTO or nil if not found.
    func fetchEntryAsDTO(byId id: String) async throws -> BathScaleOperationDTO? {
        guard let uuid = UUID(uuidString: id) else { return nil }
        return try await performBackgroundTask { backgroundContext in
            let descriptor = FetchDescriptor<Entry>(predicate: #Predicate { $0.id == uuid })
            guard let entry = try backgroundContext.fetch(descriptor).first else { return nil }
            // Extract ALL data inside the background context
            return entry.toOperationDTO()
        }
    }

    /// Fetches the latest entry as DTO for a user.
    /// - Parameter userId: The user ID to filter entries by.
    /// - Returns: BathScaleOperationDTO or nil if none exist.
    func fetchLatestEntryAsDTO(forUserId userId: String, operationType: String? = nil) async throws -> BathScaleOperationDTO? {
        return try await performBackgroundTask { backgroundContext in
            let descriptor: FetchDescriptor<Entry>
            if let opType = operationType {
                descriptor = FetchDescriptor<Entry>(
                    predicate: #Predicate { $0.accountId == userId && $0.operationType == opType },
                    sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .reverse)]
                )
            } else {
                descriptor = FetchDescriptor<Entry>(
                    predicate: #Predicate { $0.accountId == userId },
                    sortBy: [SortDescriptor(\Entry.entryTimestamp, order: .reverse)]
                )
            }
            guard let entry = try backgroundContext.fetch(descriptor).first else { return nil }
            return entry.toOperationDTO()
        }
    }

    /// Fetches an entry by PersistentIdentifier on the MainActor context.
    /// Use this after fetching identifiers to get live, modifiable models.
    /// - Parameter id: The PersistentIdentifier of the entry.
    /// - Returns: Entry or nil if not found in MainActor context.
    @MainActor
    func fetchEntry(byIdentifier id: PersistentIdentifier) -> Entry? {
        return PersistenceController.shared.context.registeredModel(for: id)
    }

    /// Fetches multiple entries by PersistentIdentifiers on the MainActor context.
    /// - Parameter ids: Array of PersistentIdentifiers.
    /// - Returns: Array of Entry objects found in MainActor context.
    @MainActor
    func fetchEntries(byIdentifiers ids: [PersistentIdentifier]) -> [Entry] {
        return ids.compactMap { PersistenceController.shared.context.registeredModel(for: $0) }
    }

    // MARK: - Main Actor Context Fetch Methods

    /// Refetches entries by their IDs using the main actor's ModelContext.
    /// This allows safe access to SwiftData properties without cross-context issues.
    /// - Parameter entryIds: Array of entry UUIDs to refetch.
    /// - Returns: Dictionary mapping entry IDs to their Entry objects (or nil if not found).
    func refetchEntriesOnMainActor(entryIds: [UUID]) async throws -> [UUID: Entry] {
        let mainContext = PersistenceController.shared.context

        // Return empty dictionary if no IDs provided
        guard !entryIds.isEmpty else { return [:] }

        // Convert to Set for efficient membership checking
        let entryIdsSet = Set(entryIds)

        // Use a single query with Set membership check for better performance
        // SwiftData Predicate supports checking if a value is in a Set
        let descriptor = FetchDescriptor<Entry>(
            predicate: #Predicate<Entry> { entry in
                entryIdsSet.contains(entry.id)
            }
        )

        let entries = try mainContext.fetch(descriptor)

        // Map entries to dictionary by ID
        var result: [UUID: Entry] = [:]
        for entry in entries {
            result[entry.id] = entry
        }

        return result
    }
    
    // MARK: - Data Extraction Helpers
    
    /// Extracts SwiftData property values from an Entry immediately after fetching.
    /// This must be called synchronously within the fetch context to avoid SwiftData access crashes.
    /// - Parameter entry: The Entry object to extract values from.
    /// - Returns: A BathScaleOperationDTO with all extracted values, or nil if entry is nil.
    static func extractDTO(from entry: Entry?) -> BathScaleOperationDTO? {
        guard let entry = entry else { return nil }
        // Access SwiftData properties synchronously within the fetch context
        return entry.toOperationDTO()
    }
    
    /// Extracts weight value from an Entry immediately after fetching.
    /// This must be called synchronously within the fetch context to avoid SwiftData access crashes.
    /// - Parameter entry: The Entry object to extract weight from.
    /// - Returns: The weight value, or 0 if not available.
    static func extractWeight(from entry: Entry?) -> Int {
        guard let entry = entry else { return 0 }
        return entry.scaleEntry?.weight ?? 0
    }

    // MARK: - Sync

    /// Syncs new and deleted entries with the local data store.
    /// - Parameters:
    ///   - newEntries: Entries to create.
    func syncEntries(newEntries: [Entry]) async throws {
        let entriesData = extractEntryData(from: newEntries)
        try await createEntriesInBackground(entriesData: entriesData)
    }
    
    // MARK: - Sync Helpers
    
    /// Sendable data structures for crossing actor boundaries
    private struct EntryData: Sendable {
        let id: UUID
        let accountId: String
        let entryTimestamp: String
        let serverTimestamp: String?
        let operationType: String
        let deviceType: String
        let isSynced: Bool
        let isFailedToSync: Bool
        let attempts: Int
        let scaleEntry: ScaleEntryData?
        let scaleEntryMetric: ScaleMetricData?
    }

    private struct ScaleEntryData: Sendable {
        let weight: Int?
        let bodyFat: Int?
        let muscleMass: Int?
        let water: Int?
        let bmi: Int?
        let source: String?
    }

    private struct ScaleMetricData: Sendable {
        let bmr: Int?
        let metabolicAge: Int?
        let proteinPercent: Int?
        let pulse: Int?
        let skeletalMusclePercent: Int?
        let subcutaneousFatPercent: Int?
        let visceralFatLevel: Int?
        let boneMass: Int?
        let impedance: Int?
        let unit: String?
    }
    
    /// Extracts entry data into Sendable structures before crossing actor boundary
    private func extractEntryData(from entries: [Entry]) -> [EntryData] {
        return entries.map { entry in
            let scaleEntryData = entry.scaleEntry.map { scaleEntry in
                ScaleEntryData(
                    weight: scaleEntry.weight,
                    bodyFat: scaleEntry.bodyFat,
                    muscleMass: scaleEntry.muscleMass,
                    water: scaleEntry.water,
                    bmi: scaleEntry.bmi,
                    source: scaleEntry.source
                )
            }
            let metricData = entry.scaleEntryMetric.map { metric in
                ScaleMetricData(
                    bmr: metric.bmr,
                    metabolicAge: metric.metabolicAge,
                    proteinPercent: metric.proteinPercent,
                    pulse: metric.pulse,
                    skeletalMusclePercent: metric.skeletalMusclePercent,
                    subcutaneousFatPercent: metric.subcutaneousFatPercent,
                    visceralFatLevel: metric.visceralFatLevel,
                    boneMass: metric.boneMass,
                    impedance: metric.impedance,
                    unit: metric.unit
                )
            }
            return EntryData(
                id: entry.id,
                accountId: entry.accountId,
                entryTimestamp: entry.entryTimestamp,
                serverTimestamp: entry.serverTimestamp,
                operationType: entry.operationType,
                deviceType: entry.deviceType,
                isSynced: entry.isSynced,
                isFailedToSync: entry.isFailedToSync,
                attempts: entry.attempts,
                scaleEntry: scaleEntryData,
                scaleEntryMetric: metricData
            )
        }
    }
    
    /// Creates entries in background context from extracted data
    private func createEntriesInBackground(entriesData: [EntryData]) async throws {
        try await performBackgroundTask { [self] ctx in
            for data in entriesData {
                let newEntry = self.createEntry(from: data)
                ctx.insert(newEntry)
            }
            try ctx.save()
            return ()
        }
    }
    
    /// Creates an Entry instance from EntryData
    private func createEntry(from data: EntryData) -> Entry {
        let newEntry = Entry(
            id: data.id,
            entryTimestamp: data.entryTimestamp,
            accountId: data.accountId,
            operationType: data.operationType,
            serverTimestamp: data.serverTimestamp,
            deviceType: data.deviceType,
            isSynced: data.isSynced
        )
        newEntry.isFailedToSync = data.isFailedToSync
        newEntry.attempts = data.attempts

        if let seData = data.scaleEntry {
            newEntry.scaleEntry = BathScaleEntry(
                weight: seData.weight,
                bodyFat: seData.bodyFat,
                muscleMass: seData.muscleMass,
                water: seData.water,
                bmi: seData.bmi,
                source: seData.source
            )
        }

        if let mData = data.scaleEntryMetric {
            newEntry.scaleEntryMetric = BathScaleMetric(
                bmr: mData.bmr,
                metabolicAge: mData.metabolicAge,
                proteinPercent: mData.proteinPercent,
                pulse: mData.pulse,
                skeletalMusclePercent: mData.skeletalMusclePercent,
                subcutaneousFatPercent: mData.subcutaneousFatPercent,
                visceralFatLevel: mData.visceralFatLevel,
                boneMass: mData.boneMass,
                impedance: mData.impedance,
                unit: mData.unit
            )
        }
        
        return newEntry
    }
}

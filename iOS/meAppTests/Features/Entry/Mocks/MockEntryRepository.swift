import Foundation
@testable import meApp

@MainActor
final class MockEntryRepository: EntryRepositoryProtocol {
    var entries: [Entry] = []

    var saveEntryError: Error?
    var updateEntryError: Error?
    var updateEntryServerEntryIdError: Error?
    var deleteEntryError: Error?
    var deleteAllEntriesError: Error?
    var fetchEntriesAsDTOError: Error?
    var fetchEntriesAsDTOResults: [Result<[BathScaleOperationDTO], Error>] = []
    var fetchEntriesAsDTODelayNanoseconds: UInt64 = 0
    var fetchUnsyncedEntriesError: Error?
    var fetchLatestEntryError: Error?

    private(set) var saveEntryCalls = 0
    private(set) var updateEntryCalls = 0
    private(set) var deleteEntryCalls = 0
    private(set) var updateEntrySyncStatusCalls = 0
    private(set) var updateEntryServerEntryIdCalls = 0
    private(set) var lastServerEntryId: String?
    private(set) var fetchEntriesAsDTOCalls = 0

    private(set) var lastSavedEntry: Entry?
    private(set) var lastUpdatedEntry: Entry?
    private(set) var lastDeletedEntryId: String?

    func fetchEntry(byId id: String) async throws -> Entry? {
        entries.first { $0.id.uuidString == id }
    }

    func fetchAllEntries() async throws -> [Entry] {
        entries
    }

    func saveEntry(_ entry: Entry) async throws {
        saveEntryCalls += 1
        lastSavedEntry = entry
        if let saveEntryError { throw saveEntryError }
        entries.removeAll { $0.id == entry.id }
        entries.append(entry)
    }

    func updateEntry(_ entry: Entry) async throws {
        updateEntryCalls += 1
        lastUpdatedEntry = entry
        if let updateEntryError { throw updateEntryError }
        entries.removeAll { $0.id == entry.id }
        entries.append(entry)
    }

    func updateEntryServerEntryId(entryId: String, serverEntryId: String) async throws {
        updateEntryServerEntryIdCalls += 1
        lastServerEntryId = serverEntryId
        if let updateEntryServerEntryIdError { throw updateEntryServerEntryIdError }
        if let entry = entries.first(where: { $0.id.uuidString == entryId }) {
            entry.serverEntryId = serverEntryId
        }
    }

    func updateEntrySyncStatus(entryId: String, isSynced: Bool, isFailedToSync: Bool, attempts: Int) async throws {
        updateEntrySyncStatusCalls += 1
        guard let entry = entries.first(where: { $0.id.uuidString == entryId }) else { return }
        entry.isSynced = isSynced
        entry.isFailedToSync = isFailedToSync
        entry.attempts = attempts
    }

    func deleteEntry(byId id: String) async throws {
        deleteEntryCalls += 1
        lastDeletedEntryId = id
        if let deleteEntryError { throw deleteEntryError }
        entries.removeAll { $0.id.uuidString == id }
    }

    func deleteAllEntries() async throws {
        if let deleteAllEntriesError { throw deleteAllEntriesError }
        entries.removeAll()
    }

    func fetchEntries(forUserId userId: String, operationType: String?) async throws -> [Entry] {
        filteredEntries(userId: userId, operationType: operationType)
    }

    func fetchEntriesOfTimestamp(forUserId userId: String, timestamp: String) async throws -> [Entry] {
        entries.filter { $0.accountId == userId && $0.entryTimestamp == timestamp }
    }

    func fetchEntries(forMonth month: String, userId: String) async throws -> [Entry] {
        entries.filter {
            $0.accountId == userId &&
            DateTimeTools.getLocalMonthStringFromUTCDate($0.entryTimestamp) == month
        }
    }

    func fetchEntries(forDay day: String, userId: String) async throws -> [Entry] {
        entries.filter {
            $0.accountId == userId &&
            DateTimeTools.getLocalDateStringFromUTCDate($0.entryTimestamp) == day
        }
    }

    func fetchUnsyncedEntries(forUserId userId: String) async throws -> [Entry] {
        if let fetchUnsyncedEntriesError { throw fetchUnsyncedEntriesError }
        return entries.filter { $0.accountId == userId && !$0.isSynced }
    }

    func fetchLatestEntry(forUserId userId: String) async throws -> Entry? {
        if let fetchLatestEntryError { throw fetchLatestEntryError }
        return entries
            .filter { $0.accountId == userId }
            .max { $0.entryTimestamp < $1.entryTimestamp }
    }

    func fetchEntries(lastNDays: Int, userId: String) async throws -> [Entry] {
        guard let cutoff = Calendar.current.date(byAdding: .day, value: -lastNDays, to: Date()) else {
            return []
        }
        return entries.filter { entry in
            guard entry.accountId == userId, let entryDate = DateTimeTools.parse(entry.entryTimestamp) else { return false }
            return entryDate >= cutoff
        }
    }

    func fetchEntryCount(forUserId userId: String) async throws -> Int {
        entries.filter { $0.accountId == userId }.count
    }

    func fetchOldestEntry(forUserId userId: String) async throws -> Entry? {
        entries
            .filter { $0.accountId == userId }
            .min { $0.entryTimestamp < $1.entryTimestamp }
    }

    func checkEntryTimestampExists(forUserId userId: String, entryTimestamp: String) async throws -> Bool {
        entries.contains { $0.accountId == userId && $0.entryTimestamp == entryTimestamp }
    }

    func fetchEntriesAsDTO(forUserId userId: String, operationType: String?) async throws -> [BathScaleOperationDTO] {
        fetchEntriesAsDTOCalls += 1
        if fetchEntriesAsDTODelayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: fetchEntriesAsDTODelayNanoseconds)
        }
        if !fetchEntriesAsDTOResults.isEmpty {
            let nextResult = fetchEntriesAsDTOResults.removeFirst()
            switch nextResult {
            case .success(let dtos):
                return dtos
            case .failure(let error):
                throw error
            }
        }
        if let fetchEntriesAsDTOError { throw fetchEntriesAsDTOError }
        return filteredEntries(userId: userId, operationType: operationType).map { $0.toOperationDTO() }
    }

    func fetchEntriesAsBpmDTO(forUserId userId: String, operationType: String?) async throws -> [BpmOperationDTO] {
        filteredEntries(userId: userId, operationType: operationType)
            .filter { $0.entryType == EntryType.bpm.rawValue }
            .map { $0.toBpmOperationDTO() }
    }

    func syncEntries(newEntries: [Entry]) async throws {
        for entry in newEntries {
            entries.removeAll { $0.id == entry.id }
            entries.append(entry)
        }
    }

    private func filteredEntries(userId: String, operationType: String?) -> [Entry] {
        entries.filter { entry in
            guard entry.accountId == userId else { return false }
            guard let operationType else { return true }
            return entry.operationType == operationType
        }
    }
}

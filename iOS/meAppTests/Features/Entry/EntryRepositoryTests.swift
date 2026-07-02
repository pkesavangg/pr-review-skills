import Foundation
@testable import meApp
import SwiftData
import Testing

@Suite(.serialized)
@MainActor
struct EntryRepositoryTests {

    // MARK: - Factory

    func makeSUT() -> EntryRepository {
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        do {
            let container = try ModelContainer(
                for: Entry.self,
                BathScaleEntry.self,
                BathScaleMetric.self,
                configurations: config
            )
            return EntryRepository(container: container)
        } catch {
            fatalError("Failed to create in-memory ModelContainer: \(error)")
        }
    }

    // MARK: - CRUD: Save & Fetch

    @Test("saveEntry persists entry and fetchAllEntries retrieves it")
    func saveAndFetchAll() async throws {
        let sut = makeSUT()
        let entry = EntryTestFixtures.makeEntry()

        try await sut.saveEntry(entry)
        let all = try await sut.fetchAllEntries()

        #expect(all.count == 1)
        #expect(all.first?.accountId == "acct-1")
    }

    @Test("saveEntry preserves scale entry relationship data")
    func savePreservesScaleEntry() async throws {
        let sut = makeSUT()
        let entry = EntryTestFixtures.makeEntry(weight: 1800, bodyFat: 250, muscleMass: 820)

        try await sut.saveEntry(entry)
        let all = try await sut.fetchAllEntries()

        #expect(all.first?.scaleEntry?.weight == 1800)
        #expect(all.first?.scaleEntry?.bodyFat == 250)
        #expect(all.first?.scaleEntry?.muscleMass == 820)
    }

    @Test("saveEntry preserves scale metric relationship data")
    func savePreservesScaleMetric() async throws {
        let sut = makeSUT()
        let entry = EntryTestFixtures.makeEntry(bmr: 1600, metabolicAge: 35, pulse: 72)

        try await sut.saveEntry(entry)
        let all = try await sut.fetchAllEntries()

        #expect(all.first?.scaleEntryMetric?.bmr == 1600)
        #expect(all.first?.scaleEntryMetric?.metabolicAge == 35)
        #expect(all.first?.scaleEntryMetric?.pulse == 72)
    }

    @Test("saveEntry without scale data persists entry with nil relationships")
    func saveWithoutScaleData() async throws {
        let sut = makeSUT()
        let entry = Entry(
            entryTimestamp: "2026-03-01T08:00:00Z",
            accountId: "acct-1",
            operationType: "create"
        )

        try await sut.saveEntry(entry)
        let all = try await sut.fetchAllEntries()

        #expect(all.count == 1)
        #expect(all.first?.scaleEntry == nil)
        #expect(all.first?.scaleEntryMetric == nil)
    }

    @Test("fetchEntry byId returns matching entry")
    func fetchEntryById() async throws {
        let sut = makeSUT()
        let id = UUID()
        let entry = EntryTestFixtures.makeEntry(id: id)

        try await sut.saveEntry(entry)
        let found = try await sut.fetchEntry(byId: id.uuidString)

        #expect(found != nil)
        #expect(found?.id == id)
    }

    @Test("fetchEntry byId returns nil for nonexistent id")
    func fetchEntryByIdNotFound() async throws {
        let sut = makeSUT()

        let found = try await sut.fetchEntry(byId: UUID().uuidString)

        #expect(found == nil)
    }

    @Test("fetchEntry byId returns nil for invalid UUID string")
    func fetchEntryByInvalidId() async throws {
        let sut = makeSUT()

        let found = try await sut.fetchEntry(byId: "not-a-uuid")

        #expect(found == nil)
    }

    // MARK: - CRUD: Update

    @Test("updateEntry modifies existing entry fields")
    func updateEntryModifiesFields() async throws {
        let sut = makeSUT()
        let id = UUID()
        let entry = EntryTestFixtures.makeEntry(id: id, accountId: "acct-1", timestamp: "2026-03-01T08:00:00Z")
        try await sut.saveEntry(entry)

        // Create an updated entry with same id
        let updatedEntry = Entry(
            id: id,
            entryTimestamp: "2026-03-02T10:00:00Z",
            accountId: "acct-1",
            operationType: "update",
            serverTimestamp: "2026-03-02T10:00:01Z",
            isSynced: true
        )
        try await sut.updateEntry(updatedEntry)

        let fetched = try await sut.fetchEntry(byId: id.uuidString)
        #expect(fetched?.entryTimestamp == "2026-03-02T10:00:00Z")
        #expect(fetched?.operationType == "update")
        #expect(fetched?.serverTimestamp == "2026-03-02T10:00:01Z")
        #expect(fetched?.isSynced == true)
    }

    @Test("updateEntrySyncStatus updates sync fields only")
    func updateEntrySyncStatus() async throws {
        let sut = makeSUT()
        let id = UUID()
        let entry = EntryTestFixtures.makeEntry(id: id, isSynced: false)
        try await sut.saveEntry(entry)

        try await sut.updateEntrySyncStatus(
            entryId: id.uuidString,
            isSynced: true,
            isFailedToSync: false,
            attempts: 3
        )

        let fetched = try await sut.fetchEntry(byId: id.uuidString)
        #expect(fetched?.isSynced == true)
        #expect(fetched?.isFailedToSync == false)
        #expect(fetched?.attempts == 3)
    }

    @Test("updateEntrySyncStatus with invalid UUID does nothing")
    func updateEntrySyncStatusInvalidId() async throws {
        let sut = makeSUT()

        // Should not throw
        try await sut.updateEntrySyncStatus(
            entryId: "bad-uuid",
            isSynced: true,
            isFailedToSync: false,
            attempts: 1
        )
    }

    // MARK: - CRUD: Delete

    @Test("deleteEntry removes entry by id")
    func deleteEntryById() async throws {
        let sut = makeSUT()
        let id = UUID()
        let entry = EntryTestFixtures.makeEntry(id: id)
        try await sut.saveEntry(entry)

        try await sut.deleteEntry(byId: id.uuidString)

        let all = try await sut.fetchAllEntries()
        #expect(all.isEmpty)
    }

    @Test("deleteEntry with nonexistent id does not throw")
    func deleteEntryNonexistent() async throws {
        let sut = makeSUT()

        try await sut.deleteEntry(byId: UUID().uuidString)
        // No throw means success
    }

    @Test("deleteAllEntries removes all entries")
    func deleteAllEntries() async throws {
        let sut = makeSUT()
        for i in 0..<5 {
            let entry = EntryTestFixtures.makeEntry(
                id: UUID(),
                timestamp: "2026-03-0\(i + 1)T08:00:00Z"
            )
            try await sut.saveEntry(entry)
        }

        let beforeDelete = try await sut.fetchAllEntries()
        #expect(beforeDelete.count == 5)

        try await sut.deleteAllEntries()

        let afterDelete = try await sut.fetchAllEntries()
        #expect(afterDelete.isEmpty)
    }

    // MARK: - Query: By User

    @Test("fetchEntries forUserId filters by accountId")
    func fetchEntriesForUser() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T08:00:00Z"))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-B", timestamp: "2026-03-01T09:00:00Z"))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T10:00:00Z"))

        let userAEntries = try await sut.fetchEntries(forUserId: "user-A")
        let userBEntries = try await sut.fetchEntries(forUserId: "user-B")

        #expect(userAEntries.count == 2)
        #expect(userBEntries.count == 1)
    }

    @Test("fetchEntries forUserId with operationType filters by both")
    func fetchEntriesForUserWithOperationType() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(
            EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T08:00:00Z", operationType: .create)
        )
        try await sut.saveEntry(
            EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T09:00:00Z", operationType: .delete)
        )
        try await sut.saveEntry(
            EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T10:00:00Z", operationType: .create)
        )

        let creates = try await sut.fetchEntries(forUserId: "user-A", operationType: OperationType.create.rawValue)
        let deletes = try await sut.fetchEntries(forUserId: "user-A", operationType: OperationType.delete.rawValue)

        #expect(creates.count == 2)
        #expect(deletes.count == 1)
    }

    // MARK: - Query: By Timestamp

    @Test("fetchEntriesOfTimestamp returns entries matching exact timestamp")
    func fetchEntriesOfTimestamp() async throws {
        let sut = makeSUT()
        let ts = "2026-03-01T08:00:00Z"
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: ts))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-02T08:00:00Z"))

        let results = try await sut.fetchEntriesOfTimestamp(forUserId: "user-A", timestamp: ts)

        #expect(results.count == 1)
        #expect(results.first?.entryTimestamp == ts)
    }

    @Test("checkEntryTimestampExists returns true for existing timestamp")
    func checkTimestampExists() async throws {
        let sut = makeSUT()
        let ts = "2026-03-01T08:00:00Z"
        try await sut.saveEntry(EntryTestFixtures.makeEntry(accountId: "user-A", timestamp: ts))

        let exists = try await sut.checkEntryTimestampExists(forUserId: "user-A", entryTimestamp: ts)
        let notExists = try await sut.checkEntryTimestampExists(forUserId: "user-A", entryTimestamp: "2099-01-01T00:00:00Z")

        #expect(exists == true)
        #expect(notExists == false)
    }

    // MARK: - Query: By Month

    @Test("fetchEntries forMonth returns entries within the month range")
    func fetchEntriesForMonth() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T08:00:00Z"))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-15T12:00:00Z"))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-04-01T08:00:00Z"))

        let marchEntries = try await sut.fetchEntries(forMonth: "2026-03", userId: "user-A")
        let aprilEntries = try await sut.fetchEntries(forMonth: "2026-04", userId: "user-A")

        #expect(marchEntries.count == 2)
        #expect(aprilEntries.count == 1)
    }

    @Test("fetchEntries forMonth with invalid month string returns empty")
    func fetchEntriesForInvalidMonth() async throws {
        let sut = makeSUT()

        let results = try await sut.fetchEntries(forMonth: "not-a-month", userId: "user-A")

        #expect(results.isEmpty)
    }

    // MARK: - Query: By Day

    @Test("fetchEntries forDay returns entries within the day range")
    func fetchEntriesForDay() async throws {
        let sut = makeSUT()

        // Generate timestamps using the same logic as the production code
        // to avoid local-timezone vs UTC mismatches.
        let dayFormatter = DateFormatter()
        dayFormatter.dateFormat = "yyyy-MM-dd"
        let day1Start = try #require(dayFormatter.date(from: "2026-03-01"))
        let day2Start = try #require(Calendar.current.date(byAdding: .day, value: 1, to: day1Start))
        let iso = ISO8601DateFormatter()

        let earlyInDay1 = iso.string(from: day1Start.addingTimeInterval(3600))       // 1h after day1 start
        let lateInDay1  = iso.string(from: day2Start.addingTimeInterval(-1))          // 1s before day1 end
        let earlyInDay2 = iso.string(from: day2Start.addingTimeInterval(1))           // 1s after day2 start

        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: earlyInDay1))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: lateInDay1))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: earlyInDay2))

        let day1 = try await sut.fetchEntries(forDay: "2026-03-01", userId: "user-A")
        let day2 = try await sut.fetchEntries(forDay: "2026-03-02", userId: "user-A")

        #expect(day1.count == 2)
        #expect(day2.count == 1)
    }

    @Test("fetchEntries forDay with invalid day string returns empty")
    func fetchEntriesForInvalidDay() async throws {
        let sut = makeSUT()

        let results = try await sut.fetchEntries(forDay: "not-a-day", userId: "user-A")

        #expect(results.isEmpty)
    }

    // MARK: - Query: Unsynced

    @Test("fetchUnsyncedEntries returns only unsynced entries for user")
    func fetchUnsyncedEntries() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T08:00:00Z", isSynced: false))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-02T08:00:00Z", isSynced: true))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-03T08:00:00Z", isSynced: false))

        let unsynced = try await sut.fetchUnsyncedEntries(forUserId: "user-A")

        #expect(unsynced.count == 2)
        #expect(unsynced.allSatisfy { !$0.isSynced })
    }

    // MARK: - Query: Latest & Oldest

    @Test("fetchLatestEntry returns entry with most recent timestamp")
    func fetchLatestEntry() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-01-01T08:00:00Z"))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-06-15T12:00:00Z"))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T08:00:00Z"))

        let latest = try await sut.fetchLatestEntry(forUserId: "user-A")

        #expect(latest?.entryTimestamp == "2026-06-15T12:00:00Z")
    }

    @Test("fetchLatestEntry returns nil when no entries exist")
    func fetchLatestEntryEmpty() async throws {
        let sut = makeSUT()

        let latest = try await sut.fetchLatestEntry(forUserId: "user-A")

        #expect(latest == nil)
    }

    @Test("fetchOldestEntry returns entry with earliest timestamp")
    func fetchOldestEntry() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-06-15T12:00:00Z"))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-01-01T08:00:00Z"))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T08:00:00Z"))

        let oldest = try await sut.fetchOldestEntry(forUserId: "user-A")

        #expect(oldest?.entryTimestamp == "2026-01-01T08:00:00Z")
    }

    @Test("fetchOldestEntry returns nil when no entries exist")
    func fetchOldestEntryEmpty() async throws {
        let sut = makeSUT()

        let oldest = try await sut.fetchOldestEntry(forUserId: "user-A")

        #expect(oldest == nil)
    }

    // MARK: - Query: Count

    @Test("fetchEntryCount returns correct count for user")
    func fetchEntryCount() async throws {
        let sut = makeSUT()
        for i in 0..<3 {
            try await sut.saveEntry(EntryTestFixtures.makeEntry(
                id: UUID(),
                accountId: "user-A",
                timestamp: "2026-03-0\(i + 1)T08:00:00Z"
            ))
        }
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-B", timestamp: "2026-03-01T09:00:00Z"))

        let countA = try await sut.fetchEntryCount(forUserId: "user-A")
        let countB = try await sut.fetchEntryCount(forUserId: "user-B")

        #expect(countA == 3)
        #expect(countB == 1)
    }

    @Test("fetchEntryCount returns 0 for user with no entries")
    func fetchEntryCountEmpty() async throws {
        let sut = makeSUT()

        let count = try await sut.fetchEntryCount(forUserId: "user-A")

        #expect(count == 0)
    }

    // MARK: - Query: Last N Days

    @Test("fetchEntries lastNDays returns entries within the timeframe")
    func fetchEntriesLastNDays() async throws {
        let sut = makeSUT()
        let now = Date()
        let isoFormatter = ISO8601DateFormatter()

        // Entry from today
        let todayTimestamp = isoFormatter.string(from: now)
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: todayTimestamp))

        // Entry from 3 days ago
        let threeDaysAgo = try #require(Calendar.current.date(byAdding: .day, value: -3, to: now))
        let threeDaysAgoTs = isoFormatter.string(from: threeDaysAgo)
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: threeDaysAgoTs))

        // Entry from 10 days ago
        let tenDaysAgo = try #require(Calendar.current.date(byAdding: .day, value: -10, to: now))
        let tenDaysAgoTs = isoFormatter.string(from: tenDaysAgo)
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: tenDaysAgoTs))

        let last7 = try await sut.fetchEntries(lastNDays: 7, userId: "user-A")
        let last1 = try await sut.fetchEntries(lastNDays: 1, userId: "user-A")

        #expect(last7.count == 2)
        #expect(last1.count == 1)
    }

    // MARK: - DTO Conversion

    @Test("fetchEntriesAsDTO returns DTOs with all relationship data")
    func fetchEntriesAsDTO() async throws {
        let sut = makeSUT()
        let entry = EntryTestFixtures.makeEntry(
            accountId: "user-A",
            weight: 1800,
            bodyFat: 250,
            bmr: 1600,
            metabolicAge: 35
        )
        try await sut.saveEntry(entry)

        let dtos = try await sut.fetchEntriesAsDTO(forUserId: "user-A")

        #expect(dtos.count == 1)
        #expect(dtos.first?.weight == 1800)
        #expect(dtos.first?.bodyFat == 250)
        #expect(dtos.first?.bmr == 1600)
        #expect(dtos.first?.metabolicAge == 35)
        #expect(dtos.first?.accountId == "user-A")
    }

    @Test("fetchEntriesAsDTO with operationType filter works")
    func fetchEntriesAsDTOWithFilter() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(
            EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T08:00:00Z", operationType: .create)
        )
        try await sut.saveEntry(
            EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-02T08:00:00Z", operationType: .delete)
        )

        let creates = try await sut.fetchEntriesAsDTO(forUserId: "user-A", operationType: OperationType.create.rawValue)

        #expect(creates.count == 1)
        #expect(creates.first?.operationType == OperationType.create.rawValue)
    }

    @Test("fetchEntryAsDTO byId returns single DTO")
    func fetchEntryAsDTOById() async throws {
        let sut = makeSUT()
        let id = UUID()
        let entry = EntryTestFixtures.makeEntry(id: id, weight: 2000)
        try await sut.saveEntry(entry)

        let dto = try await sut.fetchEntryAsDTO(byId: id.uuidString)

        #expect(dto != nil)
        #expect(dto?.weight == 2000)
    }

    @Test("fetchEntryAsDTO byId returns nil for invalid UUID")
    func fetchEntryAsDTOInvalidId() async throws {
        let sut = makeSUT()

        let dto = try await sut.fetchEntryAsDTO(byId: "invalid")

        #expect(dto == nil)
    }

    @Test("fetchLatestEntryAsDTO returns most recent entry as DTO")
    func fetchLatestEntryAsDTO() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-01-01T08:00:00Z", weight: 1500))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-06-01T08:00:00Z", weight: 1800))

        let latest = try await sut.fetchLatestEntryAsDTO(forUserId: "user-A")

        #expect(latest?.weight == 1800)
        #expect(latest?.entryTimestamp == "2026-06-01T08:00:00Z")
    }

    @Test("fetchLatestEntryAsDTO with operationType filter returns correct entry")
    func fetchLatestEntryAsDTOWithFilter() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(
            EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-06-01T08:00:00Z", operationType: .delete)
        )
        try await sut.saveEntry(
            EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T08:00:00Z", weight: 1700, operationType: .create)
        )

        let latest = try await sut.fetchLatestEntryAsDTO(forUserId: "user-A", operationType: OperationType.create.rawValue)

        #expect(latest?.operationType == OperationType.create.rawValue)
        #expect(latest?.weight == 1700)
    }

    // MARK: - Identifiers

    @Test("fetchEntryIdentifiers returns persistent identifiers")
    func fetchEntryIdentifiers() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T08:00:00Z"))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-02T08:00:00Z"))

        let ids = try await sut.fetchEntryIdentifiers(forUserId: "user-A")

        #expect(ids.count == 2)
    }

    @Test("fetchEntryIdentifiers with operationType filter works")
    func fetchEntryIdentifiersWithFilter() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(
            EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T08:00:00Z", operationType: .create)
        )
        try await sut.saveEntry(
            EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-02T08:00:00Z", operationType: .delete)
        )

        let createIds = try await sut.fetchEntryIdentifiers(forUserId: "user-A", operationType: OperationType.create.rawValue)

        #expect(createIds.count == 1)
    }

    // MARK: - Static Helpers

    @Test("extractDTO from entry returns correct DTO")
    func extractDTOFromEntry() async throws {
        let entry = EntryTestFixtures.makeEntry(weight: 1800, bodyFat: 250)
        let dto = EntryRepository.extractDTO(from: entry)

        #expect(dto != nil)
        #expect(dto?.weight == 1800)
        #expect(dto?.bodyFat == 250)
    }

    @Test("extractDTO from nil returns nil")
    func extractDTOFromNil() async throws {
        let dto = EntryRepository.extractDTO(from: nil)

        #expect(dto == nil)
    }

    @Test("extractWeight from entry returns weight value")
    func extractWeightFromEntry() async throws {
        let entry = EntryTestFixtures.makeEntry(weight: 1800)
        let weight = EntryRepository.extractWeight(from: entry)

        #expect(weight == 1800)
    }

    @Test("extractWeight from nil returns 0")
    func extractWeightFromNil() async throws {
        let weight = EntryRepository.extractWeight(from: nil)

        #expect(weight == 0)
    }

    @Test("extractWeight from entry without scaleEntry returns 0")
    func extractWeightFromNoScaleEntry() async throws {
        let entry = Entry(
            entryTimestamp: "2026-03-01T08:00:00Z",
            accountId: "acct-1",
            operationType: "create"
        )
        let weight = EntryRepository.extractWeight(from: entry)

        #expect(weight == 0)
    }

}

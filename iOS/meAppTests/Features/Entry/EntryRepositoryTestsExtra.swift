import Foundation
@testable import meApp
import SwiftData
import Testing

extension EntryRepositoryTests {

    // MARK: - Sync

    @Test("syncEntries batch inserts multiple entries")
    func syncEntriesBatchInsert() async throws {
        let sut = makeSUT()
        let entries = (0..<3).map { i in
            EntryTestFixtures.makeEntry(
                id: UUID(),
                accountId: "user-A",
                timestamp: "2026-03-0\(i + 1)T08:00:00Z",
                weight: 1800 + i * 100
            )
        }

        try await sut.syncEntries(newEntries: entries)

        let all = try await sut.fetchAllEntries()
        #expect(all.count == 3)
    }

    @Test("syncEntries preserves relationships for all entries")
    func syncEntriesPreservesRelationships() async throws {
        let sut = makeSUT()
        let entry = EntryTestFixtures.makeEntry(
            accountId: "user-A",
            weight: 2000,
            bmr: 1500
        )

        try await sut.syncEntries(newEntries: [entry])

        let all = try await sut.fetchAllEntries()
        #expect(all.first?.scaleEntry?.weight == 2000)
        #expect(all.first?.scaleEntryMetric?.bmr == 1500)
    }

    // MARK: - Edge Cases

    @Test("multiple entries for different users are isolated")
    func multipleUsersIsolation() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-A", timestamp: "2026-03-01T08:00:00Z"))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-B", timestamp: "2026-03-01T09:00:00Z"))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-C", timestamp: "2026-03-01T10:00:00Z"))

        let countA = try await sut.fetchEntryCount(forUserId: "user-A")
        let countB = try await sut.fetchEntryCount(forUserId: "user-B")
        let countC = try await sut.fetchEntryCount(forUserId: "user-C")
        let total = try await sut.fetchAllEntries().count

        #expect(countA == 1)
        #expect(countB == 1)
        #expect(countC == 1)
        #expect(total == 3)
    }

    @Test("deleting one user's entry does not affect other users")
    func deleteDoesNotAffectOtherUsers() async throws {
        let sut = makeSUT()
        let idA = UUID()
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: idA, accountId: "user-A", timestamp: "2026-03-01T08:00:00Z"))
        try await sut.saveEntry(EntryTestFixtures.makeEntry(id: UUID(), accountId: "user-B", timestamp: "2026-03-01T09:00:00Z"))

        try await sut.deleteEntry(byId: idA.uuidString)

        let all = try await sut.fetchAllEntries()
        #expect(all.count == 1)
        #expect(all.first?.accountId == "user-B")
    }

    @Test("fetchEntries forMonth for different user returns empty")
    func fetchEntriesForMonthWrongUser() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(EntryTestFixtures.makeEntry(accountId: "user-A", timestamp: "2026-03-01T08:00:00Z"))

        let results = try await sut.fetchEntries(forMonth: "2026-03", userId: "user-B")

        #expect(results.isEmpty)
    }

    @Test("fetchUnsyncedEntries for different user returns empty")
    func fetchUnsyncedEntriesWrongUser() async throws {
        let sut = makeSUT()
        try await sut.saveEntry(EntryTestFixtures.makeEntry(accountId: "user-A", isSynced: false))

        let results = try await sut.fetchUnsyncedEntries(forUserId: "user-B")

        #expect(results.isEmpty)
    }
}

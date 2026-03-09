//
//  LoggerRepositoryTests.swift
//  meAppTests
//

import Testing
import SwiftData
@testable import meApp

@Suite(.serialized)
@MainActor
struct LoggerRepositoryTests {

    private func makeContainer() throws -> ModelContainer {
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        return try ModelContainer(for: LogEntry.self, configurations: config)
    }

    private func makeSUT() throws -> LoggerRepository {
        LoggerRepository(container: try makeContainer())
    }

    private func makeEntry(
        id: String = UUID().uuidString,
        accountId: String? = nil,
        sessionId: String = "session-1",
        timestamp: Int64 = DateTimeTools.getCurrentTimestampMillis()
    ) -> LogEntry {
        LogEntry(
            id: id,
            accountId: accountId,
            sessionId: sessionId,
            tag: "TestTag",
            tagId: "testMethod",
            type: .info,
            message: "test message",
            timestamp: timestamp
        )
    }

    // MARK: - saveLogEntry

    @Test("saveLogEntry success: persists entry readable via fetchAllLogs")
    func saveLogEntrySuccess() async throws {
        let sut = try makeSUT()
        let entry = makeEntry(id: "e1", sessionId: "s1")

        await sut.saveLogEntry(entry)

        let logs = try await sut.fetchAllLogs()
        #expect(logs.count == 1)
        #expect(logs.first?.id == "e1")
        #expect(logs.first?.sessionId == "s1")
    }

    // MARK: - fetchAllLogs

    @Test("fetchAllLogs success: returns empty when none")
    func fetchAllLogsReturnsEmptyWhenNone() async throws {
        let sut = try makeSUT()

        let logs = try await sut.fetchAllLogs()

        #expect(logs.isEmpty)
    }

    @Test("fetchAllLogs success: returns all inserted entries")
    func fetchAllLogsReturnsAllInserted() async throws {
        let sut = try makeSUT()
        await sut.saveLogEntry(makeEntry(id: "e1"))
        await sut.saveLogEntry(makeEntry(id: "e2"))
        await sut.saveLogEntry(makeEntry(id: "e3"))

        let logs = try await sut.fetchAllLogs()

        #expect(logs.count == 3)
    }

    // MARK: - fetchLogs(forSession:)

    @Test("fetchLogs(forSession:) success: returns only matching session logs")
    func fetchLogsForSessionReturnsMatchingLogs() async throws {
        let sut = try makeSUT()
        await sut.saveLogEntry(makeEntry(id: "e1", sessionId: "session-A"))
        await sut.saveLogEntry(makeEntry(id: "e2", sessionId: "session-A"))
        await sut.saveLogEntry(makeEntry(id: "e3", sessionId: "session-B"))

        let logs = try await sut.fetchLogs(forSession: "session-A")

        #expect(logs.count == 2)
        #expect(logs.allSatisfy { $0.sessionId == "session-A" })
    }

    @Test("fetchLogs(forSession:) success: returns empty when no match")
    func fetchLogsForSessionReturnsEmptyWhenNoMatch() async throws {
        let sut = try makeSUT()
        await sut.saveLogEntry(makeEntry(id: "e1", sessionId: "session-A"))

        let logs = try await sut.fetchLogs(forSession: "session-X")

        #expect(logs.isEmpty)
    }

    // MARK: - fetchLogs(forAccount:)

    @Test("fetchLogs(forAccount:) success: returns only matching account logs")
    func fetchLogsForAccountReturnsMatchingLogs() async throws {
        let sut = try makeSUT()
        await sut.saveLogEntry(makeEntry(id: "e1", accountId: "acct-1", sessionId: "s1"))
        await sut.saveLogEntry(makeEntry(id: "e2", accountId: "acct-1", sessionId: "s2"))
        await sut.saveLogEntry(makeEntry(id: "e3", accountId: "acct-2", sessionId: "s3"))

        let logs = try await sut.fetchLogs(forAccount: "acct-1")

        #expect(logs.count == 2)
        #expect(logs.allSatisfy { $0.accountId == "acct-1" })
    }

    @Test("fetchLogs(forAccount:) success: returns empty when no match")
    func fetchLogsForAccountReturnsEmptyWhenNoMatch() async throws {
        let sut = try makeSUT()
        await sut.saveLogEntry(makeEntry(id: "e1", accountId: "acct-1", sessionId: "s1"))

        let logs = try await sut.fetchLogs(forAccount: "acct-X")

        #expect(logs.isEmpty)
    }

    // MARK: - fetchLogs(from:to:)

    @Test("fetchLogs(from:to:) success: returns logs within range")
    func fetchLogsFromToReturnsLogsWithinRange() async throws {
        let sut = try makeSUT()
        let now = Date()
        let start = now.addingTimeInterval(-3600)  // 1 hour ago
        let end   = now.addingTimeInterval(-1800)  // 30 min ago
        let insideTs  = Int64(now.addingTimeInterval(-2700).timeIntervalSince1970 * 1000)  // 45 min ago
        let outsideTs = Int64(now.addingTimeInterval(-7200).timeIntervalSince1970 * 1000)  // 2 hours ago

        await sut.saveLogEntry(makeEntry(id: "inside",  sessionId: "s1", timestamp: insideTs))
        await sut.saveLogEntry(makeEntry(id: "outside", sessionId: "s2", timestamp: outsideTs))

        let logs = try await sut.fetchLogs(from: start, to: end)

        #expect(logs.count == 1)
        #expect(logs.first?.id == "inside")
    }

    @Test("fetchLogs(from:to:) success: returns empty when none in range")
    func fetchLogsFromToReturnsEmptyWhenNoneInRange() async throws {
        let sut = try makeSUT()
        let now = Date()
        let start = now.addingTimeInterval(-3600)
        let end   = now.addingTimeInterval(-1800)
        let outsideTs = Int64(now.addingTimeInterval(-7200).timeIntervalSince1970 * 1000)

        await sut.saveLogEntry(makeEntry(id: "e1", sessionId: "s1", timestamp: outsideTs))

        let logs = try await sut.fetchLogs(from: start, to: end)

        #expect(logs.isEmpty)
    }

    @Test("fetchLogs(from:to:) success: excludes logs outside boundary")
    func fetchLogsFromToExcludesLogsOutsideBoundary() async throws {
        let sut = try makeSUT()
        let now = Date()
        let start = now.addingTimeInterval(-3600)
        let end   = now.addingTimeInterval(-1800)
        let startMs = Int64(start.timeIntervalSince1970 * 1000)
        let endMs   = Int64(end.timeIntervalSince1970 * 1000)

        // 1ms before start and 1ms after end — both must be excluded
        await sut.saveLogEntry(makeEntry(id: "before-start", sessionId: "s1", timestamp: startMs - 1))
        await sut.saveLogEntry(makeEntry(id: "after-end",    sessionId: "s2", timestamp: endMs   + 1))

        let logs = try await sut.fetchLogs(from: start, to: end)

        #expect(logs.isEmpty)
    }

    // MARK: - deleteLogs(forAccount:)

    @Test("deleteLogs(forAccount:) success: deletes only matching account logs, leaves others")
    func deleteLogsForAccountDeletesOnlyMatchingLogs() async throws {
        let sut = try makeSUT()
        await sut.saveLogEntry(makeEntry(id: "e1", accountId: "acct-1", sessionId: "s1"))
        await sut.saveLogEntry(makeEntry(id: "e2", accountId: "acct-1", sessionId: "s2"))
        await sut.saveLogEntry(makeEntry(id: "e3", accountId: "acct-2", sessionId: "s3"))

        try await sut.deleteLogs(forAccount: "acct-1")

        let remaining = try await sut.fetchAllLogs()
        #expect(remaining.count == 1)
        #expect(remaining.first?.id == "e3")
    }

    @Test("deleteLogs(forAccount:) success: no-op when account has no logs")
    func deleteLogsForAccountNoOpWhenNone() async throws {
        let sut = try makeSUT()
        await sut.saveLogEntry(makeEntry(id: "e1", accountId: "acct-2", sessionId: "s1"))

        try await sut.deleteLogs(forAccount: "acct-X")

        let remaining = try await sut.fetchAllLogs()
        #expect(remaining.count == 1)
    }

    // MARK: - deleteAllLogs

    @Test("deleteAllLogs success: deletes all logs")
    func deleteAllLogsDeletesAll() async throws {
        let sut = try makeSUT()
        await sut.saveLogEntry(makeEntry(id: "e1", sessionId: "s1"))
        await sut.saveLogEntry(makeEntry(id: "e2", sessionId: "s2"))

        try await sut.deleteAllLogs()

        let logs = try await sut.fetchAllLogs()
        #expect(logs.isEmpty)
    }

    @Test("deleteAllLogs success: no-op when empty")
    func deleteAllLogsNoOpWhenEmpty() async throws {
        let sut = try makeSUT()

        try await sut.deleteAllLogs()

        let logs = try await sut.fetchAllLogs()
        #expect(logs.isEmpty)
    }

    // MARK: - deleteLogsOlderThan

    @Test("deleteLogsOlderThan success: deletes old logs, keeps recent")
    func deleteLogsOlderThanDeletesOldKeepsRecent() async throws {
        let sut = try makeSUT()
        let days = 7
        let cutoff = DateTimeTools.getTimestampDaysAgo(days)
        let oldTs    = cutoff - 1
        let recentTs = DateTimeTools.getCurrentTimestampMillis()

        await sut.saveLogEntry(makeEntry(id: "old",    sessionId: "s1", timestamp: oldTs))
        await sut.saveLogEntry(makeEntry(id: "recent", sessionId: "s2", timestamp: recentTs))

        try await sut.deleteLogsOlderThan(olderThanDays: days)

        let remaining = try await sut.fetchAllLogs()
        #expect(remaining.count == 1)
        #expect(remaining.first?.id == "recent")
    }

    @Test("deleteLogsOlderThan success: no-op when all logs are recent")
    func deleteLogsOlderThanNoOpWhenAllRecent() async throws {
        let sut = try makeSUT()
        let recentTs = DateTimeTools.getCurrentTimestampMillis()
        await sut.saveLogEntry(makeEntry(id: "e1", sessionId: "s1", timestamp: recentTs))

        try await sut.deleteLogsOlderThan(olderThanDays: 7)

        let remaining = try await sut.fetchAllLogs()
        #expect(remaining.count == 1)
    }

    // MARK: - deleteLogsOlderThanInBatches

    @Test("deleteLogsOlderThanInBatches success: deletes all old logs")
    func deleteLogsOlderThanInBatchesDeletesAllOldLogs() async throws {
        let sut = try makeSUT()
        let days = 7
        let cutoff = DateTimeTools.getTimestampDaysAgo(days)
        let oldTs = cutoff - 1

        for i in 1...5 {
            await sut.saveLogEntry(makeEntry(id: "old-\(i)", sessionId: "s\(i)", timestamp: oldTs - Int64(i)))
        }

        try await sut.deleteLogsOlderThanInBatches(olderThanDays: days, batchSize: 500, interBatchDelayNs: 0)

        let remaining = try await sut.fetchAllLogs()
        #expect(remaining.isEmpty)
    }

    @Test("deleteLogsOlderThanInBatches success: respects batchSize per iteration")
    func deleteLogsOlderThanInBatchesRespectsBatchSize() async throws {
        let sut = try makeSUT()
        let days = 7
        let cutoff = DateTimeTools.getTimestampDaysAgo(days)
        let oldTs    = cutoff - 1
        let recentTs = DateTimeTools.getCurrentTimestampMillis()

        for i in 1...5 {
            await sut.saveLogEntry(makeEntry(id: "old-\(i)", sessionId: "s\(i)", timestamp: oldTs - Int64(i)))
        }
        await sut.saveLogEntry(makeEntry(id: "recent", sessionId: "s-recent", timestamp: recentTs))

        try await sut.deleteLogsOlderThanInBatches(olderThanDays: days, batchSize: 2, interBatchDelayNs: 0)

        let remaining = try await sut.fetchAllLogs()
        #expect(remaining.count == 1)
        #expect(remaining.first?.id == "recent")
    }

    @Test("deleteLogsOlderThanInBatches success: no-op when no old logs")
    func deleteLogsOlderThanInBatchesNoOpWhenNoOldLogs() async throws {
        let sut = try makeSUT()
        let recentTs = DateTimeTools.getCurrentTimestampMillis()
        await sut.saveLogEntry(makeEntry(id: "e1", sessionId: "s1", timestamp: recentTs))

        try await sut.deleteLogsOlderThanInBatches(olderThanDays: 7, batchSize: 500, interBatchDelayNs: 0)

        let remaining = try await sut.fetchAllLogs()
        #expect(remaining.count == 1)
    }

    // MARK: - hasLogsOlderThan

    @Test("hasLogsOlderThan success: returns true when old log exists")
    func hasLogsOlderThanReturnsTrueWhenOldLogExists() async throws {
        let sut = try makeSUT()
        let days = 7
        let cutoff = DateTimeTools.getTimestampDaysAgo(days)
        let oldTs = cutoff - 1

        await sut.saveLogEntry(makeEntry(id: "old", sessionId: "s1", timestamp: oldTs))

        let result = try await sut.hasLogsOlderThan(olderThanDays: days)

        #expect(result == true)
    }

    @Test("hasLogsOlderThan success: returns false when all logs are recent")
    func hasLogsOlderThanReturnsFalseWhenAllRecent() async throws {
        let sut = try makeSUT()
        let recentTs = DateTimeTools.getCurrentTimestampMillis()
        await sut.saveLogEntry(makeEntry(id: "e1", sessionId: "s1", timestamp: recentTs))

        let result = try await sut.hasLogsOlderThan(olderThanDays: 7)

        #expect(result == false)
    }

    @Test("hasLogsOlderThan success: returns false when empty")
    func hasLogsOlderThanReturnsFalseWhenEmpty() async throws {
        let sut = try makeSUT()

        let result = try await sut.hasLogsOlderThan(olderThanDays: 7)

        #expect(result == false)
    }
}

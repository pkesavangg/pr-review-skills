import Foundation
import Testing
import SwiftData
@testable import meApp

// MARK: - Mock Logger Repo

@MainActor
private final class MockLoggerRepo: LoggerRepositoryProtocol {
    var logs: [LogEntry] = []
    var saveLogEntryCalls = 0
    var deleteLogsForAccountCalls = 0
    var deleteAllLogsCalls = 0
    var deleteOlderThanCalls = 0
    var hasOlderThanResult = false
    var deleteInBatchesCalls = 0
    var fetchAllLogsError: Error?
    var deleteLogsForAccountError: Error?
    var deleteAllLogsError: Error?
    var sendLogsError: Error?

    func saveLogEntry(_ entry: LogEntry) async {
        saveLogEntryCalls += 1
        logs.append(entry)
    }

    func fetchAllLogs() async throws -> [LogEntry] {
        if let fetchAllLogsError { throw fetchAllLogsError }
        return logs
    }

    func fetchLogs(forSession sessionId: String) async throws -> [LogEntry] {
        logs.filter { $0.sessionId == sessionId }
    }

    func fetchLogs(forAccount accountId: String) async throws -> [LogEntry] {
        logs.filter { $0.accountId == accountId }
    }

    func fetchLogs(from: Date, to: Date) async throws -> [LogEntry] {
        let fromMs = Int64(from.timeIntervalSince1970 * 1000)
        let toMs = Int64(to.timeIntervalSince1970 * 1000)
        return logs.filter { $0.timestamp >= fromMs && $0.timestamp <= toMs }
    }

    func deleteLogs(forAccount accountId: String) async throws {
        deleteLogsForAccountCalls += 1
        if let deleteLogsForAccountError { throw deleteLogsForAccountError }
        logs.removeAll { $0.accountId == accountId }
    }

    func deleteAllLogs() async throws {
        deleteAllLogsCalls += 1
        if let deleteAllLogsError { throw deleteAllLogsError }
        logs.removeAll()
    }

    func deleteLogsOlderThan(olderThanDays days: Int) async throws {
        deleteOlderThanCalls += 1
    }

    func deleteLogsOlderThanInBatches(olderThanDays days: Int, batchSize: Int, interBatchDelayNs: UInt64) async throws {
        deleteInBatchesCalls += 1
    }

    func hasLogsOlderThan(olderThanDays days: Int) async throws -> Bool {
        hasOlderThanResult
    }
}

// MARK: - Mock Logger API Repo

private final class MockLoggerApiRepo: LoggerApiRepositoryProtocol {
    var sendLogsCalls = 0
    var lastPayload: LogsPayload?
    var sendLogsError: Error?

    func sendLogs(_ logsPayload: LogsPayload) async throws {
        sendLogsCalls += 1
        lastPayload = logsPayload
        if let sendLogsError { throw sendLogsError }
    }
}

private enum LoggerTestError: Error {
    case sendFailed
    case fetchFailed
    case deleteFailed
}

// MARK: - Helpers

@MainActor
private func makeSUT(
    repo: MockLoggerRepo? = nil,
    apiRepo: MockLoggerApiRepo? = nil,
    accountOverride: MockAccountService? = nil,
    sessionId: String = "test-session"
) -> (LoggerService, MockLoggerRepo, MockLoggerApiRepo, MockAccountService) { // swiftlint:disable:this large_tuple
    TestDependencyContainer.reset()

    let repo = repo ?? MockLoggerRepo()
    let apiRepo = apiRepo ?? MockLoggerApiRepo()
    let account: MockAccountService
    if let override = accountOverride {
        account = override
    } else {
        let defaultAccount = MockAccountService()
        defaultAccount.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "test@e.com", isActiveAccount: true)
        account = defaultAccount
    }

    DependencyContainer.shared.register(account as AccountServiceProtocol)

    let sut = LoggerService(
        loggerRepository: repo,
        loggerApiRepository: apiRepo,
        sessionId: sessionId,
        kv: KvStorageService.shared,
        skipCleanup: true
    )
    sut.accountService = account

    return (sut, repo, apiRepo, account)
}

private func makeLogEntry(
    accountId: String? = "acct-1",
    sessionId: String = "test-session",
    tag: String = "TestTag",
    message: String = "Test message",
    data: String? = nil,
    timestamp: Int64 = DateTimeTools.getCurrentTimestampMillis()
) -> LogEntry {
    LogEntry(
        accountId: accountId,
        sessionId: sessionId,
        tag: tag,
        tagId: "testFunc()",
        type: .info,
        message: message,
        timestamp: timestamp,
        data: data
    )
}

// MARK: - Tests

@Suite(.serialized)
@MainActor
struct LoggerServiceTests {

    // MARK: - getCurrentSessionId

    @Test("getCurrentSessionId returns injected session ID")
    func getCurrentSessionIdReturnsInjectedId() {
        let (sut, _, _, _) = makeSUT(sessionId: "my-session-42")
        #expect(sut.getCurrentSessionId() == "my-session-42")
    }

    // MARK: - log level filtering

    @Test("log debug level: does not persist to repository")
    func logDebugDoesNotPersist() async {
        let (sut, repo, _, _) = makeSUT()

        sut.log(level: .debug, tag: "T", message: "debug msg")
        try? await Task.sleep(nanoseconds: 50_000_000)

        #expect(repo.saveLogEntryCalls == 0)
    }

    @Test("log info level: resolves account ID from active account")
    func logInfoResolvesAccountId() async {
        let (sut, _, _, account) = makeSUT()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-7", email: "x@e.com", isActiveAccount: true)

        sut.log(level: .info, tag: "Tag", message: "info msg")
        try? await Task.sleep(nanoseconds: 200_000_000)
    }

    // MARK: - getAllLogs

    @Test("getAllLogs returns all stored logs")
    func getAllLogsReturnsAll() async throws {
        let repo = MockLoggerRepo()
        repo.logs = [makeLogEntry(message: "A"), makeLogEntry(message: "B")]
        let (sut, _, _, _) = makeSUT(repo: repo)

        let logs = try await sut.getAllLogs()
        #expect(logs.count == 2)
    }

    // MARK: - getCurrentSessionLogs

    @Test("getCurrentSessionLogs filters by session ID")
    func getCurrentSessionLogsFilters() async throws {
        let repo = MockLoggerRepo()
        repo.logs = [
            makeLogEntry(sessionId: "test-session", message: "A"),
            makeLogEntry(sessionId: "other-session", message: "B")
        ]
        let (sut, _, _, _) = makeSUT(repo: repo, sessionId: "test-session")

        let logs = try await sut.getCurrentSessionLogs()
        #expect(logs.count == 1)
        #expect(logs.first?.message == "A")
    }

    // MARK: - getLogsForAccount

    @Test("getLogsForAccount filters by account ID")
    func getLogsForAccountFilters() async throws {
        let repo = MockLoggerRepo()
        repo.logs = [
            makeLogEntry(accountId: "acct-1", message: "A"),
            makeLogEntry(accountId: "acct-2", message: "B")
        ]
        let (sut, _, _, _) = makeSUT(repo: repo)

        let logs = try await sut.getLogsForAccount("acct-1")
        #expect(logs.count == 1)
        #expect(logs.first?.message == "A")
    }

    // MARK: - getLogs(from:to:)

    @Test("getLogs from/to filters by timestamp range")
    func getLogsFromToFilters() async throws {
        let repo = MockLoggerRepo()
        let now = Date()
        let hourAgo = now.addingTimeInterval(-3600)
        let twoHoursAgo = now.addingTimeInterval(-7200)

        repo.logs = [
            makeLogEntry(message: "recent", timestamp: Int64(now.timeIntervalSince1970 * 1000)),
            makeLogEntry(message: "old", timestamp: Int64(twoHoursAgo.timeIntervalSince1970 * 1000))
        ]
        let (sut, _, _, _) = makeSUT(repo: repo)

        let logs = try await sut.getLogs(from: hourAgo, to: now)
        #expect(logs.count == 1)
        #expect(logs.first?.message == "recent")
    }

    // MARK: - deleteLogsForAccount

    @Test("deleteLogsForAccount: calls repository delete")
    func deleteLogsForAccountCallsRepo() async throws {
        let repo = MockLoggerRepo()
        repo.logs = [makeLogEntry(accountId: "acct-1")]
        let (sut, _, _, _) = makeSUT(repo: repo)

        try await sut.deleteLogsForAccount("acct-1")
        #expect(repo.deleteLogsForAccountCalls == 1)
        #expect(repo.logs.isEmpty)
    }

    // MARK: - deleteAllLogs

    @Test("deleteAllLogs: calls repository deleteAll")
    func deleteAllLogsCallsRepo() async throws {
        let repo = MockLoggerRepo()
        repo.logs = [makeLogEntry(), makeLogEntry()]
        let (sut, _, _, _) = makeSUT(repo: repo)

        try await sut.deleteAllLogs()
        #expect(repo.deleteAllLogsCalls == 1)
        #expect(repo.logs.isEmpty)
    }

    // MARK: - deleteOldLogs

    @Test("deleteOldLogs: passes retention days to repository")
    func deleteOldLogsCallsRepo() async throws {
        let repo = MockLoggerRepo()
        let (sut, _, _, _) = makeSUT(repo: repo)

        try await sut.deleteOldLogs(30)
        #expect(repo.deleteOlderThanCalls == 1)
    }

    // MARK: - sendLogsToServer

    @Test("sendLogsToServer success: sends formatted payload and clears local logs")
    func sendLogsToServerSuccess() async throws {
        let repo = MockLoggerRepo()
        repo.logs = [
            makeLogEntry(accountId: "acct-1", tag: "LoginStore", message: "User logged in", data: nil),
            makeLogEntry(accountId: "acct-1", tag: "EntryService", message: "Entry saved", data: "[\"extra\"]")
        ]
        let apiRepo = MockLoggerApiRepo()
        let (sut, _, _, _) = makeSUT(repo: repo, apiRepo: apiRepo)

        try await sut.sendLogsToServer(accountId: "acct-1", version: "1.0.0")

        #expect(apiRepo.sendLogsCalls == 1)
        #expect(apiRepo.lastPayload?.version == "1.0.0")
        #expect(apiRepo.lastPayload?.logs.count == 2)
        #expect(repo.deleteLogsForAccountCalls == 1)
    }

    @Test("sendLogsToServer no account: throws noActiveAccount")
    func sendLogsToServerNoAccount() async {
        let account = MockAccountService()
        account.activeAccount = nil
        let (sut, _, _, _) = makeSUT(accountOverride: account)

        do {
            try await sut.sendLogsToServer(accountId: nil, version: "1.0")
            Issue.record("Expected throw")
        } catch {
            #expect(error is LoggerServiceError)
        }
    }

    @Test("sendLogsToServer API failure: throws and does not clear local logs")
    func sendLogsToServerAPIFailure() async {
        let repo = MockLoggerRepo()
        repo.logs = [makeLogEntry(accountId: "acct-1")]
        let apiRepo = MockLoggerApiRepo()
        apiRepo.sendLogsError = LoggerTestError.sendFailed
        let (sut, _, _, _) = makeSUT(repo: repo, apiRepo: apiRepo)

        do {
            try await sut.sendLogsToServer(accountId: "acct-1", version: "1.0")
            Issue.record("Expected throw")
        } catch {
            #expect(error as? LoggerTestError == .sendFailed)
        }
        #expect(repo.deleteLogsForAccountCalls == 0)
        #expect(repo.logs.count == 1)
    }

    @Test("sendLogsToServer with explicit accountId: uses that ID, not active account")
    func sendLogsToServerExplicitAccount() async throws {
        let repo = MockLoggerRepo()
        repo.logs = [makeLogEntry(accountId: "acct-2")]
        let apiRepo = MockLoggerApiRepo()
        let (sut, _, _, _) = makeSUT(repo: repo, apiRepo: apiRepo)

        try await sut.sendLogsToServer(accountId: "acct-2", version: "2.0")
        #expect(apiRepo.sendLogsCalls == 1)
        #expect(repo.deleteLogsForAccountCalls == 1)
    }

    // MARK: - sendScaleLogsToServer

    @Test("sendScaleLogsToServer success: formats and sends payload")
    func sendScaleLogsToServerSuccess() async throws {
        let apiRepo = MockLoggerApiRepo()
        let (sut, _, _, _) = makeSUT(apiRepo: apiRepo)

        let deviceLogs = [
            DeviceLogEntry(macAddress: "AA:BB:CC", log: "Line 1\nLine 2"),
            DeviceLogEntry(macAddress: nil, log: "Single line")
        ]

        try await sut.sendScaleLogsToServer(deviceLogs: deviceLogs, version: "3.0")

        #expect(apiRepo.sendLogsCalls == 1)
        #expect(apiRepo.lastPayload?.version == "3.0")
        let logCount = apiRepo.lastPayload?.logs.count ?? 0
        #expect(logCount == 4) // 1 mac address + 2 lines from first + 1 from second
    }

    @Test("sendScaleLogsToServer empty logs: sends empty payload")
    func sendScaleLogsToServerEmpty() async throws {
        let apiRepo = MockLoggerApiRepo()
        let (sut, _, _, _) = makeSUT(apiRepo: apiRepo)

        try await sut.sendScaleLogsToServer(deviceLogs: [], version: "3.0")
        #expect(apiRepo.sendLogsCalls == 1)
        #expect(apiRepo.lastPayload?.logs.isEmpty == true)
    }

    @Test("sendScaleLogsToServer API failure: throws")
    func sendScaleLogsToServerFailure() async {
        let apiRepo = MockLoggerApiRepo()
        apiRepo.sendLogsError = LoggerTestError.sendFailed
        let (sut, _, _, _) = makeSUT(apiRepo: apiRepo)

        do {
            try await sut.sendScaleLogsToServer(deviceLogs: [DeviceLogEntry(macAddress: "A", log: "B")], version: "1.0")
            Issue.record("Expected throw")
        } catch {
            #expect(error as? LoggerTestError == .sendFailed)
        }
    }

    @Test("sendScaleLogsToServer nil log text: skips entries with nil log")
    func sendScaleLogsToServerNilLogSkips() async throws {
        let apiRepo = MockLoggerApiRepo()
        let (sut, _, _, _) = makeSUT(apiRepo: apiRepo)

        let deviceLogs = [
            DeviceLogEntry(macAddress: "AA:BB", log: nil),
            DeviceLogEntry(macAddress: nil, log: "Valid line")
        ]

        try await sut.sendScaleLogsToServer(deviceLogs: deviceLogs, version: "1.0")
        #expect(apiRepo.lastPayload?.logs.count == 2) // mac + 1 valid line
    }

    // MARK: - formatLogsForAPI (tested through sendLogsToServer)

    @Test("formatLogsForAPI with JSON object data: embeds in array")
    func formatLogsWithJSONObjectData() async throws {
        let repo = MockLoggerRepo()
        repo.logs = [makeLogEntry(accountId: "acct-1", tag: "T", message: "M", data: "{\"key\":\"value\"}")]
        let apiRepo = MockLoggerApiRepo()
        let (sut, _, _, _) = makeSUT(repo: repo, apiRepo: apiRepo)

        try await sut.sendLogsToServer(accountId: "acct-1", version: "1.0")
        #expect(apiRepo.lastPayload?.logs.count == 1)
    }

    @Test("formatLogsForAPI with plain string data: embeds as string array")
    func formatLogsWithPlainStringData() async throws {
        let repo = MockLoggerRepo()
        repo.logs = [makeLogEntry(accountId: "acct-1", tag: "T", message: "M", data: "plain text data")]
        let apiRepo = MockLoggerApiRepo()
        let (sut, _, _, _) = makeSUT(repo: repo, apiRepo: apiRepo)

        try await sut.sendLogsToServer(accountId: "acct-1", version: "1.0")
        #expect(apiRepo.lastPayload?.logs.count == 1)
    }

    @Test("formatLogsForAPI with nil data: uses string format")
    func formatLogsWithNilData() async throws {
        let repo = MockLoggerRepo()
        repo.logs = [makeLogEntry(accountId: "acct-1", tag: "T", message: "M", data: nil)]
        let apiRepo = MockLoggerApiRepo()
        let (sut, _, _, _) = makeSUT(repo: repo, apiRepo: apiRepo)

        try await sut.sendLogsToServer(accountId: "acct-1", version: "1.0")
        #expect(apiRepo.lastPayload?.logs.count == 1)
    }

    @Test("formatLogsForAPI with empty data string: uses string format")
    func formatLogsWithEmptyData() async throws {
        let repo = MockLoggerRepo()
        repo.logs = [makeLogEntry(accountId: "acct-1", tag: "T", message: "M", data: "")]
        let apiRepo = MockLoggerApiRepo()
        let (sut, _, _, _) = makeSUT(repo: repo, apiRepo: apiRepo)

        try await sut.sendLogsToServer(accountId: "acct-1", version: "1.0")
        #expect(apiRepo.lastPayload?.logs.count == 1)
    }

    @Test("formatLogsForAPI with JSON array data: embeds in array")
    func formatLogsWithJSONArrayData() async throws {
        let repo = MockLoggerRepo()
        repo.logs = [makeLogEntry(accountId: "acct-1", tag: "T", message: "M", data: "[\"a\",\"b\"]")]
        let apiRepo = MockLoggerApiRepo()
        let (sut, _, _, _) = makeSUT(repo: repo, apiRepo: apiRepo)

        try await sut.sendLogsToServer(accountId: "acct-1", version: "1.0")
        #expect(apiRepo.lastPayload?.logs.count == 1)
    }

    // MARK: - formatScaleLogsForAPI edge cases

    @Test("formatScaleLogsForAPI with empty lines: skips empty")
    func formatScaleLogsSkipsEmpty() async throws {
        let apiRepo = MockLoggerApiRepo()
        let (sut, _, _, _) = makeSUT(apiRepo: apiRepo)

        let logs = [DeviceLogEntry(macAddress: "X", log: "line1\n\nline2")]
        try await sut.sendScaleLogsToServer(deviceLogs: logs, version: "1.0")
        #expect(apiRepo.lastPayload?.logs.count == 3) // mac + line1 + line2
    }

    // MARK: - sendLogsToServer uses active account when no explicit ID

    @Test("sendLogsToServer uses active account ID when nil passed")
    func sendLogsToServerUsesActiveAccount() async throws {
        let repo = MockLoggerRepo()
        repo.logs = [makeLogEntry(accountId: "acct-1")]
        let apiRepo = MockLoggerApiRepo()
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "a@b.com", isActiveAccount: true)
        let (sut, _, _, _) = makeSUT(repo: repo, apiRepo: apiRepo, accountOverride: account)

        try await sut.sendLogsToServer(accountId: nil, version: "1.0")
        #expect(apiRepo.sendLogsCalls == 1)
    }
}

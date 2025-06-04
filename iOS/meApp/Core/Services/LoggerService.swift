import Foundation
import SwiftData

import SwiftData

@MainActor
final class DataStore {
    static let shared = DataStore()

    let container: ModelContainer
    let context: ModelContext

    private init() {
        let schema = Schema([Account.self, Device.self, LogEntry.self])
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
        self.container = try! ModelContainer(for: schema, configurations: [config])
        self.context = ModelContext(container)
    }
}

@MainActor
final class LoggerService: LoggerServiceProtocol {
    public static let shared = LoggerService()
    @Injector var accountService: AccountService
    
    private let loggerRepository: LoggerRepositoryProtocol = LoggerRepository()
    private let sessionId: String
    private let systemLogger: AppLogger
    
    init() {
        self.sessionId = UUID().uuidString
        self.systemLogger = AppLogger(tag: "GGMeAppLogger")
        Task {
            // Initialize the logger repository and delete old logs on startup
            do {
                try await deleteOldLogs()
            } catch {}
        }
    }

    public func log(level: LogLevel,
                    tag: String,
                    message: String,
                    data: Any? = nil,
                    function: StaticString = #function,
                    line: UInt = #line,
                    accountId: String? = nil) async {
        systemLogger.log(level: level,
                         tag: tag,
                         message: message,
                         data: data,
                         function: function,
                         line: line)

        let stringifiedData = data.map {
            ($0 as? Data).flatMap { String(data: $0, encoding: .utf8) } ?? String(describing: $0)
        }

        let entry = LogEntry(
            accountId: accountId ?? accountService.activeAccount?.accountId,
            sessionId: self.sessionId,
            tag: tag,
            tagId: String(describing: function),
            type: level.toLogType,
            message: message,
            data: stringifiedData
        )
        await loggerRepository.saveLogEntry(entry)
    }

    public func getAllLogs() async throws -> [LogEntry] {
        try await loggerRepository.fetchAllLogs()
    }

    public func getCurrentSessionLogs() async throws -> [LogEntry] {
        try await loggerRepository.fetchLogs(forSession: sessionId)
    }

    public func getLogsForAccount(_ accountId: String) async throws -> [LogEntry] {
        try await loggerRepository.fetchLogs(forAccount: accountId)
    }

    public func getLogs(from: Date, to: Date) async throws -> [LogEntry] {
        try await loggerRepository.fetchLogs(from: from, to: to)
    }

    public func deleteLogsForAccount(_ accountId: String) async throws {
        try await loggerRepository.deleteLogs(forAccount: accountId)
    }

    public func deleteAllLogs() async throws {
        try await loggerRepository.deleteAllLogs()
    }
    
    public func deleteOldLogs(_ olderThanDays: Int = AppConstants.logRetentionDays) async throws {
        try await loggerRepository.deleteLogsOlderThan(olderThanDays: olderThanDays)
    }
    
    public func getCurrentSessionId() -> String {
        return sessionId
    }
}

/*
 ==================================================================================
 ✅ LoggerService Usage Guide
 ==================================================================================
 📝 Logging:
 // Basic log
 await logger.log(level: .info, tag: "Startup", message: "App launched")

 // Log with additional data
 await logger.log(level: .debug, tag: "Network", message: "Response received", data: ["code": 200])

 // Log with a specific account ID
 await logger.log(level: .error, tag: "Auth", message: "Login failed", accountId: "user-123")

 📥 Fetching Logs:
 let allLogs = try await logger.getAllLogs()
 let sessionLogs = try await logger.getCurrentSessionLogs()
 let userLogs = try await logger.getLogsForAccount("user-123")
 let rangedLogs = try await logger.getLogs(from: startDate, to: endDate)

 🧹 Deleting Logs:
 try await logger.deleteLogsForAccount("user-123")
 try await logger.deleteAllLogs()

 🔍 Current Session ID:
 let sessionId = logger.getCurrentSessionId()

 🧠 Notes:
 - All logs are stored using SwiftData via LoggerRepository.
 - Each log includes session ID and function name for context.
 - System logs are mirrored using AppLogger for debugging.
 ==================================================================================
*/


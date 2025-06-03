import Foundation
import SwiftData

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
        
    public func getCurrentSessionId() -> String {
        return sessionId
    }
}


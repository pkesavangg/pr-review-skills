//
//  LoggerService.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

import Foundation
import SwiftData

@MainActor
final class LoggerService: LoggerServiceProtocol {
    public static let shared = LoggerService()
    
    @Injector var accountService: AccountService
    
    private let loggerRepository: LoggerRepositoryProtocol = LoggerRepository()
    private let sessionId: String = UUID().uuidString
    private let systemLogger: AppLogger = AppLogger(tag: "GGMeAppLogger")
    private let logQueue = DispatchQueue(label: "com.greatergoods.loggerServiceQueue", attributes: .concurrent)

    init() {
        Task {
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
                    accountId: String? = nil) {
        
        systemLogger.log(level: level,
                         tag: tag,
                         message: message,
                         data: data,
                         function: function,
                         line: line)

        // Capture values in MainActor before entering the queue
        let resolvedAccountId = accountId ?? self.accountService.activeAccount?.accountId
        let sessionId = self.sessionId

        logQueue.async(flags: .barrier) {
            let stringifiedData = data.map {
                ($0 as? Data).flatMap { String(data: $0, encoding: .utf8) } ?? String(describing: $0)
            }

            let entry = LogEntry(
                accountId: resolvedAccountId,
                sessionId: sessionId,
                tag: tag,
                tagId: String(describing: function),
                type: level.toLogType,
                message: message,
                data: stringifiedData
            )

            Task {
                await self.loggerRepository.saveLogEntry(entry)
            }
        }
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
    
    func deleteOldLogs(_ olderThanDays: Int = AppConstants.TimeoutsAndRetention.logRetentionDays) async throws {
        try await loggerRepository.deleteLogsOlderThan(olderThanDays: olderThanDays)
    }
        
    public func getCurrentSessionId() -> String {
        return sessionId
    }
}

// MARK: - USAGE GUIDE
//
// 📝 Log:
// LoggerService.shared.log(
//     level: .info,
//     tag: "MyView",
//     message: "Something happened",
//     data: ["key": "value"]
// )
//
// 🔍 Fetch:
// let logs = try await LoggerService.shared.getAllLogs()
// let session = try await LoggerService.shared.getCurrentSessionLogs()
// let account = try await LoggerService.shared.getLogsForAccount("id")
// let range = try await LoggerService.shared.getLogs(from: start, to: end)
//
// 🧹 Delete:
// try await LoggerService.shared.deleteAllLogs()
// try await LoggerService.shared.deleteLogsForAccount("id")
// try await LoggerService.shared.deleteOldLogs(30)
//
// 📌 Session:
// let id = LoggerService.shared.getCurrentSessionId()
//
// ⚠️ Error Handling:
// do {
//     let logs = try await LoggerService.shared.getAllLogs()
// } catch {
//     print("Error: \(error)")
// }
//
// ✅ Notes:
// - Singleton: `LoggerService.shared`
// - Logs saved via SwiftData + printed with `AppLogger`
// - `data` is auto-stringified if needed

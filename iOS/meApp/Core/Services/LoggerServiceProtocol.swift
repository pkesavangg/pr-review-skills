import Foundation
import SwiftData

@MainActor
protocol LoggerServiceProtocol {
    func getAllLogs() async throws -> [LogEntry]
    func getCurrentSessionLogs() async throws -> [LogEntry]
    func getLogsForAccount(_ accountId: String) async throws -> [LogEntry]
    func getLogs(from: Date, to: Date) async throws -> [LogEntry]

    func log(level: LogLevel,
             tag: String,
             message: String,
             data: Any?,
             function: StaticString,
             line: UInt,
             accountId: String?)

    func getCurrentSessionId() -> String
    func deleteLogsForAccount(_ accountId: String) async throws
    func deleteAllLogs() async throws
    func deleteOldLogs(_ olderThanDays: Int) async throws
    func sendLogsToServer(accountId: String?, version: String) async throws
    func sendScaleLogsToServer(deviceLogs: [DeviceLogEntry], version: String) async throws
}

extension LoggerServiceProtocol {
    func log(
        level: LogLevel,
        tag: String,
        message: String,
        data: Any? = nil,
        function: StaticString = #function,
        line: UInt = #line,
        accountId: String? = nil
    ) {
        log(level: level, tag: tag, message: message, data: data, function: function, line: line, accountId: accountId)
    }
}

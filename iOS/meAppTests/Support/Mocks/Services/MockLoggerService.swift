import Foundation
@testable import meApp

@MainActor
final class MockLoggerService: LoggerServiceProtocol {
    struct LoggedEntry: Equatable {
        let level: LogLevel
        let tag: String
        let message: String
    }

    private(set) var messages: [String] = []
    private(set) var entries: [LoggedEntry] = []

    func getAllLogs() async throws -> [LogEntry] { [] }
    func getCurrentSessionLogs() async throws -> [LogEntry] { [] }
    func getLogsForAccount(_ accountId: String) async throws -> [LogEntry] { [] }
    func getLogs(from: Date, to: Date) async throws -> [LogEntry] { [] }

    // swiftlint:disable:next function_parameter_count
    func log(
        level: LogLevel,
        tag: String,
        message: String,
        data: Any?,
        function: StaticString,
        line: UInt,
        accountId: String?
    ) {
        entries.append(LoggedEntry(level: level, tag: tag, message: message))
        messages.append("[\(tag)] \(message)")
    }

    func getCurrentSessionId() -> String { "test-session" }
    func deleteLogsForAccount(_ accountId: String) async throws {}
    func deleteAllLogs() async throws {}
    func deleteOldLogs(_ olderThanDays: Int) async throws {}
    func sendLogsToServer(accountId: String?, version: String) async throws {}
    func sendScaleLogsToServer(deviceLogs: [DeviceLogEntry], version: String) async throws {}
}

//
//  MockLoggerService.swift
//  meAppTests
//

import Foundation
import SwiftData
@testable import meApp

/// Mock for LoggerServiceProtocol. Records log calls for assertion in tests.
@MainActor
final class MockLoggerService: LoggerServiceProtocol {

    // MARK: - Call tracking

    struct LogCall {
        let level: LogLevel
        let tag: String
        let message: String
    }

    var logCalls: [LogCall] = []

    // MARK: - LoggerServiceProtocol

    func log(
        level: LogLevel,
        tag: String,
        message: String,
        data: Any? = nil,
        function: StaticString = #function,
        line: UInt = #line,
        accountId: String? = nil
    ) {
        logCalls.append(LogCall(level: level, tag: tag, message: message))
    }

    func getAllLogs() async throws -> [LogEntry] { [] }

    func getCurrentSessionLogs() async throws -> [LogEntry] { [] }

    func getLogsForAccount(_ accountId: String) async throws -> [LogEntry] { [] }

    func getLogs(from: Date, to: Date) async throws -> [LogEntry] { [] }

    func getCurrentSessionId() -> String { "mock-session-id" }

    func deleteLogsForAccount(_ accountId: String) async throws {}

    func deleteAllLogs() async throws {}

    func deleteOldLogs(_ olderThanDays: Int) async throws {}

    func sendLogsToServer(accountId: String?, version: String) async throws {}

    func sendScaleLogsToServer(deviceLogs: [DeviceLogEntry], version: String) async throws {}

    // MARK: - Helpers

    func reset() {
        logCalls.removeAll()
    }

    func hasLog(level: LogLevel? = nil, containing message: String) -> Bool {
        logCalls.contains { call in
            let levelMatch = level == nil || call.level == level
            return levelMatch && call.message.contains(message)
        }
    }
}

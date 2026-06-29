//
//  MockHelpStoreLoggerService.swift
//  meAppTests
//

import Foundation
@testable import meApp

@MainActor
final class MockHelpStoreLoggerService: LoggerServiceProtocol {
    private(set) var messages: [String] = []
    private(set) var sendLogsToServerCalls = 0
    private(set) var sendScaleLogsToServerCalls = 0
    var sendLogsToServerError: Error?
    var sendScaleLogsToServerError: Error?

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
        messages.append("[\(tag)] \(message)")
    }

    func sendLogsToServer(accountId: String?, version: String) async throws {
        sendLogsToServerCalls += 1
        if let error = sendLogsToServerError { throw error }
    }

    func sendScaleLogsToServer(deviceLogs: [DeviceLogEntry], version: String) async throws {
        sendScaleLogsToServerCalls += 1
        if let error = sendScaleLogsToServerError { throw error }
    }

    func getCurrentSessionId() -> String { "test-session" }
    func getAllLogs() async throws -> [LogEntry] { [] }
    func getCurrentSessionLogs() async throws -> [LogEntry] { [] }
    func getLogsForAccount(_ accountId: String) async throws -> [LogEntry] { [] }
    func getLogs(from: Date, to: Date) async throws -> [LogEntry] { [] }
    func deleteLogsForAccount(_ accountId: String) async throws {}
    func deleteAllLogs() async throws {}
    func deleteOldLogs(_ olderThanDays: Int) async throws {}
}

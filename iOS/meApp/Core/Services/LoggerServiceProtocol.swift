import Foundation
import SwiftData

@MainActor
protocol LoggerServiceProtocol {
    /// Gets all logs stored in the database
    func getAllLogs() async throws -> [LogEntry]
    
    /// Gets logs for the current session
    func getCurrentSessionLogs() async throws -> [LogEntry]
    
    /// Gets logs for a specific account
    func getLogsForAccount(_ accountId: String) async throws -> [LogEntry]
    
    /// Gets logs within a date range
    func getLogs(from: Date, to: Date) async throws -> [LogEntry]
    
    /// Log a new entry with specified parameters
    func log(level: LogLevel,
             tag: String,
             message: String,
             data: Any?,
             function: StaticString,
             line: UInt,
             accountId: String?) async
    
    /// Gets the current session ID
    func getCurrentSessionId() -> String
    
    /// Deletes all logs for a specific account
    func deleteLogsForAccount(_ accountId: String) async throws
}


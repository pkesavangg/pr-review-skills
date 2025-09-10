import Foundation
import SwiftData

@MainActor
protocol LoggerServiceProtocol {
    /// Gets all logs stored in the database
    /// - Returns: An array of `LogEntry` objects
    func getAllLogs() async throws -> [LogEntry]
    
    /// Gets logs for the current session
    /// - Returns: An array of `LogEntry` objects for the current session
    func getCurrentSessionLogs() async throws -> [LogEntry]
    
    /// Gets logs for a specific account
    /// - Parameter accountId: The ID of the account to fetch logs for
    /// - Returns: An array of `LogEntry` objects for the specified account
    func getLogsForAccount(_ accountId: String) async throws -> [LogEntry]
    
    /// Gets logs within a date range
    /// - Parameters:
    ///   - from: Start date of the range
    ///   - to: End date of the range
    /// - Returns: An array of `LogEntry` objects within the specified date range
    func getLogs(from: Date, to: Date) async throws -> [LogEntry]
    
    /// Log a new entry with specified parameters
    /// - Parameters:
    ///   - level: The log level (e.g., info, debug, error)
    ///   - tag: A tag for categorizing the log
    ///   - message: The log message
    ///   - data: Optional additional data to log
    ///   - function: The function name where the log is called
    ///   - line: The line number where the log is called
    ///   - accountId: Optional account ID to associate with the log
    /// - Note: Debug level logs are not persisted to the database; they are only printed.
    func log(level: LogLevel,
             tag: String,
             message: String,
             data: Any?,
             function: StaticString,
             line: UInt,
             accountId: String?) async
    
    /// Gets the current session ID
    /// - Returns: The current session ID as a string
    func getCurrentSessionId() -> String
    
    /// Deletes all logs for a specific account
    /// - Parameter accountId: The ID of the account whose logs should be deleted
    func deleteLogsForAccount(_ accountId: String) async throws

    /// Deletes all logs in the database
    func deleteAllLogs() async throws
    
    /// Deletes logs older than a specified number of days
    /// - Parameter olderThanDays: The number of days to look back for deletion
    func deleteOldLogs(_ olderThanDays: Int) async throws
    
    /// Sends logs to the server for the specified or current account
    /// - Parameters:
    ///   - accountId: Optional account ID to send logs for (defaults to current account)
    ///   - version: App version to include in the logs payload
    /// - Throws: Error if no active account found or if sending fails
    func sendLogsToServer(accountId: String?, version: String) async throws
    
    /// Sends scale logs to the server
    /// - Parameters:
    ///  - deviceLogs: Array of `DeviceLogEntry` objects to send
    ///  - version: App version to include in the logs payload (defaults to current app version)
    ///  - Throws: Error if sending fails
    func sendScaleLogsToServer(deviceLogs: [DeviceLogEntry], version: String) async throws
}


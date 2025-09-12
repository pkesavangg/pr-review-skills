//
//  LoggerRepositoryProtocol.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 03/06/25.
//

import Foundation

@MainActor
protocol LoggerRepositoryProtocol {
    /// Saves a log entry to the database.
    /// - Parameter entry: The log entry to be saved.
    func saveLogEntry(_ entry: LogEntry) async
    
    /// Fetches all log entries from the database.
    /// - Returns: An array of all log entries.
    func fetchAllLogs() async throws -> [LogEntry]
    
    /// Fetches log entries for a specific session.
    /// - Parameter sessionId: The ID of the session for which logs are to be fetched.
    /// - Returns: An array of log entries for the specified session.
    func fetchLogs(forSession sessionId: String) async throws -> [LogEntry]
    
    /// Fetches log entries for a specific account.
    /// - Parameter accountId: The ID of the account for which logs are to be fetched.
    /// - Returns: An array of log entries for the specified account.
    func fetchLogs(forAccount accountId: String) async throws -> [LogEntry]
    
    /// Fetches log entries within a date range.
    /// - Parameters:
    ///  - from: The start date of the range.
    ///  - to: The end date of the range.
    ///  - Returns: An array of log entries within the specified date range.
    func fetchLogs(from: Date, to: Date) async throws -> [LogEntry]
    
    /// Deletes all log entries for a specific account.
    /// - Parameter accountId: The ID of the account for which logs are to be deleted.
    /// - Throws: An error if the deletion fails.
    func deleteLogs(forAccount accountId: String) async throws
    
    /// Deletes all log entries in the database.
    func deleteAllLogs() async throws
    
    /// Deletes logs older than a specified number of days.
    /// - Parameter days: The number of days to look back for deletion.
    func deleteLogsOlderThan(olderThanDays days: Int) async throws

    /// Deletes logs older than a specified number of days in small batches to limit CPU spikes.
    /// - Parameters:
    ///   - days: The number of days to look back for deletion.
    ///   - batchSize: Maximum number of rows to delete per batch.
    ///   - interBatchDelayNs: Delay between batches in nanoseconds to yield CPU.
    func deleteLogsOlderThanInBatches(
        olderThanDays days: Int,
        batchSize: Int,
        interBatchDelayNs: UInt64
    ) async throws

    /// Quickly checks if there are any logs older than the given retention window.
    /// - Parameter days: Retention window in days.
    /// - Returns: True if at least one old log exists; false otherwise.
    func hasLogsOlderThan(olderThanDays days: Int) async throws -> Bool
}

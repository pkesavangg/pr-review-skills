//
//  LoggerRepository.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 03/06/25.
//


import Foundation
import SwiftData

@MainActor
final class LoggerRepository: LoggerRepositoryProtocol {
    // MARK: - Properties
    
    private let context: ModelContext = PersistenceController.shared.context

    // MARK: - Saving
    func saveLogEntry(_ entry: LogEntry) async {
        context.insert(entry)
        do {
            try context.save()
        } catch {
            print("LoggerRepository save failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Fetching
    func fetchAllLogs() async throws -> [LogEntry] {
        let descriptor = FetchDescriptor<LogEntry>()
        return try context.fetch(descriptor)
    }

    func fetchLogs(forSession sessionId: String) async throws -> [LogEntry] {
        let descriptor = FetchDescriptor<LogEntry>(
            predicate: #Predicate { $0.sessionId == sessionId }
        )
        return try context.fetch(descriptor)
    }

    func fetchLogs(forAccount accountId: String) async throws -> [LogEntry] {
        let descriptor = FetchDescriptor<LogEntry>(
            predicate: #Predicate { $0.accountId == accountId }
        )
        return try context.fetch(descriptor)
    }

    func fetchLogs(from: Date, to: Date) async throws -> [LogEntry] {
        let start = Int64(from.timeIntervalSince1970 * 1000)
        let end = Int64(to.timeIntervalSince1970 * 1000)
        let descriptor = FetchDescriptor<LogEntry>(
            predicate: #Predicate { $0.timestamp >= start && $0.timestamp <= end }
        )
        return try context.fetch(descriptor)
    }

    // MARK: - Deleting
    func deleteLogs(forAccount accountId: String) async throws {
        let logs = try await fetchLogs(forAccount: accountId)
        logs.forEach { context.delete($0) }
        try context.save()
    }
    
    func deleteAllLogs() async throws {
        for log in try await fetchAllLogs() {
            context.delete(log)
        }
        try context.save()
    }
    
    func deleteLogsOlderThan(olderThanDays days: Int) async throws {
        let cutoffTimestamp = DateTimeTools.getTimestampDaysAgo(days)
        
        let descriptor = FetchDescriptor<LogEntry>(
            predicate: #Predicate { $0.timestamp < cutoffTimestamp }
        )
        
        let oldLogs = try context.fetch(descriptor)
        oldLogs.forEach { context.delete($0) }
        try context.save()
    }
}

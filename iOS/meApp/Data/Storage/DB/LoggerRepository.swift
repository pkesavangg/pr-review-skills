//
//  LoggerRepository.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

import Foundation
import SwiftData

@MainActor
final class LoggerRepository: LoggerRepositoryProtocol {
    private let container: ModelContainer
    private let appLogger = AppLogger(tag: "LoggerRepository")

    init(container: ModelContainer? = nil) {
        self.container = container ?? PersistenceController.shared.container
    }

    /// Executes work on a background `ModelContext` to avoid blocking the main actor.
    /// Mirrors the approach used in `EntryRepository`.
    /// - Parameter work: Closure that performs fetch/update/delete using the provided background context.
    /// - Returns: The result of the work closure.
    private func performBackgroundTask<T>(_ work: @escaping (ModelContext) throws -> T) async throws -> T {
        let container = self.container
        return try await Task.detached(priority: .userInitiated) {
            let backgroundContext = ModelContext(container)
            return try work(backgroundContext)
        }.value
    }

    func saveLogEntry(_ entry: LogEntry) async {
        // Extract all data before crossing actor boundary
        let id = entry.id
        let accountId = entry.accountId
        let sessionId = entry.sessionId
        let tag = entry.tag
        let tagId = entry.tagId
        let type = entry.type
        let message = entry.message
        let timestamp = entry.timestamp
        let data = entry.data

        do {
            try await performBackgroundTask { ctx in
                let newEntry = LogEntry(
                    id: id,
                    accountId: accountId,
                    sessionId: sessionId,
                    tag: tag,
                    tagId: tagId,
                    type: type,
                    message: message,
                    timestamp: timestamp,
                    data: data
                )
                ctx.insert(newEntry)
                try ctx.save()
            }
        } catch {
            appLogger.log(level: .error, tag: "LoggerRepository", message: "Save failed", data: error.localizedDescription)
        }
    }

    func saveLogEntries(_ entries: [LogEntrySnapshot]) async {
        guard !entries.isEmpty else { return }
        do {
            try await performBackgroundTask { ctx in
                for entry in entries {
                    let newEntry = LogEntry(
                        id: entry.id,
                        accountId: entry.accountId,
                        sessionId: entry.sessionId,
                        tag: entry.tag,
                        tagId: entry.tagId,
                        type: entry.type,
                        message: entry.message,
                        timestamp: entry.timestamp,
                        data: entry.data
                    )
                    ctx.insert(newEntry)
                }
                // One transaction for the whole batch (MOB-519) — not one save per row.
                try ctx.save()
            }
        } catch {
            appLogger.log(level: .error, tag: "LoggerRepository", message: "Batch save failed", data: error.localizedDescription)
        }
    }

    func fetchAllLogs() async throws -> [LogEntry] {
        return try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<LogEntry>()
            return try ctx.fetch(descriptor)
        }
    }

    func fetchLogs(forSession sessionId: String) async throws -> [LogEntry] {
        return try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<LogEntry>(
                predicate: #Predicate { $0.sessionId == sessionId }
            )
            return try ctx.fetch(descriptor)
        }
    }

    func fetchLogs(forAccount accountId: String) async throws -> [LogEntry] {
        return try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<LogEntry>(
                predicate: #Predicate { $0.accountId == accountId }
            )
            return try ctx.fetch(descriptor)
        }
    }

    func fetchLogs(from: Date, to: Date) async throws -> [LogEntry] {
        let start = Int64(from.timeIntervalSince1970 * 1000)
        let end = Int64(to.timeIntervalSince1970 * 1000)
        return try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<LogEntry>(
                predicate: #Predicate { $0.timestamp >= start && $0.timestamp <= end }
            )
            return try ctx.fetch(descriptor)
        }
    }

    func deleteLogs(forAccount accountId: String) async throws {
        try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<LogEntry>(
                predicate: #Predicate { $0.accountId == accountId }
            )
            let logs = try ctx.fetch(descriptor)
            logs.forEach { ctx.delete($0) }
            try ctx.save()
            return ()
        }
    }
    
    func deleteAllLogs() async throws {
        try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<LogEntry>()
            let all = try ctx.fetch(descriptor)
            for log in all { ctx.delete(log) }
            try ctx.save()
            return ()
        }
    }
    
    func deleteLogsOlderThan(olderThanDays days: Int) async throws {
        let cutoffTimestamp = DateTimeTools.getTimestampDaysAgo(days)
        try await performBackgroundTask { ctx in
            let descriptor = FetchDescriptor<LogEntry>(
                predicate: #Predicate { $0.timestamp < cutoffTimestamp }
            )
            let oldLogs = try ctx.fetch(descriptor)
            oldLogs.forEach { ctx.delete($0) }
            try ctx.save()
            return ()
        }
    }

    /// Deletes old logs in small batches to reduce CPU spikes and memory usage.
    /// - Parameters:
    ///   - days: Retention window in days. Anything older will be deleted.
    ///   - batchSize: Maximum number of rows to delete per batch.
    ///   - interBatchDelayNs: Optional delay between batches to yield CPU.
    func deleteLogsOlderThanInBatches(
        olderThanDays days: Int,
        batchSize: Int = 500,
        interBatchDelayNs: UInt64 = 20_000_000
    ) async throws {
        let cutoffTimestamp = DateTimeTools.getTimestampDaysAgo(days)

        while true {
            let deletedCount: Int = try await performBackgroundTask { ctx in
                var descriptor = FetchDescriptor<LogEntry>(
                    predicate: #Predicate { $0.timestamp < cutoffTimestamp }
                )
                descriptor.fetchLimit = batchSize

                let toDelete = try ctx.fetch(descriptor)
                guard !toDelete.isEmpty else { return 0 }
                toDelete.forEach { ctx.delete($0) }
                try ctx.save()
                return toDelete.count
            }

            if deletedCount == 0 { break }

            // Yield a bit between batches to avoid prolonged 100% CPU usage
            try? await Task.sleep(nanoseconds: interBatchDelayNs)
            try Task.checkCancellation()
        }
    }

    func hasLogsOlderThan(olderThanDays days: Int) async throws -> Bool {
        let cutoffTimestamp = DateTimeTools.getTimestampDaysAgo(days)
        return try await performBackgroundTask { ctx in
            var descriptor = FetchDescriptor<LogEntry>(
                predicate: #Predicate { $0.timestamp < cutoffTimestamp }
            )
            descriptor.fetchLimit = 1
            let result = try ctx.fetch(descriptor)
            return !result.isEmpty
        }
    }
}

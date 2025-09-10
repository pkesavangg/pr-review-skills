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
    private let modelContext: ModelContext = PersistenceController.shared.context

    /// Executes work on a background `ModelContext` to avoid blocking the main actor.
    /// Mirrors the approach used in `EntryRepository`.
    /// - Parameter work: Closure that performs fetch/update/delete using the provided background context.
    /// - Returns: The result of the work closure.
    private func performBackgroundTask<T>(_ work: @escaping (ModelContext) throws -> T) async throws -> T {
        let container = PersistenceController.shared.container
        return try await Task.detached(priority: .userInitiated) {
            let backgroundContext = ModelContext(container)
            return try work(backgroundContext)
        }.value
    }

    func saveLogEntry(_ entry: LogEntry) async {
        modelContext.insert(entry)
        do {
            try modelContext.save()
        } catch {
            print("LoggerRepository save failed: \(error.localizedDescription)")
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

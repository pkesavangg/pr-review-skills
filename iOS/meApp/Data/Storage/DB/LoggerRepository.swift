//
//  LoggerRepository.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

import Foundation
import SwiftData

// MARK: - Serial write actor

/// Owns a single ModelContext and serialises all logger write/delete operations
/// through Swift's actor executor.  Replaces the previous pattern of creating a
/// new ModelContext per Task.detached call, which allowed multiple simultaneous
/// background contexts to write to the same SQLite store and corrupt SwiftData's
/// internal backing-data dictionary.
private actor LoggerWriteActor {
    var context: ModelContext

    init(container: ModelContainer) {
        context = ModelContext(container)
        context.autosaveEnabled = false
    }

    func saveEntry(id: String, accountId: String?, sessionId: String, tag: String,
                   tagId: String, type: LogEntry.LogType, message: String,
                   timestamp: Int64, data: String?) throws {
        let entry = LogEntry(id: id, accountId: accountId, sessionId: sessionId,
                             tag: tag, tagId: tagId, type: type,
                             message: message, timestamp: timestamp, data: data)
        context.insert(entry)
        try context.save()
    }

    func deleteForAccount(_ accountId: String) throws {
        let descriptor = FetchDescriptor<LogEntry>(
            predicate: #Predicate { $0.accountId == accountId }
        )
        let logs = try context.fetch(descriptor)
        logs.forEach { context.delete($0) }
        try context.save()
    }

    func deleteAll() throws {
        let all = try context.fetch(FetchDescriptor<LogEntry>())
        all.forEach { context.delete($0) }
        try context.save()
    }

    func deleteOlderThan(_ cutoff: Int64) throws {
        let descriptor = FetchDescriptor<LogEntry>(
            predicate: #Predicate { $0.timestamp < cutoff }
        )
        let old = try context.fetch(descriptor)
        old.forEach { context.delete($0) }
        try context.save()
    }

    func deleteOlderThanBatch(_ cutoff: Int64, batchSize: Int) throws -> Int {
        var descriptor = FetchDescriptor<LogEntry>(
            predicate: #Predicate { $0.timestamp < cutoff }
        )
        descriptor.fetchLimit = batchSize
        let toDelete = try context.fetch(descriptor)
        guard !toDelete.isEmpty else { return 0 }
        toDelete.forEach { context.delete($0) }
        try context.save()
        return toDelete.count
    }

    func hasOlderThan(_ cutoff: Int64) throws -> Bool {
        var descriptor = FetchDescriptor<LogEntry>(
            predicate: #Predicate { $0.timestamp < cutoff }
        )
        descriptor.fetchLimit = 1
        return !(try context.fetch(descriptor).isEmpty)
    }
}

// MARK: - Repository

@MainActor
final class LoggerRepository: LoggerRepositoryProtocol {

    /// Shared singleton used by LoggerService.log so every save call flows through
    /// the same LoggerWriteActor — never creating competing background contexts.
    static let shared = LoggerRepository()

    /// Serial actor that owns one background ModelContext for all writes/deletes.
    private let writeActor: LoggerWriteActor

    /// Separate container reference used only for read-only fetch operations.
    private let container: ModelContainer

    init() {
        let c = PersistenceController.shared.container
        self.container = c
        self.writeActor = LoggerWriteActor(container: c)
    }

    // MARK: - Write (serialised through writeActor)

    func saveLogEntry(_ entry: LogEntry) async {
        // Extract all primitives before crossing the actor boundary.
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
            try await writeActor.saveEntry(
                id: id, accountId: accountId, sessionId: sessionId,
                tag: tag, tagId: tagId, type: type,
                message: message, timestamp: timestamp, data: data
            )
        } catch {
            print("LoggerRepository save failed: \(error.localizedDescription)")
        }
    }

    func deleteLogs(forAccount accountId: String) async throws {
        try await writeActor.deleteForAccount(accountId)
    }

    func deleteAllLogs() async throws {
        try await writeActor.deleteAll()
    }

    func deleteLogsOlderThan(olderThanDays days: Int) async throws {
        let cutoff = DateTimeTools.getTimestampDaysAgo(days)
        try await writeActor.deleteOlderThan(cutoff)
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
        let cutoff = DateTimeTools.getTimestampDaysAgo(days)
        while true {
            let deletedCount = try await writeActor.deleteOlderThanBatch(cutoff, batchSize: batchSize)
            if deletedCount == 0 { break }
            try? await Task.sleep(nanoseconds: interBatchDelayNs)
            try Task.checkCancellation()
        }
    }

    func hasLogsOlderThan(olderThanDays days: Int) async throws -> Bool {
        let cutoff = DateTimeTools.getTimestampDaysAgo(days)
        return try await writeActor.hasOlderThan(cutoff)
    }

    // MARK: - Read (each fetch gets its own fresh context — read-only, no write contention)

    func fetchAllLogs() async throws -> [LogEntry] {
        let container = self.container
        return try await Task.detached(priority: .userInitiated) {
            let ctx = ModelContext(container)
            return try ctx.fetch(FetchDescriptor<LogEntry>())
        }.value
    }

    func fetchLogs(forSession sessionId: String) async throws -> [LogEntry] {
        let container = self.container
        return try await Task.detached(priority: .userInitiated) {
            let ctx = ModelContext(container)
            let descriptor = FetchDescriptor<LogEntry>(
                predicate: #Predicate { $0.sessionId == sessionId }
            )
            return try ctx.fetch(descriptor)
        }.value
    }

    func fetchLogs(forAccount accountId: String) async throws -> [LogEntry] {
        let container = self.container
        return try await Task.detached(priority: .userInitiated) {
            let ctx = ModelContext(container)
            let descriptor = FetchDescriptor<LogEntry>(
                predicate: #Predicate { $0.accountId == accountId }
            )
            return try ctx.fetch(descriptor)
        }.value
    }

    func fetchLogs(from: Date, to: Date) async throws -> [LogEntry] {
        let start = Int64(from.timeIntervalSince1970 * 1000)
        let end = Int64(to.timeIntervalSince1970 * 1000)
        let container = self.container
        return try await Task.detached(priority: .userInitiated) {
            let ctx = ModelContext(container)
            let descriptor = FetchDescriptor<LogEntry>(
                predicate: #Predicate { $0.timestamp >= start && $0.timestamp <= end }
            )
            return try ctx.fetch(descriptor)
        }.value
    }
}

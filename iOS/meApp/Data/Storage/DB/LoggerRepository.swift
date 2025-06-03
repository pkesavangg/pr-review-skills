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
    private let modelContext: ModelContext

    init() {
        let schema = Schema([LogEntry.self])
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
        let container = try! ModelContainer(for: schema, configurations: [config])
        self.modelContext = ModelContext(container)
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
        let descriptor = FetchDescriptor<LogEntry>()
        return try modelContext.fetch(descriptor)
    }

    func fetchLogs(forSession sessionId: String) async throws -> [LogEntry] {
        let descriptor = FetchDescriptor<LogEntry>(
            predicate: #Predicate { $0.sessionId == sessionId }
        )
        return try modelContext.fetch(descriptor)
    }

    func fetchLogs(forAccount accountId: String) async throws -> [LogEntry] {
        let descriptor = FetchDescriptor<LogEntry>(
            predicate: #Predicate { $0.accountId == accountId }
        )
        return try modelContext.fetch(descriptor)
    }

    func fetchLogs(from: Date, to: Date) async throws -> [LogEntry] {
        let start = Int64(from.timeIntervalSince1970 * 1000)
        let end = Int64(to.timeIntervalSince1970 * 1000)
        let descriptor = FetchDescriptor<LogEntry>(
            predicate: #Predicate { $0.timestamp >= start && $0.timestamp <= end }
        )
        return try modelContext.fetch(descriptor)
    }

    func deleteLogs(forAccount accountId: String) async throws {
        let logs = try await fetchLogs(forAccount: accountId)
        logs.forEach { modelContext.delete($0) }
        try modelContext.save()
    }
    
    func deleteAllLogs() async throws {
        for log in try await fetchAllLogs() {
            modelContext.delete(log)
        }
        try modelContext.save()
    }
}

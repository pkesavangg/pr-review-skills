//
//  LoggerRepositoryProtocol.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 03/06/25.
//


import Foundation

@MainActor
protocol LoggerRepositoryProtocol {
    func saveLogEntry(_ entry: LogEntry) async
    func fetchAllLogs() async throws -> [LogEntry]
    func fetchLogs(forSession sessionId: String) async throws -> [LogEntry]
    func fetchLogs(forAccount accountId: String) async throws -> [LogEntry]
    func fetchLogs(from: Date, to: Date) async throws -> [LogEntry]
    func deleteLogs(forAccount accountId: String) async throws
    func deleteAllLogs() async throws
}

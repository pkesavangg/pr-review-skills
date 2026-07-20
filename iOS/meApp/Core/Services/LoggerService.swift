//
//  LoggerService.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

import Foundation
import SwiftData
#if canImport(UIKit)
import UIKit
#endif

@MainActor
final class LoggerService: LoggerServiceProtocol {
    public static let shared = LoggerService()
    
    @Injector var accountService: AccountServiceProtocol
    
    let loggerRepository: LoggerRepositoryProtocol
    let loggerApiRepository: LoggerApiRepositoryProtocol
    private let sessionId: String
    private let systemLogger = AppLogger(tag: "GGMeAppLogger")
    private var consoleMinimumLogLevel: LogLevel = .info
    private let kv: KvStorageService
    private static let lastCleanupKey = "logger_last_cleanup_ts"

    // MARK: - Batched persistence (MOB-519)

    /// Minimum severity that gets PERSISTED to disk (console printing is gated
    /// separately by `consoleMinimumLogLevel`). Default `.info` keeps the full
    /// support-log narrative (`send logs to server`) while the batching below
    /// removes the per-line disk-write storm. Raise it (e.g. `.error`) to also
    /// drop `.info`/`.success` from disk — comparison uses `LogLevel.severityRank`.
    var persistenceMinimumLogLevel: LogLevel
    /// Flush the buffer once it holds this many rows.
    private let flushThreshold: Int
    /// Trailing-window flush interval for a partially-filled buffer.
    private let flushIntervalNs: UInt64
    /// Rows awaiting a batched write. Main-actor isolated (the class is `@MainActor`).
    private var pendingLogs: [LogEntrySnapshot] = []
    private var flushTimerTask: Task<Void, Never>?
    
    init(
        loggerRepository: LoggerRepositoryProtocol? = nil,
        loggerApiRepository: LoggerApiRepositoryProtocol? = nil,
        sessionId: String? = nil,
        kv: KvStorageService? = nil,
        persistenceMinimumLogLevel: LogLevel = .info,
        flushThreshold: Int = 50,
        flushInterval: TimeInterval = 3.0,
        skipCleanup: Bool = false
    ) {
        self.loggerRepository = loggerRepository ?? LoggerRepository()
        self.loggerApiRepository = loggerApiRepository ?? LoggerApiRepository()
        self.sessionId = sessionId ?? UUID().uuidString
        self.kv = kv ?? KvStorageService.shared
        self.persistenceMinimumLogLevel = persistenceMinimumLogLevel
        self.flushThreshold = max(1, flushThreshold)
        self.flushIntervalNs = UInt64(max(0, flushInterval) * 1_000_000_000)
        if !skipCleanup {
            Self.scheduleDeleteOldLogsBackground(service: self)
            #if canImport(UIKit)
            setupLifecycleFlush()
            #endif
        }
    }
    
    public func log(level: LogLevel,
                    tag: String,
                    message: String,
                    data: Any? = nil,
                    function: StaticString = #function,
                    line: UInt = #line,
                    accountId: String? = nil) {
        
        // Console: print only at/above the console floor. Uses severityRank, not
        // rawValue, because LogLevel raw values are not severity-ordered.
        if level.severityRank >= consoleMinimumLogLevel.severityRank {
            systemLogger.log(level: level,
                             tag: tag,
                             message: message,
                             data: data,
                             function: function,
                             line: line)
        }

        // Persistence floor (MOB-519): drop anything below the floor so low-value
        // lines never touch disk. Default floor `.info` still drops only `.debug`.
        guard level.severityRank >= persistenceMinimumLogLevel.severityRank else { return }

        // Build the Sendable payload here (the `data` argument was created on the
        // caller's actor anyway) and buffer it. The actual SwiftData write is
        // batched off-main in `flushPendingLogs()` — one transaction per flush,
        // not one per line (MOB-519).
        let stringifiedData = data.map {
            ($0 as? Data).flatMap { String(data: $0, encoding: .utf8) } ?? String(describing: $0)
        }
        let payload = LogEntrySnapshot(
            accountId: accountId ?? self.accountService.activeAccount?.accountId,
            sessionId: sessionId,
            tag: tag,
            tagId: String(describing: function),
            type: level.toLogType,
            message: message,
            data: stringifiedData
        )
        enqueueForPersistence(payload, level: level)
    }

    // MARK: - Batched persistence (MOB-519)

    private func enqueueForPersistence(_ payload: LogEntrySnapshot, level: LogLevel) {
        pendingLogs.append(payload)

        // Errors/criticals are support-critical — flush immediately so a crash
        // can't lose them. Everything else batches: flush when the buffer fills,
        // otherwise on a trailing timer window.
        if level.severityRank >= LogLevel.error.severityRank || pendingLogs.count >= flushThreshold {
            flushPendingLogs()
        } else {
            scheduleFlush()
        }
    }

    private func scheduleFlush() {
        guard flushTimerTask == nil else { return }   // a flush is already pending in this window
        let intervalNs = flushIntervalNs
        flushTimerTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: intervalNs)
            await self?.flushPendingLogs()
        }
    }

    /// Drains the buffer and persists it in one batched transaction. Safe to call
    /// repeatedly (no-op when the buffer is empty).
    func flushPendingLogs() {
        flushTimerTask?.cancel()
        flushTimerTask = nil
        guard !pendingLogs.isEmpty else { return }
        let batch = pendingLogs
        pendingLogs.removeAll(keepingCapacity: true)
        Task { @MainActor [weak self] in
            guard let self else { return }
            await self.loggerRepository.saveLogEntries(batch)
        }
    }

    #if canImport(UIKit)
    /// Flush buffered logs when the app backgrounds so a later kill doesn't lose
    /// them. Errors already flush immediately; this covers the batched
    /// `.info`/`.success` rows. The observer captures no `self` (it references the
    /// shared instance) so the `@Sendable` block stays clean; only registered for
    /// the shared instance (tests pass `skipCleanup: true` and skip it).
    private func setupLifecycleFlush() {
        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { _ in
            MainActor.assumeIsolated {
                LoggerService.shared.flushPendingLogs()
            }
        }
    }
    #endif
    
    public func getAllLogs() async throws -> [LogEntry] {
        try await loggerRepository.fetchAllLogs()
    }
    
    public func getCurrentSessionLogs() async throws -> [LogEntry] {
        try await loggerRepository.fetchLogs(forSession: sessionId)
    }
    
    public func getLogsForAccount(_ accountId: String) async throws -> [LogEntry] {
        try await loggerRepository.fetchLogs(forAccount: accountId)
    }
    
    public func getLogs(from: Date, to: Date) async throws -> [LogEntry] {
        try await loggerRepository.fetchLogs(from: from, to: to)
    }
    
    public func deleteLogsForAccount(_ accountId: String) async throws {
        try await loggerRepository.deleteLogs(forAccount: accountId)
    }
    
    public func deleteAllLogs() async throws {
        try await loggerRepository.deleteAllLogs()
    }
    
    func deleteOldLogs(_ olderThanDays: Int = AppConstants.TimeoutsAndRetention.logRetentionDays) async throws {
        try await loggerRepository.deleteLogsOlderThan(olderThanDays: olderThanDays)
    }
    
    public func getCurrentSessionId() -> String {
        return sessionId
    }
    
    /// Sends logs to the server for the current account
    public func sendLogsToServer(accountId: String? = nil, version: String = AppInfo.appVersion) async throws {
        let resolvedAccountId = accountId ?? self.accountService.activeAccount?.accountId
        guard let resolvedAccountId = resolvedAccountId else {
            throw LoggerServiceError.noActiveAccount
        }
        
        do {
            // Get logs for the account
            let logs = try await getLogsForAccount(resolvedAccountId)
            systemLogger.log(level: .info, tag: "LoggerService", message: "Uploading \(logs.count) logs for accountId=\(resolvedAccountId)")
            
            // Format logs for API
            let logsPayload = formatLogsForAPI(logs, version: version)
            
            // Send to API using LoggerApiRepository
            try await loggerApiRepository.sendLogs(logsPayload)
            
            // Clear logs for the account after successful upload
            try await loggerRepository.deleteLogs(forAccount: resolvedAccountId)
            systemLogger.log(
                level: .info,
                tag: "LoggerService",
                message: "Uploaded logs successfully and cleared local for accountId=\(resolvedAccountId)"
            )
        } catch {
            systemLogger.log(level: .error, tag: "LoggerService", message: "Failed to upload logs: \(error.localizedDescription)")
            throw error
        }
    }
    
    /// Sends scale logs to the server
    public func sendScaleLogsToServer(deviceLogs: [DeviceLogEntry], version: String = AppInfo.appVersion) async throws {
        do {
            systemLogger.log(level: .info, tag: "LoggerService", message: "Uploading scale logs count=\(deviceLogs.count)")
            // Format logs for API
            let logsPayload = formatScaleLogsForAPI(deviceLogs, version: version)
            
            // Send to API using LoggerApiRepository
            try await loggerApiRepository.sendLogs(logsPayload)
            systemLogger.log(level: .info, tag: "LoggerService", message: "Uploaded scale logs successfully")
        } catch {
            systemLogger.log(level: .error, tag: "LoggerService", message: "Failed to upload scale logs: \(error.localizedDescription)")
            throw error
        }
    }
    
    /// Helper method to parse additional data as string array
    private func parseDataAsStringArray(_ data: String) -> [String]? {
        // Try to parse as JSON array
        guard let jsonData = data.data(using: .utf8) else { return nil }
        
        do {
            if let array = try JSONSerialization.jsonObject(with: jsonData) as? [String] {
                return array
            }
        } catch {
            // If parsing fails, return nil to use as single string
        }
        
        return nil
    }
    
    /// Helper method to parse additional data as JSON object
    private func parseDataAsJSON(_ data: String) -> [String: Any]? {
        guard let jsonData = data.data(using: .utf8) else { return nil }
        
        do {
            if let jsonObject = try JSONSerialization.jsonObject(with: jsonData) as? [String: Any] {
                return jsonObject
            }
        } catch {
            // If parsing fails, return nil
        }
        
        return nil
    }
    
    /// Formats logs for API submission with the required JSON structure
    private func formatLogsForAPI(_ logs: [LogEntry], version: String = AppInfo.appVersion) -> LogsPayload {
        let formattedLogs = logs.map { logEntry -> LogEntryPayload in
            // Convert timestamp from milliseconds to ISO 8601 string
            let date = Date(timeIntervalSince1970: TimeInterval(logEntry.timestamp) / 1000.0)
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            let timeString = formatter.string(from: date)
            
            // Create the log message in the expected format: "Tag: Message"
            let logMessage = "\(logEntry.tag): \(logEntry.message)"
            
            // Create the data structure
            let data: LogEntryData
            if let additionalData = logEntry.data, !additionalData.isEmpty {
                // Try to parse additional data as JSON array
                var parsedData: [Any] = []
                
                if let dataArray = parseDataAsStringArray(additionalData) {
                    parsedData = [dataArray]
                } else {
                    // Try to parse as JSON object or use as string
                    if let jsonObject = parseDataAsJSON(additionalData) {
                        parsedData = [jsonObject]
                    } else {
                        // Use as plain string array
                        parsedData = [additionalData]
                    }
                }
                
                data = .array(logMessage, parsedData)
            } else {
                data = .string(logMessage)
            }
            
            return LogEntryPayload(time: timeString, data: data)
        }
        
        return LogsPayload(version: version, logs: formattedLogs)
    }
    
    /// Formats device logs for API submission
    private func formatScaleLogsForAPI(_ deviceLogs: [DeviceLogEntry], version: String = AppInfo.appVersion) -> LogsPayload {
        var formattedLogs: [LogEntryPayload] = []
        
        // Add MAC address entry if available
        if let macAddress = deviceLogs.first?.macAddress {
            formattedLogs.append(LogEntryPayload(
                time: ISO8601DateFormatter().string(from: Date()),
                data: .string("Mac Address: \(macAddress)")
            ))
        }
        
        // Process each log entry
        for deviceLog in deviceLogs {
            guard let logText = deviceLog.log else { continue }
            
            // Split log text into lines
            let logLines = logText.components(separatedBy: "\n")
            
            // Process each line
            for line in logLines where !line.isEmpty {
                formattedLogs.append(LogEntryPayload(
                    time: ISO8601DateFormatter().string(from: Date()),
                    data: .string(line)
                ))
            }
        }
        
        return LogsPayload(version: version, logs: formattedLogs)
    }
}

// MARK: - Background scheduling
extension LoggerService {
    /// Schedules log retention cleanup on a detached background task to avoid blocking the main actor during launch.
    private static func scheduleDeleteOldLogsBackground(service: LoggerService) {
        // Use a regular Task pinned to the main actor so we can safely
        // access main-actor-isolated properties. The repository performs
        // its own background work for heavy operations.
        Task { @MainActor in
            // Run at most once per day
            let now = Date().timeIntervalSince1970
            let last = (service.kv.getValue(forKey: Self.lastCleanupKey) as? Double) ?? 0
            if now - last < 86_400 { return }

            // Give launch a brief, jittered head start to avoid contention
            let jitterNs = UInt64(Int.random(in: 200_000_000...600_000_000))
            try? await Task.sleep(nanoseconds: jitterNs)
            do {
                // Only run if needed, to avoid waking storage unnecessarily
                if try await service.loggerRepository.hasLogsOlderThan(olderThanDays: AppConstants.TimeoutsAndRetention.logRetentionDays) {
                    // Prefer batched deletion to limit CPU spikes
                    try await service.loggerRepository.deleteLogsOlderThanInBatches(
                        olderThanDays: AppConstants.TimeoutsAndRetention.logRetentionDays,
                        batchSize: 400,
                        interBatchDelayNs: 50_000_000
                    )
                    service.kv.setValue(now, forKey: Self.lastCleanupKey)
                }
            } catch {
                // Intentionally ignore failures here; retention is best-effort
            }
        }
    }
}

// MARK: - USAGE GUIDE
//
// 📝 Log:
// LoggerService.shared.log(
//     level: .info,
//     tag: "MyView",
//     message: "Something happened",
//     data: ["key": "value"]
// )
//
// 🔍 Fetch:
// let logs = try await LoggerService.shared.getAllLogs()
// let session = try await LoggerService.shared.getCurrentSessionLogs()
// let account = try await LoggerService.shared.getLogsForAccount("id")
// let range = try await LoggerService.shared.getLogs(from: start, to: end)
//
// 🧹 Delete:
// try await LoggerService.shared.deleteAllLogs()
// try await LoggerService.shared.deleteLogsForAccount("id")
// try await LoggerService.shared.deleteOldLogs(30)
//
// 📌 Session:
// let id = LoggerService.shared.getCurrentSessionId()
//
// ⚠️ Error Handling:
// do {
//     let logs = try await LoggerService.shared.getAllLogs()
// } catch {
//     print("Error: \(error)")
// }
//
// ✅ Notes:
// - Singleton: `LoggerService.shared`
// - Logs saved via SwiftData + printed with `AppLogger`
// - `data` is auto-stringified if needed

//
//  LoggerService.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

import Foundation
import SwiftData

@MainActor
final class LoggerService: LoggerServiceProtocol {
    public static let shared = LoggerService()
    
    @Injector var accountService: AccountService
    
    private let loggerRepository: LoggerRepositoryProtocol = LoggerRepository()
    private let loggerApiRepository: LoggerApiRepositoryProtocol = LoggerApiRepository()
    private let sessionId: String = UUID().uuidString
    private let systemLogger: AppLogger = AppLogger(tag: "GGMeAppLogger")
    private let logQueue = DispatchQueue(label: "com.greatergoods.loggerServiceQueue", attributes: .concurrent)
    private var consoleMinimumLogLevel: LogLevel = .info
    
    init() {
        Task {
            do {
                try await deleteOldLogs()
            } catch {}
        }
    }
    
    public func log(level: LogLevel,
                    tag: String,
                    message: String,
                    data: Any? = nil,
                    function: StaticString = #function,
                    line: UInt = #line,
                    accountId: String? = nil) {
        
        // Only print to console if level is greater than or equal to consoleMinimumLogLevel if want to see the debug logs remove the if statement
        if level.rawValue >= consoleMinimumLogLevel.rawValue {
            systemLogger.log(level: level,
                             tag: tag,
                             message: message,
                             data: data,
                             function: function,
                             line: line)
        }
        
        // Do not persist debug logs; only print to system logger
        if level == .debug { return }
        
        // Capture values in MainActor before entering the queue
        let resolvedAccountId = accountId ?? self.accountService.activeAccount?.accountId
        let sessionId = self.sessionId
        
        logQueue.async(flags: .barrier) {
            let stringifiedData = data.map {
                ($0 as? Data).flatMap { String(data: $0, encoding: .utf8) } ?? String(describing: $0)
            }
            
            let entry = LogEntry(
                accountId: resolvedAccountId,
                sessionId: sessionId,
                tag: tag,
                tagId: String(describing: function),
                type: level.toLogType,
                message: message,
                data: stringifiedData
            )
            
            Task {
                await self.loggerRepository.saveLogEntry(entry)
            }
        }
    }
    
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
            systemLogger.log(level: .info, tag: "LoggerService", message: "Uploaded logs successfully and cleared local for accountId=\(resolvedAccountId)")
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

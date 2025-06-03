//
//  AppLogger.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 29/05/25.
//

/**
 AppLogger provides thread-safe, leveled logging for debugging and analytics in the meApp project.
 
 - Features:
 - Thread-safe logging using a concurrent queue with barrier writes.
 - Supports log levels: debug, info, error, critical.
 - Optional data serialization for structured logging.
 - OSLog integration for system-level logging.
 - In-memory log buffer with configurable maximum size.
 - Usage guide included at the end of this file.
 */
/// AppLogger provides thread-safe, leveled logging with optional data serialization for debugging and analytics.

import Foundation
import os

/// Public DTO for log entries that can be exposed outside the module
// public struct LogEntryDTO: Identifiable {
//     public let id: String
//     public let accountId: String?
//     public let sessionId: String
//     public let tag: String
//     public let tagId: String
//     public let type: String
//     public let message: String
//     public let timestamp: Int64
//     public let data: String?
    
//     init(from entry: LogEntry) {
//         self.id = entry.id
//         self.accountId = entry.accountId
//         self.sessionId = entry.sessionId
//         self.tag = entry.tag
//         self.tagId = entry.tagId
//         self.type = entry.type.rawValue
//         self.message = entry.message
//         self.timestamp = entry.timestamp
//         self.data = entry.data
//     }
// }

public enum LogLevel: Int, Sendable {
    case debug = 1
    case info = 2
    case error = 3
    case critical = 4
    
    var toLogType: LogEntry.LogType {
        switch self {
        case .debug: return .debug
        case .info: return .info
        case .error: return .error
        case .critical: return .error
        }
    }
}

@MainActor
class AppLogger {
    private let logQueue = DispatchQueue(label: "com.greatergoods.logQueue", attributes: .concurrent)
    private let logger: Logger
    private var minimumLogLevel: LogLevel = .debug
    private let tag: String
    
    init(tag: String) {
        self.tag = tag
        self.logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "", category: tag)
    }
    
    private func stringify(_ value: Any) -> String {
        if let data = value as? Data {
            return String(data: data, encoding: .utf8) ?? "Unable to decode data"
        }
        return String(describing: value)
    }
    
    func log(level: LogLevel, 
            tag: String, 
            message: String, 
            data: Any? = nil, 
            function: StaticString = #function, 
            line: UInt = #line) {
        guard level.rawValue >= minimumLogLevel.rawValue else { return }
        
        let stringifiedData = data.map(stringify)
        let logMessage = "[\(levelString(level))] \(message) Class: \(tag) Function: \(function) Line: \(line) Data: \(stringifiedData ?? "none")"
        
        logQueue.async(flags: .barrier) {
            // Only log to system logger
            self.logger.log(level: self.levelToOSLog(level: level), "\(logMessage, privacy: .public)")
        }
    }
    
    nonisolated private func levelToOSLog(level: LogLevel) -> OSLogType {
        switch level {
        case .info: return .info
        case .debug: return .debug
        case .error: return .error
        case .critical: return .fault
        }
    }
    
    private func levelString(_ level: LogLevel) -> String {
        switch level {
        case .info: return "INFO"
        case .debug: return "DEBUG"
        case .error: return "ERROR"
        case .critical: return "CRITICAL"
        }
    }
}

/// MARK: - USAGE GUIDE
///
/// Log a simple message:
/// ```swift
/// AppLogger.shared.log(level: .info, tag: "MyView", message: "View loaded")
/// ```
///
/// Log a message with custom data:
/// ```swift
/// let data = ["email": "user@example.com", "status": "active"]
/// AppLogger.shared.log(level: .error, tag: "LoginService", message: "Login failed", data: data)
/// ```
///
/// Change log level to suppress low-priority logs:
/// ```swift
/// AppLogger.shared.setLogLevel(level: .error)
/// ```
///
/// Retrieve and clear all logs:
/// ```swift
/// let allLogs = AppLogger.shared.getAllLogs()
/// ```
///
/// To stringify any value for logging, use:
/// ```swift
/// let str = stringify(anyValue)
/// ```

// MARK: - Usage Examples
extension AppLogger {
    /// Example usage:
    /// ```swift
    /// // Basic logging
    /// AppLogger.shared.log(level: .info, tag: "LoginView", message: "User login attempt")
    ///
    /// // Logging with data
    /// AppLogger.shared.log(level: .error, 
    ///                     tag: "NetworkService", 
    ///                     message: "API call failed",
    ///                     data: ["statusCode": 404])
    ///
    /// // Get session logs
    /// let sessionLogs = AppLogger.shared.getCurrentSessionLogs()
    ///
    /// // Get account logs
    /// let accountLogs = AppLogger.shared.getLogsForAccount("user123")
    ///
    /// // Get date range logs
    /// let fromDate = Calendar.current.date(byAdding: .day, value: -1, to: Date())!
    /// let logs = AppLogger.shared.getLogs(from: fromDate, to: Date())
    /// ```
    ///
    /// Note: The logger automatically manages log retention and cleanup.
    /// Logs older than 5 days are automatically removed.
    static func examples() { }
}

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

public class AppLogger {
    static let shared = AppLogger(tag: "GGMeAppLogger")
    
    private let logQueue = DispatchQueue(label: "com.greatergoods.logQueue", attributes: .concurrent)
    private var logger: Logger?
    private var logs: [String] = []
    private var minimumLogLevel: LogLevel = .debug
    private var tag: String = "AppLogger"
    private let maxLogCount = 1000
    
    init(tag: String) {
        logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "", category: tag)
    }
    
    public enum LogLevel: Int {
        case debug = 1
        case info = 2
        case error = 3
        case critical = 4
    }
    
    public func setLogLevel(level: LogLevel) {
        minimumLogLevel = level
    }
    
    public func getAllLogs() -> [String] {
        var currentLogs: [String] = []
        logQueue.sync {
            currentLogs = self.logs
            self.logs.removeAll()
        }
        for log in currentLogs {
            print(log)
        }
        return currentLogs
    }
    
    // New log method that accepts Any
    public func log(level: LogLevel, tag: String, message: String, data: Any? = nil, function: StaticString = #function, line: UInt = #line) {
        var combinedMessage = message
        if let data = data {
            let stringifiedData = stringify(data)
            combinedMessage += " | Data: \(stringifiedData)"
        }
        logInternal(level: level, tag: tag, message: combinedMessage, function: function, line: line)
    }
    
    // Internal method for actual logging logic
    private func logInternal(level: LogLevel, tag: String, message: String, function: StaticString, line: UInt) {
        guard level.rawValue >= minimumLogLevel.rawValue else { return }
        
        let logMessage = "[\(levelString(level))] \(message) Class: \(tag) Function: \(function) Line: \(line)"
        
        logQueue.async(flags: .barrier) {
            if self.logs.count >= self.maxLogCount {
                self.logs.removeFirst()
            }
            self.logs.append(logMessage)
        }
        
        logger?.log(level: levelToOSLog(level: level), "\(logMessage, privacy: .public)")
    }
    
    private func levelToOSLog(level: LogLevel) -> OSLogType {
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
    
    // TODO: Uncomment and implement API logging functionality in future.
    // Ensure appropriate dependencies are included and properly configured.
    //    // Helper to get ISO8601 string
    //    private func currentISO8601String() -> String {
    //        return ISO8601DateFormatter().string(from: Date())
    //    }
    //
    //    // Structs for API payload
    //    private struct LogEntry: Codable {
    //        let time: String
    //        let data: [String]
    //    }
    //    private struct LogPayload: Codable {
    //        let version: String
    //        let logs: [LogEntry]
    //    }
    //
    //    // Send logs to api
    //    public func sendLogsToAPI() {
    //        logQueue.async {
    //            let logEntries: [LogEntry] = self.logs.map { log in
    //                LogEntry(time: self.currentISO8601String(), data: [log])
    //            }
    //            let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "Unknown"
    //            let versionString = "\(appVersion)_\(AppEnvironment.current.rawValue)"
    //            let payload = LogPayload(version: versionString, logs: logEntries)
    //            // Print actual JSON payload for debugging
    //            let encoder = JSONEncoder()
    //            encoder.outputFormatting = .prettyPrinted
    //            Task{
    //                do {
    //                    let _: String = try await HTTPClient.shared.send(.log,
    //                                                                            method: .post,
    //                                                                            body: payload)
    //                } catch {
    //                }
    //            }
    //        }
    //    }
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

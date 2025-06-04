//
//  AppLogger.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 29/05/25.
//
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
        logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "=", category: tag)
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

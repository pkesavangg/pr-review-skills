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

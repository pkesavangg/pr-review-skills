//
//  LogLevel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 03/06/25.
//


public enum LogLevel: Int, Sendable {
    case debug = 1
    case info = 2
    case error = 3
    case critical = 4
    case success = 5
    
    var toLogType: LogEntry.LogType {
        switch self {
        case .debug: return .debug
        case .info: return .info
        case .error: return .error
        case .critical: return .error
        case .success: return .success
        }
    }
}

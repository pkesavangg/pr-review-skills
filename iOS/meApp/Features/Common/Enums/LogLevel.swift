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

    /// Severity ordering used for console / persistence log-level floors.
    ///
    /// ⚠️ The `rawValue`s are NOT severity-ordered (`success = 5` > `error = 3`),
    /// so any "minimum level" comparison MUST use `severityRank`, never `rawValue`,
    /// or a floor of `.error` would wrongly keep `.success`. Order (low → high):
    /// debug < info < success < error < critical.
    var severityRank: Int {
        switch self {
        case .debug: return 0
        case .info: return 1
        case .success: return 2
        case .error: return 3
        case .critical: return 4
        }
    }
}

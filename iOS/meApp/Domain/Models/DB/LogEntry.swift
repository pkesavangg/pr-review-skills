/// Table: log_entry
///
/// Stores application logs with session tracking and account context.
///
/// | Column Name | Type   | Description                                       |
/// | ----------- | ------ | ------------------------------------------------- |
/// | id          | string | Unique identifier for the log entry (PK)          |
/// | account_id  | string | ID of the account associated with this log        |
/// | session_id  | string | Session identifier, generated on app launch       |
/// | tag         | string | Class name or component that generated the log    |
/// | tag_id      | string | Function or method name that generated the log    |
/// | type        | string | Type of log (i=info, e=error, d=debug, s=success) |
/// | message     | string | Short message describing the log entry            |
/// | timestamp   | long   | Timestamp when the log was created                |
/// | data        | string | Additional data associated with the log entry     |

import Foundation
import SwiftData

@Model
final class LogEntry {
    /// Unique identifier for the log entry
    @Attribute(.unique) var id: String
    /// ID of the account associated with this log
    var accountId: String?
    /// Session identifier, generated on app launch
    var sessionId: String
    /// Class name or component that generated the log
    var tag: String
    /// Function or method name that generated the log
    var tagId: String
    /// Type of log (i=info, e=error, d=debug, s=success)
    var type: LogType
    /// Short message describing the log entry
    var message: String
    /// Timestamp when the log was created
    var timestamp: Int64
    /// Additional data associated with the log entry
    var data: String?
    
    /// Log type enumeration
    enum LogType: String, Codable {
        case info = "i"
        case error = "e"
        case debug = "d"
        case success = "s"
    }
    
    init(id: String = UUID().uuidString,
         accountId: String? = nil,
         sessionId: String,
         tag: String,
         tagId: String,
         type: LogType,
         message: String,
         timestamp: Int64 = DateTimeTools.getCurrentTimestampMillis(),
         data: String? = nil) {
        self.id = id
        self.accountId = accountId
        self.sessionId = sessionId
        self.tag = tag
        self.tagId = tagId
        self.type = type
        self.message = message
        self.timestamp = timestamp
        self.data = data
    }
}

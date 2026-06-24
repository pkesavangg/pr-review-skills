---
name: logging-guide
description: App logging system — LoggerService, log persistence, retention, and server submission. Use when instrumenting code, debugging issues, or configuring log retention.
---

# Logging Guide

This guide explains how logging works in meApp iOS. The logging system is **session-aware**, **account-aware**, and automatically manages log retention. Logs are persisted locally via SwiftData and can be sent to the server for analysis.

---

## Quick Reference

```swift
// 1. Log messages (singleton)
LoggerService.shared.log(
    level: .info,
    tag: "MyFeature",
    message: "User tapped button",
    data: ["userId": "123"]
)

// 2. Fetch logs
let allLogs = try await LoggerService.shared.getAllLogs()
let sessionLogs = try await LoggerService.shared.getCurrentSessionLogs()
let accountLogs = try await LoggerService.shared.getLogsForAccount(accountId)

// 3. Send logs to server
try await LoggerService.shared.sendLogsToServer()

// 4. Manual cleanup
try await LoggerService.shared.deleteOldLogs(30)  // Delete logs older than 30 days
```

---

## Key Types

| Type | Purpose |
|------|---------|
| `LoggerService` | @MainActor singleton — public API for logging and log management |
| `LogLevel` | Enum: `.debug`, `.info`, `.warning`, `.error` — controls persistence and console output |
| `LogEntry` | SwiftData @Model — a single persisted log entry |
| `LoggerRepository` | SwiftData repository — handles local CRUD via background ModelContext |
| `LoggerApiRepository` | API repository — sends log payloads to server |
| `LogsPayload` | DTO — formatted logs for API submission |

---

## Architecture

```
LoggerService (@MainActor singleton)
  ├── LoggerRepository (SwiftData CRUD)
  │   └── ModelContext (background thread)
  ├── LoggerApiRepository (HTTP client)
  │   └── HTTPClient.send(.log, ...)
  └── KvStorageService (retention timestamp tracking)
```

**Data Flow:**
1. Call `LoggerService.shared.log(level:tag:message:data:)`
2. If level is `.debug`, only print to console (no persistence)
3. If level is `.info`/`.warning`/`.error`, capture account ID on main thread
4. Offload to background queue for string formatting
5. Bounce back to main actor to create and save `LogEntry` via `LoggerRepository`
6. Repository inserts into SwiftData on background ModelContext

---

## Logging Patterns

### Basic Logging

```swift
// Info-level log (persisted + console)
LoggerService.shared.log(
    level: .info,
    tag: "EntryStore",
    message: "Weight entry saved successfully"
)

// Warning-level log with context data
LoggerService.shared.log(
    level: .warning,
    tag: "BluetoothService",
    message: "Scale disconnected unexpectedly",
    data: ["deviceId": "12345", "duration": "42s"]
)

// Error-level log
LoggerService.shared.log(
    level: .error,
    tag: "AccountService",
    message: "Failed to fetch account",
    data: error.localizedDescription
)

// Debug-level log (console only, NOT persisted)
LoggerService.shared.log(
    level: .debug,
    tag: "DebugView",
    message: "Form state: \(formState)"
)
```

### Per-Account and Per-Session Logging

```swift
// Log for specific account (not current active account)
LoggerService.shared.log(
    level: .info,
    tag: "AccountMigration",
    message: "Migrating account settings",
    accountId: "other-account-id"  // Optional: defaults to active account
)

// Get current session ID
let sessionId = LoggerService.shared.getCurrentSessionId()
print("Session: \(sessionId)")  // UUID persists for lifetime of app session

// Get logs for current session
let sessionLogs = try await LoggerService.shared.getCurrentSessionLogs()

// Get logs for specific account
let accountLogs = try await LoggerService.shared.getLogsForAccount("account-id")
```

### Fetching Logs

```swift
// Get all logs
let allLogs = try await LoggerService.shared.getAllLogs()

// Get logs for current session
let sessionLogs = try await LoggerService.shared.getCurrentSessionLogs()

// Get logs for specific account
let accountLogs = try await LoggerService.shared.getLogsForAccount("account-id")

// Get logs within a date range
let from = Calendar.current.date(byAdding: .day, value: -7, to: Date())!
let logs = try await LoggerService.shared.getLogs(from: from, to: Date())
```

### Sending Logs to Server

```swift
// Send logs for current account to server
do {
    try await LoggerService.shared.sendLogsToServer()
    // Logs automatically deleted after successful upload
} catch {
    LoggerService.shared.log(level: .error, tag: "LogUpload", message: "Failed: \(error)")
}

// Send logs for specific account
try await LoggerService.shared.sendLogsToServer(accountId: "custom-id")

// Send scale device logs
let scaleLogs = [DeviceLogEntry(...)]
try await LoggerService.shared.sendScaleLogsToServer(deviceLogs: scaleLogs)
```

### Cleanup and Retention

```swift
// Delete logs for specific account
try await LoggerService.shared.deleteLogsForAccount("account-id")

// Delete all logs
try await LoggerService.shared.deleteAllLogs()

// Delete old logs (older than retention period in days)
try await LoggerService.shared.deleteOldLogs(30)  // Default: AppConstants.TimeoutsAndRetention.logRetentionDays
```

---

## Error Handling Pattern

```swift
// In stores and services, wrap logging in error handler
func doSomethingRisky() async throws {
    do {
        let result = try await apiCall()
        LoggerService.shared.log(
            level: .info,
            tag: "MyStore",
            message: "Operation succeeded",
            data: ["result": String(describing: result)]
        )
    } catch {
        LoggerService.shared.log(
            level: .error,
            tag: "MyStore",
            message: "Operation failed",
            data: error.localizedDescription
        )
        throw error  // Re-throw after logging
    }
}
```

---

## Golden Rules

### ✅ Do

- **Use appropriate log levels:** `.info` for normal flow, `.warning` for unexpected but recoverable, `.error` for failures
- **Tag by feature:** Use meaningful tags like "EntryStore", "BluetoothService", "AccountMigration"
- **Log before async boundaries:** Capture account ID and context on main actor *before* awaiting
- **Include structured data:** Use dictionaries or strings for context — helps with server analysis
- **Let retention run automatically:** LoggerService auto-cleans old logs at launch (once per day)
- **Send logs to server periodically:** Call `sendLogsToServer()` in background or after major events
- **Debug logs for development:** Use `.debug` level for temporary instrumentation (not persisted)

### ❌ Never

- **Log passwords, tokens, or PII:** Only log high-level context; redact sensitive fields
- **Log at `.debug` expecting persistence:** Debug logs are console-only by design
- **Block main actor on log save:** LoggerService offloads persistence to background queue automatically
- **Ignore log errors:** Wrap server uploads in try-catch; failures are best-effort
- **Override log retention too aggressively:** 30+ days is safe; shorter windows may lose critical context
- **Send unformatted error objects:** Convert to string first — `error.localizedDescription`

---

## Key Implementation Details

### Log Levels

| Level | Persisted? | Console | Use Case |
|-------|-----------|---------|----------|
| `.debug` | ❌ | ✅ | Development-only instrumentation |
| `.info` | ✅ | ✅ | Normal flow milestones |
| `.warning` | ✅ | ✅ | Unexpected but recoverable events |
| `.error` | ✅ | ✅ | Failures and exceptions |

### Background Processing

LoggerService uses a concurrent DispatchQueue (`com.greatergoods.loggerServiceQueue`) to:
- Stringify `data` parameters (safe, no side effects)
- Format log entries
- Schedule SwiftData saves

This prevents main-actor blocking even on heavy logging.

### Session ID

Each LoggerService instance generates a UUID at init. The session ID:
- Persists for the lifetime of the app session
- Is attached to all logs
- Helps correlate logs across server logs

### Auto-Cleanup

LoggerService schedules background cleanup at launch:
- Runs at most once per day (tracked in KvStorage)
- Checks if old logs exist before deleting
- Deletes in **batches** (400 rows at a time) to avoid CPU spikes
- Delays between batches to yield CPU
- Silently ignores errors (best-effort retention)

### Account Awareness

When logging, LoggerService resolves the account ID:
1. Use explicit `accountId` parameter if provided
2. Otherwise, use `activeAccount?.accountId` from AccountService
3. If neither available, log with `accountId = nil`

This enables per-account log filtering and deletion.

---

## File Structure

```
meApp/
├── Core/Services/
│   └── LoggerService.swift                    # Public API
├── Data/Storage/DB/
│   └── LoggerRepository.swift                 # SwiftData CRUD
└── Data/API/
    └── LoggerApiRepository.swift              # HTTP submission

Domain/Models/
├── API/
│   └── LogsPayload.swift                      # API DTO
└── DB/
    └── LogEntry.swift                         # SwiftData @Model
```

---

## Common Patterns in Codebase

### Logging in Stores

```swift
@MainActor final class MyStore: ObservableObject {
    @Injector var myService: MyServiceProtocol
    
    func doWork() async {
        LoggerService.shared.log(
            level: .info,
            tag: "MyStore",
            message: "Starting work"
        )
        
        do {
            let result = try await myService.fetchData()
            LoggerService.shared.log(
                level: .info,
                tag: "MyStore",
                message: "Work completed",
                data: ["count": result.count]
            )
        } catch {
            LoggerService.shared.log(
                level: .error,
                tag: "MyStore",
                message: "Work failed: \(error.localizedDescription)"
            )
        }
    }
}
```

### Logging in Services

```swift
@MainActor final class MyService: MyServiceProtocol {
    func fetchData() async throws -> [Data] {
        LoggerService.shared.log(level: .debug, tag: "MyService", message: "Fetch started")
        
        let result = try await apiCall()
        
        LoggerService.shared.log(
            level: .info,
            tag: "MyService",
            message: "Fetch succeeded",
            data: ["itemCount": result.count]
        )
        
        return result
    }
}
```

### Logging Critical Paths

For critical failures (auth, account changes, sync issues), log at `.error` and consider uploading immediately:

```swift
func criticalOperation() async throws {
    do {
        try await risky()
    } catch {
        LoggerService.shared.log(
            level: .error,
            tag: "CriticalOp",
            message: "Critical failure",
            data: error.localizedDescription
        )
        // Consider uploading logs immediately for critical events
        try? await LoggerService.shared.sendLogsToServer()
        throw error
    }
}
```

---

## Related Skills

| Skill | Purpose |
|-------|---------|
| `/analytics` | Structured logging + Crashlytics integration |
| `/debug-issue` | Use logging to investigate bugs systematically |
| `/review-security` | Check logs don't contain sensitive data |

---

## Configuration

Log retention is configured in `AppConstants.TimeoutsAndRetention`:

```swift
public struct TimeoutsAndRetention {
    public static let logRetentionDays = 30  // Default: 30 days
}
```

Adjust if longer or shorter retention is needed.

---

## Testing

When testing code that logs:

```swift
// Create LoggerService with test dependencies
let testKv = KvStorageService()
let testLogger = LoggerService(
    loggerRepository: MockLoggerRepository(),
    loggerApiRepository: MockLoggerApiRepository(),
    skipCleanup: true  // Disable auto-cleanup in tests
)

// Then verify logging calls
let logs = try await testLogger.getAllLogs()
#expect(logs.count == expectedCount)
```


---
name: analytics
description: Add structured logging/instrumentation to a store, service, or repository using the project's LoggerService pattern. Use when the user says "add logging for X", "instrument this flow", "log this event", "add analytics for Y", or when a new store method has no observability. Also use when reviewing a new store that is missing lifecycle logs.
---

Add structured logging to a store, service, or repository using the project's `LoggerService` pattern.

The flow or method to instrument is: $ARGUMENTS

## Background

This project uses `LoggerService` as its instrumentation layer — there is no separate analytics SDK. Logs are persisted via SwiftData (except `.debug` level), retrievable per account/session, and uploadable to the server for support diagnostics.

Key files:
- Protocol: `meApp/Core/Services/LoggerServiceProtocol.swift`
- Implementation: `meApp/Data/Services/LoggerService.swift`
- Log levels: `meApp/Features/Common/Enums/LogLevel.swift`

## Instructions

### 1 — Locate the Target File

If `$ARGUMENTS` is a class name, find it:

```bash
rg -l "$ARGUMENTS" meApp/Features meApp/Data -g '*.swift' | head -5
```

Read the file. Identify:
- Whether `logger` is already injected (`@Injector var logger: LoggerServiceProtocol`)
- Whether a `tag` constant is already defined (`private let tag = "ClassName"`)
- Which methods are missing log calls (look for async methods with no `logger.log` calls)

---

### 2 — Add Logger Injection (if missing)

Add at the top of the class body, grouped with other `@Injector` properties:

```swift
@Injector var logger: LoggerServiceProtocol
```

Add the tag constant directly below:

```swift
private let tag = "ExactClassName"  // Use the exact Swift class name
```

Do not add an `import` statement — `LoggerService` resolves via `@Injector`, no import needed.
Do not call `/wire-service` — `LoggerService` is registered as an essential service at app launch.

---

### 3 — Apply the Logging Pattern

#### Level guide

| Level | Persisted | When to use |
|---|---|---|
| `.debug` | No (console only) | Verbose dev context, input values before validation |
| `.info` | Yes | Start of a significant operation |
| `.success` | Yes | Successful completion of an operation |
| `.error` | Yes | Caught errors, guard failures, unexpected nil |

#### Standard pair pattern — every async method gets a start + completion log

```swift
func fetchEntries(for accountId: String) async throws -> [Entry] {
    logger.log(level: .info, tag: tag, message: "fetchEntries started. accountId=\(accountId)")
    do {
        let entries = try await entryService.getEntries(for: accountId)
        logger.log(level: .success, tag: tag, message: "fetchEntries succeeded. count=\(entries.count)")
        return entries
    } catch {
        logger.log(level: .error, tag: tag, message: "fetchEntries failed. error=\(error.localizedDescription)")
        throw error
    }
}
```

#### Structured data pattern — use `data:` for key-value context

```swift
logger.log(
    level: .info,
    tag: tag,
    message: "saveEntry started",
    data: "accountId=\(accountId) weight=\(weight) unit=\(unit)"
)
```

#### Guard / validation failure pattern

```swift
guard let account = activeAccount else {
    logger.log(level: .error, tag: tag, message: "saveEntry failed. reason=no active account")
    return
}
```

---

### 4 — Rules

- Keep messages short and machine-parseable: `"<action> <state>. <key>=<value> <key>=<value>"`
- Always log errors **before** rethrowing — `throw error` must come after the `.error` log call
- Do NOT log sensitive data: auth tokens, passwords, full email addresses, raw weight arrays
- Log IDs (`accountId`, `entryId`) and counts — not PII content fields
- `.debug` is safe for verbose output — it is never persisted, console-only
- Do not add `data:` if the values would contain sensitive fields

---

### 5 — Verify Against Existing Patterns

Before finishing, check a nearby store for reference to confirm call style matches:

```bash
rg -n "logger.log" meApp/Features/Entry/Stores -g '*.swift'
```

---

### 6 — Report

```
Logging added to: {ClassName}
Methods instrumented: {list}
Log calls added: {count} (info: N, success: N, error: N, debug: N)
Sensitive data avoided: confirmed
```

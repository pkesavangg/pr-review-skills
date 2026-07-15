---
name: logging-guide
description: Add logging to meApp Android code using AppLog (never android.util.Log). Use when instrumenting a flow, debugging, or when a review finds a raw Log call. Covers levels, tags, and the no-PII rule.
---

Log via `AppLog` — never `android.util.Log` directly.

The logging task is: $ARGUMENTS

## Instructions

### 1 — Use AppLog
- Find `AppLog` and match its call signature (level + tag + message).
- Pick the right level: verbose/debug for dev detail, info for lifecycle, warn for recoverable issues, error for failures.

### 2 — Rules
- **Never** call `Log.d/i/e(...)` directly — always `AppLog`.
- **No PII / health data / tokens** in log messages (names, emails, weights, credentials). Log ids/enums/counts, not payloads.
- Keep messages actionable: what happened + relevant identifiers, not entire objects.

### 3 — Where to log
- ViewModel/service lifecycle transitions, network/DB failures (in `catch`), and unexpected branches.
- Don't log inside tight recomposition/loops.

### 4 — Verify
```bash
rg -n "\bLog\.(d|e|i|w|v)\(" Android/app/src/main -g '*.kt'   # should not include your new code
cd Android && ./gradlew assembleDebug
```

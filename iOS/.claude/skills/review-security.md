Review the PR diff for Swift/SwiftUI security concerns.

Inputs available: PR_META (number, title, branch), DIFF (full patch text), CHANGED_FILES (list), WORKTREE_PATH

## Instructions

Work through each category below against the diff (`+` lines = new code introduced by the PR).

---

### 1. Hardcoded Secrets / API Keys

Scan new lines in the diff for patterns that suggest embedded credentials:
- String literals matching patterns: `apiKey`, `secret`, `password`, `token`, `Bearer`, `Basic `, AWS/Firebase key patterns
- Long alphanumeric strings (32+ chars) assigned to constants
- Any key that looks like it belongs in environment config but is hardcoded in source

If found: report file path, line, and the type of secret. Flag as **FAIL**.
If none found: **PASS**.

---

### 2. Force Unwrap / Force Try / Force Cast

Scan new `+` lines for:
- Force unwrap: trailing `!` on an optional expression (not `!=`)
- Force try: `try!`
- Force cast: `as!`

For each occurrence, note the file, line, and whether a safe alternative (`guard let`, `if let`, `try?`, `as?`) is feasible.
- New occurrences in production code: **WARNING** (or **FAIL** if in a critical path like auth, data persistence)
- New occurrences in test helpers/fixtures: **PASS** (acceptable)

---

### 3. Sensitive Data in Keychain vs UserDefaults / SwiftData

Read `meApp/Core/Config/AppConstants.swift` and `meApp/Domain/Repositories/` to understand what counts as sensitive (auth tokens, refresh tokens, passwords, user PII).

Check new `+` lines for:
- Sensitive fields being written to `UserDefaults`
- Sensitive fields stored as `@Model` properties in SwiftData without encryption
- Auth tokens or passwords being passed outside `KeychainService`

Flag as **FAIL** if sensitive data bypasses `KeychainService`.

---

### 4. Insecure HTTP URLs

Scan new `+` lines for hardcoded `http://` URLs (not `https://`).
- Exception: URLs inside `#if DEBUG`, Dev-only config blocks, or `AppEnvironment.dev` blocks.
- Any `http://` in production code paths: **FAIL**

---

### 5. Sensitive Data in Logs

Scan new `+` lines for:
- `print(`, `debugPrint(`, `NSLog(`, `Logger.` calls that include auth tokens, passwords, email, weight/health data, or other PII fields
- `dump(` on model objects containing sensitive fields

Flag each as **WARNING** — logging sensitive data can expose it in device consoles and crash reports.

---

### 6. Actor Isolation & Concurrency Safety

Scan new `+` lines for:
- `Task.detached {` capturing `self` — check if `self` is a `@MainActor` type; detached tasks run off the main actor
- `nonisolated` added to a property/method on a `@MainActor` type that touches UI state
- `DispatchQueue.main.async` mixed with `await` in the same flow (legacy + modern concurrency mixing)
- `@Sendable` closures capturing mutable reference types

Flag as **WARNING** with explanation of the race risk.

---

### 7. Input Sanitization

Scan new `+` lines for:
- User-supplied strings interpolated directly into URL paths (URL injection risk)
- User-supplied strings used in SwiftData predicates or `NSPredicate` (predicate injection risk)
- File paths constructed from user input without sanitization

Flag as **WARNING** or **FAIL** depending on severity.

---

## Output

Report each category with its status and findings:

```
### Security Review

| Category | Status | Notes |
|----------|--------|-------|
| Hardcoded Secrets | PASS / WARNING / FAIL | … |
| Force Unwrap/Try/Cast | PASS / WARNING / FAIL | … |
| Keychain Usage | PASS / WARNING / FAIL | … |
| Insecure HTTP | PASS / WARNING / FAIL | … |
| Log Exposure | PASS / WARNING / FAIL | … |
| Actor Isolation | PASS / WARNING / FAIL | … |
| Input Sanitization | PASS / WARNING / FAIL | … |

**Security verdict:** PASS / WARNING / FAIL

Findings:
- [file:line] Description of issue and recommended fix
```

Overall verdict rules:
- Any **FAIL** → Security verdict = FAIL
- Any **WARNING**, no FAIL → Security verdict = WARNING
- All **PASS** → Security verdict = PASS

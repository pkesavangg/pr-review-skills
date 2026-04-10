---
name: review-security
description: Review Swift/SwiftUI code changes for security issues — hardcoded secrets, force unwrap in critical paths, tokens stored outside Keychain, insecure HTTP, PII in logs, actor isolation bugs, and input sanitisation. Use when reviewing a PR for security, or when the user says "security review", "check for secrets", "security audit". Also called automatically by /self-review and /review-pr.
---

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

### 2b. Silent Error Swallowing (`try?`)

Scan new `+` lines in `Service`, `Store`, `API`, and `Repository` files for `try?` usages where **all of the following are true**:

1. The call is in a production service/store/API file (not a View, test, or pure UI helper)
2. There is no `else { logger.log(level: .error, ...) }` or `else { LoggerService... }` branch following the `try?`
3. The function's behavior visibly degrades when the call fails (e.g., returns an empty array, skips a permissions step, silently no-ops, fails to expand HealthKit authorization)

**Safe `try?` patterns — do NOT flag:**
- `try?` followed by explicit `else { logger.log(...) }` error handling
- `try?` in View files or pure UI helpers where nil → empty state is intentional by design
- `try?` on cleanup/delete operations where failure is non-critical (e.g. `try? modelContext.delete(item)`)

For each flagged occurrence:
- Report: `[High] Silent error swallow — try? at {file}:{line} suppresses {ServiceName} errors. If {service} throws ({error scenario}), {describe user-visible degradation}. Replace with do/catch + logger.log(level: .error, tag: tag, message: ...)`

Flag as **WARNING** in non-critical paths, **FAIL** if the suppressed error affects auth, HealthKit permissions, device pairing, or data persistence.

---

### 3. Sensitive Data in Keychain vs UserDefaults / SwiftData

Sensitive data includes: auth tokens, refresh tokens, passwords, email addresses, weight/health metrics, and any user PII.

Check new `+` lines for:
- Sensitive fields being written to `UserDefaults`
- Sensitive fields stored as `@Model` properties in SwiftData without encryption
- Auth tokens or passwords being passed outside `KeychainService`

Flag as **FAIL** if sensitive data bypasses `KeychainService`.

**For comprehensive storage decision logic, classification of sensitive vs non-sensitive data, and implementation patterns, reference the `/keychain-pattern` skill.** It includes:
- Storage decision tree (Keychain vs SwiftData vs KvStorage)
- Tier 1-3 sensitive data classification
- ✅ correct and ❌ wrong implementation patterns
- Multi-account Keychain access patterns
- Security checklist for code review

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
| Silent Error Swallowing (try?) | PASS / WARNING / FAIL | … |
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

---
name: review-code-standards
description: Audit new/changed Swift code against the project's architecture and code conventions from CLAUDE.md — DI patterns, feature structure, store conventions, string centralisation, logging, API repository pattern, protocol naming, and concurrency rules. Use when reviewing a PR for code standards, or when the user says "code standards review", "architecture review", "check conventions". Also called automatically by /review-pr.
---

Audit new and changed code against the project's architecture and code conventions.

Inputs available: PR_META (number, title, branch), DIFF (full patch text), CHANGED_FILES (list), WORKTREE_PATH

## Instructions

Read the project CLAUDE.md files for reference, then work through each category below against the changed files. For modified files, focus on the **new `+` lines in the diff**. For new files, audit the entire file.

---

### 1 — Dependency Injection

Scan all new `+` lines and new files for:

- **Direct `.shared` singleton access in views, stores, or components** (e.g. `SomeService.shared`, `SomeStore.shared`)
  - Views and stores must use `@Injector` property wrapper or constructor injection
  - `.shared` is only acceptable inside `ServiceRegistry` registration and within the service's own `init`
- **`@Injector` injecting concrete types instead of protocols** (e.g. `@Injector var service: EntryService` instead of `EntryServiceProtocol`)
  - All `@Injector` usages should reference the protocol type for testability
- **Missing DI double-registration** — if a new service is created, check `ServiceRegistry` for both concrete and protocol registration

Flag `.shared` in views/stores as **FAIL**.
Flag concrete `@Injector` types as **WARNING**.
Flag missing DI registration as **FAIL**.

---

### 2 — Feature Structure

For each new feature directory added, verify it follows the expected convention:

```
Features/{Feature}/
├── Routes/       # Navigation enum (Routable) — optional if reusing parent routes
├── Stores/       # @MainActor ObservableObject state managers — optional if reusing parent store
├── Forms/        # Reactive form validation — optional
├── Views/
│   ├── Screens/  # Root screen views
│   └── Components/
├── Strings/      # PascalCase string constants
└── Enums/        # Feature-specific enums — optional
```

Flag missing `Views/Screens/` or `Views/Components/` as **WARNING**.
Flag missing `Strings/` (when hardcoded strings exist) as **FAIL**.

---

### 3 — Store Convention

For each new or modified store/manager class:

- Must be `@MainActor final class` with `@Published` properties
- Must conform to `ObservableObject` if used as `@ObservedObject`/`@StateObject`

Flag missing `final` as **WARNING**.
Flag missing `@MainActor` on stores that publish UI state as **FAIL**.

---

### 4 — String Conventions

Scan new `+` lines in view files (`*View.swift`, `*Screen.swift`, `*Sheet.swift`, `*Card.swift`) for:

- **Hardcoded user-facing string literals** (e.g. `Text("Some label")`) that are NOT referencing a `Strings` constant
- Acceptable exceptions: SF Symbol names, format specifiers, accessibility identifiers, debug strings

Check that each feature has a corresponding `Strings/` folder with PascalCase structs for all static text.

Flag hardcoded user-facing strings as **WARNING** (or **FAIL** if >3 instances in a single file).

---

### 5 — Logging

Scan new `+` lines for:

- `print(` — should use `LoggerService`
- `debugPrint(` — should use `LoggerService`
- `NSLog(` — should use `LoggerService`
- `dump(` on production code paths — should use `LoggerService`

Flag any occurrence in non-test, non-preview code as **FAIL**.

---

### 6 — API Repository Pattern

If any file under `Data/API/` or named `*RepositoryAPI.swift` is in CHANGED_FILES:

- Verify it only contains network calls (HTTP requests via `HTTPClient`)
- Flag business logic, caching, data transformation, or state management in API repositories as **FAIL**
- Services (`Data/Services/`) should handle all business logic

---

### 7 — Protocol Naming

For any new protocol defined in the diff:

- Must use `Protocol` suffix (e.g. `EntryServiceProtocol`)
- OR descriptive `-ing`/`-able` suffix for capability protocols (e.g. `DashboardChartManaging`, `Routable`)
- Must NOT use `I` prefix (that's the Android convention)

Flag violations as **WARNING**.

---

### 8 — Concurrency

Scan new `+` lines for:

- **SwiftData `@Model` classes marked as `Sendable`** or passed across actor boundaries → **FAIL**
- **`DispatchQueue.main.async` mixed with `await`** in the same flow → **WARNING**
- **`Task.detached` capturing `@MainActor` types strongly** (without `[weak self]`) → **WARNING**
- **`nonisolated` on `@MainActor` type** that accesses mutable state → **FAIL** (pure functions are acceptable)
- **Missing `@MainActor`** on SwiftData CRUD operations → **WARNING**

---

## Output

```
### Code Standards Review

| Category | Status | Notes |
|----------|--------|-------|
| Dependency Injection | PASS / WARNING / FAIL | … |
| Feature Structure | PASS / WARNING / FAIL | … |
| Store Convention | PASS / WARNING / FAIL | … |
| String Conventions | PASS / WARNING / FAIL | … |
| Logging | PASS / WARNING / FAIL | … |
| API Repository Pattern | PASS / WARNING / FAIL / N/A | … |
| Protocol Naming | PASS / WARNING / FAIL | … |
| Concurrency | PASS / WARNING / FAIL | … |

**Code standards verdict:** PASS / WARNING / FAIL

Findings:
- [file:line] Description of violation and recommended fix
```

Overall verdict rules:
- Any **FAIL** → Code standards verdict = FAIL
- Any **WARNING**, no FAIL → Code standards verdict = WARNING
- All **PASS** → Code standards verdict = PASS

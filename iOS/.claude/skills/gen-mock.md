Generate a mock class for a Swift protocol, following the project's exact mock conventions.

The protocol to mock is: $ARGUMENTS

## Instructions

### 1 — Locate the Protocol

If `$ARGUMENTS` is a file path, read it directly.
If it's a protocol name (e.g. `EntryServiceProtocol`), search for it:
```bash
grep -r "protocol $ARGUMENTS" /Users/kesavan/meApp-1/iOS/meApp --include="*.swift" -l
```
Read the file and extract the full protocol definition.

---

### 2 — Detect the Mock Pattern

**Pattern A — Service Mock** (use for `*ServiceProtocol`):
- All async throwing methods use `Result<T, Error>` stubs
- Default stub value is `.failure(UnexpectedCallError.methodCalled("methodName"))`
- Exception: methods that are clearly non-critical convenience reads may use `throw UnexpectedCallError.methodCalled("methodName")` directly without a stub property

**Pattern B — Repository Mock** (use for `*RepositoryProtocol`):
- Has an in-memory backing collection (e.g. `var entries: [Entry] = []`)
- Write methods (`save`, `update`, `delete`) use `var methodError: Error?` — throw it if set
- Read methods return filtered slices of the backing collection directly
- No `Result` stubs for read methods

**Pattern C — API Repository Mock** (use for `*RepositoryAPIProtocol` or `*APIRepositoryProtocol`):
- Uses injected `HTTPClientProtocol` — do NOT create one of these manually; use `MockHTTPClient` instead
- Only create this pattern if explicitly asked

---

### 3 — Determine Placement

| Protocol lives in | Mock goes in |
|-------------------|-------------|
| `Domain/Services/` or is used across multiple features | `meAppTests/Support/Mocks/Services/` |
| `Domain/Repositories/` and used by a single feature | `meAppTests/Features/<Feature>/Mocks/` |
| Unclear | `meAppTests/Features/<Feature>/Mocks/` matching the feature closest to the protocol |

---

### 4 — Generate the Mock

**Structure for Pattern A (Service):**
```swift
import Foundation
@testable import meApp

@MainActor
final class Mock<ProtocolName>: <ProtocolName> {

    // MARK: - Stubs
    var <method>Result: Result<<ReturnType>, Error> = .failure(UnexpectedCallError.methodCalled("<method>"))
    // For Void: var <method>Result: Result<Void, Error> = .failure(UnexpectedCallError.methodCalled("<method>"))

    // MARK: - Call Tracking
    private(set) var <method>Calls = 0
    private(set) var last<Method>Arg1: <ArgType1>?
    private(set) var last<Method>Arg2: <ArgType2>?

    // MARK: - <ProtocolName>
    func <method>(<args>) async throws -> <ReturnType> {
        <method>Calls += 1
        last<Method>Arg1 = arg1
        last<Method>Arg2 = arg2
        return try <method>Result.get()
    }

    // For Void return:
    func <method>(<args>) async throws {
        <method>Calls += 1
        last<Method>Arg = arg
        _ = try <method>Result.get()
    }

    // For non-throwing Void (no stub needed, just throw):
    func <method>(<args>) {
        throw UnexpectedCallError.methodCalled("<method>")
    }
}
```

**Structure for Pattern B (Repository):**
```swift
import Foundation
@testable import meApp

@MainActor
final class Mock<ProtocolName>: <ProtocolName> {

    // MARK: - In-Memory State
    var items: [<ModelType>] = []

    // MARK: - Error Stubs (write operations only)
    var saveError: Error?
    var updateError: Error?
    var deleteError: Error?

    // MARK: - Call Tracking
    private(set) var saveCalls = 0
    private(set) var updateCalls = 0
    private(set) var deleteCalls = 0
    private(set) var lastSavedItem: <ModelType>?
    private(set) var lastUpdatedItem: <ModelType>?
    private(set) var lastDeletedId: String?

    // MARK: - <ProtocolName>
    func save(_ item: <ModelType>) async throws {
        saveCalls += 1
        lastSavedItem = item
        if let saveError { throw saveError }
        items.removeAll { $0.id == item.id }
        items.append(item)
    }

    func fetchAll() async throws -> [<ModelType>] {
        items
    }

    func delete(byId id: String) async throws {
        deleteCalls += 1
        lastDeletedId = id
        if let deleteError { throw deleteError }
        items.removeAll { $0.id.uuidString == id }
    }
}
```

**Rules:**
- Import `Combine` only if the protocol uses `@Published` / `Publisher` properties
- Include `@Published` backing properties if the protocol declares published vars
- Group with `// MARK: - Stubs`, `// MARK: - Call Tracking`, `// MARK: - <ProtocolName>`
- Use `private(set)` on all call tracking properties
- Name the file `Mock<ProtocolNameWithoutProtocol>.swift`
  - e.g. `EntryServiceProtocol` → `MockEntryService.swift`

---

### 5 — Write the File

Write the mock to the determined path. Confirm the full file path to the caller.

If this was called from `gen-test-file`, return the mock class name and file path
so the test file can import it correctly.

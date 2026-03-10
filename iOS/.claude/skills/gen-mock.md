---
name: gen-mock
description: Generate a mock class for a Swift protocol following the project's exact mock conventions. Use when the user says "generate a mock", "create a mock for", "mock this protocol", or when a test file needs a mock that doesn't exist yet. Also called automatically by /gen-test-file when required mocks are missing.
---

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

### 2 — Choose the Mock Pattern

**Pattern A — Service Mock** (`*ServiceProtocol`):
- Async throwing methods use `Result<T, Error>` stubs defaulting to `.failure(UnexpectedCallError.methodCalled("methodName"))`
- Track all calls with `private(set) var <method>Calls = 0` and capture last arguments

**Pattern B — Repository Mock** (`*RepositoryProtocol`):
- In-memory backing collection (e.g. `var items: [Entry] = []`)
- Write methods (`save`, `update`, `delete`) have `var saveError: Error?` — throw it if set
- Read methods return slices of the backing collection directly — no `Result` stubs

**Pattern C — API Repository Mock** (`*RepositoryAPIProtocol`):
- These are tested via `MockHTTPClient`, not a hand-written mock. Do NOT create one unless explicitly asked.

---

### 3 — Determine Placement

| Protocol lives in | Mock goes in |
|-------------------|-------------|
| `Domain/Services/` or used across multiple features | `meAppTests/Support/Mocks/Services/` |
| `Domain/Repositories/` and used by a single feature | `meAppTests/Features/<Feature>/Mocks/` |
| Unclear | `meAppTests/Features/<Feature>/Mocks/` nearest to the protocol |

---

### 4 — Generate the Mock

Apply the chosen pattern to the actual protocol methods. The structures below are reference templates — adapt them to the real protocol, don't copy them verbatim.

**Pattern A (Service) — reference:**
```swift
import Foundation
@testable import meApp

@MainActor
final class MockFooService: FooServiceProtocol {

    // MARK: - Stubs
    var fetchResult: Result<Foo, Error> = .failure(UnexpectedCallError.methodCalled("fetch"))

    // MARK: - Call Tracking
    private(set) var fetchCalls = 0
    private(set) var lastFetchId: String?

    // MARK: - FooServiceProtocol
    func fetch(id: String) async throws -> Foo {
        fetchCalls += 1
        lastFetchId = id
        return try fetchResult.get()
    }
}
```

**Pattern B (Repository) — reference:**
```swift
import Foundation
@testable import meApp

@MainActor
final class MockFooRepository: FooRepositoryProtocol {

    // MARK: - In-Memory State
    var items: [Foo] = []

    // MARK: - Error Stubs
    var saveError: Error?
    var deleteError: Error?

    // MARK: - Call Tracking
    private(set) var saveCalls = 0
    private(set) var deleteCalls = 0

    // MARK: - FooRepositoryProtocol
    func save(_ item: Foo) async throws {
        saveCalls += 1
        if let saveError { throw saveError }
        items.removeAll { $0.id == item.id }
        items.append(item)
    }

    func fetchAll() async throws -> [Foo] { items }

    func delete(byId id: String) async throws {
        deleteCalls += 1
        if let deleteError { throw deleteError }
        items.removeAll { $0.id == id }
    }
}
```

**Additional rules:**
- Import `Combine` only if the protocol uses `@Published` / `Publisher` properties
- Use `private(set)` on all call tracking properties
- Name the file `Mock<ProtocolNameWithoutSuffix>.swift` (e.g. `FooServiceProtocol` → `MockFooService.swift`)

---

### 5 — Write the File

Write the mock to the determined path. Confirm the full file path to the caller.

If called from `gen-test-file`, return the mock class name and file path so the test file can reference it correctly.

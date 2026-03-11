---
name: gen-mock-single
description: Generate a mock class for a single Swift protocol following the project's exact mock conventions. Use when the user says "generate a mock", "create a mock for", "mock this protocol", or when a test file needs a mock that doesn't exist yet. Also called automatically by /gen-test-file when required mocks are missing.
---

Generate a mock class for a Swift protocol, following the project's exact mock conventions.

The protocol to mock is: $ARGUMENTS

## Instructions

### 1 — Locate the Protocol

If `$ARGUMENTS` is a file path, read it directly.
If it's a protocol name (e.g. `EntryServiceProtocol`), search for it:
```bash
rg -l "protocol $ARGUMENTS" meApp -g '*.swift'
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

Apply the chosen pattern to the actual protocol methods. Key structure per pattern:

| Component | Pattern A (Service) | Pattern B (Repository) |
|-----------|--------------------|-----------------------|
| Imports | `Foundation`, `@testable import meApp` | same |
| Class attrs | `@MainActor final class Mock<Name>: <Protocol>` | same |
| Stubs | `var <method>Result: Result<T, Error> = .failure(UnexpectedCallError.methodCalled("<method>"))` | `var <method>Error: Error?` per mutating method |
| State | — | `var items: [T] = []` (in-memory backing) |
| Call tracking | `private(set) var <method>Calls = 0` + `private(set) var last<Arg>: T?` | same for mutating methods |
| Impl | `<method>Calls += 1; capture args; return try <method>Result.get()` | `<method>Calls += 1; if let err { throw err }; mutate items` |
| Read methods | uses `Result` stub | returns slice of `items` directly — no `Result` |

**Additional rules:**
- Import `Combine` only if the protocol uses `@Published` / `Publisher` properties
- Use `private(set)` on all call tracking properties
- Name the file `Mock<ProtocolNameWithoutSuffix>.swift` (e.g. `FooServiceProtocol` → `MockFooService.swift`)

---

### 5 — Write the File

Write the mock to the determined path. Confirm the full file path to the caller.

If called from `gen-test-file`, return the mock class name and file path so the test file can reference it correctly.

---

### Follow-Up

If called standalone (not from `/gen-test-file`):
- Run `/gen-test-file` to scaffold a test file that uses this mock
- Run `/verify-tests` after writing tests to confirm coverage meets the layer threshold

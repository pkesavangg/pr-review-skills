---
name: gen-test-file
description: Scaffold a complete unit test file for a Service, Repository, or Store using Swift Testing. Use when the user says "generate tests for X", "add unit tests", or "write a test file for Y". Also called automatically by /work-ticket Step 4.
---

Scaffold a complete unit test file for a Swift Service, Repository, or Store class.

The source file to generate tests for is: $ARGUMENTS

## Instructions

### 1 — Read the Source File

Read the file at `$ARGUMENTS`. If only a class name was given, search for it:
```bash
rg -l "$ARGUMENTS" meApp -g '*.swift'
```

Extract:
- **ClassName** — the class/struct name (e.g. `EntryService`)
- **Layer** — detect from file path (see threshold table below)
- **Dependencies** — constructor parameters (these become mock dependencies in `makeSUT`)
- **Methods** — all public/internal `func` declarations (async, throws, return types)
- **Feature** — the feature folder name (e.g. `Entry`)

---

### 2 — Determine Coverage Threshold

Use the layer minimums from `CLAUDE.md`. Note the threshold for this file's layer as a comment at the top of the generated file: `// Coverage target: <N>% (<Layer>)`

---

### 3 — Identify Required Mocks

For each constructor dependency:
1. Search for an existing mock: `rg -l "Mock<DepName>" meAppTests -g '*.swift'`
2. List which mocks already exist vs. need to be created

**If 3+ mocks are missing, invoke `gen-mock-batch` agent** to generate them in parallel. This subagent will:
- Read all protocol definitions at once
- Generate mock implementations for each protocol simultaneously
- Apply project conventions (call tracking, argument capture, result builders)
- Return complete list of generated mock file paths

**Invocation:**
```
Spawn gen-mock-batch agent with:
- List of missing mocks: [Protocol1, Protocol2, Protocol3, ...]
- Protocols located at: Domain/Services/*, Domain/Repositories/*
- Output location: meAppTests/Support/Mocks/
- Conventions: meApp iOS mock patterns (call tracking, argument capture)
```

The agent will return:
- List of generated mock files
- File paths ready to import in test file
- Skip if all required mocks already exist
- Skip if only 1-2 mocks needed (faster to create manually)

---

### 4 — Generate the Test File

```swift
// Coverage target: <N>% (<Layer>)
import Testing
import Foundation
import Combine
@testable import meApp

@Suite(.serialized)
@MainActor
struct <ClassName>Tests {

    // MARK: - SUT Factory

    private func makeSUT() -> (sut: <ClassName>, dep1: Mock<Dep1>, dep2: Mock<Dep2>) {
        // For Stores using @Injector, reset DI container and register mocks
        TestDependencyContainer.reset()

        let dep1 = Mock<Dep1>()
        let dep2 = Mock<Dep2>()

        // CRITICAL for @Injector-backed stores: register both concrete AND protocol
        DependencyContainer.shared.register(dep1)
        DependencyContainer.shared.register(dep1 as Dep1Protocol)
        DependencyContainer.shared.register(dep2)
        DependencyContainer.shared.register(dep2 as Dep2Protocol)

        let sut = <ClassName>()  // Will use @Injector to pull from DependencyContainer
        return (sut, dep1, dep2)
    }

    // MARK: - <methodName1>

    @Test("<methodName1> success: <expected happy path outcome>")
    func <methodName1>Success() async throws {
        // Arrange
        let (sut, dep1, _) = makeSUT()
        dep1.<relevantResult> = .success(.previewSample)  // Use fixture

        // Act
        let result = try await sut.<methodName1>(<args>)

        // Assert
        #expect(dep1.<relevantCalls> == 1)
        #expect(result == .expected)
    }

    @Test("<methodName1> failure: propagates error from <dependency>")
    func <methodName1>Failure() async throws {
        // Arrange
        let (sut, dep1, _) = makeSUT()
        dep1.<relevantResult> = .failure(TestError.sample)

        // Act / Assert
        await #expect(throws: TestError.sample) {
            try await sut.<methodName1>(<args>)
        }
    }

    @Test("<methodName1> handles nil/optional guards")
    func <methodName1>NilGuard() async throws {
        let (sut, _, _) = makeSUT()

        // Act / Assert
        await #expect(throws: TestError.invalidInput) {
            try await sut.<methodName1>(nil)
        }
    }

    // MARK: - <methodName2>
    // ... repeat pattern for each method

}

// MARK: - Helpers

private enum TestError: Error, Equatable {
    case sample
    case invalidInput
}
```

**Rules:**
- Use `import Testing` (Swift Testing framework, NOT XCTest)
- Use `@Test`, `@Suite(.serialized)`, `#expect`, `Issue.record` — never `XCTAssert`
- Use `await #expect(throws:)` for async throwing failure paths
- Use `guard case .endpoint(let x) = capturedArg else { Issue.record("..."); return }` for endpoint matching
- Follow naming: `"methodName success: ..."`, `"methodName failure: ..."`, `"methodName validation: ..."`
- Group tests under `// MARK: - methodName` matching the source method name
- Order within each group: 1) success path 2) validation/guard failures 3) runtime/network/persistence failures
- One `makeSUT()` per test suite; never create the SUT inline in individual tests
- **For Stores with `@Injector`:** Call `TestDependencyContainer.reset()` first, then **double-register** each mock (concrete + protocol cast). This prevents `fatalError("Dependency not found")` at runtime.
- **For protocols with `@Published` properties:** Mock setup requires `import Combine` and binding the publisher in the mock. Note the published types are **value-type snapshots**, not SwiftData `@Model` objects. E.g., `var activeAccountPublisher: Published<AccountSnapshot?>.Publisher { $activeAccount }`
- Place file at: `meAppTests/Features/<Feature>/<ClassName>Tests.swift`

### Fixtures & Test Data

For realistic tests, use **snapshot factories** (preferred) over SwiftData `@Model` construction:

1. **`AccountTestFixtures.makeAccountSnapshot(...)`** — flat `AccountSnapshot` with all 67 fields defaulted.
   ```swift
   accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(
       id: "acct-1", isActiveAccount: true, weightUnit: .kg
   )
   ```
2. **`EntryTestFixtures.makeEntrySnapshot(...)` / `makeBpmEntrySnapshot(...)` / `makeBabyEntrySnapshot(...)`** — `EntrySnapshot` with nested child snapshots pre-wired.
   ```swift
   entryService.fetchEntrySnapshotsForMonthResult = .success([
       EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-15T12:00:00Z", weight: 15000)
   ])
   ```
3. **`ScaleTestFixtures.makeDevice(...).toSnapshot(isConnected:)`** — build a `Device`, convert to `DeviceSnapshot` before seeding `scaleService.scales`.
   ```swift
   scaleService.scales = [ScaleTestFixtures.makeDevice(id: "s1").toSnapshot(isConnected: true)]
   ```
4. **Only use `@Model` fixtures** (`makeAccountModel`, `makeDevice`, `makeEntry`) when testing `*Repository`, `*Migration`, or `EntryService` write-path logic that genuinely persists SwiftData.

**Rule of thumb:** If the SUT consumes a publisher or a Service method that returns a snapshot, the fixture must be a snapshot. Mutating `@Model` fields after construction won't compile against snapshot-typed APIs (snapshots are immutable `let` fields).

**Avoid placeholder values** — replace `<fixture>` placeholders with actual test data before running tests.

### Branch Coverage

For each method, ensure these branches are tested:
- **Success path** — method completes and returns expected result
- **Guard/validation failures** — nil checks, invalid input, preconditions
- **Optional chaining failures** — property access on nil objects
- **Error propagation** — dependency throws error, network timeout, persistence failure
- **Early returns** — guard, if let, switch statements

Add a test for each branch. Placeholder test names above capture these; fill in assertions matching the actual branch logic.

---

### 5 — Output

1. Write the scaffolded test file to `meAppTests/Features/<Feature>/<ClassName>Tests.swift`
2. Report the file path and coverage target
3. List any mocks that still need to be created:
   ```
   Missing mocks — run /gen-mock for each:
   - MockFooService (FooServiceProtocol at Domain/Services/FooServiceProtocol.swift)
   - MockBarRepository (BarRepositoryProtocol at Domain/Repositories/BarRepositoryProtocol.swift)
   ```
4. Remind: these are stubs — fill in real fixture values and assertions before running tests

### 6 — Verify Coverage (MANDATORY)

**ALWAYS follow this skill with `/verify-tests`** to run the test suite and confirm:
- All tests pass
- Coverage meets or exceeds the threshold (comment at top of file)
- No `fatalError` from missing DI dependencies
- Fixture data and assertions are filled in (no `<fixture>` placeholders remain)

If coverage falls short, return to this skill and add more test cases targeting uncovered branches.

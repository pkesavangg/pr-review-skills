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

---

### 4 — Generate the Test File

```swift
// Coverage target: <N>% (<Layer>)
import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct <ClassName>Tests {

    // MARK: - SUT Factory

    private func makeSUT() -> (sut: <ClassName>, dep1: Mock<Dep1>, dep2: Mock<Dep2>) {
        let dep1 = Mock<Dep1>()
        let dep2 = Mock<Dep2>()
        let sut = <ClassName>(dep1: dep1, dep2: dep2)
        return (sut, dep1, dep2)
    }

    // MARK: - <methodName1>

    @Test("<methodName1> success: <expected happy path outcome>")
    func <methodName1>Success() async throws {
        // Arrange
        let (sut, dep1, _) = makeSUT()
        dep1.<relevantResult> = .success(<fixture>)

        // Act
        let result = try await sut.<methodName1>(<args>)

        // Assert
        #expect(dep1.<relevantCalls> == 1)
        #expect(result == <expected>)
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

    // MARK: - <methodName2>
    // ... repeat pattern for each method

}

// MARK: - Helpers

private enum TestError: Error, Equatable {
    case sample
}
```

**Rules:**
- Use `import Testing` (Swift Testing framework, NOT XCTest)
- Use `@Test`, `@Suite(.serialized)`, `#expect`, `Issue.record` — never `XCTAssert`
- Use `await #expect(throws:)` for async throwing failure paths
- Use `guard case .endpoint(let x) = capturedArg else { Issue.record("..."); return }` for endpoint matching
- Follow naming: `"methodName success: ..."`, `"methodName failure: ..."`, `"methodName validation failure: ..."`
- Group tests under `// MARK: - methodName` matching the source method name
- Order within each group: 1) success path 2) validation/guard failures 3) runtime/network/persistence failures
- One `makeSUT()` per test suite; never create the SUT inline in individual tests
- Add `TestDependencyContainer.reset()` as the first line of `makeSUT()` if the SUT is a Store that uses `@Injector`
- Place file at: `meAppTests/Features/<Feature>/<ClassName>Tests.swift`

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

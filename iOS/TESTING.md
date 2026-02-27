# Testing Conventions (iOS)

This is the single source of truth for team testing standards.

## Purpose
Defines how tests should be written, organized, executed, and reviewed in `meApp`.

## Test Naming Conventions
Use behavior-first names:
- `method success: expected behavior`
- `method validation failure: expected guard behavior`
- `method API/network failure: expected fallback behavior`

## Test Structure Pattern
Follow Arrange / Act / Assert in each test:
1. Arrange: create SUT + mocks + fixture data
2. Act: call one method under test
3. Assert: verify result and side effects (state, call counts, persisted values)

## Ordering Convention
Within a method group, keep this order:
1. Success path
2. Validation/guard failures
3. Runtime/API/network/persistence failure paths

## Mock Guidelines
- Prefer protocol-based mocks.
- Avoid `fatalError` in mock behavior.
- Throw explicit test errors for unexpected paths.
- Track call counts and captured inputs for assertions.
- Keep reusable mocks in `meAppTests/Support/Mocks/`.

## DI and Isolation Pattern
When tests depend on global DI (`DependencyContainer`):
- Reset container per test setup.
- Register base dependencies first.
- Pin dependencies directly onto SUT if async callbacks can resolve lazily.
- Use serialized suites only when global mutable state cannot be avoided.

## SwiftData In-Memory Pattern
For repository/service tests that touch persistence, use in-memory stores.

```swift
import SwiftData

@MainActor
func makeInMemoryContainer() throws -> ModelContainer {
    let schema = Schema([YourModel.self])
    let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
    return try ModelContainer(for: schema, configurations: [config])
}
```

Rules:
- Never use app production persistent paths in unit tests.
- Build per-test containers (no cross-test reuse).
- Seed data in Arrange phase only.

## Async and MainActor Patterns
- Mark UI/store test suites `@MainActor`.
- For async state transitions, wait deterministically (`waitUntil`) rather than sleeping.
- If tests use shared global mutable state + async callbacks, prefer `@Suite(.serialized)`.

## String Assertion Rule
For toast/error/alert assertions:
- Define expected strings as static test constants in the test file.
- Do not assert using production string containers (`ToastStrings`, `FormErrorMessages`, etc.).

Example:
```swift
private enum LoginStoreTestText {
    static let passwordResetFailed = "Failed to send password reset email."
}

#expect(store.resetError == LoginStoreTestText.passwordResetFailed)
```

For alert checks, assert both title and message using test-local constants.

## Minimum Coverage Requirements (Per Layer)
- Service layer (`Data/Services`): **80% minimum**, **85% preferred** for critical auth/account/sync.
- Store/ViewModel layer (`Features/*/Stores`, `ViewModels`): **80% minimum**.
- Form/validation layer (`Features/*/Forms`): **85% minimum**.
- Repository/API adapters: **75% minimum**.

If changed files in a layer fall below threshold, add tests before merge.

## Real Test File References
- `meAppTests/Features/Account/AccountServiceTests.swift`
- `meAppTests/Features/Auth/Stores/LoginStoreTests.swift`
- `meAppTests/Features/Auth/Stores/SignupStoreTests.swift`
- `meAppTests/Features/Auth/Forms/LoginFormTests.swift`
- `meAppTests/Features/Auth/Forms/SignupFormTests.swift`
- `meAppTests/Support/DI/TestDependencyContainer.swift`
- `meAppTests/Support/Mocks/Services/MockAccountService.swift`

## How To Run Tests

### Xcode (UI)
1. Open `meApp.xcodeproj`
2. Select scheme `meAppTests`
3. Use `Dev` configuration
4. Run `Cmd+U`

### Command Line (Simulator)
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

### Command Line (Physical Device)
```bash
export DEVICE_ID=$(xcrun xctrace list devices 2>/dev/null | grep -E "iPhone|iPad" | head -1 | sed -n 's/.*(\([^)]*\)).*/\1/p')
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination "platform=iOS,id=$DEVICE_ID"
```

Device must be connected, unlocked, trusted, and signing must be valid.

### Run a Single Suite
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:meAppTests/AccountServiceTests
```

## How To Check Coverage In Xcode
1. Open Test Report (`Cmd+9`)
2. Open latest run
3. Open **Coverage** tab
4. Inspect changed files
5. Confirm per-layer minimum coverage is met

## Coverage Practices
- Add tests for success and failure branches.
- Prioritize branch-heavy methods.
- Cover guard/early-return paths.
- Cover offline/network fallback paths separately.
- Add regression tests for fixed bugs.

## PR Testing Checklist
- [ ] Added/updated tests for changed logic
- [ ] Followed Arrange / Act / Assert pattern
- [ ] Mock behavior is explicit (no `fatalError` paths)
- [ ] Async tests use deterministic waiting (no arbitrary sleeps)
- [ ] If using shared global DI state, tests are isolated (`reset`/pinning/serialized where required)
- [ ] Toast/error/alert assertions use test-local constants (not production string containers)
- [ ] SwiftData persistence tests use in-memory containers
- [ ] Service-layer changes meet minimum coverage (80%, target 85%+ for critical auth/account/sync)
- [ ] Store/ViewModel changes meet minimum coverage (80%)
- [ ] Form/validation changes meet minimum coverage (85%)
- [ ] Repository/API adapter changes meet minimum coverage (75%)
- [ ] Coverage checked in Xcode report for changed files

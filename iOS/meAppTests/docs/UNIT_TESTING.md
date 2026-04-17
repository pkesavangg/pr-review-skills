# Unit Testing Guide (`meAppTests`)

## Purpose
Defines how unit tests are written, organized, run, and reviewed in `meAppTests`.

## Naming Convention
Use behavior-first names:
- `method success: expected behavior`
- `method validation failure: expected guard behavior`
- `method API/network failure: expected fallback behavior`

## Test Structure
Follow Arrange / Act / Assert:
1. Arrange: create SUT + mocks + fixtures
2. Act: call one method under test
3. Assert: verify result and side effects

## Ordering Convention
For each method group:
1. Success path
2. Validation/guard failures
3. Runtime/API/network/persistence failures

## Mock Guidelines
- Prefer protocol-based mocks.
- Avoid `fatalError` in mock behavior.
- Throw explicit test errors for unexpected paths.
- Track call counts and captured input.
- Keep reusable mocks in `meAppTests/Support/Mocks/`.

## DI / Isolation Pattern
`DependencyContainer` is global mutable state.

Use this setup pattern per suite:
1. `TestDependencyContainer.reset()`
2. `TestDependencyContainer.registerBase(...)`
3. Register suite-specific mocks
4. Create SUT
5. Pin dependencies directly on SUT where async/lazy resolve can race

References:
- `meAppTests/Support/DI/TestDependencyContainer.swift`
- `meAppTests/Features/Auth/Stores/LoginStoreTests.swift`

## SwiftData Rules
For persistence tests:
- Use in-memory containers only.
- Never use production persistent paths.
- Build a per-test container.
- Seed data only in Arrange phase.

## AccountSnapshot Pattern
Tests that need an active account should create `AccountSnapshot` (immutable value type), **not** the SwiftData `Account` `@Model`.

**Factory:** `AccountTestFixtures.makeAccountSnapshot(...)` — all 67 properties have sensible defaults.

**Seeding mocks:**
```swift
let account = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "test@example.com", isActiveAccount: true)
accountService.activeAccount = account
accountService.seedAccounts([account], active: account)
```

**Key differences from Account @Model:**
- All `let` properties — set everything at construction, no post-creation mutation.
- Flat structure — use `account.weightUnit` not `account.weightSettings?.weightUnit`.
- Property mapping: `goalType`, `goalWeight`, `isWeightlessOn`, `dashboardType`, `isHealthKitOn`, etc. are top-level fields.
- `Sendable` — safe to pass across actor boundaries.

**When to use Account @Model:** Only in `AccountRepository` and `AccountMigration` tests that test SwiftData persistence directly. Use `AccountTestFixtures.makeAccountModel()` for those.

**Result types:** Service methods (`logIn`, `signUp`, `createGoal`, `updateProfile`, etc.) return `Void`. Set `mockAccountService.logInResult = .success(())`, not `.success(someAccount)`.

## Async Rules
- Use `@MainActor` for UI/store tests.
- Use deterministic polling helpers (`waitUntil`) instead of arbitrary sleeps.
- Use serialized suites where shared global mutable state cannot be avoided.

## Assertion Rules
Each test should assert:
1. Primary result (return value/thrown error)
2. At least one side effect

For toast/error/alert text assertions:
- Define expected strings in test-local constants.
- Do not assert with production string containers.

## Coverage Minimums
- Service layer (`Data/Services`): **80% min**, **85% preferred** for critical auth/account/sync
- Store/ViewModel layer: **80% min**
- Form/validation layer: **85% min**
- Repository/API adapters: **75% min**

## Run Unit Tests
### Xcode
1. Open `meApp.xcodeproj`
2. Select scheme `meAppTests`
3. Use `Dev` configuration
4. Run `Cmd+U`

### CLI (Simulator)
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

### Run Single Suite
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:meAppTests/AccountServiceTests
```

## Unit Testing Checklist
- [ ] Added/updated tests for changed logic
- [ ] Arrange/Act/Assert followed
- [ ] Mock behavior explicit
- [ ] Async tests deterministic (no arbitrary sleeps)
- [ ] DI state isolated (`reset`/pinning/serialized as needed)
- [ ] Toast/error/alert assertions use test-local constants
- [ ] SwiftData tests use in-memory container
- [ ] Coverage checked for changed files

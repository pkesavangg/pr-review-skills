# AccountRepository Test Coverage Guide

## Purpose
This document explains how `AccountRepository` is tested, which persistence behaviors are covered, and how to rerun the focused suite with coverage.

## Files Involved
- Repository under test:
  - `meApp/Data/Storage/DB/AccountRepository.swift`
- Protocol:
  - `meApp/Domain/Repositories/AccountRepositoryProtocol.swift`
- Main test suite:
  - `meAppTests/Features/Account/AccountRepositoryTests.swift`
- Shared fixtures:
  - `meAppTests/Features/Account/Fixtures/AccountTestFixtures.swift`

## Testability Change
`AccountRepository` now supports injection via:

```swift
init(context: ModelContext? = nil)
```

Production behavior is unchanged because the default remains `PersistenceController.shared.context`.

## Coverage Strategy
`AccountRepository` is local SwiftData persistence, so coverage focuses on:
1. CRUD persistence behavior
2. Duplicate prevention and idempotent reruns
3. Active-account and logged-in-account queries
4. Relationship merge behavior during repeated saves/updates
5. Persistence failure propagation

Each test uses an isolated in-memory `ModelContainer` so no shared app state is touched.

## Flows Covered

### 1) Insert and Fetch
- `saveAccount` persists scalar account fields
- `saveAccount` persists attached relationship models
- `fetchAccount(byId:)` returns stored account
- `fetchAllAccounts()`
- `fetchAllAccountsSync()`

### 2) Update and Rerun Safety
- `saveAccount` with same `accountId` updates existing record instead of duplicating
- repeated `saveAccount` calls do not corrupt stored state
- `updateAccount` persists detached changes
- `updateAccount` upserts when the account is missing
- repeated `updateAccount` calls preserve last-write state

### 3) Duplicate Prevention
- saving a second account with the same email removes the old duplicate row
- duplicate prevention keeps only the newest canonical account record

### 4) Relationship Edge Cases
- `updateAccount` recreates missing relationship models when stored account has none
- `updateAccount` clears relationships when the source account omits them
- merge logic is exercised for:
  - `WeightCompSettings`
  - `GoalSettings`
  - `StreaksSettings`
  - `WeightlessSettings`
  - `NotificationSettings`
  - `DashboardSettings`
  - `IntegrationSettings`

### 5) Delete Paths
- `deleteAccount(byId:)` removes only the requested account
- repeated `deleteAccount(byId:)` calls are safe
- `deleteAllAccounts()` clears storage
- repeated `deleteAllAccounts()` calls are safe

### 6) Account State Queries
- `fetchActiveAccount()` returns the selected active account
- `fetchLoggedInAccounts()` filters out logged-out accounts
- `activateAccount(withId:lastActiveTime:)` switches the active account and clears the flag on others
- repeated account switching keeps only one active account
- activating a missing account throws `AccountError.accountNotFound`

### 7) Persistence Failure Handling
- `saveAccount` propagates SwiftData save failures
- `deleteAccount` propagates SwiftData save failures
- failure tests use a real unique-constraint conflict in the same context, not a fake mock error

## Verified Coverage Result
Focused coverage was verified for:
- `meApp/Data/Storage/DB/AccountRepository.swift`

Measured result:
- `100.00% (317/317)`

## Run Commands
Run from repo root: `meApp/meApp`

### Focused AccountRepository tests
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -destination 'id=00008020-00191D5136E9002E' \
  -only-testing:meAppTests/AccountRepositoryTests
```

### Focused AccountRepository tests with coverage
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -destination 'id=00008020-00191D5136E9002E' \
  -only-testing:meAppTests/AccountRepositoryTests \
  -enableCodeCoverage YES \
  -resultBundlePath /tmp/AccountRepositoryCoverage.xcresult
```

### Inspect coverage
```bash
xcrun xccov view --report /tmp/AccountRepositoryCoverage.xcresult | rg 'AccountRepository.swift'
```

## Important Note About Destinations
This project currently includes bundled XCFrameworks that do not provide simulator slices for the test build path used here. Because of that, the focused `AccountRepositoryTests` coverage run was validated on a connected physical iPhone instead of an iOS Simulator.

Device must be:
- connected
- unlocked
- trusted by Xcode

## How To Extend This Suite
1. Keep tests in `AccountRepositoryTests.swift`
2. Create state only through the in-memory container
3. Prefer verifying persisted state via repository fetches, not object identity
4. For new rerun-safety bugs, add repeated save/update/switch/delete assertions
5. For new persistence failure paths, prefer real SwiftData constraint failures over mock-only errors

## Team Expectation
- Keep `AccountRepository.swift` coverage at or above **90%**
- Current verified coverage is **100%**
- Every local account persistence bug fix should add a regression test

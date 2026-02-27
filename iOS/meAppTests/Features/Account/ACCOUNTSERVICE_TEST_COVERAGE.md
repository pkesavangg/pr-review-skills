# AccountService Test Coverage Guide

## Purpose
This document explains how `AccountService` is tested, what flows are covered, and how to extend tests safely.

## Files Involved
- Service under test:
  - `meApp/Data/Services/AccountService.swift`
- Main test suite:
  - `meAppTests/Features/Account/AccountServiceTests.swift`
- Account-specific fixtures/mocks:
  - `meAppTests/Features/Account/Fixtures/AccountTestFixtures.swift`
  - `meAppTests/Features/Account/Mocks/MockAccountAPIRepository.swift`
  - `meAppTests/Features/Account/Mocks/MockAccountRepository.swift`
- Shared test support:
  - `meAppTests/Support/DI/TestDependencyContainer.swift`
  - `meAppTests/Support/Mocks/Services/MockLoggerService.swift`
  - `meAppTests/Support/Mocks/Services/MockKeychainService.swift`
  - `meAppTests/Support/Mocks/Services/MockBluetoothService.swift`
  - `meAppTests/Support/Mocks/Services/MockNetworkMonitor.swift`
  - `meAppTests/Support/Mocks/Repositories/MockIntegrationAPIRepository.swift`

## Coverage Strategy
`AccountService` is large and branch-heavy. Coverage is improved by testing each method with:
1. Success path
2. Guard/validation failures
3. Network error/offline fallback behavior
4. Non-network failure behavior

## Flows Covered

### 1) Authentication
- `signUp`: success, API failure, save failure, max accounts reached
- `logIn`: success, API failure, save failure, refresh failure

### 2) Session and Lifecycle
- `logOut`: no active account, account not found, success
- `deleteAccount`: no active account, success
- `deleteAllAccounts`: success
- `logOutAllAccounts`: success, API failure continuation
- `setActiveAccount`: success
- `switchAccount`: success, no internet

### 3) Account State Fetching
- `getActiveAccount`
- `getAllLoggedInAccounts`
- `fetchAccount(byId:)` found/missing
- `fetchAllAccounts` token hydration
- `updatePublishedState`

### 4) Account Updates
- `createGoal`: no active account, success, network fallback
- `updateProfile`: no active account, success, offline save
- `updateBodyComp`: success, offline save
- `updateNotifications`: no active account, success, offline save
- `updateDashboardType`: no active account, success, local update failure
- `updateIntegrations`: no active account, success, network fallback
- `deleteHealthIntegration`: no active account, success, network fallback
- `updateDashboardMetrics`: success, network error
- `updateProgressMetrics`: success, network error
- `updateStreak`: success, network fallback
- `updateWeightless`: success, network fallback

### 5) Tokens and Security
- `updateTokens`: success, no active account, account not found
- `refreshTokens`: success, no active account, missing refresh token, explicit accountId path
- `getActiveTokens`: keychain path, fallback path, no active account
- `requestPasswordReset`: success, API failure
- `updatePassword`: success, API failure

### 6) Refresh and Sync
- `refreshAccount`: no active account, account not found, success, network fallback
- `refreshAllAccounts`: success, network error continue, non-network error expiry marking
- `syncUnsyncedAccounts`: no connectivity, no active local account, healthKit on/off branches, non-network throw

## How `makeSUT` Works
`makeSUT` in `AccountServiceTests` builds `AccountService` with mock dependencies:
- API repo mock
- local repo mock
- integration repo mock
- network monitor mock
- logger/keychain/bluetooth via test DI container

It also:
- resets DI container per test
- disables startup background loading (`performInitialLoad: false`)

This keeps tests deterministic and focused on business logic.

## How To Add New AccountService Tests
1. Add/extend fixture in `AccountTestFixtures`
2. Configure mock `Result` for the scenario
3. Seed repo state needed for the method
4. Call method under test
5. Assert:
   - returned result or thrown error
   - state transitions (local account flags/settings)
   - key side effects (API/repo/keychain call counts)

### String Assertion Convention
When validating error/toast/alert text in tests:
- Define expected text as static constants inside the test file (e.g. `AccountServiceTestText`).
- Assert against those test constants.
- Do not use production string containers directly in assertions.

Example:
```swift
private enum AccountServiceTestText {
    static let logoutFailure = "Unable to clear local session"
}

#expect(message.contains(AccountServiceTestText.logoutFailure))
```

## Run and Check Coverage
Run from **repo root** (`meApp-1`).

**Simulator:**
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Production \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:meAppTests/AccountServiceTests
```

**Physical device (e.g. iPhone 15 Plus):**
```bash
export DEVICE_ID=$(xcrun xctrace list devices 2>/dev/null | grep -E "iPhone|iPad" | head -1 | sed -n 's/.*(\([^)]*\)).*/\1/p')
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Production \
  -destination "platform=iOS,id=$DEVICE_ID" \
  -only-testing:meAppTests/AccountServiceTests
```
Device must be connected, unlocked, and trusted.

Coverage in Xcode:
1. Test Report (`Cmd+9`)
2. Open latest run
3. Coverage tab
4. Inspect `AccountService.swift`

## Team Expectation
- Keep AccountService coverage at least **80%**
- For auth/sync changes, aim for **85%+**
- Every bug fix in AccountService should add a regression test

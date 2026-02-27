# Testing Guidelines

## Purpose
This document defines how unit tests should be implemented in this project, how to run them, and how to verify coverage.

## Coverage Target
- Minimum required coverage for service-layer code: **80%**
- Preferred target for critical services (auth/account/sync): **85%+**

If coverage drops below 80% for changed modules, add tests before merging.

## Test Implementation Standard

### 1. Test Structure
Use clear Arrange / Act / Assert flow:
1. Arrange: create SUT + mocks + fixture data
2. Act: execute one method under test
3. Assert: verify outputs + side effects (calls/state/errors)

### 2. Test Naming
Use behavior-first names:
- `method success: expected behavior`
- `method failure: expected error behavior`
- `method network error: offline fallback behavior`

### 3. Ordering Convention
Within each method group, keep this order:
1. Success path
2. Validation/guard failures (no active account, missing record, etc.)
3. Runtime/API/network/persistence failure paths

### 4. Mocking Rules
- Use protocol-based mocks whenever possible.
- Avoid `fatalError` in mock methods.
- For unexpected calls, throw explicit test errors (for clear failures).
- Track call counts and key inputs for assertions.

### 5. Isolation Rules
- Keep tests deterministic and independent.
- Reset shared DI state between tests.
- Prefer mock dependencies over real singletons for unit tests.

### 6. Assertions
Each test should assert:
- Primary result (returned value / thrown error)
- At least one side effect (repo save/update, API call count, local state change)

### 7. UI/Error String Assertion Rule
- Do not assert against production string containers directly in tests (`ToastStrings`, `FormErrorMessages`, `AlertStrings`, etc.).
- Declare expected strings as static constants in the test file and assert against those constants.
- This keeps tests independent from production string definitions and makes expectation intent explicit.

Example pattern:
```swift
private enum LoginStoreTestText {
    static let passwordResetFailed = "Failed to send password reset email."
}

#expect(store.resetError == LoginStoreTestText.passwordResetFailed)
```

For alert checks, assert both title and message using test-local constants.

## How To Run Tests

## Xcode (UI)
1. Open `meApp.xcodeproj`
2. Select scheme: `meAppTests`
3. Ensure configuration: `Dev`
4. Run tests (`Cmd+U`)

### Command Line
Run from **repo root** (`meApp-1`).

**Simulator:**
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Production \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

**Physical device (e.g. iPhone 15 Plus):**  
Get the device ID, then run tests with it:
```bash
export DEVICE_ID=$(xcrun xctrace list devices 2>/dev/null | grep -E "iPhone|iPad" | head -1 | sed -n 's/.*(\([^)]*\)).*/\1/p')
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Production \
  -destination "platform=iOS,id=$DEVICE_ID"
```
Device must be connected, unlocked, and trusted. Signing must be valid for the app and test target.

To run only AccountService tests (simulator):
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Production \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:meAppTests/AccountServiceTests
```

To run only AccountService tests (physical device):
```bash
export DEVICE_ID=$(xcrun xctrace list devices 2>/dev/null | grep -E "iPhone|iPad" | head -1 | sed -n 's/.*(\([^)]*\)).*/\1/p')
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Production \
  -destination "platform=iOS,id=$DEVICE_ID" \
  -only-testing:meAppTests/AccountServiceTests
```

## How To Check Coverage In Xcode
1. Open the Test Report navigator (`Cmd+9`)
2. Open the latest test run
3. Select the **Coverage** tab
4. Expand target/file list and inspect:
   - `meApp/Data/Services/AccountService.swift`
5. Confirm coverage is at least 80%

## Coverage Practices
- Add tests for both success and failure branches.
- Prioritize large branch-heavy methods first.
- Cover guard/early-return paths (`noActiveAccount`, `accountNotFound`, connectivity checks).
- Cover network-offline fallback paths separately from generic failures.
- Include regression tests for past bugs.

## PR Checklist (Testing)
- New/changed service logic has unit tests.
- Existing tests updated if behavior changed.
- No flaky assertions tied to unstable global side effects.
- Toast/error/alert string assertions use test-local constants (not production string containers).
- Coverage checked and meets minimum threshold (80%).

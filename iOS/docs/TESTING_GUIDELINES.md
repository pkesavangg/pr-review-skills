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

## Automated Coverage Export (Share-Friendly)
Use `iOS/scripts/run_tests_with_coverage.sh` to run tests and generate shareable coverage reports automatically.
Detailed flow and troubleshooting: `docs/COVERAGE_REPORTING.md`.

### Prerequisites
Check Python 3:

```bash
python3 --version
```

If missing on macOS:

```bash
brew install python
```

Then verify:

```bash
python3 --version
```

### Outputs
- If selected scheme is unit-test (`meAppTests...`):
  - `iOS/meAppTests/Reports/coverage-report.md`
  - `iOS/meAppTests/Reports/coverage-report.csv`
  - `iOS/meAppTests/Reports/coverage-report.html`
  - Absolute:
    - `/Users/kesavan/meApp-1/iOS/meAppTests/Reports/coverage-report.md`
    - `/Users/kesavan/meApp-1/iOS/meAppTests/Reports/coverage-report.csv`
    - `/Users/kesavan/meApp-1/iOS/meAppTests/Reports/coverage-report.html`
- If selected scheme is UI-test (`meAppUITests...`):
  - `iOS/meAppUITests/Reports/coverage-report.md`
  - `iOS/meAppUITests/Reports/coverage-report.csv`
  - `iOS/meAppUITests/Reports/coverage-report.html`
  - Absolute:
    - `/Users/kesavan/meApp-1/iOS/meAppUITests/Reports/coverage-report.md`
    - `/Users/kesavan/meApp-1/iOS/meAppUITests/Reports/coverage-report.csv`
    - `/Users/kesavan/meApp-1/iOS/meAppUITests/Reports/coverage-report.html`

Reports include:
- Per-file coverage for each Swift source file under `meApp/`
- App-only coverage (`meApp/**/*.swift`, weighted by executable lines)

Coverage scope note:
- Official coverage metric is App-only coverage.
- Reason: it excludes external package/framework targets and keeps the number tied to app code quality.

### Command 1 (interactive)
From repo root (`meApp-1/`):
```bash
CONFIGURATION=Dev ./iOS/scripts/run_tests_with_coverage.sh
```

Behavior:
- Lists available test schemes and prompts for selection
- Lists available connected physical iOS devices and prompts for selection
- Runs tests with coverage enabled and exports `.md`, `.csv`, and `.html`
- Overwrites previous report files each run

### Command 2 (direct, no prompts)
```bash
SCHEME="meAppTests 1" DEVICE_ID=<device-id> CONFIGURATION=Dev ./iOS/scripts/run_tests_with_coverage.sh
```

Recommended: choose `meAppTests 1` if `meAppTests` fails with module dependency errors.

### Configuration Default
- Use `Dev` as the default configuration for coverage runs.
- Reason:
  - Faster and less noisy than release-style builds for regular coverage checks.
- If you manually switch to `Production`, set it explicitly in the command:
```bash
SCHEME="meAppTests" DEVICE_ID=<device-id> CONFIGURATION=Production ./iOS/scripts/run_tests_with_coverage.sh
```

## Coverage Practices
- Add tests for both success and failure branches.
- Prioritize large branch-heavy methods first.
- Cover guard/early-return paths (`noActiveAccount`, `accountNotFound`, connectivity checks).
- Cover network-offline fallback paths separately from generic failures.
- Include regression tests for past bugs.

## How Coverage Numbers Work
- `Executable Lines`: source lines that can actually run.
- `Covered Lines`: executable lines hit by tests.
- `Coverage %`: `Covered Lines / Executable Lines * 100`.

Example report row:
- `meApp/Theme/Enums/CustomTextStyle.swift | 30.00 | 30 | 100.00%`
- Interpretation: 30 out of 30 executable lines were exercised by tests.

Example:
```swift
// Calculator.swift
struct Calculator {
    func add(_ a: Int, _ b: Int) -> Int { a + b }
    func divide(_ a: Int, _ b: Int) -> Int? {
        guard b != 0 else { return nil }
        return a / b
    }
}
```

```swift
// CalculatorTests.swift
import XCTest
@testable import YourApp

final class CalculatorTests: XCTestCase {
    func testAdd() {
        let sut = Calculator()
        XCTAssertEqual(sut.add(2, 3), 5)
    }

    func testDivideByZero() {
        let sut = Calculator()
        XCTAssertNil(sut.divide(4, 0))
    }
}
```

With only these two tests, the non-zero divide path is not hit, so coverage is less than 100%. Adding a test for `divide(4, 2)` covers that remaining executable path.

## PR Checklist (Testing)
- New/changed service logic has unit tests.
- Existing tests updated if behavior changed.
- No flaky assertions tied to unstable global side effects.
- Toast/error/alert string assertions use test-local constants (not production string containers).
- Coverage checked and meets minimum threshold (80%).

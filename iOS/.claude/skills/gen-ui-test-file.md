---
name: gen-ui-test-file
description: Scaffold a UI test file for a screen or flow in meAppUITests. Triggers: "add UI tests for X", "scaffold UI test for Y flow", "generate UI tests", or when a new screen needs end-to-end coverage.
---

Scaffold a UI test file for a screen or navigation flow.

The screen or flow to test is: $ARGUMENTS

## Instructions

### 1 — Read the Target Screen

Locate and read the screen file:

```bash
rg -l "$ARGUMENTS" meApp/Features -g '*Screen.swift' | head -5
```

Extract:
- **Screen name** (e.g. `LoginScreen`)
- **Feature** (e.g. `Auth`)
- **User-visible interactive elements** — buttons, text fields, labels the user taps or reads
- **Navigation flows** — what actions can the user take from this screen
- **Possible states** — loading, error, empty, authenticated

---

### 2 — Check for Existing UI Tests

```bash
find meAppUITests -name "*{ScreenName}*" -o -name "*{Feature}UI*" 2>/dev/null
```

If a file exists, extend it rather than creating a new one.

---

### 3 — Identify Missing Accessibility Identifiers

For each interactive element (buttons, text fields, labels the user taps or reads), confirm it has `.accessibilityIdentifier("...")` in the source screen.

List any missing identifiers — these **must** be added to the source screen before the test can target them.

---

### 4 — Scaffold the UI Test File

```swift
import XCTest

final class <ScreenName>UITests: XCTestCase {

    var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["UI_TESTING"] = "1"
        app.launch()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    // MARK: - Happy Path

    func test_<screenName>_<scenario>_<expectedOutcome>() throws {
        // Navigate to screen if needed
        // Interact with elements using app.buttons["accessibilityId"]
        // Assert expected state
    }

    // MARK: - Error and Empty States

    func test_<screenName>_<errorState>_showsErrorMessage() throws {
        // ...
    }

    // MARK: - Navigation

    func test_<screenName>_<userAction>_navigatesTo<Destination>() throws {
        // ...
    }
}
```

**Rules:**
- Use `XCTestCase` and `XCTAssert*` — **NOT** Swift Testing (`@Test`, `#expect`, `@Suite`)
- Use `app.buttons["id"]`, `app.staticTexts["id"]`, `app.textFields["id"]` for element targeting
- Always set `continueAfterFailure = false` in `setUpWithError`
- Test naming: `test_{screenName}_{scenario}_{outcome}`
- Group tests: happy path → error/empty states → navigation flows

---

### 5 — Output

1. Write the scaffolded file to: `meAppUITests/Features/<Feature>/<ScreenName>UITests.swift`
2. Report the file path
3. List accessibility identifiers that need to be added to the source screen
4. Coverage target: ≥ 85% for UI test files (per `verify-tests.md`)
5. Reminder: these are stubs — fill in real interactions and assertions before running

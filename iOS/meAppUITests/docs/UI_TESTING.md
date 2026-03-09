# UI Testing Guide (`meAppUITests`)

## Purpose
Defines how UI tests are structured and executed with deterministic app-launch scenarios.

## Key Model
Unlike unit tests, UI tests do not instantiate stores/viewmodels directly.
UI tests launch the real app and drive the UI.

Therefore, test behavior is controlled through app startup hooks + DI override.

## Startup Flow (Scenario-Based)
1. UI test sets launch environment:
   - `UITEST=1`
   - `UITEST_SCENARIO=<scenario>`
2. App launches.
3. `AppDelegate` calls `UITestLaunchHandler`.
4. `UITestDependencyContainer` overrides protocol registrations (mock services).
5. Screens resolve mocks through `@Injector`.
6. Test interacts via accessibility identifiers.

## Scenario Keys
Current scenarios:
- `logged_out`
- `login_success`
- `login_unauthorized`
- `login_network_error`

Relevant files:
- `meApp/Core/Testing/UITest/UITestLaunchOptions.swift`
- `meApp/Core/Testing/UITest/UITestLaunchHandler.swift`
- `meApp/Core/Testing/UITest/UITestDependencyContainer.swift`
- `meApp/Core/Testing/UITest/Mocks/UITestAccountService.swift`

## Folder Structure
- `meAppUITests/Support/`
  - launch helpers
  - accessibility ID constants
- `meAppUITests/PageObjects/`
  - reusable screen interaction wrappers
- `meAppUITests/Features/`
  - feature-level UI test suites

## Writing UI Tests
### Pattern
1. Launch app with scenario
2. Navigate using page objects
3. Assert stable accessibility IDs and text

### Example Launch
```swift
let app = XCUIApplication()
app.launchEnvironment["UITEST"] = "1"
app.launchEnvironment["UITEST_SCENARIO"] = "login_network_error"
app.launch()
```

## Run UI Tests
### Xcode
1. Open `meApp.xcodeproj`
2. Select scheme `meAppUITests`
3. Use `Dev` configuration
4. Run `Cmd+U`

### CLI (Simulator)
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppUITests \
  -configuration Dev \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

### Run Single UI Suite
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppUITests \
  -configuration Dev \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:meAppUITests/LoginPageUITests
```

## Reliability Notes
- Prefer ID-based assertions over coordinate/timing assumptions.
- Keep tests scenario-driven and backend-independent.
- Avoid brittle timing assumptions for transient UI.

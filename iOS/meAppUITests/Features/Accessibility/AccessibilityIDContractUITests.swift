import XCTest

/// Contract test: every applied `AccessibilityID` must resolve to **exactly one**
/// element on its screen. This is the native counterpart to the Appium gate — it is
/// what catches a screen-root id bleeding onto child controls (MOB-1132), and it goes
/// red the moment an id is removed or duplicated.
///
/// Coverage grows per module: each module task (MOB-1134…1143) adds a
/// `test<Module>IDsResolveUniquely()` method here as it applies its ids. This file
/// currently covers the pilot surface (Landing + Login) verified in MOB-1132.
final class AccessibilityIDContractUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    // MARK: - Helpers

    /// Number of elements (of any type) currently matching an accessibility id.
    @MainActor
    private func matchCount(_ id: String) -> Int {
        app.descendants(matching: .any).matching(identifier: id).count
    }

    /// Assert an id resolves to exactly one element — the core contract.
    @MainActor
    private func assertResolvesToOne(_ id: String, _ message: String = "", file: StaticString = #filePath, line: UInt = #line) {
        let count = matchCount(id)
        XCTAssertEqual(count, 1, "\(id) should resolve to exactly one element, found \(count). \(message)", file: file, line: line)
    }

    // MARK: - Landing

    @MainActor
    func testLandingIDsResolveUniquely() throws {
        app.launchForUITest(scenario: .loggedOut)

        let landing = LandingPage(app: app)
        XCTAssertTrue(landing.root.waitForExistence(timeout: 5))

        assertResolvesToOne(AccessibilityID.landingScreenRoot, "root must be the container only, not stamped on siblings")
        assertResolvesToOne(AccessibilityID.landingLogInButton)
    }

    // MARK: - Login (the MOB-1132 pilot surface)

    @MainActor
    func testLoginIDsResolveUniquely() throws {
        app.launchForUITest(scenario: .loggedOut)

        let landing = LandingPage(app: app)
        XCTAssertTrue(landing.root.waitForExistence(timeout: 5))
        landing.openLogin()

        let login = LoginPage(app: app)
        XCTAssertTrue(login.emailField.waitForExistence(timeout: 5))

        // Each control resolves to exactly one node …
        assertResolvesToOne(AccessibilityID.loginWelcomeTitle)
        assertResolvesToOne(AccessibilityID.loginEmailField)
        assertResolvesToOne(AccessibilityID.loginPasswordField)
        assertResolvesToOne(AccessibilityID.loginSubmitButton)
        assertResolvesToOne(AccessibilityID.loginForgotPasswordButton)
        assertResolvesToOne(AccessibilityID.loginCloseButton)
        assertResolvesToOne(AccessibilityID.loginHelpButton)

        // … and the screen-root id lands on the container only — NOT on Close/Help.
        // (The MOB-1132 regression was: this matched 8 nodes and overrode close/help.)
        assertResolvesToOne(AccessibilityID.loginScreenRoot, "screen-root must not bleed onto Close/Help/legal")

        // Derived control ids (AppInputField/BaseInputField).
        assertResolvesToOne("\(AccessibilityID.loginPasswordField)_visibility_toggle")

        // The clear button surfaces after typing into a field.
        login.emailField.tap()
        login.emailField.typeText("user@example.com")
        assertResolvesToOne("\(AccessibilityID.loginEmailField)_clear_button")
    }
}

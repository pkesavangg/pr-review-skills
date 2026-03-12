import XCTest

final class LoginPageUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
    }

    @MainActor
    func testLoginScreenRendersWithExpectedControls() throws {
        app.launchForUITest(scenario: .loggedOut)

        let landing = LandingPage(app: app)
        XCTAssertTrue(landing.root.waitForExistence(timeout: 5))

        landing.openLogin()

        let login = LoginPage(app: app)
        XCTAssertTrue(login.emailField.waitForExistence(timeout: 5))
        XCTAssertTrue(login.passwordField.exists)
        XCTAssertTrue(login.submitButton.exists)
        XCTAssertTrue(login.forgotPasswordButton.exists)
    }

    @MainActor
    func testLoginSubmitButtonStateChangesWithFormValidity() throws {
        app.launchForUITest(scenario: .loggedOut)

        let landing = LandingPage(app: app)
        XCTAssertTrue(landing.root.waitForExistence(timeout: 5))
        landing.openLogin()

        let login = LoginPage(app: app)
        XCTAssertTrue(login.emailField.waitForExistence(timeout: 5))
        XCTAssertFalse(login.submitButton.isEnabled)

        login.emailField.tap()
        login.emailField.typeText("user@example.com")
        login.passwordField.tap()
        login.passwordField.typeText("secret123")

        XCTAssertTrue(login.submitButton.isEnabled)
    }

    @MainActor
    func testLoginSuccessNavigatesBackToLanding() throws {
        app.launchForUITest(scenario: .loginSuccess)

        let landing = LandingPage(app: app)
        XCTAssertTrue(landing.root.waitForExistence(timeout: 5))
        landing.openLogin()

        let login = LoginPage(app: app)
        XCTAssertTrue(login.emailField.waitForExistence(timeout: 5))
        login.login(email: "user@example.com", password: "secret123")

        XCTAssertTrue(landing.root.waitForExistence(timeout: 5))
    }

    @MainActor
    func testLoginUnauthorizedStaysOnLoginScreen() throws {
        app.launchForUITest(scenario: .loginUnauthorized)

        let landing = LandingPage(app: app)
        XCTAssertTrue(landing.root.waitForExistence(timeout: 5))
        landing.openLogin()

        let login = LoginPage(app: app)
        XCTAssertTrue(login.emailField.waitForExistence(timeout: 5))
        login.login(email: "user@example.com", password: "wrong-pass")

        XCTAssertTrue(login.emailField.waitForExistence(timeout: 5))
    }

    @MainActor
    func testLoginNetworkErrorStaysOnLoginScreen() throws {
        app.launchForUITest(scenario: .loginNetworkError)

        let landing = LandingPage(app: app)
        XCTAssertTrue(landing.root.waitForExistence(timeout: 5))
        landing.openLogin()

        let login = LoginPage(app: app)
        XCTAssertTrue(login.emailField.waitForExistence(timeout: 5))
        login.login(email: "user@example.com", password: "secret123")

        XCTAssertTrue(login.emailField.waitForExistence(timeout: 5))
    }
}

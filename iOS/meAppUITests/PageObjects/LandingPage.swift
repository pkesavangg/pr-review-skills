import XCTest

struct LandingPage {
    let app: XCUIApplication

    var root: XCUIElement { app.otherElements[AccessibilityID.landingScreenRoot] }
    var logInButton: XCUIElement { app.buttons[AccessibilityID.landingLogInButton] }

    func openLogin() {
        logInButton.tap()
    }
}

import XCTest

struct LandingPage {
    let app: XCUIApplication

    var root: XCUIElement { app.otherElements[UIAccessibilityID.landingScreenRoot] }
    var logInButton: XCUIElement { app.buttons[UIAccessibilityID.landingLogInButton] }

    func openLogin() {
        logInButton.tap()
    }
}

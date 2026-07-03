import XCTest

struct LoginPage {
    let app: XCUIApplication

    var root: XCUIElement { app.otherElements[AccessibilityID.loginScreenRoot] }
    var emailField: XCUIElement { app.textFields[AccessibilityID.loginEmailField] }
    var passwordField: XCUIElement { app.secureTextFields[AccessibilityID.loginPasswordField] }
    var submitButton: XCUIElement { app.buttons[AccessibilityID.loginSubmitButton] }
    var forgotPasswordButton: XCUIElement { app.buttons[AccessibilityID.loginForgotPasswordButton] }

    func login(email: String, password: String) {
        emailField.tap()
        emailField.typeText(email)
        passwordField.tap()
        passwordField.typeText(password)
        submitButton.tap()
    }
}

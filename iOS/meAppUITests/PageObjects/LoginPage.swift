import XCTest

struct LoginPage {
    let app: XCUIApplication

    var root: XCUIElement { app.otherElements[UIAccessibilityID.loginScreenRoot] }
    var emailField: XCUIElement { app.textFields[UIAccessibilityID.loginEmailField] }
    var passwordField: XCUIElement { app.secureTextFields[UIAccessibilityID.loginPasswordField] }
    var submitButton: XCUIElement { app.buttons[UIAccessibilityID.loginSubmitButton] }
    var forgotPasswordButton: XCUIElement { app.buttons[UIAccessibilityID.loginForgotPasswordButton] }

    func login(email: String, password: String) {
        emailField.tap()
        emailField.typeText(email)
        passwordField.tap()
        passwordField.typeText(password)
        submitButton.tap()
    }
}

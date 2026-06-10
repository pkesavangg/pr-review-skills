//
//  AccessibilityIdentifiers.swift
//  meApp
//
//  Stable accessibility identifiers shared between the app target and UI test target.
//  Add `.accessibilityIdentifier(AccessibilityID.<constant>)` to SwiftUI views.
//

enum AccessibilityID {
    // MARK: - Root Screens
    static let landingScreenRoot = "landing_screen_root"
    static let loginScreenRoot = "login_screen_root"

    // MARK: - Account Card (shared: Landing + My Accounts)
    static let accountCardLoggedOutLabel = "account_card_logged_out_label"

    // MARK: - Landing Screen
    static let landingLogInButton = "landing_log_in_button"
    static let landingSignUpButton = "landing_sign_up_button"
    static let landingLogInToExistingAccountButton = "landing_log_in_to_existing_account_button"
    static let landingCreateNewAccountButton = "landing_create_new_account_button"

    // MARK: - Login Screen
    static let loginEmailField = "login_email_field"
    static let loginPasswordField = "login_password_field"
    static let loginSubmitButton = "login_submit_button"
    static let loginForgotPasswordButton = "login_forgot_password_button"

    // MARK: - Signup Screen (Name Step — first screen shown after tapping Sign Up)
    static let signupFirstNameField = "signup_first_name_field"
}

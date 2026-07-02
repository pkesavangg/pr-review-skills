//
//  AccessibilityID+Auth.swift
//  Auth feature — Landing, Login, Signup accessibility identifiers.
//

extension AccessibilityID {
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
    static let loginCloseButton = "login_close_button"
    static let loginHelpButton = "login_help_button"
    static let loginWelcomeTitle = "login_welcome_title"
    // Clear / visibility-toggle ids are derived from the field id in AppInputField/BaseInputField:
    //   "<field_id>_clear_button" and "<field_id>_visibility_toggle".

    // MARK: - Signup Screen (Name Step — first screen shown after tapping Sign Up)
    static let signupFirstNameField = "signup_first_name_field"
}

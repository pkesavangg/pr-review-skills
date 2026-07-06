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

    // MARK: - Signup wizard — fields
    static let signupFirstNameField = "signup_first_name_field"
    static let signupLastNameField = "signup_last_name_field"
    static let signupEmailField = "signup_email_field"
    static let signupPasswordField = "signup_password_field"
    static let signupConfirmPasswordField = "signup_confirm_password_field"
    static let signupZipcodeField = "signup_zipcode_field"

    // MARK: - Signup wizard — navbar & footer actions
    static let signupCloseButton = "signup_close_button"
    static let signupHelpButton = "signup_help_button"
    static let signupBackButton = "signup_back_button"
    static let signupNextButton = "signup_next_button"
    static let signupCreateAccountButton = "signup_create_account_button"
    static let signupSkipButton = "signup_skip_button"
    static let signupFinishButton = "signup_finish_button"
    static let signupConnectAnotherDeviceButton = "signup_connect_another_device_button"
    static let signupAddDeviceButton = "signup_add_device_button"
    static let signupDoneButton = "signup_done_button"
    static let signupTryAgainButton = "signup_try_again_button"

    // MARK: - Landing saved-account tiles (list — id is suffixed with the accountID per row)
    static let accountCardRow = "account_card_row"
    static let accountCardDeleteButton = "account_card_delete_button"
    static let accountCardLogInButton = "account_card_log_in_button"
}

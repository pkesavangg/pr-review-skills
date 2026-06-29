//
//  AccessibilityIdentifiers.swift
//  meApp
//
//  Stable accessibility identifiers shared between the app target and UI test target.
//  Add `.accessibilityIdentifier(AccessibilityID.<constant>)` to SwiftUI views.
//
//  Values are snake_case and MUST stay byte-for-byte identical to MA-4031's
//  Android `Modifier.testTag` strings so meAppTest's `platformLocator(android, ios)`
//  resolves to a single Appium `~snake_case` selector on both platforms.
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
    static let loginCloseButton = "login_close_button"
    static let loginHelpButton = "login_help_button"
    static let loginWelcomeTitle = "login_welcome_title"
    // Clear / visibility-toggle ids are derived from the field id in AppInputField/BaseInputField:
    //   "<field_id>_clear_button" and "<field_id>_visibility_toggle".
    // Mirror the same derivation in the Android testTags for cross-platform parity.

    // MARK: - Signup Screen (Name Step — first screen shown after tapping Sign Up)
    static let signupFirstNameField = "signup_first_name_field"

    // MARK: - Change Password Screen
    static let currentPasswordField = "current_password_field"
    static let newPasswordField = "new_password_field"
    static let confirmPasswordField = "confirm_password_field"

    // MARK: - Goal Setting Screen
    static let goalWeightInput = "goal_weight_input"
    static let startingWeightInput = "starting_weight_input"
    static let goalSaveButton = "goal_save_button"
    static let goalMaintainTab = "goal_maintain_tab"
    static let goalLoseGainTab = "goal_lose_gain_tab"

    // MARK: - Manual Entry Screen
    static let weightField = "weight_field"
    static let bmiField = "bmi_field"
    static let bodyFatField = "body_fat_field"
    static let muscleMassField = "muscle_mass_field"
    static let bodyWaterField = "body_water_field"
    static let heartRateField = "heart_rate_field"
    static let boneMassField = "bone_mass_field"
    static let visceralFatField = "visceral_fat_field"
    static let subcutaneousFatField = "subcutaneous_fat_field"
    static let proteinField = "protein_field"
    static let skeletalMusclesField = "skeletal_muscles_field"
    static let basalMetabolicField = "basal_metabolic_field"
    static let metabolicAgeField = "metabolic_age_field"

    // MARK: - Edit Profile Screen
    static let firstNameField = "first_name_field"
    static let lastNameField = "last_name_field"
    static let emailField = "email_field"
    static let zipcodeField = "zipcode_field"
    static let profileSaveButton = "profile_save_button"

    // MARK: - Settings Screen rows
    static let settingsRowUserProfile = "settings_row_user_profile"
    static let settingsRowGoalSetting = "settings_row_goal_setting"
    static let settingsRowUnitType = "settings_row_unit_type"
    static let settingsRowWeightless = "settings_row_weightless"
    static let settingsRowBiologicalSex = "settings_row_biological_sex"
    static let settingsRowActivityLevel = "settings_row_activity_level"
    static let settingsRowHeight = "settings_row_height"
    static let settingsRowLogOut = "settings_row_log_out"

    // MARK: - History
    static let historyEntryRow = "history_entry_row"
    static let historyDeleteButton = "history_delete_button"
    static let historyDownloadButton = "history_download_button"

    // MARK: - Dashboard
    static let weightCard = "weight_card"
    static let bpCard = "bp_card"

    // MARK: - Account Settings / Add Scale
    static let accountSettingsAddScalesRow = "account_settings_add_scales_row"
    static let accountSettingsIntegrationsRow = "account_settings_integrations_row"
    static let accountSettingsExportDataRow = "account_settings_export_data_row"
    static let accountSettingsChangePasswordRow = "account_settings_change_password_row"
    static let accountSettingsUserProfileRow = "account_settings_user_profile_row"
}

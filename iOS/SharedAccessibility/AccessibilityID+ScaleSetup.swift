//
//  AccessibilityID+ScaleSetup.swift
//  ScaleSetup feature — Bluetooth / WiFi / Hybrid / A6 / Baby pairing flows.
//

extension AccessibilityID {
    // MARK: - Shared setup steps
    static let scaleSetupIntroButton = "scale_setup_intro_button"
    static let scaleDiscoveredConnectButton = "scale_discovered_connect_button"

    // MARK: - Complete Profile Setup step (MOB-1388)
    static let scaleSetupProfileGenderRow = "scale_setup_profile_gender_row"
    static let scaleSetupProfileHeightRow = "scale_setup_profile_height_row"
    static let scaleSetupProfileGoalMaintainTab = "scale_setup_profile_goal_maintain_tab"
    static let scaleSetupProfileGoalLoseGainTab = "scale_setup_profile_goal_lose_gain_tab"
    static let scaleSetupProfileGoalWeightField = "scale_setup_profile_goal_weight_field"
    static let scaleSetupProfileStartingWeightField = "scale_setup_profile_starting_weight_field"
    static let scaleSetupProfileSkipButton = "scale_setup_profile_skip_button"
    static let scaleSetupProfileNextButton = "scale_setup_profile_next_button"
}

// MARK: - MOB-1489 accessibility-id sweep (declared centrally)
extension AccessibilityID {
    static let a6ScaleSetupScreenRoot = "a6_scale_setup_screen_root"
    static let accuCheckInfoCloseButton = "accu_check_info_close_button"
    static let appSyncScaleSetupScreenRoot = "appsync_scale_setup_screen_root"
    static let babyBiologicalSexRow = "baby_biological_sex_row"
    static let babyBirthLengthField = "baby_birth_length_field"
    static let babyBirthWeightField = "baby_birth_weight_field"
    static let babyBirthWeightOzField = "baby_birth_weight_oz_field"
    static let babyBirthdayRow = "baby_birthday_row"
    static let babyConnectionErrorPairAgainButton = "baby_connection_error_pair_again_button"
    static let babyConnectionErrorSupportButton = "baby_connection_error_support_button"
    static let babyConnectionFailurePairAgainButton = "baby_connection_failure_pair_again_button"
    static let babyConnectionFailureSupportButton = "baby_connection_failure_support_button"
    static let babyNameField = "baby_name_field"
    static let babyScaleNicknameField = "baby_scale_nickname_field"
    static let babyScaleSetupScreenRoot = "baby_scale_setup_screen_root"
    static let babySexPickerCancelButton = "baby_sex_picker_cancel_button"
    static func babySexPickerOption(_ option: String) -> String { "baby_sex_picker_option_\(option)" }
    static let babySexPickerSaveButton = "baby_sex_picker_save_button"
    static let babySkipDialogCancelButton = "baby_skip_dialog_cancel_button"
    static let babySkipDialogConfirmButton = "baby_skip_dialog_confirm_button"
    static let bluetoothConnectingPairAgainButton = "bluetooth_connecting_pair_again_button"
    static let bluetoothConnectionSupportButton = "bluetooth_connection_support_button"
    static let bluetoothConnectionTryAgainButton = "bluetooth_connection_try_again_button"
    static let bluetoothScaleSetupScreenRoot = "bluetooth_scale_setup_screen_root"
    static let btWifiErrorSupportButton = "bt_wifi_error_support_button"
    static let btWifiErrorTryAgainButton = "bt_wifi_error_try_again_button"
    static let btWifiScaleSetupScreenRoot = "bt_wifi_scale_setup_screen_root"
    static let btWifiWhatThisButton = "bt_wifi_what_this_button"
    static func customizeSettingsItem(_ item: String) -> String { "customize_settings_item_\(item)" }
    static let duplicateUserNameField = "duplicate_user_name_field"
    static let duplicateUserRestoreAccountButton = "duplicate_user_restore_account_button"
    static let scaleDiscoveredCloseButton = "scale_discovered_close_button"
    static let scaleDiscoveredSheetRoot = "scale_discovered_sheet_root"
    static let scaleSetupBackButton = "scale_setup_back_button"
    static let scaleSetupCloseButton = "scale_setup_close_button"
    static let scaleSetupHelpButton = "scale_setup_help_button"
    static let scaleSetupNextButton = "scale_setup_next_button"
    static let scaleSetupSkipButton = "scale_setup_skip_button"
    static func userNumberButton(_ number: Int) -> String { "user_number_button_\(number)" }
    static let wifiApModeOptionButton = "wifi_ap_mode_option_button"
    static let wifiConnectionConfirmSeeSomethingElseButton = "wifi_connection_confirm_see_something_else_button"
    static let wifiConnectionSupportButton = "wifi_connection_support_button"
    static let wifiConnectionTryAgainButton = "wifi_connection_try_again_button"
    static let wifiCopyMacAddressButton = "wifi_copy_mac_address_button"
    static func wifiErrorCodeButton(_ code: String) -> String { "wifi_error_code_button_\(code)" }
    static let wifiErrorCodeSeeSomethingElseButton = "wifi_error_code_see_something_else_button"
    static let wifiNetworkNameField = "wifi_network_name_field"
    static func wifiNetworkRow(_ ssid: String) -> String { "wifi_network_row_\(ssid)" }
    static let wifiNoPasswordToggle = "wifi_no_password_toggle"
    static let wifiPasswordBackButton = "wifi_password_back_button"
    static let wifiPasswordConnectButton = "wifi_password_connect_button"
    static let wifiPasswordField = "wifi_password_field"
    static let wifiScaleSetupScreenRoot = "wifi_scale_setup_screen_root"
    static let wifiSelectionRefreshButton = "wifi_selection_refresh_button"
    static let wifiSetupCompleteOptionButton = "wifi_setup_complete_option_button"
}

package com.dmdbrands.gurus.weight.core.shared.utilities.testing

/**
 * Central catalog of stable Compose `testTag` values used as automation resource-ids.
 *
 * Values are shared verbatim (snake_case) with the iOS `AccessibilityID` set so a single
 * cross-platform selector works on both apps. On Android the tags are surfaced as Android
 * `resource-id`s in debug builds via [Modifier.exposeTestTagsAsResourceId]; there is no
 * separate Android tag — the same string is applied here through `Modifier.testTag(...)`.
 *
 * Pilot introduced for the Login screen (MOB-1492); further groups are added as screens are
 * migrated under parent MOB-1491. Reference these constants instead of inline string literals.
 */
object TestTags {
  /** Auth feature — Login screen. Mirrors the iOS `AccessibilityID+Auth` Login group. */
  object Login {
    const val EmailField = "login_email_field"
    const val PasswordField = "login_password_field"
    const val SubmitButton = "login_submit_button"
    const val ForgotPasswordButton = "login_forgot_password_button"
    const val CloseButton = "login_close_button"
    const val HelpButton = "login_help_button"
    const val WelcomeTitle = "login_welcome_title"
  }

  /** Auth feature — Forgot / reset-password modal (opened from Login). Android-defined; iOS to mirror. */
  object ForgotPassword {
    const val Title = "forgot_password_title"
    const val EmailField = "forgot_password_email_field"
    const val SubmitButton = "forgot_password_submit_button"
    const val CancelButton = "forgot_password_cancel_button"
  }

  /** Auth feature — Signup wizard (all steps + shared top-bar/footer chrome). Mirrors the iOS `AccessibilityID+Auth` Signup group. */
  object Signup {
    // Input fields (AppInput testTag param also derives _clear_button / _visibility_toggle children).
    const val FirstNameField = "signup_first_name_field"
    const val LastNameField = "signup_last_name_field"
    const val EmailField = "signup_email_field"
    const val PasswordField = "signup_password_field"
    const val ConfirmPasswordField = "signup_confirm_password_field"
    const val ZipcodeField = "signup_zipcode_field"
    const val GoalUnitToggle = "signup_goal_unit_toggle"

    // Shared top-bar (present on every step) + footer nav (non-terminal steps).
    const val CloseButton = "signup_close_button"
    const val HelpButton = "signup_help_button"
    const val BackButton = "signup_back_button"
    const val SkipButton = "signup_skip_button"
    const val NextButton = "signup_next_button"
    // Same footer button as NextButton on the final data step (label switches to "Create account").
    const val CreateAccountButton = "signup_create_account_button"

    // Terminal steps (Device Ready / All Devices Ready / Error) — own buttons.
    const val FinishButton = "signup_finish_button"
    const val ConnectAnotherDeviceButton = "signup_connect_another_device_button"
    const val TryAgainButton = "signup_try_again_button"
  }

  /** Auth feature — Landing screens (pre-auth + saved-accounts). Mirrors the iOS `AccessibilityID+Auth` Landing group. */
  object Landing {
    const val LogInButton = "landing_log_in_button"
    const val SignUpButton = "landing_sign_up_button"
    const val LogInToExistingAccountButton = "landing_log_in_to_existing_account_button"
    const val CreateNewAccountButton = "landing_create_new_account_button"
    const val VersionLabel = "landing_version_label"

    // Saved-account tile (repeated row) — suffix each with the per-account id, e.g.
    // "account_card_row_<accountId>", so every row resolves to a unique node.
    const val AccountCardRow = "account_card_row"
    const val AccountCardDeleteButton = "account_card_delete_button"
    const val AccountCardLogInButton = "account_card_log_in_button"
  }

  /**
   * ScaleSetup feature — Bluetooth / WiFi / Hybrid / A6 / Baby pairing flows. Mirrors the iOS
   * `AccessibilityID+ScaleSetup` set. Applied on the shared setup chrome + profile step so a single
   * selector matches every scale-type flow.
   */
  object ScaleSetup {
    const val IntroButton = "scale_setup_intro_button"
    const val DiscoveredConnectButton = "scale_discovered_connect_button"

    const val ProfileGenderRow = "scale_setup_profile_gender_row"
    const val ProfileHeightRow = "scale_setup_profile_height_row"
    const val ProfileGoalMaintainTab = "scale_setup_profile_goal_maintain_tab"
    const val ProfileGoalLoseGainTab = "scale_setup_profile_goal_lose_gain_tab"
    const val ProfileGoalWeightField = "scale_setup_profile_goal_weight_field"
    const val ProfileStartingWeightField = "scale_setup_profile_starting_weight_field"
    const val ProfileSkipButton = "scale_setup_profile_skip_button"
    const val ProfileNextButton = "scale_setup_profile_next_button"
  }

  /** BpmSetup feature — blood-pressure monitor pairing/onboarding. Mirrors the iOS `AccessibilityID+BpmSetup` set. */
  object BpmSetup {
    const val ScreenRoot = "bpm_setup_screen_root"
    const val CloseButton = "bpm_setup_close_button"
    const val HelpButton = "bpm_setup_help_button"
    const val BackButton = "bpm_setup_back_button"
    const val NextButton = "bpm_setup_next_button"
    const val NicknameField = "bpm_setup_nickname_field" // Android-defined; iOS to mirror
  }

  /**
   * Device setup flows (Bluetooth / WiFi / Hybrid / A6 / Baby / AppSync pairing). Android-defined;
   * iOS to mirror. Covers the shared setup chrome (`DeviceSetupHeader` close/help) and the per-screen
   * bottom-nav buttons passed into `HorizontalPagerWithBottomNavigation`. The BPM monitor flow uses
   * [BpmSetup] for its screen-specific controls; the shared header close/help fall under this group.
   */
  object DeviceSetup {
    const val ScreenRoot = "device_setup_screen_root"
    const val CloseButton = "device_setup_close_button"
    const val HelpButton = "device_setup_help_button"
    const val BackButton = "device_setup_back_button"
    const val NextButton = "device_setup_next_button"
    const val SkipButton = "device_setup_skip_button"
    const val FinishButton = "device_setup_finish_button"
    const val ContinueButton = "device_setup_continue_button"
    const val RetryButton = "device_setup_retry_button"
    const val PairAgainButton = "device_setup_pair_again_button"
    const val SupportButton = "device_setup_support_button"
    const val AddBabyButton = "device_setup_add_baby_button"
  }

  /** Device Users list screen. Android-defined; iOS to mirror. */
  object DeviceUsers {
    const val ScreenRoot = "device_users_screen_root"
    const val CloseButton = "device_users_close_button"
    const val SaveButton = "device_users_save_button"
  }

  /** Device Mode + Mode-Settings screens (scale measurement mode). Android-defined; iOS to mirror. */
  object DeviceMode {
    const val ScreenRoot = "device_mode_screen_root"
    const val CloseButton = "device_mode_close_button"
    const val SaveButton = "device_mode_save_button"
  }

  /** Device Metrics Setting + Display Metrics screens (metric reorder/toggle). Android-defined; iOS to mirror. */
  object DeviceMetrics {
    const val SettingScreenRoot = "device_metrics_setting_screen_root"
    const val DisplayScreenRoot = "device_display_metrics_screen_root"
    const val DisplayCloseButton = "device_display_metrics_close_button"
    const val DisplaySaveButton = "device_display_metrics_save_button"
  }

  /** Device Customization pager (post-pair scale customization). Android-defined; iOS to mirror. */
  object DeviceCustomization {
    const val ScreenRoot = "device_customization_screen_root"
    const val BackButton = "device_customization_back_button"
    const val NextButton = "device_customization_next_button"
    const val SaveButton = "device_customization_save_button"
    const val UsernameField = "device_customization_username_field"
  }

  /**
   * Device Details screen + its sub-screens (Additional Settings, Software Update, WiFi MAC,
   * Bluetooth, Device-Name dialog). Android-defined; iOS to mirror. Row ids are applied via the
   * shared `SettingsItem.testTag` field.
   */
  object DeviceDetails {
    const val ScreenRoot = "device_details_screen_root"
    const val CloseButton = "device_details_close_button"
    const val EnableBodyMetricsButton = "device_details_enable_body_metrics_button"
    const val SetupWifiButton = "device_details_setup_wifi_button"
    // SettingsItem rows
    const val ModeRow = "device_details_mode_row"
    const val DisplayMetricsRow = "device_details_display_metrics_row"
    const val UsersRow = "device_details_users_row"
    const val DeviceNameRow = "device_details_device_name_row"
    const val BluetoothRow = "device_details_bluetooth_row"
    const val WifiRow = "device_details_wifi_row"
    const val WifiMacRow = "device_details_wifi_mac_row"
    const val ProductGuideRow = "device_details_product_guide_row"
    const val DeleteRow = "device_details_delete_row"
    const val SoftwareUpdateRow = "device_details_software_update_row"
    const val OtherSettingsRow = "device_details_other_settings_row"
    const val SessionImpedanceRow = "device_details_session_impedance_row"

    // Additional Settings sub-screen
    const val AdditionalScreenRoot = "device_additional_settings_screen_root"
    const val AdditionalCloseButton = "device_additional_settings_close_button"
    const val StartAnimationRow = "device_additional_settings_start_animation_row"
    const val EndAnimationRow = "device_additional_settings_end_animation_row"
    const val TimeFormatRow = "device_additional_settings_time_format_row"
    const val ResetFirmwareRow = "device_additional_settings_reset_firmware_row"
    const val FactoryResetRow = "device_additional_settings_factory_reset_row"
    const val DownloadLogsRow = "device_additional_settings_download_logs_row"
    const val ClearDataRow = "device_additional_settings_clear_data_row"

    // Software Update sub-screen
    const val SoftwareUpdateScreenRoot = "device_software_update_screen_root"
    const val SoftwareUpdateCloseButton = "device_software_update_close_button"
    const val SoftwareUpgradeButton = "device_software_update_upgrade_button"
    const val SoftwareUpdateSaveButton = "device_software_update_save_button"

    // WiFi MAC + Bluetooth sub-screens
    const val WifiMacScreenRoot = "device_wifi_mac_screen_root"
    const val WifiMacCloseButton = "device_wifi_mac_close_button"
    const val BluetoothScreenRoot = "device_bluetooth_settings_screen_root"
    const val BluetoothCloseButton = "device_bluetooth_settings_close_button"

    // Device-Name dialog
    const val NameDialogField = "device_name_dialog_field"
    const val NameDialogSaveButton = "device_name_dialog_save_button"
    const val NameDialogCancelButton = "device_name_dialog_cancel_button"
  }

  /** Add Device + Choose Device screens (and model-number help dialog). Android-defined; iOS to mirror. */
  object AddDevice {
    const val ScreenRoot = "add_device_screen_root"
    const val CloseButton = "add_device_close_button"
    const val ModelNumberField = "add_device_model_number_field"
    const val SubmitButton = "add_device_submit_button"
    const val CantFindButton = "add_device_cant_find_button"
    const val ChooseScreenRoot = "choose_device_screen_root"
    const val ChooseCloseButton = "choose_device_close_button"
  }

  /** My Kids list + Add Baby screens. Android-defined; iOS to mirror. */
  object MyKids {
    const val ScreenRoot = "my_kids_screen_root"
    const val CloseButton = "my_kids_close_button"
    const val AddBabyButton = "my_kids_add_baby_button"

    // Add Baby screen
    const val AddBabyScreenRoot = "add_baby_screen_root"
    const val AddBabyCloseButton = "add_baby_close_button"
    const val AddBabySaveButton = "add_baby_save_button"
    const val NameField = "add_baby_name_field"
    const val LengthField = "add_baby_length_field"
    const val WeightLbField = "add_baby_weight_lb_field"
    const val WeightOzField = "add_baby_weight_oz_field"
    const val WeightField = "add_baby_weight_field"
  }

  /**
   * Generic chrome for shared dialog/alert windows routed through `BaseModal` (alerts, confirms,
   * radio pickers, time picker, etc.). Applied by default so every such window is selectable even
   * before it gets bespoke ids; a caller-supplied `ActionButton.testTag` / `titleTestTag` overrides.
   */
  object Dialog {
    const val Title = "dialog_title"
    const val PrimaryButton = "dialog_primary_button"
    const val SecondaryButton = "dialog_secondary_button"
  }

  /** Generic chrome for shared bottom sheets routed through `AppBottomSheet`. */
  object BottomSheet {
    const val Title = "bottom_sheet_title"
    const val CloseButton = "bottom_sheet_close_button"
  }

  /**
   * Suffixes for child controls whose tag is derived from a field's base tag by the shared
   * input component, e.g. `login_password_field` -> `login_password_field_visibility_toggle`.
   * Mirrors the iOS `AppInputField` / `BaseInputField` derivation.
   */
  object FieldSuffix {
    const val ClearButton = "_clear_button"
    const val VisibilityToggle = "_visibility_toggle"
  }
}

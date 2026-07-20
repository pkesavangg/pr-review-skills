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
   * Settings feature — Settings home. Mirrors the iOS `AccessibilityID+Settings` Settings-row group.
   *
   * NOTE: Android renders on ONE screen what iOS splits into a "Settings" screen and an "Account
   * Settings" screen, so a few ids differ from iOS and are flagged inline:
   *  • [Integrations] / [ChangePassword] — iOS names these `account_settings_integrations_row` /
   *    `account_settings_change_password_row` on its Account Settings screen (which Android has no
   *    counterpart for). Kept at the Android `settings_row_*` value pending QA cross-platform
   *    reconciliation (MOB-1491).
   *  • Rows with no iOS constant ([MyDevices], [PrivacyPolicy], [TermsOfService], [GreaterGoods],
   *    [LogoutAll]) are Android-defined; iOS to mirror.
   */
  object Settings {
    const val UserProfile = "settings_row_user_profile"
    const val MyKids = "settings_row_my_kids"
    const val MyDevices = "settings_row_my_devices" // Android-defined; iOS to mirror
    const val Integrations = "settings_row_integrations" // iOS: account_settings_integrations_row (see note)
    const val ChangePassword = "settings_row_change_password" // iOS: account_settings_change_password_row (see note)
    const val UnitType = "settings_row_unit_type"
    const val Permissions = "settings_row_app_permissions" // realigned to iOS `settingsRowAppPermissions`
    const val Messages = "settings_row_messages"
    const val Appearance = "settings_row_appearance"
    const val Notifications = "settings_row_notifications"
    const val GoalSetting = "settings_row_goal_setting"
    const val ActivityLevel = "settings_row_activity_level"
    const val Weightless = "settings_row_weightless"

    // Rendered on the Edit Profile screen on Android, but iOS groups these as Settings rows —
    // shared verbatim so a single selector matches both.
    const val BiologicalSex = "settings_row_biological_sex"
    const val Height = "settings_row_height"

    const val Help = "settings_row_help"
    const val PrivacyPolicy = "settings_row_privacy_policy" // Android-defined; iOS to mirror
    const val TermsOfService = "settings_row_terms_of_service" // Android-defined; iOS to mirror
    const val GreaterGoods = "settings_row_greater_goods" // Android-defined; iOS to mirror
    const val SwitchAccounts = "settings_row_switch_accounts"
    const val LogOut = "settings_row_log_out"
    const val LogoutAll = "settings_row_logout_all" // Android-defined; iOS to mirror
    const val DeleteAccount = "settings_row_delete_account"
  }

  /** Settings feature — Edit Profile screen. Mirrors the iOS `AccessibilityID+Settings` Edit Profile group. */
  object Profile {
    const val FirstNameField = "first_name_field"
    const val LastNameField = "last_name_field"
    const val EmailField = "email_field"
    const val ZipcodeField = "zipcode_field"
    const val SaveButton = "profile_save_button"
    const val CloseButton = "profile_close_button" // Android-defined; iOS to mirror
  }

  /**
   * Settings feature — My Accounts screen (multi-account switcher; the "Account" entry under
   * `AppRoute.AccountSettings.MyAccounts`). Android-defined; iOS to mirror. The per-account rows
   * are tagged separately via the shared [Landing] `account_card_*` ids.
   */
  object Account {
    const val ScreenRoot = "my_accounts_screen_root"
    const val CloseButton = "my_accounts_close_button"
    const val LogInToExistingButton = "my_accounts_log_in_existing_button"
    const val CreateNewAccountButton = "my_accounts_create_new_account_button"
  }

  /** Settings feature — Change Password screen. Mirrors the iOS `AccessibilityID+Settings` Change Password group. */
  object ChangePassword {
    const val CurrentPasswordField = "current_password_field"
    const val NewPasswordField = "new_password_field"
    const val ConfirmPasswordField = "confirm_password_field"
    const val SaveButton = "change_password_save_button"
    const val ForgotPasswordButton = "change_password_forgot_password_button" // Android-defined; iOS to mirror
    const val CloseButton = "change_password_close_button" // Android-defined; iOS to mirror
  }

  /** Settings feature — Integrations screen. Mirrors the iOS `AccessibilityID+Settings` Integrations group. */
  object Integrations {
    const val ScreenRoot = "integrations_screen_root"
    const val RequestButton = "integrations_request_button"
    const val CloseButton = "integrations_close_button" // Android-defined; iOS to mirror

    // Per-provider row. Suffix with the provider (e.g. "integration_row_fitbit") so every row
    // resolves to a unique node. Mirrors the iOS `integrationRow` derivation.
    const val Row = "integration_row"
  }

  /**
   * Settings feature — Goal Setting screen. Mirrors the iOS `AccessibilityID+Settings` Goal Setting group.
   *
   * The maintain / lose-gain segment tabs (iOS `goal_maintain_tab` / `goal_lose_gain_tab`) render
   * through the shared `SegmentButtonGroup`, which does not yet thread per-item tags — deferred as a
   * follow-up so shared components stay untouched in this rollout (MOB-1491).
   */
  object Goal {
    const val ScreenRoot = "goal_setting_screen_root"
    const val GoalWeightInput = "goal_weight_input"
    const val StartingWeightInput = "starting_weight_input"
    const val SaveButton = "goal_save_button"
    const val CloseButton = "goal_close_button" // Android-defined; iOS to mirror
  }

  /**
   * AppSync feature — body-composition scanner root. Mirrors the iOS `AccessibilityID+AppSync`.
   *
   * NOTE: the Android AppSync tab (`AppRoute.Main.AppSync`) is still an empty placeholder and the
   * scan itself runs in an external activity, so there is no stable app-side node to tag yet. The
   * id is reserved here for parity and applied once the Android AppSync surface lands.
   */
  object AppSync {
    const val ScannerRoot = "appsync_scanner_root"
  }

  /** Settings feature — Help screen. Android-defined; iOS to mirror. */
  object Help {
    const val ScreenRoot = "help_screen_root"
    const val CloseButton = "help_close_button"
  }

  /** Metric Info screen (opened from the dashboard / history). Android-defined; iOS to mirror. */
  object MetricInfo {
    const val ScreenRoot = "metric_info_screen_root"
    const val CloseButton = "metric_info_close_button"
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
    // Root of a shared modal window (ModalDialog). Android-defined; iOS to mirror.
    const val Container = "modal_container"
  }

  /** Generic chrome for shared bottom sheets routed through `AppBottomSheet`. */
  object BottomSheet {
    const val Title = "bottom_sheet_title"
    const val CloseButton = "bottom_sheet_close_button"
  }

  /** Dashboard snapshot product cards + bottom tab bar. Mirrors the iOS `AccessibilityID+Dashboard` group. */
  object Dashboard {
    const val WeightCard = "weight_card"
    const val BpCard = "bp_card"
    const val BabyCard = "baby_card"
    const val TabBarItem = "tab_bar_item" // suffix with the route, e.g. "tab_bar_item_history"
  }

  /** History (month list) + History Detail (entry rows). Mirrors the iOS `AccessibilityID+History` group. */
  object History {
    const val MonthRow = "history_month_row" // suffix with the month key
    const val EntryRow = "history_entry_row" // suffix with the entry id
    const val BpEntryRow = "bp_history_entry_row"
    const val BabyEntryRow = "baby_history_entry_row"
    const val BpRowExpand = "history_bp_row_expand"
    const val DeleteButton = "history_delete_button"
    const val DownloadButton = "history_download_button"
    const val EditNoteButton = "history_edit_note_button"
    const val EmptyStatePrimaryButton = "empty_state_primary_button"
    const val EmptyStateSecondaryButton = "empty_state_secondary_button"
  }

  /** Manual entry (weight / body-comp / blood-pressure / baby). Mirrors the iOS `AccessibilityID+Entry` group. */
  object ManualEntry {
    const val DateButton = "manual_entry_date_button"
    const val TimeButton = "manual_entry_time_button"
    const val SaveButton = "manual_entry_save_button"

    // Blood pressure
    const val BpSystolicField = "bp_systolic_field"
    const val BpDiastolicField = "bp_diastolic_field"
    const val BpPulseField = "bp_pulse_field"
    const val BpSaveButton = "bp_save_button"

    // Baby
    const val BabyWeightField = "baby_weight_field"
    const val BabyLengthField = "baby_length_field"
    const val BabySaveButton = "baby_save_button"
  }

  /**
   * Shared toast / in-app banner chrome ([ToastCard]) and the reading-arrival card
   * ([ReadingArrivalCard]). Android-defined; iOS to mirror.
   */
  object Toast {
    const val Card = "toast_card"
    const val Icon = "toast_icon"
    const val Title = "toast_title"
    const val Message = "toast_message"
    const val Action = "toast_action"
    const val ReadingCard = "reading_toast_card"
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

  /**
   * Derives a unique per-row tag by suffixing a base row tag with the item's stable id,
   * e.g. `rowTag(Landing.AccountCardRow, account.id)` -> `account_card_row_<id>`.
   *
   * A list renders many rows, so a single shared tag would match many nodes; suffixing with the
   * item's stable id keeps every repeated row / per-row control resolving to exactly one node.
   * Use this (or [Modifier.rowTestTag][com.dmdbrands.gurus.weight.core.shared.utilities.testing.rowTestTag])
   * instead of hand-writing the `_` join. Mirrors the iOS
   * `"\(AccessibilityID.accountCardRow)_\(id)"` contained-list-row pattern.
   */
  fun rowTag(base: String, stableId: Any): String = "${base}_$stableId"
}

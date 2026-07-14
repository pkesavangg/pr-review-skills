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

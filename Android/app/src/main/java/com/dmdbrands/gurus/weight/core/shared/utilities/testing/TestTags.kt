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

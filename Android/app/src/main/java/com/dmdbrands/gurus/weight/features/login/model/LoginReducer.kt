package com.dmdbrands.gurus.weight.features.login.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.services.MaxAccountsReachedException
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.login.strings.LoginStrings

/**
 * Controls for Login form.
 */
data class LoginFormControls(
  val email: FormControl<String>,
  val password: FormControl<String>,
) {
  companion object {
    fun create(email: String = "") = LoginFormControls(
      email =
        FormControl.create(
          initialValue = email,
          validators = listOf(
            FormValidations.required(),
            FormValidations.maxLength(100, LoginStrings.EmailLabel),
            FormValidations.email(),
          ),
        ),
      password =
        FormControl.create(
          initialValue = "",
          validators = listOf(
            FormValidations.required(),
            FormValidations.minLength(6, LoginStrings.PasswordLabel),
            FormValidations.maxLength(50, LoginStrings.PasswordLabel),
          ),
        ),
    )
  }
}

/**
 * State for Login screen, including form group and UI state.
 * @property form The form group containing login controls.
 * @property isLoading Whether the login process is ongoing.
 * @property error Error message to display, if any.
 */
data class LoginState(
  val form: FormGroup<LoginFormControls>,
  val isLoading: Boolean = false,
  val error: String? = null,
) : IReducer.State

/**
 * Intents for Login screen actions.
 */
sealed class LoginIntent : IReducer.Intent {
  /** Trigger login submission. */
  object Submit : LoginIntent()
  object OpenForgotPasswordModal : LoginIntent()
  object OpenHelpModal : LoginIntent()
  object OnBack : LoginIntent()
  object ShowMaxAccountAlert : LoginIntent()
  data class OpenInAppBrowser(val url: String) : LoginIntent()

  /** Show an error message. */
  data class Error(
    val message: String,
  ) : LoginIntent()

  /** Update the form state. */
  data class UpdateForm(
    val form: FormGroup<LoginFormControls>,
  ) : LoginIntent()

  /** Login was successful. */
  object Success : LoginIntent()
}

/**
 * Reducer for Login screen state transitions.
 */
class LoginReducer : IReducer<LoginState, LoginIntent> {
  /**
   * Reduces the current state and intent to a new state.
   * @param state The current state.
   * @param intent The intent/action to handle.
   * @return The new state after applying the intent.
   */
  override fun reduce(
    state: LoginState,
    intent: LoginIntent,
  ): LoginState =
    when (intent) {
      is LoginIntent.Submit -> {
        state.copy(isLoading = true, error = null)
      }

      is LoginIntent.OpenForgotPasswordModal -> {
        state.copy(isLoading = false, error = null)
      }

      is LoginIntent.OpenHelpModal -> {
        state.copy(isLoading = false, error = null)
      }

      is LoginIntent.Error -> {
        state.copy(isLoading = false, error = intent.message)
      }

      is LoginIntent.UpdateForm -> {
        state.copy(form = intent.form)
      }

      is LoginIntent.Success -> {
        state.copy(isLoading = false, error = null)
      }

      is LoginIntent.OnBack -> {
        state.copy(isLoading = false, error = null)
      }

      is LoginIntent.ShowMaxAccountAlert -> {
        state.copy(isLoading = false, error = MaxAccountsReachedException().message)
      }

      else -> state
    }
}

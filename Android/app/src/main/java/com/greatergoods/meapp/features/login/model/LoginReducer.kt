package com.greatergoods.meapp.features.login.model

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations

/**
 * Controls for Login form.
 */
data class LoginFormControls(
    val email: FormControl<String>,
    val password: FormControl<String>,
) {
    companion object {
        fun create() = LoginFormControls(
            email =
                FormControl.create(
                    initialValue = "",
                    validators = listOf(
                        FormValidations.required(),
                        FormValidations.maxLength(100),
                        FormValidations.email(),
                    ),
                ),
            password =
                FormControl.create(
                    initialValue = "",
                    validators = listOf(
                        FormValidations.minLength(6),
                        FormValidations.maxLength(50),
                    ),
                ),
        )
    }
}

/**
 * Controls for Reset Password form.
 */
data class ResetPasswordFormControls(
    val email: FormControl<String>,
) {
    companion object {
        fun create() = ResetPasswordFormControls(
            email =
                FormControl.create(
                    initialValue = "",
                    validators = listOf(
                        FormValidations.required(),
                        FormValidations.maxLength(100),
                        FormValidations.email(),
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

            else -> state
        }
}

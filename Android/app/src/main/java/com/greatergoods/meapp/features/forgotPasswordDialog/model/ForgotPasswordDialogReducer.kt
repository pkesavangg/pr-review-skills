package com.greatergoods.meapp.features.forgotPasswordDialog.model

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.login.strings.LoginStrings

/**
 * Controls for Forgot Password Dialog form.
 */
data class ForgotPasswordDialogFormControls(
    val email: FormControl<String>,
) {
    companion object {
        fun create() = ForgotPasswordDialogFormControls(
            email = FormControl.create(
                initialValue = "",
                validators = listOf(
                    FormValidations.required(),
                    FormValidations.maxLength(100, LoginStrings.ForgotPasswordDialogStrings.EmailLabel),
                    FormValidations.email(),
                ),
            ),
        )
    }
}

/**
 * State for Forgot Password Dialog, including form group and UI state.
 * @property form The form group containing email control.
 * @property isLoading Whether the password reset process is ongoing.
 * @property error Error message to display, if any.
 * @property isSuccess Whether the password reset email was sent successfully.
 */
data class ForgotPasswordDialogState(
    val form: FormGroup<ForgotPasswordDialogFormControls>,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
) : IReducer.State

/**
 * Intents for Forgot Password Dialog actions.
 */
sealed class ForgotPasswordDialogIntent : IReducer.Intent {
    /** Trigger password reset submission. */
    object Submit : ForgotPasswordDialogIntent()

    /** Close the dialog. */
    object Close : ForgotPasswordDialogIntent()

    /** Show an error message. */
    data class Error(
        val message: String,
    ) : ForgotPasswordDialogIntent()

    /** Password reset email was sent successfully. */
    object Success : ForgotPasswordDialogIntent()

    /** Set email value. */
    data class SetEmail(val email: String) : ForgotPasswordDialogIntent()
}

/**
 * Reducer for Forgot Password Dialog state transitions.
 */
class ForgotPasswordDialogReducer : IReducer<ForgotPasswordDialogState, ForgotPasswordDialogIntent> {
    /**
     * Reduces the current state and intent to a new state.
     * @param state The current state.
     * @param intent The intent/action to handle.
     * @return The new state after applying the intent.
     */
    override fun reduce(
        state: ForgotPasswordDialogState,
        intent: ForgotPasswordDialogIntent,
    ): ForgotPasswordDialogState =
        when (intent) {
            is ForgotPasswordDialogIntent.Submit -> {
                state.copy(isLoading = true, error = null, isSuccess = false)
            }

            is ForgotPasswordDialogIntent.Close -> {
                state.copy(isLoading = false, error = null, isSuccess = false)
            }

            is ForgotPasswordDialogIntent.Error -> {
                state.copy(isLoading = false, error = intent.message, isSuccess = false)
            }

            is ForgotPasswordDialogIntent.Success -> {
                state.copy(isLoading = false, error = null, isSuccess = true)
            }

            is ForgotPasswordDialogIntent.SetEmail -> {
                val updatedForm = state.form.controls.copy(
                    email = state.form.controls.email.apply {
                        onValueChange(intent.email)
                    },
                )
                state.copy(form = FormGroup(updatedForm))
            }
        }
}

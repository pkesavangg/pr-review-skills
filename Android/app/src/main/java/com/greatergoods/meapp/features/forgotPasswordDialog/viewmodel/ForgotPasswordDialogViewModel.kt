package com.greatergoods.meapp.features.forgotPasswordDialog.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.forgotPasswordDialog.model.ForgotPasswordDialogFormControls
import com.greatergoods.meapp.features.forgotPasswordDialog.model.ForgotPasswordDialogIntent
import com.greatergoods.meapp.features.forgotPasswordDialog.model.ForgotPasswordDialogReducer
import com.greatergoods.meapp.features.forgotPasswordDialog.model.ForgotPasswordDialogState
import com.greatergoods.meapp.features.forgotPasswordDialog.strings.ForgotPasswordDialogStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Forgot Password Dialog. Handles form state, validation, and password reset logic.
 * @property accountAuthService Service for authentication.
 * @property dialogQueueService Service for managing dialog queue.
 */
@HiltViewModel
class ForgotPasswordDialogViewModel
@Inject
constructor(
    private val accountAuthService: IAccountAuthService,
) : BaseIntentViewModel<ForgotPasswordDialogState, ForgotPasswordDialogIntent>(
    reducer = ForgotPasswordDialogReducer(),
) {
    override fun provideInitialState(): ForgotPasswordDialogState {
        return ForgotPasswordDialogState(
            form = FormGroup(ForgotPasswordDialogFormControls.create()),
        )
    }

    /**
     * Handles incoming intents and updates the state accordingly.
     * @param intent The intent to handle.
     */
    override fun handleIntent(intent: ForgotPasswordDialogIntent) {
        super.handleIntent(intent)
        when (intent) {
            is ForgotPasswordDialogIntent.Submit -> onSubmit()
            is ForgotPasswordDialogIntent.Close -> dismissModal()
            is ForgotPasswordDialogIntent.SetEmail -> setEmail(intent.email)
            else -> null
        }
    }

    /**
     * Sets the initial email value for the form.
     * @param email The email to set as initial value.
     */
    fun setInitialEmail(email: String) {
        if (email.isNotBlank()) {
            handleIntent(ForgotPasswordDialogIntent.SetEmail(email))
        }
    }

    /**
     * Returns true if the form is valid and submit button should be enabled.
     */
    val isSubmitEnabled: Boolean
        get() = state.value.form.controls.email.error == null &&
            state.value.form.controls.email.value.isNotBlank() &&
            !state.value.isLoading

    /**
     * Handles the password reset form submission. Validates the form, shows loading, and attempts password reset.
     * On success, shows success state. On failure, shows an error message.
     */
    private fun onSubmit() {
        if (!state.value.form.validate()) {
            return
        }

        val email = state.value.form.controls.email.value
        resetPassword(email)
        dialogQueueService.dismissCurrent()
    }

    /**
     * Handles password reset with loader and error handling.
     * @param email The email to send password reset to.
     */
    private fun resetPassword(email: String) {
        dialogQueueService.showLoader(
            message = ForgotPasswordDialogStrings.LoaderMessage,
        )
        viewModelScope.launch {
            try {
                accountAuthService.resetPassword(email)
                AppLog.i("resetPassword", "Password reset requested for email: $email")
                handleIntent(ForgotPasswordDialogIntent.Success)
            } catch (e: Exception) {
                AppLog.e("resetPassword", "Reset Password failed", e.toString())
            } finally {
                dialogQueueService.dismissLoader()
                state.value.form.controls.email.reset()
            }
        }
    }

    /**
     * Handles successful password reset.
     */
    private fun dismissModal() {
        // Reset the form after success
        state.value.form.controls.email.reset()
        dialogQueueService.dismissCurrent()
    }

    /**
     * Sets the email value for the form.
     * @param email The email to set.
     */
    private fun setEmail(email: String) {
        if (email.isNotBlank()) {
            state.value.form.controls.email.onValueChange(email)
        }
    }
}

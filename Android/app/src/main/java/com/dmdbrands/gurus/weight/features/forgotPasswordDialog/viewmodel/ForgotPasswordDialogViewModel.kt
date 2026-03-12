package com.dmdbrands.gurus.weight.features.forgotPasswordDialog.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.forgotPasswordDialog.model.ForgotPasswordDialogFormControls
import com.dmdbrands.gurus.weight.features.forgotPasswordDialog.model.ForgotPasswordDialogIntent
import com.dmdbrands.gurus.weight.features.forgotPasswordDialog.model.ForgotPasswordDialogReducer
import com.dmdbrands.gurus.weight.features.forgotPasswordDialog.model.ForgotPasswordDialogState
import com.dmdbrands.gurus.weight.features.forgotPasswordDialog.strings.ForgotPasswordDialogStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Forgot Password Dialog. Handles form state, validation, and password reset logic.
 * @property accountService Service for authentication.
 * @property dialogQueueService Service for managing dialog queue.
 */
@HiltViewModel
class ForgotPasswordDialogViewModel
@Inject
constructor(
  private val accountService: IAccountService,
) : BaseIntentViewModel<ForgotPasswordDialogState, ForgotPasswordDialogIntent>(
  reducer = ForgotPasswordDialogReducer(),
) {
  private val TAG = "ForgotPasswordDialogViewModel"
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
      is ForgotPasswordDialogIntent.Close -> resetForm()
      is ForgotPasswordDialogIntent.SetEmail -> setEmail(intent.email)
      else -> null
    }
  }

  /**
   * Sets the initial email value for the form.
   * @param email The email to set as initial value.
   */
  fun setInitialEmail(email: String) {
    // Reset form first to clear any previous state
    resetForm()

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
        accountService.resetPassword(email)
        AppLog.i(TAG, "Password reset requested")
        handleIntent(ForgotPasswordDialogIntent.Success)
      } catch (e: Exception) {
        handleIntent(ForgotPasswordDialogIntent.Error("Reset Password failed"))
        AppLog.e(TAG, "Reset Password failed", e)
      } finally {
        dialogQueueService.dismissLoader()
        resetForm()
      }
    }
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

  /**
   * Resets the form to its initial state, clearing all values and errors.
   */
  private fun resetForm() {
    state.value.form.resetForm()
  }
}

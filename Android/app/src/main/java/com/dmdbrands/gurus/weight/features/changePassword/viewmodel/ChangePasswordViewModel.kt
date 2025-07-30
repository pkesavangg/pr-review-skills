package com.dmdbrands.gurus.weight.features.changePassword.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.changePassword.model.ChangePasswordFormControls
import com.dmdbrands.gurus.weight.features.changePassword.model.ChangePasswordIntent
import com.dmdbrands.gurus.weight.features.changePassword.model.ChangePasswordReducer
import com.dmdbrands.gurus.weight.features.changePassword.model.ChangePasswordState
import com.dmdbrands.gurus.weight.features.changePassword.strings.ChangePasswordStrings
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Change Password screen. Handles form state, validation, change password logic, and navigation.
 * @property accountService Service for authentication.
 */
@HiltViewModel
class ChangePasswordViewModel
@Inject
constructor(
    private val accountService: IAccountService,
) : BaseIntentViewModel<ChangePasswordState, ChangePasswordIntent>(
    reducer = ChangePasswordReducer(),
) {
    override fun provideInitialState(): ChangePasswordState {
        return ChangePasswordState(
            form = FormGroup(ChangePasswordFormControls.create()),
        )
    }

    /**
     * Handles incoming intents and updates the state accordingly.
     * @param intent The intent to handle.
     */
    override fun handleIntent(intent: ChangePasswordIntent) {
        super.handleIntent(intent)
        when (intent) {
            is ChangePasswordIntent.Submit -> onSubmit()
            is ChangePasswordIntent.Success -> onSuccess()
            is ChangePasswordIntent.OpenForgotPasswordModal -> openForgotPasswordModal()
            is ChangePasswordIntent.OpenHelpModal -> openHelpModal()
            is ChangePasswordIntent.OnRequestBack -> onRequestBack()
            else -> null
        }
    }

    /**
     * Handles the change password form submission. Validates the form, shows loading, and attempts to change password.
     * On success, shows success message and navigates back. On failure, shows an error message.
     */
    private fun onSubmit() {
        dialogQueueService.showLoader(
            message = ChangePasswordStrings.LoaderMessage,
        )
        if (!state.value.form.validate()) {
            dialogQueueService.dismissLoader()
            return
        }
        val currentPassword = state.value.form.controls.currentPassword.value
        val newPassword = state.value.form.controls.newPassword.value
        viewModelScope.launch {
            try {
                val result = accountService.changePassword(currentPassword, newPassword)
                if (result) {
                    handleIntent(ChangePasswordIntent.Success)
                } else {
                    handleIntent(ChangePasswordIntent.Error(ChangePasswordStrings.ErrorMessage))
                }
            } catch (e: Exception) {
                AppLog.e("ChangePasswordViewModel", "Change password failed", e.toString())
                handleIntent(ChangePasswordIntent.Error(ChangePasswordStrings.ErrorMessage))
            } finally {
                dialogQueueService.dismissLoader()
            }
        }
    }

    /**
     * Handles successful password change. Shows success message and navigates back.
     */
    private fun onSuccess() {
        viewModelScope.launch {
            dialogQueueService.showToast(
                Toast(
                    title = "Success!",
                    message = ChangePasswordStrings.SuccessMessage,
                    action = null,
                ),
            )
            navigationService.navigateBack(null)
            AppLog.i("ChangePasswordViewModel", "Password changed successfully")
        }
    }

         /**
      * Opens the Forgot Password modal.
      */
      private fun openForgotPasswordModal() {
          viewModelScope.launch {
              // Get current user's email - in change password screen, we get it from auth service
              val currentAccount = accountService.getCurrentAccount()
              val currentUserEmail = currentAccount?.email ?: ""

              dialogQueueService.enqueue(
                  DialogModel.Confirm(
                      title = "Forgot your Password?",
                      message = "Send a password reset link to $currentUserEmail",
                      confirmText = "SEND",
                      cancelText = "CANCEL",
                      onConfirm = {
                          resetPasswordForCurrentUser(currentUserEmail)
                          dialogQueueService.dismissCurrent()
                      },
                      onCancel = {
                          dialogQueueService.dismissCurrent()
                      }
                  ),
              )
          }
      }

    /**
     * Resets password for the current user using the same logic as ForgotPasswordDialogViewModel.
     * @param email The email to send password reset to.
     */
    private fun resetPasswordForCurrentUser(email: String) {
        dialogQueueService.showLoader(
            message = ChangePasswordStrings.LoaderMessage,
        )
        viewModelScope.launch {
            try {
                accountService.resetPassword(email)
                AppLog.i("resetPasswordForCurrentUser", "Password reset requested for email: $email")

                // Show success toast
                dialogQueueService.showToast(
                    Toast(
                        title = "Email Sent!",
                        message = "Password reset instructions have been sent to your email address.",
                        action = null,
                    ),
                )
            } catch (e: Exception) {
                AppLog.e("resetPasswordForCurrentUser", "Reset Password failed", e.toString())

                // Show error toast
                dialogQueueService.showToast(
                    Toast(
                        title = "Error",
                        message = "Failed to send password reset email. Please try again.",
                        action = null,
                    ),
                )
            } finally {
                dialogQueueService.dismissLoader()
            }
        }
    }

    /**
     * Opens the Help modal.
     */
    private fun openHelpModal() {
        dialogQueueService.enqueue(
            DialogModel.Custom(
                contentKey = DialogType.HelpPopup,
                onDismiss = {},
            ),
        )
    }

    /**
     * Handles request to navigate back from change password screen.
     * Shows confirmation dialog if form has unsaved changes.
     */
    private fun onRequestBack() {
        val form = state.value.form
        val hasChanges = form.isDirty

        if (hasChanges) {
            // Show confirmation dialog if there are unsaved changes
            dialogQueueService.enqueue(
                DialogModel.Confirm(
                    title = "Confirm",
                    message = "Are you sure you want to leave?",
                    confirmText = "EXIT",
                    cancelText = "RETURN",
                    onConfirm = {
                        navigateBack()
                        dialogQueueService.dismissCurrent()
                    },
                    onCancel = {
                        dialogQueueService.dismissCurrent()
                    },
                ),
            )
        } else {
            // No changes, navigate back directly
            navigateBack()
        }
    }

    /**
     * Handles navigation back from change password screen.
     * Call this when user wants to exit the change password flow.
     */
    private fun navigateBack() {
        viewModelScope.launch {
            try {
                navigationService.navigateBack()
                AppLog.d("ChangePasswordViewModel", "Successfully navigated back from change password")
            } catch (e: Exception) {
                AppLog.e("ChangePasswordViewModel", "Failed to navigate back from change password", e.toString())
            }
        }
    }
}

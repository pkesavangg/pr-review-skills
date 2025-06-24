package com.greatergoods.meapp.features.changePassword.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.features.changePassword.model.ChangePasswordFormControls
import com.greatergoods.meapp.features.changePassword.model.ChangePasswordIntent
import com.greatergoods.meapp.features.changePassword.model.ChangePasswordReducer
import com.greatergoods.meapp.features.changePassword.model.ChangePasswordState
import com.greatergoods.meapp.features.changePassword.strings.ChangePasswordStrings
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Change Password screen. Handles form state, validation, change password logic, and navigation.
 * @property accountAuthService Service for authentication.
 */
@HiltViewModel
class ChangePasswordViewModel
@Inject
constructor(
    private val accountAuthService: IAccountAuthService,
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
            is ChangePasswordIntent.OpenHelpModal -> openHelpModal()
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
                val result = accountAuthService.changePassword(currentPassword, newPassword)
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
}

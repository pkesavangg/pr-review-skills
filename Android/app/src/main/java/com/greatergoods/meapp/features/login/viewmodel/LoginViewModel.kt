package com.greatergoods.meapp.features.login.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.MaxAccountsReachedException
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.common.strings.AppPopupStrings
import com.greatergoods.meapp.features.login.model.LoginFormControls
import com.greatergoods.meapp.features.login.model.LoginIntent
import com.greatergoods.meapp.features.login.model.LoginReducer
import com.greatergoods.meapp.features.login.model.LoginState
import com.greatergoods.meapp.features.login.strings.LoginStrings
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel for the Login screen. Handles form state, validation, login logic, and navigation.
 * @property accountService Service for authentication.
 * @property customTabManager Service for opening URLs in custom tabs.
 */
@HiltViewModel(
    assistedFactory = LoginViewModel.Factory::class,
)
class LoginViewModel @AssistedInject constructor(
    @Assisted val email: String? = null,
    private val accountService: IAccountService,
    private val dialogUtility: IDialogUtility,
) : BaseIntentViewModel<LoginState, LoginIntent>(
    reducer = LoginReducer(),
) {
    @AssistedFactory
    interface Factory {
        fun create(email: String? = null): LoginViewModel
    }

    override fun provideInitialState(): LoginState {
        return LoginState(
            form = FormGroup(LoginFormControls.create()),
        )
    }

    init {
        if (email != null) {
            state.value.form.controls.email.onValueChange(email)
        }
    }

    /**
     * Handles incoming intents and updates the state accordingly.
     * @param intent The intent to handle.
     */
    override fun handleIntent(intent: LoginIntent) {
        super.handleIntent(intent)
        when (intent) {
            is LoginIntent.Submit -> onSubmit()
            is LoginIntent.OpenForgotPasswordModal -> openForgotPasswordModal()
            is LoginIntent.OpenInAppBrowser -> openInAppBrowser(intent.url)
            is LoginIntent.Success -> navigateToDashboard()
            is LoginIntent.OpenHelpModal -> openHelpModal()
            is LoginIntent.OnBack -> onBack()
            is LoginIntent.ShowMaxAccountAlert -> showMaxLimitReachedAlert()
            else -> null
        }
    }

    /**
     * Handles the login form submission. Validates the form, shows loading, and attempts login.
     * On success, navigates to the dashboard. On failure, shows an error message.
     */
    private fun onSubmit() {
        dialogQueueService.showLoader(
            message = LoginStrings.LoaderMessage,
        )
        if (!state.value.form.validate()) {
            dialogQueueService.dismissLoader()
            return
        }
        val email = state.value.form.controls.email.value
        val password = state.value.form.controls.password.value
        viewModelScope.launch {
            try {
                val account = accountService.login(email, password)
                if (account != null) {
                    handleIntent(LoginIntent.Success)
                } else {
                    handleIntent(LoginIntent.Error("Login failed"))
                }
            } catch (e: MaxAccountsReachedException) {
                handleIntent(LoginIntent.ShowMaxAccountAlert)
            } catch (e: Exception) {
                handleIntent(LoginIntent.Error(e.toString()))
                AppLog.e("onSubmit", "Login failed", e.toString())
            } finally {
                dialogQueueService.dismissLoader()
            }
        }
    }

    /**
     * Opens the Forgot Password modal.
     */
    private fun openForgotPasswordModal() {
        val loginEmail = state.value.form.controls.email.value
        dialogQueueService.enqueue(
            DialogModel.Custom(
                contentKey = DialogType.PasswordReset,
                params = mapOf(
                    "email" to loginEmail,
                ),
                onDismiss = {},
            ),
        )
    }

    private fun onBack() {
        val hasChanges = state.value.form.isDirty || state.value.form.isTouched

        if (hasChanges) {
            dialogQueueService.enqueue(
                DialogModel.Confirm(
                    title = AppPopupStrings.UnsavedChanges.Title,
                    message = AppPopupStrings.UnsavedChanges.Message,
                    confirmText = AppPopupStrings.UnsavedChanges.Exit,
                    cancelText = AppPopupStrings.UnsavedChanges.Return,
                    onConfirm = {
                        navigateBack()
                        state.value.form.resetForm()
                    },
                ),
            )
        } else {
            // No changes, exit directly
            navigateBack()
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

    private fun showMaxLimitReachedAlert() {
        dialogUtility.showMaxAccountAlert(
            isFromLanding = true,
            onDismiss = {},
        )
    }

    private fun navigateToDashboard() {
        viewModelScope.launch {
            navigationService.reInitialize()

        }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            try {
                navigationService.navigateBack()
            } catch (e: Exception) {
                AppLog.e("Login viewModel navigateBack", "Failed to navigate back from login", e.toString())
            }
        }
    }
}

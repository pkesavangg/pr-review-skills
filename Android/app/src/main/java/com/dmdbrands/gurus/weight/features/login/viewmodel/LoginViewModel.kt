package com.dmdbrands.gurus.weight.features.login.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.MaxAccountsReachedException
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.login.model.LoginFormControls
import com.dmdbrands.gurus.weight.features.login.model.LoginIntent
import com.dmdbrands.gurus.weight.features.login.model.LoginReducer
import com.dmdbrands.gurus.weight.features.login.model.LoginState
import com.dmdbrands.gurus.weight.features.login.strings.LoginStrings
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
class LoginViewModel
@AssistedInject
constructor(
  @Assisted val email: String? = null,
  private val accountService: IAccountService,
  private val dialogUtility: IDialogUtility,
) : BaseIntentViewModel<LoginState, LoginIntent>(
  reducer = LoginReducer(),
) {
  private val TAG = "LoginViewModel"
  @AssistedFactory
  interface Factory {
    fun create(email: String? = null): LoginViewModel
  }

  override fun provideInitialState(): LoginState =
    LoginState(
      form = FormGroup(LoginFormControls.create()),
    )

  init {
    if (email != null) {
      state.value.form.controls.email
        .onValueChange(email)
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
      is LoginIntent.OnBack -> navigateBack()
      is LoginIntent.OnRequestBack -> onRequestBack()
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
    val email = state.value.form.controls.email.value.trim()
    val password = state.value.form.controls.password.value.trim()
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
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  private fun onRequestBack() {
    if(state.value.form.isDirty){
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = AppPopupStrings.UnsavedChanges.Title,
          message = AppPopupStrings.UnsavedChanges.Message,
          confirmText = AppPopupStrings.UnsavedChanges.Exit,
          cancelText = AppPopupStrings.UnsavedChanges.Return,
          onConfirm = {
            navigateBack()
            dialogQueueService.dismissCurrent()
          },
          onCancel = {
            dialogQueueService.dismissCurrent()
          },
        ),
      )
    }
    else {
      navigateBack()
    }

  }

  /**
   * Opens the Forgot Password modal.
   */
  private fun openForgotPasswordModal() {
    val loginEmail = state.value.form.controls.email.value.trim()
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.PasswordReset,
        params =
          mapOf(
            "email" to loginEmail,
          ),
        onDismiss = {},
      ),
    )
  }

  /**
   * Opens the Help modal.
   */
  private fun openHelpModal() {
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.HelpPopup,
      ),
    )
  }

  private fun showMaxLimitReachedAlert() {
    dialogUtility.showMaxAccountAlert(
      isFromLanding = true,
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
        AppLog.e(TAG, "Failed to navigate back from login", e)
      }
    }
  }
}

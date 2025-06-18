package com.greatergoods.meapp.features.login.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.browser.ICustomTabManager
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.login.model.LoginFormControls
import com.greatergoods.meapp.features.login.model.LoginIntent
import com.greatergoods.meapp.features.login.model.LoginReducer
import com.greatergoods.meapp.features.login.model.LoginState
import com.greatergoods.meapp.features.login.strings.LoginStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Login screen. Handles form state, validation, login logic, and navigation.
 * @property accountAuthService Service for authentication.
 * @property customTabManager Service for opening URLs in custom tabs.
 */
@HiltViewModel
class LoginViewModel
@Inject
constructor(
    private val accountAuthService: IAccountAuthService,
    private val customTabManager: ICustomTabManager,
) : BaseIntentViewModel<LoginState, LoginIntent>(
    reducer = LoginReducer(),
) {
    override fun provideInitialState(): LoginState {
        return LoginState(
            form = FormGroup(LoginFormControls.create()),
        )
    }

    /**
     * Handles incoming intents and updates the state accordingly.
     * @param intent The intent to handle.
     */
    override fun handleIntent(intent: LoginIntent) {
        super.handleIntent(intent)
        when (intent) {
            is LoginIntent.Submit -> onSubmit()
            is LoginIntent.OpenInAppBrowser -> openUrl(intent.url)
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
                val account = accountAuthService.login(email, password)
                if (account != null) {
                    navigationService.replaceStack(AppRoute.Init.Loading)
                    AppLog.i("logIn", "Navigation to dashboard successful")
                    handleIntent(LoginIntent.Success)
                } else {
                    handleIntent(LoginIntent.Error(LoginStrings.Error.MessageNotAuth))
                }
            } catch (e: Exception) {
                AppLog.e("logIn", "Login failed", e.toString())
                handleIntent(LoginIntent.Error(LoginStrings.Error.MessageGeneric))
            } finally {
                dialogQueueService.dismissLoader()
            }
        }
    }

    fun openResetPassword() {
    }

    /**
     * Opens a URL using the injected CustomTabManager.
     * @param url The URL to open.
     */
    fun openUrl(url: String) {
        customTabManager.openChromeTab(url)
    }
}

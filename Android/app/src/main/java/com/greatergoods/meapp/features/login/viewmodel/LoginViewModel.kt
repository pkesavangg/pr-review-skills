package com.greatergoods.meapp.features.login.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.browser.ICustomTabManager
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.common.model.Loader
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
    initialState = createInitialState(),
    reducer = LoginReducer(),
) {
    init {
    }

    companion object {
        private fun createLoginFormControls() =
            LoginFormControls(
                email =
                    FormControl.create(
                        "",
                        listOf(
                            FormValidations.required(),
                            FormValidations.maxLength(100),
                            FormValidations.email(),
                        ),
                    ),
                password =
                    FormControl.create(
                        "",
                        listOf(
                            FormValidations.minLength(6),
                            FormValidations.maxLength(50),
                        ),
                    ),
            )

        private fun createInitialState() =
            LoginState(
                form = FormGroup(createLoginFormControls()),
            )
    }

    /**
     * Returns true if the form is valid.
     */
    val isFormValid: Boolean
        get() = state.value.form.validate()

    /**
     * Handles the login form submission. Validates the form, shows loading, and attempts login.
     * On success, navigates to the dashboard. On failure, shows an error message.
     */
    fun onSubmit() {
        dialogQueueService.showLoader(
            Loader(
                message = LoginStrings.LoaderMessage,
            ),
        )
        if (!state.value.form.validate()) return
        handleIntent(LoginIntent.Submit)
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

    /**
     * Opens a URL using the injected CustomTabManager.
     * @param url The URL to open.
     */
    fun openUrl(url: String) {
        customTabManager.openChromeTab(url)
    }
}

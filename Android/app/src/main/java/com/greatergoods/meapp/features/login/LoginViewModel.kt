package com.greatergoods.meapp.features.login

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.config.HttpErrorConfig
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.browser.ICustomTabManager
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.login.strings.LoginStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val accountAuthService: IAccountAuthService,
    private val customTabManager: ICustomTabManager,
) : BaseIntentViewModel<LoginState, LoginIntent>(
    initialState = LoginState(
        form = FormGroup(
            LoginFormControls(
                email = FormControl("", emptyList(), emptyList(), CoroutineScope(SupervisorJob() + Dispatchers.Main)),
                password = FormControl(
                    "",
                    emptyList(),
                    emptyList(),
                    CoroutineScope(SupervisorJob() + Dispatchers.Main),
                ),
            ),
        ),
    ),
    reducer = LoginReducer(),
) {
    init {
        // Create form controls with proper validators using viewModelScope
        val emailControl = FormControl(
            initialValue = "",
            validators = listOf(
                FormValidations.required(),
                FormValidations.noWhitespace(),
                FormValidations.email(),
                FormValidations.maxLength(100),
            ),
            asyncValidators = emptyList(),
            scope = viewModelScope,
        )

        val passwordControl = FormControl(
            initialValue = "",
            validators = listOf(
                FormValidations.required(),
                FormValidations.minLength(6, "Password"),
                FormValidations.maxLength(50),
            ),
            asyncValidators = emptyList(),
            scope = viewModelScope,
        )

        val formControls = LoginFormControls(email = emailControl, password = passwordControl)
        val formGroup = FormGroup(formControls)

        // Update the state with the properly configured form
        handleIntent(LoginIntent.UpdateForm(formGroup))
    }

    val isFormValid: Boolean
        get() = state.value.form.validate()

    fun onSubmit() {
        state.value.form.forceShowAllErrors()
        if (!state.value.form.validate()) return
        handleIntent(LoginIntent.Submit)
        val email = state.value.form.controls.email.value
        val password = state.value.form.controls.password.value
        viewModelScope.launch {
            val account = accountAuthService.login(email, password)
            if (account != null) {
                try {
                    navigationService.navigateTo(AppRoute.Init.Loading)
                    AppLog.i("logIn", "Navigation to dashboard successful")
                    handleIntent(LoginIntent.Success)
                } catch (e: Exception) {
                    AppLog.e("logIn", "Navigation failed", e.toString())
                }
            }
        }
    }

    // Open URL using injected CustomTabManager
    fun openUrl(url: String) {
        customTabManager.openChromeTab(url)
    }
}

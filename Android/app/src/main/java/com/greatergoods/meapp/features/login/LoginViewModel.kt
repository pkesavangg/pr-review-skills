package com.greatergoods.meapp.features.login

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.service.AccountAuthService
import com.greatergoods.meapp.core.shared.utilities.browser.ICustomTabManager
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val accountAuthService: IAccountAuthService,
    private val customTabManager: ICustomTabManager,
    ) : BaseIntentViewModel<LoginState, LoginIntent>(
    initialState = createInitialLoginState(viewModelScope),
    reducer = LoginReducer()
) {
    val isFormValid: Boolean
        get() = state.value.form.validate()

    fun onSubmit() {
        state.value.form.forceShowAllErrors()
        if (!state.value.form.validate()) return
        handleIntent(LoginIntent.Submit)
        val email = state.value.form.controls.email.value
        val password = state.value.form.controls.password.value
        viewModelScope.launch {
            try {
                val account = accountAuthService.login(email, password)
                if (account == null) {
                    handleIntent(LoginIntent.Error("Login failed"))
                } else {
                    // Success: handle navigation or state update as needed
                }
            } catch (e: Exception) {
                handleIntent(LoginIntent.Error(e.message ?: "Login failed"))
            }
        }
    }

    // Open URL using injected CustomTabManager
    fun openUrl(url: String) {
        customTabManager.openChromeTab(url)
    }
}

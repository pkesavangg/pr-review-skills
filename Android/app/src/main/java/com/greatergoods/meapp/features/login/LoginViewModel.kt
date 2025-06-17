package com.greatergoods.meapp.features.login

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.browser.ICustomTabManager
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val accountAuthService: IAccountAuthService,
        private val customTabManager: ICustomTabManager,
    ) : BaseIntentViewModel<LoginState, LoginIntent>(
            initialState =
                LoginState(
                    form =
                        FormGroup(
                            LoginFormControls(
                                email = FormControl.create("", emptyList()),
                                password =
                                    FormControl.create(
                                        "",
                                        emptyList(),
                                    ),
                            ),
                        ),
                ),
            reducer = LoginReducer(),
        ) {
        init {
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

package com.greatergoods.meapp.features.login

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import kotlinx.coroutines.CoroutineScope

/**
 * Controls for Login form.
 */
data class LoginFormControls(
    val email: FormControl<String>,
    val password: FormControl<String>
)

/**
 * State for Login screen, including form group and UI state.
 */
data class LoginState(
    val form: FormGroup<LoginFormControls>,
    val isLoading: Boolean = false,
    val error: String? = null,
) : IReducer.State

/**
 * Intents for Login screen actions (minimal set).
 */
sealed class LoginIntent : IReducer.Intent {
    object Submit : LoginIntent()
    data class Error(val message: String) : LoginIntent()
}

/**
 * Reducer for Login screen state transitions.
 */
class LoginReducer : IReducer<LoginState, LoginIntent> {
    override fun reduce(state: LoginState, intent: LoginIntent): LoginState {
        return when (intent) {
            is LoginIntent.Submit -> {
                state.copy(isLoading = true, error = null)
            }
            is LoginIntent.Error -> {
                state.copy(isLoading = false, error = intent.message)
            }
        }
    }
}

/**
 * Helper to create initial LoginState with form group and validators.
 */
fun createInitialLoginState(scope: CoroutineScope): LoginState {
    val email = FormControl("", listOf(FormValidations.required(), FormValidations.email()), emptyList(), scope)
    val password = FormControl("", listOf(FormValidations.required(), FormValidations.minLength(6)), emptyList(), scope)
    val controls = LoginFormControls(email = email, password = password)
    val formGroup = FormGroup(controls)
    return LoginState(form = formGroup)
}

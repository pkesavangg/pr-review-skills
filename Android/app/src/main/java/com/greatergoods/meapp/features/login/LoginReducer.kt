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
    data class UpdateForm(val form: FormGroup<LoginFormControls>) : LoginIntent()
    object Success : LoginIntent()
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
            is LoginIntent.UpdateForm -> {
                state.copy(form = intent.form)
            }
            is LoginIntent.Success -> {
                state.copy(isLoading = false, error = null)
            }
        }
    }
}

package com.greatergoods.meapp.features.common.sample.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup

data class RegisterControls(
    val username: FormControl<String>,
    val password: FormControl<String>,
    val confirmPassword: FormControl<String>,
)

class FormSampleViewModel : ViewModel() {
    val email =
        FormControl(
            initialValue = "",
            validators =
                listOf(
                    { if (it.isBlank()) "Email required" else null },
                    {
                        if (!android.util.Patterns.EMAIL_ADDRESS
                                .matcher(it)
                                .matches()
                        ) {
                            "Invalid email"
                        } else {
                            null
                        }
                    },
                ),
            scope = viewModelScope,
        )
    val password =
        FormControl(
            initialValue = "",
            validators = listOf({ if (it.length < 6) "Password too short" else null }),
            scope = viewModelScope,
        )
    val confirmPassword =
        FormControl(
            initialValue = "",
            validators = listOf({ if (it.length < 6) "Password too short" else null }),
            scope = viewModelScope,
        )

    val controls = RegisterControls(email, password, confirmPassword)
    val form =
        FormGroup(
            controls = controls,
            groupValidators =
                listOf { c ->
                    if (c.password.value != c.confirmPassword.value) "Passwords do not match" else null
                },
        )

    // To show all errors after submit attempt
    var showAllErrors by mutableStateOf(false)
        private set

    fun validateForm(): Boolean {
        showAllErrors = true
        form.forceShowAllErrors()
        return form.validate()
    }
}

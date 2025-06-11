package com.greatergoods.meapp.features.common.components.input

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.greatergoods.meapp.features.common.helper.form.Form
import com.greatergoods.meapp.features.common.helper.form.FormField
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.features.common.components.PreviewTheme

@Composable
fun PasswordInput(
    modifier: Modifier = Modifier,
    formControl: FormField<Any>? = null,
    name: String = "",
    label: String,
    placeHolder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Password,
    imeAction: ImeAction = ImeAction.Done,
) {
    InputFieldBase(
        modifier = modifier,
        formControl = formControl,
        name = name,
        label = label,
        value = formControl?.value?.toString() ?: "",
        onValueChange = { newValue ->
            formControl?.parent?.update(name, newValue)
        },
        placeHolder = placeHolder,
        enabled = enabled,
        readOnly = readOnly,
        supportingText = supportingText,
        type = InputType.PASSWORD,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        showTrailingIcon = true
    )
}

@PreviewTheme
@Composable
fun PasswordInputPreview() {
    MeAppTheme {
        val formControl = FormField<Any>(
            value = "",
            validations = listOf(
                { field -> if (field.value.toString().isBlank()) "required" else null },
                { field -> if (field.value.toString().length < 8) "min_length" else null }
            ),
            messages = mapOf(
                "required" to "Password is required",
                "min_length" to "Password must be at least 8 characters"
            )
        )

        PasswordInput(
            formControl = formControl,
            name = "password",
            label = "Password",
            placeHolder = "Enter password"
        )
    }
}

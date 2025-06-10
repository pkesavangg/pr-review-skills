package com.greatergoods.meapp.features.common.components.input

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.theme.MeAppTheme

@Composable
fun PasswordInput(
    formControl: FormControl<String>,
    modifier: Modifier = Modifier,
    label: String = "Password",
    placeHolder: String = "Enter password",
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    InputFieldBase(
        formControl = formControl,
        label = label,
        placeHolder = placeHolder,
        modifier = modifier,
        supportingText = supportingText,
        enabled = enabled,
        readOnly = readOnly,
        isPassword = true,
        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            // Add eye/eye-off icon here if you have one
        }
    )
}

@PreviewTheme
@Composable
fun PasswordInputPreview() {
    val scope = rememberCoroutineScope()
    val control = remember { FormControl("", scope = scope) }
    MeAppTheme {
        PasswordInput(formControl = control)
    }
}

package com.greatergoods.meapp.features.common.components.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.theme.MeAppTheme


@Composable
fun TextInput(
    formControl: FormControl<String>,
    modifier: Modifier = Modifier,
    label: String = "Text",
    placeHolder: String = "Enter text",
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    InputFieldBase(
        formControl = formControl,
        label = label,
        placeHolder = placeHolder,
        modifier = modifier,
        supportingText = supportingText,
        enabled = enabled,
        readOnly = readOnly,
    )
}

@PreviewTheme
@Composable
fun TextInputPreview() {
    val scope = rememberCoroutineScope()
    val control = remember { FormControl("", scope = scope) }
    MeAppTheme {
        TextInput(formControl = control)
    }
}

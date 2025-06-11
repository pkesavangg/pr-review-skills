package com.greatergoods.meapp.features.common.components.input

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.helper.form.FormField
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.features.common.components.PreviewTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun TextInput(
    modifier: Modifier = Modifier,
    formControl: FormField<Any>? = null,
    name: String = "",
    label: String,
    placeHolder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
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
        type = InputType.TEXT,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        )
    )
}

@PreviewTheme
@Composable
fun TextInputPreview() {
    MeAppTheme {
        val formControl = FormField<Any>(
            value = "",
            validations = listOf(
                { field -> if (field.value.toString().isBlank()) "required" else null }
            ),
            messages = mapOf(
                "required" to "This field is required"
            )
        )
        
        TextInput(
            formControl = formControl,
            name = "text",
            label = "Text Input",
            placeHolder = "Enter text"
        )
    }
}

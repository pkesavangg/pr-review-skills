package com.greatergoods.meapp.features.common.components.input

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.greatergoods.meapp.features.common.helper.form.FormField
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.common.helper.form.ValidationType
import com.greatergoods.meapp.features.common.helper.form.Form

@Composable
fun SkuInput(
    modifier: Modifier = Modifier,
    formControl: FormField<Any>? = null,
    name: String = "",
    label: String,
    placeHolder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Number,
    imeAction: ImeAction = ImeAction.Next
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
        type = InputType.NUMBER,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        )
    )
}

@PreviewTheme
@Composable
fun SkuInputPreview() {
    MeAppTheme {
        val formControl = FormField<Any>(
            value = "",
            validations = listOf(
                FormValidations.skuValidator()
            ),
            messages = mapOf(
                ValidationType.PATTERN to "SKU must be 4 digits"
            )
        )
        
        SkuInput(
            formControl = formControl,
            name = "sku",
            label = "SKU",
            placeHolder = "Enter SKU"
        )
    }
}

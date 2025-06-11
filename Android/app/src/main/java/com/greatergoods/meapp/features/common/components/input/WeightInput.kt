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
fun WeightInput(
    modifier: Modifier = Modifier,
    formControl: FormField<Any>? = null,
    name: String = "",
    label: String,
    placeHolder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Number,
    imeAction: ImeAction = ImeAction.Next,
    unitType: String = "kg"
) {
    InputFieldBase(
        modifier = modifier,
        formControl = formControl,
        name = name,
        label = label,
        value = formControl?.value?.toString() ?: "",
        onValueChange = { newValue ->
            // Store raw digits, actual conversion will happen in InputFieldBase or validator
            formControl?.parent?.update(name, newValue.filter { it.isDigit() })
        },
        placeHolder = placeHolder,
        enabled = enabled,
        readOnly = readOnly,
        supportingText = supportingText,
        type = InputType.NUMBER,
        visualTransformation = DecimalInputVisualTransformation(decimalDigits = 1),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        )
    )
}

@PreviewTheme
@Composable
fun WeightInputPreview() {
    MeAppTheme {
        val formControl = FormField<Any>(
            value = "",
            validations = listOf(
                FormValidations.weightValidator(unitType = "kg")
            ),
            messages = mapOf(
                ValidationType.REQUIRED to "Weight is required",
                ValidationType.NOT_IN_RANGE to "Weight must be between 1 and 450 kg"
            )
        )

        WeightInput(
            formControl = formControl,
            name = "weight",
            label = "Weight",
            placeHolder = "Enter weight"
        )
    }
}

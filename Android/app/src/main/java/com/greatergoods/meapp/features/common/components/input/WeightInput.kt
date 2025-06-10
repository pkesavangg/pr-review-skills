package com.greatergoods.meapp.features.common.components.input

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.theme.MeAppTheme


@Composable
fun WeightInput(
    formControl: FormControl<String>,
    isMetric: Boolean,
    modifier: Modifier = Modifier,
    label: String = "Weight",
    placeHolder: String = "Enter weight",
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() }
            val formatted = when {
                filtered.length <= 1 -> filtered
                else -> filtered.dropLast(1) + "." + filtered.last()
            }
            formControl.onValueChange(formatted)
        }
    )
}

@PreviewTheme
@Composable
fun WeightInputPreview() {
    val scope = rememberCoroutineScope()
    val control = remember { FormControl("", validators = listOf { weightValidator(it, true) }, scope = scope) }
    MeAppTheme {
        WeightInput(formControl = control, isMetric = true)
    }
}

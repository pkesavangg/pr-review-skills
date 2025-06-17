package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun AppHeightInput(
    formControl: FormControl<HeightInput>? = null,
    value: HeightInput? = null,
    onValueChange: ((HeightInput) -> Unit)? = null,
    modifier: Modifier = Modifier,
    label: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    var isModalTriggered by remember { mutableStateOf(false) }
    val localState = remember { mutableStateOf(value ?: HeightInput.FtIn(0, 0)) }
    val currentValue = formControl?.value ?: value ?: localState.value
    val isError = formControl?.error?.isNullOrBlank()?.not() == true

    AppChip(
        currentValue.getString(),
        selected = isModalTriggered,
        enabled = enabled && !readOnly,
        modifier = modifier,
    ) {
        if (enabled && !readOnly) {
            isModalTriggered = true
        }
    }
    if (isModalTriggered) {
        Dialog(onDismissRequest = { isModalTriggered = false }) {
            AppHeightPickerModal(
                value = currentValue,
                onCancel = {
                    isModalTriggered = false
                },
                onOk = { data ->
                    isModalTriggered = false
                    if (formControl != null) {
                        formControl.onValueChange(data)
                    } else {
                        localState.value = data
                        onValueChange?.invoke(data)
                    }
                },
            )
        }
    }
    // Optionally show error/supporting text below
    if (formControl != null && isError) {
        Text(
            formControl.error ?: "",
            color = MeTheme.colorScheme.textError,
            style = MeTheme.typography.body3,
        )
    } else if (supportingText != null) {
        Text(
            supportingText,
            color = MeTheme.colorScheme.textSubheading,
            style = MeTheme.typography.body3,
        )
    }
}

@PreviewTheme
@Composable
fun AppHeightInputPreview() {
    MeAppTheme {
        Column(Modifier.fillMaxSize()) {
            val fakeScope = rememberCoroutineScope()
            val heightControl =
                remember { FormControl<HeightInput>(HeightInput.FtIn(5, 1), emptyList(), emptyList(), fakeScope) }
            AppHeightInput(formControl = heightControl)
            AppHeightInput(value = HeightInput.FtIn(5, 7), onValueChange = {})
        }
    }
}

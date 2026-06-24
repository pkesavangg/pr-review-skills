package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.typography

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
    val isError = formControl?.isError ?: false

    AppChip(
        currentValue.getString(),
        selected = isModalTriggered,
        enabled = enabled && !readOnly,
        textTransform = TextTransform.NONE,
        modifier = modifier,
    ) {
        if (enabled && !readOnly) {
            isModalTriggered = true
        }
    }
    if (isModalTriggered) {
        ModalDialog(
          onDismiss = {
            isModalTriggered = false
          },
          config = ModalConfigs.Critical,
        ) {
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
        val errorMessage = formControl.error?.message ?: ""
        Text(
            errorMessage,
            color = colorScheme.textError,
            style = typography.body3,
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
            val heightControl =
                remember { FormControl.create<HeightInput>(HeightInput.FtIn(5, 1), emptyList(), ) }
            AppHeightInput(formControl = heightControl)
            AppHeightInput(value = HeightInput.FtIn(5, 7), onValueChange = {})
        }
    }
}

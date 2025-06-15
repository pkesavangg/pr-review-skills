package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.greatergoods.meapp.theme.MeAppTheme

@Composable
fun AppHeightInput(value: HeightInput) {
    var currentValue by remember { mutableStateOf(value) }
    var isModalTriggered by remember { mutableStateOf(false) }

    AppChip(currentValue.getString(), selected = isModalTriggered) {
        isModalTriggered = true
    }
    if (isModalTriggered) {
        Dialog(onDismissRequest = { isModalTriggered = false }) {
            AppHeightPickerModal(
                value = value,
                onCancel = {
                    isModalTriggered = false
                },
                onOk = { data ->
                    isModalTriggered = false
                    currentValue = data
                },
            )
        }
    }
}

@PreviewTheme
@Composable
fun AppHeightInputPreview() {
    MeAppTheme {
        Column(Modifier.fillMaxSize()) {

        AppHeightInput(HeightInput.FtIn(5, 1))
        }
    }
}

package com.greatergoods.meapp.features.common.components

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.viewmodel.DialogQueueViewModel

enum class DialogType {
    HeightPicker,
}

@Composable
fun DialogHost() {
    val dialogQueueViewModel: DialogQueueViewModel = hiltViewModel()
    // Global dialog host
    DialogQueueHost(dialogQueueViewModel) { dialog ->
        when (dialog.contentKey) {
            // Custom dialog for height picker
            DialogType.HeightPicker -> {
                // Extract params for unit and initial values
                AppHeightPickerModal(
                    value = dialog.params.get("value") as HeightInput,
                    onCancel = {
                        dialog.onDismiss()
                        dialogQueueViewModel.dismissCurrent()
                    },
                    onOk = { data ->
                        // You can add callback logic here to return the selected value
                        dialog.onConfirm?.let { it(data) }
                        dialogQueueViewModel.dismissCurrent()
                    },
                )
            }

            else -> {
                // Default dialog handling
                // This can be a placeholder or a default dialog implementation
            }
        }
    }
}

package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.enum.AppHeightUnit
import com.greatergoods.meapp.features.common.model.PickerState
import com.greatergoods.meapp.features.common.viewmodel.DialogQueueViewModel

@Composable
fun DialogHost() {
    val dialogQueueViewModel: DialogQueueViewModel = hiltViewModel()
    // Global dialog host
    DialogQueueHost(dialogQueueViewModel) { dialog ->
        when (dialog.contentKey) {
            // Custom dialog for height picker
            "height_picker" -> {
                // Extract params for unit and initial values
                val unit = dialog.params["unit"] as? AppHeightUnit ?: AppHeightUnit.CM
                val cm = dialog.params["cm"] as? Int ?: 160
                val feet = dialog.params["feet"] as? Int ?: 5
                val inch = dialog.params["inch"] as? Int ?: 7
                val cmState = remember { PickerState(cm) }
                val feetState = remember { PickerState(feet) }
                val inchState = remember { PickerState(inch) }
                AppHeightPickerModal(
                    unit = unit,
                    cmState = cmState,
                    feetState = feetState,
                    inchState = inchState,
                    onCancel = { dialog.onDismiss(); dialogQueueViewModel.dismissCurrent() },
                    onOk = {
                        // You can add callback logic here to return the selected value
                        dialog.onDismiss(); dialogQueueViewModel.dismissCurrent()
                    }
                )
            }
            "custom_dialog" -> {
                Text("Custom dialog: ${dialog.params}", modifier = Modifier.background(Red))
            }
            else -> {
                // Default dialog handling
                // This can be a placeholder or a default dialog implementation
            }
        }
    }
}

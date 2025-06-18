package com.greatergoods.meapp.features.common.components

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.DialogType.HelpPopup
import com.greatergoods.meapp.features.common.viewmodel.DialogQueueViewModel
import com.greatergoods.meapp.features.forgotPasswordDialog.screen.PasswordResetModal

enum class DialogType {
    HeightPicker,
    HelpPopup,
    PasswordReset,
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

            HelpPopup -> {
                // Custom dialog for help popup
                AppHelpModal(
                    onClose = {
                        dialog.onDismiss()
                        dialogQueueViewModel.dismissCurrent()
                    },
                )
            }

            DialogType.PasswordReset -> {
                val emailControl =
                    dialog.params["emailControl"] as? com.greatergoods.meapp.features.common.helper.form.FormControl<String>
                        ?: return@DialogQueueHost
                val isSubmitEnabledLambda = dialog.params["isSubmitEnabled"] as? (() -> Boolean)
                val isSubmitEnabled = isSubmitEnabledLambda?.invoke() ?: true
                PasswordResetModal(
                    emailControl = emailControl,
                    onSubmit = {
                        dialog.onConfirm?.invoke(Unit)
                        dialogQueueViewModel.dismissCurrent()
                    },
                    onCancel = {
                        dialog.onDismiss()
                        dialogQueueViewModel.dismissCurrent()
                    },
                    isSubmitEnabled = isSubmitEnabled,
                )
            }

            else -> {
                // Default dialog handling
                // This can be a placeholder or a default dialog implementation
            }
        }
    }
}

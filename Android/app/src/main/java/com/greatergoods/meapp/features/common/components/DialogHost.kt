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
    RadioGroupPicker,
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
                        dialog.onDismiss?.let { it() }
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
                        dialog.onDismiss?.let { it() }
                        dialogQueueViewModel.dismissCurrent()
                    },
                )
            }

            DialogType.RadioGroupPicker -> {
                // Custom dialog for radio group picker
                val config = dialog.params["config"] as? RadioGroupModalConfig<*>
                val onConfirm = dialog.params["onConfirm"] as? (Any?) -> Unit
                val onCancel = dialog.params["onCancel"] as? (() -> Unit)

                if (config != null) {
                    AppRadioGroupModal(
                        config = config as RadioGroupModalConfig<Any>,
                        onCancel = {
                            onCancel?.invoke()
                            dialogQueueViewModel.dismissCurrent()
                        },
                        onOk = { selectedValue ->
                            onConfirm?.invoke(selectedValue)
                            dialogQueueViewModel.dismissCurrent()
                        },
                    )
                }
            }

            DialogType.PasswordReset -> {
                val email = dialog.params["email"] as? String ?: ""
                PasswordResetModal(
                    email = email,
                    onDismiss = {
                        dialog.onDismiss?.let { it() }
                        dialogQueueViewModel.dismissCurrent()
                    },
                )
            }
        }
    }
}

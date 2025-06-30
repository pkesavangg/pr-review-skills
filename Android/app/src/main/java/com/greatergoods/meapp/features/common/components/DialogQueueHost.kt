package com.greatergoods.meapp.features.common.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greatergoods.meapp.features.common.model.ActionButton
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.viewmodel.DialogQueueViewModel

/**
 * Composable host that observes a DialogQueueViewModel and displays dialogs and toasts reactively as the queue changes.
 *
 * Supports Alert, Confirm, and Custom dialog types, as well as toast notifications. Handles dialog and toast dismissal.
 *
 * Usage example:
 * ```kotlin
 * val dialogQueueViewModel: DialogQueueViewModel = ...
 * DialogQueueHost(dialogQueueViewModel)
 * ```
 *
 * @param dialogQueueViewModel The dialog queue ViewModel to observe for dialog and toast state.
 * @param customDialogContent Optional composable for rendering custom dialogs. If null, custom dialogs fall back to alert style.
 */
@Composable
fun DialogQueueHost(
    dialogQueueViewModel: DialogQueueViewModel,
    customDialogContent: (
        @Composable (
            DialogModel.Custom,
        ) -> Unit
    )? = null,
) {
    val snackBarHostState = remember { SnackbarHostState() }
    val currentDialog by dialogQueueViewModel.currentDialog.collectAsState()
    val currentToast by dialogQueueViewModel.currentToast.collectAsState()
    val loader by dialogQueueViewModel.loader.collectAsState()

    currentToast?.let { dialog ->
        ToastHandler(hostState = snackBarHostState, toast = dialog) {
            dialogQueueViewModel.dismissToast()
        }
    }

    loader?.let { loader ->
        LoaderCard(loader = loader)
    }

    currentDialog?.let { dialog ->
        when (dialog) {
            is DialogModel.Alert -> {
                AppDialog(
                    title = dialog.title,
                    body = dialog.message,
                    confirmAction =
                        ActionButton(
                            dialog.dismissText,
                            action = {
                                dialogQueueViewModel.dismissCurrent()
                            },
                        ),
                    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                )
            }

            is DialogModel.Confirm -> {
                AppDialog(
                    title = dialog.title,
                    body = dialog.message,
                    confirmAction =
                        ActionButton(dialog.confirmText) {
                            dialog.onConfirm?.let { it() }
                            dialogQueueViewModel.dismissCurrent()
                        },
                    dismissAction =
                        ActionButton(dialog.cancelText) {
                            dialog.onDismiss?.let { it() }
                            dialog.onCancel?.invoke()
                            dialogQueueViewModel.dismissCurrent()
                        },
                    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                )
            }

            is DialogModel.Custom -> {
                if (customDialogContent != null) {
                    Dialog(
                        onDismissRequest = {
                            dialog.onDismiss?.let { it() }
                            dialogQueueViewModel.dismissCurrent()
                        },
                        content = {
                            customDialogContent(
                                dialog,
                            )
                        },
                    )
                } else {
                    // Fallback: treat as alert
                    AlertDialog(
                        onDismissRequest = {
                            dialog.onDismiss?.let { it() }
                            dialogQueueViewModel.dismissCurrent()
                        },
                        title = { Text(dialog.contentKey.toString()) },
                        text = { Text("Custom dialog: ${dialog.params}") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    dialogQueueViewModel.dismissCurrent()
                                },
                            ) {
                                Text("OK")
                            }
                        },
                    )
                }
            }
        }
    }
}

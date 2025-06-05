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
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.viewmodel.DialogQueueViewModel

/**
 * Composable host that observes a DialogQueueViewModel and displays dialogs reactively as the queue changes.
 * Supports Alert, Confirm, and Custom dialog types.
 *
 * Note: Each dialog's onDismiss() must be called when the dialog is closed (confirm, cancel, or dismiss).
 *
 * Usage:
 * ```kotlin
 * val dialogQueueViewModel: DialogQueueViewModel = ...
 * DialogQueueHost(dialogQueueViewModel)
 * ```
 *
 * @param dialogQueueViewModel The dialog queue ViewModel to observe.
 * @param customDialogContent Optional composable for rendering custom dialogs.
 */
@Composable
fun DialogQueueHost(
    dialogQueueViewModel: DialogQueueViewModel,
    customDialogContent: (@Composable (DialogModel.Custom) -> Unit)? = null,
) {
    val snackBarHostState = remember { SnackbarHostState() }
    val currentDialog by dialogQueueViewModel.currentDialog.collectAsState()
    val currentToast by dialogQueueViewModel.currentToast.collectAsState()
    currentToast?.let { dialog ->
        ToastHandler(hostState = snackBarHostState, toast = dialog) {
            dialogQueueViewModel.dismissToast()
        }
    }
    currentDialog?.let { dialog ->
        when (dialog) {
            is DialogModel.Alert -> {
                AlertDialog(
                    onDismissRequest = {
                        dialog.onDismiss()
                        dialogQueueViewModel.dismissCurrent()
                    },
                    title = { Text(dialog.title) },
                    text = { Text(dialog.message) },
                    confirmButton = {
                        Button(
                            onClick = {
                                dialog.onDismiss()
                                dialogQueueViewModel.dismissCurrent()
                            },
                        ) {
                            Text(dialog.dismissText)
                        }
                    },
                )
            }

            is DialogModel.Confirm -> {
                AlertDialog(
                    onDismissRequest = {
                        dialog.onDismiss()
                        dialogQueueViewModel.dismissCurrent()
                    },
                    title = { Text(dialog.title) },
                    text = { Text(dialog.message) },
                    confirmButton = {
                        Button(
                            onClick = {
                                dialog.onConfirm?.invoke()
                                dialog.onDismiss()
                                dialogQueueViewModel.dismissCurrent()
                            },
                        ) {
                            Text(dialog.confirmText)
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                dialog.onCancel?.invoke()
                                dialog.onDismiss()
                                dialogQueueViewModel.dismissCurrent()
                            },
                        ) {
                            Text(dialog.cancelText)
                        }
                    },
                )
            }

            is DialogModel.Custom -> {
                if (customDialogContent != null) {
                    Dialog(
                        onDismissRequest = {
                            dialog.onDismiss()
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
                            dialog.onDismiss()
                            dialogQueueViewModel.dismissCurrent()
                        },
                        title = { Text(dialog.contentKey) },
                        text = { Text("Custom dialog: ${dialog.params}") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    dialog.onDismiss()
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

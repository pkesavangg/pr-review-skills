package com.greatergoods.meapp.features.common.viewmodel

import androidx.lifecycle.ViewModel
import com.greatergoods.meapp.domain.interfaces.IDialogQueueHandler
import com.greatergoods.meapp.features.common.model.ActionButton
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.DialogQueueService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel wrapper for DialogQueueService, exposing dialog queue operations and state for Compose.
 * Use this ViewModel in screens or globally to push dialogs.
 */
@HiltViewModel
class DialogQueueViewModel @Inject constructor(
    private val dialogQueueService: DialogQueueService
) : IDialogQueueHandler, ViewModel() {

    override val currentDialog: StateFlow<DialogModel?> = dialogQueueService.currentDialog
    override val currentToast: StateFlow<Toast?> = dialogQueueService.currentToast

    /**
     * Enqueue an alert dialog
     */
    override fun enqueueAlert(
        title: String,
        message: String,
        dismissText: String,
        onDismiss: () -> Unit,
        priority: Int,
        delayMillis: Long
    ) {
        dialogQueueService.enqueue(
            DialogModel.Alert(
                title = title,
                message = message,
                dismissText = dismissText,
                onDismiss = onDismiss,
                alertPriority = priority,
                alertDelayMillis = delayMillis,
            ),
        )
    }

    /**
     * Enqueue a confirmation dialog
     */
    override fun enqueueConfirm(
        title: String,
        message: String,
        confirmText: String,
        cancelText: String,
        onConfirm: (() -> Unit)?,
        onCancel: (() -> Unit)?,
        onDismiss: () -> Unit,
        priority: Int,
        delayMillis: Long
    ) {
        dialogQueueService.enqueue(
            DialogModel.Confirm(
                title = title,
                message = message,
                confirmText = confirmText,
                cancelText = cancelText,
                onConfirm = onConfirm,
                onCancel = onCancel,
                onDismiss = onDismiss,
                confirmPriority = priority,
                confirmDelayMillis = delayMillis,
            ),
        )
    }

    /**
     * Enqueue a custom dialog with arbitrary parameters
     */
    override fun enqueueCustomDialog(
        contentKey: String,
        params: Map<String, Any?>,
        onDismiss: () -> Unit,
        priority: Int,
        delayMillis: Long
    ) {
        dialogQueueService.enqueue(
            DialogModel.Custom(
                contentKey = contentKey,
                params = params,
                onDismiss = onDismiss,
                customPriority = priority,
                customDelayMillis = delayMillis,
            ),
        )
    }

    /**
     * Enqueue a toast message
     */
    override fun enqueueToast(
        message: String,
        title: String?,
        action: ActionButton?
    ) {
        dialogQueueService.enqueueToast(
            Toast(
                message = message,
                title = title,
                action = action,
            ),
        )
    }

    /**
     * Dismiss the current dialog and optionally show the next one
     */
    override fun dismissCurrent(showNext: Boolean) {
        dialogQueueService.dismissCurrent()
        if (!showNext) {
            dialogQueueService.clear()
        }
    }

    override fun dismissToast() {
        dialogQueueService.dismissToast()
    }

    /**
     * Clear all dialogs from the queue
     */
    override fun clear() {
        dialogQueueService.clear()
    }

    /**
     * Check if there are any dialogs in the queue
     */
    override fun hasPendingDialogs(): Boolean =
        dialogQueueService.getQueueSize() > 0 || dialogQueueService.currentDialog.value != null

    /**
     * Get the next dialog in the queue without dequeuing it
     */
    override fun peekNextDialog(): DialogModel? =
        dialogQueueService.peekNextDialog()

    /**
     * Get the number of dialogs in the queue
     */
    override fun getQueueSize(): Int =
        dialogQueueService.getQueueSize() + (if (dialogQueueService.currentDialog.value != null) 1 else 0)

    /**
     * Update the delay of the current dialog
     */
    override fun updateCurrentDialogDelay(delayMillis: Long) {
        val current = dialogQueueService.currentDialog.value
        if (current != null) {
            dialogQueueService.dismissCurrent()
            when (current) {
                is DialogModel.Alert -> enqueueAlert(
                    title = current.title,
                    message = current.message,
                    dismissText = current.dismissText,
                    onDismiss = current.onDismiss,
                    priority = current.alertPriority,
                    delayMillis = delayMillis,
                )

                is DialogModel.Confirm -> enqueueConfirm(
                    title = current.title,
                    message = current.message,
                    confirmText = current.confirmText,
                    cancelText = current.cancelText,
                    onConfirm = current.onConfirm,
                    onCancel = current.onCancel,
                    onDismiss = current.onDismiss,
                    priority = current.confirmPriority,
                    delayMillis = delayMillis,
                )

                is DialogModel.Custom -> enqueueCustomDialog(
                    contentKey = current.contentKey,
                    params = current.params,
                    onDismiss = current.onDismiss,
                    priority = current.customPriority,
                    delayMillis = delayMillis,
                )

                else -> {}
            }
        }
    }

    /**
     * Update the priority of the current dialog
     */
    override fun updateCurrentDialogPriority(priority: Int) {
        val current = dialogQueueService.currentDialog.value
        if (current != null) {
            dialogQueueService.dismissCurrent()
            when (current) {
                is DialogModel.Alert -> enqueueAlert(
                    title = current.title,
                    message = current.message,
                    dismissText = current.dismissText,
                    onDismiss = current.onDismiss,
                    priority = priority,
                    delayMillis = current.alertDelayMillis,
                )

                is DialogModel.Confirm -> enqueueConfirm(
                    title = current.title,
                    message = current.message,
                    confirmText = current.confirmText,
                    cancelText = current.cancelText,
                    onConfirm = current.onConfirm,
                    onCancel = current.onCancel,
                    onDismiss = current.onDismiss,
                    priority = priority,
                    delayMillis = current.confirmDelayMillis,
                )

                is DialogModel.Custom -> enqueueCustomDialog(
                    contentKey = current.contentKey,
                    params = current.params,
                    onDismiss = current.onDismiss,
                    priority = priority,
                    delayMillis = current.customDelayMillis,
                )

                else -> {}
            }
        }
    }
}

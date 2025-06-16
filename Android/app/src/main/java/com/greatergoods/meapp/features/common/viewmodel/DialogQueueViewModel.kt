package com.greatergoods.meapp.features.common.viewmodel

import androidx.lifecycle.ViewModel
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.features.common.components.LoaderConfig
import com.greatergoods.meapp.features.common.components.LoaderDefaults
import com.greatergoods.meapp.features.common.components.LoaderStyle
import com.greatergoods.meapp.features.common.model.ActionButton
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Loader
import com.greatergoods.meapp.features.common.model.Toast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel wrapper for DialogQueueService, exposing dialog queue operations and state for Compose.
 * Use this ViewModel in screens or globally to push dialogs.
 */
@HiltViewModel
class DialogQueueViewModel @Inject constructor(
    private val dialogQueueService: IDialogQueueService
) : ViewModel() {

    val currentDialog: StateFlow<DialogModel?> = dialogQueueService.currentDialog
    val currentToast: StateFlow<Toast?> = dialogQueueService.currentToast
    val loader: StateFlow<Loader?> = dialogQueueService.loader

    /**
     * Enqueue an alert dialog
     */
    fun enqueueAlert(
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
    fun enqueueConfirm(
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
    fun enqueueCustomDialog(
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

    fun showLoader(
        message: String,
        loaderStyle: LoaderStyle = LoaderStyle.DASHED,
        loaderConfig: LoaderConfig = LoaderDefaults.defaultFor(loaderStyle)
    ) {
        dialogQueueService.showLoader(
            Loader(
                message = message,
                style = loaderStyle,
                config = loaderConfig,
            ),
        )
    }

    fun dismissLoader() {
        dialogQueueService.dismissLoader()
    }

    /**
     * Enqueue a toast message
     */
    fun enqueueToast(
        message: String,
        title: String?,
        action: ActionButton?
    ) {
        dialogQueueService.showToast(
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
    fun dismissCurrent(showNext: Boolean = true) {
        dialogQueueService.dismissCurrent()
        if (!showNext) {
            dialogQueueService.clear()
        }

    fun dismissToast() {
        dialogQueueService.dismissToast()
    }

    /**
     * Clear all dialogs from the queue
     */
    fun clear() {
        dialogQueueService.clear()
    }

    /**
     * Check if there are any dialogs in the queue
     */
    fun hasPendingDialogs(): Boolean =
        dialogQueueService.getQueueSize() > 0 || dialogQueueService.currentDialog.value != null

    /**
     * Get the next dialog in the queue without dequeuing it
     */
    fun peekNextDialog(): DialogModel? =
        dialogQueueService.peekNextDialog()

    /**
     * Get the number of dialogs in the queue
     */
    fun getQueueSize(): Int =
        dialogQueueService.getQueueSize() + (if (dialogQueueService.currentDialog.value != null) 1 else 0)

    /**
     * Update the delay of the current dialog
     */
    fun updateCurrentDialogDelay(delayMillis: Long) {
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
                    is DialogModel.Alert ->
                        enqueueAlert(
                            title = current.title,
                            message = current.message,
                            dismissText = current.dismissText,
                            onDismiss = current.onDismiss,
                            priority = priority,
                            delayMillis = current.alertDelayMillis,
                        )

                    is DialogModel.Confirm ->
                        enqueueConfirm(
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

                    is DialogModel.Custom ->
                        enqueueCustomDialog(
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

    /**
     * Update the priority of the current dialog
     */
    fun updateCurrentDialogPriority(priority: Int) {
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

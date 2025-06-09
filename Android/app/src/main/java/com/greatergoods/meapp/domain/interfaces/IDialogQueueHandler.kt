package com.greatergoods.meapp.domain.interfaces

import com.greatergoods.meapp.features.common.model.ActionButton
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Toast
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for handling dialog queues in the app.
 */
interface IDialogQueueHandler {
    /**
     * Get the current dialog as a StateFlow
     */
    val currentDialog: StateFlow<DialogModel?>

    /**
     * Enqueue an alert dialog
     */
    fun enqueueAlert(
        title: String,
        message: String,
        dismissText: String = "OK",
        onDismiss: () -> Unit,
        priority: Int = 100,
        delayMillis: Long = 0L
    )

    /**
     * Enqueue a confirmation dialog
     */
    fun enqueueConfirm(
        title: String,
        message: String,
        confirmText: String = "Yes",
        cancelText: String = "No",
        onConfirm: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null,
        onDismiss: () -> Unit,
        priority: Int = 100,
        delayMillis: Long = 0L
    )

    /**
     * Enqueue a custom dialog with arbitrary parameters
     */
    fun enqueueCustomDialog(
        contentKey: String,
        params: Map<String, Any?> = emptyMap(),
        onDismiss: () -> Unit,
        priority: Int = 100,
        delayMillis: Long = 0L
    )

    /**
     * Dismiss the current dialog and optionally show the next one
     */
    fun dismissCurrent(showNext: Boolean = true)

    /**
     * Clear all dialogs from the queue
     */
    fun clear()

    /**
     * Get the number of dialogs in the queue
     */
    fun getQueueSize(): Int

    /**
     * Check if there are any dialogs in the queue
     */
    fun hasPendingDialogs(): Boolean

    /**
     * Get the next dialog in the queue without dequeuing it
     */
    fun peekNextDialog(): DialogModel?

    /**
     * Update the priority of the current dialog
     */
    fun updateCurrentDialogPriority(priority: Int)

    /**
     * Update the delay of the current dialog
     */
    fun updateCurrentDialogDelay(delayMillis: Long)
    fun enqueueToast(
        message: String,
        title: String?,
        action: ActionButton? = null
    )

    val currentToast: StateFlow<Toast?>
    fun dismissToast()
}

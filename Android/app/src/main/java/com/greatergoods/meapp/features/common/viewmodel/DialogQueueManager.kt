package com.greatergoods.meapp.features.common.viewmodel

import com.greatergoods.meapp.features.common.model.DialogModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.PriorityQueue

/**
 * Manages a priority queue of dialogs, ensuring only one dialog is shown at a time.
 * Supports enqueueing, showing, and dismissing dialogs in priority order (lower number = higher priority).
 * Integrates with Jetpack Compose via StateFlow for reactive UI updates.
 * Supports delay between dialogs via delayMillis property.
 */
class DialogQueueManager {
    // Internal priority queue for dialog requests (lower priority number = higher priority)
    private val dialogQueue: PriorityQueue<DialogModel> = PriorityQueue()

    // StateFlow for the currently visible dialog (null if none)
    private val _currentDialog = MutableStateFlow<DialogModel?>(null)

    /**
     * The currently visible dialog, or null if none.
     * Observe this in Compose to display dialogs reactively.
     */
    val currentDialog: StateFlow<DialogModel?> = _currentDialog.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Enqueues a dialog to be shown. If no dialog is currently visible, shows it immediately.
     * @param dialog The dialog to enqueue.
     */
    fun enqueue(dialog: DialogModel) {
        dialogQueue.add(dialog)
        if (_currentDialog.value == null) {
            showNext()
        }
    }

    /**
     * Dismisses the currently visible dialog and shows the next one in the queue, if any.
     * If the dialog has a delayMillis > 0, waits for the specified delay before showing the next dialog.
     */
    fun dismissCurrent() {
        val dismissed = _currentDialog.value
        if (dialogQueue.isNotEmpty()) {
            dialogQueue.remove(dismissed)
        }
        if (dismissed?.delayMillis ?: 0L > 0L) {
            scope.launch {
                delay(dismissed!!.delayMillis)
                showNext()
            }
        } else {
            showNext()
        }
    }

    /**
     * Cancels all dialogs and clears the queue.
     */
    fun clear() {
        dialogQueue.clear()
        _currentDialog.value = null
    }

    /**
     * Shows the next dialog in the queue, or sets current to null if queue is empty.
     * Called after a dialog is dismissed and any delay has elapsed.
     */
    private fun showNext() {
        _currentDialog.value = dialogQueue.peek()
    }
}

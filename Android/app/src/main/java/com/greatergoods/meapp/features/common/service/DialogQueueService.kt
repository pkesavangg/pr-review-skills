package com.greatergoods.meapp.features.common.service

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
 * Service for managing a global dialog queue with priority and delay support.
 * Exposes enqueue, dismiss, clear, and currentDialog as StateFlow for Compose integration.
 */
class DialogQueueService {
    private val dialogQueue: PriorityQueue<DialogModel> = PriorityQueue()
    private val _currentDialog = MutableStateFlow<DialogModel?>(null)
    val currentDialog: StateFlow<DialogModel?> = _currentDialog.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Enqueue a dialog. If no dialog is showing, show immediately.
     */
    fun enqueue(dialog: DialogModel) {
        dialogQueue.add(dialog)
        if (_currentDialog.value == null) {
            showNext()
        }
    }

    /**
     * Dismiss the current dialog and show the next one after delayMillis.
     */
    fun dismissCurrent() {
        val dismissed = _currentDialog.value
        if (dialogQueue.isNotEmpty()) {
            dialogQueue.remove(dismissed)
        }
        if ((dismissed?.delayMillis ?: 0L) > 0L) {
            scope.launch {
                delay(dismissed!!.delayMillis)
                showNext()
            }
        } else {
            showNext()
        }
    }

    /**
     * Clear all dialogs and reset state.
     */
    fun clear() {
        dialogQueue.clear()
        _currentDialog.value = null
    }

    /**
     * Show the next dialog in the queue, or null if empty.
     */
    private fun showNext() {
        _currentDialog.value = dialogQueue.peek()
    }
}

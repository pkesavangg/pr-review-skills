package com.greatergoods.meapp.features.common.service

import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Loader
import com.greatergoods.meapp.features.common.model.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.PriorityQueue
import javax.inject.Inject

/**
 * Service for managing a global dialog queue with priority and delay support.
 * Exposes enqueue, dismiss, clear, and currentDialog as StateFlow for Compose integration.
 */
class DialogQueueService
    @Inject
    constructor() : IDialogQueueService {
        private val dialogQueue: PriorityQueue<DialogModel> = PriorityQueue()
        private val _currentDialog = MutableStateFlow<DialogModel?>(null)
        override val currentDialog: StateFlow<DialogModel?> = _currentDialog.asStateFlow()

    private val _currentToast = MutableStateFlow<Toast?>(null)
    override val currentToast: StateFlow<Toast?> = _currentToast.asStateFlow()

    private val _loader = MutableStateFlow<Loader?>(null)
    override val loader: StateFlow<Loader?> = _loader.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Main)

        /**
         * Enqueue a dialog. If no dialog is showing, show immediately.
         */
        override fun enqueue(dialog: DialogModel) {
            dialogQueue.add(dialog)
            if (_currentDialog.value == null) {
                _currentDialog.value = dialogQueue.peek()
            }
        }

        /**
         * Show an toast.
         */
        override fun showToast(dialog: Toast) {
            _currentToast.value = dialog
        }

    /**
     * Show a loader.
     */
    override fun showLoader(loader: Loader) {
        _loader.value = loader
    }

    /**
     * Dismiss the current loader if it exists.
     */
    override fun dismissLoader() {
        _loader.value = null
    }

    /**
     * Dismiss the current toast if it exists.
     */
    override fun dismissToast() {
        _currentToast.value = null
    }

        /**
         * Dismiss the current dialog and show the next one after delayMillis.
         */
        override fun dismissCurrent() {
            val dismissed = _currentDialog.value
            if (dialogQueue.isNotEmpty()) {
                dialogQueue.remove(dismissed)
                _currentDialog.value = null
                showNext()
            }
        }

        /**
         * Clear all dialogs and reset state.
         */
        override fun clear() {
            dialogQueue.clear()
            _currentDialog.value = null
        }

        /**
         * Get the current queue size (excluding current dialog)
         */
        override fun getQueueSize(): Int = dialogQueue.size

        /**
         * Get the next dialog in the queue without removing it
         */
        override fun peekNextDialog(): DialogModel? {
            val current = _currentDialog.value
            return dialogQueue.peek()?.takeUnless { it == current }
        }

        /**
         * Show the next dialog in the queue, or null if empty.
         */
        private fun showNext() {
            if (dialogQueue.isNotEmpty()) {
                val next = dialogQueue.peek()
                scope.launch {
                    delay(next!!.delayMillis)
                    _currentDialog.value = next
                }
            }
        }
    }

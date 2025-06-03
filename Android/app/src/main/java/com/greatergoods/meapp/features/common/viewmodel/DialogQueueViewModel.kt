package com.greatergoods.meapp.features.common.viewmodel

import androidx.lifecycle.ViewModel
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.DialogQueueService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel wrapper for DialogQueueService, exposing dialog queue operations and state for Compose.
 * Use this ViewModel in screens or globally to push dialogs.
 */
@HiltViewModel
class DialogQueueViewModel
    @Inject
    constructor(
        private val dialogQueueService: DialogQueueService,
    ) : ViewModel() {
        val currentDialog: StateFlow<DialogModel?> = dialogQueueService.currentDialog

        fun enqueue(dialog: DialogModel) = dialogQueueService.enqueue(dialog)

        fun dismissCurrent() = dialogQueueService.dismissCurrent()

        fun clear() = dialogQueueService.clear()
    }

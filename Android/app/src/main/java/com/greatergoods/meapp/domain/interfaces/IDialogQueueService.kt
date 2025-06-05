package com.greatergoods.meapp.domain.interfaces

import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Toast
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for dialog and toast queue management service.
 */
interface IDialogQueueService {
    val currentDialog: StateFlow<DialogModel?>
    val currentToast: StateFlow<Toast?>
    fun enqueue(dialog: DialogModel)
    fun showToast(dialog: Toast)
    fun dismissToast()
    fun dismissCurrent()
    fun clear()
    fun getQueueSize(): Int
    fun peekNextDialog(): DialogModel?
}

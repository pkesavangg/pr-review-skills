package com.dmdbrands.gurus.weight.domain.interfaces

import com.dmdbrands.gurus.weight.features.common.components.LoaderStyle
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Loader
import com.dmdbrands.gurus.weight.features.common.model.Toast
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for dialog and toast queue management service.
 */
interface IDialogQueueService {
    val currentDialog: StateFlow<DialogModel?>
    val currentToast: StateFlow<Toast?>
    val loader: StateFlow<Loader?>
    fun enqueue(dialog: DialogModel)
    fun showToast(dialog: Toast)
    fun showLoader(
        message: String,
        style: LoaderStyle = LoaderStyle.DASHED,
    )

    fun showDialog(
        dialog: DialogModel,
    )

    fun dismissLoader()
    fun dismissToast()
    fun dismissCurrent()
    fun clear()
    fun getQueueSize(): Int
    fun peekNextDialog(): DialogModel?
}

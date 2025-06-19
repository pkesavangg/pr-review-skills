package com.greatergoods.meapp.domain.interfaces

import com.greatergoods.meapp.features.common.components.LoaderConfig
import com.greatergoods.meapp.features.common.components.LoaderDefaults
import com.greatergoods.meapp.features.common.components.LoaderStyle
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Loader
import com.greatergoods.meapp.features.common.model.Toast
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
        config: LoaderConfig = LoaderDefaults.defaultFor(style)
    )

    fun dismissLoader()
    fun dismissToast()
    fun dismissCurrent()
    fun clear()
    fun getQueueSize(): Int
    fun peekNextDialog(): DialogModel?
}

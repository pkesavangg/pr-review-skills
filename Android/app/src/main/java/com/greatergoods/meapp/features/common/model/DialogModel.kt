package com.greatergoods.meapp.features.common.model

import com.greatergoods.meapp.features.common.components.LoaderConfig
import com.greatergoods.meapp.features.common.components.LoaderDefaults
import com.greatergoods.meapp.features.common.components.LoaderStyle

/**
 * Represents a dialog request in the dialog queue system.
 * Supports alert, confirmation, and custom dialog types. Extensible for future dialog variants.
 *
 * @property priority Lower number is shown first (higher priority).
 * @property delayMillis Delay in milliseconds before showing the next dialog after dismissal.
 */
sealed class DialogModel(
    val priority: Int = 100,
    val delayMillis: Long = 0L
) : Comparable<DialogModel> {
    /**
     * Simple alert dialog with a title, message, and a single dismiss button.
     * @param title The dialog title.
     * @param message The dialog message.
     * @param dismissText The text for the dismiss button.
     * @param onDismiss Callback when the dialog is dismissed (must be called when closed).
     * @param priority Dialog priority (lower = higher priority).
     * @param delayMillis Delay before next dialog (ms).
     */
    data class Alert(
        val title: String,
        val message: String,
        val dismissText: String = "OK",
        val onDismiss: () -> Unit,
        val alertPriority: Int = 100,
        val alertDelayMillis: Long = 0L
    ) : DialogModel(priority = alertPriority, delayMillis = alertDelayMillis)

    /**
     * Confirmation dialog with title, message, confirm and cancel buttons.
     * @param title The dialog title.
     * @param message The dialog message.
     * @param confirmText The text for the confirm button.
     * @param cancelText The text for the cancel button.
     * @param onConfirm Callback when confirmed.
     * @param onCancel Callback when cancelled.
     * @param onDismiss Callback when the dialog is dismissed (must be called when closed).
     * @param priority Dialog priority (lower = higher priority).
     * @param delayMillis Delay before next dialog (ms).
     */
    data class Confirm(
        val title: String,
        val message: String,
        val confirmText: String = "Yes",
        val cancelText: String = "No",
        val onConfirm: (() -> Unit)? = null,
        val onCancel: (() -> Unit)? = null,
        val onDismiss: () -> Unit,
        val confirmPriority: Int = 100,
        val confirmDelayMillis: Long = 0L
    ) : DialogModel(priority = confirmPriority, delayMillis = confirmDelayMillis)

    /**
     * Custom dialog for advanced or composable content.
     * @param contentKey A unique key for the custom dialog type.
     * @param params Arbitrary parameters for the custom dialog.
     * @param onDismiss Callback when the dialog is dismissed (must be called when closed).
     * @param priority Dialog priority (lower = higher priority).
     * @param delayMillis Delay before next dialog (ms).
     */
    data class Custom(
        val contentKey: String,
        val params: Map<String, Any?> = emptyMap(),
        val onDismiss: () -> Unit,
        val customPriority: Int = 100,
        val customDelayMillis: Long = 0L
    ) : DialogModel(priority = customPriority, delayMillis = customDelayMillis)

    override fun compareTo(other: DialogModel): Int = this.priority.compareTo(other.priority)
}

data class Toast(
    val message: String,
    val title: String? = null,
    val action: ActionButton? = null,
)

data class Loader(
    val message: String,
    val style: LoaderStyle = LoaderStyle.CIRCULAR,
    val config: LoaderConfig = LoaderDefaults.defaultFor(style),
)

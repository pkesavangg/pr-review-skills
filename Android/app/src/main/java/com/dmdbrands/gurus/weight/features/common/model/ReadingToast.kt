package com.dmdbrands.gurus.weight.features.common.model

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.features.common.strings.ReadingToastStrings

data class ReadingToast(
    val value: String,
    val unit: String,
    val secondaryValue: String? = null,
    val secondaryUnit: String? = null,
    val pulse: Int? = null,
    val timestamp: String,
    val type: ProductType,
    val assignedTo: String? = null,
    val assignedName: String? = null,
    val onAssign: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onReassign: () -> Unit = {},
) : ToastContent {
    override val message: String
        get() = "${ReadingToastStrings.title(type)} · $value $unit"
}

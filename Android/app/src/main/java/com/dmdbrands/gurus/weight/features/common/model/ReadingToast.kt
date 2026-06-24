package com.dmdbrands.gurus.weight.features.common.model

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.features.common.strings.ReadingToastStrings

data class ReadingToast(
    val reading: String,
    val type: ProductType,
    val timestamp: String,
    val assignedTo: String? = null,
    val noBabyProfile: Boolean = false,
    val primaryAction: () -> Unit = {},
    val secondaryAction: () -> Unit = {},
) : ToastContent {
    override val message: String
        get() = "${ReadingToastStrings.title(type)} · $reading"
}

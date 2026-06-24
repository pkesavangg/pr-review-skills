package com.dmdbrands.gurus.weight.features.common.model

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.features.common.strings.ReadingToastStrings

data class ReadingToast(
    val reading: String,
    val type: ProductType,
    val timestamp: String,
    val assignedTo: String? = null,
    val noBabyProfile: Boolean = false,
    /**
     * Name of the single existing baby. When set, the arrival card shows
     * "New Reading Received for <NAME>" with SAVE/DISCARD instead of the assign picker (MOB-598).
     */
    val assignTargetName: String? = null,
    /**
     * Post-assignment only: when true there is no other baby to reassign to, so the card's
     * action becomes "Assign to new baby" and routes into the Add-a-Baby flow (MOB-598).
     */
    val assignToNewBaby: Boolean = false,
    /**
     * Number of additional readings buffered this session beyond the one shown. When > 0 the
     * card surfaces a "<N> more readings received… VIEW" pill that opens History (MOB-598).
     */
    val additionalCount: Int = 0,
    val primaryAction: () -> Unit = {},
    val secondaryAction: () -> Unit = {},
    /** Opens the History tab from the "VIEW" pill. */
    val onView: () -> Unit = {},
    /**
     * Invoked when the card auto-dismisses without user action. Used to auto-assign a
     * multi-baby reading to the last-assigned baby after the display window (MOB-598).
     */
    val onTimeout: (() -> Unit)? = null,
) : ToastContent {
    override val message: String
        get() = "${ReadingToastStrings.title(type)} · $reading"
}

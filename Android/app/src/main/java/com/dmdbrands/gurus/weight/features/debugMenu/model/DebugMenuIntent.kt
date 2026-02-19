package com.dmdbrands.gurus.weight.features.debugMenu.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import android.app.Activity

/**
 * Sealed class representing all possible intents/actions for the Debug Menu screen.
 * Based on Angular cs-menu.page actions.
 */
sealed class DebugMenuIntent : IReducer.Intent {
    data object OnBack : DebugMenuIntent()
    data object SendLogs : DebugMenuIntent()
    data object ResyncEntries : DebugMenuIntent()
    data class ClearAllData(val onDismiss: () -> Unit) : DebugMenuIntent()
    data object SendScaleLogs : DebugMenuIntent()
    /** Send scale log for the selected [device] (from picker). */
    data class SendScaleLogForScale(val device: Device) : DebugMenuIntent()
    /** Set BtWifiR4 scale list (e.g. from pairedScales); reducer maps/sorts like AddScale SetSavedScales. */
    data class SetScaleList(val scales: List<Device>) : DebugMenuIntent()
    data object ShowAppReview: DebugMenuIntent()
    data class ShowAppReviewWithActivity(val activity: Activity) : DebugMenuIntent()
}

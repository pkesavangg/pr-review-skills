package com.dmdbrands.gurus.weight.features.debugMenu.model

import android.app.Activity
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer

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
    data object ShowAppReview: DebugMenuIntent()
    data class ShowAppReviewWithActivity(val activity: Activity) : DebugMenuIntent()
}

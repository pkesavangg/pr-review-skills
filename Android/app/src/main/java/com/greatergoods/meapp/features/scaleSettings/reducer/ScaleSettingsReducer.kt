package com.greatergoods.meapp.features.scaleSettings.reducer

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.Device

/**
 * State for ScaleSettingsScreen.
 */
data class ScaleSettingsState(
    val scale: Device? = null,
) : IReducer.State

/**
 * Intents for ScaleSettingsScreen actions.
 */
sealed interface ScaleSettingsIntent : IReducer.Intent {
    data class SetScaleInfo(
        val scale: Device,
    ) : ScaleSettingsIntent

    object EditName : ScaleSettingsIntent

    object DeleteScale : ScaleSettingsIntent

    object OpenProductGuide : ScaleSettingsIntent

    object Back : ScaleSettingsIntent
}

/**
 * Reducer for ScaleSettingsScreen.
 */
class ScaleSettingsReducer : IReducer<ScaleSettingsState, ScaleSettingsIntent> {
    override fun reduce(
        state: ScaleSettingsState,
        intent: ScaleSettingsIntent,
    ): ScaleSettingsState? =
        when (intent) {
            is ScaleSettingsIntent.SetScaleInfo -> state.copy(scale = intent.scale)
            ScaleSettingsIntent.EditName -> state.copy()
            ScaleSettingsIntent.DeleteScale -> state.copy()
            ScaleSettingsIntent.OpenProductGuide -> state.copy()
            ScaleSettingsIntent.Back -> state.copy()
        }
}

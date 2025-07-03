package com.greatergoods.meapp.features.scaleSettings.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.common.model.ScaleInfo

/**
 * State for ScaleSettingsScreen.
 */
data class ScaleSettingsState(
    val scaleInfo: ScaleInfo = ScaleInfo(
        productName = "AccuCheck Verve Smart Scale",
        sku = "0412",
        imgPath = null,
        setupType = com.greatergoods.meapp.features.common.enums.ScaleSetupType.BtWifiR4,
        bodyComp = true,
        isConnected = true,
        isWifiConfigured = true
    )
) : IReducer.State

/**
 * Intents for ScaleSettingsScreen actions.
 */
sealed interface ScaleSettingsIntent : IReducer.Intent {
    object EditName : ScaleSettingsIntent
    object DeleteScale : ScaleSettingsIntent
    object OpenProductGuide : ScaleSettingsIntent
    object Back : ScaleSettingsIntent
}

/**
 * Reducer for ScaleSettingsScreen.
 */
class ScaleSettingsReducer : IReducer<ScaleSettingsState, ScaleSettingsIntent> {
    override fun reduce(state: ScaleSettingsState, intent: ScaleSettingsIntent): ScaleSettingsState? = when (intent) {
        ScaleSettingsIntent.EditName -> state.copy()
        ScaleSettingsIntent.DeleteScale -> state.copy()
        ScaleSettingsIntent.OpenProductGuide -> state.copy()
        ScaleSettingsIntent.Back -> state.copy()
    }
} 
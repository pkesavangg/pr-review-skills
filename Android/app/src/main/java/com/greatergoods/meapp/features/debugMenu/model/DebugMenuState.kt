package com.greatergoods.meapp.features.debugMenu.model

import com.greatergoods.meapp.domain.interfaces.IReducer

/**
 * State data class for the Debug Menu screen.
 * Contains all the information needed to display debug information.
 */
data class DebugMenuState(
    val appVersion: String = "",
    val isNative: Boolean = true,
    val isAndroid: Boolean = true,
    val apiUrl: String = "",
    val currentDateTime: String = "",
    val timezone: String = "",
    val timezoneOffset: String = "",
    val hasScales: Boolean = false,
    val isSendScaleLogEnabled: Boolean = false,
    val isLoading: Boolean = false
) : IReducer.State

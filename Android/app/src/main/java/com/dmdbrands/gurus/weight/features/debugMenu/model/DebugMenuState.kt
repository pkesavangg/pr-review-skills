package com.dmdbrands.gurus.weight.features.debugMenu.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * State data class for the Debug Menu screen.
 * Contains all the information needed to display debug information.
 */
@Stable
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
    val isLoading: Boolean = false,
    /** When true, show scale picker to choose which scale to send logs for (multiple scales). */
    val scaleLogsPickerScales: ImmutableList<Device> = persistentListOf(),
    /** BtWifiR4 scales (devices) for ScaleLogsPickerScreen click; same order as [scaleListScaleInfo]. */
    val scaleList: ImmutableList<Device> = persistentListOf(),
    /** BtWifiR4 scales as ScaleInfo for display (mapped + sorted in reducer, like AddScale savedScales). */
    val scaleListScaleInfo: ImmutableList<ScaleInfo> = persistentListOf(),
) : IReducer.State

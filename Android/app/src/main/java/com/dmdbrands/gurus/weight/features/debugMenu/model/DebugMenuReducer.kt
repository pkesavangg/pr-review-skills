package com.dmdbrands.gurus.weight.features.debugMenu.model

import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.features.common.helper.ScaleDataHelper.toScaleInfo
import kotlinx.collections.immutable.toImmutableList

/**
 * Reducer for Debug Menu screen state transitions.
 * Handles all state changes based on intents.
 */
class DebugMenuReducer : IReducer<DebugMenuState, DebugMenuIntent> {
    /**
     * Reduces the current state and intent to a new state.
     * @param state The current state.
     * @param intent The intent/action to handle.
     * @return The new state after applying the intent.
     */
    override fun reduce(
        state: DebugMenuState,
        intent: DebugMenuIntent,
    ): DebugMenuState =
        when (intent) {
            is DebugMenuIntent.OnBack -> {
                state.copy(isLoading = false)
            }

            is DebugMenuIntent.SendLogs -> {
                state.copy(isLoading = true)
            }

            is DebugMenuIntent.ResyncEntries -> {
                state.copy(isLoading = true)
            }

            is DebugMenuIntent.ClearAllData -> {
                state.copy(isLoading = true)
            }

            is DebugMenuIntent.SendScaleLogs -> {
                state.copy(isLoading = true)
            }

            is DebugMenuIntent.SendScaleLogForScale -> {
                state.copy(isLoading = true)
            }

            is DebugMenuIntent.SetScaleList -> {
                val scaleInfos = intent.scales
                    .map { it.toScaleInfo() }
                    .sortedByDescending { scaleInfo ->
                        DateTimeConverter.isoToTimestamp(scaleInfo.createdAt)
                    }
                val devicesById = intent.scales.associateBy { it.id }
                val devicesInOrder = scaleInfos.mapNotNull { si ->
                    si.scaleId?.let { scaleId -> devicesById[scaleId] }
                }
                state.copy(
                    scaleList = devicesInOrder.toImmutableList(),
                    scaleListScaleInfo = scaleInfos.toImmutableList(),
                    hasScales = intent.scales.isNotEmpty(),
                    isSendScaleLogEnabled = intent.scales.isNotEmpty() &&
                        (intent.scales.size > 1 ||
                            intent.scales.singleOrNull()?.connectionStatus == BLEStatus.CONNECTED),
                )
            }

          is DebugMenuIntent.ShowAppReview -> {
            state.copy(isLoading = true)
          }

          is DebugMenuIntent.ShowAppReviewWithActivity -> {
            state.copy(isLoading = true)
          }
        }
}

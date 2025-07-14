package com.greatergoods.meapp.features.scaleDisplayMetrics.reducer

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.Device

/**
 * State for ScaleDisplayMetricsScreen.
 */
data class ScaleDisplayMetricsState(
  val scale: Device? = null,
  val enabledMetrics: List<String> = emptyList(),
  val hasUpdated: Boolean = false,
) : IReducer.State

/**
 * Intents for ScaleDisplayMetricsScreen actions.
 */
sealed interface ScaleDisplayMetricsIntent : IReducer.Intent {
  data class SetScale(
    val scale: Device,
  ) : ScaleDisplayMetricsIntent

  data class UpdateMetrics(
    val enabledMetrics: List<String>,
  ) : ScaleDisplayMetricsIntent

  object UpdateScaleMode : ScaleDisplayMetricsIntent

  object Back : ScaleDisplayMetricsIntent

  object Save : ScaleDisplayMetricsIntent
}

/**
 * Reducer for ScaleDisplayMetricsScreen.
 */
class ScaleDisplayMetricsReducer : IReducer<ScaleDisplayMetricsState, ScaleDisplayMetricsIntent> {
  override fun reduce(
    state: ScaleDisplayMetricsState,
    intent: ScaleDisplayMetricsIntent,
  ): ScaleDisplayMetricsState? =
    when (intent) {
      is ScaleDisplayMetricsIntent.SetScale -> {
        val displayMetrics = intent.scale.preferences?.displayMetrics ?: emptyList()
        state.copy(
          scale = intent.scale,
          enabledMetrics = displayMetrics,
          hasUpdated = false,
        )
      }

      is ScaleDisplayMetricsIntent.UpdateMetrics -> {
        val originalMetrics = state.scale?.preferences?.displayMetrics ?: emptyList()
        val hasChanges = intent.enabledMetrics != originalMetrics
        state.copy(
          enabledMetrics = intent.enabledMetrics,
          hasUpdated = hasChanges,
        )
      }

      ScaleDisplayMetricsIntent.Save -> state.copy(hasUpdated = false)
      else -> state.copy()
    }
}

package com.dmdbrands.gurus.weight.features.scaleDisplayMetrics.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * State for ScaleDisplayMetricsScreen.
 */
@Stable
data class ScaleDisplayMetricsState(
  val scale: Device? = null,
  val enabledMetrics: ImmutableList<String> = persistentListOf(),
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
          enabledMetrics = displayMetrics.toImmutableList(),
          hasUpdated = false,
        )
      }

      is ScaleDisplayMetricsIntent.UpdateMetrics -> {
        val originalMetrics = state.scale?.preferences?.displayMetrics ?: emptyList()
        val hasChanges = intent.enabledMetrics != originalMetrics
        state.copy(
          enabledMetrics = intent.enabledMetrics.toImmutableList(),
          hasUpdated = hasChanges,
        )
      }

      ScaleDisplayMetricsIntent.Save -> state.copy(hasUpdated = false)
      else -> state.copy()
    }
}

package com.dmdbrands.gurus.weight.features.deviceDisplayMetrics.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * State for DeviceDisplayMetricsScreen.
 */
@Stable
data class DeviceDisplayMetricsState(
  val scale: Device? = null,
  val enabledMetrics: ImmutableList<String> = persistentListOf(),
  val hasUpdated: Boolean = false,
) : IReducer.State

/**
 * Intents for DeviceDisplayMetricsScreen actions.
 */
sealed interface DeviceDisplayMetricsIntent : IReducer.Intent {
  data class SetScale(
    val scale: Device,
  ) : DeviceDisplayMetricsIntent

  data class UpdateMetrics(
    val enabledMetrics: List<String>,
  ) : DeviceDisplayMetricsIntent

  object UpdateScaleMode : DeviceDisplayMetricsIntent

  object Back : DeviceDisplayMetricsIntent

  object Save : DeviceDisplayMetricsIntent
}

/**
 * Reducer for DeviceDisplayMetricsScreen.
 */
class DeviceDisplayMetricsReducer : IReducer<DeviceDisplayMetricsState, DeviceDisplayMetricsIntent> {
  override fun reduce(
    state: DeviceDisplayMetricsState,
    intent: DeviceDisplayMetricsIntent,
  ): DeviceDisplayMetricsState? =
    when (intent) {
      is DeviceDisplayMetricsIntent.SetScale -> {
        val displayMetrics = intent.scale.preferences?.displayMetrics ?: emptyList()
        state.copy(
          scale = intent.scale,
          enabledMetrics = displayMetrics.toImmutableList(),
          hasUpdated = false,
        )
      }

      is DeviceDisplayMetricsIntent.UpdateMetrics -> {
        val originalMetrics = state.scale?.preferences?.displayMetrics ?: emptyList()
        val hasChanges = intent.enabledMetrics != originalMetrics
        state.copy(
          enabledMetrics = intent.enabledMetrics.toImmutableList(),
          hasUpdated = hasChanges,
        )
      }

      DeviceDisplayMetricsIntent.Save -> state.copy(hasUpdated = false)
      else -> state.copy()
    }
}

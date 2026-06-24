package com.dmdbrands.gurus.weight.features.scaleMode.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import androidx.compose.runtime.Stable

/**
 * State for ScaleModeScreen.
 */
@Stable
data class ScaleModeState(
  val scale: Device? = null,
  val isAllBodyMetrics: Boolean = true,
  val isHeartRateOn: Boolean = false,
  val hasModeChanged: Boolean = false,
) : IReducer.State

/**
 * Intents for ScaleModeScreen actions.
 */
sealed interface ScaleModeIntent : IReducer.Intent {
  data class SetScale(
    val scale: Device,
  ) : ScaleModeIntent

  data class SetMode(
    val isAllBodyMetrics: Boolean,
    val hasModeChanged: Boolean,
  ) : ScaleModeIntent

  data class SetHeartRate(
    val isHeartRateOn: Boolean,
    val hasModeChanged: Boolean,
  ) : ScaleModeIntent

  object Back : ScaleModeIntent

  object Save : ScaleModeIntent

  object OpenBiaModal : ScaleModeIntent
}

/**
 * Reducer for ScaleModeScreen.
 */
class ScaleModeReducer : IReducer<ScaleModeState, ScaleModeIntent> {
  override fun reduce(
    state: ScaleModeState,
    intent: ScaleModeIntent,
  ): ScaleModeState? =
    when (intent) {
      is ScaleModeIntent.SetScale -> state.copy(scale = intent.scale)
      is ScaleModeIntent.SetMode ->
        state.copy(
          isAllBodyMetrics = intent.isAllBodyMetrics,
          hasModeChanged = intent.hasModeChanged,
        )

      is ScaleModeIntent.SetHeartRate ->
        state.copy(
          isHeartRateOn = intent.isHeartRateOn,
          hasModeChanged = intent.hasModeChanged,
        )

      ScaleModeIntent.Save -> state.copy()
      else -> state.copy()
    }
}

package com.dmdbrands.gurus.weight.features.deviceMode.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import androidx.compose.runtime.Stable

/**
 * State for DeviceModeScreen.
 */
@Stable
data class DeviceModeState(
  val scale: Device? = null,
  val isAllBodyMetrics: Boolean = true,
  val isHeartRateOn: Boolean = false,
  val hasModeChanged: Boolean = false,
) : IReducer.State

/**
 * Intents for DeviceModeScreen actions.
 */
sealed interface DeviceModeIntent : IReducer.Intent {
  data class SetScale(
    val scale: Device,
  ) : DeviceModeIntent

  data class SetMode(
    val isAllBodyMetrics: Boolean,
    val hasModeChanged: Boolean,
  ) : DeviceModeIntent

  data class SetHeartRate(
    val isHeartRateOn: Boolean,
    val hasModeChanged: Boolean,
  ) : DeviceModeIntent

  object Back : DeviceModeIntent

  object Save : DeviceModeIntent

  object OpenBiaModal : DeviceModeIntent
}

/**
 * Reducer for DeviceModeScreen.
 */
class DeviceModeReducer : IReducer<DeviceModeState, DeviceModeIntent> {
  override fun reduce(
    state: DeviceModeState,
    intent: DeviceModeIntent,
  ): DeviceModeState? =
    when (intent) {
      is DeviceModeIntent.SetScale -> state.copy(scale = intent.scale)
      is DeviceModeIntent.SetMode ->
        state.copy(
          isAllBodyMetrics = intent.isAllBodyMetrics,
          hasModeChanged = intent.hasModeChanged,
        )

      is DeviceModeIntent.SetHeartRate ->
        state.copy(
          isHeartRateOn = intent.isHeartRateOn,
          hasModeChanged = intent.hasModeChanged,
        )

      DeviceModeIntent.Save -> state.copy()
      else -> state.copy()
    }
}

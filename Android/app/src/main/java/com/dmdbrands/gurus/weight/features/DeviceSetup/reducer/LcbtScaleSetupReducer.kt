package com.dmdbrands.gurus.weight.features.DeviceSetup.reducer

import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.LcbtScaleSetupStep
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

val initialSteps: ImmutableList<LcbtScaleSetupStep> = persistentListOf(
  LcbtScaleSetupStep.SCALE_INFO,
  LcbtScaleSetupStep.PERMISSIONS,
  LcbtScaleSetupStep.WAKEUP,
  LcbtScaleSetupStep.CONNECTING_BLUETOOTH,
  LcbtScaleSetupStep.SETUP_FINISHED,
)

/**
 * State for LcbtScaleSetupScreen.
 */
@Stable
data class LCBTScaleSetupState(
  override val scaleSetupState: DeviceSetupState<LcbtScaleSetupStep> = DeviceSetupState(
    setupState = SetupState(
      step = LcbtScaleSetupStep.SCALE_INFO,
    ),
    steps = initialSteps,
  ),
) : BaseState<LcbtScaleSetupStep, LCBTScaleSetupState> {
  override fun copyBaseState(scaleSetupState: DeviceSetupState<LcbtScaleSetupStep>): LCBTScaleSetupState {
    return this.copy(scaleSetupState = scaleSetupState)
  }
}

/**
 * Intents for LcbtScaleSetupScreen actions.
 */
sealed interface LcbtScaleSetupIntent : DeviceSetupIntent

/**
 * Reducer for LcbtScaleSetupScreen.
 */
class LcbtScaleSetupReducer : DeviceSetupReducer<LcbtScaleSetupStep, LCBTScaleSetupState>() {
  override fun reduce(state: LCBTScaleSetupState, intent: DeviceSetupIntent): LCBTScaleSetupState? {
    return when (intent) {
      else -> super.reduce(state, intent)
    }
  }
}



package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.LcbtScaleSetupStep

val initialSteps: List<LcbtScaleSetupStep> = listOf(
  LcbtScaleSetupStep.SCALE_INFO,
  LcbtScaleSetupStep.PERMISSIONS,
  LcbtScaleSetupStep.WAKEUP,
  LcbtScaleSetupStep.CONNECTING_BLUETOOTH,
  LcbtScaleSetupStep.SETUP_FINISHED,
)

/**
 * State for LcbtScaleSetupScreen.
 */
data class LCBTScaleSetupState(
  override val scaleSetupState: ScaleSetupState<LcbtScaleSetupStep> = ScaleSetupState(
    setupState = SetupState(
      step = LcbtScaleSetupStep.SCALE_INFO,
    ),
    steps = initialSteps,
  ),
) : BaseState<LcbtScaleSetupStep, LCBTScaleSetupState> {
  override fun copyBaseState(scaleSetupState: ScaleSetupState<LcbtScaleSetupStep>): LCBTScaleSetupState {
    return this.copy(scaleSetupState = scaleSetupState)
  }
}

/**
 * Intents for LcbtScaleSetupScreen actions.
 */
sealed interface LcbtScaleSetupIntent : ScaleSetupIntent

/**
 * Reducer for LcbtScaleSetupScreen.
 */
class LcbtScaleSetupReducer : ScaleSetupReducer<LcbtScaleSetupStep, LCBTScaleSetupState>() {
  override fun reduce(state: LCBTScaleSetupState, intent: ScaleSetupIntent): LCBTScaleSetupState? {
    return when (intent) {
      else -> super.reduce(state, intent)
    }
  }
}



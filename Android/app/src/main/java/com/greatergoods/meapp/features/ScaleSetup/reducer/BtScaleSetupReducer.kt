package com.greatergoods.meapp.features.ScaleSetup.reducer

import com.greatergoods.meapp.features.ScaleSetup.enums.BtScaleSetupStep

/**
 * State for BtScaleSetupScreen.
 */
data class BtScaleSetupState(
  override val scaleSetupState: ScaleSetupState<BtScaleSetupStep> = ScaleSetupState(
    setupState = SetupState(BtScaleSetupStep.SCALE_INFO),
    steps = listOf(
      BtScaleSetupStep.SCALE_INFO,
      BtScaleSetupStep.PERMISSIONS,
      BtScaleSetupStep.WAKEUP,
      BtScaleSetupStep.SELECT_USER,
      BtScaleSetupStep.CONNECTING_BLUETOOTH,
      BtScaleSetupStep.SETUP_FINISHED,
    ),
  ),
  val user: String = "",
) : BaseState<BtScaleSetupStep, BtScaleSetupState> {
  override fun copyBaseState(scaleSetupState: ScaleSetupState<BtScaleSetupStep>): BtScaleSetupState {
    return this.copy(scaleSetupState = scaleSetupState)
  }
}

/**
 * Intents for BtScaleSetupScreen actions.
 */
sealed interface BtScaleSetupIntent : ScaleSetupIntent {
  data class SetUser(val user: String) : BtScaleSetupIntent
}

/**
 * Reducer for BtScaleSetupScreen.
 */
class BtScaleSetupReducer : ScaleSetupReducer<BtScaleSetupStep, BtScaleSetupState>() {
  override fun reduce(
    state: BtScaleSetupState,
    intent: ScaleSetupIntent,
  ): BtScaleSetupState? {
    return when (intent) {
      is BtScaleSetupIntent.SetUser -> state.copy(
        user = intent.user,
      )

      else -> super.reduce(state, intent)
    }
  }
}

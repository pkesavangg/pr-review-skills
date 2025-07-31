package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtScaleSetupStep

/**
 * State for BtScaleSetupScreen.
 */
data class BtScaleSetupState(
  override val scaleSetupState: ScaleSetupState<BtScaleSetupStep> = ScaleSetupState(
    setupState = SetupState(BtScaleSetupStep.SCALE_INFO),
    steps = listOf(
      BtScaleSetupStep.SCALE_INFO,
      BtScaleSetupStep.PERMISSIONS,
      BtScaleSetupStep.SELECT_USER,
      BtScaleSetupStep.PAIRING_MODE,
      BtScaleSetupStep.SET_DEVICE_USER,
      BtScaleSetupStep.STEP_ON,
      BtScaleSetupStep.SETUP_FINISHED,
    ),
  ),
  val user: Int? = null,
) : BaseState<BtScaleSetupStep, BtScaleSetupState> {
  override fun copyBaseState(scaleSetupState: ScaleSetupState<BtScaleSetupStep>): BtScaleSetupState {
    return this.copy(scaleSetupState = scaleSetupState)
  }

  val userString: String?
    get() = "U" + user?.toString()
}

/**
 * Intents for BtScaleSetupScreen actions.
 */
sealed interface BtScaleSetupIntent : ScaleSetupIntent {
  data class SetUser(val user: Int) : BtScaleSetupIntent
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

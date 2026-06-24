package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import androidx.compose.runtime.Stable
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.MonitorSetupStep
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Unified state for BPM monitor setup (A3 and A6 protocols).
 * For A3 SKUs, [scaleNickname] and [hasSkippedScalePairing] remain at defaults.
 */
@Stable
data class MonitorSetupState(
  override val scaleSetupState: ScaleSetupState<MonitorSetupStep> = ScaleSetupState(
    setupState = SetupState(MonitorSetupStep.MONITOR_DETAIL),
    steps = persistentListOf(MonitorSetupStep.MONITOR_DETAIL),
  ),
  val selectedUser: String? = null,
  val monitorNickname: String = "",
  val scaleNickname: String = "",
  val hasNumericUsers: Boolean = false,
  val hasSkippedScalePairing: Boolean = false,
) : BaseState<MonitorSetupStep, MonitorSetupState> {
  override fun copyBaseState(scaleSetupState: ScaleSetupState<MonitorSetupStep>): MonitorSetupState {
    return this.copy(scaleSetupState = scaleSetupState)
  }
}

/**
 * Unified intents for BPM monitor setup (A3 and A6 protocols).
 */
sealed interface MonitorSetupIntent : ScaleSetupIntent {
  data class SetSelectedUser(val user: String) : MonitorSetupIntent
  data class SetMonitorNickname(val nickname: String) : MonitorSetupIntent
  data class SetScaleNickname(val nickname: String) : MonitorSetupIntent
  data class SetHasNumericUsers(val hasNumericUsers: Boolean) : MonitorSetupIntent
  data class SetHasSkippedScalePairing(val skipped: Boolean) : MonitorSetupIntent
  data class SetSteps(val steps: ImmutableList<MonitorSetupStep>) : MonitorSetupIntent
  data object TutorialLinkClicked : MonitorSetupIntent
}

/**
 * Unified reducer for BPM monitor setup (A3 and A6 protocols).
 */
class MonitorSetupReducer : ScaleSetupReducer<MonitorSetupStep, MonitorSetupState>() {
  override fun reduce(
    state: MonitorSetupState,
    intent: ScaleSetupIntent,
  ): MonitorSetupState? {
    return when (intent) {
      is MonitorSetupIntent.SetSelectedUser -> state.copy(selectedUser = intent.user)
      is MonitorSetupIntent.SetMonitorNickname -> state.copy(monitorNickname = intent.nickname)
      is MonitorSetupIntent.SetScaleNickname -> state.copy(scaleNickname = intent.nickname)
      is MonitorSetupIntent.SetHasNumericUsers -> state.copy(hasNumericUsers = intent.hasNumericUsers)
      is MonitorSetupIntent.SetHasSkippedScalePairing -> state.copy(hasSkippedScalePairing = intent.skipped)
      is MonitorSetupIntent.SetSteps -> state.copy(
        scaleSetupState = state.scaleSetupState.copy(
          steps = intent.steps,
          setupState = state.scaleSetupState.setupState.copy(step = intent.steps.first()),
        ),
      )
      else -> super.reduce(state, intent)
    }
  }
}

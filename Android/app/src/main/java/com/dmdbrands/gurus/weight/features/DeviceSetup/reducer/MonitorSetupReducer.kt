package com.dmdbrands.gurus.weight.features.DeviceSetup.reducer

import androidx.compose.runtime.Stable
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.MonitorSetupStep
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Unified state for BPM monitor setup (A3 and A6 protocols).
 */
@Stable
data class MonitorSetupState(
  override val scaleSetupState: DeviceSetupState<MonitorSetupStep> = DeviceSetupState(
    setupState = SetupState(MonitorSetupStep.MONITOR_DETAIL),
    steps = persistentListOf(MonitorSetupStep.MONITOR_DETAIL),
  ),
  val selectedUser: String? = null,
  val monitorNickname: String = "",
  val hasNumericUsers: Boolean = false,
) : BaseState<MonitorSetupStep, MonitorSetupState> {
  override fun copyBaseState(scaleSetupState: DeviceSetupState<MonitorSetupStep>): MonitorSetupState {
    return this.copy(scaleSetupState = scaleSetupState)
  }
}

/**
 * Unified intents for BPM monitor setup (A3 and A6 protocols).
 */
sealed interface MonitorSetupIntent : DeviceSetupIntent {
  data class SetSelectedUser(val user: String) : MonitorSetupIntent
  data class SetMonitorNickname(val nickname: String) : MonitorSetupIntent
  data class SetHasNumericUsers(val hasNumericUsers: Boolean) : MonitorSetupIntent
  data class SetSteps(val steps: ImmutableList<MonitorSetupStep>) : MonitorSetupIntent
  data object TutorialLinkClicked : MonitorSetupIntent
}

/**
 * Unified reducer for BPM monitor setup (A3 and A6 protocols).
 */
class MonitorSetupReducer : DeviceSetupReducer<MonitorSetupStep, MonitorSetupState>() {
  override fun reduce(
    state: MonitorSetupState,
    intent: DeviceSetupIntent,
  ): MonitorSetupState? {
    return when (intent) {
      is MonitorSetupIntent.SetSelectedUser -> state.copy(selectedUser = intent.user)
      is MonitorSetupIntent.SetMonitorNickname -> state.copy(monitorNickname = intent.nickname)
      is MonitorSetupIntent.SetHasNumericUsers -> state.copy(hasNumericUsers = intent.hasNumericUsers)
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

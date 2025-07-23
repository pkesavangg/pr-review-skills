package com.greatergoods.meapp.features.ScaleSetup.reducer

import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.greatergoods.meapp.features.ScaleSetup.modal.ConnectionState

data class SetupState<T>(
  val step: T,
  val connectionState: ConnectionState = ConnectionState.Loading
)

/**
 * State for LcbtScaleSetupScreen.
 */
data class LCBTScaleSetupState(
  val setupState: SetupState<LcbtScaleSetupStep> = SetupState(LcbtScaleSetupStep.SCALE_INFO),
  val sku: String = "0383",
  val permissions: GGPermissionStatusMap = mutableMapOf(),
) : IReducer.State {
  companion object {
    val steps: List<LcbtScaleSetupStep> = LcbtScaleSetupStep.entries
  }

  val isLastStep: Boolean
    get() = setupState.step == steps.last()
  val isFirstStep: Boolean
    get() = setupState.step == steps.first()

  val nextStep: LcbtScaleSetupStep?
    get() = steps.getOrNull(steps.indexOf(setupState.step) + 1)
}

/**
 * Intents for LcbtScaleSetupScreen actions.
 */
sealed interface LcbtScaleSetupIntent : IReducer.Intent {

  data class SetNewStep(
    val newStep: LcbtScaleSetupStep
  ) : LcbtScaleSetupIntent

  data class AlterConnectionState(
    val connectionState: ConnectionState
  ) : LcbtScaleSetupIntent

  data class SetSku(
    val sku: String
  ) : LcbtScaleSetupIntent

  data class SetPermissions(
    val permissions: GGPermissionStatusMap
  ) : LcbtScaleSetupIntent

  data class RequestPermission(
    val permission: String
  ) : LcbtScaleSetupIntent

  object Next : LcbtScaleSetupIntent

  object Back : LcbtScaleSetupIntent

  object Skip : LcbtScaleSetupIntent
  object TryAgain : LcbtScaleSetupIntent

  data class ExitSetup(
    val isSetupFinished: Boolean,
    val isConnected: Boolean = false,
  ) : LcbtScaleSetupIntent

  object OpenHelp : LcbtScaleSetupIntent
}

/**
 * Reducer for LcbtScaleSetupScreen.
 */
class LcbtScaleSetupReducer : IReducer<LCBTScaleSetupState, LcbtScaleSetupIntent> {
  override fun reduce(
    state: LCBTScaleSetupState,
    intent: LcbtScaleSetupIntent,
  ): LCBTScaleSetupState? =
    when (intent) {
      is LcbtScaleSetupIntent.SetPermissions -> state.copy(permissions = intent.permissions)
      is LcbtScaleSetupIntent.SetSku -> state.copy(sku = intent.sku)
      is LcbtScaleSetupIntent.SetNewStep -> state.copy(setupState = SetupState(intent.newStep))
      is LcbtScaleSetupIntent.AlterConnectionState -> state.copy(
        setupState = state.setupState.copy(connectionState = intent.connectionState),
      )

      else -> state
    }
}

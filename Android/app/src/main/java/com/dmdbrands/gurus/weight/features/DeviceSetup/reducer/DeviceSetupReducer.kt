package com.dmdbrands.gurus.weight.features.DeviceSetup.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.DeviceSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Stable
data class SetupState<T>(
  val step: T,
  val connectionState: ConnectionState = ConnectionState.Loading
)

/**
 * Intentionally not sealed to allow cross-package extension by feature-specific state classes.
 * Should only be implemented by data classes that represent concrete setup flow states.
 */
interface BaseState<Step : DeviceSetupStep, S : BaseState<Step, S>> : IReducer.State {

  // Holds the core setup state for the flow and provides a type-safe copy function
// to create a new instance of the concrete state with updated setup data.
  val scaleSetupState: DeviceSetupState<Step>
  fun copyBaseState(scaleSetupState: DeviceSetupState<Step>): S

  // Convenience properties for accessing the current step, step list, navigation flow (first/last/next step),
// and setup status such as loading state — all derived from the underlying scaleSetupState.
  val isLastStep: Boolean
    get() = scaleSetupState.setupState.step == scaleSetupState.steps.last()
  val isFirstStep: Boolean
    get() = scaleSetupState.setupState.step == scaleSetupState.steps.first()
  val steps: ImmutableList<Step>
    get() = scaleSetupState.steps
  val step: Step
    get() = scaleSetupState.setupState.step
  val isLoading: Boolean
    get() = scaleSetupState.setupState.connectionState == ConnectionState.Loading

  // Navigation
  val nextStep: Step?
    get() = steps.getOrNull(steps.indexOf(step) + 1)
  val backEnabled: Boolean
    get() = scaleSetupState.backEnabled
  val nextEnabled: Boolean
    get() = scaleSetupState.nextEnabled
  val previousStep: Step?
    get() = steps.getOrNull(steps.indexOf(step) - 1)

  val permissions: GGPermissionStatusMap
    get() = scaleSetupState.permissions
}

@Stable
data class DeviceSetupState<T>(
  val setupState: SetupState<T>,
  val steps: ImmutableList<T>,
  val sku: String = "",
  val scaleInfo: DeviceModelInfo? = null,
  val permissions: GGPermissionStatusMap = mutableMapOf(),
  val backEnabled: Boolean = false,
  val nextEnabled: Boolean = true
) : IReducer.State

/**
 * Intentionally not sealed to allow cross-package extension by feature-specific intent classes.
 * Should only be extended via sealed interfaces or objects within feature-specific reducers.
 */
interface DeviceSetupIntent : IReducer.Intent {
  object Next : DeviceSetupIntent

  object Back : DeviceSetupIntent

  object Skip : DeviceSetupIntent

  object TryAgain : DeviceSetupIntent

  object OpenHelp : DeviceSetupIntent

  data class ExitSetup(
    val isSetupFinished: Boolean,
  ) : DeviceSetupIntent

  data class SetNewStep<Step : DeviceSetupStep>(val step: Step) : DeviceSetupIntent

  @Stable
  data class AlterConnectionState(
    val connectionState: ConnectionState
  ) : DeviceSetupIntent

  data class RequestPermission(
    val permission: String
  ) : DeviceSetupIntent

  data class SetPermissions(
    val permissions: GGPermissionStatusMap
  ) : DeviceSetupIntent

  data class SetSku(
    val sku: String
  ) : DeviceSetupIntent

  data class SetScaleInfo(val scaleInfo: DeviceModelInfo?): DeviceSetupIntent

  data class BackEnabled(
    val isEnabled: Boolean
  ) : DeviceSetupIntent

  data class NextEnabled(
    val isEnabled: Boolean
  ) : DeviceSetupIntent
}

open class DeviceSetupReducer<
  Step : DeviceSetupStep,
  S : BaseState<Step, S>,
  > : IReducer<S, DeviceSetupIntent> {

  override fun reduce(state: S, intent: DeviceSetupIntent): S? {
    val baseState = state.scaleSetupState

    val updatedBaseState = when (intent) {
      is DeviceSetupIntent.SetNewStep<*> -> {
        @Suppress("UNCHECKED_CAST")
        val step = intent.step as? Step ?: return state.copyBaseState(baseState)

        val updatedSteps = baseState.steps.toMutableList()
        val index = updatedSteps.indexOf(step)

        if (index >= 0) {
          updatedSteps[index] = step
        } else {
          updatedSteps.add(step)
        }

        baseState.copy(
          setupState = SetupState(step),
          steps = updatedSteps.toImmutableList(),
        )
      }

      is DeviceSetupIntent.BackEnabled -> baseState.copy(
        backEnabled = intent.isEnabled,
      )

      is DeviceSetupIntent.NextEnabled -> baseState.copy(
        nextEnabled = intent.isEnabled,
      )

      is DeviceSetupIntent.AlterConnectionState -> baseState.copy(
        setupState = baseState.setupState.copy(
          connectionState = intent.connectionState,
        ),
      )

      is DeviceSetupIntent.SetPermissions -> baseState.copy(
        permissions = intent.permissions,
      )

      is DeviceSetupIntent.SetSku -> baseState.copy(
        sku = intent.sku,
      )

      is DeviceSetupIntent.SetScaleInfo -> baseState.copy(
        scaleInfo = intent.scaleInfo,
      )

      else -> null

    }
    return if (updatedBaseState == null) state else state.copyBaseState(updatedBaseState)
  }
}



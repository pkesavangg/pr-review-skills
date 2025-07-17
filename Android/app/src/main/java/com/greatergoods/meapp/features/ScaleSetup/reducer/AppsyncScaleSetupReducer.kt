package com.greatergoods.meapp.features.ScaleSetup.reducer

import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.ScaleSetup.enums.AppsyncScaleSetupStep

/**
 * State for AppsyncScaleSetupScreen.
 */
data class AppsyncScaleSetupState(
  val currentStep: AppsyncScaleSetupStep = AppsyncScaleSetupStep.SCALE_INFO,
  val sku: String = "0341",
  val steps: List<AppsyncScaleSetupStep> = listOf(AppsyncScaleSetupStep.SCALE_INFO),
  val isLoading: Boolean = false,
  val error: String? = null,
  val isSetupFinished: Boolean = false,
  val isConnected: Boolean = false,
  val permissions: GGPermissionStatusMap = mutableMapOf(),
  ) : IReducer.State {
  val currentStepIndex: Int = steps.indexOf(currentStep)
  val isFirstStep: Boolean = currentStepIndex == 0
  val isLastStep: Boolean = currentStepIndex == steps.lastIndex
  val progress: Float = if (steps.isEmpty()) 0f else (currentStepIndex + 1).toFloat() / steps.size.toFloat()
}

/**
 * Intents for AppsyncScaleSetupScreen actions.
 */
sealed interface AppsyncScaleSetupIntent : IReducer.Intent {
  data class SetScaleSku(
    val sku: String,
  ) : AppsyncScaleSetupIntent

  data class SetCurrentStep(
    val step: AppsyncScaleSetupStep,
  ) : AppsyncScaleSetupIntent

  data class SetLoading(
    val isLoading: Boolean,
  ) : AppsyncScaleSetupIntent

  data class SetError(
    val error: String?,
  ) : AppsyncScaleSetupIntent

  object Next : AppsyncScaleSetupIntent

  object Back : AppsyncScaleSetupIntent

  object Skip : AppsyncScaleSetupIntent

  data class ExitSetup(
    val isSetupFinished: Boolean,
    val isConnected: Boolean = false,
  ) : AppsyncScaleSetupIntent

  object OpenHelp : AppsyncScaleSetupIntent

  data class SetPermissions(val permissions: GGPermissionStatusMap) : AppsyncScaleSetupIntent
  data class RequestPermission(val permissionType: String) : AppsyncScaleSetupIntent
}

/**
 * Reducer for AppsyncScaleSetupScreen.
 */
class AppsyncScaleSetupReducer : IReducer<AppsyncScaleSetupState, AppsyncScaleSetupIntent> {
  override fun reduce(
    state: AppsyncScaleSetupState,
    intent: AppsyncScaleSetupIntent,
  ): AppsyncScaleSetupState? =
    when (intent) {
      is AppsyncScaleSetupIntent.SetScaleSku -> state.copy(sku = intent.sku)
      is AppsyncScaleSetupIntent.SetCurrentStep -> state.copy(currentStep = intent.step)
      is AppsyncScaleSetupIntent.SetLoading -> state.copy(isLoading = intent.isLoading)
      is AppsyncScaleSetupIntent.SetError -> state.copy(error = intent.error)
      is AppsyncScaleSetupIntent.SetPermissions -> state.copy(permissions = intent.permissions)
      is AppsyncScaleSetupIntent.Next -> {
        val nextIndex = state.currentStepIndex + 1
        if (nextIndex < state.steps.size) {
          state.copy(currentStep = state.steps[nextIndex])
        } else {
          state.copy() // No change if at last step
        }
      }

      is AppsyncScaleSetupIntent.Back -> {
        val prevIndex = state.currentStepIndex - 1
        if (prevIndex >= 0) {
          state.copy(currentStep = state.steps[prevIndex])
        } else {
          state.copy() // No change if at first step
        }
      }

      is AppsyncScaleSetupIntent.Skip -> state.copy()
      is AppsyncScaleSetupIntent.ExitSetup ->
        state.copy(
          isSetupFinished = intent.isSetupFinished,
          isConnected = intent.isConnected,
        )

      else -> state.copy()
    }
}

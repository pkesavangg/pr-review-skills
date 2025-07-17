package com.greatergoods.meapp.features.ScaleSetup.reducer

import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.libs.appsync.model.AppSyncResult
import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.ScaleSetup.enums.AppsyncScaleSetupStep
import com.greatergoods.meapp.features.appPermissions.helper.AppPermissionsHelper

/**
 * State for AppsyncScaleSetupScreen.
 */
data class AppsyncScaleSetupState(
  val currentStep: AppsyncScaleSetupStep = AppsyncScaleSetupStep.SCALE_INFO,
  val sku: String = "0341",
  val steps: List<AppsyncScaleSetupStep> = listOf(
    AppsyncScaleSetupStep.SCALE_INFO,
    AppsyncScaleSetupStep.PERMISSIONS,
    AppsyncScaleSetupStep.ACTIVATE_SCALE,
    AppsyncScaleSetupStep.ADD_INFO,
    AppsyncScaleSetupStep.STEP_ON,
    AppsyncScaleSetupStep.OPEN_CAMERA,
    AppsyncScaleSetupStep.SETUP_FINISHED,
  ),
  val isNextEnabled: Boolean = true,
  val error: String? = null,
  val isSetupFinished: Boolean = false,
  val permissions: GGPermissionStatusMap = mutableMapOf(),
  val scanResult: AppSyncResult? = null,
) : IReducer.State {
  val currentStepIndex: Int = steps.indexOf(currentStep)
  val isFirstStep: Boolean = currentStepIndex == 0
  val isLastStep: Boolean = currentStepIndex == steps.lastIndex
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

  data class SetNextButtonState(
    val isEnabled: Boolean,
  ) : AppsyncScaleSetupIntent

  data class SetError(
    val error: String?,
  ) : AppsyncScaleSetupIntent

  object Next : AppsyncScaleSetupIntent

  object Back : AppsyncScaleSetupIntent

  data class ExitSetup(
    val isSetupFinished: Boolean,
  ) : AppsyncScaleSetupIntent

  object OpenHelp : AppsyncScaleSetupIntent

  data class SetPermissions(val permissions: GGPermissionStatusMap) : AppsyncScaleSetupIntent
  data class RequestPermission(val permissionType: String) : AppsyncScaleSetupIntent

  data class HandleAppSyncResult(
    val result: AppSyncResult
  ) : AppsyncScaleSetupIntent
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
      is AppsyncScaleSetupIntent.SetNextButtonState -> state.copy(isNextEnabled = intent.isEnabled)
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
        var backStepCount = 1
        val areRequiredPermissionsEnabled = AppPermissionsHelper
          .areRequiredPermissionsEnabled(state.permissions, sku = state.sku)
        if (state.currentStep == AppsyncScaleSetupStep.ACTIVATE_SCALE && areRequiredPermissionsEnabled ||
          state.currentStep == AppsyncScaleSetupStep.SETUP_FINISHED
        ) {
          backStepCount = 2
        }
        val prevIndex = state.currentStepIndex - backStepCount

        if (prevIndex >= 0) {
          state.copy(currentStep = state.steps[prevIndex])
        } else {
          state.copy() // No change if at first step
        }
      }

      is AppsyncScaleSetupIntent.ExitSetup ->
        state.copy(
          isSetupFinished = intent.isSetupFinished,
        )

      else -> state.copy()
    }
}

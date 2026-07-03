package com.dmdbrands.gurus.weight.features.DeviceSetup.reducer

import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.libs.appsync.model.AppSyncResult
import com.dmdbrands.gurus.weight.core.config.AppSyncConfig
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.AppsyncScaleSetupStep
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * State for AppsyncScaleSetupScreen.
 */
@Stable
data class AppsyncScaleSetupState(
  val currentStep: AppsyncScaleSetupStep = AppsyncScaleSetupStep.SCALE_INFO,
  val sku: String = "0341",
  val bodyComp: Boolean = true,
  val steps: ImmutableList<AppsyncScaleSetupStep> = persistentListOf(),
  val isNextEnabled: Boolean = true,
  val error: String? = null,
  val isSetupFinished: Boolean = false,
  val permissions: GGPermissionStatusMap = mutableMapOf(),
  val scanResult: AppSyncResult? = null,
  val appSyncZoomLevel: Int = AppSyncConfig.DEFAULT_ZOOM,
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

  data class SetBodyComp(
    val bodyComp: Boolean,
  ) : AppsyncScaleSetupIntent

  data class SetCurrentStep(
    val step: AppsyncScaleSetupStep,
  ) : AppsyncScaleSetupIntent

  @Stable
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

  data class SetAppSyncZoomLevel(val zoom: Int) : AppsyncScaleSetupIntent
}

/**
 * Helper function to generate steps based on bodyComp property.
 */
private fun generateSteps(bodyComp: Boolean): List<AppsyncScaleSetupStep> {
  return if (bodyComp) {
    // Full flow with ADD_INFO step for body composition scales
    listOf(
      AppsyncScaleSetupStep.SCALE_INFO,
      AppsyncScaleSetupStep.PERMISSIONS,
      AppsyncScaleSetupStep.ACTIVATE_SCALE,
      AppsyncScaleSetupStep.ADD_INFO,
      AppsyncScaleSetupStep.STEP_ON,
      AppsyncScaleSetupStep.OPEN_CAMERA,
      AppsyncScaleSetupStep.SETUP_FINISHED,
    )
  } else {
    // Simplified flow without ADD_INFO step for basic scales
    listOf(
      AppsyncScaleSetupStep.SCALE_INFO,
      AppsyncScaleSetupStep.PERMISSIONS,
      AppsyncScaleSetupStep.ACTIVATE_SCALE,
      AppsyncScaleSetupStep.STEP_ON,
      AppsyncScaleSetupStep.OPEN_CAMERA,
      AppsyncScaleSetupStep.SETUP_FINISHED,
    )
  }
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
      is AppsyncScaleSetupIntent.SetBodyComp -> {
        val newSteps = generateSteps(intent.bodyComp)
        state.copy(
          bodyComp = intent.bodyComp,
          steps = newSteps.toImmutableList(),
          // Reset to first step if current step is not in new steps
          currentStep = if (newSteps.contains(state.currentStep)) state.currentStep else newSteps.first()
        )
      }
      is AppsyncScaleSetupIntent.SetCurrentStep -> state.copy(currentStep = intent.step)
      is AppsyncScaleSetupIntent.SetNextButtonState -> state.copy(isNextEnabled = intent.isEnabled)
      is AppsyncScaleSetupIntent.SetError -> state.copy(error = intent.error)
      is AppsyncScaleSetupIntent.SetPermissions -> state.copy(permissions = intent.permissions)
      is AppsyncScaleSetupIntent.SetAppSyncZoomLevel -> state.copy(appSyncZoomLevel = intent.zoom)

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

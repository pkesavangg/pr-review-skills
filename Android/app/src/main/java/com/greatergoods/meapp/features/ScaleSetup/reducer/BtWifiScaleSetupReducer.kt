package com.greatergoods.meapp.features.ScaleSetup.reducer

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.ScaleSetup.enums.BtWifiSetupStep

/**
 * State for BtWifiScaleSetupScreen.
 */
data class BtWifiScaleSetupState(
  val currentStep: BtWifiSetupStep = BtWifiSetupStep.SCALE_INFO,
  val sku: String = "0412",
  val steps: List<BtWifiSetupStep> = listOf(
    BtWifiSetupStep.SCALE_INFO,
    BtWifiSetupStep.WAKEUP
  ),
  val isLoading: Boolean = false,
  val error: String? = null,
  val isSetupFinished: Boolean = false,
  val isConnected: Boolean = false,
) : IReducer.State {
  val currentStepIndex: Int = steps.indexOf(currentStep)
  val isFirstStep: Boolean = currentStepIndex == 0
  val isLastStep: Boolean = currentStepIndex == steps.lastIndex
  val progress: Float = if (steps.isEmpty()) 0f else (currentStepIndex + 1).toFloat() / steps.size.toFloat()
}

/**
 * Intents for BtWifiScaleSetupScreen actions.
 */
sealed interface BtWifiScaleSetupIntent : IReducer.Intent {
  data class SetScaleSku(
    val sku: String,
  ) : BtWifiScaleSetupIntent

  data class SetCurrentStep(
    val step: BtWifiSetupStep,
  ) : BtWifiScaleSetupIntent

  data class SetLoading(
    val isLoading: Boolean,
  ) : BtWifiScaleSetupIntent

  data class SetError(
    val error: String?,
  ) : BtWifiScaleSetupIntent

  object Next : BtWifiScaleSetupIntent

  object Back : BtWifiScaleSetupIntent

  object Skip : BtWifiScaleSetupIntent

  data class ExitSetup(
    val isSetupFinished: Boolean,
    val isConnected: Boolean = false,
  ) : BtWifiScaleSetupIntent

  object OpenHelp : BtWifiScaleSetupIntent
}

/**
 * Reducer for BtWifiScaleSetupScreen.
 */
class BtWifiScaleSetupReducer : IReducer<BtWifiScaleSetupState, BtWifiScaleSetupIntent> {
  override fun reduce(
    state: BtWifiScaleSetupState,
    intent: BtWifiScaleSetupIntent,
  ): BtWifiScaleSetupState? =
    when (intent) {
      is BtWifiScaleSetupIntent.SetScaleSku -> state.copy(sku = intent.sku)
      is BtWifiScaleSetupIntent.SetCurrentStep -> state.copy(currentStep = intent.step)
      is BtWifiScaleSetupIntent.SetLoading -> state.copy(isLoading = intent.isLoading)
      is BtWifiScaleSetupIntent.SetError -> state.copy(error = intent.error)
      is BtWifiScaleSetupIntent.Next -> {
        val nextIndex = state.currentStepIndex + 1
        if (nextIndex < state.steps.size) {
          state.copy(currentStep = state.steps[nextIndex])
        } else {
          state.copy() // No change if at last step
        }
      }

      is BtWifiScaleSetupIntent.Back -> {
        val prevIndex = state.currentStepIndex - 1
        if (prevIndex >= 0) {
          state.copy(currentStep = state.steps[prevIndex])
        } else {
          state.copy() // No change if at first step
        }
      }

      is BtWifiScaleSetupIntent.Skip -> state.copy()
      is BtWifiScaleSetupIntent.ExitSetup ->
        state.copy(
          isSetupFinished = intent.isSetupFinished,
          isConnected = intent.isConnected,
        )

      else -> state.copy()
    }
}

package com.greatergoods.meapp.features.ScaleSetup.reducer

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.ScaleSetup.enums.WifiScaleSetupStep

/**
 * State for WifiScaleSetupScreen.
 */
data class WifiScaleSetupState(
  val currentStep: WifiScaleSetupStep = WifiScaleSetupStep.SCALE_INFO,
  val sku: String = "0384",
  val steps: List<WifiScaleSetupStep> = listOf(WifiScaleSetupStep.SCALE_INFO),
  val isLoading: Boolean = false,
  val error: String? = null,
  val isSetupFinished: Boolean = false,
  val isConnected: Boolean = false,
  val shouldGetMacAddress: Boolean = false,
) : IReducer.State {
  val currentStepIndex: Int = steps.indexOf(currentStep)
  val isFirstStep: Boolean = currentStepIndex == 0
  val isLastStep: Boolean = currentStepIndex == steps.lastIndex
  val progress: Float = if (steps.isEmpty()) 0f else (currentStepIndex + 1).toFloat() / steps.size.toFloat()
}

/**
 * Intents for WifiScaleSetupScreen actions.
 */
sealed interface WifiScaleSetupIntent : IReducer.Intent {
  data class SetScaleSku(
    val sku: String,
  ) : WifiScaleSetupIntent

  data class SetCurrentStep(
    val step: WifiScaleSetupStep,
  ) : WifiScaleSetupIntent

  data class SetLoading(
    val isLoading: Boolean,
  ) : WifiScaleSetupIntent

  data class SetError(
    val error: String?,
  ) : WifiScaleSetupIntent

  data class OnGetScaleMacAddress(
    val shouldGetMacAddress: Boolean,
  ) : WifiScaleSetupIntent

  object Next : WifiScaleSetupIntent

  object Back : WifiScaleSetupIntent

  object Skip : WifiScaleSetupIntent

  data class ExitSetup(
    val isSetupFinished: Boolean,
    val isConnected: Boolean = false,
  ) : WifiScaleSetupIntent

  object OpenHelp : WifiScaleSetupIntent
}

/**
 * Reducer for WifiScaleSetupScreen.
 */
class WifiScaleSetupReducer : IReducer<WifiScaleSetupState, WifiScaleSetupIntent> {
  override fun reduce(
    state: WifiScaleSetupState,
    intent: WifiScaleSetupIntent,
  ): WifiScaleSetupState? =
    when (intent) {
      is WifiScaleSetupIntent.SetScaleSku -> state.copy(sku = intent.sku)
      is WifiScaleSetupIntent.SetCurrentStep -> state.copy(currentStep = intent.step)
      is WifiScaleSetupIntent.SetLoading -> state.copy(isLoading = intent.isLoading)
      is WifiScaleSetupIntent.SetError -> state.copy(error = intent.error)
      is WifiScaleSetupIntent.Next -> {
        val nextIndex = state.currentStepIndex + 1
        if (nextIndex < state.steps.size) {
          state.copy(currentStep = state.steps[nextIndex])
        } else {
          state.copy() // No change if at last step
        }
      }

      is WifiScaleSetupIntent.Back -> {
        val prevIndex = state.currentStepIndex - 1
        if (prevIndex >= 0) {
          state.copy(currentStep = state.steps[prevIndex])
        } else {
          state.copy() // No change if at first step
        }
      }

      is WifiScaleSetupIntent.Skip -> state.copy()
      is WifiScaleSetupIntent.ExitSetup ->
        state.copy(
          isSetupFinished = intent.isSetupFinished,
          isConnected = intent.isConnected,
        )

      is WifiScaleSetupIntent.OnGetScaleMacAddress -> state.copy(shouldGetMacAddress = intent.shouldGetMacAddress)

      else -> state.copy()
    }
}

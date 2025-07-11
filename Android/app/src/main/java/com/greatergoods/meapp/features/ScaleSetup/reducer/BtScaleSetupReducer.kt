package com.greatergoods.meapp.features.ScaleSetup.reducer

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.ScaleSetup.enums.BtScaleSetupStep

/**
 * State for BtScaleSetupScreen.
 */
data class BtScaleSetupState(
  val currentStep: BtScaleSetupStep = BtScaleSetupStep.ScaleInfo,
  val sku: String = "0375",
  val steps: List<BtScaleSetupStep> = listOf(BtScaleSetupStep.ScaleInfo),
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
 * Intents for BtScaleSetupScreen actions.
 */
sealed interface BtScaleSetupIntent : IReducer.Intent {
  data class SetScaleSku(
    val sku: String,
  ) : BtScaleSetupIntent

  data class SetCurrentStep(
    val step: BtScaleSetupStep,
  ) : BtScaleSetupIntent

  data class SetLoading(
    val isLoading: Boolean,
  ) : BtScaleSetupIntent

  data class SetError(
    val error: String?,
  ) : BtScaleSetupIntent

  object Next : BtScaleSetupIntent

  object Back : BtScaleSetupIntent

  object Skip : BtScaleSetupIntent

  data class ExitSetup(
    val isSetupFinished: Boolean,
    val isConnected: Boolean = false,
  ) : BtScaleSetupIntent

  object OpenHelp : BtScaleSetupIntent
}

/**
 * Reducer for BtScaleSetupScreen.
 */
class BtScaleSetupReducer : IReducer<BtScaleSetupState, BtScaleSetupIntent> {
  override fun reduce(
    state: BtScaleSetupState,
    intent: BtScaleSetupIntent,
  ): BtScaleSetupState? =
    when (intent) {
      is BtScaleSetupIntent.SetScaleSku -> state.copy(sku = intent.sku)
      is BtScaleSetupIntent.SetCurrentStep -> state.copy(currentStep = intent.step)
      is BtScaleSetupIntent.SetLoading -> state.copy(isLoading = intent.isLoading)
      is BtScaleSetupIntent.SetError -> state.copy(error = intent.error)
      is BtScaleSetupIntent.Next -> {
        val nextIndex = state.currentStepIndex + 1
        if (nextIndex < state.steps.size) {
          state.copy(currentStep = state.steps[nextIndex])
        } else {
          state.copy() // No change if at last step
        }
      }

      is BtScaleSetupIntent.Back -> {
        val prevIndex = state.currentStepIndex - 1
        if (prevIndex >= 0) {
          state.copy(currentStep = state.steps[prevIndex])
        } else {
          state.copy() // No change if at first step
        }
      }

      is BtScaleSetupIntent.Skip -> state.copy()
      is BtScaleSetupIntent.ExitSetup ->
        state.copy(
          isSetupFinished = intent.isSetupFinished,
          isConnected = intent.isConnected,
        )

      else -> state.copy()
    }
}

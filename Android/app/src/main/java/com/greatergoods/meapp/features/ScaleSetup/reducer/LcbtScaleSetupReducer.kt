package com.greatergoods.meapp.features.ScaleSetup.reducer

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.ScaleSetup.enums.LcbtScaleSetupStep

/**
 * State for LcbtScaleSetupScreen.
 */
data class LcbtScaleSetupState(
  val currentStep: LcbtScaleSetupStep = LcbtScaleSetupStep.SCALE_INFO,
  val sku: String = "0399",
  val steps: List<LcbtScaleSetupStep> = listOf(LcbtScaleSetupStep.SCALE_INFO),
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
 * Intents for LcbtScaleSetupScreen actions.
 */
sealed interface LcbtScaleSetupIntent : IReducer.Intent {
  data class SetScaleSku(
    val sku: String,
  ) : LcbtScaleSetupIntent

  data class SetCurrentStep(
    val step: LcbtScaleSetupStep,
  ) : LcbtScaleSetupIntent

  data class SetLoading(
    val isLoading: Boolean,
  ) : LcbtScaleSetupIntent

  data class SetError(
    val error: String?,
  ) : LcbtScaleSetupIntent

  object Next : LcbtScaleSetupIntent

  object Back : LcbtScaleSetupIntent

  object Skip : LcbtScaleSetupIntent

  data class ExitSetup(
    val isSetupFinished: Boolean,
    val isConnected: Boolean = false,
  ) : LcbtScaleSetupIntent

  object OpenHelp : LcbtScaleSetupIntent
}

/**
 * Reducer for LcbtScaleSetupScreen.
 */
class LcbtScaleSetupReducer : IReducer<LcbtScaleSetupState, LcbtScaleSetupIntent> {
  override fun reduce(
    state: LcbtScaleSetupState,
    intent: LcbtScaleSetupIntent,
  ): LcbtScaleSetupState? =
    when (intent) {
      is LcbtScaleSetupIntent.SetScaleSku -> state.copy(sku = intent.sku)
      is LcbtScaleSetupIntent.SetCurrentStep -> state.copy(currentStep = intent.step)
      is LcbtScaleSetupIntent.SetLoading -> state.copy(isLoading = intent.isLoading)
      is LcbtScaleSetupIntent.SetError -> state.copy(error = intent.error)
      is LcbtScaleSetupIntent.Next -> {
        val nextIndex = state.currentStepIndex + 1
        if (nextIndex < state.steps.size) {
          state.copy(currentStep = state.steps[nextIndex])
        } else {
          state.copy() // No change if at last step
        }
      }

      is LcbtScaleSetupIntent.Back -> {
        val prevIndex = state.currentStepIndex - 1
        if (prevIndex >= 0) {
          state.copy(currentStep = state.steps[prevIndex])
        } else {
          state.copy() // No change if at first step
        }
      }

      is LcbtScaleSetupIntent.Skip -> state.copy()
      is LcbtScaleSetupIntent.ExitSetup ->
        state.copy(
          isSetupFinished = intent.isSetupFinished,
          isConnected = intent.isConnected,
        )

      else -> state.copy()
    }
}

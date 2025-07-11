package com.greatergoods.meapp.features.ScaleSetup.reducer

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.ScaleSetup.enums.BtWifiSetupStep
import com.greatergoods.meapp.features.common.components.ConnectionState

/**
 * State for BtWifiScaleSetupScreen.
 */
data class BtWifiScaleSetupState(
  val currentStep: BtWifiSetupStep = BtWifiSetupStep.SCALE_INFO,
  val sku: String = "0412",
  val steps: List<BtWifiSetupStep> = listOf(
    BtWifiSetupStep.SCALE_INFO,
    BtWifiSetupStep.WAKEUP,
    BtWifiSetupStep.CONNECTING_BLUETOOTH,
    BtWifiSetupStep.GATHERING_NETWORK,
    BtWifiSetupStep.CONNECTING_WIFI,
    BtWifiSetupStep.MEASUREMENT,
    BtWifiSetupStep.SCALE_CONNECTED,
  ),
  val isLoading: Boolean = false,
  val errorCode: String? = null,
  val isSetupFinished: Boolean = false,
  val isConnected: Boolean = false,
  val stepConnectionStates: Map<BtWifiSetupStep, ConnectionState> = mapOf(),
  val canProceedToNext: Boolean = true,
) : IReducer.State {
  val currentStepIndex: Int = steps.indexOf(currentStep)
  val isFirstStep: Boolean = currentStepIndex == 0
  val isLastStep: Boolean = currentStepIndex == steps.lastIndex
  val progress: Float = if (steps.isEmpty()) 0f else (currentStepIndex + 1).toFloat() / steps.size.toFloat()
  val currentStepConnectionState: ConnectionState = stepConnectionStates[currentStep] ?: ConnectionState.Loading
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

  data class SetErrorCode(
    val errorCode: String?,
  ) : BtWifiScaleSetupIntent

  data class SetStepConnectionState(
    val step: BtWifiSetupStep,
    val connectionState: ConnectionState,
  ) : BtWifiScaleSetupIntent

  data class SetCanProceedToNext(
    val canProceed: Boolean,
  ) : BtWifiScaleSetupIntent

  object Next : BtWifiScaleSetupIntent

  object Back : BtWifiScaleSetupIntent

  object Skip : BtWifiScaleSetupIntent

  data class ExitSetup(
    val isSetupFinished: Boolean,
    val isConnected: Boolean = false,
  ) : BtWifiScaleSetupIntent

  object OpenHelp : BtWifiScaleSetupIntent

  object TryAgain : BtWifiScaleSetupIntent
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
      is BtWifiScaleSetupIntent.SetErrorCode -> state.copy(errorCode = intent.errorCode)
      is BtWifiScaleSetupIntent.SetStepConnectionState -> state.copy(
        stepConnectionStates = state.stepConnectionStates.toMutableMap().apply {
          put(intent.step, intent.connectionState)
        },
      )

      is BtWifiScaleSetupIntent.SetCanProceedToNext -> state.copy(canProceedToNext = intent.canProceed)
      is BtWifiScaleSetupIntent.Next -> {
        val nextIndex = state.currentStepIndex + 1
        if (nextIndex < state.steps.size && state.canProceedToNext) {
          state.copy(
            currentStep = state.steps[nextIndex],
            canProceedToNext = true, // Reset for next step
            errorCode = null,
          )
        } else {
          state.copy(errorCode = null) // No change if at last step or can't proceed
        }
      }

      is BtWifiScaleSetupIntent.Back -> {
        val prevIndex = state.currentStepIndex - 1
        if (prevIndex >= 0) {
          state.copy(
            currentStep = state.steps[prevIndex],
            canProceedToNext = true, // Reset when going back
          )
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

      is BtWifiScaleSetupIntent.TryAgain -> state.copy(
        errorCode = null,
        canProceedToNext = false, // Prevent manual progression during retry
      )

      else -> state.copy()
    }
}

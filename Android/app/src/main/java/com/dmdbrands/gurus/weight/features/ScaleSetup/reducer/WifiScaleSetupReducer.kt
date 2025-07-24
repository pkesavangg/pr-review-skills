package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.WifiScaleSetupStep
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.gurus.weight.domain.model.permission.PermissionState
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.login.strings.LoginStrings

/**
 * Controls for WiFi-Password form.
 */
data class WifiScalePasswordFormControls(
  val ssid: FormControl<String>,
  val password: FormControl<String>,
  val noPasswordNetwork: FormControl<Boolean>,
) {
  companion object {
    fun create() = WifiScalePasswordFormControls(
      ssid = FormControl.create(
        initialValue = "",
        validators = listOf(
          FormValidations.required(),
        ),
      ),
      password = FormControl.create(
        initialValue = "",
        validators = listOf(
          FormValidations.required(),
          FormValidations.minLength(6, LoginStrings.PasswordLabel),
          FormValidations.maxLength(50, LoginStrings.PasswordLabel),
        ),
      ),
      noPasswordNetwork = FormControl.create(initialValue = false),
    )
  }
}

/**
 * State for WifiScaleSetupScreen.
 */
data class WifiScaleSetupState(
  val currentStep: WifiScaleSetupStep = WifiScaleSetupStep.PERMISSIONS,
  val sku: String = "0384",
  val steps: List<WifiScaleSetupStep> = listOf(
    WifiScaleSetupStep.SCALE_INFO,
    WifiScaleSetupStep.SETUP_FINISHED),
  val isApMode: Boolean = false,
  val isLoading: Boolean = false,
  val error: String? = null,
  val isSetupFinished: Boolean = false,
  val isConnected: Boolean = false,
  val shouldGetMacAddress: Boolean = false,
  val permissions: GGPermissionStatusMap = mutableMapOf(
    "LOCATION_SWITCH" to PermissionState.ENABLED,
    "LOCATION" to PermissionState.DISABLED,
    "NETWORK" to PermissionState.ENABLED,
  ),
  val wifiPasswordForm: WifiScalePasswordFormControls = WifiScalePasswordFormControls.create(),
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
  data class OnCopyMacAddress(val isCopied: Boolean) : WifiScaleSetupIntent
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

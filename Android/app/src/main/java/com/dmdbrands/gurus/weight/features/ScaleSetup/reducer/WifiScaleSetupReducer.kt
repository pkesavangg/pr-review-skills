package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import com.dmdbrands.gurus.weight.core.service.WifiStatus
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.WifiScaleSetupStep
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap

/**
 * Setup path options for WiFi scale setup
 */
enum class SetupPath {
  COMPLETE,
  AP_MODE
}

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
        ),
      ),
      noPasswordNetwork = FormControl.create(initialValue = false),
    )
  }
}

data class ScaleNetworkForm(
  val ssid: FormControl<String>,
) {
  companion object {
    fun create() = ScaleNetworkForm(
      ssid = FormControl.create(
        initialValue = "",
        validators = listOf(
          FormValidations.required(),
        ),
      ),
    )
  }
}

/**
 * State for WifiScaleSetupScreen.
 */
data class WifiScaleSetupState(
  val currentStep: WifiScaleSetupStep = WifiScaleSetupStep.SCALE_INFO,
  val sku: String = "0384",
  val steps: List<WifiScaleSetupStep> = listOf(
    WifiScaleSetupStep.SCALE_INFO,
    WifiScaleSetupStep.PERMISSIONS,
    WifiScaleSetupStep.WIFI_PASSWORD,
    WifiScaleSetupStep.SELECT_USER,
    WifiScaleSetupStep.ACTIVATE_SCALE,
    WifiScaleSetupStep.WIFI_MODE,
    WifiScaleSetupStep.SWITCH_WIFI,
    WifiScaleSetupStep.SCALE_COUNTS,
    WifiScaleSetupStep.STEP_ON,
    WifiScaleSetupStep.SETUP_FINISHED,
    WifiScaleSetupStep.MAC_ADDRESS,
    WifiScaleSetupStep.ERROR_GUIDE,
    WifiScaleSetupStep.ERROR_CODE_SELECTED,
    WifiScaleSetupStep.TROUBLE_SHOOTING,
  ),
  val isLoading: Boolean = false,
  val isSetupFinished: Boolean = false,
  val isConnected: Boolean = false,
  val shouldGetMacAddress: Boolean = false,
  val selectedUser: Int? = null,
  val selectedWifiMode: String? = null,
  val selectedErrorCode: String? = null,
  val canProceedToNext: Boolean = false,
  val isApMode: Boolean = false,
  val permissions: GGPermissionStatusMap = mutableMapOf(),
  val wifiPasswordForm: WifiScalePasswordFormControls = WifiScalePasswordFormControls.create(),
  val scaleNetworkForm: ScaleNetworkForm = ScaleNetworkForm.create(),
  val showApMode: Boolean = false,
  val showError: Boolean = false,
  val permissionsSkipped: Boolean = false,
  val isGetMACSetup: Boolean = false,
  val saved: Boolean = false,
  val nextButtonText: String = "Next",
  val wifiStatus: WifiStatus? = null,
  val scaleToken: String? = null,
  val macAddress: String = "AA:BB:CC:DD:EE:FF",
  val isLastStep: Boolean = false,
  val scaleWifiSsid: String = "gg_SmartScale_##",
  val isNavigating: Boolean = false, // Add navigation state to prevent double-clicks
) : IReducer.State {
  val currentStepIndex: Int = steps.indexOf(currentStep)
  val isFirstStep: Boolean = currentStepIndex == 0
  val progress: Float = if (steps.isEmpty()) 0f else (currentStepIndex + 1).toFloat() / steps.size.toFloat()

  /**
   * Computed property to determine if the next button should be enabled for the current step
   */
  val isNextButtonEnabled: Boolean
    get() = when (currentStep) {
      WifiScaleSetupStep.SCALE_INFO ->
        true

      WifiScaleSetupStep.PERMISSIONS -> {
        if (isGetMACSetup) {
          AppPermissionsHelper.areRequiredPermissionsEnabled(permissions, setupType = ScaleSetupType.Wifi)
        } else {
          AppPermissionsHelper.areRequiredPermissionsEnabled(permissions, setupType = ScaleSetupType.Wifi)
        }
      }

      WifiScaleSetupStep.WIFI_PASSWORD -> {
        // Both permission skipped and normal flow need valid form
        wifiPasswordForm.ssid.isValueValid() &&
          (wifiPasswordForm.noPasswordNetwork.value || wifiPasswordForm.password.isValueValid())
      }

      WifiScaleSetupStep.SELECT_USER ->
        // Both permission skipped and normal flow need user selection
        selectedUser != null

      WifiScaleSetupStep.ACTIVATE_SCALE ->
        // All flows can proceed
        canProceedToNext

      WifiScaleSetupStep.WIFI_MODE -> {
        when {
          isGetMACSetup -> {
            // MAC setup flow - only AP mode allowed
            selectedWifiMode == "apmode"
          }

          permissionsSkipped -> {
            // Permission skipped flow - only AP mode allowed
            selectedWifiMode == "apmode"
          }

          else -> {
            // Normal flow - any mode allowed
            !selectedWifiMode.isNullOrEmpty()
          }
        }
      }

      WifiScaleSetupStep.SWITCH_WIFI -> {
        when {
          isGetMACSetup -> {
            // MAC setup flow - check if connected to scale WiFi
            scaleNetworkForm.ssid.value.isNotEmpty()
          }

          permissionsSkipped -> {
            // Permission skipped flow - can proceed
            true
          }

          else -> {
            // Normal flow - check if connected to scale WiFi
            scaleNetworkForm.ssid.value.isNotEmpty()
          }
        }
      }

      WifiScaleSetupStep.MAC_ADDRESS -> {
        if (isGetMACSetup) {
          // MAC setup flow - check if not showing error and have MAC address
          !showError && macAddress.isNotEmpty()
        } else {
          // Normal flow - check if not showing error
          !showError
        }
      }

      WifiScaleSetupStep.ERROR_GUIDE ->
        // Can only proceed if error code is selected
        !selectedErrorCode.isNullOrEmpty()

      WifiScaleSetupStep.ERROR_CODE_SELECTED,
      WifiScaleSetupStep.TROUBLE_SHOOTING,
      WifiScaleSetupStep.SCALE_COUNTS,
      WifiScaleSetupStep.STEP_ON,
      WifiScaleSetupStep.SETUP_FINISHED ->
        // These steps can always proceed
        canProceedToNext

    }
}

/**
 * Intents for WifiScaleSetupScreen actions.
 */
sealed class WifiScaleSetupIntent : IReducer.Intent {
  data class SetScaleSku(val sku: String) : WifiScaleSetupIntent()
  data class SetCurrentStep(val step: WifiScaleSetupStep) : WifiScaleSetupIntent()
  data class SelectUser(val userNumber: Int) : WifiScaleSetupIntent()
  data class SelectWifiMode(val wifiMode: String) : WifiScaleSetupIntent()
  data class SelectErrorCode(val errorCode: String) : WifiScaleSetupIntent()
  data class RequestPermission(val permissionType: String) : WifiScaleSetupIntent()
  data class SetPermissions(val permissions: GGPermissionStatusMap) : WifiScaleSetupIntent()
  data class SetCanProceedToNext(val canProceed: Boolean) : WifiScaleSetupIntent()
  data class SetWifiStatus(val wifiStatus: WifiStatus) : WifiScaleSetupIntent()
  data class SetWifiSsid(val ssid: String) : WifiScaleSetupIntent()
  data class SetUserNumber(val userNumber: Int) : WifiScaleSetupIntent()
  data class SetWifiPasswordFormSsid(val ssid: String) : WifiScaleSetupIntent()
  data class SetWifiPasswordFormPassword(val password: String) : WifiScaleSetupIntent()
  data class SetWifiPasswordFormNoPassword(val noPassword: Boolean) : WifiScaleSetupIntent()
  data class SetWifiPasswordForm(val form: WifiScalePasswordFormControls) : WifiScaleSetupIntent()
  data class SetScaleNetworkFormSsid(val ssid: String) : WifiScaleSetupIntent()
  data class SetConnectionSuccess(val isSuccess: Boolean) : WifiScaleSetupIntent()
  data class HandleUserConfirmSelected(val result: SetupPath) : WifiScaleSetupIntent()
  data class HandleErrorCodeSelected(val code: String) : WifiScaleSetupIntent()
  data class SetShowApMode(val show: Boolean) : WifiScaleSetupIntent()
  data class SetShowError(val show: Boolean) : WifiScaleSetupIntent()
  data class SetPermissionsSkipped(val skipped: Boolean) : WifiScaleSetupIntent()
  data class SetNextButtonText(val text: String) : WifiScaleSetupIntent()
  data class SetMacAddress(val macAddress: String) : WifiScaleSetupIntent()
  data class OnGetScaleMacAddress(val macAddress: String = "") : WifiScaleSetupIntent()
  data class OnCopyMacAddress(val macAddress: String) : WifiScaleSetupIntent()
  data class ExitSetup(val isSetupFinished: Boolean, val isConnected: Boolean = false) : WifiScaleSetupIntent()
  data class OpenHelp(val helpType: String = "wifi") : WifiScaleSetupIntent()
  data class SetShouldGetMacAddress(val shouldGet: Boolean) : WifiScaleSetupIntent() // Add this intent
  data class NavigateToErrorGuide(val step: WifiScaleSetupStep = WifiScaleSetupStep.ERROR_GUIDE) :
    WifiScaleSetupIntent()

  data class NavigateToTroubleShooting(val step: WifiScaleSetupStep = WifiScaleSetupStep.TROUBLE_SHOOTING) :
    WifiScaleSetupIntent()

  object Next : WifiScaleSetupIntent()
  object Back : WifiScaleSetupIntent()
  object Skip : WifiScaleSetupIntent()
  object GoToWifiSettings : WifiScaleSetupIntent()
  object ClearNavigationState : WifiScaleSetupIntent() // Add this to clear navigation state
}

/**
 * Reducer for WifiScaleSetupScreen.
 */
class WifiScaleSetupReducer : IReducer<WifiScaleSetupState, WifiScaleSetupIntent> {
  override fun reduce(
    state: WifiScaleSetupState,
    intent: WifiScaleSetupIntent,
  ): WifiScaleSetupState? {
    return when (intent) {
      is WifiScaleSetupIntent.SetScaleSku -> state.copy(sku = intent.sku)
      is WifiScaleSetupIntent.SetCurrentStep -> state.copy(
        currentStep = intent.step,
        isNavigating = false, // Clear navigation state after direct step change
      )

      is WifiScaleSetupIntent.Next -> {
        // Prevent double-clicks during navigation
        if (state.isNavigating) {
          return null
        }

        // Handle special navigation cases first
        val nextStep = when (state.currentStep) {
          WifiScaleSetupStep.WIFI_MODE -> {
            // Handle different WiFi mode selections - skip steps based on mode
            when (state.selectedWifiMode) {
              "apmode" -> {
                // Normal +1 navigation to SWITCH_WIFI
                if (state.currentStepIndex < state.steps.size - 1) {
                  state.steps[state.currentStepIndex + 1]
                } else null
              }

              else -> {
                // Skip SWITCH_WIFI step and go directly to SCALE_COUNTS
                WifiScaleSetupStep.SCALE_COUNTS
              }
            }
          }

          WifiScaleSetupStep.SWITCH_WIFI -> {
            if (state.isGetMACSetup) {
              // For MAC setup, skip to MAC_ADDRESS step
              WifiScaleSetupStep.MAC_ADDRESS
            } else {
              // For normal setup, normal +1 navigation
              if (state.currentStepIndex < state.steps.size - 1) {
                state.steps[state.currentStepIndex + 1]
              } else null
            }
          }

          WifiScaleSetupStep.ERROR_GUIDE -> {
            // Only proceed if error code is selected
            if (!state.selectedErrorCode.isNullOrEmpty()) {
              WifiScaleSetupStep.ERROR_CODE_SELECTED
            } else {
              // Can't proceed without selecting an error
              return state.copy() // No navigation
            }
          }


          WifiScaleSetupStep.MAC_ADDRESS -> {
            if (state.isGetMACSetup) {
              // End MAC setup flow - don't navigate, let ViewModel handle exit
              return state.copy(isLastStep = true)
            } else {
              // Normal flow continues
              if (state.currentStepIndex < state.steps.size - 1) {
                state.steps[state.currentStepIndex + 1]
              } else null
            }
          }

          WifiScaleSetupStep.ERROR_CODE_SELECTED -> {
            // End error flow - don't navigate, let ViewModel handle exit
            return state.copy(isLastStep = true)
          }

          WifiScaleSetupStep.TROUBLE_SHOOTING -> {
            // End troubleshooting flow - don't navigate, let ViewModel handle exit
            return state.copy(isLastStep = true)
          }

          else -> {
            // Default +1 navigation for all other steps
            if (state.currentStepIndex < state.steps.size - 1) {
              state.steps[state.currentStepIndex + 1]
            } else null
          }
        }

        if (nextStep != null) {

          // Handle MAC setup flag update for SCALE_INFO step
          val finalState =  if (state.currentStep == WifiScaleSetupStep.WIFI_MODE && state.selectedWifiMode == "apmode") {
            // Set showApMode flag when proceeding from WIFI_MODE with AP mode selected
            state.copy(showApMode = true)
          } else if (nextStep == WifiScaleSetupStep.ACTIVATE_SCALE) {
            // Clear flags when entering ACTIVATE_SCALE step
            state.copy(showApMode = false,)
          } else {
            state
          }

          finalState.copy(
            currentStep = nextStep,
            isNavigating = true, // Set navigation state during transition
            canProceedToNext = false,
            isLastStep = nextStep == WifiScaleSetupStep.SETUP_FINISHED || nextStep == WifiScaleSetupStep.TROUBLE_SHOOTING,
          )
        } else {
          // No next step available
          state.copy(isLastStep = true, isNavigating = false) // Clear navigation state when at end
        }
      }

      is WifiScaleSetupIntent.Back -> {
        // Prevent double-clicks during navigation
        if (state.isNavigating) {
          return null
        }

        // Handle special back navigation cases
        val previousStep = when (state.currentStep) {
          WifiScaleSetupStep.PERMISSIONS -> {
            if (state.isGetMACSetup) {
              // Going back from permissions in MAC setup should reset MAC setup flag
              // Return to SCALE_INFO
              WifiScaleSetupStep.SCALE_INFO
            } else {
              // Normal -1 navigation
              if (state.currentStepIndex > 0) {
                state.steps[state.currentStepIndex - 1]
              } else null
            }
          }

          WifiScaleSetupStep.SCALE_COUNTS -> {
            // Check how we got here to determine correct back step
            if (state.selectedWifiMode != "apmode") {
              // We came directly from WIFI_MODE, skip SWITCH_WIFI
              WifiScaleSetupStep.WIFI_MODE
            } else {
              // Normal -1 navigation to SWITCH_WIFI
              if (state.currentStepIndex > 0) {
                state.steps[state.currentStepIndex - 1]
              } else null
            }
          }

          WifiScaleSetupStep.MAC_ADDRESS -> {
            if (state.isGetMACSetup) {
              // For MAC setup, go back to SWITCH_WIFI
              WifiScaleSetupStep.SWITCH_WIFI
            } else {
              // Normal -1 navigation
              if (state.currentStepIndex > 0) {
                state.steps[state.currentStepIndex - 1]
              } else null
            }
          }

          WifiScaleSetupStep.ERROR_GUIDE -> {
            // Go back to WiFi mode
            WifiScaleSetupStep.WIFI_MODE
          }

          WifiScaleSetupStep.ERROR_CODE_SELECTED -> {
            // Go back to error guide
            WifiScaleSetupStep.ERROR_GUIDE
          }

          WifiScaleSetupStep.TROUBLE_SHOOTING -> {
            // Go back to error guide
            WifiScaleSetupStep.ERROR_GUIDE
          }

          WifiScaleSetupStep.ACTIVATE_SCALE -> {
            if (state.isGetMACSetup) {
              // For MAC setup, skip WIFI_PASSWORD and SELECT_USER steps
              WifiScaleSetupStep.PERMISSIONS
            } else {
              // Normal -1 navigation
              if (state.currentStepIndex > 0) {
                state.steps[state.currentStepIndex - 1]
              } else null
            }
          }

          else -> {
            // Default -1 navigation for all other steps
            if (state.currentStepIndex > 0) {
              state.steps[state.currentStepIndex - 1]
            } else null
          }
        }

        if (previousStep != null) {
          // Handle MAC setup flag reset for PERMISSIONS step
          val updatedState = if (state.currentStep == WifiScaleSetupStep.PERMISSIONS && state.isGetMACSetup) {
            state.copy(isGetMACSetup = false)
          } else {
            state
          }

          // Handle error state clearing for ACTIVATE_SCALE step
          val finalState = if (state.currentStep == WifiScaleSetupStep.ACTIVATE_SCALE && state.showError) {
            updatedState.copy(
              showError = false,
              selectedErrorCode = null,
            )
          } else {
            updatedState
          }

          finalState.copy(
            currentStep = previousStep,
            isNavigating = true, // Set navigation state during transition
            canProceedToNext = false,
            nextButtonText = "Next", // Reset button text to default when going back
            isLastStep = false,
          )
        } else {
          // No previous step available (at first step)
          state.copy(isNavigating = false) // Clear navigation state when at start
        }
      }

      is WifiScaleSetupIntent.ExitSetup ->
        state.copy(
          isSetupFinished = intent.isSetupFinished,
          isConnected = intent.isConnected,
        )

      is WifiScaleSetupIntent.OnGetScaleMacAddress -> {
        // Set MAC setup flag and navigate to permissions step
        state.copy(
          isGetMACSetup = true,
          shouldGetMacAddress = true,
          currentStep = WifiScaleSetupStep.PERMISSIONS,
        )
      }

      is WifiScaleSetupIntent.NavigateToErrorGuide -> {
        // Navigate to error guide step
        state.copy(currentStep = WifiScaleSetupStep.ERROR_GUIDE)
      }

      is WifiScaleSetupIntent.NavigateToTroubleShooting -> {
        // Navigate to trouble shooting step
        state.copy(
          currentStep = WifiScaleSetupStep.TROUBLE_SHOOTING,
          nextButtonText = "Finish",
        )
      }

      is WifiScaleSetupIntent.SelectUser -> state.copy(selectedUser = intent.userNumber)

      is WifiScaleSetupIntent.SelectWifiMode -> state.copy(selectedWifiMode = intent.wifiMode)

      is WifiScaleSetupIntent.SelectErrorCode -> state.copy(
        selectedErrorCode = intent.errorCode,
        canProceedToNext = intent.errorCode.isNotEmpty(),
      )

      is WifiScaleSetupIntent.SetPermissions -> state.copy(permissions = intent.permissions)
      is WifiScaleSetupIntent.SetCanProceedToNext -> state.copy(
        canProceedToNext = intent.canProceed,
      )

      is WifiScaleSetupIntent.SetWifiStatus -> state.copy(wifiStatus = intent.wifiStatus)
      is WifiScaleSetupIntent.SetUserNumber -> state.copy(selectedUser = intent.userNumber)
      is WifiScaleSetupIntent.SetWifiPasswordFormSsid -> {
        // Create a new form control with the updated value while preserving validators
        val updatedSsid = FormControl.create(
          initialValue = intent.ssid,
          validators = listOf(FormValidations.required()),
        )
        // Mark as touched and dirty to preserve form state
        updatedSsid.onValueChange(intent.ssid)
        updatedSsid.onBlur()

        val updatedForm = state.wifiPasswordForm.copy(ssid = updatedSsid)
        state.copy(wifiPasswordForm = updatedForm)
      }

      is WifiScaleSetupIntent.SetWifiPasswordFormPassword -> {
        // Create a new form control with the updated value while preserving validators
        val updatedPassword = FormControl.create(
          initialValue = intent.password,
          validators = listOf(
            FormValidations.required(),
          ),
        )
        // Mark as touched and dirty to preserve form state
        updatedPassword.onValueChange(intent.password)
        updatedPassword.onBlur()

        val updatedForm = state.wifiPasswordForm.copy(password = updatedPassword)
        state.copy(wifiPasswordForm = updatedForm)
      }

      is WifiScaleSetupIntent.SetWifiPasswordFormNoPassword -> {
        // Create a new form control with the updated value
        val updatedNoPassword = FormControl.create(initialValue = intent.noPassword)
        // Mark as touched and dirty to preserve form state
        updatedNoPassword.onValueChange(intent.noPassword)
        updatedNoPassword.onBlur()

        val updatedForm = state.wifiPasswordForm.copy(noPasswordNetwork = updatedNoPassword)
        state.copy(wifiPasswordForm = updatedForm)
      }

      is WifiScaleSetupIntent.SetWifiPasswordForm -> {
        // Replace the entire form with the provided form
        state.copy(wifiPasswordForm = intent.form)
      }

      is WifiScaleSetupIntent.SetScaleNetworkFormSsid -> {
        // Create a new form control with the updated value while preserving validators
        val updatedSsid = FormControl.create(
          initialValue = intent.ssid,
          validators = listOf(FormValidations.required()),
        )
        // Mark as touched and dirty to preserve form state
        updatedSsid.onValueChange(intent.ssid)
        updatedSsid.onBlur()

        val updatedForm = state.scaleNetworkForm.copy(ssid = updatedSsid)
        state.copy(scaleNetworkForm = updatedForm)
      }

      is WifiScaleSetupIntent.SetConnectionSuccess -> state.copy(isConnected = intent.isSuccess)

      // New reducer cases for user confirmation and setup flow
      is WifiScaleSetupIntent.HandleUserConfirmSelected -> state.copy(
        showApMode = intent.result == SetupPath.AP_MODE,
      )

      is WifiScaleSetupIntent.HandleErrorCodeSelected -> state.copy(
        selectedErrorCode = intent.code,
      )

      is WifiScaleSetupIntent.SetShowApMode -> state.copy(
        showApMode = intent.show,
      )

      is WifiScaleSetupIntent.SetShowError -> state.copy(
        showError = intent.show,
      )

      is WifiScaleSetupIntent.SetPermissionsSkipped -> state.copy(
        permissionsSkipped = intent.skipped,
      )

      is WifiScaleSetupIntent.SetNextButtonText -> state.copy(
        nextButtonText = intent.text,
      )

      is WifiScaleSetupIntent.SetMacAddress -> state.copy(
        macAddress = intent.macAddress,
      )

      is WifiScaleSetupIntent.ClearNavigationState -> {
        // Clear navigation state to allow button clicks again
        state.copy(isNavigating = false)
      }

      is WifiScaleSetupIntent.SetShouldGetMacAddress -> {
        state.copy(shouldGetMacAddress = intent.shouldGet)
      }

      else -> state.copy()
    }
  }
}

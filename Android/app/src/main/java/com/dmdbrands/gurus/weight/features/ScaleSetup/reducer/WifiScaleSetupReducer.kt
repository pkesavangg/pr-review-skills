package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import com.dmdbrands.gurus.weight.core.service.WifiSetupType
import com.dmdbrands.gurus.weight.core.service.WifiStatus
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.WifiScaleSetupStep
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.login.strings.LoginStrings
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
          FormValidations.minLength(6, LoginStrings.PasswordLabel),
          FormValidations.maxLength(50, LoginStrings.PasswordLabel),
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
  val error: String? = null,
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
  val setupResult: SetupPath? = null,
  val showApMode: Boolean = false,
  val showError: Boolean = false,
  val permissionsSkipped: Boolean = false,
  val isGetMACSetup: Boolean = false,
  val saved: Boolean = false,
  val nextButtonText: String = "Next",
  val wifiStatus: WifiStatus? = null,
  val scaleToken: String? = null,
  val macAddress: String = "AA:BB:CC:DD:EE:FF",
  val isConnectedToScaleWifi: Boolean = false,
  val isLastStep: Boolean = false,
  val scaleWifiSsid: String = "gg_SmartScale_33",
  val isNavigating: Boolean = false, // Add navigation state to prevent double-clicks
) : IReducer.State {
  val currentStepIndex: Int = steps.indexOf(currentStep)
  val isFirstStep: Boolean = currentStepIndex == 0
  val progress: Float = if (steps.isEmpty()) 0f else (currentStepIndex + 1).toFloat() / steps.size.toFloat()

  /**
   * Gets the next step based on the current setup flow
   */
  fun getNextStep(): WifiScaleSetupStep? {
    return when {
      // MAC Setup Flow: SCALE_INFO -> PERMISSIONS -> ACTIVATE_SCALE -> WIFI_MODE -> SWITCH_WIFI -> MAC_ADDRESS
      // (with error flows: WIFI_MODE -> ERROR_GUIDE -> ERROR_CODE_SELECTED/TROUBLE_SHOOTING)
      isGetMACSetup -> {
        when (currentStep) {
          WifiScaleSetupStep.SCALE_INFO -> WifiScaleSetupStep.PERMISSIONS
          WifiScaleSetupStep.PERMISSIONS -> WifiScaleSetupStep.ACTIVATE_SCALE
          WifiScaleSetupStep.ACTIVATE_SCALE -> WifiScaleSetupStep.WIFI_MODE
          WifiScaleSetupStep.WIFI_MODE -> WifiScaleSetupStep.SWITCH_WIFI
          WifiScaleSetupStep.SWITCH_WIFI -> WifiScaleSetupStep.MAC_ADDRESS
          WifiScaleSetupStep.MAC_ADDRESS -> null // End of MAC setup
          // Error flows
          WifiScaleSetupStep.ERROR_GUIDE -> {
            if (!selectedErrorCode.isNullOrEmpty()) {
              WifiScaleSetupStep.ERROR_CODE_SELECTED
            } else {
              null // Can't proceed without selecting an error
            }
          }

          WifiScaleSetupStep.ERROR_CODE_SELECTED -> WifiScaleSetupStep.WIFI_MODE // Return to WiFi mode
          WifiScaleSetupStep.TROUBLE_SHOOTING -> null // End of troubleshooting
          else -> null
        }
      }

      // Permission Skipped Flow: SCALE_INFO -> WIFI_PASSWORD -> SELECT_USER -> ACTIVATE_SCALE -> WIFI_MODE -> SWITCH_WIFI -> SCALE_COUNTS -> STEP_ON -> SETUP_FINISHED
      // (with error flows: WIFI_MODE -> ERROR_GUIDE -> ERROR_CODE_SELECTED/TROUBLE_SHOOTING)
      permissionsSkipped -> {
        when (currentStep) {
          WifiScaleSetupStep.SCALE_INFO -> WifiScaleSetupStep.PERMISSIONS
          WifiScaleSetupStep.PERMISSIONS -> WifiScaleSetupStep.WIFI_PASSWORD
          WifiScaleSetupStep.WIFI_PASSWORD -> WifiScaleSetupStep.SELECT_USER
          WifiScaleSetupStep.SELECT_USER -> WifiScaleSetupStep.ACTIVATE_SCALE
          WifiScaleSetupStep.ACTIVATE_SCALE -> WifiScaleSetupStep.WIFI_MODE
          WifiScaleSetupStep.WIFI_MODE -> WifiScaleSetupStep.SWITCH_WIFI
          WifiScaleSetupStep.SWITCH_WIFI -> WifiScaleSetupStep.SCALE_COUNTS
          WifiScaleSetupStep.SCALE_COUNTS -> WifiScaleSetupStep.STEP_ON
          WifiScaleSetupStep.STEP_ON -> WifiScaleSetupStep.SETUP_FINISHED
          WifiScaleSetupStep.SETUP_FINISHED -> null
          // Error flows
          WifiScaleSetupStep.ERROR_GUIDE -> {
            if (!selectedErrorCode.isNullOrEmpty()) {
              WifiScaleSetupStep.ERROR_CODE_SELECTED
            } else {
              null // Can't proceed without selecting an error
            }
          }

          WifiScaleSetupStep.ERROR_CODE_SELECTED -> WifiScaleSetupStep.WIFI_MODE // Return to WiFi mode
          WifiScaleSetupStep.TROUBLE_SHOOTING -> null // End of troubleshooting
          else -> null
        }
      }

      // Normal Flow: SCALE_INFO -> PERMISSIONS -> WIFI_PASSWORD -> SELECT_USER -> ACTIVATE_SCALE -> WIFI_MODE -> SWITCH_WIFI/SCALE_COUNTS -> STEP_ON -> SETUP_FINISHED
      // (with error flows: WIFI_MODE -> ERROR_GUIDE -> ERROR_CODE_SELECTED/TROUBLE_SHOOTING)
      else -> {
        when (currentStep) {
          WifiScaleSetupStep.SCALE_INFO -> WifiScaleSetupStep.PERMISSIONS
          WifiScaleSetupStep.PERMISSIONS -> WifiScaleSetupStep.WIFI_PASSWORD
          WifiScaleSetupStep.WIFI_PASSWORD -> WifiScaleSetupStep.SELECT_USER
          WifiScaleSetupStep.SELECT_USER -> WifiScaleSetupStep.ACTIVATE_SCALE
          WifiScaleSetupStep.ACTIVATE_SCALE -> WifiScaleSetupStep.WIFI_MODE
          WifiScaleSetupStep.WIFI_MODE -> {
            when (selectedWifiMode) {
              "apmode" -> WifiScaleSetupStep.SWITCH_WIFI
              else -> WifiScaleSetupStep.SCALE_COUNTS
            }
          }

          WifiScaleSetupStep.SWITCH_WIFI -> WifiScaleSetupStep.SCALE_COUNTS
          WifiScaleSetupStep.SCALE_COUNTS -> WifiScaleSetupStep.STEP_ON
          WifiScaleSetupStep.STEP_ON -> WifiScaleSetupStep.SETUP_FINISHED
          WifiScaleSetupStep.SETUP_FINISHED -> null
          // Error flows
          WifiScaleSetupStep.ERROR_GUIDE -> {
            if (!selectedErrorCode.isNullOrEmpty()) {
              WifiScaleSetupStep.ERROR_CODE_SELECTED
            } else {
              null // Can't proceed without selecting an error
            }
          }

          WifiScaleSetupStep.ERROR_CODE_SELECTED -> WifiScaleSetupStep.WIFI_MODE // Return to WiFi mode
          WifiScaleSetupStep.TROUBLE_SHOOTING -> null // End of troubleshooting
          else -> null
        }
      }
    }
  }

  /**
   * Gets the previous step based on the current setup flow
   */
  fun getPreviousStep(): WifiScaleSetupStep? {
    return when {
      // MAC Setup Flow
      isGetMACSetup -> {
        when (currentStep) {
          WifiScaleSetupStep.PERMISSIONS -> WifiScaleSetupStep.SCALE_INFO
          WifiScaleSetupStep.ACTIVATE_SCALE -> WifiScaleSetupStep.PERMISSIONS
          WifiScaleSetupStep.WIFI_MODE -> WifiScaleSetupStep.ACTIVATE_SCALE
          WifiScaleSetupStep.SWITCH_WIFI -> WifiScaleSetupStep.WIFI_MODE
          WifiScaleSetupStep.MAC_ADDRESS -> WifiScaleSetupStep.SWITCH_WIFI
          // Error flows
          WifiScaleSetupStep.ERROR_GUIDE -> WifiScaleSetupStep.WIFI_MODE
          WifiScaleSetupStep.ERROR_CODE_SELECTED -> WifiScaleSetupStep.ERROR_GUIDE
          WifiScaleSetupStep.TROUBLE_SHOOTING -> WifiScaleSetupStep.ERROR_GUIDE
          else -> null
        }
      }

      // Permission Skipped Flow
      permissionsSkipped -> {
        when (currentStep) {
          WifiScaleSetupStep.WIFI_PASSWORD -> WifiScaleSetupStep.SCALE_INFO
          WifiScaleSetupStep.SELECT_USER -> WifiScaleSetupStep.WIFI_PASSWORD
          WifiScaleSetupStep.ACTIVATE_SCALE -> WifiScaleSetupStep.SELECT_USER
          WifiScaleSetupStep.WIFI_MODE -> WifiScaleSetupStep.ACTIVATE_SCALE
          WifiScaleSetupStep.SWITCH_WIFI -> WifiScaleSetupStep.WIFI_MODE
          WifiScaleSetupStep.SCALE_COUNTS -> WifiScaleSetupStep.SWITCH_WIFI
          WifiScaleSetupStep.STEP_ON -> WifiScaleSetupStep.SCALE_COUNTS
          WifiScaleSetupStep.SETUP_FINISHED -> WifiScaleSetupStep.STEP_ON
          // Error flows
          WifiScaleSetupStep.ERROR_GUIDE -> WifiScaleSetupStep.WIFI_MODE
          WifiScaleSetupStep.ERROR_CODE_SELECTED -> WifiScaleSetupStep.ERROR_GUIDE
          WifiScaleSetupStep.TROUBLE_SHOOTING -> WifiScaleSetupStep.ERROR_GUIDE
          else -> null
        }
      }

      // Normal Flow
      else -> {
        when (currentStep) {
          WifiScaleSetupStep.PERMISSIONS -> WifiScaleSetupStep.SCALE_INFO
          WifiScaleSetupStep.WIFI_PASSWORD -> WifiScaleSetupStep.PERMISSIONS
          WifiScaleSetupStep.SELECT_USER -> WifiScaleSetupStep.WIFI_PASSWORD
          WifiScaleSetupStep.ACTIVATE_SCALE -> WifiScaleSetupStep.SELECT_USER
          WifiScaleSetupStep.WIFI_MODE -> WifiScaleSetupStep.ACTIVATE_SCALE
          WifiScaleSetupStep.SWITCH_WIFI -> WifiScaleSetupStep.WIFI_MODE
          WifiScaleSetupStep.SCALE_COUNTS -> {
            // Check how we got to SCALE_COUNTS to determine correct back step
            if (selectedWifiMode == "apmode") {
              WifiScaleSetupStep.SWITCH_WIFI
            } else {
              WifiScaleSetupStep.WIFI_MODE
            }
          }

          WifiScaleSetupStep.STEP_ON -> WifiScaleSetupStep.SCALE_COUNTS
          WifiScaleSetupStep.SETUP_FINISHED -> WifiScaleSetupStep.STEP_ON
          // Error flows
          WifiScaleSetupStep.ERROR_GUIDE -> WifiScaleSetupStep.WIFI_MODE
          WifiScaleSetupStep.ERROR_CODE_SELECTED -> WifiScaleSetupStep.ERROR_GUIDE
          WifiScaleSetupStep.TROUBLE_SHOOTING -> WifiScaleSetupStep.ERROR_GUIDE
          else -> null
        }
      }
    }
  }
}

/**
 * Intents for WifiScaleSetupScreen actions.
 */
sealed class WifiScaleSetupIntent : IReducer.Intent {
  data class SetScaleSku(val sku: String) : WifiScaleSetupIntent()
  data class SetCurrentStep(val step: WifiScaleSetupStep) : WifiScaleSetupIntent()
  data class SetLoading(val isLoading: Boolean) : WifiScaleSetupIntent()
  data class SetError(val error: String?) : WifiScaleSetupIntent()
  data class SelectUser(val userNumber: Int) : WifiScaleSetupIntent()
  data class SelectWifiMode(val wifiMode: String) : WifiScaleSetupIntent()
  data class SelectErrorCode(val errorCode: String) : WifiScaleSetupIntent()
  data class RequestPermission(val permissionType: String) : WifiScaleSetupIntent()
  data class SetPermissions(val permissions: GGPermissionStatusMap) : WifiScaleSetupIntent()
  data class SetNewStep(val step: WifiScaleSetupStep) : WifiScaleSetupIntent()
  data class SetCanProceedToNext(val canProceed: Boolean) : WifiScaleSetupIntent()
  data class SetScaleToken(val token: String) : WifiScaleSetupIntent()
  data class SetWifiStatus(val wifiStatus: WifiStatus) : WifiScaleSetupIntent()
  data class SetWifiSsid(val ssid: String) : WifiScaleSetupIntent()
  data class SetWifiSetupType(val setupType: WifiSetupType) : WifiScaleSetupIntent()
  data class SetUserNumber(val userNumber: Int) : WifiScaleSetupIntent()
  data class SetWifiPassword(val password: String) : WifiScaleSetupIntent()
  data class SetWifiPasswordFormSsid(val ssid: String) : WifiScaleSetupIntent()
  data class SetWifiPasswordFormPassword(val password: String) : WifiScaleSetupIntent()
  data class SetWifiPasswordFormNoPassword(val noPassword: Boolean) : WifiScaleSetupIntent()
  data class SetWifiPasswordForm(val form: WifiScalePasswordFormControls) : WifiScaleSetupIntent()
  data class SetScaleNetworkFormSsid(val ssid: String) : WifiScaleSetupIntent()
  data class SetConnectionSuccess(val isSuccess: Boolean) : WifiScaleSetupIntent()
  data class SetConnectionError(val error: String) : WifiScaleSetupIntent()
  data class HandleUserConfirmSelected(val result: SetupPath) : WifiScaleSetupIntent()
  data class HandleErrorCodeSelected(val code: String) : WifiScaleSetupIntent()
  data class SetSetupResult(val result: SetupPath?) : WifiScaleSetupIntent()
  data class SetShowApMode(val show: Boolean) : WifiScaleSetupIntent()
  data class SetShowError(val show: Boolean) : WifiScaleSetupIntent()
  data class SetPermissionsSkipped(val skipped: Boolean) : WifiScaleSetupIntent()
  data class SetIsGetMACSetup(val isGetMACSetup: Boolean) : WifiScaleSetupIntent()
  data class SetSaved(val saved: Boolean) : WifiScaleSetupIntent()
  data class SetNextButtonText(val text: String) : WifiScaleSetupIntent()
  data class SetConnectedToScaleWifi(val isConnected: Boolean) : WifiScaleSetupIntent()
  data class SetScaleWifiSsid(val ssid: String) : WifiScaleSetupIntent()
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
  object CheckScaleWifiConnection : WifiScaleSetupIntent()
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
      is WifiScaleSetupIntent.SetCurrentStep -> state.copy(currentStep = intent.step)
      is WifiScaleSetupIntent.SetLoading -> state.copy(
        isLoading = intent.isLoading,
        isNavigating = if (!intent.isLoading) false else state.isNavigating, // Clear navigation state when loading finishes
      )

      is WifiScaleSetupIntent.SetError -> state.copy(error = intent.error)
      is WifiScaleSetupIntent.Next -> {
        // Prevent double-clicks during navigation
        if (state.isNavigating) {
          return null
        }

        val nextStep = state.getNextStep()
        if (nextStep != null) {
          // Clear error state when returning to normal flow from error flows
          val updatedState = if (state.currentStep == WifiScaleSetupStep.ERROR_CODE_SELECTED &&
            nextStep == WifiScaleSetupStep.WIFI_MODE
          ) {
            state.copy(
              selectedErrorCode = null,
              showError = false,
            )
          } else {
            state
          }

          updatedState.copy(
            currentStep = nextStep,
            isNavigating = true,
            canProceedToNext = false,
            error = null,
            isLastStep = when {
              state.isGetMACSetup -> nextStep == WifiScaleSetupStep.MAC_ADDRESS
              else -> nextStep == WifiScaleSetupStep.SETUP_FINISHED || nextStep == WifiScaleSetupStep.TROUBLE_SHOOTING
            },
          )
        } else {
          // No next step available
          state.copy(isLastStep = true)
        }
      }

      is WifiScaleSetupIntent.Back -> {
        // Prevent double-clicks during navigation
        if (state.isNavigating) {
          return null
        }

        val previousStep = state.getPreviousStep()
        if (previousStep != null) {
          state.copy(
            currentStep = previousStep,
            isNavigating = true,
            canProceedToNext = false,
            error = null,
            isLastStep = false,
          )
        } else {
          // No previous step available (at first step)
          state.copy()
        }
      }

      is WifiScaleSetupIntent.Skip -> state.copy()
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

      is WifiScaleSetupIntent.RequestPermission -> state.copy() // No change for now, permission handling is separate
      is WifiScaleSetupIntent.SetPermissions -> state.copy(permissions = intent.permissions)
      is WifiScaleSetupIntent.SetNewStep -> state.copy(currentStep = intent.step)
      is WifiScaleSetupIntent.SetCanProceedToNext -> state.copy(
        canProceedToNext = intent.canProceed,
      )

      is WifiScaleSetupIntent.SetScaleToken -> state.copy()
      is WifiScaleSetupIntent.SetWifiStatus -> state.copy(wifiStatus = intent.wifiStatus)
      is WifiScaleSetupIntent.SetWifiSsid -> state.copy()
      is WifiScaleSetupIntent.SetWifiSetupType -> state.copy()
      is WifiScaleSetupIntent.SetUserNumber -> state.copy(selectedUser = intent.userNumber)
      is WifiScaleSetupIntent.SetWifiPassword -> state.copy()
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
            FormValidations.minLength(6, LoginStrings.PasswordLabel),
            FormValidations.maxLength(50, LoginStrings.PasswordLabel),
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
      is WifiScaleSetupIntent.SetConnectionError -> state.copy(error = intent.error)

      // New reducer cases for user confirmation and setup flow
      is WifiScaleSetupIntent.HandleUserConfirmSelected -> state.copy(
        setupResult = intent.result,
        showApMode = intent.result == SetupPath.AP_MODE,
      )

      is WifiScaleSetupIntent.HandleErrorCodeSelected -> state.copy(
        selectedErrorCode = intent.code,
      )

      is WifiScaleSetupIntent.SetSetupResult -> state.copy(
        setupResult = intent.result,
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

      is WifiScaleSetupIntent.SetIsGetMACSetup -> state.copy(
        isGetMACSetup = intent.isGetMACSetup,
      )

      is WifiScaleSetupIntent.SetSaved -> state.copy(
        saved = intent.saved,
      )

      is WifiScaleSetupIntent.SetNextButtonText -> state.copy(
        nextButtonText = intent.text,
      )

      is WifiScaleSetupIntent.SetConnectedToScaleWifi -> state.copy(
        isConnectedToScaleWifi = intent.isConnected,
      )

      is WifiScaleSetupIntent.SetScaleWifiSsid -> state.copy(
        scaleWifiSsid = intent.ssid,
      )

      is WifiScaleSetupIntent.SetMacAddress -> state.copy(
        macAddress = intent.macAddress,
      )

      is WifiScaleSetupIntent.GoToWifiSettings -> {
        // This intent will be handled by the ViewModel to open WiFi settings
        state.copy()
      }

      is WifiScaleSetupIntent.CheckScaleWifiConnection -> {
        // This intent will be handled by the ViewModel to check WiFi connection
        state.copy()
      }

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

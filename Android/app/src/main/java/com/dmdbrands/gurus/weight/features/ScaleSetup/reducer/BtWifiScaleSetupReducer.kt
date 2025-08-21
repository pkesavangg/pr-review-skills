package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.ggbluetoothsdk.external.models.GGWifiInfo
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.login.strings.LoginStrings

/**
 * Controls for WiFi-Password form.
 */
data class WifiPasswordFormControls(
  val ssid: FormControl<String>,
  val password: FormControl<String>,
  val noPasswordNetwork: FormControl<Boolean>,
) {
  companion object {
    fun create() = WifiPasswordFormControls(
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
 * Controls for Scale Username form.
 */
data class ScaleUsernameFormControls(
  val username: FormControl<String>,
) {
  companion object {
    fun create() = ScaleUsernameFormControls(
      username = FormControl.create(
        initialValue = "",
        validators = listOf(
          FormValidations.required(),
        ),
      ),
    )
  }
}

/**
 * State for BtWifiScaleSetupScreen.
 */
data class BtWifiScaleSetupState(
  val currentStep: BtWifiSetupStep = BtWifiSetupStep.SCALE_INFO,
  val sku: String = "0412",
  val steps: List<BtWifiSetupStep> = listOf(
    BtWifiSetupStep.SCALE_INFO,
    BtWifiSetupStep.PERMISSIONS,
    BtWifiSetupStep.WAKEUP,
    BtWifiSetupStep.CONNECTING_BLUETOOTH,
    BtWifiSetupStep.USER_LIMIT_REACHED,
    BtWifiSetupStep.GATHERING_NETWORK,
    BtWifiSetupStep.AVAILABLE_WIFI_LIST,
    BtWifiSetupStep.WIFI_PASSWORD,
    BtWifiSetupStep.CONNECTING_WIFI,
    BtWifiSetupStep.CUSTOMIZE_SETTINGS,
    BtWifiSetupStep.UPDATE_SETTINGS,
    BtWifiSetupStep.STEP_ON,
    BtWifiSetupStep.MEASUREMENT,
    BtWifiSetupStep.SETUP_FINISHED,
  ),
  val nextButtonText: String = ScaleSetupStrings.SetupButtons.Next,
  val wifiList: List<GGWifiInfo> = emptyList(),
  val connectedSSID: String? = "",
  val isLoading: Boolean = false,
  val errorCode: String? = null,
  val isSetupFinished: Boolean = false,
  val stepConnectionStates: Map<BtWifiSetupStep, ConnectionState> = mapOf(),
  val canProceedToNext: Boolean = true,
  val wifiPasswordForm: WifiPasswordFormControls = WifiPasswordFormControls.create(),
  val usernameForm: ScaleUsernameFormControls = ScaleUsernameFormControls.create(),
  val dashboardKeys: List<DashboardKey> = listOf(),
  val duplicateUser: GGBTUser? = null,
  val userList: List<GGBTUser> = listOf(),
  val permissions: GGPermissionStatusMap = mutableMapOf(),
  // Scale mode preferences - similar to Angular component's setModePreference logic
  val isAllBodyMetrics: Boolean = true, // Default to metrics mode (ScaleModeEnum.metrics)
  val isHeartRateOn: Boolean = false, // Default heart rate off
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
  data class SetConnectedSSID(val ssid: String) : BtWifiScaleSetupIntent
  data class SetUserList(val userList: List<GGBTUser>) : BtWifiScaleSetupIntent
  data class SetDuplicateUser(val duplicateUser: GGBTUser?) : BtWifiScaleSetupIntent
  data class UpdateSettings(
    val dashboardKeys: List<DashboardKey>? = null,
    val preferences: Preferences? = null
  ) : BtWifiScaleSetupIntent

  data class SetDashboardKeys(val dashboardKeys: List<DashboardKey>) : BtWifiScaleSetupIntent
  data class SetWifiList(val wifiList: List<GGWifiInfo>) : BtWifiScaleSetupIntent
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

  data class UpdateNextButtonText(
    val text: String,
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
  ) : BtWifiScaleSetupIntent

  data class ReplaceAccount(
    val userName: String? = null
  ) : BtWifiScaleSetupIntent

  object ShowRestoreAccountAlert : BtWifiScaleSetupIntent

  data class SetPermissions(val permissions: GGPermissionStatusMap) : BtWifiScaleSetupIntent
  data class RequestPermission(val permissionType: String) : BtWifiScaleSetupIntent

  object OpenHelp : BtWifiScaleSetupIntent

  object TryAgain : BtWifiScaleSetupIntent
  object OpenAccucheckModal : BtWifiScaleSetupIntent
  object RefreshNetworks : BtWifiScaleSetupIntent
  object HandlePasswordNetworkStatus : BtWifiScaleSetupIntent

  data class DeleteUser(
    val user: GGBTUser,
  ) : BtWifiScaleSetupIntent

  // Scale mode preference intents - similar to Angular component's setModePreference
  data class SetScaleModePreference(
    val isAllBodyMetrics: Boolean,
    val isHeartRateOn: Boolean,
  ) : BtWifiScaleSetupIntent

  data class SetAllBodyMetrics(
    val isAllBodyMetrics: Boolean,
  ) : BtWifiScaleSetupIntent

  data class SetHeartRateMode(
    val isHeartRateOn: Boolean,
  ) : BtWifiScaleSetupIntent
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
      is BtWifiScaleSetupIntent.SetConnectedSSID -> state.copy(connectedSSID = intent.ssid)
      is BtWifiScaleSetupIntent.SetUserList -> state.copy(userList = intent.userList)
      is BtWifiScaleSetupIntent.SetDuplicateUser -> state.copy(duplicateUser = intent.duplicateUser)
      is BtWifiScaleSetupIntent.SetDashboardKeys -> state.copy(dashboardKeys = intent.dashboardKeys)
      is BtWifiScaleSetupIntent.SetWifiList -> state.copy(wifiList = intent.wifiList)
      is BtWifiScaleSetupIntent.SetScaleSku -> state.copy(sku = intent.sku)
      is BtWifiScaleSetupIntent.SetCurrentStep -> state.copy(currentStep = intent.step)
      is BtWifiScaleSetupIntent.SetLoading -> state.copy(isLoading = intent.isLoading)
      is BtWifiScaleSetupIntent.SetErrorCode -> state.copy(errorCode = intent.errorCode)
      is BtWifiScaleSetupIntent.SetStepConnectionState -> state.copy(
        stepConnectionStates = state.stepConnectionStates.toMutableMap().apply {
          put(intent.step, intent.connectionState)
        },
      )

      is BtWifiScaleSetupIntent.SetPermissions -> state.copy(permissions = intent.permissions)
      is BtWifiScaleSetupIntent.SetCanProceedToNext -> state.copy(canProceedToNext = intent.canProceed)
      is BtWifiScaleSetupIntent.Next -> {
        val nextIndex = state.currentStepIndex + 1
        if (nextIndex < state.steps.size && state.canProceedToNext) {
          state.copy(
            currentStep = state.steps[nextIndex],
            canProceedToNext = true, // Reset for next step
            errorCode = null,
            nextButtonText = ScaleSetupStrings.SetupButtons.Next,
          )
        } else {
          state.copy(errorCode = null, isSetupFinished = state.isLastStep) // No change if at last step or can't proceed
        }
      }

      is BtWifiScaleSetupIntent.Skip -> state.copy()
      is BtWifiScaleSetupIntent.ExitSetup ->
        state.copy(
          isSetupFinished = intent.isSetupFinished,
        )

      is BtWifiScaleSetupIntent.TryAgain -> state.copy(
        errorCode = null,
        canProceedToNext = false, // Prevent manual progression during retry
      )

      is BtWifiScaleSetupIntent.UpdateNextButtonText -> state.copy(nextButtonText = intent.text)
      is BtWifiScaleSetupIntent.RefreshNetworks -> state.copy(currentStep = BtWifiSetupStep.GATHERING_NETWORK)
      BtWifiScaleSetupIntent.HandlePasswordNetworkStatus -> state.copy() // Logic handled in ViewModel

      // Scale mode preference reducers
      is BtWifiScaleSetupIntent.SetScaleModePreference -> state.copy(
        isAllBodyMetrics = intent.isAllBodyMetrics,
        isHeartRateOn = intent.isHeartRateOn,
      )
      is BtWifiScaleSetupIntent.SetAllBodyMetrics -> state.copy(isAllBodyMetrics = intent.isAllBodyMetrics)
      is BtWifiScaleSetupIntent.SetHeartRateMode -> state.copy(isHeartRateOn = intent.isHeartRateOn)

      else -> state
    }
}

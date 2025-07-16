package com.greatergoods.meapp.features.ScaleSetup.reducer

import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.ggbluetoothsdk.external.models.GGWifiInfo
import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.Preferences
import com.greatergoods.meapp.features.ScaleSetup.enums.BtWifiSetupStep
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.common.components.ConnectionState
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.common.model.DashboardKey
import com.greatergoods.meapp.features.login.strings.LoginStrings

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
    BtWifiSetupStep.SCALE_CONNECTED,
  ),
  val nextButtonText: String = ScaleSetupStrings.SetupButtons.Next,
  val wifiList: List<GGWifiInfo> = emptyList(),
  val isLoading: Boolean = false,
  val errorCode: String? = null,
  val isSetupFinished: Boolean = false,
  val stepConnectionStates: Map<BtWifiSetupStep, ConnectionState> = mapOf(),
  val canProceedToNext: Boolean = true,
  val wifiPasswordForm: WifiPasswordFormControls = WifiPasswordFormControls.create(),
  val usernameForm: ScaleUsernameFormControls = ScaleUsernameFormControls.create(),
  val dashboardKeys: List<DashboardKey> = listOf(),
  val duplicateUser: GGBTUser? = null,
  val permissions: GGPermissionStatusMap = mutableMapOf(),
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

  data class SetPermissions(val permissions: GGPermissionStatusMap) : BtWifiScaleSetupIntent
  data class RequestPermission(val permissionType: String) : BtWifiScaleSetupIntent

  object OpenHelp : BtWifiScaleSetupIntent

  object TryAgain : BtWifiScaleSetupIntent
  object OpenAccucheckModal : BtWifiScaleSetupIntent
  object RefreshNetworks : BtWifiScaleSetupIntent
  object HandlePasswordNetworkStatus : BtWifiScaleSetupIntent
  object ShowSetupWifiLaterAlert : BtWifiScaleSetupIntent
  object RestoreAccount : BtWifiScaleSetupIntent
  data class DeleteUser(
    val user: GGBTUser,
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

      else -> state
    }
}

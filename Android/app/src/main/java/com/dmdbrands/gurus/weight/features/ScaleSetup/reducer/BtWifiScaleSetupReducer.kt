package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.WeightProgress
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.CustomizeSettings
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.ggbluetoothsdk.external.models.GGWifiInfo
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Stable composite key for a Wi-Fi network entry.
 *
 * SSID alone is not unique (same SSID can be broadcast on 2.4 GHz + 5 GHz or by
 * multiple APs). MAC is the real identifier; SSID is appended so entries remain
 * disambiguated if the SDK returns a blank MAC.
 */
internal fun GGWifiInfo.listKey(): String = "${macAddress.orEmpty()}|${ssid.orEmpty()}"

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
          FormValidations.noWhiteSpace(),
          FormValidations.maxLength(20,),
          FormValidations.scaleDisplayNameValidator(BtWifiScaleSetupStrings.DuplicateUser.UserErrorMessage),
        ),
      ),
    )
  }
}

/**
 * State for BtWifiScaleSetupScreen.
 */
@Stable
data class BtWifiScaleSetupState(
  val currentStep: BtWifiSetupStep = BtWifiSetupStep.SCALE_INFO,
  val sku: String = "0412",
  val steps: ImmutableList<BtWifiSetupStep> = persistentListOf(
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
  val wifiList: ImmutableList<GGWifiInfo> = persistentListOf(),
  val connectedSSID: String? = "",
  val isLoading: Boolean = false,
  val errorCode: String? = null,
  val isSetupFinished: Boolean = false,
  // Scale ID for preference management
  val scaleId: String = "",
  val stepConnectionStates: Map<BtWifiSetupStep, ConnectionState> = mapOf(),
  val canProceedToNext: Boolean = true,
  val wifiPasswordForm: WifiPasswordFormControls = WifiPasswordFormControls.create(),
  val usernameForm: ScaleUsernameFormControls = ScaleUsernameFormControls.create(),
  val dashboardKeys: ImmutableList<DashboardKey> = persistentListOf(),
  val goalProgress: WeightProgress = WeightProgress(),
  val duplicateUser: GGBTUser? = null,
  val duplicateUserList: ImmutableList<GGBTUser> = persistentListOf(),
  val userList: ImmutableList<GGBTUser> = persistentListOf(),
  val permissions: GGPermissionStatusMap = mutableMapOf(),
  // Scale mode preferences - similar to Angular component's setModePreference logic
  val isAllBodyMetrics: Boolean = true, // Default to metrics mode (ScaleModeEnum.metrics)
  val isHeartRateOn: Boolean = false, // Default heart rate off
  val hasSavedSettings: Boolean = false, // Track if any customization settings have been saved
  val scrollToRootPage: Boolean = false,
  val isSaving: Boolean = false, // Gates SAVE button during the cosmetic saving-loader window (MA-2501)
  val visitedCustomizeSteps: Set<CustomizeSettings> = emptySet(), // Preserve visited steps when returning from UPDATE_SETTINGS Try again
  val scaleMetrics: ImmutableList<String> = ScaleMetricsHelper.getAllMetrics().toImmutableList(),
  val initialStep: BtWifiSetupStep = BtWifiSetupStep.SCALE_INFO, // Track the initial step for button visibility logic
  val latestWeight: Double? = null, // Latest weight from entry service
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
  data class SetDuplicateUserList(val duplicateUserList: List<GGBTUser>) : BtWifiScaleSetupIntent
  data class UpdateSettings(
    val dashboardKeys: List<DashboardKey>? = null,
    val preferences: Preferences? = null
  ) : BtWifiScaleSetupIntent

  data class SetDashboardKeys(val dashboardKeys: List<DashboardKey>) : BtWifiScaleSetupIntent

  /**
   * Triggers the ViewModel's [showSavingLoader] side-effect. Pure side-effect intent — the
   * reducer maps it to a no-op `state.copy()` so every UI action still flows through the same
   * `handleIntent` pipeline, keeping the intent contract single-streamed (MA-2501).
   */
  data object ShowSavingLoader : BtWifiScaleSetupIntent
  data object ScrollToRootPage : BtWifiScaleSetupIntent
  data object ClearScrollToRootPage : BtWifiScaleSetupIntent
  data class SetIsSaving(val value: Boolean) : BtWifiScaleSetupIntent
  data class SetGoalProgress(val progress: WeightProgress) : BtWifiScaleSetupIntent
  data class SetWifiList(val wifiList: List<GGWifiInfo>) : BtWifiScaleSetupIntent
  data class SetScaleSku(
    val sku: String,
  ) : BtWifiScaleSetupIntent

  data class SetScaleId(
    val scaleId: String,
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

  @Stable
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
  object OpenBiaModal : BtWifiScaleSetupIntent
  object RefreshNetworks : BtWifiScaleSetupIntent
  object HandlePasswordNetworkStatus : BtWifiScaleSetupIntent
  object ClearWifiPasswordForm : BtWifiScaleSetupIntent

  data class DeleteUser(
    val user: GGBTUser,
  ) : BtWifiScaleSetupIntent

  // Scale mode preference intents - similar to Angular component's setModePreference
  data class SetScaleModePreference(
    val isAllBodyMetrics: Boolean,
    val isHeartRateOn: Boolean,
  ) : BtWifiScaleSetupIntent

  data class SetHasSavedSettings(
    val hasSavedSettings: Boolean,
  ) : BtWifiScaleSetupIntent

  data class SetVisitedCustomizeSteps(
    val steps: Set<CustomizeSettings>,
  ) : BtWifiScaleSetupIntent

  data class SetScaleMetrics(
    val scaleMetrics: List<String>
  ) : BtWifiScaleSetupIntent
  data class SetInitialStep(
    val initialStep: BtWifiSetupStep,
  ) : BtWifiScaleSetupIntent

  data class UpdateUsernameForm(
    val username: String,
  ) : BtWifiScaleSetupIntent

  data class SetLatestWeight(
    val latestWeight: Double?,
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
      is BtWifiScaleSetupIntent.SetUserList -> state.copy(userList = intent.userList.toImmutableList())
      is BtWifiScaleSetupIntent.SetDuplicateUser -> state.copy(duplicateUser = intent.duplicateUser)
      is BtWifiScaleSetupIntent.SetDuplicateUserList -> state.copy(duplicateUserList = intent.duplicateUserList.toImmutableList())
      is BtWifiScaleSetupIntent.SetDashboardKeys -> state.copy(dashboardKeys = intent.dashboardKeys.toImmutableList())
      is BtWifiScaleSetupIntent.SetGoalProgress -> state.copy(goalProgress = intent.progress)
      is BtWifiScaleSetupIntent.SetWifiList -> state.copy(
        wifiList = intent.wifiList.distinctBy { it.listKey() }.toImmutableList(),
      )
      is BtWifiScaleSetupIntent.SetScaleSku -> state.copy(sku = intent.sku)
      is BtWifiScaleSetupIntent.SetCurrentStep -> state.copy(
        currentStep = intent.step,
        nextButtonText = ScaleSetupStrings.SetupButtons.Next // Reset to default "Next" for all step changes
      )
      is BtWifiScaleSetupIntent.SetLoading -> state.copy(isLoading = intent.isLoading)
      is BtWifiScaleSetupIntent.SetErrorCode -> state.copy(errorCode = intent.errorCode)
      is BtWifiScaleSetupIntent.SetScaleId -> state.copy(
        scaleId = intent.scaleId,
      )
      is BtWifiScaleSetupIntent.SetStepConnectionState -> state.copy(
        stepConnectionStates = state.stepConnectionStates.toMutableMap().apply {
          put(intent.step, intent.connectionState)
        },
      )

      is BtWifiScaleSetupIntent.SetPermissions -> state.copy(permissions = intent.permissions)
      is BtWifiScaleSetupIntent.SetCanProceedToNext -> state.copy(canProceedToNext = intent.canProceed)
      is BtWifiScaleSetupIntent.Next -> {
        state.copy(errorCode = null, isSetupFinished = state.isLastStep)
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

      is BtWifiScaleSetupIntent.Back -> {
        // Reset button text to "Next" by default when going back
        // This ensures that when users navigate back, they see "Next" by default
        // Specific steps will override this in their step change logic if needed
        state.copy(nextButtonText = ScaleSetupStrings.SetupButtons.Next)
      }

      // Scale mode preference reducers
      is BtWifiScaleSetupIntent.SetScaleModePreference -> state.copy(
        isAllBodyMetrics = intent.isAllBodyMetrics,
        isHeartRateOn = intent.isHeartRateOn,
      )
      is BtWifiScaleSetupIntent.SetHasSavedSettings -> state.copy(hasSavedSettings = intent.hasSavedSettings)
      // Pure side-effect intent — see ShowSavingLoader KDoc on the intent class.
      BtWifiScaleSetupIntent.ShowSavingLoader -> state.copy()
      BtWifiScaleSetupIntent.ScrollToRootPage -> state.copy(scrollToRootPage = true)
      BtWifiScaleSetupIntent.ClearScrollToRootPage -> state.copy(scrollToRootPage = false)
      is BtWifiScaleSetupIntent.SetIsSaving -> state.copy(isSaving = intent.value)
      is BtWifiScaleSetupIntent.SetVisitedCustomizeSteps -> state.copy(visitedCustomizeSteps = intent.steps)
      is BtWifiScaleSetupIntent.SetScaleMetrics -> state.copy(scaleMetrics = intent.scaleMetrics.toImmutableList())
      is BtWifiScaleSetupIntent.SetInitialStep -> state.copy(initialStep = intent.initialStep)
      is BtWifiScaleSetupIntent.UpdateUsernameForm -> state.copy(
        usernameForm = ScaleUsernameFormControls.create().copy(
          username = FormControl.create(
            initialValue = intent.username,
            validators = listOf(
              FormValidations.required(),
              FormValidations.noWhiteSpace(),
              FormValidations.maxLength(20,),
              FormValidations.scaleDisplayNameValidator(BtWifiScaleSetupStrings.DuplicateUser.UserErrorMessage),
            ),
          )
        )
      )
      is BtWifiScaleSetupIntent.SetLatestWeight -> state.copy(latestWeight = intent.latestWeight)

      else -> state
    }
}

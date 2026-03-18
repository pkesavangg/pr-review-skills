package com.dmdbrands.gurus.weight.features.scaleDetails.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.scaleDetails.Enums.ScaleSettingSteps
import com.dmdbrands.gurus.weight.features.scaleDetails.strings.ScaleNameDialogStrings
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import androidx.compose.runtime.Stable

/**
 * Controls for Scale Name Dialog form.
 */
data class ScaleNameDialogFormControls(
  val name: FormControl<String>,
) {
  companion object Companion {
    fun create() = ScaleNameDialogFormControls(
      name = FormControl.create(
        initialValue = "",
        validators = listOf(
          FormValidations.required(),
          FormValidations.noWhiteSpace(),
          FormValidations.maxLength(100, ScaleNameDialogStrings.ScalenameLabel),
          FormValidations.scaleDisplayNameValidator(BtWifiScaleSetupStrings.DuplicateUser.UserErrorMessage),
        ),
      ),
    )
  }
}

/**
 * State for ScaleDetailsScreen.
 */
@Stable
data class ScaleDetailsState(
  val scale: Device? = null,
  val scaleNameForm: FormGroup<ScaleNameDialogFormControls>,
  val permissions: GGPermissionStatusMap = mutableMapOf(),
  val settingsScreenStep: ScaleSettingSteps = ScaleSettingSteps.NONE,
  val connectedSSID: String? = null,
  val wifiMacAddress: String? = null,
  val deviceInfo: GGDeviceDetail? = null,
  val isSessionImpedanceEnabled: Boolean = false,
  val enableTestingFeatures: Boolean = false,
  val isStartAnimationEnabled: Boolean = false,
  val isEndAnimationEnabled: Boolean = false,
  val currentTimeFormat: String = "12H", // "12H" or "24H"
  val currentClearDataSelection: String? = null, // "ALL", "WIFI", etc.
) : IReducer.State

/**
 * Intents for ScaleDetailsScreen actions.
 */
sealed interface ScaleDetailsIntent : IReducer.Intent {
  data class SetScaleInfo(
    val scale: Device,
  ) : ScaleDetailsIntent

  data class SetConnectedSSID(val connectedSSID: String?) : ScaleDetailsIntent
  data class SetWifiMacAddress(val macAddress: String) : ScaleDetailsIntent

  object EditName : ScaleDetailsIntent

  object DeleteScale : ScaleDetailsIntent

  object OpenProductGuide : ScaleDetailsIntent

  object OpenScaleMode : ScaleDetailsIntent

  object OpenScaleUsers : ScaleDetailsIntent
  object OpenScaleDisplayMetrics : ScaleDetailsIntent
  object OpenWiFiSetup : ScaleDetailsIntent

  object Back : ScaleDetailsIntent
  object ShowScaleNameModal : ScaleDetailsIntent
  object UpdateScaleName : ScaleDetailsIntent
  data class OnCopyMacAddress(val isCopied: Boolean) : ScaleDetailsIntent
  data class SetScaleName(val name: String) : ScaleDetailsIntent
  data class SetPermissions(val permissions: GGPermissionStatusMap) : ScaleDetailsIntent
  data class SetSettingsScreenStep(val step: ScaleSettingSteps) : ScaleDetailsIntent
  data class SetScaleUsers(val users: List<GGBTUser>) : ScaleDetailsIntent
  data class RequestPermission(val permissionType: String) : ScaleDetailsIntent

  // Testing Features Intents
  data class SetDeviceDetail(val deviceInfo: GGDeviceDetail) : ScaleDetailsIntent
  data class ToggleSessionImpedance(val enabled: Boolean) : ScaleDetailsIntent

  // Firmware Update Intents
  object StartFirmwareUpdate : ScaleDetailsIntent
  data class StartScheduledFirmwareUpdate(val timestamp: Long) : ScaleDetailsIntent

  // Additional Settings Intents
  object DownloadLogs : ScaleDetailsIntent
  data class ClearScaleData(val dataType: String) : ScaleDetailsIntent
  data class ChangeTimeFormat(val is12Hour: Boolean) : ScaleDetailsIntent
  data class ToggleScaleAnimation(val isStartAnimation: Boolean, val enabled: Boolean) : ScaleDetailsIntent
  object ResetFirmware : ScaleDetailsIntent
  object RestoreFactorySettings : ScaleDetailsIntent

  // Dialog Management Intents (handled by dialog service)
  object ShowTimeFormatDialog : ScaleDetailsIntent
  object ShowClearDataDialog : ScaleDetailsIntent
  object ShowEnableBodyMetricsAlert : ScaleDetailsIntent
  object EnableBodyMetrics : ScaleDetailsIntent
}

/**
 * Reducer for ScaleDetailsScreen.
 */
class ScaleDetailsReducer : IReducer<ScaleDetailsState, ScaleDetailsIntent> {
  override fun reduce(
    state: ScaleDetailsState,
    intent: ScaleDetailsIntent,
  ): ScaleDetailsState? =
    when (intent) {
      is ScaleDetailsIntent.SetConnectedSSID -> state.copy(connectedSSID = intent.connectedSSID)
      is ScaleDetailsIntent.SetWifiMacAddress -> state.copy(wifiMacAddress = intent.macAddress)
      is ScaleDetailsIntent.SetScaleInfo -> state.copy(scale = intent.scale)
      ScaleDetailsIntent.EditName -> state.copy()
      ScaleDetailsIntent.DeleteScale -> state.copy()
      ScaleDetailsIntent.OpenProductGuide -> state.copy()
      ScaleDetailsIntent.Back -> state.copy()
      is ScaleDetailsIntent.SetScaleName -> {
        val updatedForm = state.scaleNameForm.controls.name.apply {
          onValueChange(intent.name)
        }
        state.copy(scaleNameForm = FormGroup(ScaleNameDialogFormControls(updatedForm)))
      }

      is ScaleDetailsIntent.SetPermissions -> state.copy(permissions = intent.permissions)
      is ScaleDetailsIntent.SetSettingsScreenStep -> state.copy(settingsScreenStep = intent.step)
      is ScaleDetailsIntent.SetDeviceDetail -> state.copy(deviceInfo = intent.deviceInfo)
      // Testing Features Reducers
      is ScaleDetailsIntent.ToggleSessionImpedance -> state.copy(isSessionImpedanceEnabled = intent.enabled)

      // Firmware Update Reducers
      ScaleDetailsIntent.StartFirmwareUpdate -> state.copy()
      is ScaleDetailsIntent.StartScheduledFirmwareUpdate -> state.copy()

      // Additional Settings Reducers
      ScaleDetailsIntent.DownloadLogs -> state.copy()
      is ScaleDetailsIntent.ClearScaleData -> state.copy(
        currentClearDataSelection = intent.dataType,
      )

      is ScaleDetailsIntent.ChangeTimeFormat -> state.copy(
        currentTimeFormat = if (intent.is12Hour) "12H" else "24H",
      )

      is ScaleDetailsIntent.ToggleScaleAnimation -> state.copy(
        isStartAnimationEnabled = if (intent.isStartAnimation) intent.enabled else state.isStartAnimationEnabled,
        isEndAnimationEnabled = if (!intent.isStartAnimation) intent.enabled else state.isEndAnimationEnabled,
      )

      ScaleDetailsIntent.ResetFirmware -> state.copy()
      ScaleDetailsIntent.RestoreFactorySettings -> state.copy()

      // Dialog Management Reducers (no state changes needed - handled by dialog service)
      ScaleDetailsIntent.ShowTimeFormatDialog -> state.copy()
      ScaleDetailsIntent.ShowClearDataDialog -> state.copy()
      ScaleDetailsIntent.ShowEnableBodyMetricsAlert -> state.copy()
      ScaleDetailsIntent.EnableBodyMetrics -> state.copy()

      else -> state.copy()
    }
}

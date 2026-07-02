package com.dmdbrands.gurus.weight.features.deviceDetails.reducer

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.deviceDetails.Enums.DeviceSettingSteps
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.DeviceNameDialogStrings
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import androidx.compose.runtime.Stable

/**
 * Controls for Scale Name Dialog form.
 */
data class DeviceNameDialogFormControls(
  val name: FormControl<String>,
) {
  companion object Companion {
    fun create() = DeviceNameDialogFormControls(
      name = FormControl.create(
        initialValue = "",
        validators = listOf(
          FormValidations.required(),
          FormValidations.noWhiteSpace(),
          FormValidations.maxLength(100, DeviceNameDialogStrings.DevicenameLabel),
          FormValidations.scaleDisplayNameValidator(BtWifiScaleSetupStrings.DuplicateUser.UserErrorMessage),
        ),
      ),
    )
  }
}

/**
 * State for DeviceDetailsScreen.
 */
@Stable
data class DeviceDetailsState(
  val scale: Device? = null,
  val scaleNameForm: FormGroup<DeviceNameDialogFormControls>,
  val permissions: GGPermissionStatusMap = mutableMapOf(),
  val settingsScreenStep: DeviceSettingSteps = DeviceSettingSteps.NONE,
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
 * Intents for DeviceDetailsScreen actions.
 */
sealed interface DeviceDetailsIntent : IReducer.Intent {
  data class SetScaleInfo(
    val scale: Device,
  ) : DeviceDetailsIntent

  data class SetConnectedSSID(val connectedSSID: String?) : DeviceDetailsIntent
  data class SetWifiMacAddress(val macAddress: String) : DeviceDetailsIntent

  object EditName : DeviceDetailsIntent

  object DeleteScale : DeviceDetailsIntent

  object OpenProductGuide : DeviceDetailsIntent

  object OpenScaleMode : DeviceDetailsIntent

  object OpenScaleUsers : DeviceDetailsIntent
  object OpenScaleDisplayMetrics : DeviceDetailsIntent
  object OpenWiFiSetup : DeviceDetailsIntent

  object Back : DeviceDetailsIntent
  object ShowScaleNameModal : DeviceDetailsIntent
  object UpdateScaleName : DeviceDetailsIntent
  data class OnCopyMacAddress(val isCopied: Boolean) : DeviceDetailsIntent
  data class SetScaleName(val name: String) : DeviceDetailsIntent
  data class SetPermissions(val permissions: GGPermissionStatusMap) : DeviceDetailsIntent
  data class SetSettingsScreenStep(val step: DeviceSettingSteps) : DeviceDetailsIntent
  data class SetScaleUsers(val users: List<GGBTUser>) : DeviceDetailsIntent
  data class RequestPermission(val permissionType: String) : DeviceDetailsIntent

  // Testing Features Intents
  data class SetDeviceDetail(val deviceInfo: GGDeviceDetail) : DeviceDetailsIntent
  data class ToggleSessionImpedance(val enabled: Boolean) : DeviceDetailsIntent

  // Firmware Update Intents
  object StartFirmwareUpdate : DeviceDetailsIntent
  data class StartScheduledFirmwareUpdate(val timestamp: Long) : DeviceDetailsIntent

  // Additional Settings Intents
  object DownloadLogs : DeviceDetailsIntent
  data class ClearScaleData(val dataType: String) : DeviceDetailsIntent
  data class ChangeTimeFormat(val is12Hour: Boolean) : DeviceDetailsIntent
  data class ToggleScaleAnimation(val isStartAnimation: Boolean, val enabled: Boolean) : DeviceDetailsIntent
  object ResetFirmware : DeviceDetailsIntent
  object RestoreFactorySettings : DeviceDetailsIntent

  // Dialog Management Intents (handled by dialog service)
  object ShowTimeFormatDialog : DeviceDetailsIntent
  object ShowClearDataDialog : DeviceDetailsIntent
  object ShowEnableBodyMetricsAlert : DeviceDetailsIntent
  object EnableBodyMetrics : DeviceDetailsIntent
}

/**
 * Reducer for DeviceDetailsScreen.
 */
class DeviceDetailsReducer : IReducer<DeviceDetailsState, DeviceDetailsIntent> {
  override fun reduce(
    state: DeviceDetailsState,
    intent: DeviceDetailsIntent,
  ): DeviceDetailsState? =
    when (intent) {
      is DeviceDetailsIntent.SetConnectedSSID -> state.copy(connectedSSID = intent.connectedSSID)
      is DeviceDetailsIntent.SetWifiMacAddress -> state.copy(wifiMacAddress = intent.macAddress)
      is DeviceDetailsIntent.SetScaleInfo -> state.copy(scale = intent.scale)
      DeviceDetailsIntent.EditName -> state.copy()
      DeviceDetailsIntent.DeleteScale -> state.copy()
      DeviceDetailsIntent.OpenProductGuide -> state.copy()
      DeviceDetailsIntent.Back -> state.copy()
      is DeviceDetailsIntent.SetScaleName -> {
        val updatedForm = state.scaleNameForm.controls.name.apply {
          onValueChange(intent.name)
        }
        state.copy(scaleNameForm = FormGroup(DeviceNameDialogFormControls(updatedForm)))
      }

      is DeviceDetailsIntent.SetPermissions -> state.copy(permissions = intent.permissions)
      is DeviceDetailsIntent.SetSettingsScreenStep -> state.copy(settingsScreenStep = intent.step)
      is DeviceDetailsIntent.SetDeviceDetail -> state.copy(deviceInfo = intent.deviceInfo)
      // Testing Features Reducers
      is DeviceDetailsIntent.ToggleSessionImpedance -> state.copy(isSessionImpedanceEnabled = intent.enabled)

      // Firmware Update Reducers
      DeviceDetailsIntent.StartFirmwareUpdate -> state.copy()
      is DeviceDetailsIntent.StartScheduledFirmwareUpdate -> state.copy()

      // Additional Settings Reducers
      DeviceDetailsIntent.DownloadLogs -> state.copy()
      is DeviceDetailsIntent.ClearScaleData -> state.copy(
        currentClearDataSelection = intent.dataType,
      )

      is DeviceDetailsIntent.ChangeTimeFormat -> state.copy(
        currentTimeFormat = if (intent.is12Hour) "12H" else "24H",
      )

      is DeviceDetailsIntent.ToggleScaleAnimation -> state.copy(
        isStartAnimationEnabled = if (intent.isStartAnimation) intent.enabled else state.isStartAnimationEnabled,
        isEndAnimationEnabled = if (!intent.isStartAnimation) intent.enabled else state.isEndAnimationEnabled,
      )

      DeviceDetailsIntent.ResetFirmware -> state.copy()
      DeviceDetailsIntent.RestoreFactorySettings -> state.copy()

      // Dialog Management Reducers (no state changes needed - handled by dialog service)
      DeviceDetailsIntent.ShowTimeFormatDialog -> state.copy()
      DeviceDetailsIntent.ShowClearDataDialog -> state.copy()
      DeviceDetailsIntent.ShowEnableBodyMetricsAlert -> state.copy()
      DeviceDetailsIntent.EnableBodyMetrics -> state.copy()

      else -> state.copy()
    }
}

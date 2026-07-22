package com.dmdbrands.gurus.weight.features.deviceDetails.viewmodel

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsIntent
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsState
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.DeviceDetailsStrings
import com.dmdbrands.library.ggbluetooth.enums.ClearDataType
import com.dmdbrands.library.ggbluetooth.enums.GGBTSettingType
import com.dmdbrands.library.ggbluetooth.model.GGBTSetting
import com.dmdbrands.library.ggbluetooth.model.GGBTSettingValue
import com.greatergoods.blewrapper.GGDeviceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the device-info, firmware, testing-feature and scale-settings side-effect slice of
 * [DeviceDetailsViewModel] (MOB-1500). Every method talks to [ggDeviceService] and reports back
 * through toast/loader callbacks. Behaviour-preserving verbatim move.
 */
class DeviceScaleSettingsManager(
  private val ggDeviceService: GGDeviceService,
  private val scope: CoroutineScope,
  private val getState: () -> DeviceDetailsState,
  private val onIntent: (DeviceDetailsIntent) -> Unit,
  private val showToast: (String) -> Unit,
  private val showToastModel: (Toast) -> Unit,
  private val showLoader: (String) -> Unit,
  private val dismissLoader: () -> Unit,
) {

  private val TAG = "DeviceDetailsViewModel"

  /**
   * Gets device info from the scale (similar to Angular's getDeviceInfo method).
   * Updates device details in the state when connection status changes.
   */
  fun getDeviceInfo() {
    scope.launch {
      try {
        val scale = getState().scale
        if (scale != null && scale.connectionStatus == BLEStatus.CONNECTED) {
          AppLog.d(TAG, "Getting device info for connected scale: ${scale.device?.deviceName}")
          ggDeviceService.getDeviceInfo(scale.toGGBTDevice()) { deviceDetails ->
            if (deviceDetails != null) {
              onIntent(DeviceDetailsIntent.SetDeviceDetail(deviceDetails))
            }
            AppLog.d(
              TAG,
              "Device info received - Firmware: $deviceDetails",
            )
          }
        } else {
          AppLog.w(TAG, "Cannot get device info - scale not connected or null")
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error getting device info", e.toString())
      }
    }
  }

  /**
   * Starts firmware update (similar to Angular's unserviceable.updateFirmware).
   * @param timestamp The timestamp for scheduled update (0 for immediate update)
   */
  fun startFirmwareUpdate(timestamp: Long) {
    scope.launch {
      try {
        val scale = getState().scale
        if (scale != null && scale.connectionStatus == BLEStatus.CONNECTED) {
          val isWifiConfigured = scale.device?.isWifiConfigured ?: false

          if (!isWifiConfigured) {
            showToast(DeviceDetailsStrings.WifiRequiredForUpdate)
            return@launch
          }

          // Show updating message (similar to Angular implementation)
          showToast(DeviceDetailsStrings.UpdatingFirmware)

          // Call the actual firmware update service (similar to Angular's bluetoothservice.updateFirmware)
          ggDeviceService.startFirmwareUpgrade(scale.toGGBTDevice(), timestamp)

          AppLog.d(TAG, "Firmware update started for scale: ${scale.device.deviceName}, timestamp: $timestamp")

          if (timestamp == 0L) {
            showToast(DeviceDetailsStrings.FirmwareUpdateStarted)
          } else {
            val date = java.text.SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", java.util.Locale.getDefault())
              .format(java.util.Date(timestamp))
            showToast("${DeviceDetailsStrings.FirmwareUpdateScheduled} $date")
          }
        } else {
          showToast(DeviceDetailsStrings.DeviceNotConnectedUpdate)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error starting firmware update", e.toString())
        showToast(DeviceDetailsStrings.ErrorStartingUpdate)
      }
    }
  }

  /**
   * Toggles session impedance for the scale (similar to Angular implementation).
   * This is a testing feature that allows enabling/disabling impedance for the current session.
   */
  fun toggleSessionImpedance(enabled: Boolean) {
    scope.launch {
      try {
        val scale = getState().scale
        if (scale != null && scale.connectionStatus == BLEStatus.CONNECTED) {
          // Update the state immediately for UI feedback
          // Call the actual Bluetooth service to update session impedance
          // Similar to Angular's bluetoothService.updateSetting implementation
          ggDeviceService.updateSettings(
            scale.toGGBTDevice(),
            GGBTSetting(
              key = GGBTSettingType.SESSION_IMPEDANCE,
              value = GGBTSettingValue.Boolean(enabled),
            ),
          )

          AppLog.d(TAG, "Session impedance toggled: $enabled for scale: ${scale.device?.deviceName}")

          showToast(
            if (enabled) DeviceDetailsStrings.SessionImpedanceEnabled
            else DeviceDetailsStrings.SessionImpedanceDisabled,
          )
        } else {
          AppLog.w(TAG, "Cannot toggle session impedance - scale not connected")
          showToast(DeviceDetailsStrings.DeviceNotConnectedImpedance)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error toggling session impedance", e.toString())
        showToast(DeviceDetailsStrings.ErrorUpdatingImpedance)
      }
    }
  }

  /**
   * Downloads device logs (similar to Angular implementation).
   */
  fun downloadLogs() {
    scope.launch {
      try {
        val scale = getState().scale
        if (scale != null && scale.connectionStatus == BLEStatus.CONNECTED) {
          showToast(DeviceDetailsStrings.DownloadingLogs)
          ggDeviceService.getDeviceLogs(scale.toGGBTDevice()) { logResponse ->
            // AppLog.d(TAG, "Device logs downloaded: ${logResponse.logs?.size ?: 0} entries")
            showToast(DeviceDetailsStrings.LogsDownloaded)
          }
        } else {
          showToast(DeviceDetailsStrings.DeviceNotConnectedLogs)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error downloading logs", e.toString())
        showToast(DeviceDetailsStrings.ErrorDownloadingLogs)
      }
    }
  }

  /**
   * Clears scale data based on type (similar to Angular's clearData method).
   * @param dataType The type of data to clear ("ALL", "WIFI", "SETTINGS", "HISTORY", "ACCOUNT")
   */
  fun clearScaleData(dataType: String) {
    scope.launch {
      try {
        val scale = getState().scale
        if (scale != null && scale.connectionStatus == BLEStatus.CONNECTED) {
          showToast("${DeviceDetailsStrings.ClearingData} $dataType data...")

          val clearType = when (dataType) {
            "ALL" -> ClearDataType.ALL
            "WIFI" -> ClearDataType.WIFI
            "SETTINGS" -> ClearDataType.SETTINGS
            "HISTORY" -> ClearDataType.HISTORY
            "ACCOUNT" -> ClearDataType.ACCOUNT
            else -> ClearDataType.ALL
          }

          ggDeviceService.clearData(scale.toGGBTDevice(), clearType) { result ->
            AppLog.d(TAG, "Clear data result: $result")
            showToast("$dataType ${DeviceDetailsStrings.DataCleared}")
          }
        } else {
          showToast(DeviceDetailsStrings.DeviceNotConnectedClear)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error clearing scale data", e.toString())
        showToast(DeviceDetailsStrings.ErrorClearingData)
      }
    }
  }

  /**
   * Changes time format on the scale (similar to Angular's changeTimeFormat method).
   * @param is12Hour True for 12-hour format, false for 24-hour format
   */
  fun changeTimeFormat(is12Hour: Boolean) {
    scope.launch {
      try {
        val scale = getState().scale
        if (scale != null && scale.connectionStatus == BLEStatus.CONNECTED) {
          showToast(DeviceDetailsStrings.UpdatingTimeFormat)

          ggDeviceService.updateSettings(
            scale.toGGBTDevice(),
            GGBTSetting(
              key = GGBTSettingType.TIME_FORMAT,
              value = GGBTSettingValue.Boolean(is12Hour),
            ),
          )

          AppLog.d(
            TAG,
            "Time format changed to: ${if (is12Hour) DeviceDetailsStrings.TimeFormat12H else DeviceDetailsStrings.TimeFormat24H}",
          )
          showToast("${DeviceDetailsStrings.TimeFormatUpdated} ${if (is12Hour) DeviceDetailsStrings.TimeFormat12H else DeviceDetailsStrings.TimeFormat24H}")
        } else {
          showToast(DeviceDetailsStrings.DeviceNotConnectedTimeFormat)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error changing time format", e.toString())
        showToast(DeviceDetailsStrings.ErrorChangingTimeFormat)
      }
    }
  }

  /**
   * Toggles scale animation settings (similar to Angular's toggleScaleAnimation method).
   * @param isStartAnimation True for start animation, false for end animation
   * @param enabled Whether the animation should be enabled
   */
  fun toggleScaleAnimation(isStartAnimation: Boolean, enabled: Boolean) {
    scope.launch {
      try {
        val scale = getState().scale
        if (scale != null && scale.connectionStatus == BLEStatus.CONNECTED) {
          val animationType =
            if (isStartAnimation) DeviceDetailsStrings.StartAnimation else DeviceDetailsStrings.EndAnimation
          showToast("${DeviceDetailsStrings.UpdatingAnimation} $animationType animation...")

          val settingKey = if (isStartAnimation) {
            GGBTSettingType.INITIAL_LOGO_ANIM
          } else {
            GGBTSettingType.FINAL_LOGO_ANIM
          }

          ggDeviceService.updateSettings(
            scale.toGGBTDevice(),
            GGBTSetting(
              key = settingKey,
              value = GGBTSettingValue.Boolean(enabled),
            ),
          )

          AppLog.d(
            TAG,
            "$animationType animation ${if (enabled) DeviceDetailsStrings.AnimationEnabled else DeviceDetailsStrings.AnimationDisabled}",
          )
          showToast("$animationType ${if (enabled) DeviceDetailsStrings.AnimationEnabled else DeviceDetailsStrings.AnimationDisabled}")
        } else {
          showToast(DeviceDetailsStrings.DeviceNotConnectedAnimation)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error toggling scale animation", e.toString())
        showToast(DeviceDetailsStrings.ErrorUpdatingAnimation)
      }
    }
  }

  /**
   * Resets firmware (similar to Angular's resetFirmware method).
   */
  fun resetFirmware() {
    scope.launch {
      try {
        val scale = getState().scale
        if (scale != null && scale.connectionStatus == BLEStatus.CONNECTED) {
          showToast(DeviceDetailsStrings.ResettingFirmware)

          ggDeviceService.updateSettings(
            scale.toGGBTDevice(),
            GGBTSetting(
              key = GGBTSettingType.RESET_FIRMWARE,
              value = GGBTSettingValue.Boolean(true),
            ),
          )

          AppLog.d(TAG, "Firmware reset initiated")
          showToast(DeviceDetailsStrings.FirmwareResetSuccess)
        } else {
          showToast(DeviceDetailsStrings.DeviceNotConnectedReset)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error resetting firmware", e.toString())
        showToast(DeviceDetailsStrings.ErrorResettingFirmware)
      }
    }
  }

  /**
   * Restores factory settings (similar to Angular's restoreFactorySettings method).
   */
  fun restoreFactorySettings() {
    scope.launch {
      try {
        val scale = getState().scale
        if (scale != null && scale.connectionStatus == BLEStatus.CONNECTED) {
          showToast(DeviceDetailsStrings.RestoringFactory)

          ggDeviceService.updateSettings(
            scale.toGGBTDevice(),
            GGBTSetting(
              key = GGBTSettingType.RESTORE_FACTORY,
              value = GGBTSettingValue.Boolean(true),
            ),
          )

          AppLog.d(TAG, "Factory settings restore initiated")
          showToast(DeviceDetailsStrings.FactoryRestoreSuccess)
        } else {
          showToast(DeviceDetailsStrings.DeviceNotConnectedFactory)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error restoring factory settings", e.toString())
        showToast(DeviceDetailsStrings.ErrorRestoringFactory)
      }
    }
  }

  /**
   * Enables body metrics for the scale (temporarily).
   * This follows the same pattern as onWeightOnlyModeEnable() in HomeViewModel.
   */
  fun enableBodyMetrics() {
    scope.launch {
      try {
        val scale = getState().scale
        if (scale != null && scale.connectionStatus == BLEStatus.CONNECTED) {
          AppLog.d(TAG, "Enabling body metrics for scale: ${scale.device?.deviceName}")

          // Show loading toast
          showLoader(DeviceDetailsStrings.UpdateMode)

          // Update scale settings to enable body metrics (session impedance)
          enableSessionImpedence(scale)

          // Show success toast
          showToastModel(
            Toast.Simple(
              message = DeviceDetailsStrings.EnableBodyMetricsAlertSuccess,
            ),
          )

          AppLog.d(TAG, "Body metrics enabled successfully for scale: ${scale.device?.deviceName}")
        } else {
          showToast(DeviceDetailsStrings.DeviceNotConnectedImpedance) // Reuse existing message
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error enabling body metrics", e.toString())
        showToast(DeviceDetailsStrings.EnableBodyMetricsAlertError)
      } finally {
        dismissLoader()
      }
    }
  }

  /**
   * Enables session impedance for the scale to allow body metrics collection.
   * This follows the same pattern as enableSessionImpedence() in HomeViewModel.
   */
  private fun enableSessionImpedence(device: Device) {
    scope.launch {
      try {
        ggDeviceService.updateSettings(
          device.toGGBTDevice(),
          GGBTSetting(
            key = GGBTSettingType.SESSION_IMPEDANCE,
            value = GGBTSettingValue.Boolean(true),
          ),
        )
        AppLog.d(TAG, "Session impedance enabled for scale: ${device.device?.deviceName}")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to enable session impedance for scale: ${device.device?.deviceName}", e)
        throw e // Re-throw to be handled by the calling method
      }
    }
  }
}

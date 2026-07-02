package com.dmdbrands.gurus.weight.features.deviceDetails.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.AppStatusService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.showRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0412
import com.dmdbrands.gurus.weight.features.common.helper.StringUtil.cleanCorruptedChars
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsIntent
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsReducer
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsState
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceNameDialogFormControls
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.DeviceDetailsStrings
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.DeviceNameDialogStrings
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.WifiMacAddressStrings
import com.dmdbrands.library.ggbluetooth.enums.ClearDataType
import com.dmdbrands.library.ggbluetooth.enums.GGBTSettingType
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTSetting
import com.dmdbrands.library.ggbluetooth.model.GGBTSettingValue
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel for the DeviceDetails screen. Handles scale details logic and navigation.
 */
@HiltViewModel(
  assistedFactory = DeviceDetailsViewModel.Factory::class,
)
class DeviceDetailsViewModel
@AssistedInject
constructor(
  private val accountService: IAccountService,
  private val deviceService: IDeviceService,
  private val ggDeviceService: GGDeviceService,
  private val permissionService: GGPermissionService,
  private val dialogUtility: IDialogUtility,
  @Assisted val scaleId: String,
) : BaseIntentViewModel<DeviceDetailsState, DeviceDetailsIntent>(
  reducer = DeviceDetailsReducer(),
) {
  @AssistedFactory
  interface Factory {
    fun create(scaleId: String): DeviceDetailsViewModel
  }

  private var activeAccount: Account? = null

  init {
    observeAccountChanges()
    provideInitialState()
    observePermissions()
    observeScaleConnectionChanges()
  }

  override fun provideInitialState(): DeviceDetailsState = DeviceDetailsState(
    scaleNameForm = FormGroup(DeviceNameDialogFormControls.create()),
    enableTestingFeatures = AppStatusService.enableTestingFeatures,
  )

  override fun handleIntent(intent: DeviceDetailsIntent) {
    super.handleIntent(intent)
    when (intent) {

      DeviceDetailsIntent.DeleteScale -> {
        deleteScaleAlert()
      }

      DeviceDetailsIntent.OpenProductGuide -> {
        openProductGuide()
      }

      DeviceDetailsIntent.Back -> {
        navigateBack()
      }

      DeviceDetailsIntent.OpenScaleMode -> {
        openScaleMode()
      }

      DeviceDetailsIntent.OpenScaleDisplayMetrics -> {
        openScaleDisplayMetrics()
      }

      DeviceDetailsIntent.OpenScaleUsers -> openScaleUsers()

      DeviceDetailsIntent.OpenWiFiSetup -> openWiFiSetup()

      DeviceDetailsIntent.ShowScaleNameModal -> openScaleNameModal()
      DeviceDetailsIntent.UpdateScaleName -> updateScaleName()
      is DeviceDetailsIntent.OnCopyMacAddress -> onCopyMacAddress(intent.isCopied)
      is DeviceDetailsIntent.RequestPermission -> requestPermission(
        intent.permissionType,
      )

      // Testing Features Handlers
      is DeviceDetailsIntent.ToggleSessionImpedance -> toggleSessionImpedance(intent.enabled)
      // Firmware Update Handlers
      DeviceDetailsIntent.StartFirmwareUpdate -> startFirmwareUpdate(0) // Immediate update
      is DeviceDetailsIntent.StartScheduledFirmwareUpdate -> startFirmwareUpdate(intent.timestamp)

      // Additional Settings Handlers
      DeviceDetailsIntent.DownloadLogs -> downloadLogs()
      is DeviceDetailsIntent.ClearScaleData -> clearScaleData(intent.dataType)
      is DeviceDetailsIntent.ChangeTimeFormat -> changeTimeFormat(intent.is12Hour)
      is DeviceDetailsIntent.ToggleScaleAnimation -> toggleScaleAnimation(intent.isStartAnimation, intent.enabled)
      DeviceDetailsIntent.ResetFirmware -> resetFirmware()
      DeviceDetailsIntent.RestoreFactorySettings -> restoreFactorySettings()

      // Dialog Management Handlers
      DeviceDetailsIntent.ShowTimeFormatDialog -> showTimeFormatModal()
      DeviceDetailsIntent.ShowClearDataDialog -> showClearDataModal()
      DeviceDetailsIntent.ShowEnableBodyMetricsAlert -> showEnableBodyMetricsAlert()
      DeviceDetailsIntent.EnableBodyMetrics -> enableBodyMetrics()
      else -> {}
    }
  }

  private fun observeAccountChanges() {
    viewModelScope.launch {
      accountService.activeAccountFlow.collect {
        activeAccount = it
      }
    }
  }

  private fun configureR4ScaleDetails() {
    viewModelScope.launch {
      try {
        val scale = state.value.scale ?: return@launch
        ggDeviceService.getConnectedWifiSSID(scale.toGGBTDevice()) { ssid ->
          handleIntent(DeviceDetailsIntent.SetConnectedSSID(if (ssid.isEmpty()) null else ssid.cleanCorruptedChars()))
        }
        fetchWifiMacAddress()
      } catch (e: Exception) {
        handleIntent(DeviceDetailsIntent.SetConnectedSSID(null))
      }
    }
  }

  /**
   * Fetches the WiFi MAC address from the connected R4 scale.
   * Only fetches if the scale is an R4 scale and is connected.
   */
  private fun fetchWifiMacAddress() {
    viewModelScope.launch {
      val scale = state.value.scale
      if (scale != null &&
        scale.deviceType == DeviceSetupType.BtWifiR4.value &&
        scale.connectionStatus == BLEStatus.CONNECTED
      ) {
        try {
          ggDeviceService.getConnectedWifiMacAddress(scale.toGGBTDevice()) { macAddress ->
            handleIntent(DeviceDetailsIntent.SetWifiMacAddress(macAddress))
          }
        } catch (e: Exception) {
          AppLog.e("DeviceDetailsViewModel", "Failed to fetch WiFi MAC address", e)
          handleIntent(DeviceDetailsIntent.SetWifiMacAddress(""))
        }
      }
    }
  }

  private fun openWiFiSetup() {
    viewModelScope.launch {
      try {
        val scale = state.value.scale
        if (scale != null) {
          ggDeviceService.addCacheDevice(scale.device?.broadcastId, scale)
          navigationService.navigateTo(
            AppRoute.DeviceSetup.BtWifiScaleSetup(
              scale.sku ?: SKU_0412,
              BtWifiSetupStep.GATHERING_NETWORK,
              scale.device?.broadcastId,
            ),
          )
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to navigate to WiFi setup", e)
      }
    }
  }

  private fun observePermissions() {
    viewModelScope.launch {
      permissionService.permissionCallBackFlow.collect {
        handleIntent(DeviceDetailsIntent.SetPermissions(it))
      }
    }
  }

  /**
   * Observes scale connection changes and updates device info when connection status changes.
   * Similar to Angular's pairedScaleService.scales subscription in scale-detail.page.ts
   */
  private fun observeScaleConnectionChanges() {
    viewModelScope.launch {
      deviceService.pairedScales.collect { devices ->
        val updatedScale = devices.find { it.id == scaleId }
        updatedScale?.let { scale ->
          handleIntent(DeviceDetailsIntent.SetScaleInfo(scale))
          val scaleName = scale.nickname
          handleIntent(DeviceDetailsIntent.SetScaleName(scaleName))
          getDeviceInfo()
          configureR4ScaleDetails()
        }
      }
    }
  }

  private fun openProductGuide() {
    val sku = state.value.scale?.getSKU()
    if (!sku.isNullOrEmpty()) {
      val url = "${AppConfig.PRODUCT_URL}/$sku"
      openInAppBrowser(url)
    }
  }

  private fun openScaleMode() {
    viewModelScope.launch {
      val id = state.value.scale?.id ?: return@launch
      navigationService.navigateTo(AppRoute.DeviceDetails.DeviceMode(id))
    }
  }

  private fun openScaleDisplayMetrics() {
    viewModelScope.launch {
      val id = state.value.scale?.id ?: return@launch
      navigationService.navigateTo(AppRoute.DeviceDetails.DeviceDisplayMetrics(id))
    }
  }

  private fun deleteScaleAlert() {
    try {
      viewModelScope.launch {
        dialogQueueService.showDialog(
          DialogModel.Confirm(
            message = DeviceDetailsStrings.DeleteConfirmation,
            confirmText = DeviceDetailsStrings.Delete,
            cancelText = DeviceDetailsStrings.Cancel,
            primaryActionType = com.dmdbrands.gurus.weight.features.common.components.ButtonType.ErrorText,
            onConfirm = {
              viewModelScope.launch {
                val scale = state.value.scale ?: return@launch
                dialogQueueService.dismissCurrent()
                dialogQueueService.showLoader(message = DeviceDetailsStrings.DeleteLoaderMessage)
                try {
                  if (scale.deviceType == DeviceSetupType.BtWifiR4.value && scale.connectionStatus == BLEStatus.CONNECTED) {
                    ggDeviceService.deleteAccount(scale.toGGBTDevice()) {
                      if (it == GGUserActionResponseType.DELETE_COMPLETED) {
                        ggDeviceService.disconnectDevice(scale.toGGBTDevice())
                      } else {
                        dialogQueueService.showToast(
                          Toast.Simple(
                            message = DeviceDetailsStrings.DeleteErrorMessage,
                          ),
                        )
                      }
                    }
                  }
                  deviceService.deleteScale(scale.id)
                  dialogQueueService.showToast(
                    Toast.Simple(
                      message = DeviceDetailsStrings.DeleteSuccessMessage,
                    ),
                  )
                } catch (e: Exception) {
                  dialogQueueService.showToast(
                    Toast.Simple(
                      message = DeviceDetailsStrings.DeleteErrorMessage,
                    ),
                  )
                } finally {
                  dialogQueueService.dismissLoader()
                  navigateBack()
                }
              }
            },
            onDismiss = {
              dialogQueueService.dismissCurrent()
            },

            ),

          )
      }
    } catch (e: Exception) {
      dialogQueueService.dismissLoader()
      AppLog.d(TAG, "Error while deleting an scale")
    }
  }

  private fun openScaleUsers() {
    viewModelScope.launch {
      val id = state.value.scale?.id ?: return@launch
      navigationService.navigateTo(AppRoute.DeviceDetails.DeviceUsers(id))
    }
  }

  /**
   * Opens the Scale Name modal.
   */
  private fun openScaleNameModal() {
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.DeviceName,
        params = mapOf(
          "scaleId" to (state.value.scale?.id ?: scaleId),
          "accountId" to (activeAccount?.id ?: ""),
        ),
        dismissOnBackPress = true,
      ),
    )
  }

  /**
   * Handles scale name update with loader and error handling.
   * @param scaleName The scale name to update scale nickname for btwifi scale.
   */
  private fun updateScaleName() {
    val nameControl = state.value.scaleNameForm.controls.name
    val trimmedScaleName = nameControl.value.trim()

    // Normalize scale name input before validation/save.
    if (trimmedScaleName != nameControl.value) {
      nameControl.onValueChange(trimmedScaleName)
    }

    if (!state.value.scaleNameForm.isValid) {
      return
    }
    val scaleName = trimmedScaleName
    dialogQueueService.showLoader(
      message = DeviceNameDialogStrings.LoaderMessage,
    )
    viewModelScope.launch {
      try {
        val scale = state.value.scale ?: return@launch
        deviceService.updateScaleNickname(scale, scaleName)
        AppLog.i("SaveScaleName", "Updated scale name: ${state.value.scale}")
        showToast(DeviceNameDialogStrings.Toast.Success)
        dialogQueueService.dismissCurrent()
        // Note: Form will be repopulated with updated nickname when dialog reopens
        // because setScaleDetails() observes device changes and updates the form
      } catch (e: Exception) {
        AppLog.e("SaveScaleName", "Reset Password failed", e)
        showToast(DeviceNameDialogStrings.Toast.Error)
      } finally {
        dialogQueueService.dismissLoader()
        // Don't reset form here - let it be handled by the scale data observer
      }
    }
  }

  /**
   * Requests a specific permission with rationale alert using the permission service.
   */
  private fun requestPermission(permissionType: String) {
    viewModelScope.launch {
      try {
        dialogUtility.permissionAlert(
          permissionType = permissionType,
          onRequest = {
            permissionService.requestPermission(permissionType)
          },
        )
      } catch (e: Exception) {
        AppLog.e("requestPermission", "Error requesting permission ${permissionType}", e)
      }
    }
  }

  private fun onCopyMacAddress(isCopied: Boolean) {
    showToast(
      message = if (isCopied) WifiMacAddressStrings.Toast.Success
      else WifiMacAddressStrings.Toast.Error,
    )
  }

  private fun showToast(message: String) {
    dialogQueueService.showToast(
      Toast.Simple(
        title = null,
        message = message,
        action = null,
      ),
    )
  }

  /**
   * Gets device info from the scale (similar to Angular's getDeviceInfo method).
   * Updates device details in the state when connection status changes.
   */
  private fun getDeviceInfo() {
    viewModelScope.launch {
      try {
        val scale = state.value.scale
        if (scale != null && scale.connectionStatus == BLEStatus.CONNECTED) {
          AppLog.d(TAG, "Getting device info for connected scale: ${scale.device?.deviceName}")
          ggDeviceService.getDeviceInfo(scale.toGGBTDevice()) { deviceDetails ->
            if (deviceDetails != null) {
              handleIntent(DeviceDetailsIntent.SetDeviceDetail(deviceDetails))
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
  private fun startFirmwareUpdate(timestamp: Long) {
    viewModelScope.launch {
      try {
        val scale = state.value.scale
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
  private fun toggleSessionImpedance(enabled: Boolean) {
    viewModelScope.launch {
      try {
        val scale = state.value.scale
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
  private fun downloadLogs() {
    viewModelScope.launch {
      try {
        val scale = state.value.scale
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
  private fun clearScaleData(dataType: String) {
    viewModelScope.launch {
      try {
        val scale = state.value.scale
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
  private fun changeTimeFormat(is12Hour: Boolean) {
    viewModelScope.launch {
      try {
        val scale = state.value.scale
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
  private fun toggleScaleAnimation(isStartAnimation: Boolean, enabled: Boolean) {
    viewModelScope.launch {
      try {
        val scale = state.value.scale
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
  private fun resetFirmware() {
    viewModelScope.launch {
      try {
        val scale = state.value.scale
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
  private fun restoreFactorySettings() {
    viewModelScope.launch {
      try {
        val scale = state.value.scale
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
   * Shows the time format selection modal.
   */
  private fun showTimeFormatModal() {
    val currentSelection = state.value.currentTimeFormat
    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = DeviceDetailsStrings.TimeFormat,
      options = listOf(
        RadioButtonOption(DeviceDetailsStrings.TimeFormat12H, DeviceDetailsStrings.TimeFormat12H),
        RadioButtonOption(DeviceDetailsStrings.TimeFormat24H, DeviceDetailsStrings.TimeFormat24H),
      ),
      selectedItem = currentSelection,
      onConfirm = { selectedValue ->
        val is12Hour = selectedValue == DeviceDetailsStrings.TimeFormat12H
        handleIntent(DeviceDetailsIntent.ChangeTimeFormat(is12Hour))
      },
    )
  }

  /**
   * Shows the clear data selection modal.
   */
  private fun showClearDataModal() {
    val currentSelection = state.value.currentClearDataSelection
    showRadioGroupModal(
      dialogService = dialogQueueService,
      title = DeviceDetailsStrings.ClearData,
      options = listOf(
        RadioButtonOption("ALL", DeviceDetailsStrings.All),
        RadioButtonOption("WIFI", DeviceDetailsStrings.WiFi),
        RadioButtonOption("SETTINGS", DeviceDetailsStrings.Settings),
        RadioButtonOption("HISTORY", DeviceDetailsStrings.History),
        RadioButtonOption("ACCOUNT", DeviceDetailsStrings.Account),
      ),
      selectedItem = currentSelection,
      onConfirm = { selectedValue ->
        handleIntent(DeviceDetailsIntent.ClearScaleData(selectedValue ?: ""))
      },
    )
  }

  /**
   * Shows the enable body metrics alert dialog.
   */
  private fun showEnableBodyMetricsAlert() {
    try {
      viewModelScope.launch {
        dialogQueueService.showDialog(
          DialogModel.Confirm(
            title = DeviceDetailsStrings.EnableBodyMetricsAlertTitle,
            message = DeviceDetailsStrings.EnableBodyMetricsAlertMessage,
            confirmText = DeviceDetailsStrings.EnableBodyMetricsAlertConfirm,
            cancelText = DeviceDetailsStrings.EnableBodyMetricsAlertCancel,
            onConfirm = {
              viewModelScope.launch {
                dialogQueueService.dismissCurrent()
                handleIntent(DeviceDetailsIntent.EnableBodyMetrics)
              }
            },
            onDismiss = {
              dialogQueueService.dismissCurrent()
            },
          ),
        )
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error showing enable body metrics alert", e.toString())
    }
  }

  /**
   * Enables body metrics for the scale (temporarily).
   * This follows the same pattern as onWeightOnlyModeEnable() in HomeViewModel.
   */
  private fun enableBodyMetrics() {
    viewModelScope.launch {
      try {
        val scale = state.value.scale
        if (scale != null && scale.connectionStatus == BLEStatus.CONNECTED) {
          AppLog.d(TAG, "Enabling body metrics for scale: ${scale.device?.deviceName}")

          // Show loading toast
          dialogQueueService.showLoader(
            message = DeviceDetailsStrings.UpdateMode,
          )

          // Update scale settings to enable body metrics (session impedance)
          enableSessionImpedence(scale)

          // Show success toast
          dialogQueueService.showToast(
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
        dialogQueueService.dismissLoader()
      }
    }
  }

  /**
   * Enables session impedance for the scale to allow body metrics collection.
   * This follows the same pattern as enableSessionImpedence() in HomeViewModel.
   */
  private fun enableSessionImpedence(device: Device) {
    viewModelScope.launch {
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

  private fun navigateBack() {
    viewModelScope.launch {
      navigationService.navigateBack()
    }
  }

  companion object {
    private const val TAG = "DeviceDetailsViewModel"
  }
}

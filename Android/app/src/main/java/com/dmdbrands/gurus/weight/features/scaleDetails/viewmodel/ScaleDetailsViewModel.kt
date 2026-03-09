package com.dmdbrands.gurus.weight.features.scaleDetails.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.AccountService
import com.dmdbrands.gurus.weight.core.service.AppStatusService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.showRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0412
import com.dmdbrands.gurus.weight.features.common.helper.StringUtil.cleanCorruptedChars
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleDetailsIntent
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleDetailsReducer
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleDetailsState
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleNameDialogFormControls
import com.dmdbrands.gurus.weight.features.scaleDetails.strings.ScaleDetailsStrings
import com.dmdbrands.gurus.weight.features.scaleDetails.strings.ScaleNameDialogStrings
import com.dmdbrands.gurus.weight.features.scaleDetails.strings.WifiMacAddressStrings
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
 * ViewModel for the ScaleDetails screen. Handles scale details logic and navigation.
 */
@HiltViewModel(
  assistedFactory = ScaleDetailsViewModel.Factory::class,
)
class ScaleDetailsViewModel
@AssistedInject
constructor(
  private var accountService: AccountService,
  private val deviceService: IDeviceService,
  private val ggDeviceService: GGDeviceService,
  private val permissionService: GGPermissionService,
  private val dialogUtility: IDialogUtility,
  @Assisted val scaleId: String,
) : BaseIntentViewModel<ScaleDetailsState, ScaleDetailsIntent>(
  reducer = ScaleDetailsReducer(),
) {
  @AssistedFactory
  interface Factory {
    fun create(scaleId: String): ScaleDetailsViewModel
  }

  private var activeAccount: Account? = null

  init {
    observeAccountChanges()
    provideInitialState()
    observePermissions()
    observeScaleConnectionChanges()
  }

  override fun provideInitialState(): ScaleDetailsState = ScaleDetailsState(
    scaleNameForm = FormGroup(ScaleNameDialogFormControls.create()),
    enableTestingFeatures = AppStatusService.enableTestingFeatures,
  )

  override fun handleIntent(intent: ScaleDetailsIntent) {
    super.handleIntent(intent)
    when (intent) {

      ScaleDetailsIntent.DeleteScale -> {
        deleteScaleAlert()
      }

      ScaleDetailsIntent.OpenProductGuide -> {
        openProductGuide()
      }

      ScaleDetailsIntent.Back -> {
        navigateBack()
      }

      ScaleDetailsIntent.OpenScaleMode -> {
        openScaleMode()
      }

      ScaleDetailsIntent.OpenScaleDisplayMetrics -> {
        openScaleDisplayMetrics()
      }

      ScaleDetailsIntent.OpenScaleUsers -> openScaleUsers()

      ScaleDetailsIntent.OpenWiFiSetup -> openWiFiSetup()


      ScaleDetailsIntent.ShowScaleNameModal -> openScaleNameModal()
      ScaleDetailsIntent.UpdateScaleName -> updateScaleName()
      is ScaleDetailsIntent.OnCopyMacAddress -> onCopyMacAddress(intent.isCopied)
      is ScaleDetailsIntent.RequestPermission -> requestPermission(
        intent.permissionType,
      )

      // Testing Features Handlers
      is ScaleDetailsIntent.ToggleSessionImpedance -> toggleSessionImpedance(intent.enabled)
      // Firmware Update Handlers
      ScaleDetailsIntent.StartFirmwareUpdate -> startFirmwareUpdate(0) // Immediate update
      is ScaleDetailsIntent.StartScheduledFirmwareUpdate -> startFirmwareUpdate(intent.timestamp)

      // Additional Settings Handlers
      ScaleDetailsIntent.DownloadLogs -> downloadLogs()
      is ScaleDetailsIntent.ClearScaleData -> clearScaleData(intent.dataType)
      is ScaleDetailsIntent.ChangeTimeFormat -> changeTimeFormat(intent.is12Hour)
      is ScaleDetailsIntent.ToggleScaleAnimation -> toggleScaleAnimation(intent.isStartAnimation, intent.enabled)
      ScaleDetailsIntent.ResetFirmware -> resetFirmware()
      ScaleDetailsIntent.RestoreFactorySettings -> restoreFactorySettings()

      // Dialog Management Handlers
      ScaleDetailsIntent.ShowTimeFormatDialog -> showTimeFormatModal()
      ScaleDetailsIntent.ShowClearDataDialog -> showClearDataModal()
      ScaleDetailsIntent.ShowEnableBodyMetricsAlert -> showEnableBodyMetricsAlert()
      ScaleDetailsIntent.EnableBodyMetrics -> enableBodyMetrics()
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
          handleIntent(ScaleDetailsIntent.SetConnectedSSID(if (ssid.isEmpty()) null else ssid.cleanCorruptedChars()))
        }
        fetchWifiMacAddress()
      } catch (e: Exception) {
        handleIntent(ScaleDetailsIntent.SetConnectedSSID(null))
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
          scale.deviceType == ScaleSetupType.BtWifiR4.value &&
          scale.connectionStatus == BLEStatus.CONNECTED) {
        try {
          ggDeviceService.getConnectedWifiMacAddress(scale.toGGBTDevice()) { macAddress ->
            handleIntent(ScaleDetailsIntent.SetWifiMacAddress(macAddress))
          }
        } catch (e: Exception) {
          AppLog.e("ScaleDetailsViewModel", "Failed to fetch WiFi MAC address", e)
          handleIntent(ScaleDetailsIntent.SetWifiMacAddress(""))
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
            AppRoute.ScaleSetup.BtWifiScaleSetup(
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
        handleIntent(ScaleDetailsIntent.SetPermissions(it))
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
          handleIntent(ScaleDetailsIntent.SetScaleInfo(scale))
          val scaleName = scale.nickname
          handleIntent(ScaleDetailsIntent.SetScaleName(scaleName))
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
      navigationService.navigateTo(AppRoute.ScaleDetails.ScaleMode(id))
    }
  }

  private fun openScaleDisplayMetrics() {
    viewModelScope.launch {
      val id = state.value.scale?.id ?: return@launch
      navigationService.navigateTo(AppRoute.ScaleDetails.ScaleDisplayMetrics(id))
    }
  }

  private fun deleteScaleAlert() {
    try {
      viewModelScope.launch {
        dialogQueueService.showDialog(
          DialogModel.Confirm(
            message = ScaleDetailsStrings.DeleteScaleConfirmation,
            confirmText = ScaleDetailsStrings.Delete,
            cancelText = ScaleDetailsStrings.Cancel,
            primaryActionType = com.dmdbrands.gurus.weight.features.common.components.ButtonType.ErrorText,
            onConfirm = {
              viewModelScope.launch {
                val scale = state.value.scale ?: return@launch
                dialogQueueService.dismissCurrent()
                dialogQueueService.showLoader(message = ScaleDetailsStrings.DeleteLoaderMessage)
                try {
                  if (scale.deviceType == ScaleSetupType.BtWifiR4.value && scale.connectionStatus == BLEStatus.CONNECTED) {
                    ggDeviceService.deleteAccount(scale.toGGBTDevice()) {
                      if (it == GGUserActionResponseType.DELETE_COMPLETED) {
                        ggDeviceService.disconnectDevice(scale.toGGBTDevice())
                      } else {
                        dialogQueueService.showToast(
                          Toast(
                            message = ScaleDetailsStrings.DeleteErrorMessage,
                          ),
                        )
                      }
                    }
                  }
                  deviceService.deleteScale(scale.id)
                  dialogQueueService.showToast(
                    Toast(
                      message = ScaleDetailsStrings.DeleteSuccessMessage,
                    ),
                  )
                } catch (e: Exception) {
                  dialogQueueService.showToast(
                    Toast(
                      message = ScaleDetailsStrings.DeleteErrorMessage,
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
      navigationService.navigateTo(AppRoute.ScaleDetails.ScaleUsers(id))
    }
  }

  /**
   * Opens the Scale Name modal.
   */
  private fun openScaleNameModal() {
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.ScaleName,
        params = mapOf(
          "scaleId" to (state.value.scale?.id ?: scaleId),
          "accountId" to (activeAccount?.id ?: ""),
        ),
        dismissOnBackPress = true
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
      message = ScaleNameDialogStrings.LoaderMessage,
    )
    viewModelScope.launch {
      try {
        val scale = state.value.scale ?: return@launch
        deviceService.updateScaleNickname(scale, scaleName)
        AppLog.i("SaveScaleName", "Updated scale name: ${state.value.scale}")
        showToast(ScaleNameDialogStrings.Toast.Success)
        dialogQueueService.dismissCurrent()
        // Note: Form will be repopulated with updated nickname when dialog reopens
        // because setScaleDetails() observes device changes and updates the form
      } catch (e: Exception) {
        AppLog.e("SaveScaleName", "Reset Password failed", e)
        showToast(ScaleNameDialogStrings.Toast.Error)
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
      Toast(
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
              handleIntent(ScaleDetailsIntent.SetDeviceDetail(deviceDetails))
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
            showToast(ScaleDetailsStrings.WifiRequiredForUpdate)
            return@launch
          }

          // Show updating message (similar to Angular implementation)
          showToast(ScaleDetailsStrings.UpdatingFirmware)

          // Call the actual firmware update service (similar to Angular's bluetoothservice.updateFirmware)
          ggDeviceService.startFirmwareUpgrade(scale.toGGBTDevice(), timestamp)

          AppLog.d(TAG, "Firmware update started for scale: ${scale.device.deviceName}, timestamp: $timestamp")

          if (timestamp == 0L) {
            showToast(ScaleDetailsStrings.FirmwareUpdateStarted)
          } else {
            val date = java.text.SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", java.util.Locale.getDefault())
              .format(java.util.Date(timestamp))
            showToast("${ScaleDetailsStrings.FirmwareUpdateScheduled} $date")
          }
        } else {
          showToast(ScaleDetailsStrings.ScaleNotConnectedUpdate)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error starting firmware update", e.toString())
        showToast(ScaleDetailsStrings.ErrorStartingUpdate)
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
            if (enabled) ScaleDetailsStrings.SessionImpedanceEnabled
            else ScaleDetailsStrings.SessionImpedanceDisabled,
          )
        } else {
          AppLog.w(TAG, "Cannot toggle session impedance - scale not connected")
          showToast(ScaleDetailsStrings.ScaleNotConnectedImpedance)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error toggling session impedance", e.toString())
        showToast(ScaleDetailsStrings.ErrorUpdatingImpedance)
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
          showToast(ScaleDetailsStrings.DownloadingLogs)
// TODO: need to implement download option
          ggDeviceService.getDeviceLogs(scale.toGGBTDevice()) { logResponse ->
            // AppLog.d(TAG, "Device logs downloaded: ${logResponse.logs?.size ?: 0} entries")
            showToast(ScaleDetailsStrings.LogsDownloaded)
          }
        } else {
          showToast(ScaleDetailsStrings.ScaleNotConnectedLogs)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error downloading logs", e.toString())
        showToast(ScaleDetailsStrings.ErrorDownloadingLogs)
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
          showToast("${ScaleDetailsStrings.ClearingData} $dataType data...")

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
            showToast("$dataType ${ScaleDetailsStrings.DataCleared}")
          }
        } else {
          showToast(ScaleDetailsStrings.ScaleNotConnectedClear)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error clearing scale data", e.toString())
        showToast(ScaleDetailsStrings.ErrorClearingData)
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
          showToast(ScaleDetailsStrings.UpdatingTimeFormat)

          ggDeviceService.updateSettings(
            scale.toGGBTDevice(),
            GGBTSetting(
              key = GGBTSettingType.TIME_FORMAT,
              value = GGBTSettingValue.Boolean(is12Hour),
            ),
          )

          AppLog.d(
            TAG,
            "Time format changed to: ${if (is12Hour) ScaleDetailsStrings.TimeFormat12H else ScaleDetailsStrings.TimeFormat24H}",
          )
          showToast("${ScaleDetailsStrings.TimeFormatUpdated} ${if (is12Hour) ScaleDetailsStrings.TimeFormat12H else ScaleDetailsStrings.TimeFormat24H}")
        } else {
          showToast(ScaleDetailsStrings.ScaleNotConnectedTimeFormat)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error changing time format", e.toString())
        showToast(ScaleDetailsStrings.ErrorChangingTimeFormat)
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
            if (isStartAnimation) ScaleDetailsStrings.StartAnimation else ScaleDetailsStrings.EndAnimation
          showToast("${ScaleDetailsStrings.UpdatingAnimation} $animationType animation...")

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
            "$animationType animation ${if (enabled) ScaleDetailsStrings.AnimationEnabled else ScaleDetailsStrings.AnimationDisabled}",
          )
          showToast("$animationType ${if (enabled) ScaleDetailsStrings.AnimationEnabled else ScaleDetailsStrings.AnimationDisabled}")
        } else {
          showToast(ScaleDetailsStrings.ScaleNotConnectedAnimation)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error toggling scale animation", e.toString())
        showToast(ScaleDetailsStrings.ErrorUpdatingAnimation)
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
          showToast(ScaleDetailsStrings.ResettingFirmware)

          ggDeviceService.updateSettings(
            scale.toGGBTDevice(),
            GGBTSetting(
              key = GGBTSettingType.RESET_FIRMWARE,
              value = GGBTSettingValue.Boolean(true),
            ),
          )

          AppLog.d(TAG, "Firmware reset initiated")
          showToast(ScaleDetailsStrings.FirmwareResetSuccess)
        } else {
          showToast(ScaleDetailsStrings.ScaleNotConnectedReset)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error resetting firmware", e.toString())
        showToast(ScaleDetailsStrings.ErrorResettingFirmware)
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
          showToast(ScaleDetailsStrings.RestoringFactory)

          ggDeviceService.updateSettings(
            scale.toGGBTDevice(),
            GGBTSetting(
              key = GGBTSettingType.RESTORE_FACTORY,
              value = GGBTSettingValue.Boolean(true),
            ),
          )

          AppLog.d(TAG, "Factory settings restore initiated")
          showToast(ScaleDetailsStrings.FactoryRestoreSuccess)
        } else {
          showToast(ScaleDetailsStrings.ScaleNotConnectedFactory)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error restoring factory settings", e.toString())
        showToast(ScaleDetailsStrings.ErrorRestoringFactory)
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
      title = ScaleDetailsStrings.TimeFormat,
      options = listOf(
        RadioButtonOption(ScaleDetailsStrings.TimeFormat12H, ScaleDetailsStrings.TimeFormat12H),
        RadioButtonOption(ScaleDetailsStrings.TimeFormat24H, ScaleDetailsStrings.TimeFormat24H),
      ),
      selectedItem = currentSelection,
      onConfirm = { selectedValue ->
        val is12Hour = selectedValue == ScaleDetailsStrings.TimeFormat12H
        handleIntent(ScaleDetailsIntent.ChangeTimeFormat(is12Hour))
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
      title = ScaleDetailsStrings.ClearData,
      options = listOf(
        RadioButtonOption("ALL", ScaleDetailsStrings.All),
        RadioButtonOption("WIFI", ScaleDetailsStrings.WiFi),
        RadioButtonOption("SETTINGS", ScaleDetailsStrings.Settings),
        RadioButtonOption("HISTORY", ScaleDetailsStrings.History),
        RadioButtonOption("ACCOUNT", ScaleDetailsStrings.Account),
      ),
      selectedItem = currentSelection,
      onConfirm = { selectedValue ->
        handleIntent(ScaleDetailsIntent.ClearScaleData(selectedValue ?: ""))
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
            title = ScaleDetailsStrings.EnableBodyMetricsAlertTitle,
            message = ScaleDetailsStrings.EnableBodyMetricsAlertMessage,
            confirmText = ScaleDetailsStrings.EnableBodyMetricsAlertConfirm,
            cancelText = ScaleDetailsStrings.EnableBodyMetricsAlertCancel,
            onConfirm = {
              viewModelScope.launch {
                dialogQueueService.dismissCurrent()
                handleIntent(ScaleDetailsIntent.EnableBodyMetrics)
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
            message = ScaleDetailsStrings.UpdateMode,
          )

          // Update scale settings to enable body metrics (session impedance)
          enableSessionImpedence(scale)

          // Show success toast
          dialogQueueService.showToast(
            Toast(
              message = ScaleDetailsStrings.EnableBodyMetricsAlertSuccess,
            ),
          )

          AppLog.d(TAG, "Body metrics enabled successfully for scale: ${scale.device?.deviceName}")
        } else {
          showToast(ScaleDetailsStrings.ScaleNotConnectedImpedance) // Reuse existing message
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error enabling body metrics", e.toString())
        showToast(ScaleDetailsStrings.EnableBodyMetricsAlertError)
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
    private const val TAG = "ScaleDetailsViewModel"
  }
}

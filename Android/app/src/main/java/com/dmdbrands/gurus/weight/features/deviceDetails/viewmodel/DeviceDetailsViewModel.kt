package com.dmdbrands.gurus.weight.features.deviceDetails.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.AppStatusService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsIntent
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsReducer
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsState
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceNameDialogFormControls
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.DeviceNameDialogStrings
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.WifiMacAddressStrings
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

/**
 * Coordinator ViewModel for the DeviceDetails screen.
 *
 * Owns screen-level intent dispatch, flow subscriptions and simple navigation, delegating each
 * cohesive slice to a focused collaborator: device-info / firmware / settings side-effects
 * ([DeviceScaleSettingsManager]), dialogs & modals ([DeviceDetailsDialogManager]) and the R4 WiFi
 * lookup / setup ([DeviceWifiManager]). Split from a single large ViewModel in MOB-1500 to clear
 * detekt's LargeClass limit; behaviour-preserving.
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

  private val settingsManager: DeviceScaleSettingsManager = DeviceScaleSettingsManager(
    ggDeviceService = ggDeviceService,
    scope = viewModelScope,
    getState = { state.value },
    onIntent = ::handleIntent,
    showToast = ::showToast,
    showToastModel = { dialogQueueService.showToast(it) },
    showLoader = { dialogQueueService.showLoader(message = it) },
    dismissLoader = { dialogQueueService.dismissLoader() },
  )

  private val dialogManager: DeviceDetailsDialogManager = DeviceDetailsDialogManager(
    ggDeviceService = ggDeviceService,
    deviceService = deviceService,
    scope = viewModelScope,
    scaleId = scaleId,
    getState = { state.value },
    onIntent = ::handleIntent,
    getDialogQueueService = { dialogQueueService },
    getActiveAccountId = { activeAccount?.id },
    navigateBack = { navigationService.navigateBack() },
  )

  private val wifiManager: DeviceWifiManager = DeviceWifiManager(
    ggDeviceService = ggDeviceService,
    scope = viewModelScope,
    getState = { state.value },
    onIntent = ::handleIntent,
    navigateTo = { navigationService.navigateTo(it) },
  )

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
        dialogManager.deleteScaleAlert()
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

      DeviceDetailsIntent.OpenWiFiSetup -> wifiManager.openWiFiSetup()

      DeviceDetailsIntent.ShowScaleNameModal -> dialogManager.openScaleNameModal()
      DeviceDetailsIntent.UpdateScaleName -> updateScaleName()
      is DeviceDetailsIntent.OnCopyMacAddress -> onCopyMacAddress(intent.isCopied)
      is DeviceDetailsIntent.RequestPermission -> requestPermission(
        intent.permissionType,
      )

      // Testing Features Handlers
      is DeviceDetailsIntent.ToggleSessionImpedance -> settingsManager.toggleSessionImpedance(intent.enabled)
      // Firmware Update Handlers
      DeviceDetailsIntent.StartFirmwareUpdate -> settingsManager.startFirmwareUpdate(0) // Immediate update
      is DeviceDetailsIntent.StartScheduledFirmwareUpdate -> settingsManager.startFirmwareUpdate(intent.timestamp)

      // Additional Settings Handlers
      DeviceDetailsIntent.DownloadLogs -> settingsManager.downloadLogs()
      is DeviceDetailsIntent.ClearScaleData -> settingsManager.clearScaleData(intent.dataType)
      is DeviceDetailsIntent.ChangeTimeFormat -> settingsManager.changeTimeFormat(intent.is12Hour)
      is DeviceDetailsIntent.ToggleScaleAnimation ->
        settingsManager.toggleScaleAnimation(intent.isStartAnimation, intent.enabled)
      DeviceDetailsIntent.ResetFirmware -> settingsManager.resetFirmware()
      DeviceDetailsIntent.RestoreFactorySettings -> settingsManager.restoreFactorySettings()

      // Dialog Management Handlers
      DeviceDetailsIntent.ShowTimeFormatDialog -> dialogManager.showTimeFormatModal()
      DeviceDetailsIntent.ShowClearDataDialog -> dialogManager.showClearDataModal()
      DeviceDetailsIntent.ShowEnableBodyMetricsAlert -> dialogManager.showEnableBodyMetricsAlert()
      DeviceDetailsIntent.EnableBodyMetrics -> settingsManager.enableBodyMetrics()
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
          settingsManager.getDeviceInfo()
          wifiManager.configureR4ScaleDetails()
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

  private fun openScaleUsers() {
    viewModelScope.launch {
      val id = state.value.scale?.id ?: return@launch
      navigationService.navigateTo(AppRoute.DeviceDetails.DeviceUsers(id))
    }
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

  private fun navigateBack() {
    viewModelScope.launch {
      navigationService.navigateBack()
    }
  }
}

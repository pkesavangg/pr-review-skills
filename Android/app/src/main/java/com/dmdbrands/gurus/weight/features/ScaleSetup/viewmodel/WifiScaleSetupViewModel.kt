package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.service.WifiScaleService
import com.dmdbrands.gurus.weight.core.service.WifiSetupInfo
import com.dmdbrands.gurus.weight.core.service.WifiSetupType
import com.dmdbrands.gurus.weight.core.service.WifiStatus
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enum.CustomPermissionType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.WifiScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.SetupPath
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScalePasswordFormControls
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScaleSetupReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.SCALES
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.gurus.weight.features.login.strings.LoginStrings
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log

@HiltViewModel(
  assistedFactory = WifiScaleSetupViewModel.Factory::class,
)
class WifiScaleSetupViewModel
@AssistedInject
constructor(
  @Assisted("sku") private val sku: String,
  @Assisted("wifiSetupType") private val wifiSetupTypeString: String,
  @Assisted("scaleInfo") private val scaleInfo: ScaleInfo?,
  override val ggDeviceService: GGDeviceService,
  private val wifiScaleService: WifiScaleService,
  override val permissionService: GGPermissionService,
  override val connectivityObserver: IConnectivityObserver,
  private val dialogUtility: IDialogUtility,
  private val deviceService: IDeviceService
) : ScaleSetupViewmodel<WifiScaleSetupState, WifiScaleSetupIntent>(
  ggDeviceService, connectivityObserver, permissionService,
  reducer = WifiScaleSetupReducer(),
), DefaultLifecycleObserver {
  @AssistedFactory
  interface Factory {
    fun create(
      @Assisted("sku") sku: String,
      @Assisted("wifiSetupType") wifiSetupType: String,
      @Assisted("scaleInfo") scaleInfo: ScaleInfo?
    ): WifiScaleSetupViewModel
  }

  private val TAG = "WifiScaleSetupViewModel"

  // WiFi setup properties
  private var scaleToken: String? = null
  private var wifiStatus: WifiStatus? = null
  private var lastSsid: String? = null
  private var mac: String? = null
  private var connectedSsid: String? = null
  private var connectedBssid: String? = null
  private var wifiSetupType: WifiSetupType = WifiSetupType.FIRST
  private var isDestroyed = false
  private var isWifiSwitchingContext = false // Track if we're in WiFi switching context

  init {
    // Convert wifiSetupTypeString to WifiSetupType enum
    wifiSetupType = when (wifiSetupTypeString) {
      "espTouchWifi" -> WifiSetupType.ESP_TOUCH_WIFI
      "join" -> WifiSetupType.JOIN
      "change" -> WifiSetupType.CHANGE
      else -> WifiSetupType.FIRST
    }
    // Set setup in progress when initialization starts
    deviceService.setSetupInProgress(true)
    loadScaleInfo()
    observePermissions()
    observeStepChanges()
    getNetworkInfo()
    getScaleToken()
    monitorNetworkStatus()
    // Observe selectedWifiMode changes and update canProceedToNext in WIFI_MODE step
    viewModelScope.launch {
      state.collect { currentState ->
        if (currentState.currentStep == WifiScaleSetupStep.WIFI_MODE) {
          val canProceed = isWifiModeSelected()
          handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(canProceed))
        }
      }
    }
  }

  override fun onResume(owner: LifecycleOwner) {
    observeAppResume()
  }

  override fun provideInitialState(): WifiScaleSetupState = WifiScaleSetupState()
  override fun onScanResponse(response: GGScanResponse.DeviceDetail) {
    TODO("Not yet implemented")
  }

  override fun handleIntent(intent: WifiScaleSetupIntent) {
    when (intent) {
      WifiScaleSetupIntent.Next -> onNext()
      WifiScaleSetupIntent.Back -> onBack()
      WifiScaleSetupIntent.Skip -> onSkip()
      is WifiScaleSetupIntent.ExitSetup ->
        onExitSetup(
          intent.isSetupFinished,
          intent.isConnected,
        )

      is WifiScaleSetupIntent.OpenHelp -> openHelpModal()
      is WifiScaleSetupIntent.RequestPermission -> requestPermission(intent.permissionType)
      is WifiScaleSetupIntent.GoToWifiSettings -> goToWifiSettings()
      is WifiScaleSetupIntent.OnGetScaleMacAddress -> onGetScaleMacAddress()
      else -> {}
    }
    super.handleIntent(intent)
  }

  /**
   * Loads scale information based on the provided SKU.
   */
  private fun loadScaleInfo() {
    AppLog.d(TAG, "Loading scale info for SKU: $sku")
    handleIntent(WifiScaleSetupIntent.SetScaleSku(sku))
  }

  private fun observePermissions() {
    viewModelScope.launch {
      subscribePermissions(true).collect { permissions ->
        handleIntent(WifiScaleSetupIntent.SetPermissions(permissions))
        AppPermissionsHelper.areRequiredPermissionsEnabled(permissions, setupType = ScaleSetupType.Wifi)

        // Refresh WiFi information when permissions change to ensure WiFi name is current
        updateNetworkStatus()
      }
    }
  }

  /**
   * Observes step changes and triggers appropriate functions when steps change.
   * Handles the three flows:
   * 1. MAC setup: SCALE_INFO -> PERMISSIONS -> ACTIVATE_SCALE -> WIFI_MODE -> SWITCH_WIFI -> MAC_ADDRESS -> EXIT
   * 2. Permissions skipped: SCALE_INFO -> WIFI_PASSWORD -> SELECT_USER -> ACTIVATE_SCALE -> WIFI_MODE -> SWITCH_WIFI -> SCALE_COUNTS -> STEP_ON -> SETUP_FINISHED
   * 3. Normal flow: SCALE_INFO -> PERMISSIONS -> WIFI_PASSWORD -> SELECT_USER -> ACTIVATE_SCALE -> WIFI_MODE -> SWITCH_WIFI/SCALE_COUNTS -> STEP_ON -> SETUP_FINISHED
   */
  private fun observeStepChanges() {
    viewModelScope.launch {
      var previousStep: WifiScaleSetupStep? = null

      state.collect { currentState ->
        val currentStep = currentState.currentStep

        if (previousStep != null && previousStep != currentStep) {
          AppLog.d(TAG, "Step changed from $previousStep to $currentStep")

          // Clear navigation state after step change
          handleIntent(WifiScaleSetupIntent.ClearNavigationState)

          when (currentStep) {
            WifiScaleSetupStep.SCALE_INFO -> {
              updateNetworkStatus()
            }

            WifiScaleSetupStep.PERMISSIONS -> {
              val areRequiredPermissionsEnabled = AppPermissionsHelper
                .areRequiredPermissionsEnabled(state.value.permissions, setupType = ScaleSetupType.Wifi)
              handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(areRequiredPermissionsEnabled))
              updateNetworkStatus()
            }

            WifiScaleSetupStep.WIFI_PASSWORD -> {
              updateNetworkStatus()
              if (state.value.permissionsSkipped) {
                clearWifiPasswordForm()
              }
              val canProceed = isWifiPasswordFormValid()
              handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(canProceed))
            }

            WifiScaleSetupStep.SELECT_USER -> {
              val canProceed = isUserSelected()
              handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(canProceed))
            }

            WifiScaleSetupStep.ACTIVATE_SCALE -> {
              handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
              if (currentState.isGetMACSetup) {
                handleIntent(WifiScaleSetupIntent.SetNextButtonText("Next"))
              } else {
                handleIntent(WifiScaleSetupIntent.SetNextButtonText("Pair"))
              }
              handleIntent(WifiScaleSetupIntent.SetShowError(false))
              handleIntent(WifiScaleSetupIntent.HandleErrorCodeSelected(""))
            }

            WifiScaleSetupStep.WIFI_MODE -> {
              if (currentState.isGetMACSetup || currentState.permissionsSkipped) {
                // Only AP mode available for MAC setup and permission skipped flows
                val canProceed = currentState.selectedWifiMode == "apmode"
                handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(canProceed))
                handleIntent(WifiScaleSetupIntent.SetNextButtonText("Next"))
              } else {
                // Normal flow - both modes available
                val canProceed = isWifiModeSelected()
                handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(canProceed))
              }
            }

            WifiScaleSetupStep.SWITCH_WIFI -> {
              val canProceed = isConnectedToScaleWifi()
              if (!state.value.permissionsSkipped || state.value.isGetMACSetup) {
                handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(canProceed))
              } else {
                handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
              }
              handleIntent(WifiScaleSetupIntent.SetNextButtonText("Next"))
            }

            WifiScaleSetupStep.MAC_ADDRESS -> {
              if (currentState.isGetMACSetup) {
                if (currentState.selectedWifiMode == "apmode") {
                  viewModelScope.launch {
                    try {
                      val macAddress = getMacAddress()
                      if (macAddress != null) {
                        handleIntent(WifiScaleSetupIntent.SetMacAddress(macAddress))
                      }
                    } catch (e: Exception) {
                      AppLog.e(TAG, "Error getting MAC address", e)
                    }
                  }
                }
                handleIntent(WifiScaleSetupIntent.SetNextButtonText("Close"))
                handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
              } else {
                handleIntent(WifiScaleSetupIntent.SetNextButtonText("Save"))
                handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
              }
            }

            WifiScaleSetupStep.SCALE_COUNTS -> {
              handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
              handleIntent(WifiScaleSetupIntent.SetNextButtonText("Next"))
            }

            WifiScaleSetupStep.STEP_ON -> {
              handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
              handleIntent(WifiScaleSetupIntent.SetNextButtonText("Next"))
            }

            WifiScaleSetupStep.SETUP_FINISHED -> {
              handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
              handleIntent(WifiScaleSetupIntent.SetNextButtonText("Finish"))
            }

            WifiScaleSetupStep.ERROR_CODE_SELECTED -> {
              handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
              handleIntent(WifiScaleSetupIntent.SetNextButtonText("Finish"))
            }

            WifiScaleSetupStep.TROUBLE_SHOOTING -> {
              handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
              handleIntent(WifiScaleSetupIntent.SetNextButtonText("Finish"))
            }

            WifiScaleSetupStep.ERROR_GUIDE -> {
              handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(false))
              handleIntent(WifiScaleSetupIntent.SetNextButtonText("Next"))
            }

            else -> {
              AppLog.d(TAG, "No automatic action for step: $currentStep")
            }
          }
        }

        previousStep = currentStep
      }
    }
  }

  fun goToWifiSettings() {
    try {
      // Use WifiScaleService to open WiFi settings since it has access to context
      wifiScaleService.openWifiSettings()
      AppLog.d(TAG, "WiFi settings opened successfully")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to open WiFi settings", e)
    }
  }

  /**
   * Observes app resume events and triggers network status updates.
   * Equivalent to TypeScript platform.resume subscription.
   */
  private fun observeAppResume() {
    viewModelScope.launch {
      AppLog.d(TAG, "App resumed - updating network status and checking permissions")

      // Update network status immediately on resume
      updateNetworkStatus()
      // Check if we need to show permission alerts
      val currentState = state.value
      val currentStep = currentState.currentStep
      val hasLocationPermission = isAllLocationPermissionGranted()
      if (currentStep != WifiScaleSetupStep.SCALE_INFO &&
        currentStep != WifiScaleSetupStep.PERMISSIONS &&
        !hasLocationPermission && !state.value.permissionsSkipped
      ) {
        // Delay permission alert slightly to avoid immediate display
        delay(300)
        showPermissionRevokedAlert()
      }
    }
  }

  /**
   * Handles skipping the current step.
   * Equivalent to TypeScript skipHandler()
   */
  private fun onSkip() {
    if (!checkScaleToken()) {
      return
    }
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = ScaleSetupStrings.SkipWifiPermissions.Title,
        message = ScaleSetupStrings.SkipWifiPermissions.Message,
        confirmText = ScaleSetupStrings.SkipWifiPermissions.Skip,
        cancelText = ScaleSetupStrings.SkipWifiPermissions.Goback,
        onConfirm = {
          handleIntent(WifiScaleSetupIntent.SetPermissionsSkipped(true))
          clearWifiPasswordForm()
          handleUserConfirmSelected(SetupPath.AP_MODE)
          handleIntent(WifiScaleSetupIntent.Next)
        },
      ),
    )
  }

  private fun onExitSetup(
    isSetupFinished: Boolean,
    isConnected: Boolean,
  ) {
    // Clear setup in progress state when exiting
    deviceService.setSetupInProgress(false)
    if (isSetupFinished) {
      navigateBack()
      return
    }
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = ScaleSetupStrings.ExitSetupAlert.Title,
        message = ScaleSetupStrings.ExitSetupAlert.Message(isConnected),
        confirmText = ScaleSetupStrings.ExitSetupAlert.Exit,
        cancelText = ScaleSetupStrings.ExitSetupAlert.Back,
        onConfirm = {
          navigateBack()
        },
      ),
    )
  }

  /**
   * Opens the Help modal.
   */
  private fun openHelpModal() {
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.HelpPopup,
        params =
          mapOf(
            "showGuide" to true,
            "onGuideClick" to {
              openProductGuide()
            },
          ),
      ),
    )
  }

  private fun openProductGuide() {
    val sku = state.value.sku
    val url = "${AppConfig.PRODUCT_URL}/$sku"
    openInAppBrowser(url)
  }

  /**
   * Requests a specific permission using the PermissionService.
   */
  private fun requestPermission(permissionType: String) {
    if (permissionType == CustomPermissionType.WIFI_SWITCH_LOCATION.value) {
      // Check if location permissions are granted before allowing WiFi switch request
      val hasLocationPermissions = isAllLocationPermissionGranted()
      if (!hasLocationPermissions) {
        AppLog.w(TAG, "Location permissions not granted")
        return
      }
      permissionService.requestPermission(GGPermissionType.WIFI_SWITCH)
      return
    }
    viewModelScope.launch {
      try {
        dialogUtility.permissionAlert(
          permissionType = permissionType,
          onRequest = {
            permissionService.requestPermission(permissionType)
          },
        )
      } catch (e: Exception) {
        AppLog.e(TAG, "Error requesting permission ${permissionType}", e)
      }
    }
  }

  /**
   * Navigates back from the setup screen.
   */
  private fun navigateBack() {
    viewModelScope.launch {
      try {
        navigationService.navigateBack()
        AppLog.d(TAG, "Successfully navigated back from scale setup")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to navigate back from scale setup", e)
      }
    }
  }

  /**
   * Gets the scale token from the API.
   * Equivalent to TypeScript getScaleToken()
   */
  private fun getScaleToken() {
    viewModelScope.launch {
      try {
        val token = wifiScaleService.getScaleToken()
        scaleToken = token
        AppLog.d(TAG, "getScaleToken - token retrieved: $token")
      } catch (e: Exception) {
        AppLog.e(TAG, "getScaleToken - Error getting scale token", e)
      }
    }
  }

  /**
   * Gets network information including WiFi status.
   * Equivalent to TypeScript getNetworkInfo()
   */
  private fun getNetworkInfo() {
    viewModelScope.launch {
      try {
        val status = wifiScaleService.getConnectedWifiInfo()
        wifiStatus = status

        // Update SSID if it changed
        if (status.ssid.isNotEmpty() && status.ssid != lastSsid) {
          lastSsid = status.ssid
          handleIntent(WifiScaleSetupIntent.SetWifiSsid(status.ssid))
          updateFormValuesWithSsid(status.ssid)
        }

        handleIntent(WifiScaleSetupIntent.SetWifiStatus(status))
        AppLog.d(TAG, "getNetworkInfo: $status")
      } catch (e: Exception) {
        AppLog.e(TAG, "getNetworkInfo - Error getting network info", e)
      }
    }
  }

  /**
   * Updates the network status.
   * Equivalent to TypeScript updateNetworkStatus()
   */
  private fun updateNetworkStatus() {
    viewModelScope.launch {
      try {
        val hasLocationPermission = isAllLocationPermissionGranted()
        val status = wifiScaleService.getConnectedWifiInfo(hasLocationPermission)
        wifiStatus = status
          lastSsid = status.ssid
          handleIntent(WifiScaleSetupIntent.SetWifiSsid(status.ssid))
          updateFormValuesWithSsid(status.ssid)
        handleIntent(WifiScaleSetupIntent.SetWifiStatus(status))
      } catch (e: Exception) {
      }
    }
  }

  /**
   * Monitors network status continuously.
   * Equivalent to TypeScript monitorNetworkStatus()
   */
  private fun monitorNetworkStatus() {
    viewModelScope.launch {
      try {
        updateNetworkStatus()
      } catch (e: Exception) {
        AppLog.e(TAG, "monitorNetworkStatus - Error monitoring network", e)
      }
    }
  }

  /**
   * Gets the MAC address of the connected WiFi network.
   * Equivalent to TypeScript getMacAddress()
   */
  suspend fun getMacAddress(): String? {
    return try {
      val ssid = wifiScaleService.getConnectedSsid()
      val scanResults = wifiScaleService.getScanResults()

      for (network in scanResults) {
        if (network.SSID == ssid) {
          var mac = network.BSSID
          val hexes = mac.split(":")
          val formattedHexes = hexes.map { hex ->
            if (hex.length == 1) "0$hex" else hex
          }
          mac = formattedHexes.joinToString(":")
          Log.d("macaddress", mac)
          this.mac = mac
          AppLog.d(TAG, "getMacAddress - MAC address found: $mac")
          return mac
        }
      }

      AppLog.w(TAG, "getMacAddress - No matching network found")
      null
    } catch (e: Exception) {
      AppLog.e(TAG, "getMacAddress - Error getting MAC address", e)
      null
    }
  }

  /**
   * Gets the setup information based on the setup type.
   * Equivalent to TypeScript getSetupInfo()
   */
  private fun getSetupInfo(setupType: WifiSetupType): WifiSetupInfo {
    val currentState = state.value
    val hasPassword = !currentState.wifiPasswordForm.noPasswordNetwork.value
    val currentUserNumber = currentState.selectedUser

    return when (setupType) {
      WifiSetupType.JOIN -> {
        WifiSetupInfo(
          userNumber = currentUserNumber,
          token = scaleToken,
        )
      }

      WifiSetupType.CHANGE -> {
        WifiSetupInfo(
          ssid = currentState.wifiPasswordForm.ssid.value,
          password = if (hasPassword) currentState.wifiPasswordForm.password.value else "",
          userNumber = currentUserNumber,
          token = scaleToken,
        )
      }

      else -> {
        WifiSetupInfo(
          ssid = currentState.wifiPasswordForm.ssid.value,
          bssid = currentState.wifiStatus?.bssid ?: "",
          password = if (hasPassword) currentState.wifiPasswordForm.password.value else "",
          userNumber = currentUserNumber,
          token = scaleToken,
        )
      }
    }
  }

  /**
   * Starts smart connect process.
   * Equivalent to TypeScript startSmartConnect()
   */
  private fun startSmartConnect() {
    viewModelScope.launch {
      try {
        // If permission is skipped stop the scale to pair through the normal setup
        if (state.value.permissionsSkipped) {
          return@launch
        }

        // Determine the correct setup type based on scaleInfo
        val setupType = when (scaleInfo?.setupType) {
          ScaleSetupType.EspTouchWifi -> WifiSetupType.ESP_TOUCH_WIFI
          ScaleSetupType.Wifi -> WifiSetupType.FIRST
          else -> WifiSetupType.FIRST // Default fallback
        }

        val info = getSetupInfo(setupType)
        wifiScaleService.stop()
        connectedSsid = info.ssid
        connectedBssid = info.bssid

        wifiScaleService.connect(
          setupInfo = info,
          setupType = setupType,
          onSuccess = {
            AppLog.d(TAG, "Connection successful")
            handleIntent(WifiScaleSetupIntent.SetConnectionSuccess(true))
          },
          onError = { error ->
            AppLog.e(TAG, "Connection failed: $error")
            handleIntent(WifiScaleSetupIntent.SetConnectionError(error))
          },
        )
      } catch (e: Exception) {
        AppLog.e(TAG, "startSmartConnect - Error starting connect", e)
        handleIntent(WifiScaleSetupIntent.SetConnectionError(e.message ?: "Unknown error"))
      }
    }
  }

  /**
   * Starts AP mode process.
   * Equivalent to TypeScript startApMode()
   */
  private fun startApMode(count: Int = 0) {
    viewModelScope.launch {
      try {
        val info = getSetupInfo(WifiSetupType.CHANGE)
        wifiScaleService.stop()
        info.ssid = connectedSsid ?: info.ssid
        info.bssid = connectedBssid ?: info.bssid
        wifiScaleService.connect(
          setupInfo = info,
          setupType = WifiSetupType.CHANGE,
          onSuccess = {
            AppLog.d(TAG, "AP Mode connection successful")
            handleIntent(WifiScaleSetupIntent.SetConnectionSuccess(true))
          },
          onError = { error ->
            AppLog.e(TAG, "AP Mode connection failed: $error")
            handleIntent(WifiScaleSetupIntent.SetConnectionError(error))
          },
        )
      } catch (e: Exception) {
        AppLog.e(TAG, "startApMode - Error starting AP mode", e)

        // Retry logic similar to TypeScript
        if (count < 5) {
          delay(5000) // 5 seconds delay
          startApMode(count + 1)
        } else {
          handleIntent(WifiScaleSetupIntent.SetConnectionError("AP Mode failed after 5 attempts"))
        }
      }
    }
  }

  /**
   * Checks if scale token is available.
   */
  fun checkScaleToken(): Boolean {
    if (scaleToken.isNullOrEmpty()) {
      AppLog.w(TAG, "checkScaleToken - No scale token available")
      return false
    }
    return true
  }

  /**
   * Updates form values when SSID changes.
   * Helper method to keep form values in sync with network status.
   *
   * Logic:
   * - If on early steps (index < 3), only fill WiFi password form
   * - If in WiFi switching context (after SWITCH_WIFI step), only fill scale network form
   * - Otherwise, fill both forms as before
   */
  private fun updateFormValuesWithSsid(ssid: String) {
    val currentState = state.value
    val currentStep = currentState.currentStep
    val currentStepIndex = currentState.currentStepIndex
    val shouldAutoPopulate = !currentState.permissionsSkipped || currentState.isGetMACSetup
    if (shouldAutoPopulate) {
      // Check if we're on early steps (index < 3) - equivalent to TypeScript condition
      val isEarlyStep = currentStepIndex < 3
      AppLog.d(
        TAG,
        "updateFormValuesWithSsid - SSID: $ssid, currentStep: $currentStep, currentStepIndex: $currentStepIndex, isEarlyStep: $isEarlyStep, isWifiSwitchingContext: $isWifiSwitchingContext",
      )
      if (isEarlyStep) {
        // On early steps (index < 3), only fill WiFi password form
        // Update WiFi password form SSID if on relevant steps
        if (currentStep == WifiScaleSetupStep.WIFI_PASSWORD || currentStep == WifiScaleSetupStep.SCALE_INFO) {
          handleIntent(WifiScaleSetupIntent.SetWifiPasswordFormSsid(ssid))
          AppLog.d(TAG, "Early step detected - filled WiFi password form with SSID: $ssid")
        }
      } else if (currentStep == WifiScaleSetupStep.SWITCH_WIFI) {
        handleIntent(WifiScaleSetupIntent.SetScaleNetworkFormSsid(ssid))
        AppLog.d(TAG, "WiFi switching context detected - filled scale network form with SSID: $ssid")
      } else {
        if (currentStep == WifiScaleSetupStep.WIFI_PASSWORD || currentStep == WifiScaleSetupStep.SCALE_INFO) {
          handleIntent(WifiScaleSetupIntent.SetWifiPasswordFormSsid(ssid))
        }
      }
    } else {
      AppLog.d(TAG, "Skipping auto-population of WiFi form - permissions were skipped and not in MAC setup mode")
    }
  }

  /**
   * Clears the WiFi password form when permissions are skipped.
   * Resets form controls to initial state to avoid validation errors.
   */
  private fun clearWifiPasswordForm() {
    // Create fresh form controls with empty initial values
    val emptySsid = FormControl.create(
      initialValue = "",
      validators = listOf(FormValidations.required()),
    )
    val emptyPassword = FormControl.create(
      initialValue = "",
      validators = listOf(
        FormValidations.required(),
        FormValidations.minLength(6, LoginStrings.PasswordLabel),
        FormValidations.maxLength(50, LoginStrings.PasswordLabel),
      ),
    )
    val noPasswordControl = FormControl.create(
      initialValue = false,
      validators = emptyList(),
    )

    // Update the form with fresh controls using the correct type
    handleIntent(
      WifiScaleSetupIntent.SetWifiPasswordForm(
        WifiScalePasswordFormControls(
          ssid = emptySsid,
          password = emptyPassword,
          noPasswordNetwork = noPasswordControl,
        ),
      ),
    )

    AppLog.d(TAG, "Cleared WiFi password form - reset all form controls to initial state")
  }


  /**
   * Checks if all location permissions are granted.
   */
  private fun isAllLocationPermissionGranted(): Boolean {
    return try {
      val permissions = permissionService.permissionCallBackFlow.value
      val locationSwitchEnabled = permissions[GGPermissionType.LOCATION_SWITCH] == GGPermissionState.ENABLED
      val locationEnabled = permissions[GGPermissionType.LOCATION] == GGPermissionState.ENABLED

      locationSwitchEnabled && locationEnabled
    } catch (e: Exception) {
      AppLog.e(TAG, "Error checking location permissions", e.toString())
      false
    }
  }

  /**
   * Handles user confirmation selection.
   * Equivalent to TypeScript handleUserConfirmSelected()
   */
  fun handleUserConfirmSelected(result: SetupPath) {
    AppLog.d(TAG, "handleUserConfirmSelected called with result: $result")
    handleIntent(WifiScaleSetupIntent.HandleUserConfirmSelected(result))
  }

  /**
   * Simplified next() method - special navigation logic is now in the reducer
   */
  private fun onNext() {
    val currentState = state.value

    // Prevent double-clicks during navigation
    if (currentState.isNavigating || currentState.isLoading) {
      AppLog.d(TAG, "Ignoring next click - navigation in progress")
      return
    }


    AppLog.d(TAG, "Moving to next step from: ${currentState.currentStep}")

    // Handle actions that need to happen before/during navigation
    when (currentState.currentStep) {
      WifiScaleSetupStep.SCALE_INFO -> {
        state.value.copy(
          isGetMACSetup = false,
          shouldGetMacAddress = false
        )
      }
      WifiScaleSetupStep.PERMISSIONS -> {
        if (!checkScaleToken()) {
          return
        }
      }

      WifiScaleSetupStep.ACTIVATE_SCALE -> {
        handleIntent(WifiScaleSetupIntent.SetShowApMode(false))
      }

      WifiScaleSetupStep.WIFI_MODE -> {
        when (currentState.selectedWifiMode) {
          "apmode" -> {
            handleIntent(WifiScaleSetupIntent.SetShowApMode(true))
          }
        }
      }

      WifiScaleSetupStep.SWITCH_WIFI -> {
        if (!currentState.isGetMACSetup) {
          // For normal setup, start AP mode
          startApMode()
        }
      }

      WifiScaleSetupStep.SCALE_COUNTS -> {
        saveScale()
        notificationPermission()
      }

      WifiScaleSetupStep.MAC_ADDRESS -> {
        if (currentState.isGetMACSetup) {
          // End MAC setup flow
          startExitSetup(true)
          return
        }
      }

      WifiScaleSetupStep.ERROR_CODE_SELECTED -> {
        // Clear error state and exit
        handleIntent(WifiScaleSetupIntent.SetShowError(false))
        handleIntent(WifiScaleSetupIntent.HandleErrorCodeSelected(""))
        startExitSetup(true)
        return
      }

      WifiScaleSetupStep.TROUBLE_SHOOTING -> {
        // From troubleshooting, user wants to exit
        startExitSetup(true)
        return
      }

      else -> {
        // Handle button text actions for backward compatibility
        when (currentState.nextButtonText) {
          "Pair" -> {
            startSmartConnect()
            return // Don't proceed to next step yet
          }

          "Finish", "close", "exit", "Close" -> {
            startExitSetup(true)
            return
          }
        }
      }
    }
  }

  /**
   * Simplified back() method - special navigation logic is now in the reducer
   */
  private fun onBack() {
    val currentState = state.value

    // Prevent double-clicks during navigation
    if (currentState.isNavigating || currentState.isLoading) {
      AppLog.d(TAG, "Ignoring back click - navigation in progress")
      return
    }
   if(state.value.currentStep == WifiScaleSetupStep.PERMISSIONS && state.value.isGetMACSetup){
      state.value.copy(
        isGetMACSetup = false,
        shouldGetMacAddress = false
      )
   }
    AppLog.d(TAG, "Moving back from step: ${currentState.currentStep}")
  }

  private fun checkAndSaveScale() {
    dialogQueueService.showLoader(
      message = ScaleSetupStrings.SaveScaleLoader,
    )
    try {
      viewModelScope.launch {
        val alreadyPairedScale =
          deviceService.pairedScales.first().find { it.sku == sku && it.userNumber == state.value.selectedUser }
        if (alreadyPairedScale != null) {
          deviceService.deleteScale(alreadyPairedScale.id)
        }
        val scaleInfo = SCALES.find { it.sku == state.value.sku }
        val wifiDevice = Device(
          device = GGDeviceDetail(
            deviceName = scaleInfo?.productName ?: "",
            macAddress = state.value.macAddress,
            identifier = "",
          ),
          sku = state.value.sku,
          deviceType = ScaleSetupType.Wifi.value,
          nickname = scaleInfo?.productName!!,
          token = scaleToken,
          userNumber = state.value.selectedUser,
        )
        deviceService.saveScale(wifiDevice)
      }
    } finally {
      dialogQueueService.dismissLoader()
    }
  }

  /**
   * Saves the scale configuration.
   * Equivalent to TypeScript saveScale()
   */
  private fun saveScale() {
    viewModelScope.launch {
      try {
        checkAndSaveScale()
      } catch (e: Exception) {
        AppLog.e(TAG, "Error saving scale", e)
      }
    }
  }

  /**
   * Starts exit setup process.
   * Equivalent to TypeScript startExitSetup()
   */
  private fun startExitSetup(canExit: Boolean = false) {
    val currentState = state.value
    try {
      wifiScaleService.stop()
    } catch (e: Exception) {
      AppLog.e(TAG, "Error stopping WiFi service", e)
    }
    if (currentState.saved || canExit) {
      navigateBack()
      return
    }


    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = ScaleSetupStrings.ExitSetupAlert.Title,
        message = ScaleSetupStrings.ExitSetupAlert.Message(currentState.isConnected),
        confirmText = ScaleSetupStrings.ExitSetupAlert.Exit,
        cancelText = ScaleSetupStrings.ExitSetupAlert.Back,
        onConfirm = {
          navigateBack()
        },
      ),
    )
  }

  /**
   * Validates the WiFi password form to determine if user can proceed to next step.
   * @return true if the form is valid and user can proceed, false otherwise
   */
  private fun isWifiPasswordFormValid(): Boolean {
    val currentState = state.value
    val form = currentState.wifiPasswordForm

    // Check if SSID is selected/entered
    val isSsidValid = form.ssid.value.isNotEmpty()

    // Check if password is valid (either entered or "no password" is selected)
    val isPasswordValid = if (form.noPasswordNetwork.value) {
      // If "no password" is selected, password field is not required
      true
    } else {
      // If "no password" is not selected, password must be entered and valid
      form.password.value.isNotEmpty() && form.password.isValueValid()
    }

    // Check if SSID form control is valid
    val isSsidFormValid = form.ssid.isValueValid()

    AppLog.d(
      TAG,
      "WiFi password form validation - SSID: $isSsidValid, Password: $isPasswordValid, SSID Form: $isSsidFormValid",
    )

    return isSsidValid && isPasswordValid && isSsidFormValid
  }

  /**
   * Validates if a user has been selected for the scale setup.
   * @return true if a user is selected, false otherwise
   */
  private fun isUserSelected(): Boolean {
    val currentState = state.value
    val isSelected = currentState.selectedUser != null
    AppLog.d(TAG, "User selection validation - selected user: ${currentState.selectedUser}, is valid: $isSelected")
    return isSelected
  }

  /**
   * Validates if a WiFi mode has been selected for the scale setup.
   * @return true if a WiFi mode is selected, false otherwise
   */
  private fun isWifiModeSelected(): Boolean {
    val currentState = state.value
    val isSelected = !currentState.selectedWifiMode.isNullOrEmpty()
    AppLog.d(
      TAG,
      "WiFi mode selection validation - selected mode: ${currentState.selectedWifiMode}, is valid: $isSelected",
    )
    return isSelected
  }

  /**
   * Checks if the user is connected to the scale's WiFi network in AP mode.
   * @return true if connected to scale's WiFi, false otherwise
   */
  private fun isConnectedToScaleWifi(): Boolean {
    val currentState = state.value
    val isConnected = currentState.scaleNetworkForm.ssid.value.isNotEmpty()
    AppLog.d(TAG, "Scale WiFi connection check - is connected: $isConnected")
    return isConnected
  }

  /**
   * Updates the connection status to scale's WiFi.
   * This should be called when the WiFi connection status changes.
   */
  fun updateScaleWifiConnectionStatus(isConnected: Boolean) {
    AppLog.d(TAG, "Updating scale WiFi connection status: $isConnected")
    handleIntent(WifiScaleSetupIntent.SetConnectedToScaleWifi(isConnected))
  }


  /**
   * Shows permission revoked alert.
   * Equivalent to TypeScript showPermissionRevokedAlert()
   */
  private fun showPermissionRevokedAlert() {
    viewModelScope.launch {
      try {
        val currentState = state.value
        val permissions = currentState.permissions

        // Check location switch permission
        val isLocationSwitchEnabled = permissions[GGPermissionType.LOCATION_SWITCH] == GGPermissionState.ENABLED
        val isLocationAuthorized = permissions[GGPermissionType.LOCATION] == GGPermissionState.ENABLED

        AppLog.d(
          TAG,
          "showPermissionRevokedAlert - Location switch: $isLocationSwitchEnabled, Location: $isLocationAuthorized",
        )

        if (!isLocationSwitchEnabled) {
          // Show location disabled error
          dialogQueueService.enqueue(
            DialogModel.Alert(
              title = ScaleSetupStrings.PermissionAlerts.LocationDisabled.Title,
              message = ScaleSetupStrings.PermissionAlerts.LocationDisabled.Message,
              dismissText = ScaleSetupStrings.PermissionAlerts.LocationDisabled.Enable,
              onDismiss = {
                // Open location settings
                try {
                  wifiScaleService.openWifiSettings()
                } catch (e: Exception) {
                  AppLog.e(TAG, "Failed to open location settings", e)
                }
              },
            ),
          )
        } else if (!isLocationAuthorized) {
          // Show location access disabled error
          dialogQueueService.enqueue(
            DialogModel.Alert(
              title = ScaleSetupStrings.PermissionAlerts.LocationAccessDisabled.Title,
              message = ScaleSetupStrings.PermissionAlerts.LocationAccessDisabled.Message,
              dismissText = ScaleSetupStrings.PermissionAlerts.LocationAccessDisabled.Enable,
              onDismiss = {
                requestPermission(GGPermissionType.LOCATION)
              },
            ),
          )
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error showing permission revoked alert", e)
      }
    }
  }

  private fun notificationPermission() {
    val canRequestNotifPermission =
      AppPermissionsHelper.canRequestNotificationPermission(state.value.permissions)
    if (canRequestNotifPermission) {
      requestPermission(GGPermissionType.NOTIFICATION)
    }
  }

  /**
   * Handles the "Get Scale MAC Address" button click
   */
  private fun onGetScaleMacAddress() {
    AppLog.d(TAG, "MAC address setup requested")
    handleIntent(WifiScaleSetupIntent.SetShouldGetMacAddress(true))
    // The intent is already handled by the reducer to set MAC setup flags
  }

  /**
   * Cleanup method called when ViewModel is destroyed.
   */
  override fun onCleared() {
    super.onCleared()
    isDestroyed = true
    isWifiSwitchingContext = false // Reset WiFi switching context flag
    wifiScaleService.stop()
    deviceService.setSetupInProgress(false)
  }
}

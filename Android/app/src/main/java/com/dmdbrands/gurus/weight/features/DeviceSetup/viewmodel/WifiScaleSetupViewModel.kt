package com.dmdbrands.gurus.weight.features.DeviceSetup.viewmodel

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.service.WifiDeviceService
import com.dmdbrands.gurus.weight.core.service.WifiSetupType
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.WifiModes
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.WifiScaleSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.WifiConnectManager
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.WifiNetworkManager
import com.dmdbrands.gurus.weight.features.DeviceSetup.manager.WifiPermissionsManager
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.SetupPath
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.WifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.WifiScaleSetupReducer
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.WifiScaleSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.DeviceSetup.helper.switchActiveProductAfterSetup
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Coordinator ViewModel for the WifiScaleSetupScreen.
 *
 * Owns step navigation and delegates each cohesive slice to a focused collaborator:
 * network status / SSID form / validation ([WifiNetworkManager]), scale-token / connect / save /
 * exit ([WifiConnectManager]) and permissions / alerts ([WifiPermissionsManager]). Split from a
 * single large ViewModel in MOB-1501 to clear detekt's LargeClass limit; behaviour-preserving.
 */
@HiltViewModel(
  assistedFactory = WifiScaleSetupViewModel.Factory::class,
)
class WifiScaleSetupViewModel
@AssistedInject
constructor(
  @Assisted("sku") private val sku: String,
  @Assisted("wifiSetupType") private val wifiSetupTypeString: String,
  @Assisted("scaleInfo") private val scaleInfo: DeviceModelInfo?,
  override val ggDeviceService: GGDeviceService,
  private val wifiScaleService: WifiDeviceService,
  override val permissionService: GGPermissionService,
  override val connectivityObserver: IConnectivityObserver,
  private val dialogUtility: IDialogUtility,
  private val deviceService: IDeviceService
) : DeviceSetupViewmodel<WifiScaleSetupState, WifiScaleSetupIntent>(
  ggDeviceService, connectivityObserver, permissionService,
  reducer = WifiScaleSetupReducer(),
), DefaultLifecycleObserver {
  @AssistedFactory
  interface Factory {
    fun create(
      @Assisted("sku") sku: String,
      @Assisted("wifiSetupType") wifiSetupType: String,
      @Assisted("scaleInfo") scaleInfo: DeviceModelInfo?
    ): WifiScaleSetupViewModel
  }

  private val TAG = "WifiScaleSetupViewModel"

  private var wifiSetupType: WifiSetupType = WifiSetupType.FIRST
  private var isWifiSwitchingContext = false // Track if we're in WiFi switching context

  private val networkManager: WifiNetworkManager = WifiNetworkManager(
    wifiScaleService = wifiScaleService,
    permissionService = permissionService,
    scope = viewModelScope,
    getState = { state.value },
    onIntent = ::handleIntent,
    getScaleToken = { connectManager.scaleToken },
    onRefreshScaleToken = { connectManager.getScaleToken() },
  )

  private val connectManager: WifiConnectManager = WifiConnectManager(
    wifiScaleService = wifiScaleService,
    deviceService = deviceService,
    scope = viewModelScope,
    scaleInfo = scaleInfo,
    getState = { state.value },
    onIntent = ::handleIntent,
    enqueueDialog = { dialogQueueService.enqueue(it) },
    showLoader = { dialogQueueService.showLoader(message = it) },
    dismissLoader = { dialogQueueService.dismissLoader() },
    showToast = { dialogQueueService.showToast(it) },
    navigateBackNav = { navigationService.navigateBack() },
    switchActiveProductAfterSetup = {
      // Auto-switch the dashboard header to the newly added scale (MOB-422).
      productSelectionManager.switchActiveProductAfterSetup(ProductSelection.MyWeight)
    },
    onRequestNotificationPermission = { permissionsManager.requestPermission(GGPermissionType.NOTIFICATION) },
  )

  private val permissionsManager: WifiPermissionsManager = WifiPermissionsManager(
    permissionService = permissionService,
    dialogUtility = dialogUtility,
    wifiScaleService = wifiScaleService,
    scope = viewModelScope,
    getState = { state.value },
    onIntent = ::handleIntent,
    subscribePermissions = { subscribePermissions(true) },
    onUpdateNetworkStatus = { networkManager.updateNetworkStatus() },
    isAllLocationPermissionGranted = { networkManager.isAllLocationPermissionGranted() },
    enqueueDialog = { dialogQueueService.enqueue(it) },
  )

  init {
    // Convert wifiSetupTypeString to WifiSetupType enum
    wifiSetupType = when (wifiSetupTypeString) {
      WifiModes.ESP_TOUCH_WIFI.value -> WifiSetupType.ESP_TOUCH_WIFI
     WifiModes.JOIN.value -> WifiSetupType.JOIN
      WifiModes.CHANGE.value -> WifiSetupType.CHANGE
      else -> WifiSetupType.FIRST
    }
    // Set setup in progress when initialization starts
    deviceService.setSetupInProgress(true)
    loadScaleInfo()
    permissionsManager.observePermissions()
    observeStepChanges()
    networkManager.getNetworkInfo()
    connectManager.getScaleToken()
    // Start monitoring network status
    networkManager.monitorNetworkStatus()
    // Observe selectedWifiMode changes and update canProceedToNext in WIFI_MODE step
    viewModelScope.launch {
      state
        .map { it.currentStep to it.selectedWifiMode }
        .distinctUntilChanged()
        .collect { (step, _) ->
          if (step == WifiScaleSetupStep.WIFI_MODE) {
            val canProceed = networkManager.isWifiModeSelected()
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
    //No need to implement them
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
      is WifiScaleSetupIntent.RequestPermission -> permissionsManager.requestPermission(intent.permissionType)
      is WifiScaleSetupIntent.GoToWifiSettings -> goToWifiSettings()
      is WifiScaleSetupIntent.OnGetScaleMacAddress -> permissionsManager.onGetScaleMacAddress()
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

  /**
   * Observes step changes and triggers appropriate functions when steps change.
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

          handleStepChange(currentStep, currentState)
        }

        previousStep = currentStep
      }
    }
  }

  private fun handleStepChange(currentStep: WifiScaleSetupStep, currentState: WifiScaleSetupState) {
    when (currentStep) {
      WifiScaleSetupStep.SCALE_INFO -> {
        networkManager.updateNetworkStatus()
      }

      WifiScaleSetupStep.PERMISSIONS -> permissionsManager.handlePermissionsStep()

      WifiScaleSetupStep.WIFI_PASSWORD -> {
        networkManager.updateNetworkStatus()
        val canProceed = networkManager.isWifiPasswordFormValid()
        handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(canProceed))
      }

      WifiScaleSetupStep.SELECT_USER -> {
        val canProceed = networkManager.isUserSelected()
        handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(canProceed))
      }

      WifiScaleSetupStep.ACTIVATE_SCALE -> {
        handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
        handleIntent(WifiScaleSetupIntent.SetShowError(false))
        handleIntent(WifiScaleSetupIntent.HandleErrorCodeSelected(""))
      }

      WifiScaleSetupStep.WIFI_MODE -> handleWifiModeStep(currentState)

      WifiScaleSetupStep.SWITCH_WIFI -> handleSwitchWifiStep()

      WifiScaleSetupStep.MAC_ADDRESS -> handleMacAddressStep(currentState)

      WifiScaleSetupStep.STEP_ON, WifiScaleSetupStep.SCALE_COUNTS -> {
        handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
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
      }

      else -> {
        AppLog.d(TAG, "No automatic action for step: $currentStep")
      }
    }
  }

  private fun handleWifiModeStep(currentState: WifiScaleSetupState) {
    if (currentState.isGetMACSetup || currentState.permissionsSkipped) {
      // Only AP mode available for MAC setup and permission skipped flows
      val canProceed = currentState.selectedWifiMode == WifiModes.AP_MODE.value
      handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(canProceed))
    } else {
      // Normal flow - both modes available
      val canProceed = networkManager.isWifiModeSelected()
      handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(canProceed))
    }
  }

  private fun handleSwitchWifiStep() {
    val canProceed = networkManager.isConnectedToScaleWifi()
    if (!state.value.permissionsSkipped || state.value.isGetMACSetup) {
      handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(canProceed))
    } else {
      handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
    }
  }

  private fun handleMacAddressStep(currentState: WifiScaleSetupState) {
    if (currentState.isGetMACSetup) {
      if (currentState.selectedWifiMode == WifiModes.AP_MODE.value) {
        viewModelScope.launch {
          try {
            val macAddress = networkManager.getMacAddress()
            if (macAddress != null) {
              handleIntent(WifiScaleSetupIntent.SetMacAddress(macAddress))
            }
          } catch (e: Exception) {
            AppLog.e(TAG, "Error getting MAC address", e)
          }
        }
      }
      handleIntent(WifiScaleSetupIntent.SetNextButtonText("Finish"))
      handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
    } else {
      handleIntent(WifiScaleSetupIntent.SetNextButtonText("Save"))
      handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
    }
  }

  fun goToWifiSettings() {
    try {
      // Use WifiDeviceService to open WiFi settings since it has access to context
      wifiScaleService.openWifiSettings()
      AppLog.d(TAG, "WiFi settings opened successfully")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to open WiFi settings", e)
    }
  }

  /**
   * Observes app resume events and triggers network status updates.
   */
  private fun observeAppResume() {
    viewModelScope.launch {
      AppLog.d(TAG, "App resumed - updating network status and checking permissions")

      // Update network status immediately on resume
      networkManager.updateNetworkStatus()
      // Check if we need to show permission alerts
      val currentState = state.value
      val currentStep = currentState.currentStep
      val hasLocationPermission = networkManager.isAllLocationPermissionGranted()
      if (currentStep != WifiScaleSetupStep.SCALE_INFO &&
        currentStep != WifiScaleSetupStep.PERMISSIONS &&
        !hasLocationPermission && !state.value.permissionsSkipped
      ) {
        // Delay permission alert slightly to avoid immediate display
        delay(300)
        permissionsManager.showPermissionRevokedAlert()
      }
    }
  }

  /**
   * Handles skipping the current step.
   */
  private fun onSkip() {
    if (!checkScaleToken()) {
      return
    }
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = DeviceSetupStrings.SkipWifiPermissions.Title,
        message = DeviceSetupStrings.SkipWifiPermissions.Message,
        confirmText = DeviceSetupStrings.SkipWifiPermissions.Skip,
        cancelText = DeviceSetupStrings.SkipWifiPermissions.Goback,
        onConfirm = {
          handleIntent(WifiScaleSetupIntent.SetPermissionsSkipped(true))
          if(state.value.wifiPasswordForm.ssid.value.isEmpty()){
            networkManager.clearWifiPasswordForm()
          }
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
      connectManager.navigateBack(waitForScaleInList = true)
      return
    }
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = DeviceSetupStrings.ExitSetupAlert.Title,
        message = DeviceSetupStrings.ExitSetupAlert.Message(isConnected),
        confirmText = DeviceSetupStrings.ExitSetupAlert.Exit,
        cancelText = DeviceSetupStrings.ExitSetupAlert.GoBack,
        onConfirm = {
          connectManager.navigateBack()
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
   * Handles user confirmation selection.
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


    AppLog.d(TAG, "Moving to next step from: ${currentState.currentStep}")

    // Handle actions that need to happen before/during navigation
    when (currentState.currentStep) {

      WifiScaleSetupStep.PERMISSIONS -> {
        if (!checkScaleToken()) {
          return
        }
      }

      WifiScaleSetupStep.ACTIVATE_SCALE -> {
        handleIntent(WifiScaleSetupIntent.SetShowApMode(false))
        connectManager.startSmartConnect()
        return
      }

      WifiScaleSetupStep.WIFI_MODE -> {
        if(state.value.permissionsSkipped || state.value.isGetMACSetup){
          handleIntent(WifiScaleSetupIntent.SelectWifiMode(wifiMode = WifiModes.AP_MODE.value))
        }
        if(state.value.selectedWifiMode == WifiModes.AP_MODE.value){
          handleIntent(WifiScaleSetupIntent.SetShowApMode(true))
        }
        connectManager.startSmartConnect()
        return
      }

      WifiScaleSetupStep.SWITCH_WIFI -> {
        if (!currentState.isGetMACSetup) {
          // For normal setup, start AP mode
          connectManager.startApMode()
        }
      }

      WifiScaleSetupStep.STEP_ON -> {
        connectManager.saveScale()
        connectManager.notificationPermission()
      }

      WifiScaleSetupStep.MAC_ADDRESS -> {
        if (currentState.isGetMACSetup) {
          // End MAC setup flow
          connectManager.startExitSetup(true)
          return
        }
      }

      WifiScaleSetupStep.ERROR_CODE_SELECTED -> {
        // Clear error state and exit
        handleIntent(WifiScaleSetupIntent.SetShowError(false))
        connectManager.startExitSetup(true)
        return
      }

      WifiScaleSetupStep.TROUBLE_SHOOTING -> {
        // From troubleshooting, user wants to exit
        connectManager.startExitSetup(true)
        return
      }

      else -> {
        // Handle button text actions for backward compatibility
        when (currentState.nextButtonText) {
          "Finish", "close", "exit", "Close" -> {
            connectManager.startExitSetup(true)
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
    if (state.value.currentStep == WifiScaleSetupStep.WIFI_PASSWORD) {
      val areRequiredPermissionsEnabled = AppPermissionsHelper
        .areRequiredPermissionsEnabled(state.value.permissions, setupType = DeviceSetupType.Wifi)
      if (areRequiredPermissionsEnabled) {
        handleIntent(WifiScaleSetupIntent.SetCurrentStep(WifiScaleSetupStep.SCALE_INFO))
      }
      handleIntent(WifiScaleSetupIntent.SetPermissionsSkipped(false))
      // Reset auto-populated flag so when the user returns, the SSID detection runs fresh
      // via updateNetworkStatus() rather than showing a stale auto-populated chip.
      handleIntent(WifiScaleSetupIntent.SetWifiAutoPopulated(false))
      networkManager.clearWifiPasswordFormSsid()
    }

    AppLog.d(TAG, "Moving back from step: ${currentState.currentStep}")
  }

  /**
   * Checks if scale token is available. Retained on the ViewModel because
   * [WifiScaleSetupViewModelTest] calls it directly; delegates to [connectManager].
   */
  fun checkScaleToken(): Boolean = connectManager.checkScaleToken()

  /**
   * Cleanup method called when ViewModel is destroyed.
   */
  override fun onCleared() {
    super.onCleared()
    networkManager.stopMonitoring()
    isWifiSwitchingContext = false // Reset WiFi switching context flag
    wifiScaleService.stop()
    deviceService.setSetupInProgress(false)
  }
}

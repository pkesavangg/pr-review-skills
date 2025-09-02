package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.ScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BaseState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.SetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGEntry
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Singleton
class BLESetupDependencies @Inject constructor(
  val ggDeviceService: GGDeviceService,
  val connectivityObserver: IConnectivityObserver,
  val deviceService: IDeviceService,
  val permissionService: GGPermissionService,
  val dialogUtility: IDialogUtility,
)

abstract class BLESetupViewmodel<Step : ScaleSetupStep, State : BaseState<Step, State>>(
  val protocolType: String,
  val setupInitData: SetupInitData<Step>,
  val reducer: IReducer<State, ScaleSetupIntent>,
  protected val dependencies: BLESetupDependencies,
) : BaseIntentViewModel<State, ScaleSetupIntent>(reducer) {

  private val initialStep = setupInitData.initialStep
  private val sku = setupInitData.sku
  private val broadcastId = setupInitData.broadcastId

  protected val ggDeviceService get() = dependencies.ggDeviceService
  protected val connectivityObserver get() = dependencies.connectivityObserver
  protected val deviceService get() = dependencies.deviceService
  protected val permissionService get() = dependencies.permissionService
  protected val dialogUtility get() = dependencies.dialogUtility

  private var isInitialized = false

  protected fun lazyInit() {
    if (!isInitialized) {
      AppLog.d(TAG, "Initializing BLESetupViewmodel for SKU: $sku, protocol: $protocolType")
      isInitialized = true
      onInit()
    }
  }

  private fun onInit() {
    AppLog.d(TAG, "Starting BLESetupViewmodel initialization")
    loadScaleInfo()
    observeStepChanges()
    observePermissions()
  }

  // Abstract methods for concrete implementations
  abstract override fun provideInitialState(): State
  abstract fun observePermissions()
  abstract fun onStepChange(step: ScaleSetupStep)
  abstract fun onTryAgain()
  abstract fun onNext()
  abstract fun onBack()
  abstract fun onSkip()
  abstract suspend fun onSetupFinished()

  private var deviceObservationJob: Job? = null
  protected val bluetoothTimeout: Long = 5 * 60 * 1000L
  protected var bluetoothTimeoutJob: Job? = null

  private var entryObservationJob: Job? = null

  private var permissions: GGPermissionStatusMap? = null

  var isPermissionGranted = false

  var currentSetupState: SetupState<Step> = SetupState(
    provideInitialState().step,
  )

  override fun handleIntent(intent: ScaleSetupIntent) {
    AppLog.d(TAG, "Handling BLE setup intent: ${intent::class.simpleName}")
    super.handleIntent(intent)
    when (intent) {
      ScaleSetupIntent.Next -> {
        AppLog.d(TAG, "Next intent received")
        onNext()
      }
      ScaleSetupIntent.Back -> {
        AppLog.d(TAG, "Back intent received")
        onBack()
      }
      ScaleSetupIntent.Skip -> {
        AppLog.d(TAG, "Skip intent received")
        onSkip()
      }
      ScaleSetupIntent.TryAgain -> {
        AppLog.d(TAG, "Try again intent received")
        onTryAgain()
      }
      is ScaleSetupIntent.ExitSetup -> {
        AppLog.d(TAG, "Exit setup intent received, isSetupFinished: ${intent.isSetupFinished}")
        onExitSetup(intent.isSetupFinished)
      }

      ScaleSetupIntent.OpenHelp -> {
        AppLog.d(TAG, "Open help intent received")
        openHelpModal()
      }
      is ScaleSetupIntent.RequestPermission -> {
        AppLog.d(TAG, "Request permission intent received: ${intent.permission}")
        this.requestPermission(intent.permission)
      }

      else -> {}
    }
  }

  /**
   * Called when a new device matching the protocol is found during setup.
   * @param device The GGDeviceDetail of the new device found.
   */
  protected open fun onScanResponse(response: GGScanResponse.DeviceDetail, onDeviceFound: (GGDeviceDetail) -> Unit) {
    val ggDeviceDetail = response.data
    AppLog.d(TAG, "Received scan response: ${response.type}, protocol: ${ggDeviceDetail.protocolType}")
    
    when (response.type) {
      GGScanResponseType.NEW_DEVICE -> {
        if (ggDeviceDetail.protocolType == protocolType) {
          AppLog.d(TAG, "New device found with matching protocol: ${ggDeviceDetail.deviceName}")
          viewModelScope.launch {
            try {
              if (deviceService.scaleExistsByMac(ggDeviceDetail.macAddress)) {
                AppLog.w(TAG, "Known scale discovered with MAC: ${ggDeviceDetail.macAddress}")
                dialogQueueService.showDialog(
                  DialogModel.Alert(
                    title = "Known Scale Discovered",
                    message = "Weight Gurus sees a scale that is already set up. If you are trying to set up a second scale, make sure only one is turned on at a time.",
                    onDismiss = {
                      AppLog.d(TAG, "User dismissed known scale dialog")
                      onExitSetup(true)
                      dialogQueueService.dismissCurrent()
                    },
                  ),
                )
              } else {
                AppLog.d(TAG, "New scale discovered, proceeding to next step")
                stopObservingDevices()
                onDeviceFound(ggDeviceDetail)
              }
            } catch (e: Exception) {
              AppLog.e(TAG, "Error checking scale existence", e)
            }
          }
        } else {
          AppLog.d(TAG, "Device protocol mismatch - expected: $protocolType, found: ${ggDeviceDetail.protocolType}")
        }
      }

      else -> {
        AppLog.d(TAG, "Ignoring scan response type: ${response.type}")
      }
    }
  }

  protected open fun onEntryResponse(response: GGScanResponse.Entry, onEntryFound: (List<GGEntry>) -> Unit) {
    AppLog.d(TAG, "Received entry response: ${response.type}")
    when (response.type) {
      GGScanResponseType.SINGLE_ENTRY, GGScanResponseType.MULTI_ENTRIES -> {
        AppLog.d(TAG, "Processing entry data: ${response.data.size} entries")
        onEntryFound(response.data)
      }

      else -> {
        AppLog.d(TAG, "Ignoring entry response type: ${response.type}")
      }
    }
  }

  protected var discoveredScale: Device? = null

  /**
   * Clears the bluetooth timeout job.
   */
  protected fun clearBluetoothTimeout() {
    AppLog.d(TAG, "Clearing bluetooth timeout")
    bluetoothTimeoutJob?.cancel()
    bluetoothTimeoutJob = null
  }

  /**
   * Starts observing device scan responses. Call this when you want to begin collecting devices.
   */
  protected fun startObservingDevices(onDeviceFound: (GGDeviceDetail) -> Unit = {}) {
    AppLog.d(TAG, "Starting device observation")
    deviceObservationJob?.cancel()
    deviceObservationJob = viewModelScope.launch {
      try {
        ggDeviceService.deviceCallbackFlow.filter { it is GGScanResponse.DeviceDetail }
          .collect { scanResponse ->
            AppLog.d(TAG, "Received device scan response: ${scanResponse::class.simpleName}")
            onScanResponse(scanResponse as GGScanResponse.DeviceDetail, onDeviceFound)
          }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error observing device scan responses", e)
      }
    }
  }

  protected fun stopObservingDevices() {
    AppLog.d(TAG, "Stopping device observation")
    deviceObservationJob?.cancel()
    deviceObservationJob = null
  }

  protected fun stopObservingEntries() {
    AppLog.d(TAG, "Stopping entry observation")
    entryObservationJob?.cancel()
    entryObservationJob = null
  }

  protected fun startObservingEntries(onEntryFound: (List<GGEntry>) -> Unit = {}) {
    AppLog.d(TAG, "Starting entry observation")
    if (entryObservationJob == null) {
      entryObservationJob = viewModelScope.launch {
        try {
          ggDeviceService.deviceCallbackFlow.filter { it is GGScanResponse.Entry }
            .collect { scanResponse ->
              AppLog.d(TAG, "Received entry scan response: ${scanResponse::class.simpleName}")
              onEntryResponse(scanResponse as GGScanResponse.Entry, onEntryFound)
            }
        } catch (e: Exception) {
          AppLog.e(TAG, "Error observing entry scan responses", e)
        }
      }
    }
  }

  protected open fun handleButtonChanges(step: Step) {}
  private fun observeStepChanges() {
    AppLog.d(TAG, "Starting step changes observation")
    viewModelScope.launch {
      try {
        state.map { it.scaleSetupState.setupState.step }.collect { newStep ->
          if (currentSetupState.step != newStep) {
            AppLog.d(TAG, "Step changed from ${currentSetupState.step} to $newStep")
            currentSetupState = _state.value.scaleSetupState.setupState
            onStepChange(newStep)
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error observing step changes", e)
      }
    }
    viewModelScope.launch {
      try {
        state.collect {
          handleButtonChanges(state.value.step)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error handling button changes", e)
      }
    }
  }

  /**
   * Requests a specific permission with rationale alert using the permission service.
   */
  private fun requestPermission(permissionType: String) {
    AppLog.d(TAG, "Requesting permission: $permissionType")
    if (permissionType == GGPermissionType.WIFI_SWITCH) {
      AppLog.d(TAG, "Directly requesting WiFi switch permission")
      permissionService.requestPermission(permissionType)
      return
    }
    viewModelScope.launch {
      try {
        dialogUtility.permissionAlert(
          permissionType = permissionType,
          onRequest = {
            AppLog.d(TAG, "User confirmed permission request for: $permissionType")
            permissionService.requestPermission(permissionType)
          },
        )
      } catch (e: Exception) {
        AppLog.e(TAG, "Error requesting permission $permissionType", e)
      }
    }
  }

  fun navigateTo(route: AppRoute) {
    AppLog.d(TAG, "Navigating to route: $route")
    viewModelScope.launch {
      try {
        navigationService.navigateTo(route)
        AppLog.d(TAG, "Successfully navigated to route: $route")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error navigating to route: $route", e)
      }
    }
  }

  /**
   * Opens the Help modal.
   */
  private fun openHelpModal() {
    AppLog.d(TAG, "Opening help modal")
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.HelpPopup,
      ),
    )
  }

  private fun onExitSetup(
    isSetupFinished: Boolean,
  ) {
    AppLog.d(TAG, "Exit setup requested - isSetupFinished: $isSetupFinished, connection status: ${discoveredScale?.connectionStatus}")
    if (isSetupFinished) {
      AppLog.d(TAG, "Setup is finished, proceeding to exit")
      onExit(isSetupFinished)
    } else {
      AppLog.d(TAG, "Setup not finished, showing exit confirmation dialog")
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = ScaleSetupStrings.ExitSetupAlert.Title,
          message = ScaleSetupStrings.ExitSetupAlert.Message(discoveredScale?.connectionStatus == BLEStatus.CONNECTED),
          confirmText = ScaleSetupStrings.ExitSetupAlert.Exit,
          cancelText = ScaleSetupStrings.ExitSetupAlert.Back,
          onConfirm = {
            AppLog.d(TAG, "User confirmed exit setup")
            onExit(isSetupFinished)
          },
        ),
      )
    }
  }

  private fun onExit(isSetupFinished: Boolean) {
    AppLog.d(TAG, "Exiting setup - isSetupFinished: $isSetupFinished")
    viewModelScope.launch {
      try {
        if (isSetupFinished) {
          AppLog.d(TAG, "Setup finished, calling onSetupFinished")
          onSetupFinished()
        }
        AppLog.d(TAG, "Resuming scan and syncing devices")
        ggDeviceService.resumeScan(false)
        val pairedDevices = deviceService.pairedScales.first().map { it.toGGBTDevice() }
        AppLog.d(TAG, "Syncing ${pairedDevices.size} paired devices")
        ggDeviceService.syncDevices(pairedDevices)
        navigateBack()
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during exit process", e)
        navigateBack()
      }
    }
  }

  /**
   * Navigates back from the setup screen.
   */
  private suspend fun navigateBack() {
    AppLog.d(TAG, "Navigating back from BLE setup")
    try {
      navigationService.navigateBack()
      AppLog.d(TAG, "Successfully navigated back from BLE setup")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to navigate back from BLE setup", e)
    }
  }

  protected fun subscribePermissions(): Flow<GGPermissionStatusMap> {
    AppLog.d(TAG, "Subscribing to permissions")
    return combine(
      permissionService.permissionCallBackFlow,
      connectivityObserver.observe(),
    ) { permissions, networkState ->
      val networkStatus = if (networkState.available) GGPermissionState.ENABLED else GGPermissionState.DISABLED
      val wifiSwitchStatus = permissions[GGPermissionType.WIFI_SWITCH] ?: GGPermissionState.DISABLED

      AppLog.d(TAG, "Permission status - Network: $networkStatus, WiFi Switch: $wifiSwitchStatus")

      // WiFi switch is enabled if either network is available OR WiFi switch is enabled
      val updatedWifiSwitchStatus = if (networkStatus == GGPermissionState.ENABLED ||
        wifiSwitchStatus == GGPermissionState.ENABLED
      ) {
        GGPermissionState.ENABLED
      } else {
        GGPermissionState.DISABLED
      }

      AppLog.d(TAG, "Updated WiFi switch status: $updatedWifiSwitchStatus")
      permissions.toMutableMap().apply {
        put(GGPermissionType.WIFI_SWITCH, updatedWifiSwitchStatus)
      }
    }
  }

  /**
   * Loads scale information based on the provided SKU.
   */
  private fun loadScaleInfo() {
    AppLog.d(TAG, "Loading scale info for SKU: $sku, initial step: $initialStep")
    handleIntent(ScaleSetupIntent.SetNewStep(initialStep))
    viewModelScope.launch {
      try {
        if (broadcastId != null) {
          AppLog.d(TAG, "Loading scale from broadcast ID: $broadcastId")
          discoveredScale = ggDeviceService.deviceCache.value[broadcastId] as? Device
          AppLog.d(TAG, "Found scale from cache: ${discoveredScale?.id}")
        }
        handleIntent(ScaleSetupIntent.SetSku(sku))
      } catch (e: Exception) {
        AppLog.e(TAG, "Error loading scale info", e)
      }
    }
  }

  companion object {
    private const val TAG = "BLESetupViewmodel"
  }
}


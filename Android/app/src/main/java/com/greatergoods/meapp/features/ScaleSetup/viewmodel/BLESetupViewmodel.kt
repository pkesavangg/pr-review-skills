package com.greatergoods.meapp.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.BLEStatus
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.domain.model.storage.toGGBTDevice
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.features.ScaleSetup.enums.ScaleSetupStep
import com.greatergoods.meapp.features.ScaleSetup.modal.SetupInitData
import com.greatergoods.meapp.features.ScaleSetup.reducer.BaseState
import com.greatergoods.meapp.features.ScaleSetup.reducer.ScaleSetupIntent
import com.greatergoods.meapp.features.ScaleSetup.reducer.SetupState
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Singleton

private val TAG = "ScaleSetupViewModel"

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
      isInitialized = true
      onInit()
    }
  }

  private fun onInit() {
    loadScaleInfo()
    observeStepChanges()
    observePermissions()
  }

  // Abstract methods for concrete implementations
  abstract override fun provideInitialState(): State
  abstract fun observePermissions()
  abstract fun onStepChange(step: ScaleSetupStep)
  abstract fun onNext()
  abstract fun onBack()
  abstract fun onSkip()

  private var deviceObservationJob: Job? = null

  private var entryObservationJob: Job? = null

  private var permissions: GGPermissionStatusMap? = null

  var isPermissionGranted = false

  var currentSetupState: SetupState<Step> = SetupState(initialStep)

  override fun handleIntent(intent: ScaleSetupIntent) {
    super.handleIntent(intent)
    when (intent) {
      ScaleSetupIntent.Next -> onNext()
      ScaleSetupIntent.Back -> onBack()
      ScaleSetupIntent.Skip -> onSkip()
      is ScaleSetupIntent.ExitSetup ->
        onExitSetup(
          intent.isSetupFinished,
        )

      ScaleSetupIntent.OpenHelp -> openHelpModal()
      is ScaleSetupIntent.RequestPermission -> {
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
    when (response.type) {
      GGScanResponseType.NEW_DEVICE -> {
        if (ggDeviceDetail.protocolType == protocolType) {
          viewModelScope.launch {
            if (deviceService.scaleExistsByMac(ggDeviceDetail.macAddress)) {
              dialogQueueService.showDialog(
                DialogModel.Alert(
                  title = "Known Scale Discovered",
                  message = "Weight Gurus sees a scale that is already set up. If you are trying to set up a second scale, make sure only one is turned on at a time.",
                  onDismiss = {
                    onExitSetup(true)
                    dialogQueueService.dismissCurrent()
                  },
                ),
              )
            }
          }
        } else {
          AppLog.d(TAG, "Wake up successful, proceeding to next step")
          stopObservingDevices()
          onDeviceFound(ggDeviceDetail)
        }
      }

      else -> null
    }
  }

  protected open fun onEntryResponse(response: GGScanResponse.Entry) {}

  protected var discoveredScale: Device? = null

  /**
   * Starts observing device scan responses. Call this when you want to begin collecting devices.
   */
  protected fun startObservingDevices(onDeviceFound: (GGDeviceDetail) -> Unit) {
    deviceObservationJob?.cancel()
    deviceObservationJob = viewModelScope.launch {
      ggDeviceService.deviceCallbackFlow.filter { it is GGScanResponse.DeviceDetail }
        .collect { scanResponse ->
          onScanResponse(scanResponse as GGScanResponse.DeviceDetail, onDeviceFound)
        }
    }
  }

  protected fun stopObservingDevices() {
    deviceObservationJob?.cancel()
    deviceObservationJob = null
  }

  protected fun stopObservingEntries() {
    entryObservationJob?.cancel()
    entryObservationJob = null
  }

  protected fun startObservingEntries() {
    if (entryObservationJob == null) {
      entryObservationJob = viewModelScope.launch {
        ggDeviceService.deviceCallbackFlow.filter { it is GGScanResponse.Entry }
          .collect { scanResponse ->
            onEntryResponse(scanResponse as GGScanResponse.Entry)
          }
      }
    }
  }

  private fun observeStepChanges() {
    viewModelScope.launch {
      state.map { it.scaleSetupState.setupState.step }.collect { newStep ->
        if (currentSetupState.step != newStep) {
          currentSetupState = _state.value.scaleSetupState.setupState
          onStepChange(newStep)
        }
      }
    }
  }

  /**
   * Requests a specific permission with rationale alert using the permission service.
   */
  private fun requestPermission(permissionType: String) {
    if (permissionType == GGPermissionType.WIFI_SWITCH) {
      permissionService.requestPermission(permissionType)
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
        AppLog.e(TAG, "Error requesting permission ${permissionType}", e.toString())
      }
    }
  }

  fun navigateTo(route: AppRoute) {
    viewModelScope.launch {
      navigationService.navigateTo(route)
    }
  }

  /**
   * Opens the Help modal.
   */
  private fun openHelpModal() {
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.HelpPopup,
      ),
    )
  }

  private fun onExitSetup(
    isSetupFinished: Boolean,
  ) {
    if (isSetupFinished) {
      onExit()
    } else {
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = ScaleSetupStrings.ExitSetupAlert.Title,
          message = ScaleSetupStrings.ExitSetupAlert.Message(discoveredScale?.connectionStatus == BLEStatus.CONNECTED),
          confirmText = ScaleSetupStrings.ExitSetupAlert.Exit,
          cancelText = ScaleSetupStrings.ExitSetupAlert.Back,
          onConfirm = {
            onExit()
          },
        ),
      )
    }
  }

  private fun onExit() {
    viewModelScope.launch {
      if (discoveredScale != null && discoveredScale!!.device?.protocolType == GGDeviceProtocolType.GG_DEVICE_PROTOCOL_R4.value) {
        deviceService.updateDevice(discoveredScale!!)
        ggDeviceService.cancelWifi(discoveredScale!!.toGGBTDevice()) {}
      }
      ggDeviceService.resumeScan(true)
      val pairedDevices = deviceService.pairedScales.first().map { it.toGGBTDevice() }
      ggDeviceService.syncDevices(pairedDevices)
      navigateBack()
    }
  }

  /**
   * Navigates back from the setup screen.
   */
  private suspend fun navigateBack() {
    try {
      navigationService.navigateBack()
      AppLog.d(TAG, "Successfully navigated back from scale setup")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to navigate back from scale setup", e.toString())
    }
  }

  protected fun subscribePermissions(): Flow<GGPermissionStatusMap> {
    return combine(
      permissionService.permissionCallBackFlow,
      connectivityObserver.observe(),
    ) { permissions, networkState ->
      val networkStatus = if (networkState.available) GGPermissionState.ENABLED else GGPermissionState.DISABLED
      val wifiSwitchStatus = permissions[GGPermissionType.WIFI_SWITCH] ?: GGPermissionState.DISABLED

      // WiFi switch is enabled if either network is available OR WiFi switch is enabled
      val updatedWifiSwitchStatus = if (networkStatus == GGPermissionState.ENABLED ||
        wifiSwitchStatus == GGPermissionState.ENABLED
      ) {
        GGPermissionState.ENABLED
      } else {
        GGPermissionState.DISABLED
      }

      permissions.toMutableMap().apply {
        put(GGPermissionType.WIFI_SWITCH, updatedWifiSwitchStatus)
      }
    }
  }

  /**
   * Loads scale information based on the provided SKU.
   */
  private fun loadScaleInfo() {
    AppLog.d(TAG, "Loading scale info for SKU: $sku")
    viewModelScope.launch {
      if (broadcastId != null) {
        discoveredScale = ggDeviceService.deviceCache.value[broadcastId] as? Device
      }
      handleIntent(ScaleSetupIntent.SetSku(sku))
      handleIntent(ScaleSetupIntent.SetNewStep(initialStep))
    }
  }
}


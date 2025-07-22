package com.greatergoods.meapp.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.domain.model.storage.toGGBTDevice
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.greatergoods.meapp.features.ScaleSetup.modal.ConnectionState
import com.greatergoods.meapp.features.ScaleSetup.reducer.LCBTScaleSetupState
import com.greatergoods.meapp.features.ScaleSetup.reducer.LcbtScaleSetupIntent
import com.greatergoods.meapp.features.ScaleSetup.reducer.LcbtScaleSetupReducer
import com.greatergoods.meapp.features.ScaleSetup.reducer.SetupState
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.appPermissions.helper.AppPermissionsHelper
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import com.greatergoods.meapp.features.common.model.DialogModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ViewModel for the LcbtScaleSetupScreen. Handles scale setup flow state and navigation.
 * @property sku The SKU/model number of the scale being set up.
 */
@HiltViewModel(
  assistedFactory = LcbtScaleSetupViewModel.Factory::class,
)
class LcbtScaleSetupViewModel
@AssistedInject
constructor(
  @Assisted("sku") private val sku: String,
  @Assisted("broadcastId") private val broadcastId: String? = null,
  @Assisted("initialStep") private val initialStep: LcbtScaleSetupStep = LcbtScaleSetupStep.SCALE_INFO,
  override val ggDeviceService: GGDeviceService,
  override val permissionService: GGPermissionService,
  override val connectivityObserver: IConnectivityObserver,
  private val deviceService: IDeviceService,
  private val dialogUtility: IDialogUtility,
) : ScaleSetupViewmodel<LCBTScaleSetupState, LcbtScaleSetupIntent>(
  ggDeviceService, connectivityObserver, permissionService,
  reducer = LcbtScaleSetupReducer(),
) {
  @AssistedFactory
  interface Factory {
    fun create(
      @Assisted("sku") sku: String,
      @Assisted("broadcastId") broadcastId: String? = null,
      @Assisted("initialStep") initialStep: LcbtScaleSetupStep = LcbtScaleSetupStep.SCALE_INFO
    ): LcbtScaleSetupViewModel
  }

  private val TAG = "LcbtScaleSetupViewModel"
  private var currentSetupState: SetupState<LcbtScaleSetupStep> = SetupState(initialStep)
  private var isPermissionGranted = false

  override fun provideInitialState(): LCBTScaleSetupState = LCBTScaleSetupState()

  override fun onScanResponse(response: GGScanResponse.DeviceDetail) {
    val data = response.data
    when (response.type) {
      GGScanResponseType.NEW_DEVICE -> {
        if (data.protocolType == GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A6.value) {
          viewModelScope.launch {
            discoveredScale = Device(
              device = data,
              deviceType = ScaleSetupType.Lcbt.value,
              sku = sku,
            )
            onNext()
          }
        }
      }

      else -> null
    }
  }

  override fun handleIntent(intent: LcbtScaleSetupIntent) {
    super.handleIntent(intent)
    when (intent) {
      LcbtScaleSetupIntent.Next -> onNext()
      LcbtScaleSetupIntent.Back -> onBack()
      LcbtScaleSetupIntent.Skip -> onSkip()
      is LcbtScaleSetupIntent.ExitSetup ->
        onExitSetup(
          intent.isSetupFinished,
          intent.isConnected,
        )

      LcbtScaleSetupIntent.OpenHelp -> openHelpModal()
      is LcbtScaleSetupIntent.RequestPermission -> {
        this.requestPermission(intent.permission)
      }

      else -> {}
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

  init {
    loadScaleInfo()
    observePermissions()
    observeStepChanges()
    viewModelScope.launch {
      if (broadcastId != null) {
        discoveredScale = ggDeviceService.deviceCache.value[broadcastId] as? Device
      }
      handleIntent(LcbtScaleSetupIntent.SetNewStep(initialStep))
    }
  }

  private fun observePermissions() {
    viewModelScope.launch {
      permissionService.permissionCallBackFlow.collect {
        handleIntent(LcbtScaleSetupIntent.SetPermissions(it))
        val areRequiredPermissionsEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(it, sku)
        if (isPermissionGranted != areRequiredPermissionsEnabled) {
          isPermissionGranted = areRequiredPermissionsEnabled
        }
        if (!areRequiredPermissionsEnabled) {
          if (currentSetupState.step != LcbtScaleSetupStep.PERMISSIONS && currentSetupState.step != LcbtScaleSetupStep.SCALE_INFO) {
            handleIntent(LcbtScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.PERMISSIONS))
          }
        }
      }
    }
  }

  private fun observeStepChanges() {
    viewModelScope.launch {
      var previousStep: LcbtScaleSetupStep? = null

      state.map { it.setupState.step }.collect { newStep ->

        // Only trigger if step actually changed
        if (previousStep != null && previousStep != newStep) {
          AppLog.d(TAG, "Step changed from $previousStep to $newStep")
          currentSetupState = _state.value.setupState

          // Call appropriate function based on the new step
          when (newStep) {

            LcbtScaleSetupStep.WAKEUP -> {
              wakeUpScale()
            }

            LcbtScaleSetupStep.CONNECTING_BLUETOOTH -> {
              connectToBluetooth()
            }

            else -> {
              // No automatic action needed for other steps
            }
          }
        }

        previousStep = newStep
      }
    }
  }

  /**
   * Handles waking up the scale. Sets loading state and controls when to proceed.
   */
  private fun wakeUpScale() {

    // Start collecting device scan responses only now
    AppLog.d(TAG, "Starting wake up scale process")
    if (currentSetupState.connectionState != ConnectionState.Loading) {
      handleIntent(LcbtScaleSetupIntent.AlterConnectionState(ConnectionState.Loading))
    }

    viewModelScope.launch {
      try {
        ggDeviceService.scanForPairing()
        startObservingDevices()
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during wake up process", e.toString())
        handleIntent(LcbtScaleSetupIntent.AlterConnectionState(ConnectionState.ErrorWithMessage("WAKEUP_002")))
      }
    }
  }

  private fun connectToBluetooth() {
    if (currentSetupState.connectionState != ConnectionState.Loading) {
      handleIntent(
        LcbtScaleSetupIntent.AlterConnectionState(
          ConnectionState.Loading,
        ),
      )
    }
    viewModelScope.launch {
      try {
        val ggBtDevice = discoveredScale!!.toGGBTDevice()
        ggDeviceService.pairDevice(device = ggBtDevice) {}
        deviceService.saveScale(discoveredScale!!)
        handleIntent(
          LcbtScaleSetupIntent.AlterConnectionState(
            ConnectionState.Success,
          ),
        )
        val pairedDevices = deviceService.pairedScales.first().map { it.toGGBTDevice() }
        ggDeviceService.syncDevices(pairedDevices)
        delay(1000)
        onNext()
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during bluetooth connection", e.toString())
        handleIntent(
          LcbtScaleSetupIntent.AlterConnectionState(
            ConnectionState.ErrorWithMessage("BT_002"),
          ),
        )
      }
    }
  }

  /**
   * Loads scale information based on the provided SKU.
   */
  private fun loadScaleInfo() {
    AppLog.d(TAG, "Loading scale info for SKU: $sku")
    handleIntent(LcbtScaleSetupIntent.SetSku(sku))
  }

  /**
   * Handles moving to the next step in the setup process.
   */
  private fun onNext() {
    val currentState = state.value
    AppLog.d(TAG, "Moving to next step from: ${currentState.setupState.step}")

    if (currentState.isLastStep) {
      AppLog.d(TAG, "Reached last step, completing setup")
      handleIntent(LcbtScaleSetupIntent.ExitSetup(true, true))
    } else if (currentSetupState.step == LcbtScaleSetupStep.SCALE_INFO && isPermissionGranted) {
      handleIntent(LcbtScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.WAKEUP))
    } else {
      AppLog.d(TAG, "After Next intent - new currentStep: ${currentState.setupState.step}")
      if (currentState.nextStep != null)
        handleIntent(LcbtScaleSetupIntent.SetNewStep(currentState.nextStep!!))
    }
  }

  /**
   * Handles moving to the previous step in the setup process.
   */
  private fun onBack() {
    val currentState = state.value
    AppLog.d(TAG, "Moving to previous step from: ${currentState.setupState.step}")

    if (currentState.isFirstStep) {
      AppLog.d(TAG, "At first step, navigating back")
      navigateTo(AppRoute.AccountSettings.AddEditScales)
    } else {
      AppLog.d(TAG, "After Back intent - new currentStep: ${currentState.setupState.step}")
    }
  }

  /**
   * Handles skipping the current step.
   */
  private fun onSkip() {
    AppLog.d(TAG, "Skipping current step: ${state.value.setupState.step}")
    // For now, treat skip as next
    onNext()
  }

  private fun onExitSetup(
    isSetupFinished: Boolean,
    isConnected: Boolean,
  ) {
    if (isSetupFinished) {
      onExit()
    } else {
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = ScaleSetupStrings.ExitSetupAlert.Title,
          message = ScaleSetupStrings.ExitSetupAlert.Message(isConnected),
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
      ggDeviceService.resumeScan(true)
      navigateBack()
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

  private fun navigateTo(route: AppRoute) {
    viewModelScope.launch {
      navigationService.navigateTo(route)
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
        AppLog.e(TAG, "Failed to navigate back from scale setup", e.toString())
      }
    }
  }
}

package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.ScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.LCBTScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.LcbtScaleSetupReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel for the LcbtScaleSetupScreen. Handles scale setup flow state and navigation.
 * @property sku The SKU/model number of the scale being set up.
 */
@HiltViewModel(
  assistedFactory = LcbtBLESetupViewModel.Factory::class,
)
class LcbtBLESetupViewModel
@AssistedInject
constructor(
  @Assisted val setupInit: SetupInitData<LcbtScaleSetupStep>,
  dependencies: BLESetupDependencies
) : BLESetupViewmodel<LcbtScaleSetupStep, LCBTScaleSetupState>(
  GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A6.value,
  setupInit,
  reducer = LcbtScaleSetupReducer(),
  dependencies,
) {
  @AssistedFactory
  interface Factory {
    fun create(
      @Assisted setupInit: SetupInitData<LcbtScaleSetupStep>,
    ): LcbtBLESetupViewModel
  }

  private val sku = setupInit.sku
  private val TAG = "LcbtScaleSetupViewModel"

  override fun provideInitialState(): LCBTScaleSetupState {
    return LCBTScaleSetupState()
  }

  init {
    lazyInit()
  }

  override fun onNext() {
    val currentState = state.value
    AppLog.d(TAG, "Moving to next step from: ${currentState.step}")

    if (currentState.isLastStep) {
      AppLog.d(TAG, "Reached last step, completing setup")
      this.handleIntent(ScaleSetupIntent.ExitSetup(true))
    } else if (currentSetupState.step == LcbtScaleSetupStep.SCALE_INFO && isPermissionGranted) {
      handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.WAKEUP))
    } else {
      AppLog.d(TAG, "After Next intent - new currentStep: ${currentState.step}")
      if (currentState.nextStep != null)
        handleIntent(ScaleSetupIntent.SetNewStep(currentState.nextStep!!))
    }
  }

  override suspend fun onSetupFinished() {
    dialogQueueService.showLoader(ScaleSetupStrings.SaveScaleLoader)
    try {
      if (discoveredScale != null) {
        deviceService.saveScale(discoveredScale!!)
      }
    } finally {
      dialogQueueService.dismissLoader()
    }
  }

  override fun onBack() {
    val currentState = state.value
    AppLog.d(TAG, "Moving to previous step from: ${currentState.step}")

    if (currentState.isFirstStep) {
      AppLog.d(TAG, "At first step, navigating back")
      navigateTo(AppRoute.AccountSettings.AddEditScales)
    } else {
      AppLog.d(TAG, "After Back intent - new currentStep: ${currentState.step}")
    }
  }

  override fun onSkip() {
    AppLog.d(TAG, "Skipping current step: ${state.value.scaleSetupState.setupState.step}")
    // For now, treat skip as next
    onNext()
  }

  override fun onTryAgain() {
    val currentStep = state.value.step
    AppLog.d(TAG, "Trying again for step: $currentStep")

    when (currentStep) {
      LcbtScaleSetupStep.WAKEUP -> {
        wakeUpScale()
      }

      LcbtScaleSetupStep.CONNECTING_BLUETOOTH -> {
        connectToBluetooth()
      }

      else -> null
    }
  }

  override fun onStepChange(step: ScaleSetupStep) {
    viewModelScope.launch {
      when (step) {

        LcbtScaleSetupStep.WAKEUP -> {
          wakeUpScale()
        }

        LcbtScaleSetupStep.CONNECTING_BLUETOOTH -> {
          connectToBluetooth()
        }

        else -> null
      }
    }
  }

  /**
   * Handles waking up the scale. Sets loading state and controls when to proceed.
   */
  private fun wakeUpScale() {
    // Always set loading state to ensure UI updates
    handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Loading))
    // Clear any existing timeout and stop any existing device observation
    clearBluetoothTimeout()
    stopObservingDevices()

    // Set timeout for bluetooth scanning
    bluetoothTimeoutJob = viewModelScope.launch {
      delay(bluetoothTimeout)
      // Check if we're still in the wakeup step and no device was found
      if (discoveredScale == null) {
        AppLog.d(TAG, "Bluetooth scan timeout reached")
        handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
      }
    }

    // Start collecting device scan responses only now
    AppLog.d(TAG, "Starting wake up scale process")

    try {
      ggDeviceService.scanForPairing()
      startObservingDevices { data ->
        viewModelScope.launch {
          discoveredScale = Device(
            device = data,
            deviceType = ScaleSetupType.Lcbt.value,
            sku = sku,
          )
          // Clear timeout when device is found
          clearBluetoothTimeout()
          delay(2000)
          onNext()
        }
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during wake up process", e.toString())
      clearBluetoothTimeout()
      handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
    }
  }

  private fun connectToBluetooth() {
    // Always set loading state to ensure UI updates
    handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Loading))
    clearBluetoothTimeout()
    AppLog.d(TAG, "Connecting to bluetooth")
    bluetoothTimeoutJob = viewModelScope.launch {
      delay(bluetoothTimeout)
      AppLog.d(TAG, "Bluetooth connection timeout reached")
      handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
    }
    viewModelScope.launch {
      try {
        deviceService.onDeviceUpdate(
          macAddress = discoveredScale!!.device?.macAddress,
          connectionStatus = BLEStatus.CONNECTED,
        )
        clearBluetoothTimeout() // Cancel timeout on success
        delay(3000)
        handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Success))
        delay(2000)
        onNext()
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during bluetooth connection", e.toString())
        clearBluetoothTimeout()
        handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.ErrorWithMessage("BT_002")))
      }
    }
  }

  override fun observePermissions() {
    viewModelScope.launch {
      subscribePermissions().collect { newPermissions: GGPermissionStatusMap ->
        val areRequiredPermissionsEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(newPermissions, sku)
        handleIntent(ScaleSetupIntent.SetPermissions(newPermissions))
        if (isPermissionGranted != areRequiredPermissionsEnabled) {
          isPermissionGranted = areRequiredPermissionsEnabled
        }

        if (!areRequiredPermissionsEnabled) {
          if (currentSetupState.step != LcbtScaleSetupStep.PERMISSIONS && currentSetupState.step != LcbtScaleSetupStep.SCALE_INFO) {
            handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.PERMISSIONS))
          }
        }
        handleIntent(ScaleSetupIntent.NextEnabled(areRequiredPermissionsEnabled))

      }
    }
  }
}

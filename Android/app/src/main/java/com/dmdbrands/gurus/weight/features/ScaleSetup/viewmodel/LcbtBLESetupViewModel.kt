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

  override fun provideInitialState(): LCBTScaleSetupState {
    return LCBTScaleSetupState()
  }

  init {
    AppLog.d(TAG, "LcbtBLESetupViewModel initialized for SKU: $sku")
    lazyInit()
  }

  suspend fun saveScale(){
    try {
      if (discoveredScale != null) {
        discoveredScale = discoveredScale!!.copy(
          nickname = state.value.scaleSetupState.scaleInfo?.productName ?: "Bluetooth Smart Scale",
        )
        deviceService.saveScale(discoveredScale!!)
        AppLog.i(TAG, "Successfully saved LCBT scale")
      } else {
        AppLog.w(TAG, "No discovered LCBT scale to save")
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error saving LCBT scale", e)
    } finally {
    }
  }

  override fun onNext() {
    val currentState = state.value
    AppLog.d(TAG, "Moving to next step from: ${currentState.step}")

    if (currentState.isLastStep) {
      AppLog.d(TAG, "Reached last step, completing setup")
      this.handleIntent(ScaleSetupIntent.ExitSetup(true))
    } else if (currentSetupState.step == LcbtScaleSetupStep.SCALE_INFO) {
      if (isPermissionGranted) {
        handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.WAKEUP))
      } else {
        // Check and request permissions sequentially
        handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.PERMISSIONS))
        permissionAccess()
      }
    } else {
      AppLog.d(TAG, "After Next intent - new currentStep: ${currentState.step}")
      if (currentState.nextStep != null)
        handleIntent(ScaleSetupIntent.SetNewStep(currentState.nextStep!!))
    }
  }

  override suspend fun onSetupFinished() {
  }

  override fun onBack() {
    val currentState = state.value
    val currentStep = currentState.step
    AppLog.d(TAG, "Moving to previous step from: $currentStep")

    if (currentState.isFirstStep) {
      AppLog.d(TAG, "At first step, navigating back to add/edit scales")
      navigateTo(AppRoute.AccountSettings.AddEditScales)
      return
    }

    // Otherwise, go to previous step
    val previousStep = currentState.previousStep
    if (previousStep != null) {
      AppLog.d(TAG, "Navigating to previous step: $previousStep")
      handleIntent(ScaleSetupIntent.SetNewStep(previousStep))
    } else {
      AppLog.d(TAG, "No previous step available, navigating back to add/edit scales")
      navigateTo(AppRoute.AccountSettings.AddEditScales)
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
        AppLog.d(TAG, "Retrying wake up process")
        wakeUpScale()
      }

      LcbtScaleSetupStep.CONNECTING_BLUETOOTH -> {
        AppLog.d(TAG, "Retrying Bluetooth connection")
        connectToBluetooth()
      }

      else -> {
        AppLog.w(TAG, "Try again called on step that doesn't support retry: $currentStep")
      }
    }
  }

  override fun onStepChange(step: ScaleSetupStep) {
    AppLog.d(TAG, "Step changed to: $step")
    viewModelScope.launch {
      when (step) {

        LcbtScaleSetupStep.WAKEUP -> {
          AppLog.d(TAG, "Starting wake up process")
          wakeUpScale()
        }

        LcbtScaleSetupStep.CONNECTING_BLUETOOTH -> {
          AppLog.d(TAG, "Starting Bluetooth connection")
          connectToBluetooth()
        }

        else -> {
          AppLog.d(TAG, "No specific action for step: $step")
        }
      }
    }
  }

  /**
   * Handles waking up the scale. Sets loading state and controls when to proceed.
   */
  private fun wakeUpScale() {
    // Always set loading state to ensure UI updates
    AppLog.d(TAG, "Starting wake up scale process")
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
    try {
      AppLog.d(TAG, "Starting device scan for wake up")
      ggDeviceService.scanForPairing()
      startObservingDevices { data ->
        AppLog.d(TAG, "LCBT device found: ${data.deviceName}")
        viewModelScope.launch {
          discoveredScale = Device(
            device = data,
            deviceType = ScaleSetupType.Lcbt.value,
            sku = sku,
          )
          // Clear timeout when device is found
          clearBluetoothTimeout()
          AppLog.d(TAG, "Waiting 2 seconds before proceeding")
          delay(2000)
          onNext()
        }
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during wake up process", e)
      clearBluetoothTimeout()
      handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
    }
  }

  private fun connectToBluetooth() {
    // Always set loading state to ensure UI updates
    if (setupInit.initialStep == LcbtScaleSetupStep.CONNECTING_BLUETOOTH) {
      ggDeviceService.scanForPairing()
    }
    AppLog.d(TAG, "Starting Bluetooth connection process")
    handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Loading))
    clearBluetoothTimeout()
    bluetoothTimeoutJob = viewModelScope.launch {
      delay(bluetoothTimeout)
      AppLog.d(TAG, "Bluetooth connection timeout reached")
      handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
    }
    viewModelScope.launch {
      try {
        AppLog.d(TAG, "Updating device connection status")
        delay(3000)
        deviceService.onDeviceUpdate(
          discoveredScale?.device!!,
          connectionStatus = BLEStatus.CONNECTED,
        )
        clearBluetoothTimeout() // Cancel timeout on success
        AppLog.d(TAG, "Waiting 3 seconds after connection")
        handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Success))
        saveScale()
        AppLog.d(TAG, "Waiting 2 seconds before proceeding")
        onNext()
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during bluetooth connection", e)
        clearBluetoothTimeout()
        handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.ErrorWithMessage("BT_002")))
      }
    }
  }

  override fun observePermissions() {
    AppLog.d(TAG, "Starting permission observation for LCBT scale setup")
    viewModelScope.launch {
      try {
        subscribePermissions().collect { newPermissions: GGPermissionStatusMap ->
          val areRequiredPermissionsEnabled =
            AppPermissionsHelper.areRequiredPermissionsEnabled(newPermissions, setupType = ScaleSetupType.Lcbt)
          AppLog.d(TAG, "Required permissions enabled: $areRequiredPermissionsEnabled")
          handleIntent(ScaleSetupIntent.SetPermissions(newPermissions))
          if (isPermissionGranted != areRequiredPermissionsEnabled) {
            AppLog.d(TAG, "Permission granted status changed: $isPermissionGranted -> $areRequiredPermissionsEnabled")
            isPermissionGranted = areRequiredPermissionsEnabled
          }
          if (!areRequiredPermissionsEnabled) {
            if (currentSetupState.step != LcbtScaleSetupStep.PERMISSIONS && currentSetupState.step != LcbtScaleSetupStep.SCALE_INFO) {
              AppLog.d(TAG, "Permissions not granted, moving to permissions step")
              handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.PERMISSIONS))
            }
          }
          handleIntent(ScaleSetupIntent.NextEnabled(areRequiredPermissionsEnabled))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error observing permissions", e)
      }
    }
  }

  companion object {
    private const val TAG = "LcbtBLESetupViewModel"
  }
}

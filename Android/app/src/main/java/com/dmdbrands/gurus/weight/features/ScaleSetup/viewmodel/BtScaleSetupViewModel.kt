package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.ScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtScaleSetupReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel for the BtScaleSetupScreen. Handles scale setup flow state and navigation.
 * @property sku The SKU/model number of the scale being set up.
 */
@HiltViewModel(
  assistedFactory = BtScaleSetupViewModel.Factory::class,
)
class BtScaleSetupViewModel
@AssistedInject
constructor(
  @Assisted private val scaleInit: SetupInitData<BtScaleSetupStep>,
  dependencies: BLESetupDependencies
) : BLESetupViewmodel<BtScaleSetupStep, BtScaleSetupState>(
  GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A3.value,
  scaleInit,
  reducer = BtScaleSetupReducer(),
  dependencies,
) {
  @AssistedFactory
  interface Factory {
    fun create(scaleInit: SetupInitData<BtScaleSetupStep>): BtScaleSetupViewModel
  }

  private val TAG = "BtScaleSetupViewModel"

  init {
    lazyInit()
  }

  override fun provideInitialState(): BtScaleSetupState = BtScaleSetupState()

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

  override fun observePermissions() {
    viewModelScope.launch {
      subscribePermissions().collect { newPermissions ->
        viewModelScope.launch {
          val areRequiredPermissionsEnabled =
            AppPermissionsHelper.areRequiredPermissionsEnabled(newPermissions, scaleInit.sku)
          handleIntent(ScaleSetupIntent.NextEnabled(areRequiredPermissionsEnabled))
          handleIntent(ScaleSetupIntent.SetPermissions(newPermissions))
          if (isPermissionGranted != areRequiredPermissionsEnabled) {
            isPermissionGranted = areRequiredPermissionsEnabled
          }
          if (!areRequiredPermissionsEnabled) {
            if (currentSetupState.step != BtScaleSetupStep.PERMISSIONS && currentSetupState.step != BtScaleSetupStep.SCALE_INFO) {
              handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.PERMISSIONS))
            }
          }
        }
      }
    }
  }

  override fun onNext() {
    val currentState = state.value
    AppLog.d(TAG, "Moving to next step from: ${currentState.step}")

    if (currentState.isLastStep) {
      AppLog.d(TAG, "Reached last step, completing setup")
      this.handleIntent(ScaleSetupIntent.ExitSetup(true))
    } else if (currentState.step == BtScaleSetupStep.SCALE_INFO && isPermissionGranted) {
      handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.SELECT_USER))
    } else {
      AppLog.d(TAG, "After Next intent - new currentStep: ${currentState.step}")
      if (currentState.nextStep != null)
        handleIntent(ScaleSetupIntent.SetNewStep(currentState.nextStep!!))
    }
  }

  override fun onBack() {
    val currentState = state.value
    AppLog.d(TAG, "Moving to previous step from: ${currentState.step}")

    if (currentState.isFirstStep) {
      AppLog.d(TAG, "At first step, navigating back")
      navigateTo(AppRoute.AccountSettings.AddEditScales)
    } else if (currentState.step == BtScaleSetupStep.SELECT_USER && isPermissionGranted) {
      handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.SCALE_INFO))
    } else {
      if (currentState.previousStep != null)
        handleIntent(ScaleSetupIntent.SetNewStep(currentState.previousStep!!))
      AppLog.d(TAG, "After Back intent - new currentStep: ${currentState.step}")
    }
  }

  override fun onSkip() {
    AppLog.d(TAG, "Skipping current step: ${state.value.scaleSetupState.setupState.step}")
    // For now, treat skip as next
    onNext()
  }

  override fun onStepChange(step: ScaleSetupStep) {
    when (step) {
      BtScaleSetupStep.PAIRING_MODE -> {
        connectToBluetooth()
      }

      BtScaleSetupStep.STEP_ON -> {
        collectMeasurement()
      }

      else -> null
    }
  }

  override fun handleButtonChanges(step: BtScaleSetupStep) {
    val backEnabled = when (step) {
      BtScaleSetupStep.SETUP_FINISHED, BtScaleSetupStep.SET_DEVICE_USER, BtScaleSetupStep.SCALE_INFO -> false
      else -> true
    }
    val nextEnabled = when (step) {
      BtScaleSetupStep.PAIRING_MODE, BtScaleSetupStep.STEP_ON -> false
      BtScaleSetupStep.SELECT_USER -> _state.value.user != null
      else -> true
    }
    handleIntent(ScaleSetupIntent.BackEnabled(backEnabled))
    handleIntent(ScaleSetupIntent.NextEnabled(nextEnabled))
  }

  override fun onTryAgain() {
    val currentStep = state.value.step

    when (currentStep) {
      BtScaleSetupStep.PAIRING_MODE -> {
        connectToBluetooth()
      }

      BtScaleSetupStep.STEP_ON -> {
        collectMeasurement()
      }

      else -> null
    }
  }

  private fun connectToBluetooth() {
    // Clear any existing timeout
    clearBluetoothTimeout()

    AppLog.d(TAG, "Connecting to bluetooth")
    handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Loading))
    try {
      startObservingDevices { data ->
        discoveredScale = Device(
          device = data,
          deviceType = ScaleSetupType.Bluetooth.value,
          sku = scaleInit.sku,
          userNumber = _state.value.user,
        )
        // Clear timeout when device is found
        clearBluetoothTimeout()

        ggDeviceService.pairDevice(discoveredScale!!.toGGBTDevice()) {
          when (it) {
            GGUserActionResponseType.CREATION_COMPLETED -> {
              ggDeviceService.getDeviceInfo(discoveredScale!!.toGGBTDevice()) { deviceDetails ->
                discoveredScale = discoveredScale!!.copy(connectionStatus = BLEStatus.CONNECTED, device = deviceDetails)
                handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Success))
                ggDeviceService.syncDevices(listOf(discoveredScale!!.toGGBTDevice()))
                onNext()
              }
            }

            else -> {
              showRetryToast()
              handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.ErrorWithMessage("WAKEUP_001")))
            }
          }
        }
      }

      // Set timeout for bluetooth scanning
      bluetoothTimeoutJob = viewModelScope.launch {
        delay(bluetoothTimeout)
        // Check if we're still in the pairing mode step and no device was found
        if (discoveredScale == null) {
          AppLog.d(TAG, "Bluetooth scan timeout reached")
          showRetryToast()
          handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.ErrorWithMessage("WAKEUP_001")))
        }
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during wake up process", e.toString())
      clearBluetoothTimeout()
      handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.ErrorWithMessage("WAKEUP_002")))
    }
  }

  private fun collectMeasurement() {
    AppLog.d(TAG, "Collecting measurement")
    handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Loading))
    try {
      startObservingEntries { entries ->
        handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Success))
        onNext()
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during measurement", e.toString())
      handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.ErrorWithMessage("MEASUREMENT_002")))
    }
  }

  private fun showRetryToast() {
    dialogQueueService.showToast(
      Toast(
        title = BtScaleSetupStrings.PairingMode.RetryToast.Title,
        message = BtScaleSetupStrings.PairingMode.RetryToast.Message,
        action = null,
      ),
    )
  }
}

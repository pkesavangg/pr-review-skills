package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.ScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BabyScaleSetupReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BabyScaleSetupState
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
import java.time.Instant

@HiltViewModel(
  assistedFactory = BabyScaleBLESetupViewModel.Factory::class,
)
class BabyScaleBLESetupViewModel
@AssistedInject
constructor(
  @Assisted val setupInit: SetupInitData<BabyScaleSetupStep>,
  dependencies: BLESetupDependencies,
) : BLESetupViewmodel<BabyScaleSetupStep, BabyScaleSetupState>(
  GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A6.value,
  setupInit,
  reducer = BabyScaleSetupReducer(),
  dependencies,
) {
  @AssistedFactory
  interface Factory {
    fun create(
      @Assisted setupInit: SetupInitData<BabyScaleSetupStep>,
    ): BabyScaleBLESetupViewModel
  }

  private val sku = setupInit.sku

  override fun provideInitialState(): BabyScaleSetupState {
    return BabyScaleSetupState()
  }

  init {
    AppLog.d(TAG, "BabyScaleBLESetupViewModel initialized for SKU: $sku")
    lazyInit()
  }

  private suspend fun saveScale() {
    try {
      val scale = discoveredScale
      if (scale != null) {
        val nickname = state.value.nickname
        val currentTime = Instant.now().toString()
        val updatedScale = scale.copy(
          nickname = nickname,
          deviceType = ScaleSetupType.BabyScale.value,
          sku = sku,
          createdAt = currentTime,
          device = scale.device?.copy(
            deviceName = scale.device.deviceName.ifEmpty { nickname },
          ),
        )
        discoveredScale = updatedScale
        deviceService.saveScale(updatedScale)
        AppLog.i(TAG, "Successfully saved Baby Scale with SKU: $sku")
      } else {
        AppLog.w(TAG, "No discovered Baby Scale to save")
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error saving Baby Scale", e)
    }
  }

  override fun onNext() {
    val currentState = state.value
    AppLog.d(TAG, "Moving to next step from: ${currentState.step}")

    if (currentState.isLastStep) {
      AppLog.d(TAG, "Reached last step, completing setup")
      this.handleIntent(ScaleSetupIntent.ExitSetup(true))
    } else if (currentSetupState.step == BabyScaleSetupStep.SCALE_INFO) {
      if (isPermissionGranted) {
        handleIntent(ScaleSetupIntent.SetNewStep(BabyScaleSetupStep.WAKEUP))
      } else {
        handleIntent(ScaleSetupIntent.SetNewStep(BabyScaleSetupStep.PERMISSIONS))
        permissionAccess()
      }
    } else {
      currentState.nextStep?.let { handleIntent(ScaleSetupIntent.SetNewStep(it)) }
    }
  }

  override suspend fun onSetupFinished() {
    saveScale()
  }

  override fun onBack() {
    val currentState = state.value
    val currentStep = currentState.step
    AppLog.d(TAG, "Moving to previous step from: $currentStep")

    if (currentState.isFirstStep) {
      navigateTo(AppRoute.AccountSettings.AddEditScales)
      return
    }

    // Skip WAKEUP when going back — it auto-advances, so go to PERMISSIONS instead
    if (currentStep == BabyScaleSetupStep.SCALE_NAME) {
      handleIntent(ScaleSetupIntent.SetNewStep(BabyScaleSetupStep.PERMISSIONS))
      return
    }

    val previousStep = currentState.previousStep
    if (previousStep != null) {
      handleIntent(ScaleSetupIntent.SetNewStep(previousStep))
    } else {
      navigateTo(AppRoute.AccountSettings.AddEditScales)
    }
  }

  override fun onSkip() {
    AppLog.d(TAG, "Skipping current step: ${state.value.step}")
    onNext()
  }

  override fun onTryAgain() {
    val currentStep = state.value.step
    AppLog.d(TAG, "Trying again for step: $currentStep")

    when (currentStep) {
      BabyScaleSetupStep.WAKEUP -> wakeUpScale()
      else -> AppLog.w(TAG, "Try again called on unsupported step: $currentStep")
    }
  }

  override fun onStepChange(step: ScaleSetupStep) {
    AppLog.d(TAG, "Step changed to: $step")
    viewModelScope.launch {
      when (step) {
        BabyScaleSetupStep.WAKEUP -> if (MOCK_BLE) mockWakeUpScale() else wakeUpScale()
        else -> AppLog.d(TAG, "No specific action for step: $step")
      }
    }
  }

  // TODO: Remove mock methods and restore real BLE when scale connection is ready
  private fun mockWakeUpScale() {
    AppLog.d(TAG, "Mock: Starting wake up scale process")
    handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Loading))
    discoveredScale = Device(
      device = null,
      deviceType = ScaleSetupType.BabyScale.value,
      sku = sku,
    )
    viewModelScope.launch {
      delay(3000)
      handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Success))
      delay(1000)
      onNext()
    }
  }

  private fun wakeUpScale() {
    AppLog.d(TAG, "Starting wake up scale process")
    handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Loading))
    clearBluetoothTimeout()
    stopObservingDevices()

    bluetoothTimeoutJob = viewModelScope.launch {
      delay(bluetoothTimeout)
      if (discoveredScale == null) {
        AppLog.d(TAG, "Bluetooth scan timeout reached")
        handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
      }
    }

    try {
      AppLog.d(TAG, "Starting device scan for wake up")
      ggDeviceService.scanForPairing()
      startObservingDevices { data ->
        AppLog.d(TAG, "Baby Scale device found: ${data.deviceName}")
        viewModelScope.launch {
          discoveredScale = Device(
            device = data,
            deviceType = ScaleSetupType.BabyScale.value,
            sku = sku,
          )
          clearBluetoothTimeout()
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

  override fun observePermissions() {
    AppLog.d(TAG, "Starting permission observation for Baby Scale setup")
    viewModelScope.launch {
      try {
        subscribePermissions().collect { newPermissions: GGPermissionStatusMap ->
          val areRequiredPermissionsEnabled =
            AppPermissionsHelper.areRequiredPermissionsEnabled(newPermissions, setupType = ScaleSetupType.BabyScale)
          handleIntent(ScaleSetupIntent.SetPermissions(newPermissions))
          if (isPermissionGranted != areRequiredPermissionsEnabled) {
            isPermissionGranted = areRequiredPermissionsEnabled
          }
          if (!areRequiredPermissionsEnabled) {
            if (currentSetupState.step != BabyScaleSetupStep.PERMISSIONS && currentSetupState.step != BabyScaleSetupStep.SCALE_INFO) {
              handleIntent(ScaleSetupIntent.SetNewStep(BabyScaleSetupStep.PERMISSIONS))
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
    private const val TAG = "BabyScaleBLESetupVM"
    // Toggle manually for local dev testing — must be false before merge
    private const val MOCK_BLE = false
  }
}

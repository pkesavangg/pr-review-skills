package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.BuildConfig
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
      } else {
        AppLog.w(TAG, "No discovered Baby Scale to save")
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error saving Baby Scale", e)
    }
  }

  override fun onNext() {
    val currentState = state.value

    if (currentState.isLastStep) {
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
  }

  override fun onBack() {
    val currentState = state.value
    val currentStep = currentState.step

    if (currentState.isFirstStep) {
      navigateTo(AppRoute.AccountSettings.AddEditScales)
      return
    }

    // Skip WAKEUP when going back from SCALE_NAME — go to PERMISSIONS
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
    onNext()
  }

  override fun onTryAgain() {
    when (state.value.step) {
      BabyScaleSetupStep.WAKEUP -> wakeUpScale()
      else -> {}
    }
  }

  override fun onStepChange(step: ScaleSetupStep) {
    viewModelScope.launch {
      when (step) {
        BabyScaleSetupStep.WAKEUP -> if (BuildConfig.DEBUG) mockWakeUpScale() else wakeUpScale()
        else -> AppLog.d(TAG, "No specific action for step: $step")
      }
    }
  }

  /**
   * Starts BLE scan for baby scale. When device is found, saves it and advances to SCALE_NAME.
   * Handles like A6 scale — scan + connect in one step.
   */
  private fun wakeUpScale() {
    handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Loading))
    clearBluetoothTimeout()
    stopObservingDevices()

    bluetoothTimeoutJob = viewModelScope.launch {
      delay(bluetoothTimeout)
      if (discoveredScale == null) {
        handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
      }
    }

    try {
      ggDeviceService.scanForPairing()
      startObservingDevices { data ->
        viewModelScope.launch {
          discoveredScale = Device(
            device = data,
            deviceType = ScaleSetupType.BabyScale.value,
            sku = sku,
          )
          clearBluetoothTimeout()
          saveScale()
          handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Success))
          delay(2000)
          onNext()
        }
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during BLE scan", e)
      clearBluetoothTimeout()
      handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
    }
  }

  override fun observePermissions() {
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
  }
}

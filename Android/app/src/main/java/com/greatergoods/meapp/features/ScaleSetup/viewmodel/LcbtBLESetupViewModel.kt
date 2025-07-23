package com.greatergoods.meapp.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.domain.model.storage.toGGBTDevice
import com.greatergoods.meapp.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.greatergoods.meapp.features.ScaleSetup.enums.ScaleSetupStep
import com.greatergoods.meapp.features.ScaleSetup.modal.ConnectionState
import com.greatergoods.meapp.features.ScaleSetup.modal.SetupInitData
import com.greatergoods.meapp.features.ScaleSetup.reducer.LCBTScaleSetupState
import com.greatergoods.meapp.features.ScaleSetup.reducer.LcbtScaleSetupReducer
import com.greatergoods.meapp.features.ScaleSetup.reducer.ScaleSetupIntent
import com.greatergoods.meapp.features.appPermissions.helper.AppPermissionsHelper
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    val currentState = state.value.scaleSetupState
    val currentSetupState = currentState.setupState
    AppLog.d(TAG, "Moving to next step from: ${currentState.setupState.step}")

    if (currentState.isLastStep) {
      AppLog.d(TAG, "Reached last step, completing setup")
      this.handleIntent(ScaleSetupIntent.ExitSetup(true))
    } else if (currentSetupState.step == LcbtScaleSetupStep.SCALE_INFO && isPermissionGranted) {
      handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.WAKEUP))
    } else {
      AppLog.d(TAG, "After Next intent - new currentStep: ${currentState.setupState.step}")
      if (currentState.nextStep != null)
        handleIntent(ScaleSetupIntent.SetNewStep(currentState.nextStep!!))
    }
  }

  override fun onBack() {
    val currentState = state.value.scaleSetupState
    AppLog.d(TAG, "Moving to previous step from: ${currentState.setupState.step}")

    if (currentState.isFirstStep) {
      AppLog.d(TAG, "At first step, navigating back")
      navigateTo(AppRoute.AccountSettings.AddEditScales)
    } else {
      AppLog.d(TAG, "After Back intent - new currentStep: ${currentState.setupState.step}")
    }
  }

  override fun onSkip() {
    AppLog.d(TAG, "Skipping current step: ${state.value.scaleSetupState.setupState.step}")
    // For now, treat skip as next
    onNext()
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

    // Start collecting device scan responses only now
    AppLog.d(TAG, "Starting wake up scale process")
    if (currentSetupState.connectionState != ConnectionState.Loading) {
      handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Loading))
    }

    viewModelScope.launch {
      try {
        ggDeviceService.scanForPairing()
        startObservingDevices { data ->
          discoveredScale = Device(
            device = data,
            deviceType = ScaleSetupType.Lcbt.value,
            sku = sku,
          )
          onNext()
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during wake up process", e.toString())
        handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.ErrorWithMessage("WAKEUP_002")))
      }
    }
  }

  private fun connectToBluetooth() {
    if (currentSetupState.connectionState != ConnectionState.Loading) {
      handleIntent(
        ScaleSetupIntent.AlterConnectionState(
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
          ScaleSetupIntent.AlterConnectionState(
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
          ScaleSetupIntent.AlterConnectionState(
            ConnectionState.Failed.ErrorWithMessage("BT_002"),
          ),
        )
      }
    }
  }

  override fun observePermissions() {
    viewModelScope.launch {
      subscribePermissions().collect { newPermissions ->
        viewModelScope.launch {
          val areRequiredPermissionsEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(newPermissions, sku)
          ScaleSetupIntent.SetPermissions(newPermissions)
          if (isPermissionGranted != areRequiredPermissionsEnabled) {
            isPermissionGranted = areRequiredPermissionsEnabled
          }
          if (!areRequiredPermissionsEnabled) {
            if (currentSetupState.step != LcbtScaleSetupStep.PERMISSIONS && currentSetupState.step != LcbtScaleSetupStep.SCALE_INFO) {
              handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.PERMISSIONS))
            }
          }
        }
      }
    }
  }
}

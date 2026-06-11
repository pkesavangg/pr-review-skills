package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.ScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.LCBTScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.LcbtScaleSetupReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.components.SetupLoaderTimings
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.time.Instant

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
      val scale = discoveredScale
      if (scale != null) {
        val scaleInfo = state.value.scaleSetupState.scaleInfo
        val currentTime = Instant.now().toString()
        val updatedScale = scale.copy(
          nickname = scaleInfo?.productName ?: "Bluetooth Smart Scale",
          deviceType = ScaleSetupType.Lcbt.value,
          sku = sku,
          createdAt = currentTime,
          device = scale.device?.copy(
            deviceName = scale.device.deviceName.ifEmpty { scaleInfo?.productName ?: "" },
          ),
        )
        discoveredScale = updatedScale
        deviceService.saveScale(updatedScale)
        AppLog.i(TAG, "Successfully saved LCBT scale with SKU: $sku")
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
      currentState.nextStep?.let { handleIntent(ScaleSetupIntent.SetNewStep(it)) }
    }
  }

  /** Surface the My Weight dashboard after adding a scale (MOB-422). */
  override fun productSelectionAfterSetup(): ProductSelection = ProductSelection.MyWeight

  override suspend fun onSetupFinished() {
  }

  override fun onBack() {
    val currentState = state.value
    val currentStep = currentState.step
    AppLog.d(TAG, "Moving to previous step from: $currentStep")

    if (currentState.isFirstStep) {
      AppLog.d(TAG, "At first step, navigating back to My Devices")
      navigateTo(AppRoute.AccountSettings.MyDevices)
      return
    }

    // Otherwise, go to previous step
    val previousStep = currentState.previousStep
    if (previousStep != null) {
      AppLog.d(TAG, "Navigating to previous step: $previousStep")
      handleIntent(ScaleSetupIntent.SetNewStep(previousStep))
    } else {
      AppLog.d(TAG, "No previous step available, navigating back to My Devices")
      navigateTo(AppRoute.AccountSettings.MyDevices)
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
        AppLog.d(TAG, "Waiting ${CONNECTION_SETUP_DELAY_MS}ms for bluetooth connection to settle")
        delay(CONNECTION_SETUP_DELAY_MS)
        clearBluetoothTimeout() // Cancel timeout on success
        AppLog.d(TAG, "Bluetooth setup delay elapsed without timeout, saving scale")
        saveScale()
        handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Success))
        AppLog.d(TAG, "Holding success state for ${SetupLoaderTimings.SUCCESS_DISPLAY_MS}ms to let loader animation complete")
        delay(SetupLoaderTimings.SUCCESS_DISPLAY_MS)
        // Honour cancellation if the user backed out during the success hold.
        currentCoroutineContext().ensureActive()
        // Guard against a late BLE callback flipping the state to Failed during
        // the success hold — only auto-advance if we're still in Success.
        val finalState = state.value.scaleSetupState.setupState.connectionState
        if (finalState !is ConnectionState.Success) {
          AppLog.w(TAG, "Connection state changed during success hold to $finalState — skipping auto-advance")
          return@launch
        }
        AppLog.d(TAG, "Success state displayed, advancing to next step")
        onNext()
      } catch (e: CancellationException) {
        // Never swallow cancellation — let viewModelScope unwind cleanly.
        throw e
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

    /**
     * Delay between starting the BLE connection flow and marking it successful.
     * Gives the underlying stack time to settle before we save the scale and
     * transition the UI to Success.
     */
    private const val CONNECTION_SETUP_DELAY_MS = 3000L
  }
}

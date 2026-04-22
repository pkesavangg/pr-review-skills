package com.dmdbrands.gurus.weight.features.scaleMode.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.device.toR4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.model.storage.toGGDevicePreference
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.scaleMode.reducer.ScaleModeIntent
import com.dmdbrands.gurus.weight.features.scaleMode.reducer.ScaleModeReducer
import com.dmdbrands.gurus.weight.features.scaleMode.reducer.ScaleModeState
import com.dmdbrands.gurus.weight.features.scaleMode.strings.ScaleModeStrings
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTDevice
import com.greatergoods.blewrapper.GGDeviceService
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltViewModel(
  assistedFactory = ScaleModeViewModel.Factory::class,
)
class ScaleModeViewModel
@AssistedInject
constructor(
  private val deviceService: IDeviceService,
  private val ggDeviceService: GGDeviceService,
  @Assisted val scaleId: String,
) : BaseIntentViewModel<ScaleModeState, ScaleModeIntent>(
  reducer = ScaleModeReducer(),
) {
  @AssistedFactory
  interface Factory {
    fun create(scaleId: String): ScaleModeViewModel
  }

  companion object {
    private const val MAX_RETRY_COUNT = 2
    private const val RETRY_DELAY_MS = 2000L // 2 seconds
    private const val TAG = "ScaleModeViewModel"
  }

  private var retryCount = 0

  /**
   * Exception thrown when account update fails.
   */
  private class UpdateAccountException(message: String) : Exception(message)

  /**
   * Exception thrown when the scale is busy (user selection in progress) and cannot accept updates.
   */
  private class ScaleBusyException : Exception("Scale is busy: user selection in progress")

  override fun provideInitialState(): ScaleModeState = ScaleModeState()

  override fun handleIntent(intent: ScaleModeIntent) {
    AppLog.d(TAG, "Handling scale mode intent: ${intent::class.simpleName}")
    super.handleIntent(intent)
    when (intent) {
      ScaleModeIntent.Back -> onBack()
      ScaleModeIntent.Save -> saveModeSettings()
      ScaleModeIntent.OpenBiaModal -> openBiaModel()
      else -> {}
    }
  }

  init {
    AppLog.d(TAG, "ScaleModeViewModel initialized for scaleId: $scaleId")
    initScaleMode()
  }

  private fun initScaleMode() {
    AppLog.d(TAG, "Initializing scale mode for scaleId: $scaleId")
    viewModelScope.launch {
      try {
        deviceService.pairedScales.collect { devices ->
          // Avoid overwriting local toggles while the user is making changes or saving.
          if (state.value.hasModeChanged) {
            AppLog.d(TAG, "Skipping remote scale update because mode change is in progress")
            return@collect
          }
          val device = devices.find { it.id == scaleId }
          device?.let { scaleDevice ->
            AppLog.d(TAG, "Setting scale device: ${scaleDevice.id}")
            handleIntent(ScaleModeIntent.SetScale(scaleDevice))
            val shouldMeasureImpedance = scaleDevice.preferences?.shouldMeasureImpedance == true
            val shouldMeasurePulse = scaleDevice.preferences?.shouldMeasurePulse == true
            AppLog.d(TAG, "Setting mode - Impedance: $shouldMeasureImpedance, Pulse: $shouldMeasurePulse")
            handleIntent(ScaleModeIntent.SetMode(shouldMeasureImpedance, false))
            handleIntent(ScaleModeIntent.SetHeartRate(shouldMeasurePulse, false))
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error initializing scale mode", e)
      }
    }
  }

  /**
   * Updates the scale account via BLE service and waits for completion.
   *
   * @param updatedScale The updated scale device to send to the BLE service.
   * @throws ScaleBusyException if the scale is actively in a user selection session.
   * @throws UpdateAccountException if the update fails for any other reason.
   */
  private suspend fun updateAccountAsync(updatedScale: GGBTDevice) {
    suspendCancellableCoroutine<Unit> { continuation ->
      ggDeviceService.updateAccount(updatedScale) { responseType ->
        when (responseType) {
          GGUserActionResponseType.CREATION_COMPLETED,
          GGUserActionResponseType.UPDATE_COMPLETED -> {
            continuation.resume(Unit)
          }
          GGUserActionResponseType.USER_SELECTION_IN_PROGRESS -> {
            continuation.resumeWithException(ScaleBusyException())
          }
          else -> {
            continuation.resumeWithException(
              UpdateAccountException("Account update failed with response: $responseType"),
            )
          }
        }
      }
    }
  }

  /**
   * Shows an alert dialog when account update fails, allowing the user to retry.
   *
   * @param onRetry Callback function to execute when user chooses to retry.
   */
  private fun updateAccountFailedAlert(onRetry: () -> Unit) {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = ScaleModeStrings.UpdateAccountFailedAlert.Title,
        message = ScaleModeStrings.UpdateAccountFailedAlert.Message,
        confirmText = ScaleModeStrings.UpdateAccountFailedAlert.Retry,
        cancelText = ScaleModeStrings.UpdateAccountFailedAlert.Cancel,
        onConfirm = onRetry,
        onCancel = null,
      ),
    )
  }

  private fun scaleBusyAlert() {
    dialogQueueService.enqueue(
      DialogModel.Alert(
        title = ScaleModeStrings.ScaleBusyAlert.Title,
        message = ScaleModeStrings.ScaleBusyAlert.Message,
        onDismiss = { dialogQueueService.dismissCurrent() },
      ),
    )
  }

  private fun openBiaModel() {
    AppLog.d(TAG, "Opening BIA modal")
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.BiaModal,
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
      ),
    )
  }

  private fun saveModeSettings() {
    AppLog.d(TAG, "Saving mode settings")
    dialogQueueService.showLoader(
      message = ScaleModeStrings.LoaderMessage,
    )
    val currentState = state.value
    val scale = currentState.scale
    if (scale == null) {
      AppLog.w(TAG, "No scale found for saving mode settings")
      showToast(ScaleModeStrings.Toast.Error)
      return
    }

    AppLog.d(TAG, "Saving mode settings for scale: ${scale.id}")
    AppLog.d(
      TAG,
      "Current settings - All body metrics: ${currentState.isAllBodyMetrics}, Heart rate: ${currentState.isHeartRateOn}",
    )

    viewModelScope.launch {
      try {
        // Build updated displayMetrics based on heart rate setting
        val updatedDisplayMetrics =
          updateDisplayMetricsForHeartRate(
            currentMetrics = scale.preferences?.displayMetrics ?: emptyList(),
            shouldIncludeHeartRate = currentState.isHeartRateOn && currentState.isAllBodyMetrics,
          )

        AppLog.d(TAG, "Updated display metrics count: ${updatedDisplayMetrics.size}")

        // Create R4ScalePreferenceApiModel with updated values
        val preferences = requireNotNull(
          scale.preferences?.toR4ScalePreferenceApiModel()?.copy(
            shouldMeasureImpedance = currentState.isAllBodyMetrics,
            shouldMeasurePulse = currentState.isHeartRateOn && currentState.isAllBodyMetrics,
            displayMetrics = updatedDisplayMetrics,
          )
        ) { "Scale preferences are null; cannot update scale mode" }
        val updatedScalePreference = scale.preferences.copy(
          shouldMeasureImpedance = currentState.isAllBodyMetrics,
          shouldMeasurePulse = currentState.isHeartRateOn && currentState.isAllBodyMetrics,
          displayMetrics = updatedDisplayMetrics,
        ).toGGDevicePreference()
        val updatedScale = scale.toGGBTDevice().copy(preference = updatedScalePreference)
        AppLog.d(
          TAG,
          "Created preferences - Impedance: ${preferences.shouldMeasureImpedance}, Pulse: ${preferences.shouldMeasurePulse}",
        )

        // Update scale via BLE service if connected, otherwise skip
        if (scale.connectionStatus == BLEStatus.CONNECTED) {
          updateAccountAsync(updatedScale)
        }

        // Update scale preferences via API
        val success = deviceService.updateScalePreferences(scaleId, preferences)
        if (success) {
          retryCount = 0
          AppLog.i(TAG, "Successfully saved mode settings for scale: $scaleId")
          // Refresh scale data to get updated preferences
          dialogQueueService.dismissLoader()
          navigateBack()
          showToast(ScaleModeStrings.Toast.Success)
        } else {
          AppLog.w(TAG, "Failed to save mode settings for scale: $scaleId")
          showToast(ScaleModeStrings.Toast.Error)
          dialogQueueService.dismissLoader()
        }
      } catch (err: ScaleBusyException) {
        AppLog.w(TAG, "Scale is busy (user selection in progress); cannot save mode settings now")
        retryCount = 0
        dialogQueueService.dismissLoader()
        scaleBusyAlert()
      } catch (err: Exception) {
        AppLog.e(TAG, "Error saving mode settings", err)
        if (retryCount < MAX_RETRY_COUNT) {
          delay(RETRY_DELAY_MS)
          retryCount++
          saveModeSettings()
        } else {
          retryCount = 0
          dialogQueueService.dismissLoader()
          updateAccountFailedAlert { saveModeSettings() }
        }
      }
    }
  }

  private fun updateDisplayMetricsForHeartRate(
    currentMetrics: List<String>,
    shouldIncludeHeartRate: Boolean,
  ): List<String> {
    AppLog.d(
      TAG,
      "Updating display metrics for heart rate - Current: ${currentMetrics.size}, Include heart rate: $shouldIncludeHeartRate",
    )
    val mutableMetrics = currentMetrics.toMutableList()
    val heartRateMetric = "heartRate"
    val goalMetrics = listOf("bodyFat", "muscleMass", "boneMass", "bodyWater") // Common goal metrics

    if (shouldIncludeHeartRate && !mutableMetrics.contains(heartRateMetric)) {
      // Add heart rate - insert before goal metrics if possible, otherwise append
      val insertIndex = mutableMetrics.indexOfFirst { goalMetrics.contains(it) }
      if (insertIndex != -1) {
        AppLog.d(TAG, "Adding heart rate metric before goal metrics at index: $insertIndex")
        mutableMetrics.add(insertIndex, heartRateMetric)
      } else {
        AppLog.d(TAG, "Adding heart rate metric at end")
        mutableMetrics.add(heartRateMetric)
      }
    } else if (!shouldIncludeHeartRate && mutableMetrics.contains(heartRateMetric)) {
      // Remove heart rate
      AppLog.d(TAG, "Removing heart rate metric")
      mutableMetrics.remove(heartRateMetric)
    } else {
      AppLog.d(TAG, "No changes needed for heart rate metric")
    }

    AppLog.d(TAG, "Updated metrics count: ${mutableMetrics.size}")
    return mutableMetrics
  }

  private fun onBack() {
    if (state.value.hasModeChanged) {
      AppLog.d(TAG, "Mode has changed, showing unsaved changes dialog")
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = ScaleModeStrings.UnsavedChanges.Title,
          message = ScaleModeStrings.UnsavedChanges.Message,
          confirmText = ScaleModeStrings.UnsavedChanges.Leave,
          cancelText = ScaleModeStrings.UnsavedChanges.Cancel,
          onConfirm = {
            AppLog.d(TAG, "User confirmed leaving with unsaved changes")
            navigateBack()
            initScaleMode()
          },
        ),
      )
    } else {
      AppLog.d(TAG, "No changes detected, navigating back directly")
      navigateBack()
    }
  }

  private fun navigateBack() {
    viewModelScope.launch {
      try {
        navigationService.navigateBack()
        AppLog.d(TAG, "Successfully navigated back")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error navigating back", e)
      }
    }
  }

  private fun showToast(message: String) {
    AppLog.d(TAG, "Showing toast: $message")
    dialogQueueService.showToast(
      Toast.Simple(
        title = null,
        message = message,
        action = null,
      ),
    )
  }
}

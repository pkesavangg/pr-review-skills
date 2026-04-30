package com.dmdbrands.gurus.weight.features.scaleDisplayMetrics.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.device.toR4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.model.storage.toGGDevicePreference
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.scaleDisplayMetrics.reducer.ScaleDisplayMetricsIntent
import com.dmdbrands.gurus.weight.features.scaleDisplayMetrics.reducer.ScaleDisplayMetricsReducer
import com.dmdbrands.gurus.weight.features.scaleDisplayMetrics.reducer.ScaleDisplayMetricsState
import com.dmdbrands.gurus.weight.features.scaleDisplayMetrics.strings.ScaleDisplayMetricsStrings
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
  assistedFactory = ScaleDisplayMetricsViewModel.Factory::class,
)
class ScaleDisplayMetricsViewModel
@AssistedInject
constructor(
  private val deviceService: IDeviceService,
  private val ggDeviceService: GGDeviceService,
  @Assisted val scaleId: String,
) : BaseIntentViewModel<ScaleDisplayMetricsState, ScaleDisplayMetricsIntent>(
  reducer = ScaleDisplayMetricsReducer(),
) {
  @AssistedFactory
  interface Factory {
    fun create(scaleId: String): ScaleDisplayMetricsViewModel
  }

  companion object {
    private const val MAX_RETRY_COUNT = 2
    private const val RETRY_DELAY_MS = 2000L // 2 seconds
  }

  private var retryCount = 0

  /**
   * Exception thrown when account update fails.
   */
  private class UpdateAccountException(message: String) : Exception(message)

  override fun provideInitialState(): ScaleDisplayMetricsState = ScaleDisplayMetricsState()

  override fun handleIntent(intent: ScaleDisplayMetricsIntent) {
    super.handleIntent(intent)
    when (intent) {
      ScaleDisplayMetricsIntent.Back -> onBack()
      ScaleDisplayMetricsIntent.Save -> saveDisplayMetrics()
      ScaleDisplayMetricsIntent.UpdateScaleMode -> onUpdateScaleMode()
      else -> {}
    }
  }

  init {
    initScaleDisplayMetrics()
  }

  private fun initScaleDisplayMetrics() {
    viewModelScope.launch {
      deviceService.pairedScales.collect { devices ->
        val device = devices.find { it.id == scaleId }
        device?.let { scaleDevice ->
          handleIntent(ScaleDisplayMetricsIntent.SetScale(scaleDevice))
        }
      }
    }
  }

  /**
   * Updates the scale account via BLE service and waits for completion.
   * Throws UpdateAccountException if the update fails.
   *
   * @param updatedScale The updated scale device to send to the BLE service.
   * @throws UpdateAccountException if the update fails.
   */
  private suspend fun updateAccountAsync(updatedScale: GGBTDevice) {
    suspendCancellableCoroutine<Unit> { continuation ->
      ggDeviceService.updateAccount(updatedScale) { responseType ->
        when (responseType) {
          GGUserActionResponseType.CREATION_COMPLETED,
          GGUserActionResponseType.UPDATE_COMPLETED -> {
            continuation.resume(Unit)
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

  private fun saveDisplayMetrics() {
    val currentState = state.value
    val scale = currentState.scale
    if (scale == null) {
      showToast(ScaleDisplayMetricsStrings.Toast.Error)
      return
    }
    viewModelScope.launch {
      try {
        dialogQueueService.showLoader(message = ScaleDisplayMetricsStrings.LoaderMessage)

        // Create updated preferences with new display metrics
        val preferences =
          scale.preferences?.toR4ScalePreferenceApiModel()?.copy(
            displayMetrics = currentState.enabledMetrics,
          )!!

        val updatedScalePreference = scale.preferences.copy(
          displayMetrics = currentState.enabledMetrics,
        ).toGGDevicePreference()
        val updatedScale = scale.toGGBTDevice().copy(preference = updatedScalePreference)

        // Update scale via BLE service if connected, otherwise skip
        if (scale.connectionStatus == BLEStatus.CONNECTED) {
          updateAccountAsync(updatedScale)
        }

        // Update scale preferences via API
        val success = deviceService.updateScalePreferences(scaleId, preferences)
        if (success) {
          retryCount = 0
          dialogQueueService.dismissLoader()
          deviceService.syncDevices()
          delay(1000)
          showToast(ScaleDisplayMetricsStrings.Toast.Success)
          navigateBack()
        } else {
          showToast(ScaleDisplayMetricsStrings.Toast.Error)
          dialogQueueService.dismissLoader()
        }
      } catch (err: Exception) {
        AppLog.e("ScaleDisplayMetricsViewModel", "Failed to save display metrics", err)
        if (retryCount < MAX_RETRY_COUNT) {
          delay(RETRY_DELAY_MS)
          retryCount++
          saveDisplayMetrics()
        } else {
          retryCount = 0
          dialogQueueService.dismissLoader()
          updateAccountFailedAlert { saveDisplayMetrics() }
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
        title = ScaleDisplayMetricsStrings.UpdateAccountFailedAlert.Title,
        message = ScaleDisplayMetricsStrings.UpdateAccountFailedAlert.Message,
        confirmText = ScaleDisplayMetricsStrings.UpdateAccountFailedAlert.Retry,
        cancelText = ScaleDisplayMetricsStrings.UpdateAccountFailedAlert.Cancel,
        onConfirm = onRetry,
        onCancel = null,
      ),
    )
  }

  private fun onBack() {
    if (state.value.hasUpdated) {
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = AppPopupStrings.UnsavedExitPopup.Title,
          message = AppPopupStrings.UnsavedExitPopup.Message,
          confirmText = AppPopupStrings.UnsavedExitPopup.Leave,
          cancelText = AppPopupStrings.UnsavedExitPopup.Cancel,
          onConfirm = {
            navigateBack()
            initScaleDisplayMetrics()
          },
        ),
      )
    } else {
      navigateBack()
    }
  }

  private fun onUpdateScaleMode() {
    viewModelScope.launch {
      navigationService.navigateTo(AppRoute.ScaleDetails.ScaleMode(scaleId))
    }
  }

  private fun navigateBack() {
    viewModelScope.launch {
      navigationService.navigateBack()
    }
  }

  private fun showToast(message: String) {
    dialogQueueService.showToast(
      Toast(
        title = null,
        message = message,
        action = null,
      ),
    )
  }
}

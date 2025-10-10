package com.dmdbrands.gurus.weight.features.scaleDisplayMetrics.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.device.toR4ScalePreferenceApiModel
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
import com.greatergoods.blewrapper.GGDeviceService
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

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

        // Update scale preferences via API and BLE service
        ggDeviceService.updateAccount(
          updatedScale,
        ) {
          when (it) {
            GGUserActionResponseType.CREATION_COMPLETED, GGUserActionResponseType.UPDATE_COMPLETED -> {
              viewModelScope.launch {
                val success = deviceService.updateScalePreferences(scaleId, preferences)
                if (success) {
                  dialogQueueService.dismissLoader()
                  deviceService.syncDevices()
                  showToast(ScaleDisplayMetricsStrings.Toast.Success)
                  navigateBack()
                } else {
                  showToast(ScaleDisplayMetricsStrings.Toast.Error)
                  dialogQueueService.dismissLoader()
                }
              }
            }

            else -> {
              showToast(ScaleDisplayMetricsStrings.Toast.Error)
              dialogQueueService.dismissLoader()
            }
          }
        }
      } catch (err: Exception) {
        AppLog.e("ScaleDisplayMetricsViewModel", "Failed to save display metrics", err)
        dialogQueueService.dismissLoader()
        showToast(ScaleDisplayMetricsStrings.Toast.Error)
      }
    }
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

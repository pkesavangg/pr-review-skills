package com.greatergoods.meapp.features.scaleDisplayMetrics.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.model.api.device.toR4ScalePreferenceApiModel
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.scaleDisplayMetrics.reducer.ScaleDisplayMetricsIntent
import com.greatergoods.meapp.features.scaleDisplayMetrics.reducer.ScaleDisplayMetricsReducer
import com.greatergoods.meapp.features.scaleDisplayMetrics.reducer.ScaleDisplayMetricsState
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
        ScaleDisplayMetricsIntent.Back -> navigateBack()
        ScaleDisplayMetricsIntent.Save -> saveDisplayMetrics()
        else -> {}
      }
    }

    init {
      initScaleDisplayMetrics()
    }

    private fun initScaleDisplayMetrics() {
      viewModelScope.launch {
        deviceService.savedScales.collect { devices ->
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
        // TODO: Show error toast
        return
      }
      viewModelScope.launch {
        try {
          // TODO: Show loading dialog
          // dialogQueueService.showLoader(message = "Saving...")

          // Create updated preferences with new display metrics
          val preferences =
            scale.toR4ScalePreferenceApiModel().copy(
              displayMetrics = currentState.enabledMetrics,
            )

          // Update scale preferences via API
          val success = deviceService.updateScalePreferences(scaleId, preferences)

          if (success) {
            // TODO: Dismiss loader and show success toast
            // dialogQueueService.dismissLoader()
            // showToast("Display metrics updated successfully.")

            // Refresh scale data to get updated preferences
            deviceService.syncScales()
            navigateBack()
          } else {
            // TODO: Show error toast
            // showToast("Error updating display metrics.")
          }
        } catch (err: Exception) {
          // TODO: Log error and show error toast
          // Log.e("ScaleDisplayMetricsViewModel", "Error saving display metrics", err)
          // dialogQueueService.dismissLoader()
          // showToast("Error updating display metrics.")
        }
      }
    }

    private fun navigateBack() {
      viewModelScope.launch {
        navigationService.navigateBack()
      }
    }
  }

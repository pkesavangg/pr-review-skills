package com.greatergoods.meapp.features.scaleDisplayMetrics.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.domain.model.api.device.toR4ScalePreferenceApiModel
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.common.strings.AppPopupStrings
import com.greatergoods.meapp.features.scaleDisplayMetrics.reducer.ScaleDisplayMetricsIntent
import com.greatergoods.meapp.features.scaleDisplayMetrics.reducer.ScaleDisplayMetricsReducer
import com.greatergoods.meapp.features.scaleDisplayMetrics.reducer.ScaleDisplayMetricsState
import com.greatergoods.meapp.features.scaleDisplayMetrics.strings.ScaleDisplayMetricsStrings
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
        showToast(ScaleDisplayMetricsStrings.Toast.Error)
        return
      }
      viewModelScope.launch {
        try {
          dialogQueueService.showLoader(message = ScaleDisplayMetricsStrings.LoaderMessage)

          // Create updated preferences with new display metrics
          val preferences =
            scale.toR4ScalePreferenceApiModel().copy(
              displayMetrics = currentState.enabledMetrics,
            )

          // Update scale preferences via API
          val success = deviceService.updateScalePreferences(scaleId, preferences)

          if (success) {
            dialogQueueService.dismissLoader()
            deviceService.syncScales()
            showToast(ScaleDisplayMetricsStrings.Toast.Success)
            navigateBack()
          } else {
            showToast(ScaleDisplayMetricsStrings.Toast.Error)
          }
        } catch (err: Exception) {
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

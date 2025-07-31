package com.dmdbrands.gurus.weight.features.scaleMode.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.domain.model.api.device.toR4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.scaleMode.reducer.ScaleModeIntent
import com.dmdbrands.gurus.weight.features.scaleMode.reducer.ScaleModeReducer
import com.dmdbrands.gurus.weight.features.scaleMode.reducer.ScaleModeState
import com.dmdbrands.gurus.weight.features.scaleMode.strings.ScaleModeStrings
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import android.util.Log

@HiltViewModel(
  assistedFactory = ScaleModeViewModel.Factory::class,
)
class ScaleModeViewModel
@AssistedInject
constructor(
  private val deviceService: IDeviceService,
  @Assisted val scaleId: String,
) : BaseIntentViewModel<ScaleModeState, ScaleModeIntent>(
  reducer = ScaleModeReducer(),
) {
  @AssistedFactory
  interface Factory {
    fun create(scaleId: String): ScaleModeViewModel
  }

  override fun provideInitialState(): ScaleModeState = ScaleModeState()

  override fun handleIntent(intent: ScaleModeIntent) {
    super.handleIntent(intent)
    when (intent) {
      ScaleModeIntent.Back -> onBack()
      ScaleModeIntent.Save -> saveModeSettings()
      ScaleModeIntent.OpenBiaModal -> openBiaModel()
      else -> {}
    }
  }

  init {
    initScaleMode()
  }

  private fun initScaleMode() {
    viewModelScope.launch {
      deviceService.pairedScales.collect { devices ->
        val device = devices.find { it.id == scaleId }
        Log.d("modee", device.toString())
        device?.let { scaleDevice ->
          handleIntent(ScaleModeIntent.SetScale(scaleDevice))
          handleIntent(ScaleModeIntent.SetMode(scaleDevice.preferences?.shouldMeasureImpedance == true, false))
          handleIntent(ScaleModeIntent.SetHeartRate(scaleDevice.preferences?.shouldMeasurePulse == true, false))
        }
      }
    }
  }

  private fun openBiaModel() {
    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.BiaModal,
      ),
    )
  }

  private fun saveModeSettings() {
    dialogQueueService.showLoader(
      message = ScaleModeStrings.LoaderMessage,
    )
    val currentState = state.value
    val scale = currentState.scale
    if (scale == null) {
      showToast(ScaleModeStrings.Toast.Error)
      return
    }
    viewModelScope.launch {
      try {
        // Build updated displayMetrics based on heart rate setting
        val updatedDisplayMetrics =
          updateDisplayMetricsForHeartRate(
            currentMetrics = scale.preferences?.displayMetrics ?: emptyList(),
            shouldIncludeHeartRate = currentState.isHeartRateOn && currentState.isAllBodyMetrics,
          )

        // Create R4ScalePreferenceApiModel with updated values
        val preferences =
          scale.preferences?.toR4ScalePreferenceApiModel()?.copy(
            shouldMeasureImpedance = currentState.isAllBodyMetrics,
            shouldMeasurePulse = currentState.isHeartRateOn && currentState.isAllBodyMetrics,
            displayMetrics = updatedDisplayMetrics,
          )!!

        // Update scale preferences via API
        val success = deviceService.updateScalePreferences(scaleId, preferences)

        if (success) {
          dialogQueueService.dismissLoader()
          showToast(ScaleModeStrings.Toast.Success)
          // Refresh scale data to get updated preferences
          deviceService.syncDevices()
          navigateBack()
        } else {
          showToast(ScaleModeStrings.Toast.Error)
        }
      } catch (err: Exception) {
        Log.e("ScaleModeViewModel", "Error saving mode settings", err)
        dialogQueueService.dismissLoader()
        showToast(ScaleModeStrings.Toast.Error)
      }
    }
  }

  private fun updateDisplayMetricsForHeartRate(
    currentMetrics: List<String>,
    shouldIncludeHeartRate: Boolean,
  ): List<String> {
    val mutableMetrics = currentMetrics.toMutableList()
    val heartRateMetric = "heartRate"
    val goalMetrics = listOf("bodyFat", "muscleMass", "boneMass", "bodyWater") // Common goal metrics

    if (shouldIncludeHeartRate && !mutableMetrics.contains(heartRateMetric)) {
      // Add heart rate - insert before goal metrics if possible, otherwise append
      val insertIndex = mutableMetrics.indexOfFirst { goalMetrics.contains(it) }
      if (insertIndex != -1) {
        mutableMetrics.add(insertIndex, heartRateMetric)
      } else {
        mutableMetrics.add(heartRateMetric)
      }
    } else if (!shouldIncludeHeartRate && mutableMetrics.contains(heartRateMetric)) {
      // Remove heart rate
      mutableMetrics.remove(heartRateMetric)
    }

    return mutableMetrics
  }

  private fun onBack() {
    if (state.value.hasModeChanged) {
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = ScaleModeStrings.UnsavedChanges.Title,
          message = ScaleModeStrings.UnsavedChanges.Message,
          confirmText = ScaleModeStrings.UnsavedChanges.Leave,
          cancelText = ScaleModeStrings.UnsavedChanges.Cancel,
          onConfirm = {
            navigateBack()
            initScaleMode()
          },
        ),
      )
    } else {
      navigateBack()
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

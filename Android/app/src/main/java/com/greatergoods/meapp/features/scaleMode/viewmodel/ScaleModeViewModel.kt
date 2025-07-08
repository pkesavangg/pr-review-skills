package com.greatergoods.meapp.features.scaleMode.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.scaleMode.reducer.ScaleModeIntent
import com.greatergoods.meapp.features.scaleMode.reducer.ScaleModeReducer
import com.greatergoods.meapp.features.scaleMode.reducer.ScaleModeState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

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
                ScaleModeIntent.Back -> navigateBack()
                ScaleModeIntent.Save -> saveSettings()
                ScaleModeIntent.OpenBiaModal -> openBiaModel()
                else -> {}
            }
        }

        init {
            observeScale()
        }

        private fun observeScale() {
            viewModelScope.launch {
                deviceService.savedScales.collect { devices ->
                    val device = devices.find { it.id == scaleId }
                    device?.let { scaleDevice ->
                        handleIntent(ScaleModeIntent.SetScale(scaleDevice))
                        handleIntent(ScaleModeIntent.SetMode(scaleDevice.shouldMeasureImpedance))
                        handleIntent(ScaleModeIntent.SetHeartRate(scaleDevice.shouldMeasurePulse))
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

        private fun saveSettings() {
            // TODO: Implement save logic (update device settings via DeviceService)
        }

        private fun navigateBack() {
            viewModelScope.launch {
                navigationService.navigateBack()
            }
        }
    }

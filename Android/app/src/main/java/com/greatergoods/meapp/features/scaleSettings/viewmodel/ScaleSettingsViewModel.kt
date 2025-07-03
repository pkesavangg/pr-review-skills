package com.greatergoods.meapp.features.scaleSettings.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.scaleSettings.reducer.ScaleSettingsIntent
import com.greatergoods.meapp.features.scaleSettings.reducer.ScaleSettingsReducer
import com.greatergoods.meapp.features.scaleSettings.reducer.ScaleSettingsState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel for the ScaleSettings screen. Handles scale settings logic and navigation.
 */
@HiltViewModel(
    assistedFactory = ScaleSettingsViewModel.Factory::class,
)
class ScaleSettingsViewModel
    @AssistedInject
    constructor(
        private val deviceService: IDeviceService,
        @Assisted val broadcastId: String,
    ) : BaseIntentViewModel<ScaleSettingsState, ScaleSettingsIntent>(
            reducer = ScaleSettingsReducer(),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(broadcastId: String): ScaleSettingsViewModel
        }

        override fun provideInitialState(): ScaleSettingsState = ScaleSettingsState()

        override fun handleIntent(intent: ScaleSettingsIntent) {
            super.handleIntent(intent)
            when (intent) {
                ScaleSettingsIntent.EditName -> {
                    // TODO: Handle edit name
                }

                ScaleSettingsIntent.DeleteScale -> {
                    // TODO: Handle delete scale
                }

                ScaleSettingsIntent.OpenProductGuide -> {
                    // TODO: Handle open product guide
                }

                ScaleSettingsIntent.Back -> {
                    navigateBack()
                }

                else -> {}
            }
        }

        init {
            setScaleDetails()
        }

        private fun setScaleDetails() {
            viewModelScope.launch {
                deviceService.savedScales.collect { devices ->
                    val device = devices.find { it.broadcastId == broadcastId }
                    device?.let { scaleDevice ->
                        handleIntent(ScaleSettingsIntent.SetScaleInfo(scaleDevice))
                    }
                }
            }
        }

        private fun navigateBack() {
            viewModelScope.launch {
                navigationService.navigateBack()
            }
        }
    }

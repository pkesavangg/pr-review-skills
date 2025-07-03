package com.greatergoods.meapp.features.scaleSettings.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.features.scaleSettings.viewmodel.ScaleSettingsIntent
import com.greatergoods.meapp.features.scaleSettings.viewmodel.ScaleSettingsReducer
import com.greatergoods.meapp.features.scaleSettings.viewmodel.ScaleSettingsState
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ScaleSettingsViewModel @Inject constructor(
) : BaseIntentViewModel<ScaleSettingsState, ScaleSettingsIntent>(ScaleSettingsReducer()) {
    override fun provideInitialState(): ScaleSettingsState = ScaleSettingsState()
    override fun handleIntent(intent: ScaleSettingsIntent) {
        super.handleIntent(intent)
        // No business logic for now
    }
} 
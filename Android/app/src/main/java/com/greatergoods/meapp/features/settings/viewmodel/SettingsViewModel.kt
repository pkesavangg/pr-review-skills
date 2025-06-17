package com.greatergoods.meapp.features.settings.viewmodel

import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for the settings feature, managing state and handling settings intents.
 *
 * (Add service dependencies as needed.)
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    // Inject services here as needed
) : BaseIntentViewModel<SettingsState, SettingsIntent>(
    reducer = SettingsReducer(),
) {
    override fun provideInitialState(): SettingsState {
        return SettingsState() // Provide the initial state for settings
    }

    init {
        handleIntent(SettingsIntent.LoadSettings)
        // Add loading logic as needed
    }
}

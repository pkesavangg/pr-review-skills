package com.greatergoods.meapp.features.settings.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer

/**
 * UI state for the settings feature, holding loading state and errors.
 *
 * @property isLoading Whether data is currently loading.
 * @property errorMessage Error message if any error occurs.
 */
data class SettingsState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // Add more settings fields as needed
) : IReducer.State

/**
 * Intent for settings actions, such as loading and updating settings.
 */
sealed interface SettingsIntent : IReducer.Intent {
    object LoadSettings : SettingsIntent
    data class SetError(val message: String) : SettingsIntent
    object ClearError : SettingsIntent
    // Add more intents as needed for updating settings
}

/**
 * Reducer for the settings state, handling intents to update settings and errors.
 */
class SettingsReducer : IReducer<SettingsState, SettingsIntent> {
    override fun reduce(state: SettingsState, intent: SettingsIntent): SettingsState? = when (intent) {
        is SettingsIntent.SetError -> state.copy(errorMessage = intent.message, isLoading = false)
        SettingsIntent.ClearError -> state.copy(errorMessage = null)
        SettingsIntent.LoadSettings -> state.copy(isLoading = true)
        // Add more intent handling as needed
    }
}

package com.greatergoods.meapp.features.settings.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.Account.Account

// TODO: MyAccountsReducer and related state/intent may be implemented for MyAccountsScreen if needed, following the same pattern.

/**
 * UI state for the settings feature, holding loading state and errors.
 *
 * @property isLoading Whether data is currently loading.
 * @property errorMessage Error message if any error occurs.
 */
data class SettingsState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val account: Account? = null,
) : IReducer.State

/**
 * Intent for settings actions, such as loading and updating settings.
 */
sealed interface SettingsIntent : IReducer.Intent {
    object LoadSettings : SettingsIntent
    object ExportData : SettingsIntent

    data class SetError(
        val message: String,
    ) : SettingsIntent

    object ClearError : SettingsIntent

    object Logout : SettingsIntent

    object SwitchAccount : SettingsIntent

    data class UpdateAccount(
        val account: Account?,
    ) : SettingsIntent

    // URL Opening Intents
    object OpenPrivacyPolicy : SettingsIntent
    object OpenTermsOfService : SettingsIntent
    object OpenGreaterGoodsWebsite : SettingsIntent

    // Biological Sex Selection Intent
    object ShowBiologicalSexModal : SettingsIntent
}

/**
 * Reducer for the settings state, handling intents to update settings and errors.
 */
class SettingsReducer : IReducer<SettingsState, SettingsIntent> {
    override fun reduce(
        state: SettingsState,
        intent: SettingsIntent,
    ): SettingsState? =
        when (intent) {
            is SettingsIntent.SetError -> state.copy(errorMessage = intent.message, isLoading = false)
            SettingsIntent.ClearError -> state.copy(errorMessage = null)
            SettingsIntent.LoadSettings -> state.copy(isLoading = true)
            is SettingsIntent.UpdateAccount -> state.copy(account = intent.account)
            else -> null
            // Add more intent handling as needed
        }
}

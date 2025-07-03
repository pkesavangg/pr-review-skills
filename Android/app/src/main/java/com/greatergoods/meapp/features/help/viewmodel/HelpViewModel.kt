package com.greatergoods.meapp.features.help.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.help.model.HelpIntent
import com.greatergoods.meapp.features.help.model.HelpReducer
import com.greatergoods.meapp.features.help.model.HelpState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Help screen that manages state and handles user intents.
 */
@HiltViewModel
class HelpViewModel @Inject constructor(
    private val dialogUtility: IDialogUtility,
) : BaseIntentViewModel<HelpState, HelpIntent>(
    reducer = HelpReducer(),
) {

    companion object {
        private const val TAG = "HelpViewModel"
    }

    override fun provideInitialState(): HelpState {
        return HelpState()
    }

    /**
     * Handles incoming intents and updates the state accordingly.
     * @param intent The intent to handle.
     */
    override fun handleIntent(intent: HelpIntent) {
        super.handleIntent(intent)
        AppLog.d(TAG, "Handling intent: ${intent.javaClass.simpleName}")

        when (intent) {
            is HelpIntent.ShowModelNumberHelpPopup -> showModelNumberHelpPopup()
            is HelpIntent.OnBack -> onBack()
            is HelpIntent.OpenDebugMenu -> onOpenDebugMenu()
            is HelpIntent.OpenUrl -> openInAppBrowser(intent.url)
            is HelpIntent.Error -> onError(intent.message)
        }
    }

    /**
     * Shows the Model number help popup.
     */
    private fun showModelNumberHelpPopup() {
        dialogUtility.showModelNumberHelpDialog()
    }

    /**
     * Handles back navigation.
     */
    private fun onBack() {
        AppLog.d(TAG, "Back navigation requested")
        viewModelScope.launch {
            try {
                navigationService.navigateBack()
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to navigate back from help", e.toString())
            }
        }
    }

    /**
     * Handles debug menu navigation.
     * Opens debug menu after 5 taps on title (like Angular implementation).
     */
    fun onOpenDebugMenu() {
        AppLog.d(TAG, "Debug menu navigation requested")
        viewModelScope.launch {
            try {
                // navigationService.navigateTo(AppRoute.AccountSettings.DebugMenu)
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to navigate to debug menu", e.toString())
            }
        }
    }

    /**
     * Handles error states.
     */
    private fun onError(message: String) {
        AppLog.e(TAG, "Error: $message")
    }
}

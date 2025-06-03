package com.greatergoods.meapp.features.common.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.data.storage.datastore.ThemeMode
import com.greatergoods.meapp.domain.repository.IAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the app, holding theme mode and FCM token.
 *
 * @property themeMode The current theme mode.
 * @property fcmToken The current FCM token.
 */
data class AppUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fcmToken: String = ""
)

/**
 * Centralized ViewModel for app-wide state, including theme mode and FCM token.
 *
 * @property appRepository The repository providing theme and FCM token flows and actions.
 * @constructor Injects the AppRepository dependency.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val appRepository: IAppRepository
) : NavigationViewmodel() {

    private val _uiState: MutableStateFlow<AppUiState> = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState

    init {
        viewModelScope.launch {
            appRepository.themeModeFlow.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
        viewModelScope.launch {
            appRepository.fcmTokenFlow.collectLatest { token ->
                _uiState.value = _uiState.value.copy(fcmToken = token)
            }
        }
    }

    /**
     * Sets the app's theme mode.
     * @param mode The new theme mode to set.
     */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appRepository.setThemeMode(mode)
        }
    }

    /**
     * Sets the FCM token.
     * @param token The new FCM token to set.
     */
    fun setFcmToken(token: String) {
        viewModelScope.launch {
            appRepository.setFcmToken(token)
        }
    }

    /**
     * Clears the FCM token from storage.
     */
    fun clearFcmToken() {
        viewModelScope.launch {
            appRepository.clearFcmToken()
        }
    }
}

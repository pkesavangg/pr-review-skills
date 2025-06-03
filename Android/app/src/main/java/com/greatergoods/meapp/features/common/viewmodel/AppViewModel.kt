package com.greatergoods.meapp.features.common.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IUserRepository
import com.greatergoods.meapp.proto.ThemeMode
import com.greatergoods.meapp.proto.UserAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
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
    private val appRepository: IAppRepository,
    private val userRepository: IUserRepository
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
     * Sets the app's theme mode for a specific account.
     * @param accountId The account ID to update.
     * @param mode The new theme mode to set.
     */
    fun setThemeMode(accountId: String, mode: ThemeMode) {
        viewModelScope.launch {
            appRepository.setThemeMode(accountId, mode)
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

    /**
     * Flow of all user accounts, keyed by account ID.
     */
    val accountsFlow: StateFlow<Map<String, UserAccount>> = userRepository.accountsFlow
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyMap())

    /**
     * Creates a new random account and saves it to UserDataStore.
     */
    fun createRandomAccount() {
        viewModelScope.launch {
            userRepository.createRandomAccount()
        }
    }

    /**
     * Sets the given account as active in UserDataStore.
     */
    fun setActiveAccount(accountId: String) {
        viewModelScope.launch {
            userRepository.setActiveAccount(accountId)
        }
    }
}

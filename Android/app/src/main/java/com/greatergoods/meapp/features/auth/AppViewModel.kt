package com.greatergoods.meapp.features.auth

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IUserRepository
import com.greatergoods.meapp.features.common.viewmodel.BaseViewModel
import com.greatergoods.meapp.proto.ThemeMode
import com.greatergoods.meapp.proto.UserAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the app, holding theme mode and FCM token.
 *
 * @property themeMode The current theme mode.
 * @property fcmToken The current FCM token.
 */
data class AppUiState(
    val fcmToken: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
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
    private val userRepository: IUserRepository,
) : BaseViewModel() {

    private val _uiState: MutableStateFlow<AppUiState> = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var currentAccount: UserAccount? = null

    init {
        viewModelScope.launch {
            delay(3000)
            navigationService.replaceStack(
                listOf(
                    AppRoute.Auth.LoginScreen,
                ),
            )
        }
    }

    private fun initLogic() {
        viewModelScope.launch {
            userRepository.currentAccountFlow.collectLatest { account ->
                if (currentAccount != account) {
                    if (account != null) {
                        currentAccount = account
                        val currentAccountId = userRepository.accountsFlow.firstOrNull()
                            ?.entries
                            ?.find { it.value == account }
                            ?.key

                        initLoadingData(currentAccountId)
                    } else {
                        val destinationState = if (userRepository.hasAccounts()) {
                            AppRoute.Auth.UserListScreen
                        } else {
                            AppRoute.Auth.LoginScreen
                        }
                        _uiState.value = _uiState.value.copy(
                            themeMode = ThemeMode.SYSTEM,
                        )
                        navigationService.replaceStack(listOf(destinationState))
                    }
                }
            }
        }
    }

    private fun initLoadingData(isInitLoad: String?) {
        viewModelScope.launch {
            try {
                // Simulate data loading
                delay(3000)

                // TODO: Add your actual data loading logic here
                // For example:
                // - Load user preferences
                // - Initialize services
                // - Cache necessary data
                navigationService.replaceStack(listOf(AppRoute.Home.HomeScreen))
            } catch (e: Exception) {
                // TODO: Handle error state appropriately
            }
        }
    }
}

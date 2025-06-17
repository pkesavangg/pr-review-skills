package com.greatergoods.meapp.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.core.shared.utilities.logging.LogManager
import com.greatergoods.meapp.domain.model.Account
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IUserRepository
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.proto.UserAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val accountRepository: IAccountRepository,
    private val entryService: IEntryService,
    private val logManager: LogManager
) : BaseIntentViewModel<AppState, AppIntent>(
    initialState = AppState(),
    reducer = AppReducer()
) {
    private var currentAccount: UserAccount? = null

    init {
        viewModelScope.launch {
            delay(100)
            navigationService.navigateTo(
                AppRoute.Auth.Login
            )
        }
        viewModelScope.launch {
            try {
                logManager.cleanupOldLogs(5)
                AppLog.i("MainActivity", "Cleaning up old logs")
            } catch (e: Exception) {
                AppLog.e("MainActivity", "Failed to cleanup old logs", e.toString())
            }
        }
    }

    private fun initLogic() {
        viewModelScope.launch {
            userRepository.currentAccountFlow.collectLatest { account ->
                if (currentAccount != account) {
                    if (account != null) {
                        currentAccount = account
                        val currentAccountId =
                            userRepository.accountsFlow
                                .firstOrNull()
                                ?.entries
                                ?.find { it.value == account }
                                ?.key

                        initLoadingData(currentAccountId)
                    } else {
                        if (userRepository.hasAccounts()) {
                            AppRoute.Auth.UserList
                        } else {
                            AppRoute.Auth.Login
                        }
                        navigationService.logout()
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
                // navigationService.autoLogin()
            } catch (e: Exception) {
                // TODO: Handle error state appropriately
            }
        }
    }
}

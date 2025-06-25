package com.greatergoods.meapp.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.core.shared.utilities.logging.LogManager
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.domain.services.IDeviceInfoService
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

/**
 * Centralized ViewModel for app-wide state, including theme mode and FCM token.
 *
 * @property appRepository The repository providing theme and FCM token flows and actions.
 * @constructor Injects the AppRepository dependency.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val appRepository: IAppRepository,
    private val entryService: IEntryService,
    private val accountAuthService: IAccountAuthService,
    private val logManager: LogManager,
    private val deviceInfoService: IDeviceInfoService
) : BaseIntentViewModel<AppState, AppIntent>(
    reducer = AppReducer(),
) {
    companion object {
        private const val TAG = "AppLoaderView"
        private var currentAccountId: String? = null
    }

    override fun provideInitialState(): AppState {
        return AppState()
    }

    init {
        viewModelScope.launch {
            try {
                logManager.cleanupOldLogs(5)
                AppLog.i("MainActivity", "Cleaning up old logs")
            } catch (e: Exception) {
                AppLog.e("MainActivity", "Failed to cleanup old logs", e.toString())
            }
        }
        initLogic()
    }

    private fun initLogic() {
        viewModelScope.launch {
            try {
                accountAuthService.activeAccountFlow.collect { account ->
                    val loggedInAccounts = accountAuthService.getLoggedInAccounts().filter {
                        !it.isActiveAccount
                    }
                    val isLoginStatusChecked = checkLoginStatus()
                    if (isLoginStatusChecked && account != null) {
                        initLoadingData(account.id)
                    } else {
                        routeToLandingOrApp(loggedInAccounts.isNotEmpty())
                    }
                }
            } catch (e: Exception) {
                routeToLandingOrApp()
                AppLog.e(TAG, "Load data failed", e.toString())
            }
        }
    }

    /**
     * Checks the login status for all accounts using the split methods.
     * @return true if login status check was successful
     */
    private suspend fun checkLoginStatus(): Boolean {
        return try {
            // Check active account first
            accountAuthService.checkLoginStatusForActiveAccount()

            // Then check other logged-in accounts
            accountAuthService.checkLoginStatusForLoggedInAccounts()

            AppLog.d(TAG, "Checked login status for all accounts")
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Error checking login status", e.toString())
            false
        }
    }

    /**
     * Routes to either the landing page or the app based on login status.
     * @param isLoggedIn true if user is logged in, false otherwise
     */
    private suspend fun routeToLandingOrApp(hasAccounts: Boolean = false) {
        val route = if (hasAccounts) {
            AppRoute.Auth.UserList
        } else {
            AppRoute.Auth.Landing
        }
        navigationService.replaceStack(route = route)
    }

    private fun initLoadingData(accountId: String) {
        viewModelScope.launch {
            try {
                delay(1000)
                entryService.updateAccountId(accountId)
                deviceInfoService.updateDeviceInfo()
                navigationService.autoLogin()
            } catch (e: Exception) {
                Log.d(TAG, e.toString())
            }
        }
    }
}

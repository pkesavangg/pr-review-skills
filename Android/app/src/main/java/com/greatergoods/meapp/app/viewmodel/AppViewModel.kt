package com.greatergoods.meapp.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.network.ITokenManager
import com.greatergoods.meapp.core.service.IAppNavigationService
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.core.shared.utilities.logging.LogManager
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.services.AuthState
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.IDashboardService
import com.greatergoods.meapp.domain.services.IDeviceInfoService
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.common.strings.ToastStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Centralized ViewModel for app-wide state, including theme mode and FCM token.
 *
 * @property appRepository The repository providing theme and FCM token flows and actions.
 * @constructor Injects the AppRepository dependency.
 */
@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        private val appRepository: IAppRepository,
        private val entryService: IEntryService,
        private val logManager: LogManager,
        private val deviceInfoService: IDeviceInfoService,
        private val appNavigationService: IAppNavigationService,
        private val tokenManager: ITokenManager,
            private val dashboardService: IDashboardService,
        private val accountService: IAccountService,
        private val dialogUtility: IDialogUtility,
    ) : BaseIntentViewModel<AppState, AppIntent>(
            reducer = AppReducer(),
        ) {
        companion object {
            private const val TAG = "AppLoaderView"
            private var currentAccountId: String? = null
        }

        override fun provideInitialState(): AppState = AppState()

        init {

            viewModelScope.launch {
                try {
                    logManager.cleanupOldLogs(5)
                    AppLog.i("MainActivity", "Cleaning up old logs")
                } catch (e: Exception) {
                    AppLog.e("MainActivity", "Failed to cleanup old logs", e.toString())
                }

                // Load all tokens into TokenManager's in-memory map
                try {
                    tokenManager.loadAllTokens()
                    AppLog.d(TAG, "Loaded all tokens into TokenManager")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to load tokens into TokenManager", e.toString())
                }

                val account = accountService.getCurrentAccount()
                initLoadingData(account)
                initEvents()
            }
        }

        private fun initEvents() {
            viewModelScope.launch {
                appNavigationService.authEvent.collect { authState ->
                    when (authState) {
                        is AuthState.LoggedIn -> {
                            // handle login event
                            initLoadingData(authState.account)
                        }

                        is AuthState.LoggedOut -> {
                            if (authState.isActiveAccount) {
                                routeToLandingOrApp()
                            }
                        }

                        is AuthState.UnauthorizedLogout -> {
                            // Show account logged out alert
                            viewModelScope.launch {
                                val activeAccount = accountService.handleUnauthorizedLogout(authState.accountId)
                                if (activeAccount != null) {
                                    navigationService.replaceStack(route = AppRoute.Auth.MultiAccountLanding)
                                    dialogUtility.showAccountLoggedOutAlert(activeAccount.firstName)
                                }
                            }
                        }

                        is AuthState.AccountAdded -> {
                            initLoadingData(authState.account)
                        }

                        is AuthState.AccountSwitched -> {
                            if (authState.showToast) {
                                val accountName = authState.account.firstName
                                dialogQueueService.showToast(
                                    Toast(
                                        title = null,
                                        message = ToastStrings.Success.AccountSwitchSuccess.Message(accountName),
                                        action = null,
                                    ),
                                )
                            }
                            initLoadingData(authState.account)
                        }

                        is AuthState.ProfileUpdated -> {
                            // Profile updated - no navigation needed, just log
                            AppLog.d(TAG, "Profile updated for account: ${authState.account.id}")
                        }

                        is AuthState.Error -> {
                            // Handle auth errors without triggering navigation
                            AppLog.e(TAG, "Auth error: ${authState.message}")
                        }

                        // handle other AuthState events as needed
                        else -> {}
                    }
                }
            }
        }

        /**
         * Checks the login status for all accounts using the split methods.
         * @return true if login status check was successful
         */
        private suspend fun checkLoginStatus(): Boolean =
            try {
                // Check active account first
                val isActiveAccountChecked = accountService.checkLoginStatusForActiveAccount()
                // Then check other logged-in accounts
                val isLoggedInAccountsChecked = accountService.checkLoginStatusForLoggedInAccounts()

                AppLog.d(TAG, "Checked login status for all accounts")
                isActiveAccountChecked && isLoggedInAccountsChecked
            } catch (e: Exception) {
                AppLog.e(TAG, "Error checking login status", e.toString())
                false
            }

        /**
         * Routes to either the landing page or the app based on login status.
         * @param isLoggedIn true if user is logged in, false otherwise
         */
        private suspend fun routeToLandingOrApp() {
            val loggedInAccounts =
                accountService.getLoggedInAccounts().filter {
                    !it.isActiveAccount
                }
            val hasAccounts = loggedInAccounts.isNotEmpty()
            val route =
                if (hasAccounts) {
                    AppRoute.Auth.MultiAccountLanding
                } else {
                    AppRoute.Auth.Landing
                }
            navigationService.replaceStack(route = route)
        }

        private suspend fun initLoadingData(account: Account?) {
            try {
                val isLoginStatusChecked = checkLoginStatus()
                if (account != null && isLoginStatusChecked) {
                    entryService.updateAccountId(account.id)

                    deviceInfoService.updateDeviceInfo()
                    dashboardService.setAccountId(account.id)
                    navigationService.autoLogin()
                } else {
                    routeToLandingOrApp()
                }
            } catch (e: Exception) {
                routeToLandingOrApp()
                AppLog.e(TAG, "Load data failed", e.toString())
            }
        }
    }

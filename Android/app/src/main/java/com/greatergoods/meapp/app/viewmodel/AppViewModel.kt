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
import kotlinx.coroutines.flow.collectLatest
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
    }

    override fun provideInitialState(): AppState {
        return AppState()
    }

    init {
        initLogic()
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
            accountAuthService.activeAccountFlow.collectLatest { account ->
                if (account != null) {
                    initLoadingData(account.id)
                } else {
                    navigationService.replaceStack(
                        route =
                            if (accountAuthService.checkForLoggedInUser()) {
                                AppRoute.Auth.UserList
                            } else {
                                AppRoute.Auth.Landing
                            },
                    )
                }
            }
        }
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

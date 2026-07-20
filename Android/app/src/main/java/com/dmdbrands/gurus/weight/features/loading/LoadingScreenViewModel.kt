package com.dmdbrands.gurus.weight.features.loading

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.network.TokenMigrationHelper
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IDeviceInfoService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Loading screen. Owns startup flow: wait for migration, check login,
 * loadData (entry sync, BLE/scale sync), navigate, then emit LoggedInFromLoading so
 * AppViewModel starts observers (feed, IAM, permissions, device callbacks, etc.).
 * Aligned with Angular loading.page.ts: migrationComplete → checkLoginStatus → loadData → routeToLandingOrApp.
 */
@HiltViewModel
class LoadingScreenViewModel
@Inject
constructor(
  private val workManager: WorkManager,
  private val tokenMigrationHelper: TokenMigrationHelper,
  private val tokenManager: ITokenManager,
  private val accountService: IAccountService,
  private val appNavigationService: IAppNavigationService,
  private val entryService: IEntryService,
  private val dashboardService: IDashboardService,
  private val deviceService: IDeviceService,
  private val deviceInfoService: IDeviceInfoService,
  private val productSelectionManager: IProductSelectionManager,
  private val entryReadService: IEntryReadService,
  @ApplicationContext private val applicationContext: Context,
) : ViewModel() {
  private val TAG = "Loadingscreenviewmodel"
  init {
    start()
  }

  /**
   * Runs the full startup flow: wait for migration (no timeout), check login,
   * then either routeToLandingOrApp (not logged in) or loadData + autoLogin + emit LoggedInFromLoading.
   */
  fun start() {
    viewModelScope.launch {
      try {
        AppLog.d(TAG, "Starting startup flow")
        waitForMigration()
        var isLoggedIn = checkLoginStatus()
        val account = accountService.getCurrentAccount()
        AppLog.d(TAG, "Startup flow complete, account: ${account?.id}")
        if (account == null) {
          AppLog.d(TAG, "Navigating to landing screen due to account id not available")
          routeToLandingOrApp()
          return@launch
        }
        if (!isLoggedIn && checkLocalAccountValidity()) {
          AppLog.d(TAG, "Login check failed but account is locally valid, proceeding")
          isLoggedIn = true
        }
        if (isLoggedIn) {
          loadData(account)
          appNavigationService.autoLogin()
          appNavigationService.emitAuthEvent(AuthState.LoggedInFromLoading(account))
        } else {
          AppLog.d(TAG, "Navigating to landing screen due to not logged in")
          routeToLandingOrApp()
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Startup flow exception", e)
        if (checkLocalAccountValidity()) {
          val account = accountService.getCurrentAccount()
          if (account != null) {
            AppLog.d(TAG, "Exception during startup but account is locally valid, proceeding")
            loadData(account)
            appNavigationService.autoLogin()
            appNavigationService.emitAuthEvent(AuthState.LoggedInFromLoading(account))
            return@launch
          }
        }
        AppLog.d(TAG, "Navigating to landing screen due to account info not available in offline")
        routeToLandingOrApp()
      }
    }
  }

  /**
   * Blocks the startup flow until all migrations that affect the login decision are done:
   *
   * 1. The Ionic (Capacitor 4.x → native) WorkManager job, if scheduled.
   * 2. The DataStore → EncryptedSharedPreferences token migration + loading tokens into
   *    [TokenManager]'s in-memory map.
   *
   * Step 2 is critical for the 5.0.3 → 5.1.0 upgrade (MOB-1537 / MOB-1526): the legacy app
   * kept tokens only in the DataStore proto, and 5.1.0 reads them exclusively from the secure
   * store. Previously this ran in a fire-and-forget coroutine in AppViewModel, so the login
   * check below could race ahead of it, send a `Bearer null` request, get a 401, expire the
   * account, and drop the user to Landing (then loop). Awaiting it here removes that race.
   */
  private suspend fun waitForMigration() {
    workManager.getWorkInfosByTagLiveData("ionic_migration").asFlow().first { workInfos ->
      workInfos.isEmpty() || workInfos.all { it.state.isFinished }
    }
    tokenMigrationHelper.migrateIfNeeded()
    tokenManager.loadAllTokens()
  }

  private suspend fun checkLoginStatus(): Boolean =
    try {
      val active = accountService.checkLoginStatusForActiveAccount()
      val loggedIn = accountService.checkLoginStatusForLoggedInAccounts()
      active && loggedIn
    } catch (e: Exception) {
      AppLog.e(TAG, "Error checking login status, falling back to local validity", e)
      checkLocalAccountValidity()
    }

  private suspend fun routeToLandingOrApp() {
    val loggedInAccounts = accountService.getLoggedInAccounts().filter { !it.isActiveAccount }
    val route = if (loggedInAccounts.isNotEmpty()) AppRoute.Auth.MultiAccountLanding else AppRoute.Auth.Landing
    appNavigationService.replaceStack(route = route)
  }

  /**
   * loadData (same as Angular): subscribeAccount, entry sync (via updateAllData), setAccountId on dashboard/device, syncScales.
   *
   * Also preloads the CDC percentile tables on IO in parallel so the Baby dashboard
   * sheet has them ready on first open — avoids the first-launch race where tapping
   * the chart header before ~200ms of CSV parsing completed would show "--".
   */
  private suspend fun loadData(account: Account) {
    accountService.subscribeAccount()
    deviceInfoService.updateDeviceInfo()
    coroutineScope {
      awaitAll(
        async { entryService.updateAllData(accountId = account.id) },
        async { dashboardService.setAccountId(account.id) },
        async { deviceService.setAccountId(account.id) },
        async { deviceInfoService.updateLocalIntegrationInfo() },
        async { productSelectionManager.loadAvailableProducts(account.id) },
        async(Dispatchers.IO) { BabyPercentileHelper.loadIfNeeded(applicationContext) },
      )
      entryReadService.setAccountId(account.id)
    }
  }

  /**
   * Checks if the active account is valid locally (exists and not expired).
   * Used as fallback during account switch when network checks fail.
   * @return true if active account exists and is not expired
   */
  private suspend fun checkLocalAccountValidity(): Boolean {
    val activeAccount = accountService.getCurrentAccount()
    return if (activeAccount != null && !activeAccount.isExpired) {
      AppLog.d(TAG, "Local account validity check passed for account: ${activeAccount.id}")
      true
    } else {
      AppLog.d(TAG, "Local account validity check failed - account is null or expired")
      false
    }
  }
}

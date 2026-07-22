package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.config.HttpErrorConfig
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toAccountInfo
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.services.IOfflineHandlerService
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * Verifies login status for the active account and for the other logged-in accounts, falling back
 * to local-DB validity when offline / on transient network failures, and only marking an account
 * expired on a 401. Owns the [checkIntegrations] signal that drives post-login integration checks.
 *
 * Extracted from [AccountService] (MOB-1499) so the service clears the detekt `LargeClass` limit.
 * Extends [BaseService] for the shared network helpers and is built from `AccountService`'s own
 * injected dependencies, so behaviour is unchanged.
 */
class AccountValidationService(
  private val accountRepository: IAccountRepository,
  private val offlineHandlerService: IOfflineHandlerService,
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  appNavigationService: IAppNavigationService,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService) {
  companion object {
    private const val TAG = "AccountService"
  }

  /**
   * Flow for triggering integration checks, similar to Angular BehaviorSubject.
   * Emits true when login or checkLoginStatusForLoggedInAccounts is called.
   */
  private val _checkIntegrations = MutableStateFlow(false)
  val checkIntegrations: StateFlow<Boolean> = _checkIntegrations

  private suspend fun getCurrentAccount(): Account? = accountRepository.getActiveAccount().first()

  private suspend fun getLoggedInAccounts(): List<Account> =
    accountRepository.getLoggedInAccounts().first().sortedActiveFirst()

  /**
   * Checks login status for the active account by calling getAccount API if online.
   * If offline or on network/HTTP failure (except 401), falls back to local DB validity. Only 401 marks account expired.
   * @return true if account is valid (online or offline), false if expired or unauthorized
   */
  suspend fun checkLoginStatusForActiveAccount(): Boolean {
    AppLog.d(TAG, "checkLoginStatusForActiveAccount() called")
    if (!isNetworkAvailable()) {
      AppLog.d(TAG, "Offline mode: checking local DB for active account validity.")
      return checkActiveAccountLocalValidity()
    }
    return try {
      AppLog.d(TAG, "Checking network availability for checkLoginStatusForActiveAccount()")
      requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
      val activeAccount = getCurrentAccount()
      if (activeAccount == null) {
        AppLog.d(TAG, "No active account found in checkLoginStatusForActiveAccount(). Returning false.")
        return false
      }
      // Sync any unsynced changes for the active account before checking login status
      offlineHandlerService.handleOfflineSync()
      AppLog.d(TAG, "Checking login status for active account: ${activeAccount.id}")
      val accountInfo = accountRepository.getAccountFromAPI(activeAccount.id)
      accountRepository.syncAccountSettingsWithServer(accountInfo, isOnline = true)
      AppLog.d(TAG, "Active account login status check successful")
      true
    } catch (e: java.net.UnknownHostException) {
      AppLog.w(TAG, "UnknownHostException failure during login status check, falling back to local DB ${e.toString()}")
      checkActiveAccountLocalValidity()
    } catch (e: java.io.InterruptedIOException) {
      AppLog.w(
        TAG,
        "InterruptedIOException failure during login status check, falling back to local DB ${e.toString()}",
      )
      checkActiveAccountLocalValidity()
    } catch (e: java.net.SocketTimeoutException) {
      AppLog.w(
        TAG,
        "SocketTimeoutException failure during login status check, falling back to local DB ${e.toString()}",
      )
      checkActiveAccountLocalValidity()
    } catch (e: IOException) {
      AppLog.w(TAG, "Network failure during login status check, falling back to local DB ${e.toString()}")
      checkActiveAccountLocalValidity()
    } catch (e: HttpException) {
      if (e.code() == HttpErrorConfig.ResponseCode.UNAUTHORIZED) {
        val activeAccount = getCurrentAccount()
        if (activeAccount != null) {
          AppLog.w(TAG, "Active account is unauthorized (401). Marking as expired: ${activeAccount.id}")
          accountRepository.markAccountExpired(activeAccount.id)
          accountRepository.clearAccountTokens(activeAccount.id)
        }
        false
      } else {
        AppLog.w(TAG, "HTTP error ${e.code()} during active account check, falling back to local DB")
        checkActiveAccountLocalValidity()
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Active account login status check failed, falling back to local DB", e)
      checkActiveAccountLocalValidity()
    }
  }

  /**
   * Helper method to check active account validity from local DB.
   * @return true if account exists and is not expired, false otherwise
   */
  private suspend fun checkActiveAccountLocalValidity(): Boolean {
    val activeAccount = getCurrentAccount()
    if (activeAccount == null) {
      AppLog.d(TAG, "No active account found in local DB. Returning false.")
      return false
    }
    if (activeAccount.isExpired) {
      AppLog.d(TAG, "Active account is expired in local DB. Returning false.")
      return false
    }
    // Sync local account data to database when offline
    try {
      val accountInfo = activeAccount.toAccountInfo()
      accountRepository.syncAccountSettingsWithServer(accountInfo, isOnline = false)
      AppLog.d(TAG, "Synced local account data to database in offline mode")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to sync local account data in offline mode", e)
    }
    AppLog.d(TAG, "Active account is valid in local DB. Returning true.")
    return true
  }

  /**
   * Checks login status for all logged-in accounts (non-active) by calling getAccount API if online.
   * This is a best-effort check that refreshes account data and cleans up invalid accounts.
   * Accounts that return 401 Unauthorized are marked as expired and removed.
   * On network/HTTP failure, falls back to local DB validity (does not fail the check).
   * @return true if the check completed (regardless of whether individual accounts were expired/removed),
   *         false only if a fatal error prevented the check from completing
   */
  suspend fun checkLoginStatusForLoggedInAccounts(): Boolean {
    AppLog.d(TAG, "checkLoginStatusForLoggedInAccounts() called")
    if (!isNetworkAvailable()) {
      return checkLoginStatusOffline()
    }
    // Online mode: keep existing logic
    return try {
      AppLog.d(TAG, "Checking network availability for checkLoginStatusForLoggedInAccounts()")
      requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
      val loggedInAccounts = getLoggedInAccounts().filter { !it.isActiveAccount }
      if (loggedInAccounts.isEmpty()) {
        AppLog.d(
          TAG,
          "No non-active logged-in accounts found in checkLoginStatusForLoggedInAccounts(). Returning true.",
        )
        // Emit true to trigger integration checks
        _checkIntegrations.value = false
        return true
      }
      val filterLoggedInAccounts = loggedInAccounts.filter { !it.isExpired }

      // Run account checks in parallel - network failures don't mark accounts as expired
      coroutineScope {
        filterLoggedInAccounts.forEach { account ->
          launch { refreshLoggedInAccount(account) }
        }
      }
      AppLog.d(TAG, "Logged-in accounts status check completed.")
      // Emit true to trigger integration checks
      _checkIntegrations.value = true
      true
    } catch (e: IOException) {
      AppLog.w(TAG, "Network failure during logged-in accounts check, proceeding with local state", e.toString())
      _checkIntegrations.value = true
      true
    } catch (e: Exception) {
      AppLog.e(TAG, "Logged-in accounts status check failed, proceeding with local state", e)
      _checkIntegrations.value = true
      true
    }
  }

  /** Offline branch of [checkLoginStatusForLoggedInAccounts]: validate against the local DB only. */
  private suspend fun checkLoginStatusOffline(): Boolean {
    AppLog.d(TAG, "Offline mode: checking local DB for all logged-in accounts validity.")
    val loggedInAccounts = getLoggedInAccounts().filter { !it.isActiveAccount }
    if (loggedInAccounts.isEmpty()) {
      AppLog.d(TAG, "No non-active logged-in accounts found in offline mode. Returning true.")
      // Emit true to trigger integration checks even in offline mode
      _checkIntegrations.value = false
      return true
    }

    AppLog.d(TAG, "All logged-in accounts are valid in local DB. Returning true.")
    // Emit true to trigger integration checks
    return true
  }

  /**
   * Refreshes one logged-in [account] against the API. Only a 401 marks it expired; all network
   * failures are logged and swallowed so a transient outage never logs the user out.
   */
  private suspend fun refreshLoggedInAccount(account: Account) {
    try {
      AppLog.d(TAG, "Checking login status for account: ${account.id}")
      // Get account info from API and update tokens for background operations
      val accountInfo = accountRepository.getAccountFromAPI(account.id)
      // Update account data with API response
      accountRepository.updateAccountInfo(account.id, accountInfo)
      AppLog.d(TAG, "Account ${account.id} login status check successful")
    } catch (e: IOException) {
      // Network failure - don't mark account as expired, just log and continue
      AppLog.w(
        TAG,
        "Network failure while checking account ${account.id}, skipping (not marking as expired)",
        e.toString(),
      )
    } catch (e: HttpException) {
      // Only mark as expired on 401 Unauthorized errors
      if (e.code() == HttpErrorConfig.ResponseCode.UNAUTHORIZED) {
        AppLog.w(TAG, "Account is unauthorized (401). Marking as expired: ${account.id}")
        accountRepository.markAccountExpired(account.id)
        accountRepository.removeAccount(account.id)
      } else {
        // Other HTTP errors (500, 404, etc.) - don't mark as expired, just log
        AppLog.w(TAG, "HTTP error ${e.code()} while checking account ${account.id}, not marking as expired")
      }
    } catch (e: java.net.UnknownHostException) {
      AppLog.w(TAG, "UnknownHostException failure during logged accounts check ${e.toString()}")
    } catch (e: java.io.InterruptedIOException) {
      AppLog.w(
        TAG,
        "InterruptedIOException failure during logged accounts check, falling back to local DB ${e.toString()}",
      )
    } catch (e: java.net.SocketTimeoutException) {
      AppLog.w(
        TAG,
        "SocketTimeoutException failure during logged accounts check, falling back to local DB ${e.toString()}",
      )
    } catch (e: Exception) {
      // Other exceptions - log but don't mark as expired
      AppLog.e(TAG, "Account ${account.id} login status check failed (not marking as expired)", e)
    }
  }
}

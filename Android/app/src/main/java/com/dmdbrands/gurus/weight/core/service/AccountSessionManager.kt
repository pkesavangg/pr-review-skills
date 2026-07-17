package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.config.HttpErrorConfig
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings.Error.LoginError
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException

/**
 * Handles account session lifecycle: logout, logout-all, account switching, unauthorized logout,
 * remove-from-device and account deletion.
 *
 * Extracted from [AccountService] (MOB-1499) so the service clears the detekt `LargeClass` limit.
 * Extends [BaseService] for the shared network/toast helpers and is built from `AccountService`'s
 * own injected dependencies, so behaviour and error/analytics/nav side effects are unchanged.
 */
class AccountSessionManager(
  private val accountRepository: IAccountRepository,
  private val analyticsService: IAnalyticsService,
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  appNavigationService: IAppNavigationService,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService) {
  companion object {
    private const val TAG = "AccountService"
  }

  private suspend fun getCurrentAccount(): Account? = accountRepository.getActiveAccount().first()

  private suspend fun getLoggedInAccounts(): List<Account> =
    accountRepository.getLoggedInAccounts().first().sortedActiveFirst()

  /**
   * Handles unauthorized logout when token refresh fails.
   * Marks account as expired, removes from storage, and triggers unauthorized logout event.
   * @param accountId The ID of the account to logout
   * @return The affected account or null if not found
   */
  suspend fun handleUnauthorizedLogout(accountId: String?): Account? {
    AppLog.v(TAG, "handleUnauthorizedLogout() called for accountId: $accountId")
    if (accountId.isNullOrEmpty()) {
      AppLog.w(TAG, "No account ID available for unauthorized logout")
      return null
    }

    return try {
      AppLog.v(TAG, "Handling unauthorized logout for account: $accountId")
      val account = getCurrentAccount()
      return if (account?.isActiveAccount == true && accountId == account.id) {
        // Mark account as expired in database
        accountRepository.markAccountExpired(accountId)
        // Clear account tokens from DataStore
        accountRepository.clearAccountTokens(accountId)
        AppLog.v(TAG, "Unauthorized logout completed for account: $accountId")
        account
      } else {
        null
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error during unauthorized logout for account: $accountId", e)
      null
    }
  }

  /**
   * Logs out the current user.
   * @param accountId ID of the account to log out
   * @param fcmToken FCM token for push notifications (optional)
   * @return true if logout was successful
   */
  suspend fun logout(
    accountId: String,
    fcmToken: String?,
  ): Boolean =
    try {
      if (!isNetworkAvailable()) {
        showNoNetworkErrorToast()
      }
      AppLog.v(TAG, "logout() called for accountId: $accountId")
      val isActiveAccount = getCurrentAccount()?.id == accountId
      val wasLastAccount = getLoggedInAccounts().size == 1
      val result = accountRepository.logoutAccount(accountId, fcmToken, isActiveAccount)
      accountRepository.setNotificationAlertShownForAccount(accountId, false)
      AppLog.d(TAG, "Logout successful")
      appNavigationService.emitAuthEvent(
        AuthState.LoggedOut(
          isActiveAccount = isActiveAccount,
          isLastAccount = wasLastAccount,
        ),
      )
      result
    } catch (e: Exception) {
      AppLog.e(TAG, "Logout failed", e)
      appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Logout failed"))
      false
    }

  /**
   * Logs out all users.
   * @return true if all accounts were logged out successfully
   */
  suspend fun logoutAll(): Boolean =
    try {
      AppLog.d(TAG, "logoutAll() called")
      if (!isNetworkAvailable()) {
        showNoNetworkErrorToast()
      }
      // Get all logged-in accounts before logging out to reset their notification alert settings
      val loggedInAccounts = getLoggedInAccounts()
      val result = accountRepository.logoutAllAccounts()

      // Reset notification alert settings for all accounts
      loggedInAccounts.forEach { account ->
        try {
          accountRepository.setNotificationAlertShownForAccount(account.id, false)
        } catch (e: Exception) {
          AppLog.e(TAG, "Failed to reset notification alert setting for account: ${account.id}", e)
        }
      }

      AppLog.d(TAG, "All accounts logged out successfully")
      appNavigationService.emitAuthEvent(AuthState.LoggedOut(true))
      result
    } catch (e: Exception) {
      AppLog.e(TAG, "Logout all failed", e)
      appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Logout all failed"))
      false
    }

  /**
   * Removes the specified account from this device only ("Removed = gone", MA-2672 / MOB-424).
   * Fully deletes the local account; the server account is not deleted. Navigation away from an
   * emptied list is handled by the (Multi-)Landing screen observing loggedInAccountsFlow.
   * @param accountId ID of the account to remove
   * @param fcmToken FCM token for push notifications (optional)
   * @return true if the account was removed successfully, false otherwise
   */
  suspend fun removeAccountFromDevice(
    accountId: String,
    fcmToken: String?,
  ): Boolean =
    try {
      if (!isNetworkAvailable()) {
        showNoNetworkErrorToast()
      }
      AppLog.v(TAG, "removeAccountFromDevice() called for accountId: $accountId")
      val isActiveAccount = getCurrentAccount()?.id == accountId
      accountRepository.removeAccountFromDevice(accountId, fcmToken, isActiveAccount)
      AppLog.d(TAG, "Account removed from device")
      true
    } catch (e: Exception) {
      AppLog.e(TAG, "removeAccountFromDevice failed", e)
      false
    }

  /**
   * Deletes the current user account from the server and local storage.
   */
  suspend fun deleteAccount(
    accountID: String,
    isActiveAccount: Boolean,
  ) {
    AppLog.v(TAG, "deleteAccount() called for accountId: $accountID, isActiveAccount: $isActiveAccount")
    AppLog.d(TAG, "Checking network availability for deleteAccount()")
    try {
      requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
      accountRepository.deleteAccount(accountID, isActiveAccount)
      AppLog.d(TAG, "Account deleted successfully")
    } catch (e: Exception) {
      AppLog.e(TAG, "Account deletion failed", e)
      throw e
    }
  }

  /**
   * Switches to a different account.
   * @param account Account to switch to
   * @param showToast Whether to show a toast notification after switching (default: false)
   * @return true if switch was successful
   */
  suspend fun switchAccount(
    account: Account,
    showToast: Boolean,
  ): Boolean {
    AppLog.v(TAG, "switchAccount() called for accountId: ${account.id}, showToast: $showToast")

    // Check network availability - prevent offline switching
    requireNetworkAvailable(
      onError = {
        AppLog.w(TAG, "Cannot switch account while offline")
        showNetworkErrorAndThrow()
      },
    )
    dialogQueueService.showLoader("Switching account...")
    return try {
      accountRepository.getAccountFromAPI(account.id)
      // Switch to the account using the repository method
      accountRepository.switchToAccount(account.id)
      AppLog.d(TAG, "Successfully switched account")
      analyticsService.logEvent(IAnalyticsService.Events.ACCOUNT_SWITCHED)
      appNavigationService.emitAuthEvent(AuthState.AccountSwitched(account, showToast))
      true
    } catch (e: java.net.UnknownHostException) {
      AppLog.w(TAG, "UnknownHostException failure during account switch ${e.toString()}")
      showNetworkErrorAndThrow()
      false
    } catch (e: java.io.InterruptedIOException) {
      AppLog.w(TAG, "InterruptedIOException failure failure during account switch ${e.toString()}")
      showNetworkErrorAndThrow()
      false
    } catch (e: java.net.SocketTimeoutException) {
      AppLog.w(TAG, "SocketTimeoutException e.toString() ${e.toString()}")
      showNetworkErrorAndThrow()
      false
    } catch (e: IOException) {
      // Network failed during API call - check if account is valid locally
      AppLog.w(TAG, "Network failed during account switch, checking local validity", e.toString())
      val localAccount = getLoggedInAccounts().find { it.id == account.id }
      if (localAccount != null && !localAccount.isExpired) {
        // Account exists locally and is not expired, allow switch
        AppLog.d(TAG, "Account is valid locally, proceeding with switch despite network failure")
        accountRepository.switchToAccount(account.id)
        appNavigationService.emitAuthEvent(AuthState.AccountSwitched(account, showToast))
        true
      } else {
        AppLog.w(TAG, "Account not valid locally or is expired, cannot switch")
        showErrorToast(LoginError.Header, ToastStrings.Error.NetworkError.Message)
        false
      }
    } catch (e: HttpException) {
      AppLog.e(TAG, "Failed to switch account $e", e)
      handleAccountValidationError(account.id, e)
      false
    } catch (e: Exception) {
      // Optional: handle unexpected exceptions
      AppLog.e(TAG, "Unexpected error while switching account", e)
      false
    } finally {
      dialogQueueService.dismissLoader()
    }
  }

  /**
   * Handles validation errors when checking account validity during switch.
   * @param accountId The account ID that failed validation
   * @param exception The HTTP exception from the validation call
   */
  private suspend fun handleAccountValidationError(accountId: String, exception: HttpException) {
    when (exception.code()) {
      HttpErrorConfig.ResponseCode.UNAUTHORIZED -> {
        AppLog.w(TAG, "Account is unauthorized (401). Marking as expired: $accountId")
        // Mark account as expired and stop further processing
        accountRepository.markAccountExpired(accountId)
        accountRepository.removeAccount(accountId)
      }

      else -> {
        AppLog.e(TAG, "Account validation failed with code ${exception.code()}: $accountId", exception)
        // Show generic error for other HTTP errors
        showErrorToast(
          LoginError.Header,
          LoginError.MessageGeneric,
        )
      }
    }
  }

  private fun showNoNetworkErrorToast() {
    dialogQueueService.showToast(
      Toast.Simple(
        title = null,
        message = ToastStrings.Error.NetworkError.Message,
        action = null,
      ),
    )
  }
}

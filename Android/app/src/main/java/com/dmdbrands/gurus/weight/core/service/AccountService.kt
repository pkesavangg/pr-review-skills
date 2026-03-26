package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.config.HttpErrorConfig
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toAccountInfo
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IOfflineHandlerService
import com.dmdbrands.gurus.weight.domain.services.MaxAccountsReachedException
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings.Error.LoginError
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.dmdbrands.gurus.weight.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Singleton
import android.os.Bundle

/**
 * Service for managing account authentication and session state.
 * Handles login, logout, account switching, and token management.
 */
@Singleton
class AccountService(
  private val accountRepository: IAccountRepository,
  private val offlineHandlerService: IOfflineHandlerService,
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  appNavigationService: IAppNavigationService,
  private val storageClearService: StorageClearService,
  private val analyticsService: IAnalyticsService,
  @ApplicationScope private val appScope: CoroutineScope,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService),
  IAccountService {
  companion object {
    private const val MAX_ACCOUNTS = 10
    private const val TAG = "AccountService"
  }

  private var repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  // region Public Properties

  /**
   * Flow emitting authentication state changes (login, logout, errors, etc).
   */
  override val authEvent = appNavigationService.authEvent

  private val _activeAccount = MutableStateFlow<Account?>(null)
  override val activeAccount: StateFlow<Account?> = _activeAccount

  /**
   * Flow emitting the currently active account, or null if none is active.
   */
  override val activeAccountFlow: Flow<Account?> = accountRepository.getActiveAccount()

  /**
   * Flow emitting the list of all logged-in accounts, with the active account first.
   */
  override val loggedInAccountsFlow: Flow<List<Account>> =
    accountRepository.getLoggedInAccounts().map { it.sortedActiveFirst() }

  /**
   * Flow indicating whether the maximum number of accounts has been reached.
   */
  override val hasReachedMaxAccounts: Flow<Boolean> = loggedInAccountsFlow.map { it.size >= MAX_ACCOUNTS }

  /**
   * Flow for triggering integration checks, similar to Angular BehaviorSubject.
   * Emits true when login or checkLoginStatusForLoggedInAccounts is called.
   */
  private val _checkIntegrations = MutableStateFlow(false)
  override val checkIntegrations: StateFlow<Boolean> = _checkIntegrations

  override fun subscribeAccount() {
    repositoryScope.cancel()
    repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    repositoryScope.launch {
      accountRepository.getActiveAccount().collect {
        _activeAccount.value = it
      }
    }
  }

  /**
   * Gets the current active account.
   * @return The active account or null if none
   */
  override suspend fun getCurrentAccount(): Account? = activeAccountFlow.first()

  /**
   * Gets the list of all logged-in accounts, with the active account first.
   * @return List of accounts
   */
  override suspend fun getLoggedInAccounts(): List<Account> = loggedInAccountsFlow.first().sortedActiveFirst()

  /**
   * Gets the current theme mode for the active account as a flow.
   * @return Flow of ThemeMode that emits changes
   */
  override val currentThemeModeFlow = accountRepository.currentThemeModeFlow

  /**
   * Logs in a user with email and password.
   * @param email User's email
   * @param password User's password
   * @return The authenticated account or null if login fails
   */
  override suspend fun login(
    email: String,
    password: String,
  ): Account? =
    try {
      AppLog.d(TAG, "login() called")
      val isExistingAccount = getLoggedInAccounts().any { it.email == email }
      if (hasReachedMaxAccounts.first() && !isExistingAccount) {
        AppLog.w(TAG, "Max accounts reached. Cannot login new account")
        throw MaxAccountsReachedException()
      }
      val savedAccount = accountRepository.login(email, password)

      analyticsService.logEvent(IAnalyticsService.Events.LOGIN_SUCCESS)
      AppLog.d(TAG, "login() successful")
      savedAccount
    } catch (e: HttpException) {
      val msg =
        when (e.code()) {
          HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> LoginError.MessageNoConn
          HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR -> LoginError.MessageServError
          HttpErrorConfig.ResponseCode.UNAUTHORIZED -> LoginError.MessageNotAuth
          else -> LoginError.MessageGeneric
        }
      showErrorToast(title = LoginError.Header, message = msg)
      analyticsService.logEvent(
        IAnalyticsService.Events.LOGIN_FAILURE,
        Bundle().apply { putString(IAnalyticsService.Params.ERROR_TYPE, "http_${e.code()}") },
      )
      AppLog.e(TAG, "Login failed", e)
      appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Login failed"))
      null
    }

  /**
   * Adds a new account using the provided request data.
   * @param request Account creation request data
   * @return The created account or null if creation fails
   * @throws MaxAccountsReachedException if the maximum number of accounts is reached
   */
  override suspend fun signup(request: SignupRequest): Account? {
    AppLog.d(TAG, "signup() called")
    if (hasReachedMaxAccounts.first()) {
      AppLog.w(TAG, "Max accounts reached. Cannot signup new account")
      appNavigationService.emitAuthEvent(AuthState.Error("Maximum account limit reached"))
      throw MaxAccountsReachedException()
    }
    return try {
      val savedAccount = accountRepository.signup(request)
      appNavigationService.emitAuthEvent(AuthState.AccountAdded(savedAccount))
      analyticsService.logEvent(IAnalyticsService.Events.SIGNUP_COMPLETED)
      AppLog.d(TAG, "signup() successful")
      savedAccount
    } catch (e: Exception) {
      if (e is HttpException) {
        val signupError = SignupStrings.Error
        val errorMessage =
          when (e.code()) {
            HttpErrorConfig.ResponseCode.UNAUTHORIZED -> signupError.MessageNotAuth
            HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> signupError.MessageNoConn
            HttpErrorConfig.ResponseCode.BAD_REQUEST -> signupError.accountExist
            else -> signupError.MessageGeneric
          }
        val errorHeader =
          when (e.code()) {
            HttpErrorConfig.ResponseCode.BAD_REQUEST -> signupError.accountExistHeader
            else -> null
          }
        showErrorToast(errorHeader, message = errorMessage)
      }
      AppLog.e(TAG, "Account creation failed", e)
      appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Account creation failed"))
      null
    }
  }

  /**
   * Resets the password for the given email address.
   * @param email The email address to reset the password for
   */
  override suspend fun resetPassword(email: String) {
    AppLog.d(TAG, "resetPassword() called")
    try {
      AppLog.d(TAG, "Checking network availability for resetPassword()")
      val email = email.trim()
      requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
      val response = this.accountRepository.resetPassword(email)
      if (response.isSuccessful) {
        AppLog.d(TAG, "Successfully reset password")
        showSuccessToast(
          ToastStrings.Success.ResetPasswordSuccess.Header,
          ToastStrings.Success.ResetPasswordSuccess.Message(email),
        )
      } else {
        AppLog.e(
          TAG,
          "Failed to reset password: ${response.code()} - ${response.message()}",
        )
        showErrorToast(
          ToastStrings.Error.ResetPasswordError.Header,
          ToastStrings.Error.ResetPasswordError.Message,
        )
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to reset password", e)
      if (e is HttpException) {
        val msg =
          when (e.code()) {
            HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> ToastStrings.Error.NetworkError.Message
            HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR -> ToastStrings.Error.UpdateProfileError.MessageServError
            else -> ToastStrings.Error.ResetPasswordError.Message
          }
        showErrorToast(ToastStrings.Error.ResetPasswordError.Header, msg)
      }
    }
  }

  /**
   * Changes the password for the current account.
   * @param currentPassword The current password
   * @param newPassword The new password to set
   * @return true if the password was changed successfully, false otherwise
   */
  override suspend fun changePassword(
    currentPassword: String,
    newPassword: String,
  ): Boolean {
    AppLog.d(TAG, "changePassword() called")
    return try {
      getCurrentAccount() ?: run {
        AppLog.w(TAG, "No active account found for changePassword(). Returning false.")
        return false
      }
      val accountId = activeAccountFlow.first()?.id ?: return false
      accountRepository.updatePassword(accountId, currentPassword, newPassword)
      AppLog.d(TAG, "Password changed successfully")
      dialogQueueService.showToast(Toast(ToastStrings.Success.ChangePasswordSuccess.Message))
      true
    } catch (e: Exception) {
      AppLog.e(TAG, "Password change failed", e)
      if (e is HttpException) {
        val msg =
          when (e.code()) {
            HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> ToastStrings.Error.NetworkError.Message
            HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR -> ToastStrings.Error.UpdateProfileError.MessageServError
            else -> ToastStrings.Error.UpdateProfileError.updatePasswordFailedMessage
          }
        showErrorToast(ToastStrings.Error.UpdateProfileError.updatePasswordHeader, msg)
      }
      false
    }
  }

  /**
   * Updates the user's profile information with offline support.
   * If online, calls API and marks as synced. If offline, stores locally with isSynced = false.
   * @param profileUpdateRequest The profile data to update
   * @return The updated account or null if update fails
   */
  override suspend fun updateProfile(
    profileUpdateRequest: ProfileUpdateRequest,
    isFromProfile: Boolean,
    showToast: Boolean
  ) {
    try {
      if (isFromProfile) {
        requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
      }
      accountRepository.updateProfile(profileUpdateRequest)
      if (showToast) {
        showSuccessToast(
          ToastStrings.Success.UpdateProfileSuccess.Header,
          ToastStrings.Success.UpdateProfileSuccess.Message,
        )
      }
    } catch (e: HttpException) {
      when (e.code()) {
        HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> {
          // For no internet, we still want to show success since offline updates are allowed
          showSuccessToast(
            ToastStrings.Success.UpdateProfileSuccess.Header,
            ToastStrings.Success.UpdateProfileSuccess.Message,
          )
        }

        else -> {
          // For other errors, show error toast
          val msg = when (e.code()) {
            HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR -> ToastStrings.Error.UpdateProfileError.MessageServError
            HttpErrorConfig.ResponseCode.UNAUTHORIZED -> ToastStrings.Error.UpdateProfileError.errorUpdatingProfileMessage
            HttpErrorConfig.ResponseCode.BAD_REQUEST -> ToastStrings.Error.UpdateProfileError.ErrorUpdatingEmail
            else -> ToastStrings.Error.UpdateProfileError.MessageGeneric
          }
          val header = when (e.code()) {
            HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR -> ToastStrings.Error.UpdateProfileError.Header
            HttpErrorConfig.ResponseCode.UNAUTHORIZED -> ToastStrings.Error.UpdateProfileError.errorUpdatingProfileHeader

            else -> ToastStrings.Error.UpdateProfileError.HeaderGeneric
          }
          showErrorToast(header, msg)
          AppLog.e(TAG, "Profile update failed", e)
          throw e
        }
      }
    }
  }

  override suspend fun updateDashboardType(type: DashboardType) {
    AppLog.d(TAG, "Update Dashboard Type")
    try {
      val accountId = activeAccountFlow.first()?.id ?: return
      accountRepository.updateDashboardType(type.value)
      accountRepository.updateLocalDashboardType(accountId, dashboardType = type)
    } catch (e: Exception) {
      AppLog.d(TAG, "Error updating Dashboard Type", e.toString())
    }
  }

  /**
   * Checks login status for the active account by calling getAccount API if online.
   * If offline or on network/HTTP failure (except 401), falls back to local DB validity. Only 401 marks account expired.
   * @return true if account is valid (online or offline), false if expired or unauthorized
   */
  override suspend fun checkLoginStatusForActiveAccount(): Boolean {
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
  override suspend fun checkLoginStatusForLoggedInAccounts(): Boolean {
    AppLog.d(TAG, "checkLoginStatusForLoggedInAccounts() called")
    if (!isNetworkAvailable()) {
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
          launch {
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

  /**
   * Handles unauthorized logout when token refresh fails.
   * Marks account as expired, removes from storage, and triggers unauthorized logout event.
   * @param accountId The ID of the account to logout
   * @return The affected account or null if not found
   */
  override suspend fun handleUnauthorizedLogout(accountId: String?): Account? {
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
  override suspend fun logout(
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
  override suspend fun logoutAll(): Boolean =
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
   * Deletes the current user account from the server and local storage.
   */
  override suspend fun deleteAccount(
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
  override suspend fun switchAccount(
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

  /**
   * Sets the theme mode for the active account.
   * @param themeMode The ThemeMode to set
   */
  override suspend fun setCurrentThemeMode(themeMode: ThemeMode) {
    AppLog.d(TAG, "setCurrentThemeMode() called with themeMode: $themeMode")
    try {
      accountRepository.setCurrentThemeMode(themeMode)
      AppLog.d(TAG, "Successfully set theme mode to: $themeMode")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to set theme mode", e)
      appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Failed to set theme mode"))
    }
  }

  override suspend fun reset() {
    AppLog.d(TAG, "reset() called. Clearing all storage.")
    try {
      storageClearService.clearAllStorage()
      AppLog.d(TAG, "reset() completed. All storage cleared.")
    } catch (e: Exception) {
      AppLog.e(TAG, "reset() failed during storage clear", e)
      dialogQueueService.showToast(
        Toast(
          title = null,
          message = LoginError.MessageGeneric,
        ),
      )
    }
  }

  /**
   * Refreshes the current account data from the server.
   * Updates the local account data with the latest information from the API.
   * If network is unavailable or API call fails, continues with cached data.
   */
  override suspend fun refreshAccount() {
    try {
      val currentAccount = getCurrentAccount()
      if (currentAccount == null) {
        AppLog.w(TAG, "No active account found for refreshAccount()")
        return
      }
      if (connectivityObserver.getCurrentNetworkState().available) {
        AppLog.d(TAG, "Connection available, updating account from API")
        try {
          val accountInfo = accountRepository.getAccountFromAPI(currentAccount.id)
          accountRepository.syncAccountSettingsWithServer(accountInfo, isOnline = true)
        } catch (e: Exception) {
          AppLog.w(TAG, "Error getting account from API during refresh, using cached data", e.toString())
        }
      } else {
        AppLog.d(TAG, "No network connection available, using cached account data")
      }
    } catch (e: Exception) {
      throw e
    }
  }

  override suspend fun clearSyncTimestampForResync() {
    accountRepository.updateSyncTimeStamp("")
  }

  /**
   * Gets whether the notification alert has been shown for the specified account.
   * @param accountId The account ID to check.
   * @return True if the notification alert has been shown for this account, false otherwise.
   */
  override suspend fun hasShownNotificationAlertForAccount(accountId: String): Boolean {
    AppLog.d(TAG, "hasShownNotificationAlertForAccount() called for accountId: $accountId")
    return try {
      val result = accountRepository.hasShownNotificationAlertForAccount(accountId)
      AppLog.d(TAG, "Notification alert shown status for account $accountId: $result")
      result
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to get notification alert status for account: $accountId", e)
      false
    }
  }

  /**
   * Sets whether the notification alert has been shown for the specified account.
   * @param accountId The account ID to update.
   * @param hasShown Whether the notification alert has been shown.
   */
  override suspend fun setNotificationAlertShownForAccount(accountId: String, hasShown: Boolean) {
    AppLog.d(TAG, "setNotificationAlertShownForAccount() called for accountId: $accountId, hasShown: $hasShown")
    try {
      accountRepository.setNotificationAlertShownForAccount(accountId, hasShown)
      AppLog.d(TAG, "Successfully set notification alert status for account: $accountId")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to set notification alert status for account: $accountId", e)
    }
  }

  /**
   * Extension function to sort accounts: active account first, then others by lastActiveTime descending.
   */
  private fun List<Account>.sortedActiveFirst(): List<Account> {
    AppLog.d(TAG, "sortedActiveFirst() called. Sorting accounts with active account first.")
    val active = this.find { it.isActiveAccount }
    val others =
      this
        .filter { !it.isActiveAccount }
        .sortedByDescending { it.lastActiveTime?.toLongOrNull() ?: 0L }
    return listOfNotNull(active) + others
  }

  /**
   * Emits navigation to MyAccounts event to stop scanning.
   */
  override suspend fun emitNavigateToMyAccounts() {
    appNavigationService.emitAuthEvent(AuthState.NavigateToMyAccounts)
    AppLog.d(TAG, "Emitted NavigateToMyAccounts event")
  }

  /**
   * Emits navigation back from MyAccounts event to start scanning.
   */
  override suspend fun emitNavigateBackFromMyAccounts() {
    appNavigationService.emitAuthEvent(AuthState.NavigateBackFromMyAccounts)
    AppLog.d(TAG, "Emitted NavigateBackFromMyAccounts event")
  }

  private fun showNoNetworkErrorToast() {
    dialogQueueService.showToast(
      Toast(
        title = null,
        message = ToastStrings.Error.NetworkError.Message,
        action = null,
      ),
    )
  }
}

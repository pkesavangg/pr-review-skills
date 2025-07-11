package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.config.HttpErrorConfig
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.domain.model.api.auth.SignupRequest
import com.greatergoods.meapp.domain.model.api.user.AccountToken
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.services.AuthState
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.MaxAccountsReachedException
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.strings.ToastStrings
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.proto.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing account authentication and session state.
 * Handles login, logout, account switching, and token management.
 */
@Singleton
class AccountService
  @Inject
  constructor(
    private val accountRepository: IAccountRepository,
    connectivityObserver: IConnectivityObserver,
    dialogQueueService: IDialogQueueService,
    private val appNavigationService: IAppNavigationService,
    private val storageClearService: StorageClearService,
  ) : BaseService(connectivityObserver, dialogQueueService),
    IAccountService {
    companion object {
      private const val MAX_ACCOUNTS = 10
      private const val TAG = "AccountService"
    }

    // region Public Properties

    /**
     * Flow emitting authentication state changes (login, logout, errors, etc).
     */
    override val authEvent = appNavigationService.authEvent

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

    // endregion

    // region Public Functions

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
        AppLog.d(TAG, "login() called for email: $email")
        val isExistingAccount = getLoggedInAccounts().any { it.email == email }
        if (hasReachedMaxAccounts.first() && !isExistingAccount) {
          AppLog.w(TAG, "Max accounts reached. Cannot login new account: $email")
          throw MaxAccountsReachedException()
        }
        val savedAccount = accountRepository.login(email, password)
        appNavigationService.emitAuthEvent(AuthState.LoggedIn(savedAccount))
        AppLog.d(TAG, "login() successful for email: $email")
        savedAccount
      } catch (e: HttpException) {
        val header = ToastStrings.Error.LoginError.Header
        val msg =
          when (e.code()) {
            HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> ToastStrings.Error.LoginError.MessageNoConn
            HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR -> ToastStrings.Error.LoginError.MessageServError
            HttpErrorConfig.ResponseCode.UNAUTHORIZED -> ToastStrings.Error.LoginError.MessageNotAuth
            else -> ToastStrings.Error.LoginError.MessageGeneric
          }
        showErrorToast(header, msg)
        AppLog.e(TAG, "Login failed", e.toString())
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
      AppLog.d(TAG, "signup() called for email: ${request.email}")
      if (hasReachedMaxAccounts.first()) {
        AppLog.w(TAG, "Max accounts reached. Cannot signup new account: ${request.email}")
        appNavigationService.emitAuthEvent(AuthState.Error("Maximum account limit reached"))
        throw MaxAccountsReachedException()
      }
      return try {
        val savedAccount = accountRepository.signup(request)
        appNavigationService.emitAuthEvent(AuthState.AccountAdded(savedAccount))
        AppLog.d(TAG, "signup() successful for email: ${request.email}")
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
              else -> signupError.Header
            }
          showErrorToast(errorHeader, errorMessage)
        }
        AppLog.e(TAG, "Account creation failed", e.toString())
        appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Account creation failed"))
        null
      }
    }

    /**
     * Resets the password for the given email address.
     * @param email The email address to reset the password for
     */
    override suspend fun resetPassword(email: String) {
      AppLog.d(TAG, "resetPassword() called for email: $email")
      try {
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
      } catch (e: HttpException) {
        AppLog.e(TAG, "Failed to reset password", e.toString())
        showErrorToast(
          ToastStrings.Error.ResetPasswordError.Header,
          ToastStrings.Error.ResetPasswordError.Message,
        )
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
        accountRepository.updatePassword(activeAccountFlow.first()!!.id, currentPassword, newPassword)
        AppLog.d(TAG, "Password changed successfully")
        showSuccessToast(
          ToastStrings.Success.ChangePasswordSuccess.Header,
          ToastStrings.Success.ChangePasswordSuccess.Message,
        )
        true
      } catch (e: Exception) {
        AppLog.e(TAG, "Password change failed", e.toString())
        if (e is HttpException) {
          val header = ToastStrings.Error.ChangePasswordError.Header
          val msg =
            when (e.code()) {
              HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> ToastStrings.Error.UpdateProfileError.MessageNoConn
              HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR -> ToastStrings.Error.UpdateProfileError.MessageServError
              HttpErrorConfig.ResponseCode.UNAUTHORIZED -> ToastStrings.Error.UpdateProfileError.MessageNotAuth
              else -> ToastStrings.Error.UpdateProfileError.MessageGeneric
            }
          showErrorToast(header, msg)
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
    override suspend fun updateProfile(profileUpdateRequest: ProfileUpdateRequest) =
      try {
       accountRepository.updateProfile(profileUpdateRequest)
        showSuccessToast(
          ToastStrings.Success.UpdateProfileSuccess.Header,
          ToastStrings.Success.UpdateProfileSuccess.Message,
        )
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
            val header = ToastStrings.Error.UpdateProfileError.Header
            val msg = when (e.code()) {
              HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR -> ToastStrings.Error.UpdateProfileError.MessageServError
              HttpErrorConfig.ResponseCode.UNAUTHORIZED -> ToastStrings.Error.UpdateProfileError.MessageNotAuth
              else -> ToastStrings.Error.UpdateProfileError.MessageGeneric
            }
            showErrorToast(header, msg)
            AppLog.e(TAG, "Profile update failed", e.toString())
            throw e
          }
        }
      }

    /**
     * Checks login status for the active account by calling getAccount API if online.
     * If offline, checks local DB for isExpired status.
     * @return true if account is valid (online or offline), false if expired
     */
    override suspend fun checkLoginStatusForActiveAccount(): Boolean {
      AppLog.d(TAG, "checkLoginStatusForActiveAccount() called")
      if (!isNetworkAvailable()) {
        AppLog.d(TAG, "Offline mode: checking local DB for active account validity.")
        val activeAccount = getCurrentAccount()
        if (activeAccount == null) {
          AppLog.d(TAG, "No active account found in offline mode. Returning false.")
          return false
        }
        if (activeAccount.isExpired) {
          AppLog.d(TAG, "Active account is expired in local DB. Returning false.")
          return false
        }
        AppLog.d(TAG, "Active account is valid in local DB. Returning true.")
        return true
      }
      // Online mode: keep existing logic
      return try {
        AppLog.d(TAG, "Checking network availability for checkLoginStatusForActiveAccount()")
        requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
        // from local storage
        val activeAccount = getCurrentAccount()
        if (activeAccount == null) {
          AppLog.d(TAG, "No active account found in checkLoginStatusForActiveAccount(). Returning false.")
          return false
        }
        AppLog.d(TAG, "Checking login status for active account: ${activeAccount.id}")
        //from api
        val accountInfo = accountRepository.getAccountFromAPI(activeAccount.id)
        // Sync all settings with server data
        accountRepository.syncAccountSettingsWithServer(accountInfo)
        AppLog.d(TAG, "Active account login status check successful")
        true
      } catch (e: Exception) {
        AppLog.e(TAG, "Active account login status check failed", e.toString())
        false
      }
    }

    /**
     * Checks login status for all logged-in accounts (non-active) by calling getAccount API if online.
     * If offline, checks local DB for isExpired status for all accounts.
     * @return true if all accounts are valid (online or offline), false if any are expired
     */
    override suspend fun checkLoginStatusForLoggedInAccounts(): Boolean {
      AppLog.d(TAG, "checkLoginStatusForLoggedInAccounts() called")
      if (!isNetworkAvailable()) {
        AppLog.d(TAG, "Offline mode: checking local DB for all logged-in accounts validity.")
        val loggedInAccounts = getLoggedInAccounts().filter { !it.isActiveAccount }
        if (loggedInAccounts.isEmpty()) {
          AppLog.d(TAG, "No non-active logged-in accounts found in offline mode. Returning true.")
          return true
        }
        val anyExpired = loggedInAccounts.any { it.isExpired }
        if (anyExpired) {
          AppLog.d(TAG, "At least one logged-in account is expired in local DB. Returning false.")
          return false
        }
        AppLog.d(TAG, "All logged-in accounts are valid in local DB. Returning true.")
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
          return true
        }
        for (account in loggedInAccounts) {
          try {
            AppLog.d(TAG, "Checking login status for account: ${account.id}")
            // Get account info from API and update tokens for background operations
            val accountInfo = accountRepository.getAccountFromAPI(account.id)
            // Update account data with API response
            accountRepository.updateAccountInfo(account.id, accountInfo)
            AppLog.d(TAG, "Account ${account.id} login status check successful")
          } catch (e: Exception) {
            AppLog.e(TAG, "Account ${account.id} login status check failed", e.toString())
            // Mark account as expired in database
            accountRepository.markAccountExpired(account.id)
            // Clear tokens for this account
            accountRepository.removeAccount(account.id)
          }
        }
        AppLog.d(TAG, "Logged-in accounts status check completed.")
        true
      } catch (e: Exception) {
        AppLog.e(TAG, "Logged-in accounts status check failed", e.toString())
        false
      }
    }

    /**
     * Handles unauthorized logout when token refresh fails.
     * Marks account as expired, removes from storage, and triggers unauthorized logout event.
     * @param accountId The ID of the account to logout
     * @return The affected account or null if not found
     */
    override suspend fun handleUnauthorizedLogout(accountId: String?): Account? {
      AppLog.d(TAG, "handleUnauthorizedLogout() called for accountId: $accountId")
      if (accountId.isNullOrEmpty()) {
        AppLog.w(TAG, "No account ID available for unauthorized logout")
        return null
      }

      return try {
        AppLog.d(TAG, "Handling unauthorized logout for account: $accountId")
        val account = getCurrentAccount()
        return if (account?.isActiveAccount == true && accountId == account.id) {
          // Mark account as expired in database
          accountRepository.markAccountExpired(accountId)
          // Clear account tokens from DataStore
          accountRepository.clearAccountTokens(accountId)
          AppLog.d(TAG, "Unauthorized logout completed for account: $accountId")
          account
        } else {
          null
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during unauthorized logout for account: $accountId", e.toString())
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
        AppLog.d(TAG, "logout() called for accountId: $accountId")
        val isActiveAccount = getCurrentAccount()?.id == accountId
        val result = accountRepository.logoutAccount(accountId, fcmToken, isActiveAccount)
        AppLog.d(TAG, "Logout successful")
        appNavigationService.emitAuthEvent(AuthState.LoggedOut(isActiveAccount))
        result
      } catch (e: Exception) {
        AppLog.e(TAG, "Logout failed", e.toString())
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
        val result = accountRepository.logoutAllAccounts()
        AppLog.d(TAG, "All accounts logged out successfully")
        appNavigationService.emitAuthEvent(AuthState.LoggedOut(true))
        result
      } catch (e: Exception) {
        AppLog.e(TAG, "Logout all failed", e.toString())
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
      AppLog.d(TAG, "deleteAccount() called for accountId: $accountID, isActiveAccount: $isActiveAccount")
      AppLog.d(TAG, "Checking network availability for deleteAccount()")
      requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
      try {
        accountRepository.deleteAccount(accountID, isActiveAccount)
        appNavigationService.emitAuthEvent(AuthState.AccountDeleted(isActiveAccount))
        AppLog.d(TAG, "Account deleted successfully")
      } catch (e: Exception) {
        AppLog.e(TAG, "Account deletion failed", e.toString())
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
    ): Boolean =
      try {
        AppLog.d(TAG, "switchAccount() called for accountId: ${account.id}, showToast: $showToast")
        requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
        // Switch to the account using the repository method
        accountRepository.switchToAccount(account.id)
         AppLog.d(TAG, "Successfully switched to account: ${account.email}")
        appNavigationService.emitAuthEvent(AuthState.AccountSwitched(account, showToast))
        true
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to switch account", e.toString())
        appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Failed to switch account"))
        false
      }

    /**
     * Updates the account's tokens.
     * @param tokens New token data
     * @return true if update was successful
     */
    override suspend fun updateTokens(tokens: AccountToken): Boolean =
      try {
        AppLog.d(TAG, "updateTokens() called for accountId: ${tokens.accountId}")
        accountRepository.updateTokens(tokens)
        AppLog.d(TAG, "Tokens updated successfully")
        appNavigationService.emitAuthEvent(AuthState.TokensUpdated)
        true
      } catch (e: Exception) {
        AppLog.e(TAG, "Token update failed", e.toString())
        appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Token update failed"))
        false
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
        AppLog.e(TAG, "Failed to set theme mode", e.toString())
        appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Failed to set theme mode"))
      }
    }

    override suspend fun reset() {
      AppLog.d(TAG, "reset() called. Clearing all storage.")
      try {
        storageClearService.clearAllStorage()
        AppLog.d(TAG, "reset() completed. All storage cleared.")
      } catch (e: Exception) {
        AppLog.e(TAG, "reset() failed during storage clear", e.toString())
        dialogQueueService.showToast(
          Toast(
            title = null,
            message = ToastStrings.Error.LoginError.MessageGeneric,
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
          accountRepository.syncAccountSettingsWithServer(accountInfo)
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
  }

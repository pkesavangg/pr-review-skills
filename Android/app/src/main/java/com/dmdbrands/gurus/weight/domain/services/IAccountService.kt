package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.proto.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for account service. Provides authentication, account management, and profile operations.
 */
interface IAccountService {
  /**
   * Flow emitting the currently active account, or null if none is active.
   */
  val activeAccountFlow: Flow<Account?>

  /**
   * Flow emitting authentication state changes (login, logout, errors, etc).
   */
  val authEvent: SharedFlow<AuthState>

  /**
   * Flow indicating whether the maximum number of accounts has been reached.
   */
  val hasReachedMaxAccounts: Flow<Boolean>

  /**
   * Flow emitting the list of all logged-in accounts, with the active account first.
   */
  val loggedInAccountsFlow: Flow<List<Account>>

  /**
   * Flow for triggering integration checks, similar to Angular BehaviorSubject.
   * Emits true when login or checkLoginStatusForLoggedInAccounts is called.
   */
  val checkIntegrations: StateFlow<Boolean>

  val activeAccount: StateFlow<Account?>

  fun subscribeAccount()

  /**
   * Logs in a user with the provided email and password.
   * @param email User's email
   * @param password User's password
   * @return The authenticated [Account] or null if login fails
   */
  suspend fun login(
    email: String,
    password: String,
  ): Account?

  /**
   * Adds a new account using the provided request data.
   * @param request Map containing account creation data
   * @return The created [Account] or null if creation fails
   * @throws MaxAccountsReachedException if the maximum number of accounts is reached
   */
  suspend fun signup(request: SignupRequest): Account?

  /**
   * Resets the password for the given email address.
   * @param email The email address to reset the password for
   */
  suspend fun resetPassword(email: String)

  /**
   * Changes the password for the current account.
   * @param currentPassword The current password
   * @param newPassword The new password to set
   * @return true if the password was changed successfully, false otherwise
   */
  suspend fun changePassword(
    currentPassword: String,
    newPassword: String,
  ): Boolean

  /**
   * Gets the currently active account, or null if none is active.
   * @return The active [Account] or null
   */
  suspend fun getCurrentAccount(): Account?

  /**
   * Updates the user's profile information by calling the API and updating local data.
   * @param profileUpdateRequest The profile data to update
   * @return The updated [Account] or null if update fails
   */
  suspend fun updateProfile(
    profileUpdateRequest: ProfileUpdateRequest,
    isFromProfile: Boolean = false,
    showToast: Boolean = true
  )

  suspend fun refreshAccount()

  /**
   * Checks login status for the active account by calling the API and updating local data.
   * On network/HTTP failure (except 401), falls back to local DB validity. Only 401 marks account expired.
   * @return true if the account is still valid, false if expired or unauthorized
   */
  suspend fun checkLoginStatusForActiveAccount(): Boolean

  /**
   * Checks login status for all logged-in (non-active) accounts by calling the API and updating local data.
   * This is a best-effort check that refreshes account data and cleans up invalid accounts.
   * Accounts that return 401 Unauthorized are marked as expired and removed.
   * On network/HTTP failure, falls back to local DB validity (does not fail the check).
   * @return true if the check completed (regardless of whether individual accounts were expired/removed),
   *         false only if a fatal error prevented the check from completing
   */
  suspend fun checkLoginStatusForLoggedInAccounts(): Boolean

  /**
   * Gets the list of all logged-in accounts, with the active account first.
   * @return List of [Account]s
   */
  suspend fun getLoggedInAccounts(): List<Account>

  /**
   * Switches to the specified account.
   * Validates account via API before switching, prevents switching when offline,
   * and syncs server settings with local data after successful switch.
   *
   * @param account The [Account] to switch to
   * @param showToast Whether to show a toast notification after switching (default: false)
   * @return true if the switch was successful, false otherwise
   *
   * @throws Exception if network is unavailable (offline prevention)
   *
   * Behavior:
   * - Checks network availability; returns false if offline
   * - Validates account via API call (getAccountFromAPI)
   * - On validation error (401 Unauthorized): marks account as expired, logs it out, shows error
   * - On validation error (other HTTP errors): shows generic error toast
   * - On success: switches account, syncs server settings with local data
   * - Account sync continues even if sync fails (graceful degradation)
   */
  suspend fun switchAccount(
    account: Account,
    showToast: Boolean = false,
  ): Boolean

  suspend fun updateDashboardType(type: DashboardType)

  /**
   * Handles unauthorized logout when token refresh fails. Marks account as expired, removes from storage, and triggers unauthorized logout event.
   * @param accountId The ID of the account to logout
   * @return The affected [Account] or null if not found
   */
  suspend fun handleUnauthorizedLogout(accountId: String?): Account?

  /**
   * Logs out the specified account.
   * @param accountId ID of the account to log out
   * @param fcmToken FCM token for push notifications (optional)
   * @return true if logout was successful, false otherwise
   */
  suspend fun logout(
    accountId: String,
    fcmToken: String?,
  ): Boolean

  /**
   * Logs out all accounts from the device.
   * @return true if all accounts were logged out successfully, false otherwise
   */
  suspend fun logoutAll(): Boolean

  // Theme Mode Operations
  /**
   * Gets the current theme mode for the active account as a flow.
   * @return Flow of ThemeMode that emits changes
   */
  val currentThemeModeFlow: Flow<ThemeMode>

  /**
   * Sets the theme mode for the active account.
   * @param themeMode The ThemeMode to set
   */
  suspend fun setCurrentThemeMode(themeMode: ThemeMode)

  /**
   * Deletes the current user account from the server and local storage.
   */
  suspend fun deleteAccount(accountID: String, isActiveAccount: Boolean)
  suspend fun reset()

  /**
   * Gets whether the notification alert has been shown for the specified account.
   * @param accountId The account ID to check.
   * @return True if the notification alert has been shown for this account, false otherwise.
   */
  suspend fun hasShownNotificationAlertForAccount(accountId: String): Boolean

  /**
   * Sets whether the notification alert has been shown for the specified account.
   * @param accountId The account ID to update.
   * @param hasShown Whether the notification alert has been shown.
   */
  suspend fun setNotificationAlertShownForAccount(accountId: String, hasShown: Boolean)

  /**
   * Emits navigation to MyAccounts event to stop scanning.
   */
  suspend fun emitNavigateToMyAccounts()

  /**
   * Emits navigation back from MyAccounts event to start scanning.
   */
  suspend fun emitNavigateBackFromMyAccounts()
}

/**
 * Sealed class representing different authentication states.
 */
sealed class AuthState {

  /**
   * Emitted by LoadingScreenViewModel after migration + loadData + autoLogin.
   * AppViewModel handles by starting observers only (no navigation or full initLoadingData).
   */
  data class LoggedInFromLoading(
    val account: Account,
  ) : AuthState()

  data class LoggedOut(
    val isActiveAccount: Boolean,
    val message: String? = null,
  ) : AuthState()

  data class UnauthorizedLogout(
    val accountId: String,
  ) : AuthState()

  data class AccountAdded(
    val account: Account,
  ) : AuthState()

  data class AccountSwitched(
    val account: Account,
    val showToast: Boolean,
  ) : AuthState()

  data class ProfileUpdated(
    val account: Account,
  ) : AuthState()

  object TokensUpdated : AuthState()

  data class Error(
    val message: String,
  ) : AuthState()

  data class AccountDeleted(
    val isActiveAccount: Boolean,
    val message: String? = null,
  ) : AuthState()

  data object NavigateToMyAccounts : AuthState()

  data object NavigateBackFromMyAccounts : AuthState()
}

/**
 * Custom exception for authentication-related errors.
 */
class AuthException(
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause)

class MaxAccountsReachedException : Exception("Maximum number of accounts reached.")

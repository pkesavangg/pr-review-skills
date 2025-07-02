package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.domain.model.PartialAccount
import com.greatergoods.meapp.domain.model.api.auth.SignupRequest
import com.greatergoods.meapp.domain.model.api.user.AccountToken
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.model.storage.Account.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

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
    suspend fun updateProfile(profileUpdateRequest: ProfileUpdateRequest): Account?

    /**
     * Checks login status for the active account by calling the API and updating local data.
     * @return true if the account is still valid, false if expired or network unavailable
     */
    suspend fun checkLoginStatusForActiveAccount(): Boolean

    /**
     * Checks login status for all logged-in (non-active) accounts by calling the API and updating local data.
     * @return true if all accounts are valid, false if any account is expired
     */
    suspend fun checkLoginStatusForLoggedInAccounts(): Boolean

    /**
     * Gets the list of all logged-in accounts, with the active account first.
     * @return List of [Account]s
     */
    suspend fun getLoggedInAccounts(): List<Account>

    /**
     * Switches to the specified account.
     * @param account The [Account] to switch to
     * @param showToast Whether to show a toast notification after switching (default: false)
     * @return true if the switch was successful, false otherwise
     */
    suspend fun switchAccount(
        account: Account,
        showToast: Boolean = false,
    ): Boolean

    /**
     * Updates the user's profile information in the local database only.
     * @param accountId The ID of the account to update
     * @param partialAccount The partial account data to update
     * @return The updated [Account]
     */
    suspend fun updateProfileInDB(
        accountId: String,
        partialAccount: PartialAccount,
    ): Account

    /**
     * Updates the account's tokens in the local database.
     * @param tokens Map of token values to update
     * @return true if update was successful, false otherwise
     */
    suspend fun updateTokens(tokens: AccountToken): Boolean

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
}

/**
 * Sealed class representing different authentication states.
 */
sealed class AuthState {
    data class LoggedIn(
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
}

/**
 * Custom exception for authentication-related errors.
 */
class AuthException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class MaxAccountsReachedException : Exception("Maximum number of accounts reached.")

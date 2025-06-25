package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Interface for account authentication service.
 */
interface IAccountAuthService {
    val authStateFlow: SharedFlow<AuthState>
    val activeAccountFlow: Flow<Account?>
    val activeAccountIdFlow: Flow<String?>
    val loggedInAccountsFlow: Flow<List<Account>>
    val isSignUpFlow: SharedFlow<Boolean>
    val isLoginFlow: SharedFlow<Boolean>
    val isSwitchAccountFlow: SharedFlow<Boolean>

    suspend fun login(email: String, password: String): Account?
    suspend fun logout(accountId: String): Boolean
    suspend fun logoutAll(): Boolean
    suspend fun addAccount(request: Map<String, Any>): Account?
    suspend fun removeAccount(accountId: String): Boolean
    suspend fun switchAccount(account: Account): Boolean
    suspend fun getCurrentAccount(): Account?
    suspend fun getLoggedInAccounts(): List<Account>
    suspend fun isSessionValid(): Boolean
    suspend fun refreshSession(): Boolean
    suspend fun updateTokens(tokens: Map<String, String>): Boolean
    suspend fun checkForLoggedInUser(): Boolean

    suspend fun resetPassword(email: String)
    suspend fun updateProfile(profileData: Map<String, Any>): Account?
    suspend fun changePassword(currentPassword: String, newPassword: String): Boolean

    /**
     * Checks login status for the active account by calling getAccount API.
     * Updates the account data in DB with the response and refreshes tokens if needed.
     * @return true if account is still valid, false if expired
     */
    suspend fun checkLoginStatusForActiveAccount(): Boolean

    /**
     * Checks login status for all logged-in accounts (non-active) by calling getAccount API.
     * Updates account data in DB with responses and refreshes tokens if needed.
     * For expired accounts, marks them as expired and clears tokens.
     * @return true if all accounts are valid, false if any account is expired
     */
    suspend fun checkLoginStatusForLoggedInAccounts(): Boolean
}

/**
 * Sealed class representing different authentication states.
 */
sealed class AuthState {
    data class LoggedIn(val account: Account) : AuthState()
    data class LoggedOut(val message: String? = null) : AuthState()
    data class AccountAdded(val account: Account) : AuthState()
    data class AccountRemoved(val accountId: String) : AuthState()
    data class AccountSwitched(val account: Account) : AuthState()
    data class SessionRefreshed(val account: Account) : AuthState()
    data class ProfileUpdated(val account: Account) : AuthState()
    object TokensUpdated : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Custom exception for authentication-related errors.
 */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

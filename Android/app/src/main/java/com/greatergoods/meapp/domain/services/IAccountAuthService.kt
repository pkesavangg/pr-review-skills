package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.data.storage.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Interface for account authentication service.
 */
interface IAccountAuthService {
    val authStateFlow: SharedFlow<AuthState>
    val activeAccountFlow: Flow<AccountEntity?>
    val loggedInAccountsFlow: Flow<List<AccountEntity>>
    val isSignUpFlow: SharedFlow<Boolean>
    val isLoginFlow: SharedFlow<Boolean>
    val isSwitchAccountFlow: SharedFlow<Boolean>

    suspend fun login(email: String, password: String): AccountEntity
    suspend fun logout(accountId: String)
    suspend fun logoutAll()
    suspend fun addAccount(accountData: Map<String, Any>): AccountEntity
    suspend fun removeAccount(accountId: String)
    suspend fun switchAccount(account: AccountEntity)
    suspend fun getCurrentAccount(): AccountEntity?
    suspend fun isSessionValid(): Boolean
    suspend fun refreshSession()
    suspend fun updateTokens(tokens: Map<String, String>)
    suspend fun checkForLoggedInUser()
}

/**
 * Sealed class representing different authentication states.
 */
sealed class AuthState {
    data class LoggedIn(val account: AccountEntity) : AuthState()
    data class LoggedOut(val message: String? = null) : AuthState()
    data class AccountAdded(val account: AccountEntity) : AuthState()
    data class AccountRemoved(val accountId: String) : AuthState()
    data class AccountSwitched(val account: AccountEntity) : AuthState()
    data class SessionRefreshed(val account: AccountEntity) : AuthState()
    object TokensUpdated : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Custom exception for authentication-related errors.
 */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause) 
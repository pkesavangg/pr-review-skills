package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.domain.model.PartialAccount
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.model.storage.Account.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Interface for account service.
 */
interface IAccountService {
    val authEvent: SharedFlow<AuthState>
    val activeAccountFlow: Flow<Account?>
    val loggedInAccountsFlow: Flow<List<Account>>
    val isSignUpFlow: SharedFlow<Boolean>
    val isLoginFlow: SharedFlow<Boolean>
    val isSwitchAccountFlow: SharedFlow<Boolean>

    suspend fun login(
        email: String,
        password: String,
    ): Account?

    suspend fun logout(
        accountId: String,
        fcmToken: String?,
    ): Boolean

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

    suspend fun updateProfile(profileUpdateRequest: ProfileUpdateRequest): Account?

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): Boolean

    suspend fun checkLoginStatusForActiveAccount(): Boolean

    suspend fun checkLoginStatusForLoggedInAccounts(): Boolean

    suspend fun updateProfileInDB(
        accountId: String,
        partialAccount: PartialAccount,
    ): Account?
}

/**
 * Sealed class representing different authentication states.
 */
sealed class AuthState {
    data class LoggedIn(
        val account: Account,
    ) : AuthState()

    data class LoggedOut(
        val message: String? = null,
    ) : AuthState()

    data class AccountAdded(
        val account: Account,
    ) : AuthState()

    data class AccountRemoved(
        val accountId: String,
    ) : AuthState()

    data class AccountSwitched(
        val account: Account,
    ) : AuthState()

    data class SessionRefreshed(
        val account: Account,
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

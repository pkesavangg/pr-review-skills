package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.data.storage.db.entity.account.Account
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.user.CreateAccountRequest
import com.greatergoods.meapp.domain.model.api.user.Token
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user account operations.
 * Provides methods for account authentication, management, and data synchronization.
 */
interface IAccountRepository {
    // API Operations
    suspend fun loginInAPI(email: String, password: String): LoginResponse
    suspend fun signupInAPI(request: CreateAccountRequest): Map<String, Any>
    suspend fun logoutInAPI(fcmToken: String?)
    suspend fun getAccountInAPI(): Map<String, Any>
    suspend fun updatePasswordInAPI(oldPassword: String, newPassword: String): Map<String, Any>
    suspend fun resetPasswordInAPI(email: String): Map<String, Any>
    suspend fun refreshTokenInAPI(refreshToken: String): Token

    // DB Operations
    suspend fun addAccountInDB(account: Account): Account
    suspend fun removeAccountInDB(accountId: String)
    suspend fun removeAllAccountsInDB()
    suspend fun getStoredActiveAccountFromDB(): Account?
    suspend fun deactivateOtherAccountsInDB(accountId: String)
    fun getLoggedInAccountsFromDB(): Flow<List<Account>>
    suspend fun updateTokensInDB(tokens: Map<String, String>)
    suspend fun updateLastActiveTimeInDB(accountId: String)
}

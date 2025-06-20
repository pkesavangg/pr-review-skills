package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.domain.model.Account
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.user.AccountResponse
import com.greatergoods.meapp.domain.model.api.user.CreateAccountRequest
import com.greatergoods.meapp.domain.model.api.user.Token
import kotlinx.coroutines.flow.Flow
import retrofit2.Response

/**
 * Repository interface for managing user account operations.
 * Provides methods for account authentication, management, and data synchronization.
 */
interface IAccountRepository {
    // API Operations
    /**
     * Logs in via API and returns LoginResponse.
     */
    suspend fun loginInAPI(email: String, password: String): LoginResponse

    /**
     * Signs up via API and returns LoginResponse.
     */
    suspend fun signupInAPI(request: CreateAccountRequest): LoginResponse

    /**
     * Logs out via API.
     */
    suspend fun logoutInAPI(fcmToken: String?)

    /**
     * Logs out in the database.
     */
    suspend fun logOutInDb(accountId: String)

    /**
     * Gets account info via API and returns AccountResponse.
     */
    suspend fun getAccountInAPI(): AccountResponse

    /**
     * Updates password via API and returns true if successful.
     */
    suspend fun updatePasswordInAPI(oldPassword: String, newPassword: String): Boolean

    /**
     * Requests password reset via API and returns true if successful.
     */
    suspend fun resetPasswordInAPI(email: String): Response<Unit>

    /**
     * Refreshes the token via API and returns a Token.
     */
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

    suspend fun updateSyncTimeStamp(timeStamp: String)
    suspend fun getSyncTimeStamp(): Flow<String>
}

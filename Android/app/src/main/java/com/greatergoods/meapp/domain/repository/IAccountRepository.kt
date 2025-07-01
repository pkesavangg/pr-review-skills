package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.domain.model.PartialAccount
import com.greatergoods.meapp.domain.model.api.auth.ChangePasswordResponse
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.auth.SignupRequest
import com.greatergoods.meapp.domain.model.api.user.AccountInfo
import com.greatergoods.meapp.domain.model.api.user.AccountResponse
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.model.api.user.Token
import com.greatergoods.meapp.domain.model.storage.Account.Account
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
    suspend fun loginInAPI(
        email: String,
        password: String,
    ): LoginResponse

    /**
     * Signs up via API and returns LoginResponse.
     */
    suspend fun signup(request: SignupRequest): LoginResponse

    /**
     * Logs out via API for a specific account.
     * @param fcmToken Optional FCM token to unregister
     * @param accountId The account ID to logout
     */
    suspend fun logoutInAPI(
        fcmToken: String?,
        accountId: String,
    )

    /**
     * Logs out in the database.
     */
    suspend fun logOutInDb(accountId: String)

    /**
     * Gets account info via API for a specific account and returns AccountResponse.
     * @param accountId The account ID to get info for
     * @return AccountInfo for the specified account
     */
    suspend fun getAccountInAPI(accountId: String): AccountInfo

    /**
     * Updates password via API and returns true if successful.
     */
    suspend fun updatePasswordInAPI(
        oldPassword: String,
        newPassword: String,
    ): ChangePasswordResponse

    /**
     * Requests password reset via API and returns true if successful.
     */
    suspend fun resetPasswordInAPI(email: String): Response<Unit>

    /**
     * Updates profile via API and returns AccountResponse
     */
    suspend fun updateProfileInAPI(profileData: ProfileUpdateRequest): AccountResponse

    /**
     * Refreshes the token via API and returns a Token.
     * @param refreshToken The refresh token to use
     * @param accountId The account ID to associate with the refreshed token (optional)
     * @return Token object with refreshed tokens
     */
    suspend fun refreshTokenInAPI(
        refreshToken: String,
        accountId: String? = null,
    ): Token

    // DB Operations
    suspend fun addAccountInDB(account: Account): Account

    suspend fun updateAccountInDB(
        accountId: String,
        partialUpdate: PartialAccount,
    ): Account

    suspend fun logoutInDb(accountId: String)

    suspend fun logoutAllAccountsInDb()

    suspend fun removeAccountInDB(accountId: String)

    suspend fun removeAllAccountsInDB()

    suspend fun deactivateOtherAccountsInDB(accountId: String)

    suspend fun deactivateAllAccountsInDB()

    suspend fun activateAccountInDB(accountId: String)

    suspend fun updateTokensInDB(tokens: Map<String, String>)

    suspend fun updateLastActiveTimeInDB(accountId: String)

    suspend fun getSyncTimeStamp(): Flow<String>

    suspend fun updateSyncTimeStamp(timeStamp: String)

    suspend fun updateAccountFromAPI(
        accountId: String,
        accountInfo: AccountInfo,
    ): Account

    suspend fun markAccountExpired(accountId: String)

    fun getLoggedInAccountsFromDB(): Flow<List<Account>>

    fun getStoredActiveAccountFromDB(): Flow<Account?>

    /**
     * Gets all accounts with unsynced data (isSynced = false) from the database.
     * Used by offline handler service to sync pending changes.
     * @return List of accounts that need to be synced
     */
    suspend fun getUnsyncedAccountsFromDB(): List<Account>
}

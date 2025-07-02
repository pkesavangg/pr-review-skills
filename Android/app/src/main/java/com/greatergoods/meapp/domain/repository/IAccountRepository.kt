package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.domain.model.PartialAccount
import com.greatergoods.meapp.domain.model.api.auth.ChangePasswordResponse
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.auth.SignupRequest
import com.greatergoods.meapp.domain.model.api.user.AccountInfo
import com.greatergoods.meapp.domain.model.api.user.AccountToken
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
    suspend fun login(
        email: String,
        password: String,
    ): LoginResponse

    /**
     * Signs up via API and returns LoginResponse.
     */
    suspend fun signup(request: SignupRequest): LoginResponse

    /**
     * Gets account info via API for a specific account and returns AccountResponse.
     * @param accountId The account ID to get info for
     * @return AccountInfo for the specified account
     */
    suspend fun getAccount(accountId: String): AccountInfo

    /**
     * Updates password via API and returns true if successful.
     */
    suspend fun updatePassword(
        oldPassword: String,
        newPassword: String,
    ): ChangePasswordResponse

    /**
     * Requests password reset via API and returns true if successful.
     */
    suspend fun resetPassword(email: String): Response<Unit>

    /**
     * Updates profile via API and returns AccountResponse
     */
    suspend fun updateProfile(profileData: ProfileUpdateRequest): Account

    /**
     * Refreshes the token via API and returns a Token.
     * @param refreshToken The refresh token to use
     * @param accountId The account ID to associate with the refreshed token (optional)
     * @return Token object with refreshed tokens
     */
    suspend fun refreshToken(
        refreshToken: String,
        accountId: String? = null,
    ): Token

    // DB Operations
    suspend fun addAccount(account: Account): Account

    suspend fun updateAccount(
        accountId: String,
        partialUpdate: PartialAccount,
    ): Account

    suspend fun deactivateOtherAccounts(accountId: String)

    suspend fun activateAccount(accountId: String)

    /**
     * Updates tokens for the active account in the TokenManager.
     * @param request The token update request containing all token fields
     */
    suspend fun updateTokens(request: AccountToken)

    suspend fun updateLastActiveTime(accountId: String)

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

    /**
     * Logs out the account both remotely (API) and locally (DB, tokens).
     * @param accountId The ID of the account to log out
     * @param fcmToken The FCM token for push notifications (optional)
     * @param isActiveAccount Whether this is the active account
     * @return true if logout was successful, false otherwise
     */
    suspend fun logoutAccount(
        accountId: String,
        fcmToken: String?,
        isActiveAccount: Boolean,
    ): Boolean

    /**
     * Logs out all accounts both remotely (API) and locally (DB, tokens).
     * @return true if all accounts were logged out successfully, false otherwise
     */
    suspend fun logoutAllAccounts(): Boolean
}

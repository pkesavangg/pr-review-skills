package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.core.network.ITokenManager
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.data.api.IUserAPI
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.account.AccountEntityMapper
import com.greatergoods.meapp.domain.model.Account
import com.greatergoods.meapp.domain.model.api.auth.ChangePasswordResponse
import com.greatergoods.meapp.domain.model.api.auth.LoginRequest
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.auth.LogoutRequest
import com.greatergoods.meapp.domain.model.api.auth.PasswordResetRequest
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenRequest
import com.greatergoods.meapp.domain.model.api.user.AccountResponse
import com.greatergoods.meapp.domain.model.api.auth.ChangePasswordRequest
import com.greatergoods.meapp.domain.model.api.user.AccountInfo
import com.greatergoods.meapp.domain.model.api.user.CreateAccountRequest
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.model.api.user.Token
import com.greatergoods.meapp.domain.repository.IAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of the IAccountRepository interface.
 * Handles account operations using Room database and API calls.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val userDataStore: UserDataStore,
    private val tokenManager: ITokenManager,
    private val authAPI: IAuthAPI,
    private val userAPI: IUserAPI,
) : IAccountRepository {

    companion object {
        private const val TAG = "AccountRepository"
    }

    // API Operations
    /**
     * Logs in via API and returns LoginResponse.
     */
    override suspend fun loginInAPI(email: String, password: String): LoginResponse {
        return authAPI.login(LoginRequest(email, password))
    }

    /**
     * Signs up via API and returns LoginResponse.
     */
    override suspend fun signupInAPI(request: CreateAccountRequest): LoginResponse {
        return authAPI.createAccount(request)
    }

    /**
     * Logs out via API for a specific account.
     * @param fcmToken Optional FCM token to unregister
     * @param accountId The account ID to logout
     */
    override suspend fun logoutInAPI(fcmToken: String?, accountId: String) {
        authAPI.logoutWithToken(LogoutRequest(fcmToken ?: ""), accountId)
    }

    /**
     * Logs out in the database by updating the account status.
     */
    override suspend fun logOutInDb(accountId: String) {
        accountDao.logoutAccount(accountId)
    }

    /**
     * Gets account info via API for a specific account and returns AccountResponse.
     * @param accountId The account ID to get info for
     * @return AccountInfo for the specified account
     */
    override suspend fun getAccountInAPI(accountId: String): AccountInfo {
        return authAPI.getAccountWithToken(accountId)
    }

    /**
     * Updates password via API and returns true if successful.
     */
    override suspend fun updatePasswordInAPI(oldPassword: String, newPassword: String): ChangePasswordResponse {
        val request = ChangePasswordRequest(oldPassword, newPassword)
        val response = userAPI.changePassword(request)
        return response
    }

    /**
     * Requests password reset via API and returns true if successful.
     */
    override suspend fun resetPasswordInAPI(email: String): Response<Unit> {
        return authAPI.requestPasswordReset(PasswordResetRequest(email))
    }

    /**
     * Updates profile via API and returns AccountResponse.
     */
    override suspend fun updateProfileInAPI(profileData: ProfileUpdateRequest): AccountResponse {
        return userAPI.updateProfile(profileData)
    }

    // DB Operations
    /**
     * Adds an account to the database and returns the domain model.
     */
    override suspend fun addAccountInDB(account: com.greatergoods.meapp.domain.model.Account): com.greatergoods.meapp.domain.model.Account {
        val accountEntity = AccountEntityMapper.toEntity(account)
        accountDao.insertAccount(accountEntity)
        return account
    }

    /**
     * Updates an account in the database and returns the updated domain model.
     */
    override suspend fun updateAccountInDB(account: Account): Account {
        val accountEntity = AccountEntityMapper.toEntity(account)
        accountDao.updateAccount(accountEntity)
        return account
    }

    override suspend fun logoutInDb(accountId: String) {
        accountDao.logoutAccount(accountId)
    }

    /**
     * Removes an account from the database by ID.
     */
    override suspend fun removeAccountInDB(accountId: String) {
        accountDao.deleteAccountById(accountId)
        // Also clear tokens from TokenManager if this was the active account
        val activeAccountId = userDataStore.getData().accountsMap.entries.firstOrNull { it.value.isActive }?.key
        if (accountId == activeAccountId) {
            tokenManager.clearTokens()
        }
        userDataStore.removeAccount(accountId)
    }

    /**
     * Removes all accounts from the database.
     */
    override suspend fun removeAllAccountsInDB() {
        accountDao.removeAllAccounts()
        tokenManager.clearTokens()
    }

    /**
     * Gets the stored active account from the database.
     */
    override suspend fun getStoredActiveAccountFromDB(): com.greatergoods.meapp.domain.model.Account? {
        return accountDao.getActiveAccount().firstOrNull()?.toDomainAccount()
    }

    /**
     * Deactivates all accounts except the given account ID.
     */
    override suspend fun deactivateOtherAccountsInDB(accountId: String) {
        accountDao.deactivateOtherAccounts(accountId)
    }

    /**
     * Gets all logged-in accounts from the database as a Flow.
     */
    override fun getLoggedInAccountsFromDB(): Flow<List<com.greatergoods.meapp.domain.model.Account>> {
        return accountDao.getAllLoggedInAccounts().map { accounts ->
            accounts.map { it.toDomainAccount() }
        }
    }

    /**
     * Updates tokens for the active account in the TokenManager.
     */
    override suspend fun updateTokensInDB(tokens: Map<String, String>) {
        tokenManager.setTokens(
            Token(
                accountId = tokens["accountId"] ?: "",
                accessToken = tokens["accessToken"],
                refreshToken = tokens["refreshToken"],
                expiresAt = tokens["expiresAt"],
            ),
        )
    }

    /**
     * Refreshes the token via API and returns a Token.
     * @param refreshToken The refresh token to use
     * @param accountId The account ID to associate with the refreshed token
     * @return Token object with refreshed tokens
     */
    override suspend fun refreshTokenInAPI(refreshToken: String, accountId: String?): Token {
        AppLog.d(TAG, "Refreshing token for account: $accountId")
        val response = authAPI.refreshToken(RefreshTokenRequest(refreshToken))
        return Token(
            accountId = accountId ?: "", // Preserve the account ID
            isActive = true,
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAt = response.expiresAt,
        )
    }

    /**
     * Updates the last active time for the account in the database.
     */
    override suspend fun updateLastActiveTimeInDB(accountId: String) {
        TODO() // Implement this if you have a method in AccountDao, otherwise leave as a stub
    }

    private fun com.greatergoods.meapp.data.storage.db.entity.account.Account.toDomainAccount(): com.greatergoods.meapp.domain.model.Account {
        val entity = this.account
        return Account(
            id = entity.id,
            firstName = entity.firstName,
            lastName = entity.lastName,
            dob = entity.dob,
            email = entity.email,
            expiresAt = entity.expiresAt,
            fcmToken = entity.fcmToken,
            gender = entity.gender,
            isActiveAccount = entity.isActiveAccount,
            isLoggedIn = entity.isLoggedIn,
            isExpired = entity.isExpired,
            isSynced = entity.isSynced,
            lastActiveTime = entity.lastActiveTime,
            zipcode = entity.zipcode ?: "",
        )
    }

    override suspend fun updateSyncTimeStamp(timeStamp: String) {
        val accountId = accountDao.getActiveAccount().first()?.account?.id ?: ""
        userDataStore.updateSyncTimestamp(accountId, timeStamp)
    }

    override suspend fun getSyncTimeStamp(): Flow<String> {
        return userDataStore.currentAccountFlow
            .map { it?.syncTimestamp ?: "" } // Return empty string if null
    }

    override suspend fun updateAccountFromAPI(
        accountId: String,
        accountInfo: AccountInfo
    ): com.greatergoods.meapp.domain.model.Account {
        // Get current account from database
        val currentAccount = accountDao.getAccount(accountId).first()
        currentAccount?.account
            ?: throw IllegalStateException("AccountEntity not found for accountId: $accountId")

        // Update account entity with API response data
        val updatedAccountEntity = currentAccount.account.copy(
            firstName = accountInfo.firstName,
            lastName = accountInfo.lastName,
            email = accountInfo.email,
            dob = accountInfo.dob,
            gender = accountInfo.gender,
            zipcode = accountInfo.zipcode,
            isSynced = true,
        )

        // Update in database
        accountDao.updateAccount(updatedAccountEntity)

        AppLog.d(TAG, "Updated account $accountId with API response data")

        return AccountEntityMapper.toDomain(updatedAccountEntity)
    }

    override suspend fun markAccountExpired(accountId: String) {
        accountDao.markAccountExpired(accountId)
        AppLog.d(TAG, "Marked account $accountId as expired")
    }
}

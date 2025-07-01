package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.core.network.ITokenManager
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.data.api.IUserAPI
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.account.AccountEntityMapper
import com.greatergoods.meapp.data.storage.db.entity.account.NotificationSettingsEntity
import com.greatergoods.meapp.data.storage.db.entity.account.StreaksSettingsEntity
import com.greatergoods.meapp.data.storage.db.entity.account.WeightCompSettingsEntity
import com.greatergoods.meapp.data.storage.db.entity.account.WeightlessSettingsEntity
import com.greatergoods.meapp.domain.model.PartialAccount
import com.greatergoods.meapp.domain.model.api.auth.ChangePasswordRequest
import com.greatergoods.meapp.domain.model.api.auth.ChangePasswordResponse
import com.greatergoods.meapp.domain.model.api.auth.LoginRequest
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.auth.LogoutRequest
import com.greatergoods.meapp.domain.model.api.auth.PasswordResetRequest
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenRequest
import com.greatergoods.meapp.domain.model.api.auth.SignupRequest
import com.greatergoods.meapp.domain.model.api.user.AccountInfo
import com.greatergoods.meapp.domain.model.api.user.AccountResponse
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.model.api.user.Token
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of the IAccountRepository interface.
 * Handles account operations using Room database and API calls.
 */
@Singleton
class AccountRepository
    @Inject
    constructor(
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
        override suspend fun login(
            email: String,
            password: String,
        ): LoginResponse = authAPI.login(LoginRequest(email, password))

        /**
         * Signs up via API and returns LoginResponse.
         */
        override suspend fun signup(request: SignupRequest): LoginResponse = authAPI.createAccount(request)

        /**
         * Logs out via API for a specific account.
         * @param fcmToken Optional FCM token to unregister
         * @param accountId The account ID to logout
         */
        override suspend fun logout(
            fcmToken: String?,
            accountId: String,
        ) {
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
        override suspend fun getAccount(accountId: String): AccountInfo = authAPI.getAccountWithToken(accountId)

        /**
         * Updates password via API and returns true if successful.
         */
        override suspend fun updatePassword(
            oldPassword: String,
            newPassword: String,
        ): ChangePasswordResponse {
            val request = ChangePasswordRequest(oldPassword, newPassword)
            val response = userAPI.changePassword(request)
            return response
        }

        /**
         * Requests password reset via API and returns true if successful.
         */
        override suspend fun resetPassword(email: String): Response<Unit> =
            authAPI.requestPasswordReset(PasswordResetRequest(email))

        /**
         * Updates profile via API and updates the local database with the new profile data.
         * @param profileData The profile data to update
         * @return The updated account from the database
         */
        override suspend fun updateProfile(profileData: ProfileUpdateRequest): Account {
            // Call API to update profile
            val response = userAPI.updateProfile(profileData)
            val updatedAccountInfo = response.account

            // Update the account in the DB with the new info
            val updatedAccount = updateAccountInDB(
                updatedAccountInfo.id,
                PartialAccount(
                    firstName = updatedAccountInfo.firstName,
                    lastName = updatedAccountInfo.lastName,
                    dob = updatedAccountInfo.dob,
                    gender = updatedAccountInfo.gender,
                    zipcode = updatedAccountInfo.zipcode,
                    email = updatedAccountInfo.email,
                    isActiveAccount = true,
                    isSynced = true
                )
            )
            return updatedAccount
        }

        // DB Operations

        /**
         * Adds an account to the database with all entity relations and returns the domain model.
         * Inserts the main account entity and all related settings entities.
         */
        override suspend fun addAccountInDB(account: Account): Account {
            val accountEntity = AccountEntityMapper.toEntity(account)
            accountDao.insertAccount(accountEntity)

            // Insert WeightCompSettings entity with data from account
            val weightCompSettings =
                WeightCompSettingsEntity(
                    accountId = account.id,
                    height = account.height ?: 1700, // Default height if not set
                    activityLevel = account.activityLevel ?: "normal", // Default activity level
                    weightUnit = account.weightUnit?.value ?: "lb", // Default weight unit
                    isSynced = true, // New account data is already synced
                )
            accountDao.insertWeightCompSettings(weightCompSettings)

            val notificationCompSettings =
                NotificationSettingsEntity(
                    accountId = account.id,
                    isSynced = true,
                    showWeightInNotifications = account.showWeightInNotifications ?: false,
                    entryNotificationsEnabled = account.entryNotificationsEnabled ?: false,
                )
            accountDao.insertNotificationSettings(notificationCompSettings)

            // Insert StreaksSettings entity with data from account
            val streaksSettings =
                StreaksSettingsEntity(
                    accountId = account.id,
                    isStreakOn = account.isStreakOn ?: false,
                    streakTimestamp = System.currentTimeMillis().toString(),
                    isSynced = true,
                )
            accountDao.insertStreaksSettings(streaksSettings)

            // Insert WeightlessSettings entity with data from account
            val weightlessSettings =
                WeightlessSettingsEntity(
                    accountId = account.id,
                    isWeightlessOn = account.isWeightlessOn ?: false,
                    weightlessTimestamp = System.currentTimeMillis().toString(),
                    weightlessWeight = account.weightlessWeight?.toFloat() ?: 0.0f,
                    isSynced = true,
                )
            accountDao.insertWeightlessSettings(weightlessSettings)

            AppLog.d(TAG, "Added account with all entity relations: ${account.id}")
            return account
        }

        /**
         * Updates an account in the database with partial data and returns the updated domain model.
         * Only the fields provided in partialUpdate will be updated, others will remain unchanged.
         * @param accountId The ID of the account to update
         * @param partialUpdate Partial account data to update
         * @return The updated account
         */
        override suspend fun updateAccountInDB(
            accountId: String,
            partialUpdate: PartialAccount,
        ): Account {
            // Get current account from database
            val currentAccountEntity =
                accountDao.getAccount(accountId).first()
                    ?: throw IllegalStateException("Account not found for ID: $accountId")
            val currentAccount = currentAccountEntity.account
            // Merge current account with partial update (only AccountEntity properties)
            val updatedAccountEntity =
                currentAccount.copy(
                    firstName = partialUpdate.firstName ?: currentAccount.firstName,
                    lastName = partialUpdate.lastName ?: currentAccount.lastName,
                    dob = partialUpdate.dob ?: currentAccount.dob,
                    email = partialUpdate.email ?: currentAccount.email,
                    expiresAt = partialUpdate.expiresAt ?: currentAccount.expiresAt,
                    fcmToken = partialUpdate.fcmToken ?: currentAccount.fcmToken,
                    gender = partialUpdate.gender ?: currentAccount.gender,
                    isActiveAccount = partialUpdate.isActiveAccount ?: currentAccount.isActiveAccount,
                    isLoggedIn = partialUpdate.isLoggedIn ?: currentAccount.isLoggedIn,
                    isExpired = partialUpdate.isExpired ?: currentAccount.isExpired,
                    isSynced = partialUpdate.isSynced ?: currentAccount.isSynced,
                    lastActiveTime = partialUpdate.lastActiveTime ?: currentAccount.lastActiveTime,
                    zipcode = partialUpdate.zipcode ?: currentAccount.zipcode,
                )
            // Update account entity in database
            accountDao.updateAccount(updatedAccountEntity)
            return AccountEntityMapper.toDomain(updatedAccountEntity)
        }

        override suspend fun logoutInDb(accountId: String) {
            accountDao.logoutAccount(accountId)
        }

        override suspend fun logoutAllAccountsInDb() {
            accountDao.logoutAllAccounts()
        }

        /**
         * Removes an account from the database by ID.
         */
        override suspend fun removeAccountInDB(accountId: String) {
            accountDao.deleteAccountById(accountId)
            // Also clear tokens from TokenManager if this was the active account
            val activeAccountId =
                userDataStore
                    .getData()
                    .accountsMap.entries
                    .firstOrNull { it.value.isActive }
                    ?.key
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
         * Gets the stored active account from the database as a Flow.
         */
        override fun getStoredActiveAccountFromDB(): Flow<Account?> =
            accountDao.getActiveAccount().map {
                it?.toDomainAccount()
            }

        /**
         * Deactivates all accounts except the given account ID.
         */
        override suspend fun deactivateOtherAccountsInDB(accountId: String) {
            accountDao.deactivateOtherAccounts(accountId)
        }

        override suspend fun deactivateAllAccountsInDB() {
            accountDao.deactivateAllAccounts()
        }

        /**
         * Activates the specified account by setting it as the active account.
         */
        override suspend fun activateAccountInDB(accountId: String) {
            accountDao.activateAccount(accountId)
        }

        /**
         * Gets all logged-in accounts from the database as a Flow.
         */
        override fun getLoggedInAccountsFromDB(): Flow<List<Account>> =
            accountDao.getAllLoggedInAccounts().map { accounts ->
                accounts.map { it.toDomainAccount() }
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
        override suspend fun refreshToken(
            refreshToken: String,
            accountId: String?,
        ): Token {
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
            val timestamp = System.currentTimeMillis().toString()
            accountDao.updateLastActiveTime(accountId, timestamp)
            AppLog.d(TAG, "Updated last active time for account: $accountId")
        }

        private fun com.greatergoods.meapp.data.storage.db.entity.account.Account.toDomainAccount(): Account =
            AccountEntityMapper.toDomainFromAccountWithRelations(this)

        override suspend fun updateSyncTimeStamp(timeStamp: String) {
            val accountId =
                accountDao
                    .getActiveAccount()
                    .first()
                    ?.account
                    ?.id ?: ""
            userDataStore.updateSyncTimestamp(accountId, timeStamp)
        }

        override suspend fun getSyncTimeStamp(): Flow<String> {
            return userDataStore.currentAccountFlow
                .map { it?.syncTimestamp ?: "" } // Return empty string if null
        }

        override suspend fun updateAccountFromAPI(
            accountId: String,
            accountInfo: AccountInfo,
        ): Account {
            // Get current account from database
            val currentAccount =
                accountDao.getAccount(accountId).first()
                    ?: throw IllegalStateException("Account not found for accountId: $accountId")

            // Update account entity with API response data
            val updatedAccountEntity =
                currentAccount.account.copy(
                    firstName = accountInfo.firstName,
                    lastName = accountInfo.lastName,
                    email = accountInfo.email,
                    dob = accountInfo.dob,
                    gender = accountInfo.gender,
                    zipcode = accountInfo.zipcode,
                )

            // Update account entity in database
            accountDao.updateAccount(updatedAccountEntity)

            AppLog.d(TAG, "Updated account $accountId with API response data")
            return AccountEntityMapper.toDomain(updatedAccountEntity)
        }

        override suspend fun markAccountExpired(accountId: String) {
            accountDao.markAccountExpired(accountId)
            AppLog.d(TAG, "Marked account $accountId as expired")
        }

        /**
         * Gets all accounts with unsynced data (isSynced = false) from the database.
         * Used by offline handler service to sync pending changes.
         * @return List of accounts that need to be synced
         */
        override suspend fun getUnsyncedAccountsFromDB(): List<Account> =
            accountDao.getUnsyncedAccounts().first().map { accountEntity ->
                AccountEntityMapper.toDomain(accountEntity)
            }
    }

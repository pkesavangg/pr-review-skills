package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.core.network.ITokenManager
import com.greatergoods.meapp.core.network.utility.HttpErrorResponse
import com.greatergoods.meapp.core.shared.utilities.ConversionTools
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.data.api.IUserAPI
import com.greatergoods.meapp.data.storage.datastore.DashboardKeysDatastore
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.account.AccountEntityMapper
import com.greatergoods.meapp.data.storage.db.entity.account.GoalSettingsEntity
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
import com.greatergoods.meapp.domain.model.api.user.AccountToken
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.model.api.user.Token
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.features.goal.helper.Weightless
import com.greatergoods.meapp.proto.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
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
  private val dashboardKeysDatastore: DashboardKeysDatastore,
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
  ): Account {
    val loginResponse = authAPI.login(LoginRequest(email, password))
    return addAccountFromLoginResponse(loginResponse)
  }

  /**
   * Signs up via API and returns LoginResponse.
   */
  override suspend fun signup(request: SignupRequest): Account {
    val loginResponse = authAPI.createAccount(request)
    return addAccountFromLoginResponse(loginResponse)
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
    accountId: String,
    oldPassword: String,
    newPassword: String,
  ): ChangePasswordResponse {
    val request = ChangePasswordRequest(oldPassword, newPassword)
    val response = userAPI.changePassword(request)
    setTokensForAccount(
      Token(
        accountId = accountId,
        accessToken = response.accessToken,
        refreshToken = response.refreshToken,
        expiresAt = response.expiresAt,
      ),
    )
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
    try {
      // Call API to update profile
      val response = userAPI.updateProfile(profileData)
      val updatedAccountInfo = response.account

      // Update the account in the DB with the new info
      val updatedAccount =
        updateAccount(
          updatedAccountInfo.id,
          PartialAccount(
            firstName = updatedAccountInfo.firstName,
            lastName = updatedAccountInfo.lastName,
            dob = updatedAccountInfo.dob,
            gender = updatedAccountInfo.gender,
            zipcode = updatedAccountInfo.zipcode,
            email = updatedAccountInfo.email,
            isActiveAccount = true,
            isSynced = true,
          ),
        )
      return updatedAccount
    } catch (e: HttpException) {
      if (HttpErrorResponse.isNetworkError(e.code())) {
        updateAccount(
          profileData.id,
          PartialAccount(
            firstName = profileData.firstName,
            lastName = profileData.lastName,
            dob = profileData.dob,
            gender = profileData.gender,
            zipcode = profileData.zipcode,
            email = profileData.email,
            isActiveAccount = true,
            isSynced = false,
          ),
        )
      }
      throw e
    }
  }

  // DB Operations

  /**
   * Adds an account to the database with all entity relations and returns the domain model.
   * Inserts the main account entity and all related settings entities.
   */
  override suspend fun addAccount(account: Account): Account {
    val accountEntity = AccountEntityMapper.toEntity(account)
    accountDao.insertAccount(accountEntity)

    // Insert WeightCompSettings entity with data from account
    val weightCompSettings =
      WeightCompSettingsEntity(
        accountId = account.id,
        height = account.height ?: 1700, // Default height if not set
        activityLevel = account.activityLevel ?: "normal", // Default activity level
        weightUnit = account.weightUnit.value, // Default weight unit
        isSynced = true, // New account data is already synced
      )
    accountDao.insertWeightCompSettings(weightCompSettings)

    val notificationCompSettings =
      NotificationSettingsEntity(
        accountId = account.id,
        isSynced = true,
        shouldSendEntryNotifications = account.shouldSendEntryNotifications ?: false,
        shouldSendWeightInEntryNotifications = account.shouldSendWeightInEntryNotifications ?: false,
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
    val goalEntity =
      GoalSettingsEntity(
        accountId = account.id,
        goalType = account.goalType ?: "maintain",
        weight = account.initialWeight.toFloat(),
        goalWeight = account.goalWeight.toString(),
        goalPercent = account.goalPercent.toFloat(), // Will be calculated when needed
        isSynced = true,
      )
    accountDao.insertGoalSettings(goalEntity)
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
  override suspend fun updateAccount(
    accountId: String,
    partialUpdate: PartialAccount,
  ): Account {
    // Get current account entity from database by ID
    val accountEntity =
      accountDao.getAccountEntity(accountId)
        ?: throw IllegalStateException("Account not found for ID: $accountId")

    // Merge current account with partial update
    val updatedAccountEntity =
      accountEntity.copy(
        firstName = partialUpdate.firstName ?: accountEntity.firstName,
        lastName = partialUpdate.lastName ?: accountEntity.lastName,
        dob = partialUpdate.dob ?: accountEntity.dob,
        email = partialUpdate.email ?: accountEntity.email,
        expiresAt = partialUpdate.expiresAt ?: accountEntity.expiresAt,
        fcmToken = partialUpdate.fcmToken ?: accountEntity.fcmToken,
        gender = partialUpdate.gender ?: accountEntity.gender,
        isActiveAccount = partialUpdate.isActiveAccount ?: accountEntity.isActiveAccount,
        isLoggedIn = partialUpdate.isLoggedIn ?: accountEntity.isLoggedIn,
        isExpired = partialUpdate.isExpired ?: accountEntity.isExpired,
        isSynced = partialUpdate.isSynced ?: accountEntity.isSynced,
        lastActiveTime = partialUpdate.lastActiveTime ?: accountEntity.lastActiveTime,
        zipcode = partialUpdate.zipcode ?: accountEntity.zipcode,
      )

    // Update account entity in database using simple @Update
    accountDao.updateAccount(updatedAccountEntity)
    return AccountEntityMapper.toDomain(updatedAccountEntity)
  }

  /**
   * Gets the stored active account from the database as a Flow.
   */
  override fun getActiveAccount(): Flow<Account?> =
    accountDao.getActiveAccount().map {
      it?.toDomainAccount()
    }

  /**
   * Deactivates all accounts except the given account ID.
   */
  override suspend fun deactivateOtherAccounts(accountId: String) {
    accountDao.deactivateOtherAccounts(accountId)
  }

    /**
     * Updates profile via API and updates the local database with the new profile data.
     * @param profileData The profile data to update
     * @return The updated account from the database
     */
    override suspend fun updateProfile(profileData: ProfileUpdateRequest) {
        try {
            // Call API to update profile
            val response = userAPI.updateProfile(profileData)
            val updatedAccountInfo = response.account
                updateAccount(
                    updatedAccountInfo.id,
                    PartialAccount(
                        firstName = updatedAccountInfo.firstName,
                        lastName = updatedAccountInfo.lastName,
                        dob = updatedAccountInfo.dob,
                        gender = updatedAccountInfo.gender,
                        zipcode = updatedAccountInfo.zipcode,
                        email = updatedAccountInfo.email,
                        isActiveAccount = true,
                        isSynced = true,
                    ),
                )
        } catch (e: HttpException) {
            if (HttpErrorResponse.isNetworkError(e.code())) {
                updateAccount(
                    profileData.id,
                    PartialAccount(
                        firstName = profileData.firstName,
                        lastName = profileData.lastName,
                        dob = profileData.dob,
                        gender = profileData.gender,
                        zipcode = profileData.zipcode,
                        email = profileData.email,
                        isActiveAccount = true,
                        isSynced = false,
                    ),
                )
            }
            throw e
        }
    }

    // DB Operations

    /**
     * Adds an account to the database with all entity relations and returns the domain model.
     * Inserts the main account entity and all related settings entities.
     */
    override suspend fun addAccount(account: Account): Account {
        val accountEntity = AccountEntityMapper.toEntity(account)
        accountDao.insertAccount(accountEntity)

        // Insert WeightCompSettings entity with data from account
        val weightCompSettings =
            WeightCompSettingsEntity(
                accountId = account.id,
                height = account.height ?: 1700, // Default height if not set
                activityLevel = account.activityLevel ?: "normal", // Default activity level
                weightUnit = account.weightUnit.value, // Default weight unit
                isSynced = true, // New account data is already synced
            )
        accountDao.insertWeightCompSettings(weightCompSettings)

        val notificationCompSettings =
            NotificationSettingsEntity(
                accountId = account.id,
                isSynced = true,
                shouldSendEntryNotifications = account.shouldSendEntryNotifications ?: false,
                shouldSendWeightInEntryNotifications = account.shouldSendWeightInEntryNotifications ?: false,
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
        val goalEntity =
            GoalSettingsEntity(
                accountId = account.id,
                goalType = account.goalType ?: "maintain",
                weight = account.initialWeight.toFloat(),
                goalWeight = account.goalWeight.toString(),
                goalPercent = account.goalPercent.toFloat(), // Will be calculated when needed
                isSynced = true,
            )
        accountDao.insertGoalSettings(goalEntity)
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
    override suspend fun updateAccount(
        accountId: String,
        partialUpdate: PartialAccount,
    ) {
        // Get current account entity from database by ID
        val accountEntity =
            accountDao.getAccountEntity(accountId)
                ?: throw IllegalStateException("Account not found for ID: $accountId")

        // Merge current account with partial update
        val updatedAccountEntity =
            accountEntity.copy(
                firstName = partialUpdate.firstName ?: accountEntity.firstName,
                lastName = partialUpdate.lastName ?: accountEntity.lastName,
                dob = partialUpdate.dob ?: accountEntity.dob,
                email = partialUpdate.email ?: accountEntity.email,
                expiresAt = partialUpdate.expiresAt ?: accountEntity.expiresAt,
                fcmToken = partialUpdate.fcmToken ?: accountEntity.fcmToken,
                gender = partialUpdate.gender ?: accountEntity.gender,
                isActiveAccount = partialUpdate.isActiveAccount ?: accountEntity.isActiveAccount,
                isLoggedIn = partialUpdate.isLoggedIn ?: accountEntity.isLoggedIn,
                isExpired = partialUpdate.isExpired ?: accountEntity.isExpired,
                isSynced = partialUpdate.isSynced ?: accountEntity.isSynced,
                lastActiveTime = partialUpdate.lastActiveTime ?: accountEntity.lastActiveTime,
                zipcode = partialUpdate.zipcode ?: accountEntity.zipcode,
            )

        // Update account entity in database
        accountDao.updateAccount(updatedAccountEntity)
    }

    /**
     * Gets the stored active account from the database as a Flow.
     */
    override fun getActiveAccount(): Flow<Account?> =
        accountDao.getActiveAccount().map {
            it?.toDomainAccount()
        }

    /**
     * Deactivates all accounts except the given account ID.
     */
    override suspend fun deactivateOtherAccounts(accountId: String) {
        accountDao.deactivateOtherAccounts(accountId)
    }

    /**
     * Activates the specified account by setting it as the active account.
     */
    override suspend fun activateAccount(accountId: String) {
        accountDao.activateAccount(accountId)
    }

    /**
     * Gets all logged-in accounts from the database as a Flow.
     */
    override fun getLoggedInAccounts(): Flow<List<Account>> =
        accountDao.getAllLoggedInAccounts().map { accounts ->
            accounts.map { it.toDomainAccount() }
        }

    /**
     * Updates tokens for the active account in the TokenManager.
     */
    override suspend fun updateTokens(request: AccountToken) {
        tokenManager.setTokens(
            Token(
                accountId = request.accountId,
                accessToken = request.accessToken,
                refreshToken = request.refreshToken,
                expiresAt = request.expiresAt,
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
    override suspend fun updateLastActiveTime(accountId: String) {
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

    override suspend fun updateAccountInfo(
        accountId: String,
        accountInfo: AccountInfo,
    ) {
        // Use updateAccount with only the profile fields from API response
        val partialUpdate =
            PartialAccount(
                firstName = accountInfo.firstName,
                lastName = accountInfo.lastName,
                email = accountInfo.email,
                dob = accountInfo.dob,
                gender = accountInfo.gender,
                zipcode = accountInfo.zipcode,
            )
      updateAccount(accountId, partialUpdate)
      AppLog.d(TAG, "Updated account $accountId with API response data")
    }

    override suspend fun markAccountExpired(accountId: String) {
        accountDao.markAccountExpired(accountId)
        AppLog.d(TAG, "Marked account $accountId as expired")
    }


    /**
     * Gets the active account if it is not synced.
     * @return The active account if it exists and is not synced, otherwise null
     */
    override suspend fun getUnsyncedActiveAccount(): Account? =
        accountDao.getUnsyncedActiveAccount().first()?.let { accountWithRelations ->
            AccountEntityMapper.toDomainFromAccountWithRelations(accountWithRelations)
        }

    /**
     * Logs out the account both remotely (API) and locally (DB, tokens).
     * @param accountId The ID of the account to log out
     * @param fcmToken The FCM token for push notifications (optional)
     * @param isActiveAccount Whether this is the active account
     * @return true if logout was successful, false otherwise
     */
    override suspend fun logoutAccount(
        accountId: String,
        fcmToken: String?,
        isActiveAccount: Boolean,
    ): Boolean =
        try {
            // Try to logout on API if network is available
            var apiLogoutAttempted = false
            try {
                // Assume network is available if API call does not throw
                authAPI.logoutWithToken(LogoutRequest(fcmToken ?: ""), accountId)
                apiLogoutAttempted = true
            } catch (e: Exception) {
                AppLog.e(TAG, "API logout failed", e.toString())
                // Continue with local logout even if API fails
            }
            // Always perform local logout regardless of network status
            if (isActiveAccount) {
                accountDao.deactivateAllAccounts()
            }
            // Update account flags in DB: set isLoggedIn, isExpired, isActive to false
            accountDao.logoutAccount(accountId)
            // Clear tokens from DataStore and TokenManager
            userDataStore.clearAccountTokens(accountId)
            tokenManager.clearTokens()
            AppLog.d(TAG, "Logout successful (API attempted: $apiLogoutAttempted)")
            true
        } catch (e: Exception) {
          AppLog.e(TAG, "API logout failed for account ${account.id}", e.toString())
          // Continue with local logout even if API fails
        }
      }
      // Clear all accounts from database
      accountDao.logoutAllAccounts()
      // Clear tokens
      tokenManager.clearTokens()
      AppLog.d(TAG, "All accounts logged out successfully")
      true
    } catch (e: Exception) {
      AppLog.e(TAG, "Logout all failed", e.toString())
      false
    }

  /**
   * Adds a new account from a LoginResponse, sets it as active, and updates tokens.
   * @param loginResponse The login response containing account and token info
   * @return The saved Account
   */
  override suspend fun addAccountFromLoginResponse(loginResponse: LoginResponse): Account {
    val account = addAccountFromResponse(loginResponse)
    setActiveAccountAndTokens(
      account.id,
      Token(
        accountId = account.id,
        isActive = true,
        accessToken = loginResponse.accessToken,
        refreshToken = loginResponse.refreshToken,
        expiresAt = loginResponse.expiresAt,
      ),
    )
    // For new accounts, initialize both metric and milestone keys with defaults
    // Use resetVisibleKeys to ensure both are properly initialized
    dashboardKeysDatastore.resetVisibleKeys(account.id)
    return account
  }

  /**
   * Switches to a different account by setting it as active and updating tokens.
   * @param accountId The account ID to switch to
   */
  override suspend fun switchToAccount(accountId: String) {
    setActiveAccountAndTokens(accountId, null)
  }

  /**
   * Clears the tokens for the given account ID.
   */
  override suspend fun clearAccountTokens(accountId: String) {
    userDataStore.clearAccountTokens(accountId)
  }

  /**
   * Removes the account with the given ID from the database.
   */
  override suspend fun removeAccount(accountId: String) {
    userDataStore.clearAccountTokens(accountId)
  }

  /**
   * Deletes the current user account via API and clears local data.
   */
  override suspend fun deleteAccount(accountID: String, isActiveAccount: Boolean) {
    // Call API to delete account
    if (isActiveAccount) {
      userAPI.deleteAccount()
      accountDao.logoutAccount(accountID)
      accountDao.deactivateAllAccounts()
    }
    // Clear all tokens and local data
    userDataStore.clearAccountTokens(accountID)
    tokenManager.clearTokens()
    AppLog.d(TAG, "Account deleted and local data cleared")
  }

  /**
   * Private helper to add an account from LoginResponse.account.
   */
  private suspend fun addAccountFromResponse(loginResponse: LoginResponse): Account {
    val account = loginResponse.account
    val userAccount =
      Account(
        id = account.id,
        firstName = account.firstName,
        lastName = account.lastName,
        dob = account.dob,
        email = account.email,
        expiresAt = loginResponse.expiresAt,
        fcmToken = null,
        gender = account.gender,
        isActiveAccount = true,
        isLoggedIn = true,
        isExpired = false,
        isSynced = false,
        lastActiveTime = System.currentTimeMillis().toString(),
        zipcode = account.zipcode,
        weightUnit = WeightUnit.from(account.weightUnit),
        initialWeight = account.initialWeight?.toDouble() ?: 0.0,
        isWeightlessOn = account.isWeightlessOn,
        height = account.height,
        goalWeight = account.goalWeight?.toDouble() ?: 0.0,
        goalType = account.goalType,
        metPreviousGoal = account.metPreviousGoal,
        goalPercent = account.goalPercent.toDouble(),
        activityLevel = account.activityLevel,
        weightlessTimestamp = account.weightlessTimestamp,
        weightlessWeight = account.weightlessWeight,
        isStreakOn = account.isStreakOn,
        dashboardType = account.dashboardType,
        dashboardMetrics = account.dashboardMetrics,
      )
    return addAccount(userAccount)
  }

  /**
   * Helper to activate an account, set it as active in DB, update tokens, and set as active in UserDataStore and TokenManager.
   * @param accountId The account ID to activate
   * @param tokens The tokens to set as active (if not null)
   */
  private suspend fun setActiveAccountAndTokens(
    accountId: String,
    tokens: Token?,
  ) {
    activateAccount(accountId)
    deactivateOtherAccounts(accountId)
    userDataStore.setActiveAccount(accountId)
    updateLastActiveTime(accountId)
    setTokensForAccount(tokens)
  }

  /**
   * Helper to set tokens for a non-active account (used for background API calls).
  // * @param accountId The account ID
   * @param tokens The tokens to set (if not null)
   */
  private suspend fun setTokensForAccount(tokens: Token?) {
    tokens?.let { tokenManager.setTokens(it) }
  }

  // New: Flow for active account's weight unit
  override fun getActiveAccountWeightUnitFlow(): Flow<WeightUnit?> =
    getActiveAccount().map { it?.weightUnit }.distinctUntilChanged()

  private fun Account?.toWeightless(): Weightless {
    val rawWeightless = this?.weightlessWeight ?: 0f
    val unit = this?.weightUnit
    val weightlessInLb = ConversionTools.convertStoredToDisplay(rawWeightless.toDouble(), unit == WeightUnit.KG)
    return Weightless(
      isWeightlessOn = this?.isWeightlessOn ?: false,
      weightlessWeight = weightlessInLb.toFloat(),
    )
  }

  // New: Flow for active account's Weightless settings
  override fun getActiveAccountWeightlessFlow(): Flow<Weightless> =
    getActiveAccount().map { it.toWeightless() }.distinctUntilChanged()
  // Theme Mode Operations

  /**
   * Gets the current theme mode for the active account as a flow.
   * @return Flow of ThemeMode that emits changes
   */
  override val currentThemeModeFlow = userDataStore.currentThemeModeFlow

  /**
   * Sets the theme mode for the active account.
   * @param themeMode The ThemeMode to set
   */
  override suspend fun setCurrentThemeMode(themeMode: ThemeMode) {
    val activeAccount = getActiveAccount().first()
    if (activeAccount != null) {
      userDataStore.setThemeMode(activeAccount.id, themeMode)
      AppLog.d(TAG, "Set theme mode to $themeMode for account: ${activeAccount.id}")
    } else {
      AppLog.w(TAG, "No active account found, cannot set theme mode")
    }

    /**
     * Syncs all account settings with server data.
     * Updates local database with the latest settings from server.
     * @param accountInfo The account info from server containing latest settings
     */
    override suspend fun syncAccountSettingsWithServer(accountInfo: AccountInfo) {
        AppLog.d(TAG, "Syncing all settings for account: ${accountInfo.id}")

        try {
            // Update basic account info first
            updateAccountInfo(accountInfo.id, accountInfo)

            // Update weightless settings
            val weightlessSetting = WeightlessSettingsEntity(
                accountId = accountInfo.id,
                isWeightlessOn = accountInfo.isWeightlessOn,
                weightlessTimestamp = accountInfo.weightlessTimestamp ?: "0",
                weightlessWeight = accountInfo.weightlessWeight ?: 0.0f,
                isSynced = true,
            )
            accountDao.updateWeightlessSettings(weightlessSetting)

            // Update weight composition settings
            val weightCompSettings = WeightCompSettingsEntity(
                accountId = accountInfo.id,
                height = accountInfo.height,
                activityLevel = accountInfo.activityLevel,
                weightUnit = accountInfo.weightUnit,
                isSynced = true,
            )
            accountDao.updateWeightCompSettings(weightCompSettings)

            // Update goal settings
            val goalSettings = GoalSettingsEntity(
              accountId = accountInfo.id,
              goalType = accountInfo.goalType ?: "maintain",
              weight = accountInfo.initialWeight ?: 0.0f,
              goalWeight = accountInfo.goalWeight?.toString() ?: "0",
              goalPercent = accountInfo.goalPercent.toFloat(),
              isSynced = true,
            )
            accountDao.updateGoalSettings(goalSettings)

            // Update notification settings
            val notificationSettings = NotificationSettingsEntity(
              accountId = accountInfo.id,
              shouldSendEntryNotifications = accountInfo.shouldSendEntryNotifications,
              shouldSendWeightInEntryNotifications = accountInfo.shouldSendWeightInEntryNotifications,
              isSynced = true,
            )
            accountDao.updateNotificationSettings(notificationSettings)
            AppLog.d(TAG, "Successfully synced all settings for account: ${accountInfo.id}")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to sync settings for account: ${accountInfo.id}", e.toString())
            throw e
        }
    }
}

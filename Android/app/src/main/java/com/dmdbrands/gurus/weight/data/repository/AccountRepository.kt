package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.network.ISecureTokenStore
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.network.utility.HttpErrorResponse
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IAuthAPI
import com.dmdbrands.gurus.weight.data.api.IUserAPI
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntityMapper
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.DashboardSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.GoalSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.ProductSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.StreaksSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightlessSettingsEntity
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKeyConstants
import com.dmdbrands.gurus.weight.domain.enums.MilestoneKey
import com.dmdbrands.gurus.weight.domain.enums.ProgressKeyConstants
import com.dmdbrands.gurus.weight.domain.model.PartialAccount
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.api.auth.ChangePasswordRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.ChangePasswordResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.EmailCheckRequest
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.dmdbrands.gurus.weight.domain.model.api.auth.LoginRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.LoginResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.LogoutRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.PasswordResetRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.RefreshTokenRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.api.dashboard.DashboardMetricsRequest
import com.dmdbrands.gurus.weight.domain.model.api.dashboard.DashboardTypeRequest
import com.dmdbrands.gurus.weight.domain.model.api.dashboard.ProgressMetricsRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.api.user.MeasurementUnitsRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountToken
import com.dmdbrands.gurus.weight.features.common.enums.toGraphSegment
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toWeightless
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.dmdbrands.gurus.weight.proto.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
  private val babyProfileDao: BabyProfileDao,
  private val userDataStore: UserDataStore,
  private val tokenManager: ITokenManager,
  private val secureTokenStore: ISecureTokenStore,
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
    AppLog.d(TAG, "login API call")
    return try {
      val loginResponse = authAPI.login(LoginRequest(email, password))
      val account = addAccountFromLoginResponse(loginResponse)
      AppLog.i(TAG, "login API call succeeded, account: ${account.id}")
      account
    } catch (e: Exception) {
      AppLog.e(TAG, "login API call failed", e)
      throw e
    }
  }

  /**
   * Signs up via API and returns LoginResponse.
   */
  override suspend fun signup(request: SignupRequest): Account {
    AppLog.d(TAG, "signup API call")
    return try {
      val loginResponse = authAPI.createAccount(request)
      val account = addAccountFromLoginResponse(loginResponse)
       AppLog.i(TAG, "signup API call succeeded, account: ${account.id}")
      account
    } catch (e: Exception) {
      AppLog.e(TAG, "signup API call failed", e)
      throw e
    }
  }

  /**
   * Gets account info via API for a specific account and returns AccountResponse.
   * @param accountId The account ID to get info for
   * @return AccountInfo for the specified account
   */
  override suspend fun getAccountFromAPI(accountId: String): AccountInfo {
    AppLog.d(TAG, "getAccountFromAPI for account: $accountId")
    return try {
      val result = authAPI.getAccountWithToken(accountId)
      AppLog.i(TAG, "getAccountFromAPI succeeded for account: $accountId")
      result
    } catch (e: Exception) {
      AppLog.e(TAG, "getAccountFromAPI failed for account: $accountId", e)
      throw e
    }
  }

  /**
   * Updates password via API and returns true if successful.
   */
  override suspend fun updatePassword(
    accountId: String,
    oldPassword: String,
    newPassword: String,
  ): ChangePasswordResponse {
    AppLog.d(TAG, "updatePassword API call for account: $accountId")
    return try {
      val request = ChangePasswordRequest(oldPassword, newPassword)
      val response = userAPI.changePassword(request)
      setTokensForAccount(
        Token(
          accountId = accountId,
          isActive = true,
          accessToken = response.accessToken,
          refreshToken = response.refreshToken,
          expiresAt = response.expiresAt,
        ),
      )
      AppLog.i(TAG, "updatePassword API call succeeded for account: $accountId")
      response
    } catch (e: Exception) {
      AppLog.e(TAG, "updatePassword API call failed for account: $accountId", e)
      throw e
    }
  }

  override suspend fun updateDashboardMetrics(dashboardKeys: List<String>) {
    AppLog.d("AccountRepository", "Updating dashboard metrics on server: $dashboardKeys")
    userAPI.updateDashboardMetrics(
      request = DashboardMetricsRequest(
        dashboardMetrics = dashboardKeys,
      ),
    )
    AppLog.d("AccountRepository", "Dashboard metrics updated successfully on server")
  }

  override suspend fun updateProgressMetrics(progressKeys: List<String>) {
    try {
      AppLog.d("AccountRepository", "Updating progress metrics on server: $progressKeys")
      userAPI.updateProgressMetrics(
        request = ProgressMetricsRequest(
          progressMetrics = progressKeys,
        ),
      )
    }
    catch (e: Exception){
      AppLog.e("AccountRepository", "Failed while updating the progress metrics")
    }
    AppLog.d("AccountRepository", "Progress metrics updated successfully on server")
  }

  override suspend fun updateDashboardType(dashboardType: String) {
    AppLog.d("AccountRepository", "Updating dashboard type on server: $dashboardType")
    userAPI.updateDashboardType(
      request = DashboardTypeRequest(
        dashboardType = dashboardType,
      ),
    )
    AppLog.d("AccountRepository", "Dashboard type updated successfully on server")
  }

  /**
   * Requests password reset via API and returns true if successful.
   */
  override suspend fun resetPassword(email: String): Response<Unit> {
    AppLog.d(TAG, "resetPassword API call")
    return try {
      val response = authAPI.requestPasswordReset(PasswordResetRequest(email))
      AppLog.i(TAG, "resetPassword API call succeeded")
      response
    } catch (e: Exception) {
      AppLog.e(TAG, "resetPassword API call failed", e)
      throw e
    }
  }

  /**
   * Updates profile via API and updates the local database with the new profile data.
   * @param profileData The profile data to update
   * @return The updated account from the database
   */
  override suspend fun emailCheck(email: String): Boolean {
    val response = authAPI.emailCheck(EmailCheckRequest(email))
    AppLog.d(TAG, "emailCheck -> isAvailable=${response.isAvailable}")
    return response.isAvailable
  }

  override suspend fun updateMeasurementUnits(measurementUnits: MeasurementUnits) {
    val response = userAPI.updateMeasurementUnits(MeasurementUnitsRequest(measurementUnits.value))
    // Persist the server-confirmed account state (incl. productTypes/measurementUnits) locally.
    syncAccountSettingsWithServer(response.account, isOnline = true)
  }

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
    val existingAccount = accountDao.getAccountEntity(account.id)
    if (existingAccount != null) {
      // Account exists - update it instead of replacing to avoid CASCADE DELETE
      val updatedAccountEntity = accountEntity.copy(
        // Preserve any local-only fields if needed
      )
      accountDao.updateAccount(updatedAccountEntity)
    } else {
      // New account - safe to insert
      accountDao.insertAccount(accountEntity)
    }

    // Insert WeightCompSettings entity with data from account
    val weightCompSettings =
      WeightCompSettingsEntity(
        accountId = account.id,
        height = account.height ?: 1700, // Default height if not set
        activityLevel = account.activityLevel ?: "normal", // Default activity level
        weightUnit = account.weightUnit.value, // Default weight unit
        isSynced = true, // New account data is already synced
      )
    if(existingAccount != null){
      accountDao.updateWeightCompSettings(weightCompSettings)
    }else
    accountDao.insertWeightCompSettings(weightCompSettings)

    val notificationCompSettings =
      NotificationSettingsEntity(
        accountId = account.id,
        isSynced = true,
        shouldSendEntryNotifications = account.shouldSendEntryNotifications ?: false,
        shouldSendWeightInEntryNotifications = account.shouldSendWeightInEntryNotifications ?: false,
      )
    if(existingAccount != null){
      accountDao.updateNotificationSettings(notificationCompSettings)
    }else
    accountDao.insertNotificationSettings(notificationCompSettings)

    // Insert StreaksSettings entity with data from account
    val streaksSettings =
      StreaksSettingsEntity(
        accountId = account.id,
        isStreakOn = account.isStreakOn ?: false,
        streakTimestamp = System.currentTimeMillis().toString(),
        isSynced = true,
      )
    if(existingAccount != null){
      accountDao.updateStreaksSettings(streaksSettings)
    }else
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
    if(existingAccount != null){
      accountDao.updateWeightlessSettings(weightlessSettings)
    }else
    accountDao.insertWeightlessSettings(weightlessSettings)
    val goalEntity =
      GoalSettingsEntity(
        accountId = account.id,
        goalType = account.goalType,
        weight = account.initialWeight.toFloat(),
        goalWeight = account.goalWeight.toString(),
        goalPercent = account.goalPercent.toFloat(), // Will be calculated when needed
        isSynced = true,
      )
    if(existingAccount != null){
      accountDao.updateGoalSettings(goalEntity)
    }else
    accountDao.insertGoalSettings(goalEntity)

    val integrationEntity = IntegrationsSettingsEntity(
      accountId = account.id,
      isSynced = true,
      isFitbitOn = account.isFitbitOn,
      isFitbitValid = account.isFitbitValid,
      isHealthConnectOn = account.isHealthConnectOn,
      isHealthKitOn = account.isHealthKitOn,
      isMFPOn = account.isMFPOn,
      isMFPValid = account.isMFPValid,
    )
    if(existingAccount != null){
      accountDao.updateIntegrationsSettings(integrationEntity)
    }else
    accountDao.insertIntegrationsSettings(integrationEntity)
    val dashboardSettings =
      DashboardSettingsEntity(
        accountId = account.id,
        dashboardMetrics = account.dashboardMetrics ?: MetricKeyConstants.DEFAULT_4_METRICS,
        dashboardMilestones = account.progressMetrics ?: MilestoneKey.getDefaultMilestones().map { ProgressKeyConstants.ENUM_TO_CAMEL_CASE[it] ?: it.name.lowercase() },
        dashboardType = account.dashboardType ?: DashboardType.DASHBOARD_4_METRICS.name,
        isSynced = true,
      )
    if(existingAccount != null){
      accountDao.updateDashboardSettings(dashboardSettings)
    }else
    accountDao.insertDashboardSettings(dashboardSettings)

    // Product settings (Phase 2 / MOB-377)
    val productSettings = ProductSettingsEntity(
      accountId = account.id,
      productTypes = account.productTypes,
      measurementUnits = account.measurementUnits.value,
      isSynced = true,
    )
    if (existingAccount != null) {
      accountDao.updateProductSettings(productSettings)
    } else {
      accountDao.insertProductSettings(productSettings)
    }
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
   * Updates only the dashboard type while preserving existing metrics and milestones.
   * @param accountId The account ID
   * @param dashboardType The new dashboard type to set
   */
  override suspend fun updateLocalDashboardType(accountId: String, dashboardType: DashboardType) {
    // Get existing settings to preserve metrics and milestones
    val existingSettings = accountDao.getDashboardSettings(accountId).first()

    val settings = DashboardSettingsEntity(
      accountId = accountId,
      dashboardMetrics = existingSettings?.dashboardMetrics ?: emptyList(),
      dashboardMilestones = existingSettings?.dashboardMilestones ?: emptyList(),
      dashboardType = dashboardType.value,
      isSynced = true,
    )
    accountDao.insertDashboardSettings(settings)
  }

  /**
   * Updates dashboard settings including metrics and milestones for the given account.
   * @param accountId The account ID
   * @param dashboardMetrics List of metric keys
   * @param dashboardMilestones List of milestone keys
   * @param dashboardType The dashboard type
   */
  override suspend fun updateDashboardSettings(
    accountId: String,
    dashboardMetrics: List<String>,
    dashboardMilestones: List<String>,
    dashboardType: DashboardType,
    isSynced: Boolean
  ) {
    val settings = DashboardSettingsEntity(
      accountId = accountId,
      dashboardMetrics = dashboardMetrics,
      dashboardMilestones = dashboardMilestones,
      dashboardType = dashboardType.value,
      isSynced = isSynced,
    )
    accountDao.insertDashboardSettings(settings)
  }

  /**
   * Gets the stored active account from the database as a Flow.
   */
  override fun getActiveAccount(): Flow<Account?> =
    combine(
      accountDao.getActiveAccount().distinctUntilChanged(),
      userDataStore.defaultGraphSegmentFlow.distinctUntilChanged(),
    ) { entity, segmentProto ->
      entity?.toDomainAccount()?.copy(defaultGraphSegment = segmentProto.toGraphSegment())
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
    AppLog.v(TAG, "Refreshing token for account: $accountId")
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

  private fun com.dmdbrands.gurus.weight.data.storage.db.entity.account.Account.toDomainAccount(): Account =
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
    try {
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
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to update account $accountId with API response data", e)
    }
  }

  override suspend fun markAccountExpired(accountId: String) {
    try {
      accountDao.markAccountExpired(accountId)
      AppLog.d(TAG, "Marked account $accountId as expired")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to mark account $accountId as expired", e)
    }
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
   * Gets the dashboard settings for the active account if it is not synced.
   * @return Dashboard settings if it exists and is not synced, otherwise null
   */
  override suspend fun getUnsyncedActiveDashboardSettings(): DashboardSettingsEntity? =
    accountDao.getUnsyncedActiveDashboardSettings().first()

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
        AppLog.e(TAG, "API logout failed", e)
        // Continue with local logout even if API fails
      }
      // Always perform local logout regardless of network status
      if (isActiveAccount) {
        accountDao.deactivateAllAccounts()
      }
      // Update account flags in DB: set isLoggedIn, isExpired, isActive to false
      accountDao.logoutAccount(accountId)
      markAccountExpired(accountId)
      // Clear tokens from DataStore and TokenManager
      userDataStore.clearAccountTokens(accountId)
      AppLog.d(TAG, "Logout successful (API attempted: $apiLogoutAttempted)")
      true
    } catch (e: Exception) {
      AppLog.e(TAG, "LogoutAccount failed", e)
      false
    }

  /**
   * Logs out all accounts both remotely (API) and locally (DB, tokens).
   * @return true if all accounts were logged out successfully, false otherwise
   */
  override suspend fun logoutAllAccounts(): Boolean =
    try {
      // Get all logged-in accounts
      val loggedInAccounts = accountDao.getAllLoggedInAccounts().first()
      // Sort accounts to handle active account last
      val sortedAccounts = loggedInAccounts.sortedWith(compareByDescending { it.account.isActiveAccount })
      for (accountData in sortedAccounts) {
        val account = accountData.account
        try {
          // Try to logout on API
          authAPI.logoutWithToken(LogoutRequest(account.fcmToken ?: ""), account.id)
        } catch (e: Exception) {
          AppLog.e(TAG, "API logout failed for account ${account.id}", e)
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
      AppLog.e(TAG, "Logout all failed", e)
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
    secureTokenStore.removeToken(accountId)
    userDataStore.clearAccountTokens(accountId)
  }

  /**
   * Removes the account with the given ID from the database.
   */
  override suspend fun removeAccount(accountId: String) {
    try {
      secureTokenStore.removeToken(accountId)
      userDataStore.clearAccountTokens(accountId)
    } catch (e: Exception) {
      AppLog.d(TAG, "Failed to clear account tokens")
    }
  }

  /**
   * Deletes the current user account via API and clears local data.
   */
  override suspend fun deleteAccount(accountID: String, isActiveAccount: Boolean) {
    // Call API to delete account
    try {
      if (isActiveAccount) {
        this.deleteAccountFromServer()
        accountDao.deleteAccountById(accountID)
        accountDao.deactivateAllAccounts()
      }

      // Clear all tokens and local data
      secureTokenStore.removeToken(accountID)
      userDataStore.clearAccountTokens(accountID)
      tokenManager.clearTokens()
      AppLog.d(TAG, "Account deleted in local data")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to delete account in local data", e)
      throw e
    }
  }

  private suspend fun deleteAccountFromServer() {
    try {
      userAPI.deleteAccount()
      AppLog.d(TAG, "Account deleted in server")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to delete account in server", e)
    }
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
        isWeightlessOn = account.isWeightlessOn,
        height = account.height,
        activityLevel = account.activityLevel,
        weightlessTimestamp = account.weightlessTimestamp,
        weightlessWeight = account.weightlessWeight,
        isStreakOn = account.isStreakOn,
        dashboardType = account.dashboardType,
        dashboardMetrics = account.dashboardMetrics,
        progressMetrics = account.progressMetrics,
        shouldSendEntryNotifications = account.shouldSendEntryNotifications,
        shouldSendWeightInEntryNotifications = account.shouldSendWeightInEntryNotifications,
        goalType = account.goalType,
        goalWeight = account.goalWeight?.toDouble(),
        initialWeight = account.initialWeight?.toDouble() ?: 0.0,
        metPreviousGoal = account.metPreviousGoal,
        goalPercent = account.goalPercent.toDouble(),
        // Phase 2 (MOB-377): account product list + measurement system (defaults if pre-Phase-2 response).
        productTypes = account.productTypes ?: listOf(ProductType.MY_WEIGHT.apiValue),
        measurementUnits = MeasurementUnits.fromValue(account.measurementUnits),
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
  }

  /**
   * Syncs all account settings with server data.
   * Updates local database with the latest settings from server.
   * Follows the same pattern as addAccountFromResponse during login.
   * @param accountInfo The account info from server or local account containing latest settings
   * @param isOnline Whether the sync is from online API call (true) or offline local data (false)
   */
  override suspend fun syncAccountSettingsWithServer(accountInfo: AccountInfo, isOnline: Boolean) {
    AppLog.d(TAG, "Syncing all settings for account: ${accountInfo.id}, isOnline: $isOnline")

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
        goalType = accountInfo.goalType,
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

      // Update integration settings
      val integrationsSettings = IntegrationsSettingsEntity(
        accountId = accountInfo.id,
        isFitbitOn = accountInfo.isFitbitOn,
        isFitbitValid = accountInfo.isFitbitValid,
        isHealthConnectOn = accountInfo.isHealthConnectOn,
        isHealthKitOn = accountInfo.isHealthKitOn,
        isMFPOn = accountInfo.isMFPOn,
        isMFPValid = accountInfo.isMFPValid,
        isSynced = true,
      )
      accountDao.updateIntegrationsSettings(integrationsSettings)

      // Update product settings (Phase 2 / MOB-377). Insert(REPLACE) upserts in case the
      // row predates this account's product_settings backfill.
      val productSettings = ProductSettingsEntity(
        accountId = accountInfo.id,
        productTypes = accountInfo.productTypes ?: listOf(ProductType.MY_WEIGHT.apiValue),
        measurementUnits = MeasurementUnits.fromValue(accountInfo.measurementUnits).value,
        isSynced = true,
      )
      accountDao.insertProductSettings(productSettings)

      // Mark account as synced only when online (from server)
      if (isOnline) {
        accountDao.markAccountSynced(accountInfo.id)
        AppLog.d(TAG, "Marked account as synced: ${accountInfo.id}")
      }
      AppLog.d(TAG, "Successfully synced all settings for account: ${accountInfo.id}")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to sync settings for account: ${accountInfo.id}", e)
      throw e
    }
  }

  override suspend fun hasShownNotificationAlertForAccount(accountId: String): Boolean =
    userDataStore.hasShownNotificationAlertForAccount(accountId)

  override suspend fun setNotificationAlertShownForAccount(accountId: String, hasShown: Boolean) {
    userDataStore.setNotificationAlertShownForAccount(accountId, hasShown)
  }

  override suspend fun setActiveBabyId(accountId: String, babyId: String) {
    babyProfileDao.setActiveBabyId(accountId, babyId)
  }

  override suspend fun getActiveBabyId(): String? {
    val accountId = getActiveAccount().first()?.id ?: return null
    return babyProfileDao.getActiveBabyId(accountId)
  }

  override suspend fun clearActiveBabyId(accountId: String) {
    babyProfileDao.clearActiveBabyId(accountId)
  }
}

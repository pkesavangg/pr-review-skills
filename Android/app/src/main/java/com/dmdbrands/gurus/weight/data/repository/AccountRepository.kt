package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.network.ISecureTokenStore
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.network.utility.HttpErrorResponse
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IAuthAPI
import com.dmdbrands.gurus.weight.data.api.IUserAPI
import com.dmdbrands.gurus.weight.data.repository.account.AccountPersistenceDataSource
import com.dmdbrands.gurus.weight.data.repository.account.AccountRemoteDataSource
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntityMapper
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.DashboardSettingsEntity
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.model.PartialAccount
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.api.auth.ChangePasswordResponse
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.dmdbrands.gurus.weight.domain.model.api.auth.LoginResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.api.user.ProductsRequest
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of the IAccountRepository interface.
 * Handles account operations using Room database and API calls.
 *
 * This is a thin coordinator: raw network calls are delegated to [AccountRemoteDataSource] and the
 * account-aggregate persistence (entity + settings writes) to [AccountPersistenceDataSource]. Both
 * collaborators are built from this repository's own injected dependencies, so DI wiring and unit
 * tests that mock the DAO/APIs are unaffected. Split out under MOB-1499 to clear detekt `LargeClass`.
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

  private val remote = AccountRemoteDataSource(authAPI, userAPI)
  private val persistence = AccountPersistenceDataSource(accountDao)

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
      val loginResponse = remote.login(email, password)
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
      val loginResponse = remote.signup(request)
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
  override suspend fun getAccountFromAPI(accountId: String): AccountInfo =
    remote.getAccountFromAPI(accountId)

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
      val response = remote.changePassword(oldPassword, newPassword)
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

  override suspend fun updateDashboardMetrics(dashboardKeys: List<String>) =
    remote.updateDashboardMetrics(dashboardKeys)

  override suspend fun updateProgressMetrics(progressKeys: List<String>) =
    remote.updateProgressMetrics(progressKeys)

  override suspend fun updateDashboardType(dashboardType: String) =
    remote.updateDashboardType(dashboardType)

  /**
   * Requests password reset via API and returns true if successful.
   */
  override suspend fun resetPassword(email: String): Response<Unit> =
    remote.resetPassword(email)

  override suspend fun emailCheck(email: String): Boolean =
    remote.emailCheck(email)

  override suspend fun updateMeasurementUnits(measurementUnits: MeasurementUnits) {
    val account = remote.updateMeasurementUnits(measurementUnits)
    // Persist the server-confirmed account state (incl. productTypes/measurementUnits) locally.
    syncAccountSettingsWithServer(account, isOnline = true)
  }

  override suspend fun updateProducts(productTypes: List<String>) {
    val account = remote.updateProducts(productTypes)
    // Persist the server-confirmed account state (incl. productTypes) locally.
    syncAccountSettingsWithServer(account, isOnline = true)
  }

  override suspend fun updateProfile(profileData: ProfileUpdateRequest) {
    try {
      // Call API to update profile
      val updatedAccountInfo = remote.updateProfile(profileData)
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

  override suspend fun addAccount(account: Account): Account =
    persistence.addAccount(account)

  override suspend fun updateAccount(
    accountId: String,
    partialUpdate: PartialAccount,
  ) = persistence.updateAccount(accountId, partialUpdate)

  override suspend fun updateLocalDashboardType(accountId: String, dashboardType: DashboardType) =
    persistence.updateLocalDashboardType(accountId, dashboardType)

  override suspend fun updateDashboardSettings(
    accountId: String,
    dashboardMetrics: List<String>,
    dashboardMilestones: List<String>,
    dashboardType: DashboardType,
    isSynced: Boolean
  ) = persistence.updateDashboardSettings(accountId, dashboardMetrics, dashboardMilestones, dashboardType, isSynced)

  /**
   * Gets the stored active account from the database as a Flow.
   *
   * `productTypes` is derived locally: the stored value is whatever the server last returned,
   * but a locally-present (non-deleted) baby profile means the account effectively owns the
   * "baby" product — so "baby" is injected here whenever the local baby list is non-empty.
   * This is intentionally local and self-healing: it holds offline (before the server has
   * registered the baby / added the product) and is re-derived on every emit, so a stale
   * server sync can't strip it back out while a local baby still exists.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getActiveAccount(): Flow<Account?> =
    accountDao.getActiveAccount().distinctUntilChanged().flatMapLatest { entity ->
      if (entity == null) {
        flowOf(null)
      } else {
        val account = entity.toDomainAccount()
        combine(
          userDataStore.defaultGraphSegmentFlow.distinctUntilChanged(),
          babyProfileDao.observeByAccountId(account.id).distinctUntilChanged(),
        ) { segmentProto, babies ->
          val hasLocalBaby = babies.any { !it.isDeleted }
          account
            .copy(defaultGraphSegment = segmentProto.toGraphSegment())
            .withBabyProduct(hasLocalBaby)
        }
      }
    }

  /**
   * Returns a copy with "baby" present in [Account.productTypes] when [hasLocalBaby] is true.
   * No-op when already present or when there are no local babies — never removes a product the
   * server granted.
   */
  private fun Account.withBabyProduct(hasLocalBaby: Boolean): Account {
    val babyValue = ProductType.BABY.apiValue
    if (!hasLocalBaby || productTypes.contains(babyValue)) return this
    return copy(productTypes = productTypes + babyValue)
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
  ): Token = remote.refreshToken(refreshToken, accountId)

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
  ) = persistence.updateAccountInfo(accountId, accountInfo)

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
        remote.logout(fcmToken, accountId)
        apiLogoutAttempted = true
      } catch (e: Exception) {
        AppLog.e(TAG, "API logout failed", e)
        // Continue with local logout even if API fails
      }
      // Always perform local logout regardless of network status
      if (isActiveAccount) {
        accountDao.deactivateAllAccounts()
      }
      // Keep the account row listed as "Logged out" (isLoggedIn stays 1) so it still
      // appears on the (Multi-)Landing screen — "Logged out ≠ gone" (MA-2672 / MOB-424).
      // markAccountExpired sets isExpired = 1 and isActiveAccount = 0, matching the
      // already-implemented auto-logout (401) state.
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
          remote.logout(account.fcmToken, account.id)
        } catch (e: Exception) {
          AppLog.e(TAG, "API logout failed for account ${account.id}", e)
          // Continue with local logout even if API fails
        }
      }
      // Mark all accounts as "Logged out" but keep them listed (isLoggedIn stays 1)
      // so they remain visible on the (Multi-)Landing screen — "Logged out ≠ gone"
      // (MA-2672 / MOB-424).
      accountDao.markAllAccountsExpired()
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
    val account = persistence.addAccountFromResponse(loginResponse)
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
   * Removes the account from this device only ("Removed = gone", MA-2672 / MOB-424).
   * Unlike [logoutAccount] (which keeps the account listed as "Logged out"), this fully
   * deletes the local account row and its related settings, then clears tokens. The server
   * account is NOT deleted — that is handled by [deleteAccount].
   */
  override suspend fun removeAccountFromDevice(
    accountId: String,
    fcmToken: String?,
    isActiveAccount: Boolean,
  ) {
    try {
      // Best-effort server-side session logout; continue with local removal regardless.
      try {
        remote.logout(fcmToken, accountId)
      } catch (e: Exception) {
        AppLog.e(TAG, "API logout during removeAccountFromDevice failed", e)
      }
      if (isActiveAccount) {
        accountDao.deactivateAllAccounts()
        tokenManager.clearTokens()
      }
      accountDao.deleteAllTables(accountId)
      clearAccountTokens(accountId)
      AppLog.d(TAG, "Account $accountId removed from this device")
    } catch (e: Exception) {
      AppLog.e(TAG, "removeAccountFromDevice failed", e)
    }
  }

  /**
   * Deletes the current user account via API and clears local data.
   */
  override suspend fun deleteAccount(accountID: String, isActiveAccount: Boolean) {
    // Call API to delete account
    try {
      if (isActiveAccount) {
        remote.deleteAccountFromServer()
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
   * @param accountInfo The account info from server or local account containing latest settings
   * @param isOnline Whether the sync is from online API call (true) or offline local data (false)
   */
  override suspend fun syncAccountSettingsWithServer(accountInfo: AccountInfo, isOnline: Boolean) =
    persistence.syncAccountSettingsWithServer(accountInfo, isOnline)

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

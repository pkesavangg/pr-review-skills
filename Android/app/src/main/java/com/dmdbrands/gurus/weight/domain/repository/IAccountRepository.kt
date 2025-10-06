package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.model.PartialAccount
import com.dmdbrands.gurus.weight.domain.model.api.auth.ChangePasswordResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.LoginResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountToken
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.dmdbrands.gurus.weight.proto.ThemeMode
import kotlinx.coroutines.flow.Flow
import retrofit2.Response

/**
 * Repository interface for managing user account operations.
 * Provides methods for account authentication, management, and data synchronization.
 */
interface IAccountRepository {
  /**
   * Logs in via API and returns the authenticated Account.
   * @param email User's email
   * @param password User's password
   * @return The authenticated Account
   */
  suspend fun login(email: String, password: String): Account

  /**
   * Signs up via API and returns the created Account.
   * @param request Signup request data
   * @return The created Account
   */
  suspend fun signup(request: SignupRequest): Account

  /**
   * Gets account info via API for a specific account and returns AccountResponse.
   * @param accountId The account ID to get info for
   * @return AccountInfo for the specified account
   */
  suspend fun getAccountFromAPI(accountId: String): AccountInfo

  /**
   * Changes the password for the specified account.
   * @param accountId The account ID
   * @param oldPassword The current password
   * @param newPassword The new password to set
   * @return ChangePasswordResponse with new tokens if successful
   */
  suspend fun updatePassword(accountId: String, oldPassword: String, newPassword: String): ChangePasswordResponse

  /**
   * Requests password reset via API.
   * @param email The email address to reset the password for
   * @return API response
   */
  suspend fun resetPassword(email: String): Response<Unit>

  /**
   * Updates the user's profile information via API and updates the local database.
   * @param profileData The profile data to update
   * @return The updated Account
   */
  suspend fun updateProfile(profileData: ProfileUpdateRequest)

  /**
   * Updates the dashboard metrics for the active account.
   * @param dashboardKeys The list of dashboard keys to update
   */
  suspend fun updateDashboardMetrics(dashboardKeys: List<String>)

  /**
   * Updates the dashboard type for the active account.
   * @param dashboardType The new dashboard type
   */
  suspend fun updateDashboardType(dashboardType: String)

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

  /**
   * Adds an account to the database with all entity relations and returns the domain model.
   * @param account The Account to add
   * @return The added Account
   */
  suspend fun addAccount(account: Account): Account

  /**
   * Updates an account in the database with partial data and returns the updated domain model.
   * @param accountId The ID of the account to update
   * @param partialUpdate Partial account data to update
   * @return The updated Account
   */
  suspend fun updateAccount(accountId: String, partialUpdate: PartialAccount)

  /**
   * Updates account info by ID using the provided account entity.
   * This method is used for updating account data from API responses.
   * @param accountId The ID of the account to update
   * @param accountInfo The account info from API response
   * @return The updated Account
   */
  suspend fun updateAccountInfo(accountId: String, accountInfo: AccountInfo)

  /**
   * Deactivates all accounts except the given account ID.
   * @param accountId The account ID to keep active
   */
  suspend fun deactivateOtherAccounts(accountId: String)

  /**
   * Activates the specified account by setting it as the active account.
   * @param accountId The account ID to activate
   */
  suspend fun activateAccount(accountId: String)

  /**
   * Updates tokens for the active account in the TokenManager.
   * @param request The token update request containing all token fields
   */
  suspend fun updateTokens(request: AccountToken)

  /**
   * Updates the last active time for the account in the database.
   * @param accountId The account ID to update
   */
  suspend fun updateLastActiveTime(accountId: String)

  /**
   * Gets the sync timestamp for the current account as a Flow.
   * @return Flow emitting the sync timestamp
   */
  suspend fun getSyncTimeStamp(): Flow<String>

  /**
   * Updates the sync timestamp for the current account.
   * @param timeStamp The new sync timestamp
   */
  suspend fun updateSyncTimeStamp(timeStamp: String)

  /**
   * Marks the specified account as expired in the database.
   * @param accountId The account ID to mark as expired
   */
  suspend fun markAccountExpired(accountId: String)

  /**
   * Gets all logged-in accounts from the database as a Flow.
   * @return Flow emitting the list of logged-in accounts
   */
  fun getLoggedInAccounts(): Flow<List<Account>>

  /**
   * Gets the stored active account from the database as a Flow.
   * @return Flow emitting the active account or null if none
   */
  fun getActiveAccount(): Flow<Account?>

  /**
   * Gets the active account if it is not synced.
   * @return The active account if it exists and is not synced, otherwise null
   */
  suspend fun getUnsyncedActiveAccount(): Account?

  /**
   * Logs out the account both remotely (API) and locally (DB, tokens).
   * @param accountId The ID of the account to log out
   * @param fcmToken The FCM token for push notifications (optional)
   * @param isActiveAccount Whether this is the active account
   * @return true if logout was successful, false otherwise
   */
  suspend fun logoutAccount(accountId: String, fcmToken: String?, isActiveAccount: Boolean): Boolean

  /**
   * Logs out all accounts both remotely (API) and locally (DB, tokens).
   * @return true if all accounts were logged out successfully, false otherwise
   */
  suspend fun logoutAllAccounts(): Boolean

  /**
   * Adds a new account from a LoginResponse, sets it as active, and updates tokens.
   * @param loginResponse The login response containing account and token info
   * @return The saved Account
   */
  suspend fun addAccountFromLoginResponse(loginResponse: LoginResponse): Account

  /**
   * Switches to a different account by setting it as active and updating tokens.
   * @param accountId The account ID to switch to
   */
  suspend fun switchToAccount(accountId: String)

  /**
   * Clears the tokens for the given account ID.
   * @param accountId The account ID whose tokens should be cleared
   */
  suspend fun clearAccountTokens(accountId: String)

  /**
   * Removes the account with the given ID from the database.
   * @param accountId The account ID to remove
   */
  suspend fun removeAccount(accountId: String)

  fun getActiveAccountWeightUnitFlow(): Flow<WeightUnit?>
  fun getActiveAccountWeightlessFlow(): Flow<Weightless>
  // Theme Mode Operations
  /**
   * Gets the current theme mode for the active account as a flow.
   * @return Flow of ThemeMode that emits changes
   */
  val currentThemeModeFlow: Flow<ThemeMode>

  /**
   * Sets the theme mode for the active account.
   * @param themeMode The ThemeMode to set
   */
  suspend fun setCurrentThemeMode(themeMode: ThemeMode)

  /**
   * Deletes the current user account via API and clears local data.
   */
  suspend fun deleteAccount(accountID: String, isActiveAccount: Boolean)

  /**
   * Syncs all account settings with server data.
   * Updates local database with the latest settings from server.
   * @param accountInfo The account info from server containing latest settings
   */
  suspend fun syncAccountSettingsWithServer(accountInfo: AccountInfo)

  /**
   * Gets whether the notification alert has been shown for the specified account.
   * @param accountId The account ID to check.
   * @return True if the notification alert has been shown for this account, false otherwise.
   */
  suspend fun hasShownNotificationAlertForAccount(accountId: String): Boolean

  /**
   * Sets whether the notification alert has been shown for the specified account.
   * @param accountId The account ID to update.
   * @param hasShown Whether the notification alert has been shown.
   */
  suspend fun setNotificationAlertShownForAccount(accountId: String, hasShown: Boolean)
  suspend fun updateLocalDashboardType(accountId: String, dashboardMetrics: String, dashboardType: DashboardType)
}

package com.dmdbrands.gurus.weight.data.storage.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.dmdbrands.gurus.weight.proto.DefaultGraphSegment
import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.dmdbrands.gurus.weight.proto.UserAccount
import com.dmdbrands.gurus.weight.proto.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import android.content.Context

/**
 * Extension property to provide UserPreferences DataStore instance from Context.
 */
val Context.userDataStore: DataStore<UserPreferences> by dataStore(
  fileName = "user_preferences.pb",
  serializer = UserPreferencesSerializer,
)

/**
 * DataStore for managing user accounts and preferences.
 * Provides flows and suspend functions for accessing and updating user data.
 */
class UserDataStore @Inject constructor(
  private val context: Context,
) : BaseProtoDataStore<UserPreferences>(
  dataStore = context.userDataStore,
) {
  /**
   * Emits a Flow of all user accounts, keyed by account ID.
   */
  val accountsFlow: Flow<Map<String, UserAccount>> = dataFlow.map { it.accountsMap }

  /**
   * Emits a Flow of the theme mode for the currently active account, or SYSTEM if none is active.
   */
  val currentThemeModeFlow: Flow<ThemeMode> = dataFlow.map {
    it.accountsMap.values.firstOrNull { account -> account.isActive }?.themeMode ?: ThemeMode.SYSTEM
  }

  /**
   * Emits a Flow of the current active account ID, or null if none is active.
   */
  val currentAccountIdFlow: Flow<String?> = dataFlow.map { userPreferences ->
    val entries = userPreferences.accountsMap.entries
    val activeEntry = entries.firstOrNull { entry -> entry.value.isActive }
    val currentId = activeEntry?.key
    currentId
  }

  /**
   * Emits a Flow of the current active UserAccount, or null if none is active.
   */
  val currentAccountFlow: Flow<UserAccount?> = dataFlow.map {
    it.accountsMap.values.firstOrNull { account -> account.isActive }
  }

  /**
   * Emits a Flow of the default graph segment for the currently active account.
   * UNSPECIFIED maps to MONTH (the desired default for fresh installs).
   */
  val defaultGraphSegmentFlow: Flow<DefaultGraphSegment> = dataFlow.map {
    it.accountsMap.values.firstOrNull { account -> account.isActive }?.defaultGraphSegment
      ?: DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_UNSPECIFIED
  }
  /**
   * Gets the theme mode for the currently active account, or SYSTEM if none is active.
   */
  suspend fun getCurrentThemeMode(): ThemeMode =
    getData().accountsMap.values.firstOrNull { it.isActive }?.themeMode ?: ThemeMode.SYSTEM

  /**
   * Sets the theme mode for a specific account.
   * @param accountId The account ID to update.
   * @param mode The ThemeMode to set.
   */
  suspend fun setThemeMode(accountId: String, mode: ThemeMode) {
    val current = getData()
    val updated = current.toBuilder().apply {
      val account = accountsMap[accountId]?.toBuilder()?.setThemeMode(mode)
      if (account != null) {
        putAccounts(accountId, account.build())
      }
    }.build()
    updateData { updated }
  }

  /**
   * Sets the specified account as active and deactivates all others.
   * @param accountId The account ID to activate.
   */
  suspend fun setActiveAccount(accountId: String) {
    val current = getData()
    val updated = current.toBuilder().apply {
      accountsMap.forEach { (id, account) ->
        putAccounts(id, account.toBuilder().setIsActive(false).build())
      }
      val activeAccount = accountsMap[accountId]?.toBuilder()?.setIsActive(true)?.build()
      if (activeAccount != null) {
        putAccounts(accountId, activeAccount)
      }
    }.build()
    updateData { updated }
  }

  /**
   * Updates the refresh and access tokens for a specific account.
   * @param accountId The account ID to update.
   * @param refreshToken The new refresh token.
   * @param accessToken The new access token.
   * @param expiresAt The expiration timestamp for the access token.
   * @param isActive Whether the account is active.
   */
  suspend fun updateAccountTokens(
    accountId: String,
    refreshToken: String,
    accessToken: String,
    expiresAt: String,
    isActive: Boolean = false
  ) {
    val current = getData()

    // If the account does not exist, add it and return early
    if (!current.accountsMap.containsKey(accountId)) {
      addAccount(
        accountId = accountId,
        isActive = isActive,
        refreshToken = refreshToken,
        accessToken = accessToken,
        expiresAt = expiresAt,
      )
      return
    }

    // Otherwise, update the existing account
    val updated = current.toBuilder().apply {
      val existingAccount = current.accountsMap[accountId]!!

      val updatedAccount = existingAccount.toBuilder()
        .setRefreshToken(refreshToken)
        .setAccessToken(accessToken)
        .setExpiresAt(expiresAt)
        .setIsActive(isActive)
        .build()

      putAccounts(accountId, updatedAccount)
    }.build()

    updateData { updated }
  }

  /**
   * Updates the sync timestamp for a specific account.
   * @param accountId The account ID to update.
   * @param syncTimestamp The new sync timestamp.
   */
  suspend fun updateSyncTimestamp(accountId: String, syncTimestamp: String) {
    val current = getData()
    val updated = current.toBuilder().apply {
      val account = accountsMap[accountId]?.toBuilder()
        ?.setSyncTimestamp(syncTimestamp)
      if (account != null) {
        putAccounts(accountId, account.build())
      }
    }.build()
    updateData { updated }
  }

  /**
   * Adds a new account to the DataStore.
   * @param accountId The account ID to add.
   * @param isActive Whether the account is active.
   * @param syncTimestamp The sync timestamp for the account.
   * @param refreshToken The refresh token for the account.
   * @param accessToken The access token for the account.
   * @param expiresAt The expiration timestamp for the access token.
   * @param themeMode The theme mode for the account.
   * @throws IllegalStateException if an account with the given ID already exists.
   */
  suspend fun addAccount(
    accountId: String,
    isActive: Boolean = false,
    syncTimestamp: String = "",
    refreshToken: String = "",
    accessToken: String = "",
    expiresAt: String = "",
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    forceUpdate: Boolean = false
  ) {
    val current = getData()
    if (forceUpdate) {
      updateAccount(
        accountId = accountId,
        syncTimestamp = syncTimestamp,
        refreshToken = refreshToken,
        accessToken = accessToken,
        expiresAt = expiresAt,
        isActive = isActive,
      )
      return
    }
    if (current.accountsMap.containsKey(accountId)) {
      throw IllegalStateException("Account with ID $accountId already exists")
    }

    val updated = current.toBuilder().apply {
      val account = UserAccount.newBuilder()
        .setIsActive(isActive)
        .setSyncTimestamp(syncTimestamp)
        .setRefreshToken(refreshToken)
        .setAccessToken(accessToken)
        .setExpiresAt(expiresAt)
        .setThemeMode(themeMode)
        .build()
      putAccounts(accountId, account)
    }.build()
    updateData { updated }
  }

  suspend fun containsAccount(accountId: String): Boolean {
    val current = getData()
    return current.accountsMap.containsKey(accountId)
  }

  /**
   * Updates an existing account in the DataStore.
   * Only updates the properties that are provided, keeping existing values for others.
   * @param accountId The account ID to update.
   * @param isActive Optional: Whether the account is active.
   * @param syncTimestamp Optional: The sync timestamp for the account.
   * @param refreshToken Optional: The refresh token for the account.
   * @param accessToken Optional: The access token for the account.
   * @param expiresAt Optional: The expiration timestamp for the access token.
   * @param themeMode Optional: The theme mode for the account.
   * @throws IllegalStateException if no account exists with the given ID.
   */
  suspend fun updateAccount(
    accountId: String,
    isActive: Boolean? = null,
    syncTimestamp: String? = null,
    refreshToken: String? = null,
    accessToken: String? = null,
    expiresAt: String? = null,
    themeMode: ThemeMode? = null
  ) {
    val current = getData()
    val existingAccount = current.accountsMap[accountId]
      ?: throw IllegalStateException("No account found with ID $accountId")

    val updated = current.toBuilder().apply {
      val accountBuilder = existingAccount.toBuilder()

      isActive?.let { accountBuilder.setIsActive(it) }
      syncTimestamp?.let { accountBuilder.setSyncTimestamp(it) }
      refreshToken?.let { accountBuilder.setRefreshToken(it) }
      accessToken?.let { accountBuilder.setAccessToken(it) }
      expiresAt?.let { accountBuilder.setExpiresAt(it) }
      themeMode?.let { accountBuilder.setThemeMode(it) }

      putAccounts(accountId, accountBuilder.build())
    }.build()
    updateData { updated }
  }

  override fun getDefaultInstance(): UserPreferences = UserPreferences.getDefaultInstance()

  /**
   * Clears all user data (removes all accounts).
   */
  override suspend fun clearData() {
    super.clearData()
  }

  /**
   * Logs out the current account by setting its isActive status to false.
   * Only affects the currently active account.
   */
  suspend fun logoutCurrentAccount() {
    val current = getData()
    val currentActiveAccount = current.accountsMap.entries.firstOrNull { it.value.isActive }

    if (currentActiveAccount != null) {
      val updated = current.toBuilder().apply {
        putAccounts(
          currentActiveAccount.key,
          currentActiveAccount.value.toBuilder().setIsActive(false).build(),
        )
      }.build()
      updateData { updated }
    }
  }

  /**
   * Removes the current active account from the DataStore completely.
   * This permanently deletes the account and all its data.
   */
  suspend fun removeCurrentAccount() {
    val current = getData()
    val currentActiveAccount = current.accountsMap.entries.firstOrNull { it.value.isActive }

    if (currentActiveAccount != null) {
      val updated = current.toBuilder().apply {
        removeAccounts(currentActiveAccount.key)
      }.build()
      updateData { updated }
    }
  }

  /**
   * Logs out all accounts by clearing their tokens and setting isActive to false.
   * This removes all authentication data while keeping the account records.
   */
  suspend fun logoutAllAccounts() {
    val current = getData()
    val updated = current.toBuilder().apply {
      accountsMap.forEach { (id, account) ->
        putAccounts(
          id,
          account.toBuilder()
            .setAccessToken("")
            .setRefreshToken("")
            .setExpiresAt("")
            .setIsActive(false)
            .build(),
        )
      }
    }.build()
    updateData { updated }
  }

  /**
   * Checks if any account exists in the DataStore.
   * @return True if at least one account exists, false otherwise.
   */
  suspend fun hasAccounts(): Boolean =
    getData().accountsMap.isNotEmpty()

  /**
   * Gets a UserAccount by its account ID, or null if not found.
   * @param accountId The account ID to look up.
   * @return The UserAccount if present, or null.
   */
  suspend fun getAccount(accountId: String): UserAccount? =
    getData().accountsMap[accountId]
  /**
   * Gets the expiresAt value for the current active account.
   * @return The expiresAt timestamp, or null if no account is active.
   */
  suspend fun getCurrentAccountExpiresAt(): String? =
    getData().accountsMap.values.firstOrNull { it.isActive }?.expiresAt

  /**
   * Removes an account from the DataStore.
   * @param accountId The account ID to remove.
   */
  suspend fun removeAccount(accountId: String) {
    val current = getData()
    val updated = current.toBuilder().apply {
      removeAccounts(accountId)
    }.build()
    updateData { updated }
  }

  /**
   * Clears the tokens for a specific account without removing the account.
   * @param accountId The account ID whose tokens should be cleared.
   */
  suspend fun clearAccountTokens(accountId: String) {
    val current = getData()
    val updated = current.toBuilder().apply {
      val userAccount = accountsMap[accountId]
      if (userAccount != null) {
        putAccounts(
          accountId,
          userAccount.toBuilder()
            .setAccessToken("")
            .setRefreshToken("")
            .setExpiresAt("")
            .setIsActive(false)
            .build(),
        )
      }
    }.build()
    updateData { updated }
  }

  /**
   * Gets whether the account switch info modal has been shown for this device (any account).
   * @return True if the modal has been shown for this device, false otherwise.
   */
  suspend fun hasShownAccountSwitchInfoModalForDevice(): Boolean =
    getData().hasShownAccountSwitchInfoModalForDevice

  /**
   * Sets whether the account switch info modal has been shown for this device (any account).
   * @param hasShown Whether the modal has been shown.
   */
  suspend fun setAccountSwitchInfoModalShownForDevice(hasShown: Boolean) {
    val current = getData()
    val updated = current.toBuilder()
      .setHasShownAccountSwitchInfoModalForDevice(hasShown)
      .build()
    updateData { updated }
  }

  /**
   * Sets the default graph segment for a specific account.
   * @param accountId The account ID to update.
   * @param segment The DefaultGraphSegment to persist.
   */
  suspend fun setDefaultGraphSegment(accountId: String, segment: DefaultGraphSegment) {
    val current = getData()
    val updated = current.toBuilder().apply {
      val account = accountsMap[accountId]?.toBuilder()?.setDefaultGraphSegment(segment)
      if (account != null) {
        putAccounts(accountId, account.build())
      }
    }.build()
    updateData { updated }
  }

  /**
   * Gets whether the notification alert has been shown for the specified account.
   * @param accountId The account ID to check.
   * @return True if the notification alert has been shown for this account, false otherwise.
   */
  suspend fun hasShownNotificationAlertForAccount(accountId: String): Boolean =
    getData().accountsMap[accountId]?.notificationAlertShown ?: false

  /**
   * Sets whether the notification alert has been shown for the specified account.
   * @param accountId The account ID to update.
   * @param hasShown Whether the notification alert has been shown.
   */
  suspend fun setNotificationAlertShownForAccount(accountId: String, hasShown: Boolean) {
    val current = getData()
    val account = current.accountsMap[accountId]
    if (account != null) {
      val updatedAccount = account.toBuilder()
        .setNotificationAlertShown(hasShown)
        .build()

      val updated = current.toBuilder()
        .putAccounts(accountId, updatedAccount)
        .build()
      updateData { updated }
    }
  }
}

/**
 * Serializer for UserPreferences proto.
 */
object UserPreferencesSerializer : Serializer<UserPreferences> {
  override val defaultValue: UserPreferences = UserPreferences.getDefaultInstance()

  override suspend fun readFrom(input: InputStream): UserPreferences =
    UserPreferences.parseFrom(input)

  override suspend fun writeTo(
    t: UserPreferences,
    output: OutputStream,
  ) = t.writeTo(output)
}

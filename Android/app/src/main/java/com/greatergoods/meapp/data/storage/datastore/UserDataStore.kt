package com.greatergoods.meapp.data.storage.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.greatergoods.meapp.proto.ThemeMode
import com.greatergoods.meapp.proto.UserAccount
import com.greatergoods.meapp.proto.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import android.content.Context
import android.util.Log

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
    val currentAccountIdFlow: Flow<String?> = dataFlow.map {
        it.accountsMap.entries.firstOrNull { entry -> entry.value.isActive }?.key
    }

    /**
     * Emits a Flow of the current active UserAccount, or null if none is active.
     */
    val currentAccountFlow: Flow<UserAccount?> = dataFlow.map {
        Log.d("UserDataStore", "currentAccountFlow: ${it.accountsMap.values}")
        it.accountsMap.values.firstOrNull { account -> account.isActive }
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
        themeMode: ThemeMode = ThemeMode.SYSTEM
    ) {
        val current = getData()
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

    /**
     * Clears all user data (removes all accounts).
     */
    override suspend fun clearData() {
        updateData { UserPreferences.getDefaultInstance() }
    }

    /**
     * Logs out the current account by removing it from UserDataStore.
     */
    suspend fun logoutCurrentAccount() {
        val current = getData()
        val updated = current.toBuilder().apply {
            accountsMap.forEach { (id, account) ->
                putAccounts(id, account.toBuilder().setIsActive(false).build())
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
                        .build(),
                )
            }
        }.build()
        updateData { updated }
    }

    /**
     * Gets whether the account switch info modal has been shown for a specific account.
     * @param accountId The account ID to check.
     * @return True if the modal has been shown, false otherwise.
     */
    suspend fun hasShownAccountSwitchInfoModal(accountId: String): Boolean =
        getData().accountsMap[accountId]?.hasShownAccountSwitchInfoModal ?: false

    /**
     * Sets whether the account switch info modal has been shown for a specific account.
     * @param accountId The account ID to update.
     * @param hasShown Whether the modal has been shown.
     */
    suspend fun setAccountSwitchInfoModalShown(accountId: String, hasShown: Boolean) {
        val current = getData()
        val updated = current.toBuilder().apply {
            val account = accountsMap[accountId]?.toBuilder()
                ?.setHasShownAccountSwitchInfoModal(hasShown)
            if (account != null) {
                putAccounts(accountId, account.build())
            }
        }.build()
        updateData { updated }
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

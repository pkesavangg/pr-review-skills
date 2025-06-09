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
class UserDataStore(
    context: Context,
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
            // Deactivate all accounts first
            accountsMap.forEach { (id, account) ->
                putAccounts(id, account.toBuilder().setIsActive(false).build())
            }
            // Activate the specified account
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
     */
    suspend fun updateAccountTokens(accountId: String, refreshToken: String, accessToken: String) {
        val current = getData()
        val updated = current.toBuilder().apply {
            val account = accountsMap[accountId]?.toBuilder()
                ?.setRefreshToken(refreshToken)
                ?.setAccessToken(accessToken)
            if (account != null) {
                putAccounts(accountId, account.build())
            }
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
            // Deactivate all accounts
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

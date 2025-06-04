package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.proto.UserAccount
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for user account operations, abstracting UserDataStore.
 */
interface IUserRepository {
    /** Emits a Flow of all user accounts, keyed by account ID. */
    val accountsFlow: Flow<Map<String, UserAccount>>

    /** Emits a Flow of the currently active UserAccount, or null if none is active. */
    val currentAccountFlow: Flow<UserAccount?>

    /** Gets the currently active UserAccount, or null if none is active. */
    suspend fun getCurrentAccount(): UserAccount?

    /** Sets the specified account as active and deactivates all others. */
    suspend fun setActiveAccount(accountId: String)

    /** Creates a new random account and saves it. */
    suspend fun createRandomAccount()

    /** Updates the refresh and access tokens for a specific account. */
    suspend fun updateAccountTokens(accountId: String, refreshToken: String, accessToken: String)

    /** Updates the sync timestamp for a specific account. */
    suspend fun updateSyncTimestamp(accountId: String, syncTimestamp: String)

    /** Checks if any account exists. */
    suspend fun hasAccounts(): Boolean

    /** Gets a UserAccount by its account ID, or null if not found. */
    suspend fun getAccount(accountId: String): UserAccount?

    /** Clears all user data (removes all accounts). */
    suspend fun clearData()
}

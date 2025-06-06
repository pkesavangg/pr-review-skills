package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.domain.repository.IUserRepository
import com.greatergoods.meapp.proto.UserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of IUserRepository using UserDataStore.
 */
@Singleton
class UserRepository @Inject constructor(
    private val userDataStore: UserDataStore
) : IUserRepository {
    /** Emits a Flow of all user accounts, keyed by account ID. */
    override val accountsFlow: Flow<Map<String, UserAccount>> = userDataStore.accountsFlow

    /** Emits a Flow of the currently active UserAccount, or null if none is active. */
    override var currentAccountFlow: Flow<UserAccount?> = userDataStore.currentAccountFlow

    /** Gets the currently active UserAccount, or null if none is active. */
    override suspend fun getCurrentAccount(): UserAccount? = userDataStore.currentAccountFlow
        .firstOrNull()

    /** Sets the specified account as active and deactivates all others. */
    override suspend fun setActiveAccount(accountId: String) = userDataStore.setActiveAccount(accountId)

    /** Creates a new random account and saves it. */
    override suspend fun createRandomAccount() {
        val accountId = UUID.randomUUID().toString()
        val newAccount = UserAccount.getDefaultInstance()
        val current = userDataStore.getData()
        val updated = current.toBuilder().putAccounts(accountId, newAccount).build()
        userDataStore.updateData { updated }
    }

    /** Updates the refresh and access tokens for a specific account. */
    override suspend fun updateAccountTokens(accountId: String, refreshToken: String, accessToken: String) =
        userDataStore.updateAccountTokens(accountId, refreshToken, accessToken)

    /** Updates the sync timestamp for a specific account. */
    override suspend fun updateSyncTimestamp(accountId: String, syncTimestamp: String) =
        userDataStore.updateSyncTimestamp(accountId, syncTimestamp)

    /** Checks if any account exists. */
    override suspend fun hasAccounts(): Boolean = userDataStore.hasAccounts()

    /** Gets a UserAccount by its account ID, or null if not found. */
    override suspend fun getAccount(accountId: String): UserAccount? = userDataStore.getAccount(accountId)

    /** Clears all user data (removes all accounts). */
    override suspend fun clearData() = userDataStore.clearData()

    /** Logs out the current account by removing it from UserDataStore. */
    override suspend fun logoutCurrentAccount() {
        userDataStore.logoutCurrentAccount()
        currentAccountFlow = userDataStore.currentAccountFlow
    }
}

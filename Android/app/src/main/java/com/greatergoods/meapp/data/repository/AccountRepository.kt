package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.account.Account
import com.greatergoods.meapp.domain.repository.IAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Implementation of the IAccountRepository interface.
 * Handles account operations using Room database.
 */
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
) : IAccountRepository {
    override suspend fun login(email: String, password: String): Account {
        throw NotImplementedError("Stub")
    }

    override suspend fun logout(accountId: String, showLogoutAlert: Boolean) {
        // No-op
    }

    override suspend fun logoutAllAccounts() {
        // No-op
    }

    override suspend fun createAccount(accountData: Map<String, Any>): Account {
        throw NotImplementedError("Stub")
    }

    override suspend fun updateProfile(profile: Map<String, Any>): Account {
        throw NotImplementedError("Stub")
    }

    override suspend fun updateBodyComp(bodyComp: Map<String, Any>): Account {
        throw NotImplementedError("Stub")
    }

    override suspend fun updatePassword(oldPassword: String, newPassword: String) {
        // No-op
    }

    override suspend fun requestPasswordReset(email: String) {
        // No-op
    }

    override suspend fun switchAccount(accountData: Account?) {
        // No-op
    }

    override suspend fun deleteAccount(account: Account) {
        // No-op
    }

    override fun getActiveAccount(): Flow<Account?> {
        return flowOf(null)
    }

    override fun getAllLoggedInAccounts(): Flow<List<Account>> {
        return flowOf(emptyList())
    }

    override suspend fun refreshAccount(): Account {
        throw NotImplementedError("Stub")
    }

    override suspend fun updateTokens(tokens: Map<String, String>) {
        // No-op
    }

    override suspend fun clearOfflineData() {
        // No-op
    }
}

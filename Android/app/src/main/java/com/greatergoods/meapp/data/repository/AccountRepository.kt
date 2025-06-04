package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.*
import com.greatergoods.meapp.domain.repository.IAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Stub implementation of the IAccountRepository interface.
 * All methods are empty or return default values for compilation.
 */
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
) : IAccountRepository {
    override suspend fun login(email: String, password: String): AccountEntity {
        throw NotImplementedError("Stub")
    }

    override suspend fun logout(accountId: String, showLogoutAlert: Boolean) {
        // No-op
    }

    override suspend fun logoutAllAccounts() {
        // No-op
    }

    override suspend fun createAccount(accountData: Map<String, Any>): AccountEntity {
        throw NotImplementedError("Stub")
    }

    override suspend fun updateProfile(profile: Map<String, Any>): AccountEntity {
        throw NotImplementedError("Stub")
    }

    override suspend fun updateBodyComp(bodyComp: Map<String, Any>): AccountEntity {
        throw NotImplementedError("Stub")
    }

    override suspend fun updatePassword(oldPassword: String, newPassword: String) {
        // No-op
    }

    override suspend fun requestPasswordReset(email: String) {
        // No-op
    }

    override suspend fun switchAccount(accountData: AccountEntity?) {
        // No-op
    }

    override suspend fun deleteAccount(account: AccountEntity) {
        // No-op
    }

    override fun getActiveAccount(): Flow<AccountEntity?> {
        return flowOf(null)
    }

    override fun getAllLoggedInAccounts(): Flow<List<AccountEntity>> {
        return flowOf(emptyList())
    }

    override suspend fun refreshAccount(): AccountEntity {
        throw NotImplementedError("Stub")
    }

    override suspend fun updateTokens(tokens: Map<String, String>) {
        // No-op
    }

    override suspend fun clearOfflineData() {
        // No-op
    }
}

package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.data.storage.db.entity.account.Account
import com.greatergoods.meapp.data.storage.db.entity.account.AccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user account operations.
 * Provides methods for account authentication, management, and data synchronization.
 */
interface IAccountRepository {
    // API Operations
    suspend fun login(email: String, password: String): Account
    suspend fun createAccount(email: String, password: String): Account
    suspend fun updateProfile(profile: Map<String, Any>): Account
    suspend fun updatePassword(oldPassword: String, newPassword: String): Account
    suspend fun requestPasswordReset(email: String): Boolean
    suspend fun logout(accountId: String): Boolean
    suspend fun deleteAccount(): Boolean
    suspend fun refreshAccount(): Account

    // DB Operations
    suspend fun insertAccount(account: Account): Account
    suspend fun updateAccount(account: Account): Account
    suspend fun deleteAccount(account: Account)
    suspend fun deleteAccountById(accountId: String)
    suspend fun removeAllAccounts()
    
    // Account Queries
    fun getAccount(accountId: String): Flow<Account?>
    fun getActiveAccount(): Flow<Account?>
    fun getAllLoggedInAccounts(): Flow<List<Account>>
    suspend fun getStoredActiveAccount(): Account?
    
    // Account State Management
    suspend fun deactivateOtherAccounts(accountId: String)
    suspend fun logoutAccount(accountId: String)
    suspend fun logoutAllAccounts()
    suspend fun updateSyncStatus(accountId: String, isSynced: Boolean)
    
    // Token Management
    suspend fun updateTokens(tokens: Map<String, String>)
    
    // Sync Operations
    fun getUnsyncedAccounts(): Flow<List<AccountEntity>>
    suspend fun markAllAccountsSynced()
    suspend fun markAccountSynced(accountId: String)
}

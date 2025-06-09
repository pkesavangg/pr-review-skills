package com.greatergoods.meapp.data.repository

import android.util.Log
import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.network.TokenManager
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.account.Account
import com.greatergoods.meapp.data.storage.db.entity.account.AccountEntity
import com.greatergoods.meapp.data.storage.db.entity.account.AccountEntityMapper
import com.greatergoods.meapp.data.storage.db.entity.account.AccountMapper
import com.greatergoods.meapp.data.storage.db.entity.account.UserDataStore
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.user.CreateAccountRequest
import com.greatergoods.meapp.domain.model.api.user.Token
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IAuthAPI
import com.greatergoods.meapp.domain.repository.IUserAPI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of the IAccountRepository interface.
 * Handles account operations using Room database and API calls.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val userDataStore: UserDataStore,
    private val tokenManager: TokenManager,
    private val authAPI: IAuthAPI,
    private val userAPI: IUserAPI,
    private val accountEntityMapper: AccountEntityMapper,
    private val accountMapper: AccountMapper
) : IAccountRepository {

    companion object {
        private const val TAG = "AccountRepository"
    }

    // API Operations
    override suspend fun loginInAPI(email: String, password: String): LoginResponse {
        return authAPI.login(email, password)
    }

    override suspend fun signupInAPI(request: CreateAccountRequest): Map<String, Any> {
        return userAPI.createAccount(request)
    }

    override suspend fun logoutInAPI(fcmToken: String?) {
        authAPI.logout(fcmToken)
    }

    override suspend fun getAccountInAPI(): Map<String, Any> {
        return userAPI.getAccount()
    }

    override suspend fun updatePasswordInAPI(oldPassword: String, newPassword: String): Map<String, Any> {
        return userAPI.updatePassword(oldPassword, newPassword)
    }

    override suspend fun resetPasswordInAPI(email: String): Map<String, Any> {
        return userAPI.resetPassword(email)
    }

    // DB Operations
    override suspend fun addAccountInDB(account: Account): Account {
        Log.d(TAG, "Adding account: ${account.email}")
        val accountEntity = accountEntityMapper.toEntity(account)
        accountDao.insertAccount(accountEntity)
        return account
    }

    override suspend fun removeAccountInDB(accountId: String) {
        Log.d(TAG, "Removing account: $accountId")
        accountDao.deleteAccountById(accountId)
        // Also clear tokens from TokenManager if this was the active account
        if (accountId == userDataStore.getData().activeAccountId) {
            tokenManager.clearTokens()
        }
    }

    override suspend fun removeAllAccountsInDB() {
        Log.d(TAG, "Removing all accounts")
        accountDao.deleteAllAccounts()
        tokenManager.clearTokens()
    }

    override suspend fun getStoredActiveAccountFromDB(): Account? {
        return accountDao.getActiveAccount()?.let { accountEntityMapper.toDomain(it) }
    }

    override suspend fun deactivateOtherAccountsInDB(accountId: String) {
        Log.d(TAG, "Deactivating other accounts except: $accountId")
        accountDao.deactivateOtherAccounts(accountId)
    }

    override fun getLoggedInAccountsFromDB(): Flow<List<Account>> {
        return accountDao.getLoggedInAccounts().map { accounts ->
            accounts.map { accountEntityMapper.toDomain(it) }
        }
    }

    override suspend fun updateTokensInDB(tokens: Map<String, String>) {
        Log.d(TAG, "Updating tokens for active account")
        tokenManager.setTokens(
            accessToken = tokens[AppConfig.ACCESS_TOKEN_KEY] ?: "",
            refreshToken = tokens[AppConfig.REFRESH_TOKEN_KEY] ?: "",
            expiresAt = tokens[AppConfig.EXPIRES_AT_KEY] ?: ""
        )
    }

    override suspend fun refreshTokenInAPI(refreshToken: String): Token {
        Log.d(TAG, "Refreshing token")
        return authAPI.refreshToken(refreshToken)
    }

    override suspend fun updateLastActiveTimeInDB(accountId: String) {
        Log.d(TAG, "Updating last active time for account: $accountId")
        accountDao.updateLastActiveTime(accountId, System.currentTimeMillis())
    }
}

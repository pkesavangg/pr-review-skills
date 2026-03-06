package com.dmdbrands.gurus.weight.core.network

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface ITokenManager {
    val tokens: StateFlow<Token?>
    val otherUserToken: StateFlow<Token?>
    val tokenRefreshed: StateFlow<Boolean>
    val logoutUser: StateFlow<Boolean>
    val isOtherUserTokenRefreshed: Boolean
    val accountTokens: Map<String, Token>
    suspend fun setTokens(token: Token)

    fun setOtherUserToken(token: Token?)

    suspend fun clearTokens()

    fun setTokenRefreshed(refreshed: Boolean)

    fun setLogoutUser(logout: Boolean)

    suspend fun refreshToken(): Boolean

    fun isTokenExpired(): Boolean

    suspend fun getAccessToken(): String?

    suspend fun getRefreshToken(): String?

    fun getTokenExpiresAt(): String?

    suspend fun getAccessToken(accountId: String): String?

    suspend fun getRefreshToken(accountId: String): String?

    fun getAccountIdForToken(token: String): String?

    suspend fun loadAllTokens()

  suspend fun getCurrentAccountID(): String?
  suspend fun getCurrentAcccountExpiresAt(): String?
  suspend fun getAccountExpiresAt(accountId: String): String?
}

@Singleton
class TokenManager
@Inject
constructor(
    private val userDataStore: UserDataStore,
) : ITokenManager {
    companion object {
        private const val TAG = "TokenManager"
    }

    private val _tokens = MutableStateFlow<Token?>(null)
    override val tokens: StateFlow<Token?> = _tokens

    private val _otherUserToken = MutableStateFlow<Token?>(null)
    override val otherUserToken: StateFlow<Token?> = _otherUserToken

    private val _tokenRefreshed = MutableStateFlow(false)
    override val tokenRefreshed: StateFlow<Boolean> = _tokenRefreshed

    private val _logoutUser = MutableStateFlow(false)
    override val logoutUser: StateFlow<Boolean> = _logoutUser

    override var isOtherUserTokenRefreshed = false
        private set

    // In-memory map for multi-account token management (ConcurrentHashMap for thread safety)
    override val accountTokens = ConcurrentHashMap<String, Token>()

    override suspend fun setTokens(token: Token) {
        AppLog.v(TAG, "Setting tokens for account: ${token.accountId}")
        accountTokens[token.accountId] = token
        userDataStore.updateAccountTokens(
            accountId = token.accountId,
            isActive = token.isActive,
            refreshToken = token.refreshToken ?: "",
            accessToken = token.accessToken ?: "",
            expiresAt = token.expiresAt ?: "",
        )
        _tokens.value = token
    }

    override fun setOtherUserToken(token: Token?) {
        AppLog.v(TAG, "Setting other user token")
        _otherUserToken.value = token
    }

    override suspend fun clearTokens() {
        AppLog.d(TAG, "Clearing all tokens")
        _tokens.value = null
        _otherUserToken.value = null
        isOtherUserTokenRefreshed = false
        userDataStore.logoutCurrentAccount()
    }

    override fun setTokenRefreshed(refreshed: Boolean) {
        AppLog.v(TAG, "Setting token refreshed: $refreshed")
        _tokenRefreshed.value = refreshed
    }

    override fun setLogoutUser(logout: Boolean) {
        AppLog.d(TAG, "Setting logout user: $logout")
        _logoutUser.value = logout
    }

    override suspend fun refreshToken(): Boolean {
        AppLog.v(TAG, "Refreshing token")
        // This method should be implemented in the repository or a service, not here.
        // Always return false for now to avoid DI cycles.
        return false
    }

    override fun isTokenExpired(): Boolean {
        val token = _tokens.value ?: return true
        return token.expiresAt?.let { expiresAt ->
            try {
                val expirationDate = Date(expiresAt.toLong())
                expirationDate.before(Date())
            } catch (e: Exception) {
                AppLog.e(TAG, "Error checking token expiration", e)
                true
            }
        } ?: true
    }

    override suspend fun getAccessToken(): String? =
        userDataStore.currentAccountFlow.first()?.accessToken

    override suspend fun getRefreshToken(): String? =
        userDataStore.currentAccountFlow.first()?.refreshToken

    override fun getTokenExpiresAt(): String? = _tokens.value?.expiresAt

  override suspend fun getAccessToken(accountId: String): String? {
    AppLog.d("Token manager", "Processing request for account: $accountId")
    return try {
      accountTokens[accountId]?.accessToken
        ?: userDataStore.getData().accounts[accountId]?.accessToken
    } catch (e: Exception) {
      // Log the error if needed
      AppLog.e("AccountRepo", "Error getting access token for accountId: $accountId", e)
      null
    }
  }


  override suspend fun getRefreshToken(accountId: String): String? {
        // Prefer in-memory map for fast lookup
        return accountTokens[accountId]?.refreshToken
            ?: userDataStore.getData().accounts[accountId]?.refreshToken
    }

    override fun getAccountIdForToken(token: String): String? =
        accountTokens.entries
            .find {
                it.value.accessToken == token || it.value.refreshToken == token
            }?.key

    override suspend fun loadAllTokens() {
        AppLog.v(TAG, "Loading all tokens from UserDataStore")
        accountTokens.clear()
        val allAccounts = userDataStore.getData().accounts
        allAccounts.forEach { (id, userAccount) ->
            accountTokens[id] =
                Token(
                    accountId = id,
                    isActive = userAccount.isActive,
                    accessToken = userAccount.accessToken,
                    refreshToken = userAccount.refreshToken,
                    expiresAt = userAccount.expiresAt,
                )
        }
    }

  override suspend fun getCurrentAccountID(): String? {
    return userDataStore.currentAccountIdFlow.first()
  }

  override suspend fun getCurrentAcccountExpiresAt(): String? {
    return userDataStore.currentAccountFlow.first()?.expiresAt
  }

  override suspend fun getAccountExpiresAt(accountId: String): String? {
    // Prefer in-memory map for fast lookup
    return accountTokens[accountId]?.expiresAt
        ?: userDataStore.getData().accounts[accountId]?.expiresAt
  }

}

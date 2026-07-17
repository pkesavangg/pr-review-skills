package com.dmdbrands.gurus.weight.core.network

import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
import com.dmdbrands.gurus.weight.domain.services.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
    private val secureTokenStore: ISecureTokenStore,
    private val appNavigationService: IAppNavigationService,
) : ITokenManager {
    companion object {
        private const val TAG = "TokenManager"
    }

    /**
     * Guards against an encryption-failure storm. When the encrypted store is unavailable, many
     * concurrent token reads (getAccessToken, getAccountExpiresAt, refresh, …) each throw and would
     * each emit [AuthState.EncryptionFailure] → each triggers a logout → the app "blinks/loops"
     * (MOB-1537 / MOB-1526). We handle the failure once per process instead. Reset when a token is
     * saved successfully (i.e. encryption is working again).
     */
    // AtomicBoolean (not @Volatile) so the once-per-process guard is atomic: concurrent token reads
    // that all fail when encryption is down race here, and only the winner of compareAndSet(false,
    // true) emits the forced-logout event — preventing the logout storm / blink loop. (MOB-1537)
    private val encryptionFailureHandled = AtomicBoolean(false)

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
        // Persist to encrypted storage
        try {
            secureTokenStore.saveToken(token.accountId, token)
            // Encryption is working again — clear the failure guard/counter so a future genuine
            // failure is handled afresh.
            encryptionFailureHandled.set(false)
            secureTokenStore.resetEncryptionFailureCount()
        } catch (e: EncryptionUnavailableException) {
            handleEncryptionFailure(token.accountId, e)
            return
        }
        // Ensure UserDataStore account entry exists and is active
        // (required for currentAccountIdFlow, themeMode, syncTimestamp)
        userDataStore.updateAccount(
            accountId = token.accountId,
            isActive = token.isActive,
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
        // Clear from encrypted storage
        val currentAccountId = userDataStore.currentAccountIdFlow.first()
        if (currentAccountId != null) {
            try {
                secureTokenStore.removeToken(currentAccountId)
            } catch (e: EncryptionUnavailableException) {
                AppLog.e(TAG, "Encryption unavailable during clearTokens — tokens already inaccessible", e)
            }
        }
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

    override suspend fun getAccessToken(): String? {
        val accountId = userDataStore.currentAccountIdFlow.first() ?: return null
        return try {
            accountTokens[accountId]?.accessToken
                ?: secureTokenStore.getToken(accountId)?.accessToken
        } catch (e: EncryptionUnavailableException) {
            handleEncryptionFailure(accountId, e)
            null
        }
    }

    override suspend fun getRefreshToken(): String? {
        val accountId = userDataStore.currentAccountIdFlow.first() ?: return null
        return try {
            accountTokens[accountId]?.refreshToken
                ?: secureTokenStore.getToken(accountId)?.refreshToken
        } catch (e: EncryptionUnavailableException) {
            handleEncryptionFailure(accountId, e)
            null
        }
    }

    override fun getTokenExpiresAt(): String? = _tokens.value?.expiresAt

  override suspend fun getAccessToken(accountId: String): String? {
    AppLog.d(TAG, "Processing request for account: $accountId")
    return try {
      accountTokens[accountId]?.accessToken
        ?: secureTokenStore.getToken(accountId)?.accessToken
    } catch (e: EncryptionUnavailableException) {
      handleEncryptionFailure(accountId, e)
      null
    } catch (e: Exception) {
      AppLog.e(TAG, "Error getting access token for accountId: $accountId", e)
      null
    }
  }

  override suspend fun getRefreshToken(accountId: String): String? {
    return try {
      accountTokens[accountId]?.refreshToken
        ?: secureTokenStore.getToken(accountId)?.refreshToken
    } catch (e: EncryptionUnavailableException) {
      handleEncryptionFailure(accountId, e)
      null
    }
  }

    override fun getAccountIdForToken(token: String): String? =
        accountTokens.entries
            .find {
                it.value.accessToken == token || it.value.refreshToken == token
            }?.key

    override suspend fun loadAllTokens() {
        AppLog.v(TAG, "Loading all tokens from SecureTokenStore")
        accountTokens.clear()
        try {
            val allTokens = secureTokenStore.getAllTokens()
            allTokens.forEach { (id, token) ->
                accountTokens[id] = token
            }
        } catch (e: EncryptionUnavailableException) {
            AppLog.e(TAG, "Encryption unavailable — cannot load tokens", e)
            // Don't emit auth event here; let AppViewModel handle startup flow.
            // If no tokens are loaded, user will be routed to login naturally.
        }
    }

  override suspend fun getCurrentAccountID(): String? {
    return userDataStore.currentAccountIdFlow.first()
  }

  override suspend fun getCurrentAcccountExpiresAt(): String? {
    val accountId = userDataStore.currentAccountIdFlow.first() ?: return null
    return try {
      accountTokens[accountId]?.expiresAt
        ?: secureTokenStore.getToken(accountId)?.expiresAt
    } catch (e: EncryptionUnavailableException) {
      handleEncryptionFailure(accountId, e)
      null
    }
  }

  override suspend fun getAccountExpiresAt(accountId: String): String? {
    return try {
      accountTokens[accountId]?.expiresAt
        ?: secureTokenStore.getToken(accountId)?.expiresAt
    } catch (e: EncryptionUnavailableException) {
      handleEncryptionFailure(accountId, e)
      null
    }
  }

  /**
   * Handles encryption failure by clearing in-memory tokens and emitting
   * [AuthState.EncryptionFailure] to force re-login for all accounts.
   */
  private suspend fun handleEncryptionFailure(accountId: String?, e: EncryptionUnavailableException) {
    accountTokens.clear()
    _tokens.value = null
    _otherUserToken.value = null
    // Only emit the forced-logout event once per process. Repeated concurrent token reads all fail
    // when encryption is down; emitting for each would cause a logout storm / infinite blink loop.
    // compareAndSet makes the check-and-claim atomic so exactly one concurrent caller proceeds.
    if (!encryptionFailureHandled.compareAndSet(false, true)) {
      AppLog.w(TAG, "Encryption unavailable — already handled this session, suppressing re-emit")
      return
    }
    secureTokenStore.incrementEncryptionFailureCount()
    AppLog.e(
      TAG,
      "Encryption unavailable — forcing re-login (failure #${secureTokenStore.getEncryptionFailureCount()})",
      e,
    )
    appNavigationService.emitAuthEvent(AuthState.EncryptionFailure(accountId))
  }
}

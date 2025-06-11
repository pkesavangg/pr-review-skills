package com.greatergoods.meapp.core.network

import com.greatergoods.meapp.core.logging.AppLog
import com.greatergoods.meapp.data.repository.AccountRepository
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.domain.model.api.user.Token
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

interface ITokenManager {
    val tokens: StateFlow<Token?>
    val otherUserToken: StateFlow<Token?>
    val tokenRefreshed: StateFlow<Boolean>
    val logoutUser: StateFlow<Boolean>
    val isOtherUserTokenRefreshed: Boolean

    suspend fun setTokens(token: Token)
    fun setOtherUserToken(token: Token?)
    fun clearTokens()
    fun setTokenRefreshed(refreshed: Boolean)
    fun setLogoutUser(logout: Boolean)
    suspend fun refreshToken(): Boolean
    fun isTokenExpired(): Boolean
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun getTokenExpiresAt(): String?
    suspend fun getAccessToken(accountId: String): String?
    suspend fun getRefreshToken(accountId: String): String?
}

@Singleton
class TokenManager @Inject constructor(
    private val userDataStore: UserDataStore
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

    override suspend fun setTokens(token: Token) {
        AppLog.d(TAG, "Setting tokens")
        userDataStore.updateAccountTokens(
            accountId = token.accountId,
            refreshToken = token.refreshToken ?: "",
            accessToken = token.accessToken ?: "",
            expiresAt = token.expiresAt ?: ""
        )
        _tokens.value = token
    }

    override fun setOtherUserToken(token: Token?) {
        AppLog.d(TAG, "Setting other user token")
        _otherUserToken.value = token
    }

    override fun clearTokens() {
        AppLog.d(TAG, "Clearing tokens")
        _tokens.value = null
        _otherUserToken.value = null
        isOtherUserTokenRefreshed = false
    }

    override fun setTokenRefreshed(refreshed: Boolean) {
        AppLog.d(TAG, "Setting token refreshed: $refreshed")
        _tokenRefreshed.value = refreshed
    }

    override fun setLogoutUser(logout: Boolean) {
        AppLog.d(TAG, "Setting logout user: $logout")
        _logoutUser.value = logout
    }

    override suspend fun refreshToken(): Boolean {
        AppLog.d(TAG, "Refreshing token")
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
                AppLog.e(TAG, "Error checking token expiration", e.toString())
                true
            }
        } ?: true
    }

    override fun getAccessToken(): String? = _tokens.value?.accessToken

    override fun getRefreshToken(): String? = _tokens.value?.refreshToken

    override fun getTokenExpiresAt(): String? = _tokens.value?.expiresAt

    override suspend fun getAccessToken(accountId: String): String? {
        val userPrefs = userDataStore.getData()
        return userPrefs.accounts[accountId]?.accessToken
    }

    override suspend fun getRefreshToken(accountId: String): String? {
        val userPrefs = userDataStore.getData()
        return userPrefs.accounts[accountId]?.refreshToken
    }
}
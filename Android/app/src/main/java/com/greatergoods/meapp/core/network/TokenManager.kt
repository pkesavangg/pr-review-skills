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

@Singleton
class TokenManager @Inject constructor(
    private val accountRepository: AccountRepository,
    private val userDataStore: UserDataStore
) {
    companion object {
        private const val TAG = "TokenManager"
    }

    private val _tokens = MutableStateFlow<Token?>(null)
    val tokens: StateFlow<Token?> = _tokens

    private val _otherUserToken = MutableStateFlow<Token?>(null)
    val otherUserToken: StateFlow<Token?> = _otherUserToken

    private val _tokenRefreshed = MutableStateFlow(false)
    val tokenRefreshed: StateFlow<Boolean> = _tokenRefreshed

    private val _logoutUser = MutableStateFlow(false)
    val logoutUser: StateFlow<Boolean> = _logoutUser

    var isOtherUserTokenRefreshed = false
        private set

    fun setTokens(token: Token) {
        AppLog.d(TAG, "Setting tokens")
        _tokens.value = token
    }

    fun setOtherUserToken(token: Token?) {
        AppLog.d(TAG, "Setting other user token")
        _otherUserToken.value = token
    }

    fun clearTokens() {
        AppLog.d(TAG, "Clearing tokens")
        _tokens.value = null
        _otherUserToken.value = null
        isOtherUserTokenRefreshed = false
    }

    fun setTokenRefreshed(refreshed: Boolean) {
        AppLog.d(TAG, "Setting token refreshed: $refreshed")
        _tokenRefreshed.value = refreshed
    }

    fun setLogoutUser(logout: Boolean) {
        AppLog.d(TAG, "Setting logout user: $logout")
        _logoutUser.value = logout
    }

    suspend fun refreshToken(): Boolean {
        AppLog.d(TAG, "Refreshing token")
        return try {
            val currentToken = _tokens.value ?: return false
            val refreshToken = currentToken.refreshToken ?: return false
            
            val newToken = accountRepository.refreshToken(refreshToken)
            if (newToken.accessToken != null) {
                setTokens(newToken)
                setTokenRefreshed(true)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error refreshing token", e.toString())
            false
        }
    }

    fun isTokenExpired(): Boolean {
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

    fun getAccessToken(): String? = _tokens.value?.accessToken

    fun getRefreshToken(): String? = _tokens.value?.refreshToken

    fun getTokenExpiresAt(): String? = _tokens.value?.expiresAt

    suspend fun getAccessToken(accountId: String): String? {
        val userPrefs = userDataStore.getData()
        return userPrefs.accounts[accountId]?.accessToken
    }

    suspend fun getRefreshToken(accountId: String): String? {
        val userPrefs = userDataStore.getData()
        return userPrefs.accounts[accountId]?.refreshToken
    }
} 
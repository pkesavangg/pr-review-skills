package com.dmdbrands.gurus.weight.core.network.interceptors

import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.config.NetworkConfig
import com.dmdbrands.gurus.weight.core.network.HttpClient
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.RefreshTokenAPI
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.model.api.auth.RefreshTokenRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
import com.dmdbrands.gurus.weight.domain.services.AuthState
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Authenticator for handling token refresh when API calls return 401 Unauthorized.
 * Extracts account ID from request headers and preserves it during token refresh.
 */
class TokenAuthenticator @Inject constructor(
    private val tokenManager: ITokenManager,
    private val refreshTokenAPI: RefreshTokenAPI,
    private val userDataStore: UserDataStore,
    private val appNavigationService: IAppNavigationService
) : Authenticator {
    companion object {
        private const val TAG = "TokenAuthenticator"
        private const val TOKEN_EXPIRY_BUFFER_MINUTES = 5 // Same as Angular: 5 minutes buffer
        private const val MAX_REFRESH_ATTEMPTS = 3 // Same as Angular: max 3 attempts
    }

    private var isRefreshingToken = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())


    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (NetworkConfig.isPublicEndpoint(request.url.encodedPath) || responseCount(response) > 1) {
            AppLog.v(TAG, "Skipping token refresh for public endpoint or repeated attempt")
            return null
        }
      val accountId = request.header(HttpClient.ACCOUNT_ID_HEADER)
      AppLog.v(TAG, "Attempting token refresh for account: $accountId")
        // Skip refresh for login endpoint (same as Angular)
        if (request.url.encodedPath.contains("/account/login")) {
            AppLog.v(TAG, "Skipping token refresh for login endpoint")
            return null
        }


        // Try to refresh the token (same as Angular tokenRefresh)
        return runBlocking {
            try {
              val expiresAt = tokenManager.getCurrentAcccountExpiresAt()
              var refreshResult: Token? = null
              if (isTokenExpired(expiresAt)) {
                AppLog.v(TAG, "Token expires within 5 minutes, refreshing proactively...")
                refreshResult = refreshToken(accountId)
              }

                if (refreshResult == null) {
                    return@runBlocking null
                }

                // Build new request with fresh access token
                val newRequest = response.request.newBuilder()
                    .header(AppConfig.AUTHORIZATION_HEADER, "Bearer ${refreshResult.accessToken}")
                    .build()

                return@runBlocking newRequest
            } catch (e: Exception) {
                AppLog.e(TAG, "Token refresh failed for account: $accountId", e.toString())
                logoutUser(accountId)
                return@runBlocking null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var currentResponse = response
        while (currentResponse.priorResponse != null) {
            count++
            currentResponse = currentResponse.priorResponse!!
        }
        return count
    }

    /**
     * Logout user when token refresh fails (same as Angular)
     */
    private suspend fun logoutUser(accountId: String?) {
        AppLog.w(TAG, "Logging out user due to token refresh failure for account: $accountId")

        // Emit logout event (same as Angular: this.logoutUser.next(true))
        if (accountId != null) {
            appNavigationService.emitAuthEvent(AuthState.UnauthorizedLogout(accountId))
        }

        // Clear tokens (same as Angular: this.clearTokens())
        userDataStore.logoutCurrentAccount()
    }

    /**
     * Check if token is expired or will expire soon (same as Angular checkTokenExpiration)
     */
    private fun isTokenExpired(expiresAt: String?): Boolean {
        if (expiresAt.isNullOrEmpty()) return true

        return try {
            val tokenExpires = dateFormat.parse(expiresAt)
            val currentTime = Date()
            val timeUntilExpiry = tokenExpires!!.time - currentTime.time
            val bufferTime = TOKEN_EXPIRY_BUFFER_MINUTES * 60 * 1000 // 5 minutes in milliseconds
          AppLog.e(TAG, "${timeUntilExpiry}${bufferTime}")

            timeUntilExpiry <= bufferTime
        } catch (e: Exception) {
            AppLog.e(TAG, "Error parsing token expiry date: $expiresAt", e.toString())
            false // Assume expired if parsing fails
        }
    }

    /**
     * Refresh token for the given account (same as Angular tokenRefresh)
     * Used for both proactive refresh and 401 handling
     * @param accountId The account ID to refresh token for
     * @return The new token response if successful, null if failed
     */
    private suspend fun refreshToken(accountId: String?): com.dmdbrands.gurus.weight.domain.model.api.user.Token? {
        try {
            val refreshToken = if (accountId != null) {
                tokenManager.getRefreshToken(accountId)
            } else {
                tokenManager.getRefreshToken()
            }

            if (refreshToken.isNullOrEmpty()) {
                AppLog.e(TAG, "No refresh token available for account: $accountId")
                logoutUser(accountId)
                return null
            }

            // Check if already refreshing to avoid multiple simultaneous refreshes
            if (isRefreshingToken) {
                AppLog.v(TAG, "Token refresh already in progress, waiting...")
                return null
            }

            isRefreshingToken = true
            AppLog.v(TAG, "Refreshing token for account: $accountId")

            val newTokenResponse = refreshTokenAPI.refreshToken(RefreshTokenRequest(refreshToken))

            if (newTokenResponse.accessToken.isEmpty()) {
                AppLog.e(TAG, "Received empty access token from refresh response")
                isRefreshingToken = false
                logoutUser(accountId)
                return null
            }

            // Create new token with preserved account ID
            val newToken = com.dmdbrands.gurus.weight.domain.model.api.user.Token(
                accountId = accountId ?: "",
                isActive = true,
                accessToken = newTokenResponse.accessToken,
                refreshToken = newTokenResponse.refreshToken,
                expiresAt = newTokenResponse.expiresAt,
            )

            // Update tokens in TokenManager
            tokenManager.setTokens(newToken)
            isRefreshingToken = false
            AppLog.v(TAG, "Successfully refreshed token for account: $accountId")

            return newToken

        } catch (e: Exception) {
            isRefreshingToken = false
            AppLog.e(TAG, "Token refresh failed for account: $accountId", e.toString())
            logoutUser(accountId)
            return null
        }
    }
}

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
        private const val TOKEN_EXPIRY_BUFFER_MS = 5 * 60 * 1000 // Same as Angular: 5 minutes buffer in milliseconds
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
      var accountId = request.header(HttpClient.ACCOUNT_ID_HEADER)

      AppLog.v(TAG, "Attempting token refresh for account: $accountId")
        // Skip refresh for login endpoint (same as Angular)
        if (request.url.encodedPath.contains("/account/login")) {
            AppLog.v(TAG, "Skipping token refresh for login endpoint")
            return null
        }
        // Try to refresh the token (same as Angular: proactive + reactive)
        return runBlocking {
            try {
              if(accountId == null){
                accountId = tokenManager.getCurrentAccountID()
              }

                // Check if this is a non-active account early to skip refresh attempt
                val isCurrentAccount = isCurrentAccount(accountId)
                if (!isCurrentAccount) {
                    // For non-active accounts, skip token refresh entirely to avoid blocking
                    // The AccountService will handle marking the account as expired
                    AppLog.v(TAG, "Skipping token refresh for non-active account: $accountId - failing fast")
                    return@runBlocking null
                }

                val expiresAt = if (accountId != null) {
                    tokenManager.getAccountExpiresAt(accountId)
                } else {
                  tokenManager.getCurrentAcccountExpiresAt()
                }

              AppLog.v(TAG, "Token expires at: $expiresAt for account: $accountId")
                var refreshResult: Token? = null
                if (isTokenExpired(expiresAt)) {
                    AppLog.v(TAG, "Token expires within 5 minutes - refreshing proactively for account: $accountId")
                    refreshResult = refreshToken(accountId)
                } else {
                    AppLog.v(TAG, "401 received - attempting reactive token refresh for account: $accountId")
                    refreshResult = refreshToken(accountId)
                }

                if (refreshResult == null) {
                    AppLog.e(TAG, "Token refresh failed - logging out user for account: $accountId")
                    // For current account, logout and return null to fail the request
                    logoutUser(accountId, true)
                    return@runBlocking null
                }

                // Build new request with fresh access token (retry the original request)
                val newRequest = response.request.newBuilder()
                    .header(AppConfig.AUTHORIZATION_HEADER, "Bearer ${refreshResult.accessToken}")
                    .build()

                AppLog.v(TAG, "Token refreshed successfully - retrying original request for account: $accountId")
                return@runBlocking newRequest
            } catch (e: Exception) {
                AppLog.e(TAG, "Token refresh failed for account: $accountId", e.toString())
                // At this point, we know it's the current account (non-active accounts are skipped earlier)
                // For current account, logout and return null to fail the request
                logoutUser(accountId, true)
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
     * Check if the given account ID is the current active account
     */
    private suspend fun isCurrentAccount(accountId: String?): Boolean {
        if (accountId == null) {
            AppLog.v(TAG, "No account ID provided, assuming current account")
            return true // If no account ID, assume it's current account
        }

        val currentAccountId = tokenManager.getCurrentAccountID()
        val isCurrent = accountId == currentAccountId
        AppLog.v(TAG, "Checking if account $accountId is current account $currentAccountId: $isCurrent")
        return isCurrent
    }

    /**
     * Logout user when token refresh fails (same as Angular)
     * Only called for current/active accounts
     */
    private suspend fun logoutUser(accountId: String?, isCurrentAccount: Boolean) {
        AppLog.w(TAG, "Logging out current user due to token refresh failure for account: $accountId")

        // Emit logout event and navigate to landing screen for current account
        if (isCurrentAccount && accountId != null) {
            AppLog.i(TAG, "Current account logout - navigating to landing screen")
            appNavigationService.emitAuthEvent(AuthState.UnauthorizedLogout(accountId))
        }
        // Clear tokens for current account
        if (isCurrentAccount) {
            userDataStore.logoutCurrentAccount()
        }
    }

    /**
     * Check if token is expired or will expire soon (same as Angular checkTokenExpiration)
     */
    private fun isTokenExpired(expiresAt: String?): Boolean {
        if (expiresAt.isNullOrEmpty()) {
            AppLog.v(TAG, "No expiry time available - assuming token is expired")
            return true // If no expiry time, assume expired and refresh
        }

        return try {
            val tokenExpires = dateFormat.parse(expiresAt)
            val currentTime = Date()
            val timeUntilExpiry = tokenExpires!!.time - currentTime.time

            val isExpired = timeUntilExpiry <= TOKEN_EXPIRY_BUFFER_MS

            if (isExpired) {
                val minutesUntilExpiry = timeUntilExpiry / (1000 * 60)
                AppLog.v(TAG, "Token expires within ${TOKEN_EXPIRY_BUFFER_MS / (1000 * 60)} minutes (${minutesUntilExpiry} minutes remaining)")
            } else {
                AppLog.v(TAG, "Token is still valid for ${timeUntilExpiry / (1000 * 60)} minutes")
            }

            isExpired
        } catch (e: Exception) {
            AppLog.e(TAG, "Error parsing token expiry date: $expiresAt", e.toString())
            true // Assume expired if parsing fails
        }
    }

    /**
     * Refresh token for the given account (same as Angular tokenRefresh)
     * Used for 401 handling with retry logic
     * @param accountId The account ID to refresh token for
     * @return The new token response if successful, null if failed
     */
    private suspend fun refreshToken(accountId: String?): Token? {
        return refreshTokenWithRetry(accountId, 0)
    }

    /**
     * Refresh token with retry logic (same as Angular tokenRefresh with retry)
     * @param accountId The account ID to refresh token for
     * @param retryAttempt Current retry attempt number
     * @return The new token response if successful, null if failed
     */
    private suspend fun refreshTokenWithRetry(accountId: String?, retryAttempt: Int): com.dmdbrands.gurus.weight.domain.model.api.user.Token? {
        try {
            val refreshToken = if (accountId != null) {
                tokenManager.getRefreshToken(accountId)
            } else {
                tokenManager.getRefreshToken()
            }

            if (refreshToken.isNullOrEmpty()) {
                AppLog.e(TAG, "No refresh token available for account: $accountId")
                return null
            }

            // Check if already refreshing to avoid multiple simultaneous refreshes
            if (isRefreshingToken) {
                AppLog.v(TAG, "Token refresh already in progress, waiting...")
                return null
            }

            isRefreshingToken = true
            AppLog.v(TAG, "Refreshing token for account: $accountId (attempt: ${retryAttempt + 1})")

            val newTokenResponse = refreshTokenAPI.refreshToken(RefreshTokenRequest(refreshToken))

            if (newTokenResponse.accessToken.isEmpty()) {
                AppLog.e(TAG, "Received empty access token from refresh response")
                isRefreshingToken = false
                return null
            }

            // Create new token with preserved account ID
            val newToken = Token(
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
            AppLog.e(TAG, "Token refresh failed for account: $accountId (attempt: ${retryAttempt + 1})", e.toString())

            // Check if this is a 401 error (token invalidated) or network error
            val isTokenInvalidated = e.message?.contains("401") == true ||
                                   e.message?.contains("Unauthorized") == true

            if (isTokenInvalidated) {
                AppLog.e(TAG, "Token invalidated (401) - not retrying for account: $accountId")
                return null
            } else if (retryAttempt < MAX_REFRESH_ATTEMPTS - 1) {
                // Retry for network errors (same as Angular)
                AppLog.v(TAG, "Retrying token refresh for account: $accountId (attempt: ${retryAttempt + 2})")
                return refreshTokenWithRetry(accountId, retryAttempt + 1)
            } else {
                AppLog.e(TAG, "Max retry attempts reached for account: $accountId")
                return null
            }
        }
    }
}

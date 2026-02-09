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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
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
        private const val MAX_REFRESH_ATTEMPTS = 3
    }

    private var isRefreshingToken = false
    private var ongoingRefreshDeferred: CompletableDeferred<Token?>? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())


    override fun authenticate(route: Route?, response: Response): Request? {
        // Already retried? Give up to prevent infinite retry loops
        if (responseCount(response) >= MAX_REFRESH_ATTEMPTS) {
            AppLog.w(TAG, "Token refresh already attempted - giving up to prevent infinite retry loop")
            return null
        }

        val request = response.request
        if (NetworkConfig.isPublicEndpoint(request.url.encodedPath)) {
            AppLog.v(TAG, "Skipping token refresh for public endpoint or repeated attempt")
          return null
        }
      var accountId = request.header(HttpClient.ACCOUNT_ID_HEADER)

      AppLog.v(TAG, "Attempting token refresh for account: $accountId")
        // Try to refresh the token (same as Angular: proactive + reactive)
        return runBlocking {
            try {
              if(accountId == null){
                accountId = tokenManager.getCurrentAccountID()
              }

                val expiresAt = if (accountId != null) {
                  tokenManager.getAccountExpiresAt(accountId)
                } else {
                  tokenManager.getCurrentAcccountExpiresAt()
                }

              AppLog.v(TAG, "Token expires at: $expiresAt for account: $accountId")
                var refreshResult: Token? = null

                if (!expiresAt.isNullOrEmpty() &&  isTokenExpired(expiresAt)) {
                    AppLog.v(TAG, "Token expires within 5 minutes - refreshing proactively for account: $accountId")
                    refreshResult = refreshToken(accountId)
                } else {
                    AppLog.v(TAG, "401 received - attempting reactive token refresh for account: $accountId")
                    refreshResult = refreshToken(accountId)
                }

                if (refreshResult != null) {
                  // Build new request with fresh access token (retry the original request)
                  val newRequest = response.request.newBuilder()
                    .header(AppConfig.AUTHORIZATION_HEADER, "Bearer ${refreshResult.accessToken}")
                    .build()

                  AppLog.v(TAG, "Token refreshed successfully - retrying original request for account: $accountId")
                  return@runBlocking newRequest
                }
              else {
                  AppLog.e(TAG, "Token refresh failed - logging out user for account: $accountId")
                  logoutUser(accountId, isCurrentAccount(accountId))
                  return@runBlocking null
              }

            } catch (e: Exception) {
                AppLog.e(TAG, "Token refresh failed for account: $accountId", e.toString())
                // At this point, we know it's the current account (non-active accounts are skipped earlier)
                // For current account, logout and return null to fail the request
              logoutUser(accountId, isCurrentAccount(accountId))
              return@runBlocking null
            }
        }
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
        if (isCurrentAccount && accountId != null) {
            AppLog.i(TAG, "Current account logout - navigating to landing screen")
            appNavigationService.emitAuthEvent(AuthState.UnauthorizedLogout(accountId))
          userDataStore.logoutCurrentAccount()
        }
    }

    /**
     * Check if token is expired or will expire soon (same as Angular checkTokenExpiration)
     */
    private fun isTokenExpired(expiresAt: String): Boolean {
        return try {
            val tokenExpires = dateFormat.parse(expiresAt)
            val currentTime = Date()
            val timeUntilExpiry = tokenExpires!!.time - currentTime.time
            AppLog.v(TAG, "Token expires at: $expiresAt ($timeUntilExpiry ms until expiry)")

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
     * Used for 401 handling
     * @param accountId The account ID to refresh token for
     * @return The new token response if successful, null if failed
     */
    private suspend fun refreshToken(accountId: String?): Token? {
        return tokenRefresh(accountId)
    }

    /**
     * Refresh token (same as Angular tokenRefresh method)
     * Matches Angular implementation: waits for ongoing refresh
     * @param accountId The account ID to refresh token for
     * @return The new token response if successful, null if failed
     */
    private suspend fun tokenRefresh(accountId: String?): Token? {
        // If not refreshing, proceed with refresh
        if (!isRefreshingToken) {
            isRefreshingToken = true
            // Emit refresh started (equivalent to tokenRefreshed.next(false) in Angular)
            val deferred = CompletableDeferred<Token?>()
            ongoingRefreshDeferred = deferred
            try {
                val refreshToken = if (accountId != null) {
                    tokenManager.getRefreshToken(accountId)
                } else {
                    tokenManager.getRefreshToken()
                }

                if (refreshToken.isNullOrEmpty()) {
                    AppLog.e(TAG, "No refresh token available for account: $accountId")
                    logoutUser(accountId, isCurrentAccount(accountId))
                    isRefreshingToken = false
                    deferred.complete(null)
                    ongoingRefreshDeferred = null
                    return null
                }

                AppLog.v(TAG, "Refreshing token for account: $accountId")

                // Make refresh token API call
                val newTokenResponse = refreshTokenAPI.refreshToken(RefreshTokenRequest(refreshToken))

              if (newTokenResponse.accessToken.isEmpty()) {
                    AppLog.e(TAG, "Received empty access token from refresh response")
                    isRefreshingToken = false
                    deferred.complete(null)
                    ongoingRefreshDeferred = null
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

                // Update tokens in TokenManager (equivalent to setTokens in Angular)
                tokenManager.setTokens(newToken)

                // Emit refresh success (equivalent to tokenRefreshed.next(true) in Angular)
                isRefreshingToken = false
                deferred.complete(newToken)
                ongoingRefreshDeferred = null
                AppLog.v(TAG, "Successfully refreshed token for account: $accountId")

                return newToken

            } catch (e: Exception) {
                isRefreshingToken = false
                AppLog.e(TAG, "Token refresh failed for account: $accountId", e.toString())

                // Check error status (same as Angular: err.status === 401)
                val errorStatus = getErrorStatus(e)

              when (e) {
                is java.net.UnknownHostException,
                is java.net.SocketTimeoutException,
                is java.io.InterruptedIOException,
                is java.io.IOException -> {
                  // network problem: don't logout
                  deferred.complete(null)
                  ongoingRefreshDeferred = null
                  return null
                }
              }

              // Only logout when refresh endpoint says 401/403
              val httpCode = (e as? retrofit2.HttpException)?.code()
                ?: (e.cause as? retrofit2.HttpException)?.code()

              if (httpCode == 401 || httpCode == 403 || errorStatus == 401) {
                logoutUser(accountId, isCurrentAccount(accountId))
              }
              deferred.complete(null)
              ongoingRefreshDeferred = null
              return null
            }
        } else {
            // Already refreshing - wait for ongoing refresh to complete (same as Angular Promise subscription)
            AppLog.v(TAG, "Token refresh already in progress, waiting for completion...")
            val deferred = ongoingRefreshDeferred
            return if (deferred != null) {
                // Wait for the ongoing refresh to complete
                deferred.await()
            } else {
                // If deferred is null, refresh might have just completed, try again
                AppLog.v(TAG, "Ongoing refresh completed, retrying...")
                tokenRefresh(accountId)
            }
        }
    }

    /**
     * Extract HTTP error status from exception (similar to Angular err.status)
     * @param e The exception to extract status from
     * @return HTTP status code, or 0 for network errors, or -1 if unknown
     */
    private fun getErrorStatus(e: Exception): Int {
        return when (e) {
            is IOException -> 0 // Network error
            else -> {
                // Try to extract status from exception message or cause
                val message = e.message ?: ""
                when {
                    message.contains("401") || message.contains("Unauthorized") -> 401
                    message.contains("500") -> 500
                    message.contains("502") -> 502
                    message.contains("503") -> 503
                    message.contains("504") -> 504
                    else -> {
                        // Check if it's a Retrofit HttpException
                        val cause = e.cause
                        if (cause is retrofit2.HttpException) {
                            cause.code()
                        } else {
                            -1 // Unknown error
                        }
                    }
                }
            }
        }
    }

    /**
     * Count the number of responses in the chain to prevent infinite retry loops.
     * @param response The response to count
     * @return The number of responses in the chain (1 for original, 2+ for retries)
     */
    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

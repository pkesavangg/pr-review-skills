package com.dmdbrands.gurus.weight.core.network.interceptors

import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.config.NetworkConfig
import com.dmdbrands.gurus.weight.core.network.HttpClient
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.RefreshTokenAPI
import com.dmdbrands.gurus.weight.domain.model.api.auth.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

/**
 * Authenticator for handling token refresh when API calls return 401 Unauthorized.
 * Extracts account ID from request headers and preserves it during token refresh.
 */
class TokenAuthenticator @Inject constructor(
    private val tokenManager: ITokenManager,
    private val refreshTokenAPI: RefreshTokenAPI
) : Authenticator {
    companion object {
        private const val TAG = "TokenAuthenticator"
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        
        // Avoid retrying for public endpoints or repeated attempts
        if (NetworkConfig.isPublicEndpoint(request.url.encodedPath) || responseCount(response) > 1) {
            AppLog.v(TAG, "Skipping token refresh for public endpoint or repeated attempt")
            return null
        }

        // Extract account ID from request headers
        val accountId = request.header(HttpClient.ACCOUNT_ID_HEADER)
        AppLog.v(TAG, "Attempting token refresh for account: $accountId")

        // Try to refresh the token
        return runBlocking {
            try {
                // Get refresh token for the specific account
                val refreshToken = if (accountId != null) {
                    tokenManager.getRefreshToken(accountId)
                } else {
                    tokenManager.getRefreshToken()
                }
                
                if (refreshToken.isNullOrEmpty()) {
                    AppLog.e(TAG, "No refresh token available for account: $accountId")
                    return@runBlocking null
                }

                AppLog.v(TAG, "Refreshing token for account: $accountId")
                val newTokenResponse = refreshTokenAPI.refreshToken(RefreshTokenRequest(refreshToken))
                
                if (newTokenResponse.accessToken.isNullOrEmpty()) {
                    AppLog.e(TAG, "Received empty access token from refresh response")
                    return@runBlocking null
                }

                // Create new token with preserved account ID
                val newToken = com.dmdbrands.gurus.weight.domain.model.api.user.Token(
                    accountId = accountId ?: "", // Preserve the account ID
                    isActive = true,
                    accessToken = newTokenResponse.accessToken,
                    refreshToken = newTokenResponse.refreshToken, // Use new refresh token
                    expiresAt = newTokenResponse.expiresAt,
                )

                // Update tokens in TokenManager
                tokenManager.setTokens(newToken)
                AppLog.v(TAG, "Successfully refreshed tokens for account: $accountId")

                // Build new request with fresh access token
                val newRequest = response.request.newBuilder()
                    .header(AppConfig.AUTHORIZATION_HEADER, "Bearer ${newTokenResponse.accessToken}")
                    .build()

                return@runBlocking newRequest
            } catch (e: Exception) {
                AppLog.e(TAG, "Token refresh failed for account: $accountId", e)
                return@runBlocking null
            }
        }
    }

    /**
     * Refreshes tokens for all logged-in accounts and updates their expiration status.
     */
    suspend fun refreshAllAccounts() {
        // Remove refreshAllAccounts or move to repository/service if needed
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
}

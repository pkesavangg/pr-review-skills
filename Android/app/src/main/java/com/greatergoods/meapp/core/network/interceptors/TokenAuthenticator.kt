package com.greatergoods.meapp.core.network.interceptors

import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.config.NetworkConfig
import com.greatergoods.meapp.core.network.ITokenManager
import com.greatergoods.meapp.data.api.RefreshTokenAPI
import com.greatergoods.meapp.domain.model.api.auth.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

class TokenAuthenticator @Inject constructor(
    private val tokenManager: ITokenManager,
    private val refreshTokenAPI: RefreshTokenAPI
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        // Avoid retrying for public endpoints or repeated attempts
        if (NetworkConfig.isPublicEndpoint(request.url.encodedPath) || responseCount(response) > 1) {
            return null
        }

        // Try to refresh the token
        return runBlocking {
            try {
                val refreshToken = tokenManager.getRefreshToken()
                if (refreshToken.isNullOrEmpty()) {
                    return@runBlocking null
                }
                val newTokenResponse = refreshTokenAPI.refreshToken(RefreshTokenRequest(refreshToken))
                if (newTokenResponse.accessToken.isNullOrEmpty()) {
                    return@runBlocking null
                }
                tokenManager.setTokens(
                    com.greatergoods.meapp.domain.model.api.user.Token(
                        accountId = "", // Set appropriately if available
                        accessToken = newTokenResponse.accessToken,
                        refreshToken = newTokenResponse.refreshToken,
                        expiresAt = newTokenResponse.expiresAt,
                    ),
                )
                return@runBlocking response.request.newBuilder()
                    .header(AppConfig.AUTHORIZATION_HEADER, "Bearer ${newTokenResponse.accessToken}")
                    .build()
            } catch (e: Exception) {
                e.printStackTrace()
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

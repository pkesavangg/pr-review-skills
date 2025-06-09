package com.greatergoods.meapp.core.network.interceptors

import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.config.NetworkConfig
import com.greatergoods.meapp.core.network.TokenManager
import com.greatergoods.meapp.data.repository.AccountRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val accountRepository: AccountRepository
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
                if (tokenManager.refreshToken()) {
                    val accessToken = tokenManager.getAccessToken()
                    if (accessToken != null) {
                        request.newBuilder()
                            .header(AppConfig.AUTHORIZATION_HEADER, "Bearer $accessToken")
                            .build()
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Refreshes tokens for all logged-in accounts and updates their expiration status.
     */
    suspend fun refreshAllAccounts() {
        val accounts = accountRepository.getLoggedInAccountsFromDB().first()
        accounts.forEach { account ->
            try {
                val refreshToken = account.account.refreshToken
                if (refreshToken.isNotEmpty()) {
                    val newToken = accountRepository.refreshToken(refreshToken)
                    if (newToken.accessToken != null) {
                        // Update account with new tokens
                        accountRepository.updateTokens(mapOf(
                            "accessToken" to newToken.accessToken,
                            "refreshToken" to (newToken.refreshToken ?: refreshToken),
                            "expiresAt" to (newToken.expiresAt ?: "")
                        ))
                    } else {
                        // Remove account if refresh failed
                        accountRepository.removeAccount(account.account.id)
                    }
                }
            } catch (e: Exception) {
                // Remove account if refresh failed
                accountRepository.removeAccount(account.account.id)
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
}

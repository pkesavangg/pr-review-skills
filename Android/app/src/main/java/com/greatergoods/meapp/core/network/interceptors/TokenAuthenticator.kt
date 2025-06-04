package com.greatergoods.meapp.core.network.interceptors

import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.config.NetworkConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

class TokenAuthenticator @Inject constructor(
     // private val tokenRepository: TokenRepository
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        // Avoid retrying for public endpoints or repeated attempts
        // If we already tried to refresh the token, don't try again
        if (NetworkConfig.isPublicEndpoint(request.url.encodedPath) || responseCount(response) > 1) {
            return null
        }
        // Try to refresh the token
        return runBlocking {
            // TODO: val newToken = tokenRepository.refreshToken()
            val newToken = "new-mock-token"
            if (newToken != null) {
                request.newBuilder()
                    .header(AppConfig.AUTHORIZATION_HEADER, "Bearer $newToken")
                    .build()
            } else {
                null
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

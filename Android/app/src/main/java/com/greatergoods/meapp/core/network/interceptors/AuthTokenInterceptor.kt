package com.greatergoods.meapp.core.network.interceptors

import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.config.NetworkConfig
import com.greatergoods.meapp.core.network.HttpClient
import com.greatergoods.meapp.core.network.ITokenManager
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Interceptor that adds Authorization headers to HTTP requests.
 * Supports multi-account token management by checking for X-Account-ID headers.
 */
class AuthTokenInterceptor @Inject constructor(
    private val tokenManager: ITokenManager
) : Interceptor {
    companion object {
        private const val TAG = "AuthTokenInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Skip token for public endpoints
        if (NetworkConfig.isPublicEndpoint(request.url.encodedPath)) {
            AppLog.d(TAG, "Skipping token for public endpoint: ${request.url.encodedPath}")
            return chain.proceed(request)
        }

        // Check for account ID header to determine which token to use
        val accountId = request.header(HttpClient.ACCOUNT_ID_HEADER)
        AppLog.d(TAG, "Processing request for account: $accountId")

        // Get the appropriate access token
        val accessToken = runBlocking(Dispatchers.IO) {
            if (accountId != null) {
                tokenManager.getAccessToken(accountId)
            } else {
                tokenManager.getAccessToken()
            }
        }

        // Build a new request with the Authorization header
        val newRequest = request.newBuilder()
            .addHeader(AppConfig.AUTHORIZATION_HEADER, "Bearer $accessToken")
            .build()

        AppLog.d(TAG, "Added Authorization header for account: $accountId")
        return chain.proceed(newRequest)
    }
}

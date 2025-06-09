package com.greatergoods.meapp.core.network.interceptors

import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.core.config.NetworkConfig
import com.greatergoods.meapp.core.network.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
// This interceptor adds an Authorization header to every outgoing HTTP request
class AuthTokenInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {
    companion object {
        private const val TAG = "AuthTokenInterceptor"
    }
    // Intercepts each HTTP request to add the Authorization header
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Skip token for public endpoints
        if (NetworkConfig.isPublicEndpoint(request.url.encodedPath)) {
            return chain.proceed(request)
        }

        // Get the current access token
        val accessToken = runBlocking(Dispatchers.IO) {
            tokenManager.getAccessToken()
        }

        if (accessToken.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        // Build a new request with the Authorization header
        val newRequest = request.newBuilder()
            .addHeader(AppConfig.AUTHORIZATION_HEADER, "Bearer $accessToken")
            .build()

        return chain.proceed(newRequest)
    }
}

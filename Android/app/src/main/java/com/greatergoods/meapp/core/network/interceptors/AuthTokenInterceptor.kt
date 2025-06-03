package com.greatergoods.meapp.core.network.interceptors

import com.greatergoods.meapp.core.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp interceptor that adds an Authorization header to every outgoing HTTP request.
 * Retrieves the token (mocked for now) and attaches it as a Bearer token.
 */
class AuthTokenInterceptor
    @Inject
    constructor() : Interceptor {
        /**
         * Intercepts each HTTP request to add the Authorization header.
         *
         * @param chain The OkHttp interceptor chain.
         * @return The HTTP response after adding the header.
         */
        override fun intercept(chain: Interceptor.Chain): Response {
            // Build a new request by adding the Authorization header
            val request =
                chain
                    .request()
                    .newBuilder()
                    .addHeader(AppConfig.AUTHORIZATION_HEADER, "Bearer ${getAuthorizationToken()}")
                    .build()
            // Proceed with the modified request
            return chain.proceed(request)
        }

        /**
         * Retrieves the current user's authorization token (mocked for now).
         *
         * @return The authorization token as a String.
         */
        private fun getAuthorizationToken(): String =
            runBlocking(Dispatchers.IO) {
                // TODO: need to get userToken
                "mock-token-1234567890"
            }
    }

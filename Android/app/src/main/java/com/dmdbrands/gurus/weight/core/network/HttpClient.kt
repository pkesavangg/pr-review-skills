package com.dmdbrands.gurus.weight.core.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Wrapper for Retrofit HTTP client, configured with a base URL and OkHttpClient.
 * Provides a method to create API service interfaces.
 * Supports dynamic token management for different accounts.
 *
 * @property baseUrl The base URL for API requests.
 * @property retrofit The configured Retrofit instance.
 */
class HttpClient(
    val baseUrl: String,
    okHttpClient: OkHttpClient,
) {
    val retrofit: Retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl) // can be updated dynamically via interceptor
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()

    /**
     * Creates an implementation of the API endpoints defined by the service interface.
     *
     * @param T The service interface class.
     * @param serviceClass The class of the service interface.
     * @return An implementation of the service interface.
     */
    fun <T> createService(serviceClass: Class<T>): T = retrofit.create(serviceClass)

    companion object {
        /**
         * Header key for specifying account ID in API requests.
         * This allows the AuthTokenInterceptor to use the correct token for the specified account.
         */
        const val ACCOUNT_ID_HEADER = "X-Account-ID"
    }
}

package com.dmdbrands.gurus.weight.core.network.interceptors

import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.config.NetworkConfig
import com.dmdbrands.gurus.weight.core.network.HttpClient
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
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
  private val tokenManager: ITokenManager,
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
        return@runBlocking tokenManager.getAccessToken(accountId)
      } else {
        return@runBlocking tokenManager.getAccessToken()
      }
    }

    AppLog.d(TAG, "access token: $accessToken")
    // Build a new request with the Authorization header
    val newRequest = request.newBuilder()
      .addHeader(AppConfig.AUTHORIZATION_HEADER, "Bearer $accessToken")
      .build()

    return chain.proceed(newRequest)
  }
}

package com.dmdbrands.gurus.weight.core.network.interceptors

import com.dmdbrands.gurus.weight.core.config.AppConfig
import com.dmdbrands.gurus.weight.core.config.NetworkConfig
import com.dmdbrands.gurus.weight.core.network.HttpClient
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.RefreshTokenAPI
import com.dmdbrands.gurus.weight.domain.model.api.auth.RefreshTokenRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Interceptor that adds Authorization headers to HTTP requests.
 * Supports multi-account token management by checking for X-Account-ID headers.
 * Proactively refreshes tokens if they expire within 5 minutes before sending requests.
 */
class AuthTokenInterceptor @Inject constructor(
  private val tokenManager: ITokenManager,
  private val refreshTokenAPI: RefreshTokenAPI,
) : Interceptor {
  companion object {
    private const val TAG = "AuthTokenInterceptor"
    private const val TOKEN_EXPIRY_BUFFER_MS = 5 * 60 * 1000 // 5 minutes buffer in milliseconds
    private const val MAX_RETRY_ATTEMPTS = 2 // Max 2 retries (3 total attempts)
    private const val RETRY_DELAY_MS = 750L // Base delay in milliseconds
  }

  // Ensures only one proactive refresh runs at a time across concurrent requests
  private val proactiveRefreshMutex = Mutex()

  private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    // Skip token for public endpoints
    if (NetworkConfig.isPublicEndpoint(request.url.encodedPath)) {
      AppLog.d(TAG, "Skipping token for public endpoint: ${request.url.encodedPath}")
      return chain.proceed(request)
    }

    // Check for account ID header to determine which token to use
    var accountId = request.header(HttpClient.ACCOUNT_ID_HEADER)
    AppLog.d(TAG, "Processing request for account: $accountId")

    // Get the appropriate access token with proactive refresh
    val accessToken = runBlocking(Dispatchers.IO) {
      // If no account ID in header, use current account
      if (accountId == null) {
        accountId = tokenManager.getCurrentAccountID()
      }

      // Capture into a local val so smart-cast works in all nested lambdas
      val localAccountId = accountId

      // Get expiry time for the account
      val expiresAt = if (localAccountId != null) {
        tokenManager.getAccountExpiresAt(localAccountId)
      } else {
        tokenManager.getCurrentAcccountExpiresAt()
      }

      // Check if token needs proactive refresh
      if (!expiresAt.isNullOrEmpty() && isTokenExpired(expiresAt)) {
        AppLog.v(TAG, "Token expires within 5 minutes - refreshing proactively for account: $localAccountId")
        val refreshedToken = proactiveRefreshMutex.withLock {
          // Double-check inside the lock: a previous waiter may have already refreshed
          val currentExpiresAt = if (localAccountId != null) {
            tokenManager.getAccountExpiresAt(localAccountId)
          } else {
            tokenManager.getCurrentAcccountExpiresAt()
          }
          if (!currentExpiresAt.isNullOrEmpty() && isTokenExpired(currentExpiresAt)) {
            refreshTokenProactively(localAccountId)
          } else {
            AppLog.v(TAG, "Token already refreshed by another thread, skipping for account: $localAccountId")
            null
          }
        }
        if (refreshedToken != null) {
          return@runBlocking refreshedToken.accessToken
        } else {
          AppLog.w(TAG, "Proactive token refresh failed, using original token for account: $localAccountId")
        }
      }

      // Get the access token (either original or from refresh)
      if (localAccountId != null) {
        return@runBlocking tokenManager.getAccessToken(localAccountId)
      } else {
        return@runBlocking tokenManager.getAccessToken()
      }
    }

    AppLog.d(TAG, "Using access token for account: $accountId")
    // Build a new request with the Authorization header
    val newRequest = request.newBuilder()
      .addHeader(AppConfig.AUTHORIZATION_HEADER, "Bearer $accessToken")
      .build()

    return chain.proceed(newRequest)
  }

  /**
   * Check if token is expired or will expire soon (within 5 minutes buffer).
   * @param expiresAt The token expiry time string
   * @return true if token expires within 5 minutes, false otherwise
   */
  private fun isTokenExpired(expiresAt: String): Boolean {
    return try {
      val tokenExpires = dateFormat.parse(expiresAt) ?: return true
      val currentTime = Date()
      val timeUntilExpiry = tokenExpires.time - currentTime.time
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
      false // Don't refresh if we can't parse - let the server handle it
    }
  }

  /**
   * Proactively refresh token before it expires with retry logic for timeout and network errors.
   * Similar to Angular implementation: retries on status >= 500 or status === 0 (network/timeout errors).
   * @param accountId The account ID to refresh token for
   * @param retryAttempt Current retry attempt number (default 0)
   * @return The new token if refresh successful, null otherwise
   */
  private suspend fun refreshTokenProactively(accountId: String?, retryAttempt: Int = 0): Token? {
    return try {
      val refreshToken = if (accountId != null) {
        tokenManager.getRefreshToken(accountId)
      } else {
        tokenManager.getRefreshToken()
      }

      if (refreshToken.isNullOrEmpty()) {
        AppLog.e(TAG, "No refresh token available for account: $accountId")
        return null
      }

      AppLog.v(TAG, "Proactively refreshing token for account: $accountId (attempt: ${retryAttempt + 1})")

      // Make refresh token API call
      val newTokenResponse = refreshTokenAPI.refreshToken(RefreshTokenRequest(refreshToken))

      if (newTokenResponse.accessToken.isEmpty()) {
        AppLog.e(TAG, "Received empty access token from proactive refresh response")
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

      // Update tokens in TokenManager
      tokenManager.setTokens(newToken)

      AppLog.v(TAG, "Successfully proactively refreshed token for account: $accountId")
      return newToken
    } catch (e: Exception) {
      val errorStatus = getErrorStatus(e)
      val isNetworkError = errorStatus == 0 // Network error or timeout
      val isServerError = errorStatus >= 500 // Server error (500, 502, 503, 504)
      val isRetryableError = isNetworkError || isServerError

      AppLog.e(
        TAG,
        "Proactive token refresh failed for account: $accountId (attempt: ${retryAttempt + 1}, status: $errorStatus)",
        e.toString()
      )

      // Retry logic: retry on network errors (status 0) or server errors (status >= 500)
      // Similar to Angular: retry && (error.status >= 500 || error.status === 0) && retries < 2
      if (isRetryableError && retryAttempt < MAX_RETRY_ATTEMPTS) {
        val delayMs = (retryAttempt + 1) * RETRY_DELAY_MS // Exponential backoff: 750ms, 1500ms
        AppLog.v(
          TAG,
          "Retrying proactive token refresh for account: $accountId after ${delayMs}ms (attempt: ${retryAttempt + 2})"
        )
        delay(delayMs)
        return refreshTokenProactively(accountId, retryAttempt + 1)
      }

      // Max retries reached or non-retryable error - return null to use original token
      AppLog.w(
        TAG,
        "Proactive token refresh failed after ${retryAttempt + 1} attempts for account: $accountId. Using original token."
      )
      null
    }
  }

  /**
   * Extract HTTP error status from exception (similar to Angular err. Status).
   * Handles timeout errors, network errors, and HTTP status codes.
   * @param e The exception to extract status from
   * @return HTTP status code, or 0 for network/timeout errors, or -1 if unknown
   */
  private fun getErrorStatus(e: Exception): Int {
    return when (e) {
      is SocketTimeoutException -> {
        AppLog.w(TAG, "Timeout error during proactive token refresh")
        0 // Timeout is treated as network error (status 0)
      }
      is IOException -> {
        AppLog.w(TAG, "Network error during proactive token refresh")
        0 // Network error (status 0)
      }
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
}

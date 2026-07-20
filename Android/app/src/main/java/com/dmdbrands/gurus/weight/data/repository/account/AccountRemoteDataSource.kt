package com.dmdbrands.gurus.weight.data.repository.account

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IAuthAPI
import com.dmdbrands.gurus.weight.data.api.IUserAPI
import com.dmdbrands.gurus.weight.domain.model.api.auth.ChangePasswordRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.ChangePasswordResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.EmailCheckRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.LoginRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.LoginResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.LogoutRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.PasswordResetRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.RefreshTokenRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.api.dashboard.DashboardMetricsRequest
import com.dmdbrands.gurus.weight.domain.model.api.dashboard.DashboardTypeRequest
import com.dmdbrands.gurus.weight.domain.model.api.dashboard.ProgressMetricsRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.api.user.MeasurementUnitsRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.ProductsRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.Token
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import retrofit2.Response
import javax.inject.Inject

/**
 * Remote (network) data source for account operations. Owns the raw Retrofit calls that back
 * [com.dmdbrands.gurus.weight.data.repository.AccountRepository]; it performs no local persistence.
 *
 * Extracted from `AccountRepository` (MOB-1499) so the repository clears the detekt `LargeClass`
 * limit. Method bodies are relocated verbatim — no behaviour change.
 */
class AccountRemoteDataSource
@Inject
constructor(
  private val authAPI: IAuthAPI,
  private val userAPI: IUserAPI,
) {
  companion object {
    private const val TAG = "AccountRepository"
  }

  /** Logs in via API and returns the raw [LoginResponse]. */
  suspend fun login(email: String, password: String): LoginResponse =
    authAPI.login(LoginRequest(email, password))

  /** Signs up via API and returns the raw [LoginResponse]. */
  suspend fun signup(request: SignupRequest): LoginResponse =
    authAPI.createAccount(request)

  /**
   * Gets account info via API for a specific account and returns AccountInfo.
   * @param accountId The account ID to get info for
   * @return AccountInfo for the specified account
   */
  suspend fun getAccountFromAPI(accountId: String): AccountInfo {
    AppLog.d(TAG, "getAccountFromAPI for account: $accountId")
    return try {
      val result = authAPI.getAccountWithToken(accountId)
      AppLog.i(TAG, "getAccountFromAPI succeeded for account: $accountId")
      result
    } catch (e: Exception) {
      AppLog.e(TAG, "getAccountFromAPI failed for account: $accountId", e)
      throw e
    }
  }

  /** Changes the password via API and returns the [ChangePasswordResponse]. */
  suspend fun changePassword(
    oldPassword: String,
    newPassword: String,
  ): ChangePasswordResponse =
    userAPI.changePassword(ChangePasswordRequest(oldPassword, newPassword))

  suspend fun updateDashboardMetrics(dashboardKeys: List<String>) {
    AppLog.d("AccountRepository", "Updating dashboard metrics on server: $dashboardKeys")
    userAPI.updateDashboardMetrics(
      request = DashboardMetricsRequest(
        dashboardMetrics = dashboardKeys,
      ),
    )
    AppLog.d("AccountRepository", "Dashboard metrics updated successfully on server")
  }

  suspend fun updateProgressMetrics(progressKeys: List<String>) {
    try {
      AppLog.d("AccountRepository", "Updating progress metrics on server: $progressKeys")
      userAPI.updateProgressMetrics(
        request = ProgressMetricsRequest(
          progressMetrics = progressKeys,
        ),
      )
    }
    catch (e: Exception){
      AppLog.e("AccountRepository", "Failed while updating the progress metrics")
    }
    AppLog.d("AccountRepository", "Progress metrics updated successfully on server")
  }

  suspend fun updateDashboardType(dashboardType: String) {
    AppLog.d("AccountRepository", "Updating dashboard type on server: $dashboardType")
    userAPI.updateDashboardType(
      request = DashboardTypeRequest(
        dashboardType = dashboardType,
      ),
    )
    AppLog.d("AccountRepository", "Dashboard type updated successfully on server")
  }

  /**
   * Requests password reset via API and returns the [Response].
   */
  suspend fun resetPassword(email: String): Response<Unit> {
    AppLog.d(TAG, "resetPassword API call")
    return try {
      val response = authAPI.requestPasswordReset(PasswordResetRequest(email))
      AppLog.i(TAG, "resetPassword API call succeeded")
      response
    } catch (e: Exception) {
      AppLog.e(TAG, "resetPassword API call failed", e)
      throw e
    }
  }

  suspend fun emailCheck(email: String): Boolean {
    val response = authAPI.emailCheck(EmailCheckRequest(email))
    AppLog.d(TAG, "emailCheck -> isAvailable=${response.isAvailable}")
    return response.isAvailable
  }

  /** Updates the profile via API and returns the server's [AccountInfo]. */
  suspend fun updateProfile(profileData: ProfileUpdateRequest): AccountInfo =
    userAPI.updateProfile(profileData).account

  /** Updates measurement units via API and returns the server's [AccountInfo]. */
  suspend fun updateMeasurementUnits(measurementUnits: MeasurementUnits): AccountInfo =
    userAPI.updateMeasurementUnits(MeasurementUnitsRequest(measurementUnits.value)).account

  /** Updates the account's product list via API and returns the server's [AccountInfo]. */
  suspend fun updateProducts(productTypes: List<String>): AccountInfo =
    userAPI.updateProducts(ProductsRequest(productTypes)).account

  /**
   * Refreshes the token via API and returns a Token.
   * @param refreshToken The refresh token to use
   * @param accountId The account ID to associate with the refreshed token
   * @return Token object with refreshed tokens
   */
  suspend fun refreshToken(
    refreshToken: String,
    accountId: String?,
  ): Token {
    AppLog.v(TAG, "Refreshing token for account: $accountId")
    val response = authAPI.refreshToken(RefreshTokenRequest(refreshToken))
    return Token(
      accountId = accountId ?: "", // Preserve the account ID
      isActive = true,
      accessToken = response.accessToken,
      refreshToken = response.refreshToken,
      expiresAt = response.expiresAt,
    )
  }

  /** Best-effort server-side session logout for the given account. */
  suspend fun logout(fcmToken: String?, accountId: String) {
    authAPI.logoutWithToken(LogoutRequest(fcmToken ?: ""), accountId)
  }

  suspend fun deleteAccountFromServer() {
    try {
      userAPI.deleteAccount()
      AppLog.d(TAG, "Account deleted in server")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to delete account in server", e)
    }
  }
}

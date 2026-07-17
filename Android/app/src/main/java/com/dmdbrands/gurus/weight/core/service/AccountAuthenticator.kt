package com.dmdbrands.gurus.weight.core.service

import android.os.Bundle
import com.dmdbrands.gurus.weight.core.config.HttpErrorConfig
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.MaxAccountsReachedException
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings.Error.LoginError
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

/**
 * Handles account credential flows: login, signup, password reset and password change.
 *
 * Extracted from [AccountService] (MOB-1499) so the service clears the detekt `LargeClass` limit.
 * Extends [BaseService] for the shared network/toast helpers and is built from `AccountService`'s
 * own injected dependencies, so behaviour and error/analytics/nav side effects are unchanged.
 */
class AccountAuthenticator(
  private val accountRepository: IAccountRepository,
  private val analyticsService: IAnalyticsService,
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  appNavigationService: IAppNavigationService,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService) {
  companion object {
    private const val MAX_ACCOUNTS = 10
    private const val TAG = "AccountService"
  }

  private suspend fun getCurrentAccount(): Account? = accountRepository.getActiveAccount().first()

  private suspend fun getLoggedInAccounts(): List<Account> =
    accountRepository.getLoggedInAccounts().first().sortedActiveFirst()

  private suspend fun hasReachedMaxAccounts(): Boolean =
    accountRepository.getLoggedInAccounts().first().size >= MAX_ACCOUNTS

  /**
   * Logs in a user with email and password.
   * @param email User's email
   * @param password User's password
   * @return The authenticated account or null if login fails
   */
  suspend fun login(
    email: String,
    password: String,
  ): Account? =
    try {
      AppLog.d(TAG, "login() called")
      val isExistingAccount = getLoggedInAccounts().any { it.email == email }
      if (hasReachedMaxAccounts() && !isExistingAccount) {
        AppLog.w(TAG, "Max accounts reached. Cannot login new account")
        throw MaxAccountsReachedException()
      }
      val savedAccount = accountRepository.login(email, password)

      analyticsService.logEvent(IAnalyticsService.Events.LOGIN_SUCCESS)
      AppLog.d(TAG, "login() successful")
      savedAccount
    } catch (e: HttpException) {
      val msg =
        when (e.code()) {
          HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> LoginError.MessageNoConn
          HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR -> LoginError.MessageServError
          HttpErrorConfig.ResponseCode.UNAUTHORIZED -> LoginError.MessageNotAuth
          else -> LoginError.MessageGeneric
        }
      showErrorToast(title = LoginError.Header, message = msg)
      analyticsService.logEvent(
        IAnalyticsService.Events.LOGIN_FAILURE,
        Bundle().apply { putString(IAnalyticsService.Params.ERROR_TYPE, "http_${e.code()}") },
      )
      AppLog.e(TAG, "Login failed", e)
      appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Login failed"))
      null
    }

  /**
   * Adds a new account using the provided request data.
   * @param request Account creation request data
   * @return The created account or null if creation fails
   * @throws MaxAccountsReachedException if the maximum number of accounts is reached
   */
  suspend fun signup(request: SignupRequest): Account? {
    AppLog.d(TAG, "signup() called")
    if (hasReachedMaxAccounts()) {
      AppLog.w(TAG, "Max accounts reached. Cannot signup new account")
      appNavigationService.emitAuthEvent(AuthState.Error("Maximum account limit reached"))
      throw MaxAccountsReachedException()
    }
    return try {
      val savedAccount = accountRepository.signup(request)
      appNavigationService.emitAuthEvent(AuthState.AccountAdded(savedAccount))
      analyticsService.logEvent(IAnalyticsService.Events.SIGNUP_COMPLETED)
      AppLog.d(TAG, "signup() successful")
      savedAccount
    } catch (e: Exception) {
      // Surface the most specific message we can. The server returns 400 when the
      // email is already registered, so map that to the "email already in use" copy
      // rather than a generic failure. Non-HTTP failures fall back to the generic
      // message — a toast is always shown here so callers don't add their own, which
      // would mask this one. (MOB-592)
      val signupError = SignupStrings.Error
      val httpCode = (e as? HttpException)?.code()
      val errorMessage =
        when (httpCode) {
          HttpErrorConfig.ResponseCode.UNAUTHORIZED -> signupError.MessageNotAuth
          HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> signupError.MessageNoConn
          HttpErrorConfig.ResponseCode.BAD_REQUEST -> signupError.accountExist
          else -> signupError.MessageGeneric
        }
      val errorHeader =
        if (httpCode == HttpErrorConfig.ResponseCode.BAD_REQUEST) signupError.accountExistHeader else null
      showErrorToast(errorHeader, message = errorMessage)
      AppLog.e(TAG, "Account creation failed", e)
      appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Account creation failed"))
      null
    }
  }

  /**
   * Resets the password for the given email address.
   * @param email The email address to reset the password for
   */
  suspend fun resetPassword(email: String) {
    AppLog.d(TAG, "resetPassword() called")
    try {
      AppLog.d(TAG, "Checking network availability for resetPassword()")
      val email = email.trim()
      requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
      val response = this.accountRepository.resetPassword(email)
      if (response.isSuccessful) {
        AppLog.d(TAG, "Successfully reset password")
        showSuccessToast(
          ToastStrings.Success.ResetPasswordSuccess.Header,
          ToastStrings.Success.ResetPasswordSuccess.Message(email),
        )
      } else {
        AppLog.e(
          TAG,
          "Failed to reset password: ${response.code()} - ${response.message()}",
        )
        showErrorToast(
          ToastStrings.Error.ResetPasswordError.Header,
          ToastStrings.Error.ResetPasswordError.Message,
        )
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to reset password", e)
      if (e is HttpException) {
        val msg =
          when (e.code()) {
            HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> ToastStrings.Error.NetworkError.Message
            HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR -> ToastStrings.Error.UpdateProfileError.MessageServError
            else -> ToastStrings.Error.ResetPasswordError.Message
          }
        showErrorToast(ToastStrings.Error.ResetPasswordError.Header, msg)
      }
    }
  }

  /**
   * Changes the password for the current account.
   * @param currentPassword The current password
   * @param newPassword The new password to set
   * @return true if the password was changed successfully, false otherwise
   */
  suspend fun changePassword(
    currentPassword: String,
    newPassword: String,
  ): Boolean {
    AppLog.d(TAG, "changePassword() called")
    return try {
      getCurrentAccount() ?: run {
        AppLog.w(TAG, "No active account found for changePassword(). Returning false.")
        return false
      }
      val accountId = accountRepository.getActiveAccount().first()?.id ?: return false
      accountRepository.updatePassword(accountId, currentPassword, newPassword)
      AppLog.d(TAG, "Password changed successfully")
      dialogQueueService.showToast(Toast.Simple(ToastStrings.Success.ChangePasswordSuccess.Message))
      true
    } catch (e: Exception) {
      AppLog.e(TAG, "Password change failed", e)
      if (e is HttpException) {
        val msg =
          when (e.code()) {
            HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> ToastStrings.Error.NetworkError.Message
            HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR -> ToastStrings.Error.UpdateProfileError.MessageServError
            else -> ToastStrings.Error.UpdateProfileError.updatePasswordFailedMessage
          }
        showErrorToast(ToastStrings.Error.UpdateProfileError.updatePasswordHeader, msg)
      }
      false
    }
  }
}

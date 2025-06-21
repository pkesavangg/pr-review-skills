package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.config.HttpErrorConfig
import com.greatergoods.meapp.core.network.ITokenManager
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.enum.AuthAction
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.domain.model.Account
import com.greatergoods.meapp.domain.model.api.user.CreateAccountRequest
import com.greatergoods.meapp.domain.model.api.user.Token
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.services.AuthState
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.strings.ToastStrings
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

/**
 * Service for managing account authentication and session state.
 * Handles login, logout, account switching, and token management.
 */
@Singleton
class AccountAuthService
@Inject
constructor(
    private val accountRepository: IAccountRepository,
    private val connectivityObserver: IConnectivityObserver,
    private val tokenManager: ITokenManager,
    private val dialogQueueService: IDialogQueueService
) : IAccountAuthService {
    companion object {
        private const val MAX_ACCOUNTS = 10
        private const val TAG = "AccountAuthService"
    }

    // Event flow for authentication state changes
    private val _authStateFlow = MutableSharedFlow<AuthState>()
    override val authStateFlow: SharedFlow<AuthState> = _authStateFlow

    // Current active account flow
    override val activeAccountFlow: Flow<Account?> =
        accountRepository.getLoggedInAccountsFromDB().map { accounts ->
            accounts.find { it.isActiveAccount }
        }

    // All logged in accounts flow - sorted by lastActiveTime
    override val loggedInAccountsFlow: Flow<List<Account>> = accountRepository.getLoggedInAccountsFromDB()

    // Account status flows
    private val _isSignUpFlow = MutableSharedFlow<Boolean>()
    override val isSignUpFlow: SharedFlow<Boolean> = _isSignUpFlow

    private val _isLoginFlow = MutableSharedFlow<Boolean>()
    override val isLoginFlow: SharedFlow<Boolean> = _isLoginFlow

    private val _isSwitchAccountFlow = MutableSharedFlow<Boolean>()
    override val isSwitchAccountFlow: SharedFlow<Boolean> = _isSwitchAccountFlow

    /**
     * Logs in a user with email and password.
     * @param email User's email
     * @param password User's password
     * @return The authenticated account or null if login fails
     */
    override suspend fun login(
        email: String,
        password: String,
    ): Account? {
        return try {
            val loginResponse = accountRepository.loginInAPI(email, password)
            val info = loginResponse.account
            val account =
                Account(
                    id = info.id,
                    firstName = info.firstName,
                    lastName = info.lastName,
                    dob = info.dob,
                    email = info.email,
                    expiresAt = loginResponse.expiresAt,
                    fcmToken = null,
                    gender = info.gender,
                    isActiveAccount = true,
                    isLoggedIn = true,
                    isExpired = false,
                    isSynced = true,
                    lastActiveTime = Date().time.toString(),
                    zipcode = info.zipcode,
                )
            val savedAccount = accountRepository.addAccountInDB(account)
            tokenManager.setTokens(
                Token(
                    accountId = savedAccount.id,
                    isActive = true,
                    accessToken = loginResponse.accessToken,
                    refreshToken = loginResponse.refreshToken,
                    expiresAt = loginResponse.expiresAt,
                ),
            )
            _authStateFlow.emit(AuthState.LoggedIn(savedAccount))
            _isLoginFlow.emit(true)
            savedAccount
        } catch (e: HttpException) {
            showErrorToast(AuthAction.LOGIN, e)
            AppLog.e(TAG, "Login failed", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Login failed"))
            null
        }
    }

    /**
     * Logs out the current user.
     * @param accountId ID of the account to log out
     * @return true if logout was successful
     */
    override suspend fun logout(accountId: String): Boolean =
        try {
            // Try to logout on API if network is available
            if (isNetworkAvailable()) {
                try {
                    accountRepository.logoutInAPI(null)
                } catch (e: Exception) {
                    AppLog.e(TAG, "API logout failed", e.toString())
                    // Continue with local logout even if API fails
                }
            }

            // Always perform local logout
            accountRepository.logoutInDb(accountId)
            tokenManager.clearTokens()
            AppLog.d(TAG, "Logout successful")
            _authStateFlow.emit(AuthState.LoggedOut())
            _isLoginFlow.emit(false)
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Logout failed", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Logout failed"))
            false
        }

    /**
     * Logs out all users.
     * @return true if all accounts were logged out successfully
     */
    override suspend fun logoutAll(): Boolean =
        try {
            val loggedInAccounts = loggedInAccountsFlow.first()
            val activeAccount = activeAccountFlow.first()

            // Sort accounts to handle active account last
            val sortedAccounts = loggedInAccounts.sortedWith(compareByDescending { it.isActiveAccount })

            for (account in sortedAccounts) {
                account.id != activeAccount?.id
                try {
                    if (isNetworkAvailable()) {
                        try {
                            accountRepository.logoutInAPI(null)
                        } catch (e: Exception) {
                            AppLog.e(TAG, "API logout failed for account ${account.id}", e.toString())
                            // Continue with local logout even if API fails
                        }
                    }
                    accountRepository.removeAccountInDB(account.id)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to logout account ${account.id}", e.toString())
                }
            }
            // Clear all accounts from database
            accountRepository.removeAllAccountsInDB()
            // Clear tokens
            tokenManager.clearTokens()
            AppLog.d(TAG, "All accounts logged out successfully")
            _authStateFlow.emit(AuthState.LoggedOut())
            _isLoginFlow.emit(false)
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Logout all failed", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Logout all failed"))
            false
        }

    /**
     * Adds a new account.
     * @param request Account creation request data
     * @return The created account or null if creation fails
     */
    override suspend fun addAccount(request: Map<String, Any>): Account? {

        val currentAccounts = loggedInAccountsFlow.first()
        if (currentAccounts.size >= MAX_ACCOUNTS) {
            AppLog.e(TAG, "Maximum account limit reached")
            _authStateFlow.emit(AuthState.Error("Maximum account limit reached"))
            return null
        }
        return try {
            val createRequest =
                CreateAccountRequest(
                    email = request["email"] as String,
                    firstName = request["firstName"] as String,
                    lastName = request["lastName"] as String,
                    gender = request["gender"] as String,
                    zipcode = request["zipcode"] as? String ?: "00000",
                    password = request["password"] as String,
                    dob = request["dob"] as String,
                    height = request["height"] as? Int ?: 1700,
                    weightUnit =
                        request["weightUnit"] as? WeightUnit
                            ?: WeightUnit.LB,

                )
            val response = accountRepository.signupInAPI(createRequest)
            val info = response.account
            val account =
                Account(
                    id = info.id,
                    firstName = info.firstName,
                    lastName = info.lastName,
                    dob = info.dob,
                    email = info.email,
                    expiresAt = response.expiresAt,
                    fcmToken = null,
                    gender = info.gender,
                    isActiveAccount = true,
                    isLoggedIn = true,
                    isExpired = false,
                    isSynced = false,
                    lastActiveTime = Date().time.toString(),
                    zipcode = info.zipcode,
                )
            val savedAccount = accountRepository.addAccountInDB(account)
            tokenManager.setTokens(
                Token(
                    accountId = savedAccount.id,
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    expiresAt = response.expiresAt,
                ),
            )
            _authStateFlow.emit(AuthState.AccountAdded(savedAccount))
            _isSignUpFlow.emit(true)
            savedAccount
        } catch (e: Exception) {
            handleSignupError(e as HttpException)
            AppLog.e(TAG, "Account creation failed", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Account creation failed"))
            null
        }
    }

    /**
     * Removes an account.
     * @param accountId ID of the account to remove
     * @return true if account was removed successfully
     */
    override suspend fun removeAccount(accountId: String): Boolean =
        try {
            val account = activeAccountFlow.first()
            if (account?.id == accountId) {
                // If removing active account, switch to another account first
                val otherAccounts = loggedInAccountsFlow.first().filter { it.id != accountId }
                if (otherAccounts.isNotEmpty()) {
                    switchAccount(otherAccounts.first())
                }
            }
            accountRepository.removeAccountInDB(accountId)
            AppLog.d(TAG, "Account removed successfully: $accountId")
            _authStateFlow.emit(AuthState.AccountRemoved(accountId))
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Account removal failed", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Account removal failed"))
            false
        }

    /**
     * Switches to a different account.
     * @param account Account to switch to
     * @return true if switch was successful
     */
    override suspend fun switchAccount(account: Account): Boolean {
        // Example implementation: deactivate others, activate this one
        accountRepository.deactivateOtherAccountsInDB(account.id)
        // Optionally update last active time
        accountRepository.updateLastActiveTimeInDB(account.id)
        return true
    }

    /**
     * Gets the current active account.
     * @return The active account or null if none
     */
    override suspend fun getCurrentAccount(): Account? = activeAccountFlow.first()

    /**
     * Checks if the current session is valid.
     * @return true if session is valid, false otherwise
     */
    override suspend fun isSessionValid(): Boolean {
        val account = getCurrentAccount() ?: return false
        if (!account.isLoggedIn) return false

        // Check token expiration
        account.expiresAt?.let { expiresAt ->
            val expirationDate = Date(expiresAt.toLong())
            if (expirationDate.before(Date())) {
                // Token expired, try to refresh
                return try {
                    refreshSession()
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
        return true
    }

    /**
     * Refreshes the current session.
     * @return true if refresh was successful
     */
    override suspend fun refreshSession(): Boolean {
        if (!isNetworkAvailable()) {
            AppLog.e(TAG, "No network connection available")
            _authStateFlow.emit(AuthState.Error("No network connection available"))
            return false
        }

        return try {
            getCurrentAccount() ?: return false
            // If you have a refreshTokenInAPI method, use it here to refresh tokens
            // val refreshedToken = accountRepository.refreshTokenInAPI(...)
            // Optionally update tokens in TokenManager
            // tokenManager.setTokens(refreshedToken)
            AppLog.d(TAG, "Session refreshed successfully")
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Session refresh failed", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Session refresh failed"))
            false
        }
    }

    /**
     * Updates the account's tokens.
     * @param tokens New token data
     * @return true if update was successful
     */
    override suspend fun updateTokens(tokens: Map<String, String>): Boolean =
        try {
            accountRepository.updateTokensInDB(tokens)
            AppLog.d(TAG, "Tokens updated successfully")
            _authStateFlow.emit(AuthState.TokensUpdated)
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Token update failed", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Token update failed"))
            false
        }

    /**
     * Checks for a logged-in user and restores their session if found.
     * @return true if a valid session was found and restored
     */
    override suspend fun checkForLoggedInUser(): Boolean {
        return try {
            val storedAccount = accountRepository.getStoredActiveAccountFromDB()
            if (storedAccount != null && storedAccount.id.isNotEmpty() && !storedAccount.isExpired) {
                // Restore the session
                if (storedAccount.expiresAt != null) {
                    val expirationDate = Date(storedAccount.expiresAt.toLong())
                    if (expirationDate.after(Date())) {
                        AppLog.d(TAG, "Restored valid session for account: ${storedAccount.email}")
                        _authStateFlow.emit(AuthState.LoggedIn(storedAccount))
                        _isLoginFlow.emit(true)
                        return true
                    } else {
                        return try {
                            refreshSession()
                        } catch (e: Exception) {
                            AppLog.e(TAG, "Session expired for account: " + storedAccount.email, e.toString())
                            _authStateFlow.emit(AuthState.LoggedOut("Session expired"))
                            _isLoginFlow.emit(false)
                            false
                        }
                    }
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to check logged in user", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Failed to check logged in user"))
            false
        }
    }

    override suspend fun resetPassword(email: String) {

        try {
            val response = this.accountRepository.resetPasswordInAPI(email)
            if (response.isSuccessful) {
                Log.d(TAG, "Successfully reset password: $response")
                AppLog.d(TAG, "Successfully reset password")
                showSuccessToast(AuthAction.RESET_PASSWORD, email)
            } else {
                AppLog.e(TAG, "Failed to reset password: ${response.code()} - ${response.message()}")
                showErrorToast(AuthAction.RESET_PASSWORD, HttpException(response))
            }
        } catch (e: HttpException) {
            AppLog.e(TAG, "Failed to reset password", e.toString())
            showErrorToast(AuthAction.RESET_PASSWORD, e)
        }
    }

    /**
     * Checks if network is available using the connectivity observer
     */
    private fun isNetworkAvailable(): Boolean = !connectivityObserver.getCurrentNetworkState().unAvailable
    fun showSuccessToast(action: AuthAction, data: String? = null) {
        val (title, message) = when (action) {
            AuthAction.RESET_PASSWORD -> ToastStrings.Success.ResetPasswordSuccess.Header to
                ToastStrings.Success.ResetPasswordSuccess.Message(data ?: "")

            else -> "" to ""
        }

        val successToast = Toast(
            title = title,
            message = message,
            action = null,
        )
        dialogQueueService.showToast(successToast)
    }

    fun showErrorToast(action: AuthAction, error: HttpException?) {
        val (title, message) = when (action) {
            AuthAction.LOGIN -> {
                val header = ToastStrings.Error.LoginError.Header
                val message = when (error?.code()) {
                    HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION ->
                        ToastStrings.Error.LoginError.MessageNoConn

                    HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR ->
                        ToastStrings.Error.LoginError.MessageServError

                    HttpErrorConfig.ResponseCode.UNAUTHORIZED ->
                        ToastStrings.Error.LoginError.MessageNotAuth

                    else ->
                        ToastStrings.Error.LoginError.MessageGeneric
                }
                header to message
            }

            AuthAction.RESET_PASSWORD -> ToastStrings.Error.ResetPasswordError.Header to
                ToastStrings.Error.ResetPasswordError.Message

            else -> "" to ""
        }

        val errorToast = Toast(
            title = title,
            message = message,
            action = null,
        )
        dialogQueueService.showToast(errorToast)
    }

    /**
     * Handles signup errors by displaying appropriate error messages based on the HTTP status code.
     * @param error The HttpException containing the error details
     */
    private fun handleSignupError(error: HttpException) {
        val signupError = SignupStrings.Error
        val errorMessage = when (error.code()) {
            HttpErrorConfig.ResponseCode.UNAUTHORIZED -> signupError.MessageNotAuth
            HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> signupError.MessageNoConn
            HttpErrorConfig.ResponseCode.BAD_REQUEST -> signupError.accountExist
            else -> signupError.MessageGeneric
        }
        val errorHeader = when (error.code()) {
            HttpErrorConfig.ResponseCode.BAD_REQUEST -> signupError.accountExistHeader
            else -> signupError.Header
        }
        val errorToast = Toast(
            message = errorMessage,
            title = errorHeader,
            action = null,
        )
        dialogQueueService.showToast(
            errorToast,
        )
    }
}

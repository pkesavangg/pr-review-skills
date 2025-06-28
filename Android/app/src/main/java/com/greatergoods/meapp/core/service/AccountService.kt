package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.config.HttpErrorConfig
import com.greatergoods.meapp.core.network.ITokenManager
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.domain.enum.AuthAction
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.domain.model.PartialAccount
import com.greatergoods.meapp.domain.model.api.user.AccountInfo
import com.greatergoods.meapp.domain.model.api.user.CreateAccountRequest
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.model.api.user.Token
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.services.AuthState
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.MaxAccountsReachedException
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

/**
 * Service for managing account authentication and session state.
 * Handles login, logout, account switching, and token management.
 */
@Singleton
class AccountService
    @Inject
    constructor(
        private val accountRepository: IAccountRepository,
        private val connectivityObserver: IConnectivityObserver,
        private val tokenManager: ITokenManager,
        private val dialogQueueService: IDialogQueueService,
        private val userDataStore: UserDataStore,
        private val appNavigationService: IAppNavigationService,
    ) : IAccountService {
        companion object {
            private const val MAX_ACCOUNTS = 10
            private const val TAG = "AccountService"
        }

    /**
     * Safely parses a weight unit string to WeightUnit enum.
     * Handles both enum names (KG, LB) and values (kg, lb) with fallback to LB.
     */
    private fun parseWeightUnit(weightUnitString: String): WeightUnit {
        if (weightUnitString.isBlank()) return WeightUnit.LB

        return when (weightUnitString.lowercase()) {
            "kg" -> WeightUnit.KG
            "lb", "lbs" -> WeightUnit.LB
            else -> {
                AppLog.w(TAG, "Unknown weight unit '$weightUnitString', defaulting to LB")
                WeightUnit.LB
            }
        }
    }

        /**
         * Checks if network is available using the connectivity observer
         */
        private fun isNetworkAvailable(): Boolean = !connectivityObserver.getCurrentNetworkState().unAvailable

        // Event flow for authentication state changes
        override val authEvent = appNavigationService.authEvent

        // Current active account flow
        override val activeAccountFlow: Flow<Account?> =
            accountRepository.getStoredActiveAccountFromDB()

        // All logged in accounts flow - active account first, then by lastActiveTime
        override val loggedInAccountsFlow: Flow<List<Account>> =
            accountRepository.getLoggedInAccountsFromDB().map { accounts ->
                val active = accounts.find { it.isActiveAccount }
                val others =
                    accounts
                        .filter { !it.isActiveAccount }
                        .sortedByDescending { it.lastActiveTime?.toLongOrNull() ?: 0L }
                listOfNotNull(active) + others
            }

        override val hasReachedMaxAccounts: Flow<Boolean> =
            loggedInAccountsFlow.map { it.size >= MAX_ACCOUNTS }

        // Account status flows
        private val _isSignUpFlow = MutableSharedFlow<Boolean>()
        override val isSignUpFlow: SharedFlow<Boolean> = _isSignUpFlow

        // Account status flows
        private val _activeAccount = MutableSharedFlow<Account?>()
        val activeAccount: SharedFlow<Account?> = _activeAccount

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
        ): Account? =
            try {
                val isExistingAccount = getLoggedInAccounts().any { it.email == email }
                if (hasReachedMaxAccounts.first() && !isExistingAccount) {
                    throw MaxAccountsReachedException()
                }
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
                        weightUnit = parseWeightUnit(info.weightUnit),
                        isWeightlessOn = info.isWeightlessOn,
                        height = info.height,
                        activityLevel = info.activityLevel,
                        weightlessTimestamp = info.weightlessTimestamp,
                        weightlessWeight = info.weightlessWeight,
                        isStreakOn = info.isStreakOn,
                        dashboardType = info.dashboardType,
                        dashboardMetrics = info.dashboardMetrics,
                    )
                // Deactivate all other accounts before saving this one as active
                accountRepository.deactivateOtherAccountsInDB(account.id)
                val savedAccount = accountRepository.addAccountInDB(account)
                userDataStore.setActiveAccount(account.id)
                tokenManager.setTokens(
                    Token(
                        accountId = savedAccount.id,
                        isActive = true,
                        accessToken = loginResponse.accessToken,
                        refreshToken = loginResponse.refreshToken,
                        expiresAt = loginResponse.expiresAt,
                    ),
                )
                appNavigationService.emitAuthEvent(AuthState.LoggedIn(savedAccount))
                _isLoginFlow.emit(true)
                savedAccount
            } catch (e: HttpException) {
                showErrorToast(AuthAction.LOGIN, e)
                AppLog.e(TAG, "Login failed", e.toString())
                appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Login failed"))
                null
            }

        /**
         * Logs out the current user.
         * @param accountId ID of the account to log out
         * @return true if logout was successful
         */
        override suspend fun logout(
            accountId: String,
            fcmToken: String?,
        ): Boolean =
            try {
                val isActiveAccount = getCurrentAccount()?.id == accountId
                // Try to logout on API if network is available
                if (isNetworkAvailable()) {
                    try {
                        accountRepository.logoutInAPI(fcmToken ?: "", accountId)
                    } catch (e: Exception) {
                        AppLog.e(TAG, "API logout failed", e.toString())
                        // Continue with local logout even if API fails
                    }
                }
                if (isActiveAccount) {
                    accountRepository.deactivateAllAccountsInDB()
                }
                // Always perform local logout
                accountRepository.logoutInDb(accountId)
                userDataStore.clearAccountTokens(accountId)
                tokenManager.clearTokens()
                AppLog.d(TAG, "Logout successful")
                appNavigationService.emitAuthEvent(AuthState.LoggedOut())
                _isLoginFlow.emit(false)
                true
            } catch (e: Exception) {
                AppLog.e(TAG, "Logout failed", e.toString())
                appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Logout failed"))
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
                val sortedAccounts =
                    loggedInAccounts.sortedWith(compareByDescending { it.isActiveAccount })

                for (account in sortedAccounts) {
                    account.id != activeAccount?.id
                    try {
                        if (isNetworkAvailable()) {
                            try {
                                accountRepository.logoutInAPI(account.fcmToken ?: "", account.id)
                            } catch (e: Exception) {
                                AppLog.e(
                                    TAG,
                                    "API logout failed for account ${account.id}",
                                    e.toString(),
                                )
                                // Continue with local logout even if API fails
                            }
                        }
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Failed to logout account ${account.id}", e.toString())
                    }
                }
                // Clear all accounts from database
                accountRepository.logoutAllAccountsInDb()
                // Clear tokens
                tokenManager.clearTokens()
                AppLog.d(TAG, "All accounts logged out successfully")
                appNavigationService.emitAuthEvent(AuthState.LoggedOut())
                _isLoginFlow.emit(false)
                true
            } catch (e: Exception) {
                AppLog.e(TAG, "Logout all failed", e.toString())
                appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Logout all failed"))
                false
            }

        /**
         * Adds a new account.
         * @param request Account creation request data
         * @return The created account or null if creation fails
         */
        override suspend fun addAccount(request: Map<String, Any>): Account? {
            if (hasReachedMaxAccounts.first()) {
                appNavigationService.emitAuthEvent(AuthState.Error("Maximum account limit reached"))
                throw MaxAccountsReachedException()
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
                        weightUnit = parseWeightUnit(info.weightUnit),
                        isWeightlessOn = info.isWeightlessOn,
                        height = info.height,
                        activityLevel = info.activityLevel,
                        weightlessTimestamp = info.weightlessTimestamp,
                        weightlessWeight = info.weightlessWeight,
                        isStreakOn = info.isStreakOn,
                        dashboardType = info.dashboardType,
                        dashboardMetrics = info.dashboardMetrics,
                    )
                accountRepository.deactivateOtherAccountsInDB(account.id)
                val savedAccount = accountRepository.addAccountInDB(account)
                userDataStore.setActiveAccount(account.id)
                tokenManager.setTokens(
                    Token(
                        accountId = savedAccount.id,
                        isActive = true,
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken,
                        expiresAt = response.expiresAt,
                    ),
                )
                appNavigationService.emitAuthEvent(AuthState.AccountAdded(savedAccount))
                _isSignUpFlow.emit(true)
                savedAccount
            } catch (e: Exception) {
                handleSignupError(e as HttpException)
                AppLog.e(TAG, "Account creation failed", e.toString())
                appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Account creation failed"))
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
                appNavigationService.emitAuthEvent(AuthState.AccountRemoved(accountId))
                true
            } catch (e: Exception) {
                AppLog.e(TAG, "Account removal failed", e.toString())
                appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Account removal failed"))
                false
            }

        /**
         * Switches to a different account.
         * @param account Account to switch to
         * @return true if switch was successful
         */
        override suspend fun switchAccount(
            account: Account,
            showToast: Boolean,
        ): Boolean {
            try {
                // Activate the target account
                accountRepository.activateAccountInDB(account.id)
                // Deactivate all other accounts
                accountRepository.deactivateOtherAccountsInDB(account.id)
                userDataStore.setActiveAccount(account.id)
                // Update last active time
                accountRepository.updateLastActiveTimeInDB(account.id)
                // Update tokens in TokenManager for the switched account
                val currentTokens = userDataStore.getData().accounts[account.id]
                if (currentTokens != null) {
                    tokenManager.setTokens(
                        Token(
                            accountId = account.id,
                            isActive = true,
                            accessToken = currentTokens.accessToken,
                            refreshToken = currentTokens.refreshToken,
                            expiresAt = currentTokens.expiresAt,
                        ),
                    )
                }
                AppLog.d(TAG, "Successfully switched to account: ${account.email}")
                appNavigationService.emitAuthEvent(AuthState.AccountSwitched(account, showToast))
                return true
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to switch account", e.toString())
                appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Failed to switch account"))
                return false
            }
        }

        /**
         * Gets the current active account.
         * @return The active account or null if none
         */
        override suspend fun getCurrentAccount(): Account? = activeAccountFlow.first()

        override suspend fun getLoggedInAccounts(): List<Account> = loggedInAccountsFlow.first()

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
                appNavigationService.emitAuthEvent(AuthState.Error("No network connection available"))
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
                appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Session refresh failed"))
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
                appNavigationService.emitAuthEvent(AuthState.TokensUpdated)
                true
            } catch (e: Exception) {
                AppLog.e(TAG, "Token update failed", e.toString())
                appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Token update failed"))
                false
            }

        /**
         * Checks for a logged-in user and restores their session if found.
         * @return true if a valid session was found and restored
         */
        override suspend fun checkForLoggedInUser(): Boolean {
            return try {
                val storedAccount = getCurrentAccount()
                if (storedAccount != null && storedAccount.id.isNotEmpty() && !storedAccount.isExpired) {
                    // Restore the session
                    if (storedAccount.expiresAt != null) {
                        val expirationDate = Date(storedAccount.expiresAt.toLong())
                        if (expirationDate.after(Date())) {
                            AppLog.d(TAG, "Restored valid session for account: ${storedAccount.email}")
                            appNavigationService.emitAuthEvent(AuthState.LoggedIn(storedAccount))
                            _isLoginFlow.emit(true)
                            return true
                        } else {
                            return try {
                                refreshSession()
                            } catch (e: Exception) {
                                AppLog.e(
                                    TAG,
                                    "Session expired for account: " + storedAccount.email,
                                    e.toString(),
                                )
                                appNavigationService.emitAuthEvent(AuthState.LoggedOut("Session expired"))
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
                appNavigationService.emitAuthEvent(
                    AuthState.Error(
                        e.message ?: "Failed to check logged in user",
                    ),
                )
                false
            }
        }

        override suspend fun resetPassword(email: String) {
            try {
                val response = this.accountRepository.resetPasswordInAPI(email)
                if (response.isSuccessful) {
                    AppLog.d(TAG, "Successfully reset password")
                    showSuccessToast(AuthAction.RESET_PASSWORD, email)
                } else {
                    AppLog.e(
                        TAG,
                        "Failed to reset password: ${response.code()} - ${response.message()}",
                    )
                    showErrorToast(AuthAction.RESET_PASSWORD, HttpException(response))
                }
            } catch (e: HttpException) {
                AppLog.e(TAG, "Failed to reset password", e.toString())
                showErrorToast(AuthAction.RESET_PASSWORD, e)
            }
        }

        override suspend fun updateProfileInDB(
            accountId: String,
            partialAccount: PartialAccount,
        ): Account = accountRepository.updateAccountInDB(accountId, partialAccount)

        /**
         * Updates the user's profile information with offline support.
         * If online, calls API and marks as synced. If offline, stores locally with isSynced = false.
         * Follows the same pattern as Angular account.service.ts updateProfile method.
         * @param profileUpdateRequest The profile data to update
         * @return The updated account or null if update fails
         */
        override suspend fun updateProfile(profileUpdateRequest: ProfileUpdateRequest): Account? {
            return try {
                // Get current account from flow (reactive approach like Angular observables)
                val currentAccount = activeAccountFlow.first()
                if (currentAccount == null) {
                    return null
                }
                // Call API to update profile
                val response = accountRepository.updateProfileInAPI(profileUpdateRequest)
                val updatedAccountInfo: AccountInfo = response.account
                val savedAccount =
                    updateProfileInDB(
                        updatedAccountInfo.id,
                        PartialAccount(
                            firstName = updatedAccountInfo.firstName,
                            lastName = updatedAccountInfo.lastName,
                            dob = updatedAccountInfo.dob,
                            gender = updatedAccountInfo.gender,
                            zipcode = updatedAccountInfo.zipcode,
                            email = updatedAccountInfo.email,
                            isActiveAccount = true,
                            isSynced = true, // Mark as synced since API call was successful
                        ),
                    )
                AppLog.i(TAG, "Profile updated successfully via API for account: ${savedAccount.id}")
                savedAccount.let { appNavigationService.emitAuthEvent(AuthState.ProfileUpdated(it)) }
                showSuccessToast(AuthAction.UPDATE_PROFILE)
                savedAccount
            } catch (e: HttpException) {
                showErrorToast(AuthAction.UPDATE_PROFILE, e)
                AppLog.e(TAG, "Profile update failed", e.toString())
                throw e
            }
        }

        override suspend fun changePassword(
            currentPassword: String,
            newPassword: String,
        ): Boolean {
            return try {
                getCurrentAccount() ?: return false
                val response = accountRepository.updatePasswordInAPI(currentPassword, newPassword)
                tokenManager.setTokens(
                    Token(
                        accountId = activeAccountFlow.first()?.id ?: "",
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken,
                        expiresAt = response.expiresAt,
                    ),
                )
                AppLog.d(TAG, "Password changed successfully")
                showSuccessToast(AuthAction.CHANGE_PASSWORD)
                // Password change typically invalidates existing tokens, but we'll keep using current session
                // unless the API specifically returns new tokens
                true
            } catch (e: Exception) {
                AppLog.e(TAG, "Password change failed", e.toString())
                showErrorToast(AuthAction.CHANGE_PASSWORD, e as? HttpException)
                false
            }
        }

        fun showSuccessToast(
            action: AuthAction,
            data: String? = null,
        ) {
            val (title, message) =
                when (action) {
                    AuthAction.RESET_PASSWORD ->
                        ToastStrings.Success.ResetPasswordSuccess.Header to
                            ToastStrings.Success.ResetPasswordSuccess.Message(data ?: "")

                    AuthAction.UPDATE_PROFILE ->
                        ToastStrings.Success.UpdateProfileSuccess.Header to
                            ToastStrings.Success.UpdateProfileSuccess.Message

                    AuthAction.CHANGE_PASSWORD ->
                        ToastStrings.Success.ChangePasswordSuccess.Header to
                            ToastStrings.Success.ChangePasswordSuccess.Message

                    else -> "" to ""
                }

            val successToast =
                Toast(
                    title = title,
                    message = message,
                    action = null,
                )
            dialogQueueService.showToast(successToast)
        }

        fun showErrorToast(
            action: AuthAction,
            error: HttpException?,
        ) {
            val (title, message) =
                when (action) {
                    AuthAction.LOGIN -> {
                        val header = ToastStrings.Error.LoginError.Header
                        val message =
                            when (error?.code()) {
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

                    AuthAction.RESET_PASSWORD ->
                        ToastStrings.Error.ResetPasswordError.Header to
                            ToastStrings.Error.ResetPasswordError.Message

                    AuthAction.UPDATE_PROFILE -> {
                        val header = ToastStrings.Error.UpdateProfileError.Header
                        val message =
                            when (error?.code()) {
                                HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION ->
                                    ToastStrings.Error.UpdateProfileError.MessageNoConn

                                HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR ->
                                    ToastStrings.Error.UpdateProfileError.MessageServError

                                HttpErrorConfig.ResponseCode.UNAUTHORIZED ->
                                    ToastStrings.Error.UpdateProfileError.MessageNotAuth

                                else ->
                                    ToastStrings.Error.UpdateProfileError.MessageGeneric
                            }
                        header to message
                    }

                    AuthAction.CHANGE_PASSWORD -> {
                        val header = ToastStrings.Error.ChangePasswordError.Header
                        val message =
                            when (error?.code()) {
                                HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION ->
                                    ToastStrings.Error.UpdateProfileError.MessageNoConn

                                HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR ->
                                    ToastStrings.Error.UpdateProfileError.MessageServError

                                HttpErrorConfig.ResponseCode.UNAUTHORIZED ->
                                    ToastStrings.Error.UpdateProfileError.MessageNotAuth

                                else ->
                                    ToastStrings.Error.UpdateProfileError.MessageGeneric
                            }
                        header to message
                    }

                    else -> "" to ""
                }

            val errorToast =
                Toast(
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
            val errorMessage =
                when (error.code()) {
                    HttpErrorConfig.ResponseCode.UNAUTHORIZED -> signupError.MessageNotAuth
                    HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> signupError.MessageNoConn
                    HttpErrorConfig.ResponseCode.BAD_REQUEST -> signupError.accountExist
                    else -> signupError.MessageGeneric
                }
            val errorHeader =
                when (error.code()) {
                    HttpErrorConfig.ResponseCode.BAD_REQUEST -> signupError.accountExistHeader
                    else -> signupError.Header
                }
            val errorToast =
                Toast(
                    message = errorMessage,
                    title = errorHeader,
                    action = null,
                )
            dialogQueueService.showToast(
                errorToast,
            )
        }

        /**
         * Checks login status for the active account by calling getAccount API.
         * Updates the account data in DB with the response and refreshes tokens if needed.
         * @return true if account is still valid, false if expired
         */
        override suspend fun checkLoginStatusForActiveAccount(): Boolean {
            return try {
                if (!isNetworkAvailable()) {
                    AppLog.w(TAG, "No network available for active account status check")
                    return false // Assume valid if no network
                }

                val activeAccount = getCurrentAccount()
                if (activeAccount == null) {
                    AppLog.d(TAG, "No active account found")
                    return false
                }

                // Log the token before making the API call
                userDataStore.getData().accounts[activeAccount.id]
                // Update tokens in TokenManager for active account BEFORE making the API call

                AppLog.d(TAG, "Checking login status for active account: ${activeAccount.id}")
                val accountInfo = accountRepository.getAccountInAPI(activeAccount.id)

                // Update account data with API response
                accountRepository.updateAccountFromAPI(activeAccount.id, accountInfo)
                AppLog.d(TAG, "Active account login status check successful")
                true
            } catch (e: Exception) {
                AppLog.e(TAG, "Active account login status check failed", e.toString())
                false
            }
        }

        /**
         * Checks login status for all logged-in accounts (non-active) by calling getAccount API.
         * Updates account data in DB with responses and refreshes tokens if needed.
         * For expired accounts, marks them as expired and clears tokens.
         * @return true if all accounts are valid, false if any account is expired
         */
        override suspend fun checkLoginStatusForLoggedInAccounts(): Boolean {
            return try {
                if (!isNetworkAvailable()) {
                    AppLog.w(TAG, "No network available for logged-in accounts status check")
                    return true // Assume valid if no network
                }

                val loggedInAccounts = getLoggedInAccounts().filter { !it.isActiveAccount }
                if (loggedInAccounts.isEmpty()) {
                    AppLog.d(TAG, "No non-active logged-in accounts found")
                    return true
                }

                var allAccountsValid = true

                for (account in loggedInAccounts) {
                    try {
                        AppLog.d(TAG, "Checking login status for account: ${account.id}")

                        // Update tokens in TokenManager for this account BEFORE making the API call
                        val currentTokens = userDataStore.getData().accounts[account.id]
                        if (currentTokens != null) {
                            tokenManager.setTokens(
                                Token(
                                    accountId = account.id,
                                    isActive = false,
                                    accessToken = currentTokens.accessToken,
                                    refreshToken = currentTokens.refreshToken,
                                    expiresAt = currentTokens.expiresAt,
                                ),
                            )
                        }

                        val accountInfo = accountRepository.getAccountInAPI(account.id)

                        // Update account data with API response
                        accountRepository.updateAccountFromAPI(account.id, accountInfo)

                        AppLog.d(TAG, "Account ${account.id} login status check successful")
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Account ${account.id} login status check failed", e.toString())

                        // Mark account as expired in database
                        accountRepository.markAccountExpired(account.id)

                        // Clear tokens for this account
                        userDataStore.removeAccount(account.id)

                        allAccountsValid = false
                    }
                }

                AppLog.d(TAG, "Logged-in accounts status check completed. All valid: $allAccountsValid")
                allAccountsValid
            } catch (e: Exception) {
                AppLog.e(TAG, "Logged-in accounts status check failed", e.toString())
                false
            }
        }
    }

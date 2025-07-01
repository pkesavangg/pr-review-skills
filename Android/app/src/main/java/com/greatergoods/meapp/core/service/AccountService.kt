package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.config.HttpErrorConfig
import com.greatergoods.meapp.core.network.ITokenManager
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.data.storage.db.entity.account.WeightlessSettingsEntity
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.domain.model.PartialAccount
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.auth.SignupRequest
import com.greatergoods.meapp.domain.model.api.user.AccountInfo
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.model.api.user.Token
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IUserSettingsRepository
import com.greatergoods.meapp.domain.services.AuthState
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.MaxAccountsReachedException
import com.greatergoods.meapp.features.common.strings.ToastStrings
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import kotlinx.coroutines.flow.Flow
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
        connectivityObserver: IConnectivityObserver,
        private val tokenManager: ITokenManager,
        dialogQueueService: IDialogQueueService,
        private val userDataStore: UserDataStore,
        private val appNavigationService: IAppNavigationService,
        private val userSettingsRepository: IUserSettingsRepository,
    ) : BaseService(connectivityObserver, dialogQueueService),
        IAccountService {
        companion object {
            private const val MAX_ACCOUNTS = 10
            private const val TAG = "AccountService"
        }

        // region Public Properties

        /**
         * Flow emitting authentication state changes (login, logout, errors, etc).
         */
        override val authEvent = appNavigationService.authEvent

        /**
         * Flow emitting the currently active account, or null if none is active.
         */
        override val activeAccountFlow: Flow<Account?> =
            accountRepository.getStoredActiveAccountFromDB()

        /**
         * Flow emitting the list of all logged-in accounts, with the active account first.
         */
        override val loggedInAccountsFlow: Flow<List<Account>> =
            accountRepository.getLoggedInAccountsFromDB().map { it.sortedActiveFirst() }

        /**
         * Flow indicating whether the maximum number of accounts has been reached.
         */
        override val hasReachedMaxAccounts: Flow<Boolean> =
            loggedInAccountsFlow.map { it.size >= MAX_ACCOUNTS }

        // endregion

        // region Public Functions (Alphabetical Order)

        /**
         * Adds a new account.
         * @param request Account creation request data
         * @return The created account or null if creation fails
         * @throws MaxAccountsReachedException if the maximum number of accounts is reached
         */
        override suspend fun signup(request: SignupRequest): Account? {
            if (hasReachedMaxAccounts.first()) {
                appNavigationService.emitAuthEvent(AuthState.Error("Maximum account limit reached"))
                throw MaxAccountsReachedException()
            }
            return try {
                val response = accountRepository.signup(request)
                val savedAccount = addAccount(response)
                appNavigationService.emitAuthEvent(AuthState.AccountAdded(savedAccount))
                savedAccount
            } catch (e: Exception) {
                if (e is HttpException) {
                    val signupError = SignupStrings.Error
                    val errorMessage =
                        when (e.code()) {
                            HttpErrorConfig.ResponseCode.UNAUTHORIZED -> signupError.MessageNotAuth
                            HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION -> signupError.MessageNoConn
                            HttpErrorConfig.ResponseCode.BAD_REQUEST -> signupError.accountExist
                            else -> signupError.MessageGeneric
                        }
                    val errorHeader =
                        when (e.code()) {
                            HttpErrorConfig.ResponseCode.BAD_REQUEST -> signupError.accountExistHeader
                            else -> signupError.Header
                        }
                    showErrorToast(errorHeader, errorMessage)
                }
                AppLog.e(TAG, "Account creation failed", e.toString())
                appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Account creation failed"))
                null
            }
        }

        /**
         * Changes the password for the current account.
         * @param currentPassword The current password
         * @param newPassword The new password to set
         * @return true if the password was changed successfully, false otherwise
         */
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
                showSuccessToast(
                    ToastStrings.Success.ChangePasswordSuccess.Header,
                    ToastStrings.Success.ChangePasswordSuccess.Message,
                )

                // Password change typically invalidates existing tokens, but we'll keep using current session
                // unless the API specifically returns new tokens
                true
            } catch (e: Exception) {
                AppLog.e(TAG, "Password change failed", e.toString())
                if (e is HttpException) {
                    val header = ToastStrings.Error.ChangePasswordError.Header
                    val msg =
                        when (e.code()) {
                            HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION ->
                                ToastStrings.Error.UpdateProfileError.MessageNoConn

                            HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR ->
                                ToastStrings.Error.UpdateProfileError.MessageServError

                            HttpErrorConfig.ResponseCode.UNAUTHORIZED ->
                                ToastStrings.Error.UpdateProfileError.MessageNotAuth

                            else -> ToastStrings.Error.UpdateProfileError.MessageGeneric
                        }
                    showErrorToast(header, msg)
                }
                false
            }
        }

        /**
         * Checks login status for the active account by calling getAccount API.
         * Updates the account data in DB with the response and refreshes tokens if needed.
         * @return true if account is still valid, false if expired
         */
        override suspend fun checkLoginStatusForActiveAccount(): Boolean {
            return try {
                requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
                val activeAccount = getCurrentAccount()
                if (activeAccount == null) {
                    AppLog.d(TAG, "No active account found")
                    return false
                }
                AppLog.d(TAG, "Checking login status for active account: ${activeAccount.id}")
                val accountInfo = accountRepository.getAccountInAPI(activeAccount.id)
                // Update account data with API response
                accountRepository.updateAccountFromAPI(activeAccount.id, accountInfo)
                val weightlessSetting =
                    WeightlessSettingsEntity(
                        accountId = accountInfo.id,
                        isWeightlessOn = accountInfo.isWeightlessOn,
                        weightlessTimestamp = accountInfo.weightlessTimestamp,
                        weightlessWeight = accountInfo.weightlessWeight,
                        isSynced = true,
                    )
                userSettingsRepository.updateWeightlessInDB(weightlessSetting)
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
                requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
                val loggedInAccounts = getLoggedInAccounts().filter { !it.isActiveAccount }
                if (loggedInAccounts.isEmpty()) {
                    AppLog.d(TAG, "No non-active logged-in accounts found")
                    return true
                }
                for (account in loggedInAccounts) {
                    try {
                        AppLog.d(TAG, "Checking login status for account: ${account.id}")
                        updateUserTokens(account.id)
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
                    }
                }
                AppLog.d(TAG, "Logged-in accounts status check completed.")
                true
            } catch (e: Exception) {
                AppLog.e(TAG, "Logged-in accounts status check failed", e.toString())
                false
            }
        }

        /**
         * Gets the current active account.
         * @return The active account or null if none
         */
        override suspend fun getCurrentAccount(): Account? = activeAccountFlow.first()

        /**
         * Gets the list of all logged-in accounts, with the active account first.
         * @return List of accounts
         */
        override suspend fun getLoggedInAccounts(): List<Account> = loggedInAccountsFlow.first().sortedActiveFirst()

        /**
         * Handles unauthorized logout when token refresh fails.
         * Marks account as expired, removes from storage, and triggers unauthorized logout event.
         * @param accountId The ID of the account to logout
         * @return The affected account or null if not found
         */
        override suspend fun handleUnauthorizedLogout(accountId: String?): Account? {
            if (accountId.isNullOrEmpty()) {
                AppLog.w(TAG, "No account ID available for unauthorized logout")
                return null
            }

            return try {
                AppLog.d(TAG, "Handling unauthorized logout for account: $accountId")
                val account = getCurrentAccount()
                return if (account?.isActiveAccount == true && accountId == account.id) {
                    // Mark account as expired in database
                    accountRepository.markAccountExpired(accountId)

                    // Clear account tokens from DataStore
                    userDataStore.clearAccountTokens(accountId)

                    AppLog.d(TAG, "Unauthorized logout completed for account: $accountId")
                    account
                } else {
                    null
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error during unauthorized logout for account: $accountId", e.toString())
                null
            }
        }

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
                val savedAccount = addAccount(loginResponse)
                appNavigationService.emitAuthEvent(AuthState.LoggedIn(savedAccount))
                savedAccount
            } catch (e: HttpException) {
                val header = ToastStrings.Error.LoginError.Header
                val msg =
                    when (e.code()) {
                        HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION ->
                            ToastStrings.Error.LoginError.MessageNoConn

                        HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR ->
                            ToastStrings.Error.LoginError.MessageServError

                        HttpErrorConfig.ResponseCode.UNAUTHORIZED -> ToastStrings.Error.LoginError.MessageNotAuth
                        else -> ToastStrings.Error.LoginError.MessageGeneric
                    }
                showErrorToast(header, msg)
                AppLog.e(TAG, "Login failed", e.toString())
                appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Login failed"))
                null
            }

        /**
         * Logs out the current user.
         * @param accountId ID of the account to log out
         * @param fcmToken FCM token for push notifications (optional)
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
                // Always perform local logout regardless of network status
                if (isActiveAccount) {
                    accountRepository.deactivateAllAccountsInDB()
                }

                // Update account flags in DB: set isLoggedIn, isExpired, isActive to false
                accountRepository.logoutInDb(accountId)

                // Clear tokens from DataStore and TokenManager
                userDataStore.clearAccountTokens(accountId)
                tokenManager.clearTokens()

                AppLog.d(TAG, "Logout successful")
                appNavigationService.emitAuthEvent(AuthState.LoggedOut(isActiveAccount))
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

                // Sort accounts to handle active account last
                val sortedAccounts =
                    loggedInAccounts.sortedWith(compareByDescending { it.isActiveAccount })

                for (account in sortedAccounts) {
                    try {
                        // Try to logout on API if network is available
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
                appNavigationService.emitAuthEvent(AuthState.LoggedOut(true))
                true
            } catch (e: Exception) {
                AppLog.e(TAG, "Logout all failed", e.toString())
                appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Logout all failed"))
                false
            }

        /**
         * Resets the password for the given email address.
         * @param email The email address to reset the password for
         */
        override suspend fun resetPassword(email: String) {
            try {
                val response = this.accountRepository.resetPasswordInAPI(email)
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
            } catch (e: HttpException) {
                AppLog.e(TAG, "Failed to reset password", e.toString())
                showErrorToast(
                    ToastStrings.Error.ResetPasswordError.Header,
                    ToastStrings.Error.ResetPasswordError.Message,
                )
            }
        }

        /**
         * Switches to a different account.
         * @param account Account to switch to
         * @param showToast Whether to show a toast notification after switching (default: false)
         * @return true if switch was successful
         */
        override suspend fun switchAccount(
            account: Account,
            showToast: Boolean,
        ): Boolean =
            try {
                requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
                updateUserTokens(account.id)
                AppLog.d(TAG, "Successfully switched to account: ${account.email}")
                appNavigationService.emitAuthEvent(AuthState.AccountSwitched(account, showToast))
                true
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to switch account", e.toString())
                appNavigationService.emitAuthEvent(AuthState.Error(e.message ?: "Failed to switch account"))
                false
            }

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
                showSuccessToast(
                    ToastStrings.Success.UpdateProfileSuccess.Header,
                    ToastStrings.Success.UpdateProfileSuccess.Message,
                )

                savedAccount
            } catch (e: HttpException) {
                val header = ToastStrings.Error.UpdateProfileError.Header
                val msg =
                    when (e.code()) {
                        HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION,
                        -> ToastStrings.Error.UpdateProfileError.MessageNoConn

                        HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR,
                        -> ToastStrings.Error.UpdateProfileError.MessageServError

                        HttpErrorConfig.ResponseCode.UNAUTHORIZED,
                        -> ToastStrings.Error.UpdateProfileError.MessageNotAuth

                        else -> ToastStrings.Error.UpdateProfileError.MessageGeneric
                    }
                showErrorToast(header, msg)
                AppLog.e(TAG, "Profile update failed", e.toString())
                throw e
            }
        }

        /**
         * Updates the user's profile information in the local database only.
         * @param accountId The ID of the account to update
         * @param partialAccount The partial account data to update
         * @return The updated account
         */
        override suspend fun updateProfileInDB(
            accountId: String,
            partialAccount: PartialAccount,
        ): Account = accountRepository.updateAccountInDB(accountId, partialAccount)

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

        // endregion

        /**
         * Updates the user's tokens for the given account ID.
         * @param accountId The account ID to update tokens for
         */
        private suspend fun updateUserTokens(accountId: String) {
            // Update tokens in TokenManager for the switched account
            val currentTokens = userDataStore.getData().accounts[accountId]
            setActiveAccountAndTokens(
                accountId,
                currentTokens?.let {
                    Token(
                        accountId = accountId,
                        isActive = true,
                        accessToken = it.accessToken,
                        refreshToken = it.refreshToken,
                        expiresAt = it.expiresAt,
                    )
                },
            )
        }

        /**
         * Helper to activate an account, set it as active in DB, update tokens, and set as active in UserDataStore and TokenManager.
         * @param accountId The account ID to activate
         * @param tokens The tokens to set as active (if not null)
         */
        private suspend fun setActiveAccountAndTokens(
            accountId: String,
            tokens: Token?,
        ) {
            accountRepository.activateAccountInDB(accountId)
            accountRepository.deactivateOtherAccountsInDB(accountId)
            userDataStore.setActiveAccount(accountId)
            accountRepository.updateLastActiveTimeInDB(accountId)
            tokens?.let { tokenManager.setTokens(it) }
        }

        /**
         * Helper to set tokens for a non-active account (used for background API calls).
         * @param accountId The account ID
         * @param tokens The tokens to set (if not null)
         */
        private suspend fun setTokensForAccount(
            accountId: String,
            tokens: Token?,
        ) {
            tokens?.let { tokenManager.setTokens(it) }
        }

        private suspend fun addAccount(loginResponse: LoginResponse): Account {
            val account = loginResponse.account
            val userAccount =
                Account(
                    id = account.id,
                    firstName = account.firstName,
                    lastName = account.lastName,
                    dob = account.dob,
                    email = account.email,
                    expiresAt = loginResponse.expiresAt,
                    fcmToken = null,
                    gender = account.gender,
                    isActiveAccount = true,
                    isLoggedIn = true,
                    isExpired = false,
                    isSynced = false,
                    lastActiveTime = Date().time.toString(),
                    zipcode = account.zipcode,
                    weightUnit = WeightUnit.from(account.weightUnit.lowercase()),
                    isWeightlessOn = account.isWeightlessOn,
                    height = account.height,
                    activityLevel = account.activityLevel,
                    weightlessTimestamp = account.weightlessTimestamp,
                    weightlessWeight = account.weightlessWeight,
                    isStreakOn = account.isStreakOn,
                    dashboardType = account.dashboardType,
                    dashboardMetrics = account.dashboardMetrics,
                )
            val savedAccount = accountRepository.addAccountInDB(userAccount)
            setActiveAccountAndTokens(
                savedAccount.id,
                Token(
                    accountId = savedAccount.id,
                    isActive = true,
                    accessToken = loginResponse.accessToken,
                    refreshToken = loginResponse.refreshToken,
                    expiresAt = loginResponse.expiresAt,
                ),
            )
            return savedAccount
        }

        /**
         * Extension function to sort accounts: active account first, then others by lastActiveTime descending.
         */
        private fun List<Account>.sortedActiveFirst(): List<Account> {
            val active = this.find { it.isActiveAccount }
            val others =
                this
                    .filter { !it.isActiveAccount }
                    .sortedByDescending { it.lastActiveTime?.toLongOrNull() ?: 0L }
            return listOfNotNull(active) + others
        }
    }

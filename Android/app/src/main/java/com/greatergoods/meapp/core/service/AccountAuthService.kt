package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.logging.AppLog
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.data.model.request.CreateAccountRequest
import com.greatergoods.meapp.data.storage.db.entity.account.Account
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.services.AuthState
import com.greatergoods.meapp.domain.services.IAccountAuthService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing account authentication and session state.
 * Handles login, logout, account switching, and token management.
 */
@Singleton
class AccountAuthService @Inject constructor(
    private val accountRepository: IAccountRepository,
    private val connectivityObserver: IConnectivityObserver
) : IAccountAuthService {

    companion object {
        private const val MAX_ACCOUNTS = 10
        private const val TAG = "AccountAuthService"
    }

    // Event flow for authentication state changes
    private val _authStateFlow = MutableSharedFlow<AuthState>()
    override val authStateFlow: SharedFlow<AuthState> = _authStateFlow

    // Current active account flow
    override val activeAccountFlow: Flow<Account?> = accountRepository.getActiveAccount()

    // All logged in accounts flow - sorted by lastActiveTime
    override val loggedInAccountsFlow: Flow<List<Account>> = accountRepository.getAllLoggedInAccounts()
        .map { accounts -> accounts.sortedByDescending { it.account.lastActiveTime?.toLongOrNull() ?: 0L } }

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
    override suspend fun login(email: String, password: String): Account? {
        if (!isNetworkAvailable()) {
            AppLog.e(TAG, "No network connection available")
            _authStateFlow.emit(AuthState.Error("No network connection available"))
            return null
        }

        return try {
            val response = accountRepository.loginOnApi(email, password)
            val account = accountRepository.insertAccount(response.account)
            AppLog.d(TAG, "Login successful for user: ${account.account.email}")
            _authStateFlow.emit(AuthState.LoggedIn(account))
            _isLoginFlow.emit(true)
            account
        } catch (e: Exception) {
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
    override suspend fun logout(accountId: String): Boolean {
        return try {
            // Try to logout on API if network is available
            if (isNetworkAvailable()) {
                try {
                    accountRepository.logoutOnApi(accountId)
                } catch (e: Exception) {
                    AppLog.e(TAG, "API logout failed", e.toString())
                    // Continue with local logout even if API fails
                }
            }
            
            // Always perform local logout
            accountRepository.logoutAccount(accountId)
            AppLog.d(TAG, "Logout successful for account: $accountId")
            _authStateFlow.emit(AuthState.LoggedOut())
            _isLoginFlow.emit(false)
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Logout failed", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Logout failed"))
            false
        }
    }

    /**
     * Logs out all users.
     * @return true if all accounts were logged out successfully
     */
    override suspend fun logoutAll(): Boolean {
        return try {
            val loggedInAccounts = loggedInAccountsFlow.first()
            val activeAccount = activeAccountFlow.first()

            // Sort accounts to handle active account last
            val sortedAccounts = loggedInAccounts.sortedWith(compareByDescending { it.account.isActiveAccount })

            for (account in sortedAccounts) {
                val isOtherUser = account.account.id != activeAccount?.account?.id
                try {
                    if (isNetworkAvailable()) {
                        try {
                            accountRepository.logoutOnApi(account.account.id)
                        } catch (e: Exception) {
                            AppLog.e(TAG, "API logout failed for account "+account.account.id, e.toString())
                            // Continue with local logout even if API fails
                        }
                    }
                    accountRepository.logoutAccount(account.account.id)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to logout account "+account.account.id, e.toString())
                }
            }

            AppLog.d(TAG, "All accounts logged out successfully")
            _authStateFlow.emit(AuthState.LoggedOut())
            _isLoginFlow.emit(false)
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Logout all failed", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Logout all failed"))
            false
        }
    }

    /**
     * Adds a new account.
     * @param request Account creation request data
     * @return The created account or null if creation fails
     */
    override suspend fun addAccount(request: CreateAccountRequest): Account? {
        if (!isNetworkAvailable()) {
            AppLog.e(TAG, "No network connection available")
            _authStateFlow.emit(AuthState.Error("No network connection available"))
            return null
        }

        val currentAccounts = loggedInAccountsFlow.first()
        if (currentAccounts.size >= MAX_ACCOUNTS) {
            AppLog.e(TAG, "Maximum account limit reached")
            _authStateFlow.emit(AuthState.Error("Maximum account limit reached"))
            return null
        }

        return try {
            val response = accountRepository.createAccountOnApi(request)
            val account = accountRepository.insertAccount(response.account)
            AppLog.d(TAG, "Account created successfully: ${account.account.email}")
            _authStateFlow.emit(AuthState.AccountAdded(account))
            _isSignUpFlow.emit(true)
            account
        } catch (e: Exception) {
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
    override suspend fun removeAccount(accountId: String): Boolean {
        return try {
            val account = activeAccountFlow.first()
            if (account?.account?.id == accountId) {
                // If removing active account, switch to another account first
                val otherAccounts = loggedInAccountsFlow.first().filter { it.account.id != accountId }
                if (otherAccounts.isNotEmpty()) {
                    switchAccount(otherAccounts.first())
                }
            }
            account?.let { accountRepository.deleteAccount(it) }
            AppLog.d(TAG, "Account removed successfully: $accountId")
            _authStateFlow.emit(AuthState.AccountRemoved(accountId))
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Account removal failed", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Account removal failed"))
            false
        }
    }

    /**
     * Switches to a different account.
     * @param account Account to switch to
     * @return true if switch was successful
     */
    override suspend fun switchAccount(account: Account): Boolean {
        return try {
            accountRepository.deactivateOtherAccounts(account.account.id)
            accountRepository.updateAccount(account)
            AppLog.d(TAG, "Switched to account: ${account.account.email}")
            _authStateFlow.emit(AuthState.AccountSwitched(account))
            _isSwitchAccountFlow.emit(true)
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Account switch failed", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Account switch failed"))
            false
        }
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
        if (!account.account.isLoggedIn) return false

        // Check token expiration
        account.account.expiresAt?.let { expiresAt ->
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
            val account = getCurrentAccount() ?: return false
            val refreshToken = account.account.refreshToken ?: return false
            val response = accountRepository.refreshAccountOnApi(refreshToken)
            val updatedAccount = accountRepository.updateAccount(response.account)
            AppLog.d(TAG, "Session refreshed successfully for account: ${updatedAccount.account.email}")
            _authStateFlow.emit(AuthState.SessionRefreshed(updatedAccount))
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
    override suspend fun updateTokens(tokens: Map<String, String>): Boolean {
        return try {
            val account = getCurrentAccount() ?: return false
            val updatedAccount = account.copy(
                account = account.account.copy(
                    expiresAt = tokens["expiresAt"]
                )
            )
            accountRepository.updateAccount(updatedAccount)
            AppLog.d(TAG, "Tokens updated successfully")
            _authStateFlow.emit(AuthState.TokensUpdated)
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Token update failed", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Token update failed"))
            false
        }
    }

    /**
     * Checks for a logged-in user and restores their session if found.
     * @return true if a valid session was found and restored
     */
    override suspend fun checkForLoggedInUser(): Boolean {
        return try {
            val storedAccount = accountRepository.getActiveAccount().first()
            if (storedAccount != null && storedAccount.account.id.isNotEmpty() && storedAccount.account.isLoggedIn) {
                // Restore the session
                if (storedAccount.account.expiresAt != null) {
                    val expirationDate = Date(storedAccount.account.expiresAt.toLong())
                    if (expirationDate.after(Date())) {
                        // Session is still valid
                        AppLog.d(TAG, "Restored valid session for account: ${storedAccount.account.email}")
                        _authStateFlow.emit(AuthState.LoggedIn(storedAccount))
                        _isLoginFlow.emit(true)
                        return true
                    } else {
                        // Try to refresh the session
                        return try {
                            refreshSession()
                        } catch (e: Exception) {
                            AppLog.e(TAG, "Session expired for account: "+storedAccount.account.email, e.toString())
                            _authStateFlow.emit(AuthState.LoggedOut("Session expired"))
                            _isLoginFlow.emit(false)
                            false
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to check logged in user", e.toString())
            _authStateFlow.emit(AuthState.Error(e.message ?: "Failed to check logged in user"))
            false
        }
    }

    /**
     * Checks if network is available using the connectivity observer
     */
    private fun isNetworkAvailable(): Boolean {
        return !connectivityObserver.getCurrentNetworkState().unAvailable
    }
}

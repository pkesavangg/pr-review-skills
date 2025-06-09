package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.logging.AppLog
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
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
import com.greatergoods.meapp.core.network.TokenManager
import com.greatergoods.meapp.core.network.Token
import com.greatergoods.meapp.core.network.CreateAccountRequest

/**
 * Service for managing account authentication and session state.
 * Handles login, logout, account switching, and token management.
 */
@Singleton
class AccountAuthService @Inject constructor(
    private val accountRepository: IAccountRepository,
    private val connectivityObserver: IConnectivityObserver,
    private val tokenManager: TokenManager
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
            val loginResponse = accountRepository.loginInAPI(email, password)
            
            // Create account entity from response
            val account = Account(
                account = AccountEntity(
                    id = loginResponse.id,
                    email = email,
                    firstName = loginResponse.firstName,
                    lastName = loginResponse.lastName,
                    isActiveAccount = true,
                    isLoggedIn = true,
                    expiresAt = loginResponse.expiresAt,
                    lastActiveTime = Date().time.toString()
                )
            )

            // Add account to database
            val savedAccount = accountRepository.addAccount(account)
            
            // Set as active account
            accountRepository.setActiveAccount(savedAccount.account.id)
            
            // Update tokens in TokenManager
            tokenManager.setTokens(Token(
                id = savedAccount.account.id,
                accessToken = loginResponse.accessToken,
                refreshToken = loginResponse.refreshToken,
                expiresAt = loginResponse.expiresAt
            ))

            _authStateFlow.emit(AuthState.LoggedIn(savedAccount))
            _isLoginFlow.emit(true)
            savedAccount
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
                    accountRepository.logoutInAPI(null)
                } catch (e: Exception) {
                    AppLog.e(TAG, "API logout failed", e.toString())
                    // Continue with local logout even if API fails
                }
            }

            // Always perform local logout
            accountRepository.removeAccountInDB(accountId)
            tokenManager.clearTokens()
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
                            accountRepository.logoutInAPI(null)
                        } catch (e: Exception) {
                            AppLog.e(TAG, "API logout failed for account ${account.account.id}", e.toString())
                            // Continue with local logout even if API fails
                        }
                    }
                    accountRepository.removeAccountInDB(account.account.id)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to logout account ${account.account.id}", e.toString())
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
    }

    /**
     * Adds a new account.
     * @param request Account creation request data
     * @return The created account or null if creation fails
     */
    override suspend fun addAccount(request: Map<String, Any>): Account? {
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
            val createRequest = CreateAccountRequest(
                email = request["email"] as String,
                password = request["password"] as String,
                firstName = request["firstName"] as String,
                lastName = request["lastName"] as String,
                dob = request["dob"] as String,
                gender = request["gender"] as String
            )

            val response = accountRepository.signupInAPI(createRequest)
            
            // Create account entity from response
            val account = Account(
                account = AccountEntity(
                    id = response["id"] as String,
                    email = createRequest.email,
                    firstName = createRequest.firstName,
                    lastName = createRequest.lastName,
                    dob = createRequest.dob,
                    gender = createRequest.gender,
                    isActiveAccount = true,
                    isLoggedIn = true,
                    expiresAt = response["expiresAt"] as? String,
                    lastActiveTime = Date().time.toString()
                )
            )

            // Add account to database
            val savedAccount = accountRepository.addAccount(account)
            
            // Set as active account
            accountRepository.setActiveAccount(savedAccount.account.id)
            
            // Update tokens in TokenManager
            tokenManager.setTokens(Token(
                id = savedAccount.account.id,
                accessToken = response["accessToken"] as String,
                refreshToken = response["refreshToken"] as String,
                expiresAt = response["expiresAt"] as String
            ))

            _authStateFlow.emit(AuthState.AccountAdded(savedAccount))
            _isSignUpFlow.emit(true)
            savedAccount
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
            val currentAccount = activeAccountFlow.first()
            val isOtherUser = currentAccount?.account?.id != account.account.id

            // If switching to another user, logout current user first
            if (isOtherUser && currentAccount != null) {
                try {
                    if (isNetworkAvailable()) {
                        accountRepository.logoutInAPI(null)
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "API logout failed during account switch", e.toString())
                    // Continue with local logout even if API fails
                }
                accountRepository.removeAccount(currentAccount.account.id)
            }

            // Update account states
            accountRepository.deactivateOtherAccounts(account.account.id)
            val updatedAccount = account.copy(
                account = account.account.copy(
                    isActiveAccount = true,
                    lastActiveTime = Date().time.toString()
                )
            )
            accountRepository.updateAccount(updatedAccount)

            // Update tokens
            if (isOtherUser) {
                tokenManager.clearTokens()
                tokenManager.setTokens(Token(
                    id = updatedAccount.account.id,
                    accessToken = updatedAccount.account.accessToken,
                    refreshToken = updatedAccount.account.refreshToken,
                    expiresAt = updatedAccount.account.expiresAt
                ))
            }

            AppLog.d(TAG, "Switched to account: ${updatedAccount.account.email}")
            _authStateFlow.emit(AuthState.AccountSwitched(updatedAccount))
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
            val refreshedAccount = accountRepository.refreshAccount()
            AppLog.d(TAG, "Session refreshed successfully for account: ${refreshedAccount.account.email}")
            _authStateFlow.emit(AuthState.SessionRefreshed(refreshedAccount))
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
            accountRepository.updateTokens(tokens)
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
            val storedAccount = accountRepository.getStoredActiveAccount()
            if (storedAccount != null && storedAccount.account.id.isNotEmpty() && !storedAccount.account.isExpired) {
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

    /**
     * Checks if network is available using the connectivity observer
     */
    private fun isNetworkAvailable(): Boolean {
        return !connectivityObserver.getCurrentNetworkState().unAvailable
    }
}

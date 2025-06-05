package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.data.storage.db.entity.account.Account
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.services.AuthException
import com.greatergoods.meapp.domain.services.AuthState
import com.greatergoods.meapp.domain.services.IAccountAuthService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing account authentication and session state.
 * Handles login, logout, account switching, and token management.
 */
@Singleton
class AccountAuthService @Inject constructor(
    private val accountRepository: IAccountRepository
) : IAccountAuthService {

    companion object {
        private const val MAX_ACCOUNTS = 10
    }

    // Event flow for authentication state changes
    private val _authStateFlow = MutableSharedFlow<AuthState>()
    override val authStateFlow: SharedFlow<AuthState> = _authStateFlow

    // Current active account flow
    override val activeAccountFlow: Flow<Account?> = accountRepository.getActiveAccount()

    // All logged in accounts flow
    override val loggedInAccountsFlow: Flow<List<Account>> = accountRepository.getAllLoggedInAccounts()

    // Account status flows
    private val _isSignUpFlow = MutableSharedFlow<Boolean>()
    val isSignUpFlow: SharedFlow<Boolean> = _isSignUpFlow

    private val _isLoginFlow = MutableSharedFlow<Boolean>()
    val isLoginFlow: SharedFlow<Boolean> = _isLoginFlow

    private val _isSwitchAccountFlow = MutableSharedFlow<Boolean>()
    val isSwitchAccountFlow: SharedFlow<Boolean> = _isSwitchAccountFlow

    /**
     * Logs in a user with email and password.
     * @param email User's email
     * @param password User's password
     * @return The authenticated account
     * @throws AuthException if login fails
     */
    override suspend fun login(email: String, password: String): Account {
        try {
            val account = accountRepository.login(email, password)
            _authStateFlow.emit(AuthState.LoggedIn(account))
            _isLoginFlow.emit(true)
            return account
        } catch (e: Exception) {
            _authStateFlow.emit(AuthState.Error(e.message ?: "Login failed"))
            throw AuthException("Login failed", e)
        }
    }

    /**
     * Logs out the current user.
     * @param accountId ID of the account to log out
     */
    override suspend fun logout(accountId: String) {
        try {
            accountRepository.logout(accountId)
            _authStateFlow.emit(AuthState.LoggedOut())
            _isLoginFlow.emit(false)
        } catch (e: Exception) {
            _authStateFlow.emit(AuthState.Error(e.message ?: "Logout failed"))
            throw AuthException("Logout failed", e)
        }
    }

    /**
     * Logs out all users.
     */
    override suspend fun logoutAll() {
        try {
            accountRepository.logoutAllAccounts()
            _authStateFlow.emit(AuthState.LoggedOut())
            _isLoginFlow.emit(false)
        } catch (e: Exception) {
            _authStateFlow.emit(AuthState.Error(e.message ?: "Logout all failed"))
            throw AuthException("Logout all failed", e)
        }
    }

    /**
     * Adds a new account.
     * @param accountData Account data for the new user
     * @return The created account
     * @throws AuthException if account limit reached or creation fails
     */
    override suspend fun addAccount(accountData: Map<String, Any>): Account {
        val currentAccounts = loggedInAccountsFlow.first()
        if (currentAccounts.size >= MAX_ACCOUNTS) {
            throw AuthException("Maximum account limit reached")
        }

        try {
            val account = accountRepository.createAccount(accountData)
            _authStateFlow.emit(AuthState.AccountAdded(account))
            _isSignUpFlow.emit(true)
            return account
        } catch (e: Exception) {
            _authStateFlow.emit(AuthState.Error(e.message ?: "Account creation failed"))
            throw AuthException("Account creation failed", e)
        }
    }

    /**
     * Removes an account.
     * @param accountId ID of the account to remove
     */
    override suspend fun removeAccount(accountId: String) {
        try {
            val account = activeAccountFlow.first()
            if (account?.id == accountId) {
                // If removing active account, switch to another account first
                val otherAccounts = loggedInAccountsFlow.first().filter { it.id != accountId }
                if (otherAccounts.isNotEmpty()) {
                    switchAccount(otherAccounts.first())
                }
            }
            account?.let { accountRepository.deleteAccount(it) }
            _authStateFlow.emit(AuthState.AccountRemoved(accountId))
        } catch (e: Exception) {
            _authStateFlow.emit(AuthState.Error(e.message ?: "Account removal failed"))
            throw AuthException("Account removal failed", e)
        }
    }

    /**
     * Switches to a different account.
     * @param account Account to switch to
     */
    override suspend fun switchAccount(account: Account) {
        try {
            accountRepository.switchAccount(account)
            _authStateFlow.emit(AuthState.AccountSwitched(account))
            _isSwitchAccountFlow.emit(true)
        } catch (e: Exception) {
            _authStateFlow.emit(AuthState.Error(e.message ?: "Account switch failed"))
            throw AuthException("Account switch failed", e)
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
     * @throws AuthException if refresh fails
     */
    override suspend fun refreshSession() {
        try {
            val account = getCurrentAccount() ?: throw AuthException("No active account")
            val refreshedAccount = accountRepository.refreshAccount()
            _authStateFlow.emit(AuthState.SessionRefreshed(refreshedAccount))
        } catch (e: Exception) {
            _authStateFlow.emit(AuthState.Error(e.message ?: "Session refresh failed"))
            throw AuthException("Session refresh failed", e)
        }
    }

    /**
     * Updates the account's tokens.
     * @param tokens New token data
     */
    override suspend fun updateTokens(tokens: Map<String, String>) {
        try {
            accountRepository.updateTokens(tokens)
            _authStateFlow.emit(AuthState.TokensUpdated)
        } catch (e: Exception) {
            _authStateFlow.emit(AuthState.Error(e.message ?: "Token update failed"))
            throw AuthException("Token update failed", e)
        }
    }

    /**
     * Checks for a logged-in user and restores their session if found.
     */
    suspend fun checkForLoggedInUser() {
        try {
            val storedAccount = accountRepository.getStoredActiveAccount()
            if (storedAccount != null && storedAccount.id.isNotEmpty() && storedAccount.accessToken.isNotEmpty()) {
                // Restore the session
                if (storedAccount.expiresAt != null) {
                    val expirationDate = Date(storedAccount.expiresAt.toLong())
                    if (expirationDate.after(Date())) {
                        // Session is still valid
                        _authStateFlow.emit(AuthState.LoggedIn(storedAccount))
                        _isLoginFlow.emit(true)
                    } else {
                        // Try to refresh the session
                        try {
                            refreshSession()
                        } catch (e: Exception) {
                            _authStateFlow.emit(AuthState.LoggedOut("Session expired"))
                            _isLoginFlow.emit(false)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _authStateFlow.emit(AuthState.Error(e.message ?: "Failed to check logged in user"))
        }
    }

    /**
     * Clears all account data for a specific account.
     * @param accountId ID of the account to clear
     */
    private suspend fun clearAccountData(accountId: String) {
        try {
            val account = activeAccountFlow.first()
            if (account?.id == accountId) {
                accountRepository.clearAccountData(accountId)
                _authStateFlow.emit(AuthState.LoggedOut())
                _isLoginFlow.emit(false)
            }
        } catch (e: Exception) {
            _authStateFlow.emit(AuthState.Error(e.message ?: "Failed to clear account data"))
        }
    }
} 
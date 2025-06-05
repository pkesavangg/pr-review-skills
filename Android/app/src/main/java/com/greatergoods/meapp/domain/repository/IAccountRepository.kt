package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.data.storage.db.entity.account.Account
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user account operations.
 * Provides methods for account authentication, management, and data synchronization.
 */
interface IAccountRepository {
    /**
     * Authenticates a user with email and password.
     * @param email User's email address
     * @param password User's password
     * @return The authenticated account
     */
    suspend fun login(
        email: String,
        password: String,
    ): Account

    /**
     * Logs out a specific user account.
     * @param accountId ID of the account to log out
     * @param showLogoutAlert Whether to show a logout confirmation alert
     */
    suspend fun logout(
        accountId: String,
        showLogoutAlert: Boolean = false,
    )

    /**
     * Logs out all user accounts.
     */
    suspend fun logoutAllAccounts()

    /**
     * Creates a new user account.
     * @param accountData Account data for the new user
     * @return The created account
     */
    suspend fun createAccount(accountData: Map<String, Any>): Account

    /**
     * Updates an existing account's profile information.
     * @param profile Updated profile data
     * @return The updated account
     */
    suspend fun updateProfile(profile: Map<String, Any>): Account

    /**
     * Updates an account's body composition data.
     * @param bodyComp Updated body composition data
     * @return The updated account
     */
    suspend fun updateBodyComp(bodyComp: Map<String, Any>): Account

    /**
     * Updates an account's password.
     * @param oldPassword Current password
     * @param newPassword New password
     */
    suspend fun updatePassword(
        oldPassword: String,
        newPassword: String,
    )

    /**
     * Requests a password reset for an account.
     * @param email Email address of the account
     */
    suspend fun requestPasswordReset(email: String)

    /**
     * Switches the active account.
     * @param accountData Account data to switch to
     */
    suspend fun switchAccount(accountData: Account?)

    /**
     * Deletes a user account.
     * @param account Account to delete
     */
    suspend fun deleteAccount(account: Account)

    /**
     * Gets the currently active account.
     * @return Flow of the active account
     */
    fun getActiveAccount(): Flow<Account?>

    /**
     * Gets all logged-in accounts.
     * @return Flow of list of logged-in accounts
     */
    fun getAllLoggedInAccounts(): Flow<List<Account>>

    /**
     * Refreshes the current account data from the server.
     * @return The refreshed account
     */
    suspend fun refreshAccount(): Account

    /**
     * Updates the account's tokens.
     * @param tokens New token data
     */
    suspend fun updateTokens(tokens: Map<String, String>)

    /**
     * Clears all offline data for the current account.
     */
    suspend fun clearOfflineData()
}

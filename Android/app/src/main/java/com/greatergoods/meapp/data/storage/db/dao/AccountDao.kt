package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.*
import com.greatergoods.meapp.data.storage.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the account table.
 * Provides methods to interact with the account data in the database.
 */
@Dao
interface AccountDao {
    /**
     * Insert a new account into the database.
     * @param account The account entity to insert
     * @return The row ID of the inserted account
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    /**
     * Update an existing account in the database.
     * @param account The account entity to update
     * @return The number of rows updated
     */
    @Update
    suspend fun update(account: AccountEntity): Int

    /**
     * Delete an account from the database.
     * @param account The account entity to delete
     * @return The number of rows deleted
     */
    @Delete
    suspend fun delete(account: AccountEntity): Int

    /**
     * Get an account by its ID.
     * @param id The account ID
     * @return The account entity if found, null otherwise
     */
    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun getAccountById(id: String): AccountEntity?

    /**
     * Get the active account.
     * @return The active account entity if found, null otherwise
     */
    @Query("SELECT * FROM account WHERE isActiveAccount = 1 LIMIT 1")
    suspend fun getActiveAccount(): AccountEntity?

    /**
     * Get all logged-in accounts.
     * @return A Flow of all logged-in account entities
     */
    @Query("SELECT * FROM account WHERE isLoggedIn = 1")
    fun getAllLoggedInAccounts(): Flow<List<AccountEntity>>

    /**
     * Update the last active time for an account.
     * @param id The account ID
     * @param lastActiveTime The new last active time
     * @return The number of rows updated
     */
    @Query("UPDATE account SET lastActiveTime = :lastActiveTime WHERE id = :id")
    suspend fun updateLastActiveTime(id: String, lastActiveTime: String): Int

    /**
     * Update the dashboard type for an account.
     * @param id The account ID
     * @param dashboardType The new dashboard type
     * @return The number of rows updated
     */
    @Query("UPDATE account SET dashboardType = :dashboardType WHERE id = :id")
    suspend fun updateDashboardType(id: String, dashboardType: String): Int

    /**
     * Update the FCM token for an account.
     * @param id The account ID
     * @param fcmToken The new FCM token
     * @return The number of rows updated
     */
    @Query("UPDATE account SET fcmToken = :fcmToken WHERE id = :id")
    suspend fun updateFcmToken(id: String, fcmToken: String): Int

    /**
     * Update the tokens for an account.
     * @param id The account ID
     * @param accessToken The new access token
     * @param refreshToken The new refresh token
     * @param expiresAt The new expiration time
     * @return The number of rows updated
     */
    @Query("UPDATE account SET accessToken = :accessToken, refreshToken = :refreshToken, expiresAt = :expiresAt WHERE id = :id")
    suspend fun updateTokens(id: String, accessToken: String, refreshToken: String, expiresAt: String): Int

    /**
     * Clear tokens and login status for an account.
     * @param id The account ID
     * @return The number of rows updated
     */
    @Query("UPDATE account SET accessToken = '', refreshToken = '', expiresAt = '', isActiveAccount = 0, isLoggedIn = 0, isExpired = 0 WHERE id = :id")
    suspend fun clearAccountTokens(id: String): Int

    /**
     * Update the active account status.
     * @param id The account ID to set as active
     * @return The number of rows updated
     */
    @Query("UPDATE account SET isActiveAccount = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActiveAccount(id: String): Int

    /**
     * Update the logged-in status for an account.
     * @param id The account ID
     * @param isLoggedIn The new logged-in status
     * @return The number of rows updated
     */
    @Query("UPDATE account SET isLoggedIn = :isLoggedIn WHERE id = :id")
    suspend fun updateLoggedInStatus(id: String, isLoggedIn: Boolean): Int

    /**
     * Update the profile information for an account.
     * @param id The account ID
     * @param firstName The new first name
     * @param lastName The new last name
     * @param email The new email
     * @param dob The new date of birth
     * @param gender The new gender
     * @param height The new height
     * @param zipcode The new zipcode
     * @param activityLevel The new activity level
     * @return The number of rows updated
     */
    @Query("""
        UPDATE account 
        SET firstName = :firstName,
            lastName = :lastName,
            email = :email,
            dob = :dob,
            gender = :gender,
            height = :height,
            zipcode = :zipcode,
            activityLevel = :activityLevel
        WHERE id = :id
    """)
    suspend fun updateProfile(
        id: String,
        firstName: String,
        lastName: String,
        email: String,
        dob: String,
        gender: String,
        height: String,
        zipcode: String,
        activityLevel: String
    ): Int

    /**
     * Update the goal information for an account.
     * @param id The account ID
     * @param goalType The new goal type
     * @param goalWeight The new goal weight
     * @param initialWeight The new initial weight
     * @return The number of rows updated
     */
    @Query("""
        UPDATE account 
        SET goalType = :goalType,
            goalWeight = :goalWeight,
            initialWeight = :initialWeight
        WHERE id = :id
    """)
    suspend fun updateGoal(
        id: String,
        goalType: String,
        goalWeight: String,
        initialWeight: Float
    ): Int

    /**
     * Update the dashboard metrics for an account.
     * @param id The account ID
     * @param dashboardMetrics The new dashboard metrics
     * @return The number of rows updated
     */
    @Query("UPDATE account SET dashboardMetrics = :dashboardMetrics WHERE id = :id")
    suspend fun updateDashboardMetrics(id: String, dashboardMetrics: String): Int

    /**
     * Update the weight unit for an account.
     * @param id The account ID
     * @param weightUnit The new weight unit
     * @return The number of rows updated
     */
    @Query("UPDATE account SET weightUnit = :weightUnit WHERE id = :id")
    suspend fun updateWeightUnit(id: String, weightUnit: String): Int

    /**
     * Update the integration status for an account.
     * @param id The account ID
     * @param isFitbitOn Fitbit integration status
     * @param isFitbitValid Fitbit validity status
     * @param isGoogleFitOn Google Fit integration status
     * @param isGoogleFitValid Google Fit validity status
     * @param isMFPOn MyFitnessPal integration status
     * @param isMFPValid MyFitnessPal validity status
     * @param isUAOn Under Armour integration status
     * @param isUAValid Under Armour validity status
     * @return The number of rows updated
     */
    @Query("""
        UPDATE account 
        SET isFitbitOn = :isFitbitOn,
            isFitbitValid = :isFitbitValid,
            isGoogleFitOn = :isGoogleFitOn,
            isGoogleFitValid = :isGoogleFitValid,
            isMFPOn = :isMFPOn,
            isMFPValid = :isMFPValid,
            isUAOn = :isUAOn,
            isUAValid = :isUAValid
        WHERE id = :id
    """)
    suspend fun updateIntegrationStatus(
        id: String,
        isFitbitOn: Boolean,
        isFitbitValid: Boolean,
        isGoogleFitOn: Boolean,
        isGoogleFitValid: Boolean,
        isMFPOn: Boolean,
        isMFPValid: Boolean,
        isUAOn: Boolean,
        isUAValid: Boolean
    ): Int

    /**
     * Update the notification settings for an account.
     * @param id The account ID
     * @param shouldSendEntryNotifications Whether to send entry notifications
     * @param shouldSendWeightInEntryNotifications Whether to send weight entry notifications
     * @return The number of rows updated
     */
    @Query("""
        UPDATE account 
        SET shouldSendEntryNotifications = :shouldSendEntryNotifications,
            shouldSendWeightInEntryNotifications = :shouldSendWeightInEntryNotifications
        WHERE id = :id
    """)
    suspend fun updateNotificationSettings(
        id: String,
        shouldSendEntryNotifications: Boolean,
        shouldSendWeightInEntryNotifications: Boolean
    ): Int

    /**
     * Update the weightless data for an account.
     * @param id The account ID
     * @param weightlessWeight The weightless weight
     * @param weightlessBodyFat The weightless body fat
     * @param weightlessMuscle The weightless muscle
     * @param weightlessTimestamp The weightless timestamp
     * @return The number of rows updated
     */
    @Query("""
        UPDATE account 
        SET weightlessWeight = :weightlessWeight,
            weightlessBodyFat = :weightlessBodyFat,
            weightlessMuscle = :weightlessMuscle,
            weightlessTimestamp = :weightlessTimestamp
        WHERE id = :id
    """)
    suspend fun updateWeightlessData(
        id: String,
        weightlessWeight: Float,
        weightlessBodyFat: Float,
        weightlessMuscle: Float,
        weightlessTimestamp: String
    ): Int

    /**
     * Update the sync status for an account.
     * @param id The account ID
     * @param isSynced Whether the account data is synced with the server
     * @return The number of rows updated
     */
    @Query("UPDATE account SET isSynced = :isSynced WHERE id = :id")
    suspend fun updateSyncStatus(id: String, isSynced: Boolean): Int

    /**
     * Get all accounts that need syncing.
     * @return A Flow of account entities that are not synced
     */
    @Query("SELECT * FROM account WHERE isSynced = 0")
    fun getUnsyncedAccounts(): Flow<List<AccountEntity>>

    /**
     * Mark all accounts as synced.
     * @return The number of rows updated
     */
    @Query("UPDATE account SET isSynced = 1")
    suspend fun markAllAccountsSynced(): Int

    /**
     * Mark a specific account as synced.
     * @param id The account ID
     * @return The number of rows updated
     */
    @Query("UPDATE account SET isSynced = 1 WHERE id = :id")
    suspend fun markAccountSynced(id: String): Int

    /**
     * Mark a specific account as unsynced.
     * @param id The account ID
     * @return The number of rows updated
     */
    @Query("UPDATE account SET isSynced = 0 WHERE id = :id")
    suspend fun markAccountUnsynced(id: String): Int
} 
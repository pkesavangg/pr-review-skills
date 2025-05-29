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
    @Query("SELECT * FROM account WHERE account_id = :id")
    suspend fun getAccountById(id: String): AccountEntity?

    /**
     * Get the active account.
     * @return The active account entity if found, null otherwise
     */
    @Query("SELECT * FROM account WHERE is_active_account = 1 LIMIT 1")
    suspend fun getActiveAccount(): AccountEntity?

    /**
     * Get all logged-in accounts.
     * @return A Flow of all logged-in account entities
     */
    @Query("SELECT * FROM account WHERE is_logged_in = 1")
    fun getAllLoggedInAccounts(): Flow<List<AccountEntity>>

    /**
     * Update the last active time for an account.
     * @param id The account ID
     * @param lastActiveTime The new last active time
     * @return The number of rows updated
     */
    @Query("UPDATE account SET last_active_time = :lastActiveTime WHERE account_id = :id")
    suspend fun updateLastActiveTime(id: String, lastActiveTime: String): Int

    /**
     * Update the dashboard type for an account.
     * @param id The account ID
     * @param dashboardType The new dashboard type
     * @return The number of rows updated
     */
    @Query("UPDATE account SET dashboard_type = :dashboardType WHERE account_id = :id")
    suspend fun updateDashboardType(id: String, dashboardType: String): Int

    /**
     * Update the FCM token for an account.
     * @param id The account ID
     * @param fcmToken The new FCM token
     * @return The number of rows updated
     */
    @Query("UPDATE account SET fcm_token = :fcmToken WHERE account_id = :id")
    suspend fun updateFcmToken(id: String, fcmToken: String): Int

    /**
     * Update the tokens for an account.
     * @param id The account ID
     * @param accessToken The new access token
     * @param refreshToken The new refresh token
     * @param expiresAt The new expiration time
     * @return The number of rows updated
     */
    @Query("UPDATE account SET access_token = :accessToken, refresh_token = :refreshToken, expires_at = :expiresAt WHERE account_id = :id")
    suspend fun updateTokens(id: String, accessToken: String, refreshToken: String, expiresAt: String): Int

    /**
     * Clear tokens and login status for an account.
     * @param id The account ID
     * @return The number of rows updated
     */
    @Query("UPDATE account SET access_token = '', refresh_token = '', expires_at = '', is_active_account = 0, is_logged_in = 0, is_expired = 0 WHERE account_id = :id")
    suspend fun clearAccountTokens(id: String): Int

    /**
     * Update the active account status.
     * @param id The account ID to set as active
     * @return The number of rows updated
     */
    @Query("UPDATE account SET is_active_account = CASE WHEN account_id = :id THEN 1 ELSE 0 END")
    suspend fun setActiveAccount(id: String): Int

    /**
     * Update the logged-in status for an account.
     * @param id The account ID
     * @param isLoggedIn The new logged-in status
     * @return The number of rows updated
     */
    @Query("UPDATE account SET is_logged_in = :isLoggedIn WHERE account_id = :id")
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
        SET first_name = :firstName,
            last_name = :lastName,
            email = :email,
            dob = :dob,
            gender = :gender,
            height = :height,
            zipcode = :zipcode,
            activity_level = :activityLevel
        WHERE account_id = :id
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
        SET goal_type = :goalType,
            goal_weight = :goalWeight,
            initial_weight = :initialWeight
        WHERE account_id = :id
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
    @Query("UPDATE account SET dashboard_metrics = :dashboardMetrics WHERE account_id = :id")
    suspend fun updateDashboardMetrics(id: String, dashboardMetrics: String): Int

    /**
     * Update the weight unit for an account.
     * @param id The account ID
     * @param weightUnit The new weight unit
     * @return The number of rows updated
     */
    @Query("UPDATE account SET weight_unit = :weightUnit WHERE account_id = :id")
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
        SET is_fitbit_on = :isFitbitOn,
            is_fitbit_valid = :isFitbitValid,
            is_google_fit_on = :isGoogleFitOn,
            is_google_fit_valid = :isGoogleFitValid,
            is_mfp_on = :isMFPOn,
            is_mfp_valid = :isMFPValid,
            is_ua_on = :isUAOn,
            is_ua_valid = :isUAValid
        WHERE account_id = :id
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
        SET should_send_entry_notifications = :shouldSendEntryNotifications,
            should_send_weight_in_entry_notifications = :shouldSendWeightInEntryNotifications
        WHERE account_id = :id
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
        SET weightless_weight = :weightlessWeight,
            weightless_body_fat = :weightlessBodyFat,
            weightless_muscle = :weightlessMuscle,
            weightless_timestamp = :weightlessTimestamp
        WHERE account_id = :id
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
    @Query("UPDATE account SET is_synced = :isSynced WHERE account_id = :id")
    suspend fun updateSyncStatus(id: String, isSynced: Boolean): Int

    /**
     * Get all accounts that need syncing.
     * @return A Flow of account entities that are not synced
     */
    @Query("SELECT * FROM account WHERE is_synced = 0")
    fun getUnsyncedAccounts(): Flow<List<AccountEntity>>

    /**
     * Mark all accounts as synced.
     * @return The number of rows updated
     */
    @Query("UPDATE account SET is_synced = 1")
    suspend fun markAllAccountsSynced(): Int

    /**
     * Mark a specific account as synced.
     * @param id The account ID
     * @return The number of rows updated
     */
    @Query("UPDATE account SET is_synced = 1 WHERE account_id = :id")
    suspend fun markAccountSynced(id: String): Int

    /**
     * Mark a specific account as unsynced.
     * @param id The account ID
     * @return The number of rows updated
     */
    @Query("UPDATE account SET is_synced = 0 WHERE account_id = :id")
    suspend fun markAccountUnsynced(id: String): Int
} 
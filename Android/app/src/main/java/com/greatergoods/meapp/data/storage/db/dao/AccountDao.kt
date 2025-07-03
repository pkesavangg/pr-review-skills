package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.greatergoods.meapp.data.storage.db.entity.account.Account
import com.greatergoods.meapp.data.storage.db.entity.account.AccountEntity
import com.greatergoods.meapp.data.storage.db.entity.account.DashboardSettingsEntity
import com.greatergoods.meapp.data.storage.db.entity.account.GoalSettingsEntity
import com.greatergoods.meapp.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.greatergoods.meapp.data.storage.db.entity.account.NotificationSettingsEntity
import com.greatergoods.meapp.data.storage.db.entity.account.StreaksSettingsEntity
import com.greatergoods.meapp.data.storage.db.entity.account.WeightCompSettingsEntity
import com.greatergoods.meapp.data.storage.db.entity.account.WeightlessSettingsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for managing account data and related settings.
 * Provides methods to interact with the account and its associated settings in the database.
 */
@Dao
interface AccountDao {
    // Account Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    /**
     * Updates an account entity in the database.
     * @param account The account entity to update
     */
    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    /**
     * Deletes the account with the given ID from the database.
     * @param accountId The ID of the account to delete.
     */
    @Query("DELETE FROM account WHERE accountId = :accountId")
    suspend fun deleteAccountById(accountId: String)

    @Transaction
    @Query("DELETE FROM account")
    suspend fun removeAllAccounts()

    // Account Queries
    @Transaction
    @Query("SELECT * FROM account WHERE accountId = :accountId")
    fun getAccount(accountId: String): Flow<Account?>

    /**
     * Gets just the AccountEntity by ID without relations.
     * @param accountId The account ID to get
     * @return The AccountEntity or null if not found
     */
    @Query("SELECT * FROM account WHERE accountId = :accountId")
    suspend fun getAccountEntity(accountId: String): AccountEntity?

    @Transaction
    @Query("SELECT * FROM account WHERE isActiveAccount = 1")
    fun getActiveAccount(): Flow<Account?>

    @Transaction
    @Query("SELECT * FROM account WHERE isLoggedIn = 1")
    fun getAllLoggedInAccounts(): Flow<List<Account>>

    // Account State Management
    @Query("UPDATE account SET isActiveAccount = 0 WHERE accountId != :accountId")
    suspend fun deactivateOtherAccounts(accountId: String)

    @Query("UPDATE account SET isActiveAccount = 0")
    suspend fun deactivateAllAccounts()

    @Query("UPDATE account SET isActiveAccount = 1 WHERE accountId = :accountId")
    suspend fun activateAccount(accountId: String)

    @Query("UPDATE account SET lastActiveTime = :timestamp WHERE accountId = :accountId")
    suspend fun updateLastActiveTime(accountId: String, timestamp: String)

    @Query("UPDATE account SET isLoggedIn = 0, isActiveAccount = 0, isExpired = 0 WHERE accountId = :accountId")
    suspend fun logoutAccount(accountId: String)

    @Query("UPDATE account SET isLoggedIn = 0, isActiveAccount = 0, isExpired = 0")
    suspend fun logoutAllAccounts()

    @Query("UPDATE account SET isSynced = :isSynced WHERE accountId = :accountId")
    suspend fun updateSyncStatus(accountId: String, isSynced: Boolean)

    // Weight Composition Settings
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightCompSettings(settings: WeightCompSettingsEntity)

    @Update
    suspend fun updateWeightCompSettings(settings: WeightCompSettingsEntity)

    // Goal Settings
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoalSettings(settings: GoalSettingsEntity)

    @Update
    suspend fun updateGoalSettings(settings: GoalSettingsEntity)

    // Streaks Settings
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreaksSettings(settings: StreaksSettingsEntity)

    @Update
    suspend fun updateStreaksSettings(settings: StreaksSettingsEntity)

    // Weightless Settings
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightlessSettings(settings: WeightlessSettingsEntity)

    @Update
    suspend fun updateWeightlessSettings(settings: WeightlessSettingsEntity)

    // Notification Settings
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationSettings(settings: NotificationSettingsEntity)

    @Update
    suspend fun updateNotificationSettings(settings: NotificationSettingsEntity)

    // Dashboard Settings
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDashboardSettings(settings: DashboardSettingsEntity)

    @Update
    suspend fun updateDashboardSettings(settings: DashboardSettingsEntity)

    // Integrations Settings
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntegrationsSettings(settings: IntegrationsSettingsEntity)

    @Update
    suspend fun updateIntegrationsSettings(settings: IntegrationsSettingsEntity)

    // Sync Operations
    @Transaction
    @Query("SELECT * FROM account WHERE isSynced = 0")
    fun getUnsyncedAccounts(): Flow<List<AccountEntity>>

    @Transaction
    @Query(
        """
        SELECT * FROM account
        WHERE accountId IN (
            SELECT accountId FROM weight_comp_settings WHERE isSynced = 0
        ) OR isSynced = 0
    """,
    )
    fun getUnsyncedBodyCompAccounts(): Flow<List<Account>>

    @Transaction
    @Query(
        """
        SELECT * FROM account
        WHERE accountId IN (
            SELECT accountId FROM notification_settings WHERE isSynced = 0
        ) OR isSynced = 0
    """,
    )
    fun getUnsyncedNotificationAccounts(): Flow<List<Account>>

    @Transaction
    @Query(
        """
        SELECT * FROM account
        WHERE accountId IN (
            SELECT accountId FROM streaks_settings WHERE isSynced = 0
        )
    """,
    )
    fun getUnsyncedStreakAccounts(): Flow<List<Account>>

    @Transaction
    @Query(
        """
        SELECT * FROM account
        WHERE accountId IN (
            SELECT accountId FROM weightless_settings WHERE isSynced = 0
        )
    """,
    )
    fun getUnsyncedWeightlessAccounts(): Flow<List<Account>>

    @Transaction
    @Query(
        """
        SELECT * FROM account
        WHERE accountId IN (
            SELECT accountId FROM goal_settings WHERE isSynced = 0
        )
    """,
    )
    fun getUnsyncedGoalAccounts(): Flow<List<Account>>

    @Query("UPDATE account SET isSynced = 1")
    suspend fun markAllAccountsSynced()

    @Query("UPDATE account SET isSynced = 1 WHERE accountId = :accountId")
    suspend fun markAccountSynced(accountId: String)

    // Account Expiration Management
    @Query("UPDATE account SET isExpired = 1, isActiveAccount = 0, expiresAt = '' WHERE accountId = :accountId")
    suspend fun markAccountExpired(accountId: String)

    @Query("DELETE FROM weight_comp_settings  WHERE accountId = :accountId")
    suspend fun deleteWeightCompSettingsByAccount(accountId: String)

    @Query("DELETE FROM goal_settings WHERE accountId = :accountId")
    suspend fun deleteGoalSettingsByAccount(accountId: String)

    @Query("DELETE FROM streaks_settings WHERE accountId = :accountId")
    suspend fun deleteStreaksSettingsByAccount(accountId: String)

    @Query("DELETE FROM weightless_settings  WHERE accountId = :accountId")
    suspend fun deleteWeightlessSettingsByAccount(accountId: String)

    @Query("DELETE FROM notification_settings WHERE accountId = :accountId")
    suspend fun deleteNotificationSettingsByAccount(accountId: String)

    @Query("DELETE FROM dashboard_settings   WHERE accountId = :accountId")
    suspend fun deleteDashboardSettingsByAccount(accountId: String)

    @Query("DELETE FROM integrations_settings WHERE accountId = :accountId")
    suspend fun deleteIntegrationsSettingsByAccount(accountId: String)

    @Transaction
    suspend fun deleteAllTables(accountId: String) {
        deleteWeightCompSettingsByAccount(accountId)
        deleteGoalSettingsByAccount(accountId)
        deleteStreaksSettingsByAccount(accountId)
        deleteWeightlessSettingsByAccount(accountId)
        deleteNotificationSettingsByAccount(accountId)
        deleteDashboardSettingsByAccount(accountId)
        deleteIntegrationsSettingsByAccount(accountId)
        deleteAccountById(accountId)
    }
}

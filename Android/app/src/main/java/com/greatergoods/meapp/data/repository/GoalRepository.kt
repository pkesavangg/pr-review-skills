package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.api.IGoalAPI
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.account.GoalSettingsEntity
import com.greatergoods.meapp.domain.model.api.goal.GoalRequest
import com.greatergoods.meapp.domain.model.goal.Goal
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IGoalRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/**
 * Repository implementation for goal operations.
 * Handles goal settings for user accounts.
 */
@Singleton
class GoalRepository @Inject constructor(
    private val goalAPI: IGoalAPI,
    private val accountDao: AccountDao,
    private val accountRepository: IAccountRepository,
) : IGoalRepository {

    private val TAG = "GoalRepository"

    override suspend fun updateGoalSettingsInDB(goalSettings: GoalSettingsEntity) {
        // Update local database
        val activeAccount = accountDao.getActiveAccount().first()
        activeAccount?.let { account ->
            val goalEntity = GoalSettingsEntity(
                accountId = activeAccount.account.id,
                goalType = goalSettings.goalType,
                weight = goalSettings.weight,
                goalWeight = goalSettings.goalWeight,
                goalPercent = goalSettings.goalPercent, // Will be calculated when needed
                isSynced = goalSettings.isSynced
            )
            accountDao.updateGoalSettings(goalEntity)
            account
        }
    }

    /**
     * Updates the goal setting for the active account.
     * @param goalRequest The goal setting to update
     * @return Updated account with new goal settings
     */
    override suspend fun updateGoalSetting(goalRequest: GoalRequest): Account? {
        return try {
            AppLog.d(TAG, "Updating goal setting online")

            // Call API to update goal
            val response = goalAPI.updateGoal(goalRequest)

            // Update local database with synced status
            val activeAccount = accountRepository.getStoredActiveAccountFromDB().first()
            activeAccount?.let { account ->
                val goalEntity = GoalSettingsEntity(
                    accountId = account.id,
                    goalType = response.type,
                    weight = response.initialWeight.toFloat(),
                    goalWeight = response.goalWeight.toString(),
                    goalPercent = 0f, // Will be calculated when needed
                    isSynced = true
                )
                updateGoalSettingsInDB(goalEntity)
                // Return the updated account
                account
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to update goal setting online", e.toString())
            throw e
        }
    }

    /**
     * Updates goal setting offline (stores locally for later sync).
     * Used when network is unavailable.
     * @param request The goal setting request
     * @return Updated account with new goal settings
     */
    override suspend fun updateGoalSettingOffline(request: GoalRequest): Account? {
        return try {
            AppLog.d(TAG, "Storing goal setting for offline sync")

            val activeAccount = accountRepository.getStoredActiveAccountFromDB().first()
            activeAccount?.let { account ->
                val goalEntity = GoalSettingsEntity(
                    accountId = account.id,
                    goalType = request.type,
                    weight = request.initialWeight.toFloat(),
                    goalWeight = request.goalWeight.toString(),
                    goalPercent = 0f, // Will be calculated when needed
                    isSynced = false // Mark as unsynced for later upload
                )
                updateGoalSettingsInDB(goalEntity)
                // Return updated account - would need to be fetched from database
                // For now, return the current account
                // TODO: Fetch updated account from database with new goal settings
                account
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to store goal setting offline", e.toString())
            throw e
        }
    }

    /**
     * Gets accounts with unsynced goal settings changes.
     * Used by offline handler service for syncing goal settings specifically.
     * @return List of accounts with pending goal settings changes
     */
    override suspend fun getUnsyncedGoalAccountsFromDB(): List<Account> {
        return try {
            AppLog.d(TAG, "Getting accounts with unsynced goal settings")
            // TODO: Query database for accounts with unsynced goal settings
            // This would require a database query to find GoalSettingsEntity with isSynced = false
            // and then map to Account objects
            emptyList() // Placeholder
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to get unsynced goal accounts", e.toString())
            emptyList()
        }
    }

    /**
     * Gets the current goal for the active account.
     * @return Current goal or null if no goal is set
     */
    override suspend fun getCurrentGoal(): Goal? {
        return try {
            val activeAccount = accountRepository.getStoredActiveAccountFromDB().first()
            activeAccount?.let { account ->
                // TODO: Extract goal data from account or query goal settings from database
                // This would require goal fields to be added to Account domain model
                // or a separate query to get GoalSettingsEntity for the account

                // Placeholder implementation
                null
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to get current goal", e.toString())
            null
        }
    }

    /**
     * Calculates goal completion percentage based on current weight.
     * @param goal The goal to calculate percentage for
     * @param currentWeight The current weight to calculate against
     * @return Percentage completion (0-100) or null if calculation not possible
     */
    override fun calculateGoalPercent(goal: Goal, currentWeight: Double): Int? {
        val goalWeight = goal.goalWeight
        val initialWeight = goal.initialWeight

        return when (goal.type.lowercase()) {
            "lose" -> {
                val percent = (currentWeight - goalWeight) / (initialWeight - goalWeight)
                val result = 100 - floor(percent * 100).toInt()
                if (result < 0) 0 else result
            }
            "gain" -> {
                val percent = (currentWeight - initialWeight) / (goalWeight - initialWeight)
                val result = floor(percent * 100).toInt()
                if (result < 0) 0 else result
            }
            else -> null // Maintain goals don't have percentage
        }
    }
}

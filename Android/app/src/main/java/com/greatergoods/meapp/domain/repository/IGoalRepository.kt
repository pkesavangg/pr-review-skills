package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.data.storage.db.entity.account.GoalSettingsEntity
import com.greatergoods.meapp.domain.model.api.goal.GoalRequest
import com.greatergoods.meapp.domain.model.goal.Goal
import com.greatergoods.meapp.domain.model.storage.Account.Account

/**
 * Repository interface for goal operations.
 * Handles goal settings for user accounts.
 */
interface IGoalRepository {

    /**
     * Updates the goal setting for the active account.
     * @return Updated account with new goal settings
     */
    suspend fun updateGoalSettingsInDB(goalSettings: GoalSettingsEntity)

    /**
     * Updates the goal setting for the active account.
     * @param goalRequest The goal setting to update
     * @return Updated account with new goal settings
     */
    suspend fun updateGoalSetting(goalRequest: GoalRequest): Account?

    /**
     * Updates goal setting offline (stores locally for later sync).
     * Used when network is unavailable.
     * @param request The goal setting request
     * @return Updated account with new goal settings
     */
    suspend fun updateGoalSettingOffline(request: GoalRequest): Account?

    /**
     * Gets the current goal for the active account.
     * @return Current goal or null if no goal is set
     */
    suspend fun getCurrentGoal(): Goal?

    /**
     * Calculates goal completion percentage based on current weight.
     * @param goal The goal to calculate percentage for
     * @param currentWeight The current weight to calculate against
     * @return Percentage completion (0-100) or null if calculation not possible
     */
    fun calculateGoalPercent(goal: Goal, currentWeight: Double): Int?
}

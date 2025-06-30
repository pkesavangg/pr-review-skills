package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.domain.model.goal.Goal
import com.greatergoods.meapp.domain.model.storage.Account.Account
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for goal operations.
 * Handles business logic for weight goal management.
 * Based on Angular goal.service.ts functionality.
 */
interface IGoalService {

    /**
     * Observable flow of the current goal status.
     * Similar to Angular's status BehaviorSubject.
     */
    val goalStatusFlow: Flow<Goal?>

    /**
     * Updates the goal for the active account.
     * @param goalWeight Target weight for the goal
     * @param initialWeight Starting weight for the goal
     * @param goalType Type of goal: 'lose', 'gain', or 'maintain'
     * @param wasMet Whether the previous goal was met (optional)
     * @return Updated account with new goal settings
     */
    suspend fun updateGoal(
        goalWeight: Double,
        initialWeight: Double,
        goalType: String,
        wasMet: Boolean = false
    ): Account?

    /**
     * Gets the formatted goal type string for display.
     * @param type The goal type ('lose', 'gain', 'maintain')
     * @return Formatted string for UI display
     */
    fun getFormattedGoalType(type: String): String

    /**
     * Calculates goal completion percentage.
     * @param goal The goal to calculate percentage for
     * @param currentWeight Current weight to calculate against
     * @return Percentage completion (0-100) or null if calculation not possible
     */
    fun getPercentComplete(goal: Goal, currentWeight: Double): Int?

    /**
     * Checks if goal completion alert should be shown.
     * @param currentWeight Current weight to check against goal
     * @return True if goal completion alert should be shown
     */
    suspend fun shouldShowGoalCompletionAlert(currentWeight: Double): Boolean

    /**
     * Shows goal completion alert based on goal type.
     * @param currentWeight Current weight that triggered the alert
     */
    suspend fun showGoalCompletionAlert(currentWeight: Double)

    /**
     * Gets the current goal or null if none is set.
     * @return Current goal or null
     */
    suspend fun getCurrentGoal(): Goal?
}

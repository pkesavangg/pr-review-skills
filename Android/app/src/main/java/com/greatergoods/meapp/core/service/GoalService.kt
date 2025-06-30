package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.domain.model.api.goal.GoalRequest
import com.greatergoods.meapp.domain.model.goal.Goal
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IGoalRepository
import com.greatergoods.meapp.domain.services.IGoalService
import com.greatergoods.meapp.features.common.model.DialogModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/**
 * Service implementation for goal operations.
 * Handles business logic for weight goal management.
 * Based on Angular goal.service.ts functionality.
 */
@Singleton
class GoalService @Inject constructor(
    private val goalRepository: IGoalRepository,
    private val connectivityObserver: IConnectivityObserver,
    private val dialogQueueService: IDialogQueueService
) : IGoalService {

    private val TAG = "GoalService"

    private val _goalStatusFlow = MutableStateFlow<Goal?>(null)
    override val goalStatusFlow: Flow<Goal?> = _goalStatusFlow.asStateFlow()

    init {
        // Initialize goal status flow based on active account changes
        // Similar to Angular's subscription to activeAccount
        // TODO: Set up combine flow with accountService.activeAccountFlow and latest entry
        // This would update goal status when account or latest entry changes
    }

    /**
     * Checks if network is available using the connectivity observer
     */
    private fun isNetworkAvailable(): Boolean = !connectivityObserver.getCurrentNetworkState().unAvailable

    /**
     * Updates the goal for the active account.
     * Handles both online and offline scenarios.
     * @param goalWeight Target weight for the goal
     * @param initialWeight Starting weight for the goal
     * @param goalType Type of goal: 'lose', 'gain', or 'maintain'
     * @param wasMet Whether the previous goal was met (optional)
     * @return Updated account with new goal settings
     */
    override suspend fun updateGoal(
        goalWeight: Double,
        initialWeight: Double,
        goalType: String,
        wasMet: Boolean
    ): Account? {
        return try {
            AppLog.d(TAG, "Updating goal: type=$goalType, goalWeight=$goalWeight, initialWeight=$initialWeight")

            val goalRequest = GoalRequest(
                goalWeight = goalWeight,
                initialWeight = initialWeight,
                type = goalType,
                goalType = goalType,
                metPreviousGoal = if (wasMet) true else null
            )

            val updatedAccount = if (isNetworkAvailable()) {
                // Online: Update via API and mark as synced in DB
                AppLog.d(TAG, "Network available - updating goal online")
                goalRepository.updateGoalSetting(goalRequest)
            } else {
                // Offline: Store locally and mark as unsynced for later sync
                AppLog.d(TAG, "Network unavailable - storing goal for offline sync")
                goalRepository.updateGoalSettingOffline(goalRequest)
            }

            // Update goal status flow
            updatedAccount?.let { account ->
                updateGoalStatusFromAccount(account)
            }

            updatedAccount
        } catch (e: Exception) {
            AppLog.e(TAG, "Error updating goal", e.toString())
            throw e
        }
    }

    /**
     * Gets the formatted goal type string for display.
     * Based on Angular's getFormattedGoalType method.
     * @param type The goal type ('lose', 'gain', 'maintain')
     * @return Formatted string for UI display
     */
    override fun getFormattedGoalType(type: String): String {
        return when (type.lowercase()) {
            "lose" -> "Lose"
            "gain" -> "Gain"
            "maintain" -> "Maintain"
            else -> "Maintain"
        }
    }

    /**
     * Calculates goal completion percentage.
     * Based on Angular's getPercentComplete method.
     * @param goal The goal to calculate percentage for
     * @param currentWeight Current weight to calculate against
     * @return Percentage completion (0-100) or null if calculation not possible
     */
    override fun getPercentComplete(goal: Goal, currentWeight: Double): Int? {
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

    /**
     * Checks if goal completion alert should be shown.
     * Based on Angular's showGoalMetMessage logic.
     * @param currentWeight Current weight to check against goal
     * @return True if goal completion alert should be shown
     */
    override suspend fun shouldShowGoalCompletionAlert(currentWeight: Double): Boolean {
        // TODO: Implement goal completion alert logic
        // This would check:
        // 1. If alert hasn't been shown yet for this goal
        // 2. If current weight meets the goal criteria
        // 3. If not currently in setup process
        // Similar to Angular's showGoalMetMessage method
        return false
    }

    /**
     * Shows goal completion alert based on goal type.
     * Based on Angular's showGoalMetAlert and showGoalLeaveAlert methods.
     * @param currentWeight Current weight that triggered the alert
     */
    override suspend fun showGoalCompletionAlert(currentWeight: Double) {
        val currentGoal = getCurrentGoal() ?: return

        when (currentGoal.type.lowercase()) {
            "maintain" -> {
                // Show goal leave alert for maintain goals
                showGoalLeaveAlert()
            }
            "lose", "gain" -> {
                // Show goal met alert for lose/gain goals
                showGoalMetAlert()
            }
        }
    }

    /**
     * Gets the current goal or null if none is set.
     * @return Current goal or null
     */
    override suspend fun getCurrentGoal(): Goal? {
        return goalRepository.getCurrentGoal()
    }

    /**
     * Updates the goal status flow based on account data.
     * @param account The account to extract goal data from
     */
    private fun updateGoalStatusFromAccount(account: Account) {
        // TODO: Extract goal data from account and update _goalStatusFlow
        // This would need goal fields to be added to Account domain model
        // or retrieve from goal repository
    }

    /**
     * Shows goal met alert dialog.
     * Based on Angular's showGoalMetAlert method.
     */
    private suspend fun showGoalMetAlert() {
        dialogQueueService.enqueue(
            DialogModel.Confirm(
                title = "Congratulations!",
                message = "You've reached your goal! Would you like to set a new goal or maintain your current weight?",
                confirmText = "New Goal",
                cancelText = "Maintain",
                onConfirm = {
                    // TODO: Navigate to goal setting screen
                    AppLog.d(TAG, "User chose to set new goal")
                },
                onCancel = {
                    // Set maintain goal
                    // TODO: Set maintain goal at current weight
                    AppLog.d(TAG, "User chose to maintain current weight")
                }
            )
        )
    }

    /**
     * Shows goal leave alert dialog.
     * Based on Angular's showGoalLeaveAlert method.
     */
    private suspend fun showGoalLeaveAlert() {
        dialogQueueService.enqueue(
            DialogModel.Confirm(
                title = "Goal Change",
                message = "Your weight has changed from your maintain goal. Would you like to update your goal?",
                confirmText = "Yes",
                cancelText = "No",
                onConfirm = {
                    // TODO: Navigate to goal setting screen
                    AppLog.d(TAG, "User chose to update goal")
                }
            )
        )
    }
}

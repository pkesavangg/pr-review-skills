package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.GoalAlertDataStore
import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.goal.GoalData
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.goal.helper.GoalHelper
import com.dmdbrands.gurus.weight.features.goal.strings.GoalStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/**
 * Service implementation for goal operations.
 * Handles business logic for weight goal management.
 * Based on Angular goal.service.ts functionality.
 */
@Singleton
class GoalService
@Inject
constructor(
  private val goalRepository: IGoalRepository,
  private val connectivityObserver: IConnectivityObserver,
  private val dialogQueueService: IDialogQueueService,
  private val appNavigationService: IAppNavigationService,
  private val goalAlertDataStore: GoalAlertDataStore,
  private val accountRepository: IAccountRepository,
) : IGoalService {
  private val TAG = "GoalService"
  private var isShowingAlert = false

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
    wasMet: Boolean,
  ): Account? {
    return try {
      AppLog.d(TAG, "Updating goal: type=$goalType, goalWeight=$goalWeight, initialWeight=$initialWeight")
      null
      val goalData =
        GoalData(
          goalWeight = goalWeight,
          initialWeight = initialWeight,
          type = goalType,
          metPreviousGoal = if (wasMet) true else null,
        )

      val updatedAccount =
        if (isNetworkAvailable()) {
          // Online: Update via API and mark as synced in DB
          AppLog.d(TAG, "Network available - updating goal online")
          goalRepository.updateGoalSetting(goalData)
        } else {
          // Offline: Store locally and mark as unsynced for later sync
          AppLog.d(TAG, "Network unavailable - storing goal for offline sync")
          goalRepository.updateGoalSettingOffline(goalData)
        }

      // Update goal status flow
      updatedAccount?.let { account ->
        updateGoalStatusFromAccount(account)
      }

      updatedAccount
    } catch (e: Exception) {
      AppLog.e(TAG, "Error updating goal", e.toString())
      null
    }
  }

  /**
   * Gets the formatted goal type string for display.
   * Based on Angular's getFormattedGoalType method.
   * @param type The goal type ('lose', 'gain', 'maintain')
   * @return Formatted string for UI display
   */
  override fun getFormattedGoalType(type: String): String =
    when (type.lowercase()) {
      "lose" -> "Lose"
      "gain" -> "Gain"
      "maintain" -> "Maintain"
      else -> "Maintain"
    }

  /**
   * Calculates goal completion percentage.
   * Based on Angular's getPercentComplete method - exact implementation.
   * @param goal The goal to calculate percentage for
   * @param latest Latest weight entry to calculate against
   * @return Percentage completion (0-100) or null if calculation not possible
   */
  override fun getPercentComplete(
    goal: Goal,
    latest: Double,
  ): Int? {
    val goalWt = goal.goalWeight

    if (latest <= 0.0) return null // No valid weight data

    var percent = 0
    when (goal.type.lowercase()) {
      "lose" -> {
        percent = ((latest - goalWt) / (goal.initialWeight - goalWt) * 100).toInt()
        percent = 100 - floor(percent.toDouble()).toInt()
      }

      "gain" -> {
        percent = ((latest - goal.initialWeight) / (goalWt - goal.initialWeight) * 100).toInt()
        percent = floor(percent.toDouble()).toInt()
      }

      else -> return null // Maintain goals don't have percentage
    }

    return if (percent < 0) 0 else percent
  }

  /**
   * Shows goal completion alert based on goal type.
   * Based on Angular's showGoalMetMessage method.
   * @param currentWeight Current weight that triggered the alert
   */
  override suspend fun showGoalCompletionAlert(currentWeight: Double) {
    try {
      val currentGoal = getCurrentGoal().first() ?: return
      val account = accountRepository.getActiveAccount().first() ?: return
      val hasShownAlert = goalAlertDataStore.hasShownAlert(account.id)

      // Match Angular's conditions (removed bluetooth check for now)
      if (!isShowingAlert && !hasShownAlert) {
        val shouldShowAlert = when (currentGoal.type.lowercase()) {
          "gain" -> currentWeight >= currentGoal.goalWeight
          "lose" -> currentWeight <= currentGoal.goalWeight
          "maintain" -> currentWeight != currentGoal.goalWeight
          else -> false
        }

        if (shouldShowAlert) {
          // Set flag before showing dialog (matching Angular)
          goalAlertDataStore.setAlertShown(account.id, true)

          // Show appropriate dialog based on goal type
          if (currentGoal.type.lowercase() == "maintain") {
            showGoalLeaveAlert()
          } else {
            showGoalMetAlert()
          }
        }
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error showing goal met message", e.toString())
    }
  }

  private suspend fun handleGoalMet(setNewGoal: Boolean) {
    try {
      val account = accountRepository.getActiveAccount().first() ?: return
      // Reset goal alert flag
      goalAlertDataStore.setAlertShown(account.id, false)

      if (!setNewGoal) {
        // User chose maintain - update goal to maintain at current weight
        val currentGoal = getCurrentGoal().first() ?: return
        updateGoal(
          goalWeight = currentGoal.goalWeight,
          initialWeight = currentGoal.goalWeight, // Use goal weight as initial weight for maintain
          goalType = GoalType.MAINTAIN.value,
          wasMet = true,
        )
      }
      // If setNewGoal is true, we're already navigating to goal screen
    } catch (e: Exception) {
      AppLog.e(TAG, "Error handling goal met", e.toString())
    }
  }

  private suspend fun handleGoalLeave(updateGoal: Boolean) {
    if (updateGoal) {
      val currentGoal = getCurrentGoal().first() ?: return
      // Update goal with met status
      updateGoal(
        goalWeight = currentGoal.goalWeight,
        initialWeight = currentGoal.initialWeight,
        goalType = currentGoal.type,
        wasMet = true,
      )
    }
  }

  /**
   * Creates a goal for a newly created account during signup.
   * Converts display weights to stored format and determines the correct goal type.
   *
   * @param account The newly created account
   * @param goalType The selected goal type from signup form
   * @param startingWeight Current weight in display format
   * @param goalWeight Goal weight in display format
   * @return Updated account with goal settings or null if failed
   */
  override suspend fun createGoalForSignup(
    account: Account,
    goalType: String,
    startingWeight: Double,
    goalWeight: Double,
  ): Account? =
    try {
      AppLog.d(TAG, "Creating goal for signup: type=$goalType, current=$startingWeight, goal=$goalWeight")

      // Create Goal object and convert to stored format (LB) for API using helper
      val goal = GoalHelper.createGoal(
        startingWeight = startingWeight,
        goalWeight = goalWeight,
        goalType = goalType,
        fromUnit = account.weightUnit,
        toUnit = WeightUnit.LB, // We store weights in LB format
      )

      AppLog.d(
        TAG,
        "Goal settings: type=${goal.type}, current=${goal.initialWeight}, goal=${goal.goalWeight}",
      )

      // Update goal using converted values
      val updatedAccount = updateGoal(
        goalWeight = goal.goalWeight,
        initialWeight = goal.initialWeight,
        goalType = goal.type,
        wasMet = false, // New goals are never met initially
      )

      AppLog.i(
        TAG,
        "Goal created successfully for signup: type=${goal.type}, goal=${goal.goalWeight}, initial=${goal.initialWeight}",
      )
      updatedAccount
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to create goal during signup", e.toString())
      null
    }

  /**
   * Gets the current goal or null if none is set.
   * @return Current goal or null
   */
  override suspend fun getCurrentGoal(): Flow<Goal?> =
    combine(
      accountRepository.getActiveAccountWeightUnitFlow(),
      accountRepository.getActiveAccountWeightlessFlow(),
      goalRepository.getCurrentGoal(),
    ) { weightUnit, weightless, goal ->
      goal?.process(weightUnit, weightless)
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
  @OptIn(DelicateCoroutinesApi::class)
  private fun showGoalMetAlert() {
    isShowingAlert = true
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = GoalStrings.GoalMetTitle,
        message = GoalStrings.GoalMetMessage,
        confirmText = GoalStrings.SetNewGoalButton,
        cancelText = GoalStrings.MaintainButton,
        onConfirm = {
          dialogQueueService.dismissCurrent()
          isShowingAlert = false
          CoroutineScope(Dispatchers.IO).launch {
            appNavigationService.navigateTo(AppRoute.AccountSettings.Goal)
            handleGoalMet(setNewGoal = true)
          }
        },
        onCancel = {
          dialogQueueService.dismissCurrent()
          isShowingAlert = false
          CoroutineScope(Dispatchers.IO).launch {
            handleGoalMet(setNewGoal = false)
          }
        },
      ),
    )
  }

  /**
   * Shows goal leave alert dialog.
   * Based on Angular's showGoalLeaveAlert method.
   */
  private fun showGoalLeaveAlert() {
    isShowingAlert = true
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = GoalStrings.GoalLeaveTitle,
        message = GoalStrings.GoalLeaveMessage,
        confirmText = GoalStrings.UpdateGoalButton,
        cancelText = GoalStrings.KeepGoalButton,
        onConfirm = {
          dialogQueueService.dismissCurrent()
          isShowingAlert = false
          CoroutineScope(Dispatchers.IO).launch {
            appNavigationService.navigateTo(AppRoute.AccountSettings.Goal)
          }
        },
        onCancel = {
          dialogQueueService.dismissCurrent()
          isShowingAlert = false
          CoroutineScope(Dispatchers.IO).launch {
            handleGoalLeave(updateGoal = false)
          }
        },
      ),
    )
  }
}

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
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import com.dmdbrands.gurus.weight.core.di.ApplicationScope
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.goal.helper.GoalHelper
import com.dmdbrands.gurus.weight.features.goal.strings.GoalStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
import kotlin.math.round

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
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  appNavigationService: IAppNavigationService,
  private val goalAlertDataStore: GoalAlertDataStore,
  private val accountRepository: IAccountRepository,
  private val deviceService: IDeviceService,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService), IGoalService {
  private val TAG = "GoalService"
  private var isShowingAlert = false

  private fun convertTenthsBetweenUnits(
    weightTenths: Double,
    fromUnit: WeightUnit,
    toUnit: WeightUnit,
  ): Double {
    val displayWeight = weightTenths / 10.0
    val convertedDisplayWeight = GoalHelper.convertWeight(displayWeight, fromUnit, toUnit)
    return round(convertedDisplayWeight * 10)
  }

  private val _goalStatusFlow = MutableStateFlow<Goal?>(null)
  override val goalStatusFlow: Flow<Goal?> = _goalStatusFlow.asStateFlow()
  private var account: Account? = null
  init {
    CoroutineScope(ioDispatcher).launch {
      accountRepository.getActiveAccount().collect {
        if (it != null) {
          account = it
        }
      }
    }
  }

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
      val goalData =
        GoalData(
          goalWeight = goalWeight,
          initialWeight = initialWeight,
          type = goalType,
          metPreviousGoal = wasMet,
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
      goalAlertDataStore.setAlertShown(account?.id ?: "" , false)
      updatedAccount
    } catch (e: Exception) {
      AppLog.e(TAG, "Error updating goal", e)
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
      val account = accountRepository.getActiveAccount().first() ?: return
      val convertedCurrentWeight =  convertWeight(currentWeight, WeightUnit.LB, account.weightUnit)
      val currentGoalWeight = convertWeight(account.goalWeight ?: 0.0, WeightUnit.LB, account.weightUnit)
      val currentGoal = getCurrentGoal().first() ?: return
      val hasShownAlert = goalAlertDataStore.hasShownAlert(account.id)
      val isSetupInProgress = deviceService.isSetupInProgress()

      // Match Angular's conditions: don't show alerts during setup
      if (!isShowingAlert && !hasShownAlert && !isSetupInProgress) {
        val shouldShowAlert = when (currentGoal.type.lowercase()) {
          "gain" -> convertedCurrentWeight >= currentGoalWeight
          "lose" -> convertedCurrentWeight <= currentGoalWeight
          "maintain" -> convertedCurrentWeight != currentGoalWeight
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
      } else if (isSetupInProgress) {
        AppLog.d(TAG, "Skipping goal alert - setup in progress")
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error showing goal met message", e)
    }
  }

  private suspend fun handleGoalMet(setNewGoal: Boolean) {
    try {
      val account = accountRepository.getActiveAccount().first() ?: return
      // Reset goal alert flag
      goalAlertDataStore.setAlertShown(account.id, false)

      if (setNewGoal) {
        // User chose maintain - update goal to maintain at current weight
        val convertedGoalWeight =
          convertTenthsBetweenUnits(
            weightTenths = account.goalWeight ?: 0.0,
            fromUnit = account.weightUnit,
            toUnit = WeightUnit.LB,
          )
        updateGoal(
          goalWeight = convertedGoalWeight,
          initialWeight = convertedGoalWeight, // Use goal weight as initial weight for maintain
          goalType = GoalType.MAINTAIN.value,
          wasMet = true,
        )
      }
      // If setNewGoal is true, we're already navigating to goal screen
    } catch (e: Exception) {
      AppLog.e(TAG, "Error handling goal met", e)
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
      AppLog.e(TAG, "Failed to create goal during signup", e)
      null
    }

  /**
   * Checks if the goal card (Set Goal popup) should be shown to the user.
   * Based on Angular's checkGoalCard method - shows popup if:
   * - User has no goal set (goalType is null)
   * - User has at least 3 entries
   * - Goal card popup hasn't been shown before for this account
   */
  override suspend fun checkGoalCard() {
    try {
      AppLog.d(TAG, "Checking goal card conditions")
      // Get current account
      val account = accountRepository.getActiveAccount().first()
      if (account == null) {
        AppLog.d(TAG, "No active account found, skipping goal card check")
        return
      }
      val currentGoal = getCurrentGoal().first()
      val isPopupShowed = goalAlertDataStore.getGoalCardValue(account.id)
      if (isPopupShowed != null) {
        AppLog.d(TAG, "Goal card already shown for account ${account.id}")
        return
      }
      AppLog.i(TAG, "All conditions met - showing goal card popup for account ${account.id}")
      // Mark popup as shown first (like Angular implementation)
      if(
        account.goalType == null && !deviceService.isSetupInProgress()
      ) {
        showSetGoalPopup()
        goalAlertDataStore.setGoalCardValue(account.id, "true")
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error checking goal card", e)
    }
  }

  /**
   * Shows the Set Goal popup using the dialog queue system.
   * Navigates to goal screen when user confirms.
   */
  private fun showSetGoalPopup() {
    AppLog.d(TAG, "Showing Set Goal popup")

    dialogQueueService.enqueue(
      DialogModel.Custom(
        contentKey = DialogType.SetGoalPopup,
        params = mapOf(
          "onSetGoal" to {
            AppLog.d(TAG, "User confirmed Set Goal popup - navigating to goal screen")
            appScope.launch(Dispatchers.Main) {
              appNavigationService.navigateTo(AppRoute.AccountSettings.Goal)
            }
            dialogQueueService.dismissCurrent()
          },
        ),
        onDismiss = {
          AppLog.d(TAG, "Set Goal popup dismissed")
          dialogQueueService.dismissCurrent()
        },
        dismissOnBackPress = true
      ),
    )
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
   * Gets the current goal immediately without suspension.
   * @return Current goal or null
   */
  override fun getCurrentGoalSync(): Goal? = _goalStatusFlow.value


  /**
   * Shows goal met alert dialog.
   * Based on Angular's showGoalMetAlert method.
   */
  private fun showGoalMetAlert() {
    isShowingAlert = true
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = GoalStrings.GoalMetTitle,
        message = GoalStrings.GoalMetMessage,
        confirmText = GoalStrings.MaintainButton,
        cancelText = GoalStrings.SetNewGoalButton,
        onConfirm = {
          dialogQueueService.dismissCurrent()
          appScope.launch(Dispatchers.Main) {
            handleGoalMet(true)
          }
          isShowingAlert = false
        },
        onCancel = {
          dialogQueueService.dismissCurrent()
          isShowingAlert = false
          appScope.launch(Dispatchers.Main) {
            appNavigationService.navigateTo(AppRoute.AccountSettings.Goal)
            handleGoalMet(false)
          }
        },
        onDismiss = {
          dialogQueueService.dismissCurrent()
          isShowingAlert = false
          appScope.launch(Dispatchers.Main) {
            handleGoalMet(false)
          }
        }
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
        message = GoalStrings.GoalLeaveMessage,
        confirmText = GoalStrings.YesButton,
        cancelText = GoalStrings.NoButton,
        onConfirm = {
          dialogQueueService.dismissCurrent()
          isShowingAlert = false
          appScope.launch(Dispatchers.Main) {
            appNavigationService.navigateTo(AppRoute.AccountSettings.Goal)
          }
        },
        onCancel = {
          dialogQueueService.dismissCurrent()
          isShowingAlert = false
        },
      ),
    )
  }
}

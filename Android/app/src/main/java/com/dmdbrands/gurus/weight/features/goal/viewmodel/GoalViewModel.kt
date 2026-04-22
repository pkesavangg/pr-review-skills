package com.dmdbrands.gurus.weight.features.goal.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.features.common.ScaleProfileConstants
import com.dmdbrands.gurus.weight.features.common.helper.AccountHelper.isMetricUnit
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.goal.helper.GoalHelper.toGoal
import com.dmdbrands.gurus.weight.features.goal.model.GoalFormControls
import com.dmdbrands.gurus.weight.features.goal.model.GoalIntent
import com.dmdbrands.gurus.weight.features.goal.model.GoalReducer
import com.dmdbrands.gurus.weight.features.goal.model.GoalState
import com.dmdbrands.gurus.weight.features.goal.strings.GoalStrings
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUserProfile
import com.greatergoods.blewrapper.GGDeviceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.round

/**
 * ViewModel for the Goal screen. Handles form state, validation, goal logic, and navigation.
 * @property dialogUtility Service for showing dialogs.
 * @property accountService Service for account operations.
 * @property goalService Service for goal operations.
 */
@HiltViewModel
class GoalViewModel
@Inject
constructor(
  private val dialogUtility: IDialogUtility,
  private val accountService: IAccountService,
  private val goalService: IGoalService,
  private val entryService: IEntryService,
  private val entryReadService: IEntryReadService,
  private val ggDeviceService: GGDeviceService
) : BaseIntentViewModel<GoalState, GoalIntent>(
  reducer = GoalReducer(),
) {
  private val tag = "GoalViewModel"

  override fun provideInitialState(): GoalState {
    // Always initialize with LOSE_GAIN
    // Default to LB unit - will be updated when account is loaded
    return GoalState(
      form = FormGroup(
        GoalFormControls.createWithWeightMatchValidation(
          weightUnit = WeightUnit.LB // Default to LB, will be updated when account loads
        )
      ),  // Use weight match validation
    )
  }

  init {
    // Load current account data
    viewModelScope.launch {
      accountService.activeAccountFlow.collect { account ->
        handleIntent(GoalIntent.UpdateAccount(account))
        account?.let { updateStateWithAccount(it) }
      }
    }

    // Load latest weight data for milestone display
    viewModelScope.launch {
      entryReadService.latestEntry().collect { latestEntry ->
        val latestWeight =
          when (latestEntry) {
            is ScaleEntry -> latestEntry.scale.scaleEntry.weight
            else -> null
          }
        handleIntent(GoalIntent.UpdateLatestWeight(latestWeight))
      }
    }
  }

  private fun updateStateWithAccount(currentAccount: Account) {
    val isMetric = currentAccount.isMetricUnit()
    val targetUnit = if (isMetric) WeightUnit.KG else WeightUnit.LB

    // Default to LOSE_GAIN if no goal type is set
    val goalTypeString = currentAccount.goalType ?: GoalType.LOSE_GAIN.value
    val goalType = GoalType.entries.find { it.value == goalTypeString } ?: GoalType.LOSE_GAIN
    // Get current weights and create a Goal object
    val goal = Goal(
      goalWeight = currentAccount.goalWeight ?: 0.0,
      initialWeight = currentAccount.initialWeight,
      type = goalType.value,
    ).process(targetUnit, null) // Process with target unit, no weightless needed for goals
    calculateGoalPercentage(currentAccount, state.value.latestWeight)
    // Create form controls with weight match validation
    // Pass weightUnit and initial values directly, following EntryReducer pattern
    // This ensures form is not marked as dirty initially
    val initialStartingWeight = if(goal.initialWeight.toString() == "0.0") "" else goal.initialWeight.toInt().toString()
    val initialGoalWeight = if(goal.goalWeight.toString() == "0.0") "" else goal.goalWeight.toInt().toString()

    val goalFormControls = GoalFormControls.createWithWeightMatchValidation(
      goalType = goalType,
      weightUnit = targetUnit,
      initialStartingWeight = initialStartingWeight,
      initialGoalWeight = initialGoalWeight,
    )

    val newState =
      GoalState(
        form = FormGroup(goalFormControls),
        account = currentAccount,
        latestWeight = state.value.latestWeight, // Preserve existing latestWeight
      )
    _state.value = newState
  }

  /**
   * Handles incoming intents and updates the state accordingly.
   * @param intent The intent to handle.
   */
  override fun handleIntent(intent: GoalIntent) {
    super.handleIntent(intent)
    when (intent) {
      is GoalIntent.Submit -> onSubmit()
      is GoalIntent.OnBack -> onBack()
      is GoalIntent.Success -> onSuccess()
      is GoalIntent.HandleGoalMet -> onHandleGoalMet(intent.setNewGoal)
      is GoalIntent.HandleGoalLeave -> onHandleGoalLeave(intent.updateGoal)
      is GoalIntent.UpdateAccount,
      is GoalIntent.UpdateLatestWeight,
        -> {
        // These intents are handled by the reducer
      }

      else -> null
    }
  }

  /**
   * Handles the goal form submission. Validates the form, shows loading, and attempts to save.
   */
  private fun onSubmit() {
    dialogQueueService.showLoader(
      message = GoalStrings.LoaderMessage,
    )

    val account = state.value.account ?: return
    val controls = state.value.form.controls

    // If MAINTAIN mode, always use the latest entry as the initial weight.
    // Starting weight input is hidden for maintain, so it may contain stale account data.
    if (controls.goalType.value == GoalType.MAINTAIN.value) {
      val currentWeight = state.value.latestWeight
      if (currentWeight != null && currentWeight > 0.0) {
        // Form stores weights as tenths (e.g. 152.1 -> "1521")
        val currentWeightTenths = round(currentWeight * 10).toInt().toString()
        controls.startingWeight.onValueChange(currentWeightTenths)
      }
    }

    val goal = controls.toGoal(
      fromUnit = account.weightUnit,
      toUnit = WeightUnit.LB,
    )

    AppLog.d(tag, "Goal settings: type=${goal.type}, current=${goal.initialWeight}, goal=${goal.goalWeight}")
    viewModelScope.launch {
      try {
        goalService.updateGoal(
          goalWeight = goal.goalWeight,
          initialWeight = goal.initialWeight,
          goalType = goal.type,
          wasMet = account.metPreviousGoal ?: false,
        )
        val scaleResult = updateR4Profile(account.toGGBTUserProfile())
        when (scaleResult) {
          GGUserActionResponseType.USER_SELECTION_IN_PROGRESS -> {
            dialogQueueService.enqueue(
              DialogModel.Alert(
                title = AppPopupStrings.R4ProfileUpdatePending.Title,
                message = AppPopupStrings.R4ProfileUpdatePending.Message,
                onDismiss = { dialogQueueService.dismissCurrent() },
              ),
            )
          }

          GGUserActionResponseType.CREATION_COMPLETED, GGUserActionResponseType.CREATION_FAILED, GGUserActionResponseType.UPDATE_COMPLETED -> {
            dialogQueueService.dismissLoader()
            handleIntent(GoalIntent.Success)
          }

          else -> {
            dialogQueueService.dismissLoader()
            handleIntent(GoalIntent.Success)
          }
        }
        AppLog.i(tag, "Goal settings saved successfully")
      } catch (e: Exception) {
        dialogQueueService.dismissLoader()
        handleIntent(GoalIntent.Error(GoalStrings.SaveErrorMessage))
        AppLog.e(tag, "Failed to save goal settings", e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  /**
   * Handles back navigation with unsaved changes check.
   */
  private fun onBack() {
    val hasChanges = state.value.form.isDirty

    if (hasChanges) {
      dialogQueueService.enqueue(
        DialogModel.Confirm(
          title = GoalStrings.UnsavedChangesTitle,
          message = GoalStrings.UnsavedChangesMessage,
          confirmText = GoalStrings.SaveButton,
          cancelText = GoalStrings.DiscardButton,
          onConfirm = {
            navigateBack()
            dialogQueueService.dismissCurrent()
          },
          onCancel = {
            dialogQueueService.dismissCurrent()
          },
        ),
      )
    } else {
      navigateBack()
    }
  }

  private fun onSuccess() {
    viewModelScope.launch {
      dialogQueueService.showToast(
        Toast.Simple(
          title = GoalStrings.SuccessTitle,
          message = GoalStrings.SuccessMessage,
          action = null,
        ),
      )
      navigationService.navigateBack(null)
      AppLog.i(tag, "Goal updated successfully")
    }
  }

  /**
   * Handles goal met dialog response.
   */
  private fun onHandleGoalMet(setNewGoal: Boolean) {
    AppLog.d(tag, "Goal met dialog response: setNewGoal=$setNewGoal")
    if (setNewGoal) {
      // Reset form for new goal with LOSE_GAIN as default
      val newFormControls = GoalFormControls.createWithWeightMatchValidation(GoalType.LOSE_GAIN)
      val newState =
        state.value.copy(
          form = FormGroup(newFormControls),
        )
      _state.value = newState
    } else {
      // Switch to maintain mode
      handleIntent(GoalIntent.ChangeGoalType(GoalType.MAINTAIN))
    }
  }

  /**
   * Handles goal leave dialog response.
   */
  private fun onHandleGoalLeave(updateGoal: Boolean) {
    AppLog.d(tag, "Goal leave dialog response: updateGoal=$updateGoal")
    if (updateGoal) {
      // For now, just log that the user wants to update the goal
      // In a full implementation, you would update the current weight based on the latest reading
      AppLog.i(tag, "User chose to update goal based on weight change")
    }
  }

  /**
   * Calculates goal percentage using GoalService, following Angular implementation.
   * @param account The account containing goal data
   * @param latestWeight The latest weight entry to calculate against
   * @return Calculated goal percentage or null if calculation not possible
   */
  private fun calculateGoalPercentage(
    account: Account,
    latestWeight: Double?,
  ): Double? {
    // Only calculate for lose/gain goals, maintain goals don't have percentage
    val goalType = account.goalType?.lowercase()
    if (goalType == "maintain") return null

    val initialWeight = account.initialWeight
    val goalWeight = account.goalWeight ?: return null
    val latest = latestWeight ?: return null

    // Create a Goal object from account data to use with GoalService
    val goal =
      Goal(
        goalWeight = goalWeight,
        initialWeight = initialWeight,
        type = goalType ?: "maintain",
      )

    // Use GoalService to calculate percentage, just like Angular
    return goalService.getPercentComplete(goal, latest)?.toDouble()
  }

  /**
   * Navigates back to the previous screen.
   */
  private fun navigateBack() {
    viewModelScope.launch {
      try {
        navigationService.navigateBack()
      } catch (e: Exception) {
        AppLog.e(tag, "Failed to navigate back from goal screen", e)
      }
    }
  }

  private suspend fun updateR4Profile(profile: GGBTUserProfile): GGUserActionResponseType {
    val result = CompletableDeferred<GGUserActionResponseType>()
    try {
      ggDeviceService.updateProfile(
        profile,
      ) { responseType ->
        result.complete(responseType)
      }
    } catch (e: Exception) {
      AppLog.d(tag, "updateR4Profile - Error updating profile to scale: ${e.message}")
      result.complete(GGUserActionResponseType.EXCEPTION_ENCOUNTERED)
    }
    return withTimeoutOrNull(ScaleProfileConstants.SCALE_PROFILE_UPDATE_TIMEOUT_MS) { result.await() }
      ?: run {
        AppLog.d(tag, "updateR4Profile - Timeout or no callback from scale; dismissing loader")
        GGUserActionResponseType.EXCEPTION_ENCOUNTERED
      }
  }
}

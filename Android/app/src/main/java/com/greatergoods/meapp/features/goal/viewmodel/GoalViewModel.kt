package com.greatergoods.meapp.features.goal.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.enums.GoalType
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.model.goal.Goal
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.domain.services.IGoalService
import com.greatergoods.meapp.features.common.helper.AccountHelper.convertDisplayWeightToStored
import com.greatergoods.meapp.features.common.helper.AccountHelper.convertStoredWeightToDisplay
import com.greatergoods.meapp.features.common.helper.AccountHelper.isMetricUnit
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.goal.model.GoalFormControls
import com.greatergoods.meapp.features.goal.model.GoalIntent
import com.greatergoods.meapp.features.goal.model.GoalReducer
import com.greatergoods.meapp.features.goal.model.GoalState
import com.greatergoods.meapp.features.goal.strings.GoalStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    ) : BaseIntentViewModel<GoalState, GoalIntent>(
            reducer = GoalReducer(),
        ) {
        private val tag = "GoalViewModel"

        override fun provideInitialState(): GoalState {
            // Initialize with defaults since we'll load data in init block
            return GoalState(
                form = FormGroup(GoalFormControls.create()),
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
                entryService.latestEntry.collect { latestEntry ->
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
            val goalTypeString = currentAccount.goalType ?: GoalType.MAINTAIN.value
            val goalType = GoalType.entries.find { it.value == goalTypeString } ?: GoalType.MAINTAIN

            // Get current weights (these should be in stored format from database)
            val initialWeight = currentAccount.initialWeight
            val goalWeight = currentAccount.goalWeight ?: 0.0

            // Calculate goal percentage using GoalService, just like Angular
            val goalPercent = calculateGoalPercentage(currentAccount, state.value.latestWeight)
            // Convert from stored format to display format using AccountHelper
            val displayCurrentWeight = currentAccount.convertStoredWeightToDisplay(initialWeight)
            val displayGoalWeight = currentAccount.convertStoredWeightToDisplay(goalWeight)

            // For maintain goal, current weight input is hidden, so no validation needed
            val currentWeightValidators =
                if (goalType == GoalType.LOSE_GAIN) {
                    listOf(FormValidations.required(), FormValidations.weightValidator())
                } else {
                    emptyList() // No validation for hidden field in maintain mode
                }

            val formattedCurrentWeight =
                if (displayCurrentWeight > 0) {
                    String.format("%.1f", displayCurrentWeight)
                } else {
                    ""
                }
            val formattedGoalWeight =
                if (displayGoalWeight > 0) {
                    String.format("%.1f", displayGoalWeight)
                } else {
                    ""
                }
            val newState =
                GoalState(
                    form =
                        FormGroup(
                            GoalFormControls(
                                goalType =
                                    FormControl.create(
                                        initialValue = goalType.value,
                                        validators = listOf(FormValidations.required()),
                                    ),
                                currentWeight =
                                    FormControl.create(
                                        initialValue = formattedCurrentWeight,
                                        validators = currentWeightValidators,
                                    ),
                                goalWeight =
                                    FormControl.create(
                                        initialValue = formattedGoalWeight,
                                        validators =
                                            listOf(
                                                FormValidations.required(),
                                                FormValidations.weightValidator(),
                                            ),
                                    ),
                            ),
                        ),
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
                is GoalIntent.OpenHelpModal -> openHelpModal()
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
            val currentWeightDisplay =
                if (state.value.form.controls.currentWeight.value ==
                    ""
                ) {
                    0.0
                } else {
                    state.value.form.controls.currentWeight.value
                        .toDouble()
                }
            val goalWeightDisplay =
                state.value.form.controls.goalWeight.value
                    .toDouble()
            val goalType = state.value.form.controls.goalType.value

            AppLog.d(tag, "Goal settings: type=$goalType, current=$currentWeightDisplay, goal=$goalWeightDisplay")

            viewModelScope.launch {
                try {
                    // Convert display weights to stored format using AccountHelper
                    val goalWeightStored = account.convertDisplayWeightToStored(goalWeightDisplay)
                    val currentWeightStored = account.convertDisplayWeightToStored(currentWeightDisplay)

                    // Determine specific goal type based on weight comparison
                    val specificGoalType =
                        if (goalType == GoalType.MAINTAIN.value) {
                            "maintain"
                        } else {
                            // For lose/gain, determine based on goal vs current weight comparison
                            if (goalWeightStored <= currentWeightStored) "lose" else "gain"
                        }

                    goalService.updateGoal(
                        goalWeight = goalWeightStored,
                        initialWeight = currentWeightStored,
                        goalType = specificGoalType,
                        wasMet = account.metPreviousGoal ?: false,
                    )

                    handleIntent(GoalIntent.Success)
                    AppLog.i(tag, "Goal settings saved successfully")
                } catch (e: Exception) {
                    handleIntent(GoalIntent.Error(GoalStrings.SaveErrorMessage))
                    AppLog.e(tag, "Failed to save goal settings", e.toString())
                } finally {
                    dialogQueueService.dismissLoader()
                }
            }
        }

        /**
         * Opens the Help modal with goal information.
         */
        private fun openHelpModal() {
            AppLog.d(tag, "Opening goal help modal")
            dialogQueueService.enqueue(
                DialogModel.Alert(
                    title = GoalStrings.GoalInfoTitle,
                    message = GoalStrings.GoalInfoMessage,
                    onDismiss = {},
                ),
            )
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
                    Toast(
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
                // Reset form for new goal with current metric preference
                val newFormControls = GoalFormControls.create()
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
                    AppLog.e(tag, "Failed to navigate back from goal screen", e.toString())
                }
            }
        }
    }

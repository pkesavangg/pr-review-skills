package com.greatergoods.meapp.features.goal.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.shared.utilities.ConversionTools
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogUtility
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.domain.services.IGoalService
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
import com.greatergoods.meapp.features.signup.model.GoalType
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
            goalType = GoalType.MAINTAIN,
            weightUnit = "lbs",
            isMetric = false,
            hasToggleChanged = false,
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
                val latestWeight = when (latestEntry) {
                    is ScaleEntry -> latestEntry.scale.scaleEntry.weight
                    else -> null
                }
                handleIntent(GoalIntent.UpdateLatestWeight(latestWeight))
            }
        }
    }

    private fun updateStateWithAccount(currentAccount: Account) {
        val isMetric = currentAccount.weightUnit?.value == "kg"
        val goalTypeString = currentAccount.goalType ?: GoalType.MAINTAIN.value
        val goalType = GoalType.entries.find { it.value == goalTypeString } ?: GoalType.MAINTAIN

        // Get current weights (these should be in stored format from database)
        val initialWeight = currentAccount.initialWeight ?: 0.0
        val goalWeight = currentAccount.goalWeight ?: 0.0
        val metPreviousGoal = currentAccount.metPreviousGoal ?: false
        val goalPercent = currentAccount.goalPercent

        // Convert from stored format to display format
        val displayCurrentWeight = ConversionTools.convertStoredToDisplay(initialWeight, isMetric)
        val displayGoalWeight = ConversionTools.convertStoredToDisplay(goalWeight, isMetric)

        // Create form controls with proper initial values and validators
        val goalWeightValidators = if (goalType == GoalType.LOSE_GAIN) {
            listOf(FormValidations.required(), FormValidations.weightValidator())
        } else {
            listOf(FormValidations.weightValidator())
        }

        // For maintain goal, current weight input is disabled, so no required validation
        val currentWeightValidators = if (goalType == GoalType.LOSE_GAIN) {
            listOf(FormValidations.required(), FormValidations.weightValidator())
        } else {
            listOf(FormValidations.weightValidator()) // No required validation for disabled field
        }

        val formattedCurrentWeight = if (displayCurrentWeight > 0) {
            String.format("%.1f", displayCurrentWeight)
        } else {
            ""
        }

        val formattedGoalWeight = if (displayGoalWeight > 0) {
            String.format("%.1f", displayGoalWeight)
        } else {
            ""
        }

        val newState = GoalState(
            form = FormGroup(
                GoalFormControls(
                    goalType = FormControl.create(
                        initialValue = goalType.value,
                        validators = listOf(FormValidations.required()),
                    ),
                    currentWeight = FormControl.create(
                        initialValue = formattedCurrentWeight,
                        validators = currentWeightValidators,
                    ),
                    goalWeight = FormControl.create(
                        initialValue = formattedGoalWeight,
                        validators = goalWeightValidators,
                    ),
                    useMetric = FormControl.create(
                        initialValue = isMetric,
                        validators = emptyList(),
                    ),
                ),
            ),
            goalType = goalType,
            weightUnit = if (isMetric) "kg" else "lbs",
            isMetric = isMetric,
            goalPercent = goalPercent,
            showMetPreviousGoal = metPreviousGoal,
            account = currentAccount,
            latestWeight = state.value.latestWeight, // Preserve existing latestWeight
            metPreviousGoal = metPreviousGoal,
            hasToggleChanged = false, // Initialize as false
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
            is GoalIntent.ChangeGoalType -> onChangeGoalType(intent.goalType)
            is GoalIntent.OpenHelpModal -> openHelpModal()
            is GoalIntent.OnBack -> onBack()
            is GoalIntent.Success -> onSuccess()
            is GoalIntent.HandleGoalMet -> onHandleGoalMet(intent.setNewGoal)
            is GoalIntent.HandleGoalLeave -> onHandleGoalLeave(intent.updateGoal)
            is GoalIntent.ToggleMetPreviousGoal -> {
                AppLog.d(tag, "Toggling met previous goal flag")
            }
            is GoalIntent.UpdateAccount,
            is GoalIntent.UpdateLatestWeight -> {
                // These intents are handled by the reducer
            }
            else -> null
        }
    }

    /**
     * Handles goal type change.
     */
    private fun onChangeGoalType(goalType: GoalType) {
        AppLog.d(tag, "Changing goal type to: $goalType")
        handleIntent(GoalIntent.ChangeGoalType(goalType))
    }



    /**
     * Handles the goal form submission. Validates the form, shows loading, and attempts to save.
     */
    private fun onSubmit() {
        dialogQueueService.showLoader(
            message = GoalStrings.LoaderMessage,
        )

        val currentWeightDisplay = if(state.value.form.controls.currentWeight.value == "") 0.0 else state.value.form.controls.currentWeight.value.toDouble()
        val goalWeightDisplay = state.value.form.controls.goalWeight.value
        val goalType = state.value.goalType
        val isMetric = state.value.isMetric
        AppLog.d(tag, "Goal settings: type=$goalType, current=$currentWeightDisplay, goal=$goalWeightDisplay")
        viewModelScope.launch {
            try {
                // Convert form values directly like Angular implementation (both as strings from form)
                val goalWeightStored = ConversionTools.convertDisplayToStored(
                    display = goalWeightDisplay.toDouble(),
                    isMetric = isMetric
                ).toInt()

                val currentWeightStored = ConversionTools.convertDisplayToStored(
                    display = currentWeightDisplay.toDouble(),
                    isMetric = isMetric
                ).toInt()

                // Determine the specific goal type based on Angular logic
                val specificGoalType = if (goalType == GoalType.MAINTAIN) {
                    "maintain"
                } else {
                    // For lose/gain, determine based on goal vs initial weight comparison
                    if (goalWeightStored <= currentWeightStored) "lose" else "gain"
                }

                goalService.updateGoal(
                    goalWeight = goalWeightStored.toDouble(),
                    initialWeight = currentWeightStored.toDouble(),
                    goalType = specificGoalType,
                    wasMet = state.value.showMetPreviousGoal,
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
        val hasChanges = state.value.form.isDirty || state.value.hasToggleChanged

        if (hasChanges) {
            dialogQueueService.enqueue(
                DialogModel.Confirm(
                    title = GoalStrings.UnsavedChangesTitle,
                    message = GoalStrings.UnsavedChangesMessage,
                    confirmText = GoalStrings.SaveButton,
                    cancelText = GoalStrings.DiscardButton,
                    onConfirm = {
                        onSubmit()
                    },
                    onCancel = {
                        navigateBack()
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
            val currentMetric = state.value.isMetric
            val newFormControls = GoalFormControls.create()
            val updatedUseMetricControl = FormControl.create(
                initialValue = currentMetric,
                validators = emptyList(),
            )
            val updatedControls = newFormControls.copy(useMetric = updatedUseMetricControl)

            val newState = state.value.copy(
                form = FormGroup(updatedControls),
                goalType = GoalType.LOSE_GAIN,
                showMetPreviousGoal = true,
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

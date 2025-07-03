package com.greatergoods.meapp.features.goal.model

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.signup.model.GoalType

/**
 * Controls for Goal form.
 */
data class GoalFormControls(
    val goalType: FormControl<String>,
    val currentWeight: FormControl<String>,
    val goalWeight: FormControl<String>,
) {
    companion object {
        fun create(goalType: GoalType = GoalType.LOSE_GAIN) = GoalFormControls(
            goalType = FormControl.create(
                initialValue = goalType.value,
                validators = listOf(FormValidations.required()),
            ),
            currentWeight = FormControl.create(
                initialValue = "0.0",
                validators = if (goalType == GoalType.LOSE_GAIN) {
                    listOf(FormValidations.required(), FormValidations.weightValidator())
                } else {
                    listOf(FormValidations.weightValidator()) // No required validation for disabled field
                },
            ),
            goalWeight = FormControl.create(
                initialValue = "0",
                validators = listOf(
                    FormValidations.required(),
                    FormValidations.weightValidator(),
                ),
            )
        )
    }
}

/**
 * State for Goal screen, including form group and UI state.
 * @property form The form group containing goal controls.
 * @property isLoading Whether the goal update process is ongoing.
 * @property error Error message to display, if any.
 * @property account The current account with all goal information and weight conversions via AccountHelper.
 * @property latestWeight The latest weight reading for milestone display.
 */
data class GoalState(
    val form: FormGroup<GoalFormControls>,
    val isLoading: Boolean = false,
    val error: String? = null,
    val account: Account? = null,
    val latestWeight: Double? = null,
    ) : IReducer.State

/**
 * Intents for Goal screen actions.
 */
sealed class GoalIntent : IReducer.Intent {
    /** Trigger goal form submission. */
    object Submit : GoalIntent()

    /** Change goal type between Maintain and LoseGain. */
    data class ChangeGoalType(
        val goalType: GoalType,
    ) : GoalIntent()

    /** Open help modal. */
    object OpenHelpModal : GoalIntent()

    /** Navigate back. */
    object OnBack : GoalIntent()

    /** Show an error message. */
    data class Error(
        val message: String,
    ) : GoalIntent()

    /** Update the form state. */
    data class UpdateForm(
        val form: FormGroup<GoalFormControls>,
    ) : GoalIntent()

    /** Goal update was successful. */
    object Success : GoalIntent()

    /** Handle goal met dialog response. */
    data class HandleGoalMet(
        val setNewGoal: Boolean,
    ) : GoalIntent()

    /** Handle goal leave dialog response. */
    data class HandleGoalLeave(
        val updateGoal: Boolean,
    ) : GoalIntent()

    /** Update account information. */
    data class UpdateAccount(
        val account: Account?,
    ) : GoalIntent()

    /** Update latest weight. */
    data class UpdateLatestWeight(
        val weight: Double?,
    ) : GoalIntent()

    /** Toggle met previous goal flag. */
    object ToggleMetPreviousGoal : GoalIntent()
}

/**
 * Reducer for Goal screen state transitions.
 */
class GoalReducer : IReducer<GoalState, GoalIntent> {
    /**
     * Reduces the current state and intent to a new state.
     * @param state The current state.
     * @param intent The intent/action to handle.
     * @return The new state after applying the intent.
     */
    override fun reduce(
        state: GoalState,
        intent: GoalIntent,
    ): GoalState =
        when (intent) {
            is GoalIntent.Submit -> {
                state.copy(isLoading = true, error = null)
            }

            is GoalIntent.ChangeGoalType -> {
                // Update form validators based on goal type
                val goalWeightValidators = if (intent.goalType == GoalType.LOSE_GAIN) {
                    listOf(FormValidations.required(), FormValidations.weightValidator())
                } else {
                    listOf(FormValidations.weightValidator())
                }

                // For maintain goal, current weight input is hidden, so no validation needed
                val currentWeightValidators = if (intent.goalType == GoalType.LOSE_GAIN) {
                    listOf(FormValidations.required(), FormValidations.weightValidator())
                } else {
                    emptyList() // No validation for hidden field in maintain mode
                }

                val updatedGoalTypeControl = FormControl.create(
                    initialValue = intent.goalType.value,
                    validators = listOf(FormValidations.required()),
                )

                val updatedCurrentWeightControl = FormControl.create(
                    initialValue = state.form.controls.currentWeight.value,
                    validators = currentWeightValidators,
                )

                val updatedGoalWeightControl = FormControl.create(
                    initialValue = state.form.controls.goalWeight.value,
                    validators = goalWeightValidators,
                )

                val updatedControls = state.form.controls.copy(
                    goalType = updatedGoalTypeControl,
                    currentWeight = updatedCurrentWeightControl,
                    goalWeight = updatedGoalWeightControl,
                )
                val updatedForm = FormGroup(updatedControls)

                state.copy(
                    form = updatedForm,
                )
            }

            is GoalIntent.OpenHelpModal -> {
                state.copy(isLoading = false, error = null)
            }

            is GoalIntent.Error -> {
                state.copy(isLoading = false, error = intent.message)
            }

            is GoalIntent.UpdateForm -> {
                state.copy(form = intent.form)
            }

            is GoalIntent.Success -> {
                state.copy(isLoading = false, error = null)
            }

            is GoalIntent.OnBack -> {
                state.copy(isLoading = false, error = null)
            }

            is GoalIntent.HandleGoalMet -> {
                state.copy(isLoading = false, error = null)
            }

            is GoalIntent.HandleGoalLeave -> {
                state.copy(isLoading = false, error = null)
            }

            is GoalIntent.UpdateAccount -> {
                state.copy(
                    account = intent.account,
                )
            }

            is GoalIntent.UpdateLatestWeight -> {
                state.copy(latestWeight = intent.weight)
            }

            is GoalIntent.ToggleMetPreviousGoal -> {
                state.copy()
            }
        }
}

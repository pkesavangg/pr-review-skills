package com.dmdbrands.gurus.weight.features.goal.model

import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.helper.form.Validator
import androidx.compose.runtime.Stable

/**
 * Controls for Goal form.
 */
data class GoalFormControls(
  val goalType: FormControl<String>,
  val startingWeight: FormControl<String>,
  val goalWeight: FormControl<String>,
) {
  /**
   * Returns true if the form is valid based on the current goal type.
   * For MAINTAIN mode, only goalType and goalWeight are validated.
   * For LOSE_GAIN mode, all fields are validated.
   */
  fun isValidForGoalType(): Boolean {
    val isMaintainMode = goalType.value == GoalType.MAINTAIN.value

    return goalType.isValueValid() &&
           goalWeight.isValueValid() &&
           if (isMaintainMode) {
             true // startingWeight is not validated in maintain mode
           } else {
             startingWeight.isValueValid()
           }
  }

  companion object {
    fun create(
      goalType: GoalType = GoalType.LOSE_GAIN,
      weightUnit: WeightUnit? = WeightUnit.LB,
    ): GoalFormControls {
      val startingWeightValidators = mutableListOf<Validator<String>>()
      if (goalType == GoalType.LOSE_GAIN) {
        startingWeightValidators.add(FormValidations.required())
      }
      startingWeightValidators.add(FormValidations.weightValidator(weightUnit))

      val controls =
        GoalFormControls(
          goalType =
            FormControl.create(
              initialValue = goalType.value,  // Always default to LOSE_GAIN
              validators = listOf(FormValidations.required()),
            ),
          startingWeight =
            FormControl.create(
              initialValue = "",
              validators = startingWeightValidators,
            ),
          goalWeight =
            FormControl.create(
              initialValue = "",
              validators = listOf(
                FormValidations.required(),
                FormValidations.weightValidator(weightUnit),
              ),
            ),
        )

      return controls
    }

    /**
     * Creates GoalFormControls with weight match validation.
     * This method should be used when you need cross-field validation between current weight and goal weight.
     */
    fun createWithWeightMatchValidation(
      goalType: GoalType = GoalType.LOSE_GAIN,
      weightUnit: WeightUnit? = WeightUnit.LB,
      initialStartingWeight: String = "",
      initialGoalWeight: String = "",
    ): GoalFormControls {
      val goalTypeControl = FormControl.create(
        initialValue = goalType.value,
        validators = listOf(FormValidations.required()),
      )

      val startingWeightValidators = if (goalType != GoalType.MAINTAIN) {
       listOf(
         FormValidations.required(),
         FormValidations.weightValidator(weightUnit),
       )
      } else {
        listOf(FormValidations.weightValidator(weightUnit))
      }

      val startingWeightControl = FormControl.create(
        initialValue = initialStartingWeight,
        validators = startingWeightValidators,
      )

      val goalWeightControl = FormControl.create(
        initialValue = initialGoalWeight,
        validators = listOf(
          FormValidations.required(),
          FormValidations.weightValidator(weightUnit),
          FormValidations.weightMatchValidator(startingWeightControl, goalTypeControl),
        ),
      )

      // Set up cross-field validation: when starting weight changes, re-validate goal weight
      startingWeightControl.onValueChangeListener { _, _ ->
        goalWeightControl.validate()
      }

      // Set up cross-field validation: when goal weight changes, re-validate starting weight
      goalWeightControl.onValueChangeListener { _, _ ->
        startingWeightControl.validate()
      }

      // Set up cross-field validation: when goal type changes, re-validate both weights
      goalTypeControl.onValueChangeListener { _, _ ->
        startingWeightControl.validate()
        goalWeightControl.validate()
      }

      return GoalFormControls(
        goalType = goalTypeControl,
        startingWeight = startingWeightControl,
        goalWeight = goalWeightControl,
      )
    }
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
@Stable
data class GoalState(
  val form: FormGroup<GoalFormControls>,
  val isLoading: Boolean = false,
  val error: String? = null,
  val account: Account? = null,
  val latestWeight: Double? = 0.0,
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
  override fun reduce(state: GoalState, intent: GoalIntent): GoalState {
    return when (intent) {
      is GoalIntent.Submit -> {
        state.copy(isLoading = true, error = null)
      }

      is GoalIntent.ChangeGoalType -> {
        // Update goal type and validators IN-PLACE so form remains dirty and Save stays enabled
        val controls = state.form.controls
        // Mark goalType as changed (dirty) and set new value
        controls.goalType.onValueChange(intent.goalType.value)
        // Update startingWeight validators based on new goal type BEFORE changing goal type
        // This prevents validation errors from showing when switching
        if (intent.goalType == GoalType.MAINTAIN) {
          // Remove required validator for maintain mode
          controls.startingWeight.removeValidator("required")
        } else {
          // Add required validator for lose/gain mode
          controls.startingWeight.addValidator(FormValidations.required())
        }
        state.copy() // same form reference; UI observes updated controls
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
        state.copy(account = intent.account)
      }

      is GoalIntent.UpdateLatestWeight -> {
        state.copy(latestWeight = intent.weight)
      }

      is GoalIntent.ToggleMetPreviousGoal -> {
        state.copy()
      }
    }
  }
}

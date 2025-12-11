package com.dmdbrands.gurus.weight.features.signup.model

import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.HeightInput
import com.dmdbrands.gurus.weight.features.common.helper.form.AppValidatorConfig
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations.weightValidator
import com.dmdbrands.gurus.weight.features.common.helper.form.Validator
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * All form controls for the signup process in a single FormGroup.
 */
data class SignupFormControls(
  val firstName: FormControl<String>,
  val lastName: FormControl<String>,
  val email: FormControl<String>,
  val password: FormControl<String>,
  val confirmPassword: FormControl<String>,
  val zipcode: FormControl<String>,
  val birthday: FormControl<DateTimeValue>,
  val sex: FormControl<String>,
  val height: FormControl<HeightInput>,
  val goalType: FormControl<String>,
  val currentWeight: FormControl<String>,
  val goalWeight: FormControl<String>,
  val useMetric: FormControl<Boolean>,
) {
  companion object {
    /**
     * Creates confirm password validator for password matching
     * Only the confirm password field should show mismatch errors
     */
    private fun createConfirmPasswordValidator(formGroup: () -> FormGroup<SignupFormControls>): Validator<String> =
      FormValidations.confirmPasswordMatch(formGroup)

    /**
     * Creates a dynamic weight validator that updates based on metric setting
     */
    private fun createDynamicWeightValidator(formGroup: () -> FormGroup<SignupFormControls>): Validator<String> =
      { value ->
        val isMetric = formGroup().controls.useMetric.value
        val weightUnit = if (isMetric) WeightUnit.KG else WeightUnit.LB
        weightValidator(weightUnit).invoke(value)
      }

    /**
     * Requires current weight unless goal type is MAINTAIN. When MAINTAIN, the field may be
     * hidden/disabled and should not block validation/submission.
     */
    private fun createRequiredCurrentWeightValidator(
      formGroup: () -> FormGroup<SignupFormControls>,
    ): Validator<String> = { value ->
      val currentGoalType = formGroup().controls.goalType.value
      if (currentGoalType == GoalType.MAINTAIN.value) {
        null
      } else {
        FormValidations.required().invoke(value)
      }
    }

    /**
     * Creates a new instance of SignupFormControls with default values and validations.
     */
    fun create(): SignupFormControls {
      val signupData = SignupData()
      // Create the SignupFormControls first
      val controls =
        SignupFormControls(
          firstName =
            FormControl.create(
              signupData.firstName,
              listOf(
                FormValidations.required(),
                FormValidations.noWhiteSpace(),
                FormValidations.maxLength(AppValidatorConfig.Name.MAX_LENGTH),
              ),
            ),
          lastName =
            FormControl.create(
              signupData.lastName,
              listOf(
                FormValidations.required(),
                FormValidations.noWhiteSpace(),
                FormValidations.maxLength(AppValidatorConfig.Name.MAX_LENGTH),
              ),
            ),
          email =
            FormControl.create(
              signupData.email,
              listOf(
                FormValidations.required(),
                FormValidations.email(),
                FormValidations.maxLength(AppValidatorConfig.Email.MAX_LENGTH),
              ),
            ),
          password =
            FormControl.create(
              signupData.password,
              listOf(
                FormValidations.required(),
                FormValidations.minLength(AppValidatorConfig.Password.MIN_LENGTH, "password"),
                FormValidations.maxLength(AppValidatorConfig.Password.MAX_LENGTH),
              ),
            ),
          confirmPassword =
            FormControl.create(
              signupData.confirmPassword,
              listOf(
                FormValidations.required(),
                FormValidations.minLength(AppValidatorConfig.Password.MIN_LENGTH, "confirmPassword"),
                FormValidations.maxLength(AppValidatorConfig.Password.MAX_LENGTH),
              ),
            ),
          zipcode =
            FormControl.create(
              signupData.zipcode,
              listOf(
                FormValidations.required(),
                FormValidations.noWhiteSpace(),
                FormValidations.minLength(AppValidatorConfig.ZipCode.MIN_LENGTH),
              FormValidations.maxLength(AppValidatorConfig.ZipCode.MAX_LENGTH),
              ),
            ),
          birthday =
            FormControl.create(
              DateTimeValue.Date(
                DateTimeValue.getEpochMillisFromDateString(
                  AppValidatorConfig.DateOfBirth.DEFAULT_VALUE,
                ),
              ),
              listOf(),
            ),
          sex =
            FormControl.create(
              signupData.sex,
              listOf(FormValidations.required()),
            ),
          height =
            FormControl.create(
              signupData.height, // Default height 170 cm
              emptyList(),
            ),
          goalType =
            FormControl.create(
              signupData.goalType,
              listOf(FormValidations.required()),
            ),
          currentWeight =
            FormControl.create(
              signupData.currentWeight,
              emptyList(), // Dynamic validators will be added after formGroup creation
            ),
          goalWeight =
            FormControl.create(
              signupData.goalWeight,
              listOf(FormValidations.required()), // Dynamic validator will be added after formGroup creation
            ),
          useMetric =
            FormControl.create(
              false,
              emptyList(),
            ),
        )

      val formGroup = FormGroup(controls)

      // Add dynamic weight validators that update based on metric setting
      controls.currentWeight.addValidator(createDynamicWeightValidator { formGroup })
      controls.currentWeight.addValidator(createRequiredCurrentWeightValidator { formGroup })
      controls.goalWeight.addValidator(createDynamicWeightValidator { formGroup })

      // Add password matching validation only to confirm password field
      controls.confirmPassword.addValidator(createConfirmPasswordValidator { formGroup })
      // Set up validation trigger - when password changes, validate confirm password
      controls.password.onValueChangeListener { _, _ ->
        // When password changes, trigger validation on confirm password if it has a value
        if (controls.confirmPassword.value.isNotEmpty()) {
          controls.confirmPassword.validate()
        }
      }

      // Set up metric toggle validation trigger
      controls.useMetric.onValueChangeListener { oldValue, newValue ->

        // Convert weight values when switching units
        if (controls.currentWeight.value.isNotEmpty()) {
          val convertedCurrentWeight =
            convertWeightValue(
              controls.currentWeight.value,
              oldValue,
              newValue,
            )
          controls.currentWeight.onValueChange(convertedCurrentWeight)
          // onValueChange() already calls validate(), but we explicitly validate again
          // to ensure the dynamic validator closure reads the updated useMetric value
          controls.currentWeight.validate()
        } else {
          // Re-validate empty fields to ensure validators are updated for future input
          // This handles cases where user toggles unit before entering weight values
          controls.currentWeight.validate()
        }

        if (controls.goalWeight.value.isNotEmpty()) {
          val convertedGoalWeight =
            convertWeightValue(
              controls.goalWeight.value,
              oldValue,
              newValue,
            )
          controls.goalWeight.onValueChange(convertedGoalWeight)
          // onValueChange() already calls validate(), but we explicitly validate again
          // to ensure the dynamic validator closure reads the updated useMetric value
          controls.goalWeight.validate()
        } else {
          // Re-validate empty fields to ensure validators are updated for future input
          // This handles cases where user toggles unit before entering weight values
          controls.goalWeight.validate()
        }

        // Update height input based on metric setting
        // Use proper rounding to preserve precision and prevent accumulation errors
        val currentHeight = controls.height.value
        val newHeight =
          if (newValue) {
            // Convert to metric (cm)
            when (currentHeight) {
              is HeightInput.FtIn -> {
                val totalInches = (currentHeight.feet * 12) + currentHeight.inches
                // Use roundToInt() instead of toInt() to round to nearest instead of truncating
                val cm = (totalInches * 2.54).roundToInt()
                HeightInput.Cm(cm)
              }

              is HeightInput.Cm -> currentHeight // Already metric
            }
          } else {
            // Convert to imperial (ft/in)
            when (currentHeight) {
              is HeightInput.Cm -> {
                // Use roundToInt() for proper rounding
                val totalInches = (currentHeight.value / 2.54).roundToInt()
                val feet = totalInches / 12
                val inches = totalInches % 12
                HeightInput.FtIn(feet, inches)
              }

              is HeightInput.FtIn -> currentHeight // Already imperial
            }
          }
        controls.height.onValueChange(newHeight)
      }

      return controls
    }
  }
}

/**
 * State for Signup screen, including form group, current step, and UI state.
 * @property form The form group containing all signup controls.
 * @property currentStep The current step in the signup process.
 * @property isLoading Whether the signup process is ongoing.
 * @property error Error message to display, if any.
 * @property goalSkipped Whether the goal step was skipped.
 */
data class SignupState(
  val form: FormGroup<SignupFormControls>,
  val currentStep: SignupStep = SignupStep.NAME,
  val isLoading: Boolean = false,
  val error: String? = null,
  val goalSkipped: Boolean = false,
) : IReducer.State {
  val steps: List<SignupStep> = SignupStep.entries
  val currentStepIndex: Int get() = steps.indexOf(currentStep)
  val isFirstStep: Boolean get() = currentStepIndex == 0
  val isLastStep: Boolean get() = currentStepIndex == steps.size - 1
  val showSkipButton: Boolean get() = currentStep == SignupStep.GOAL
  val progress: Float get() = (currentStepIndex + 1f) / steps.size

  /**
   * Returns true if the current step form is valid without triggering validation.
   * This is used for reactive UI updates to enable/disable the Next button.
   */
  val isCurrentStepValid: Boolean
    get() =
      when (currentStep) {
        SignupStep.NAME -> {
          val controls = form.controls
          val firstNameValid = controls.firstName.isValueValid()
          val lastNameValid = controls.lastName.isValueValid()
          firstNameValid && lastNameValid
        }

        SignupStep.BIRTHDAY -> {
          val controls = form.controls
          val birthdayValid = controls.birthday.isValueValid()
          birthdayValid
        }

        SignupStep.GENDER -> {
          val controls = form.controls
          val sexValid = controls.sex.isValueValid()
          sexValid
        }

        SignupStep.HEIGHT -> {
          // Height doesn't have validators and has a default value, so it's always valid
          true
        }

        SignupStep.EMAIL -> {
          val controls = form.controls
          val emailValid = controls.email.isValueValid()
          emailValid
        }

        SignupStep.GOAL -> {
          if (goalSkipped) {
            true
          } else {
            val controls = form.controls
            val goalTypeValid = controls.goalType.isValueValid()
            val goalWeightValid = controls.goalWeight.isValueValid()
            val currentWeightValid =
              if (controls.goalType.value == GoalType.MAINTAIN.value) {
                true
              } else {
                val currentWeightValidated = controls.currentWeight.isValueValid()
                currentWeightValidated
              }

            goalTypeValid && goalWeightValid && currentWeightValid
          }
        }

        SignupStep.PASSWORD -> {
          val controls = form.controls

          val passwordValid = controls.password.isValueValid()
          val confirmPasswordValid = controls.confirmPassword.isValueValid()
          val zipcodeValid = controls.zipcode.isValueValid()

          passwordValid && confirmPasswordValid && zipcodeValid
        }
      }
}

/**
 * Intents for Signup screen actions.
 */
sealed class SignupIntent : IReducer.Intent {
  /** Move to the next step or submit if on last step. */
  object Next : SignupIntent()

  /** Move to the previous step. */
  object Back : SignupIntent()

  object OnRequestBack : SignupIntent()

  object OpenHelpModal : SignupIntent()

  data class OpenURL(
    val url: String,
  ) : SignupIntent()

  /** Skip the current step (only available for goal step). */
  object Skip : SignupIntent()

  /** Toggle between metric and imperial units. */
  data class ToggleMetric(
    val useMetric: Boolean,
  ) : SignupIntent()

  /** Show an error message. */
  data class Error(
    val message: String,
  ) : SignupIntent()

  /** Signup was successful. */
  object Success : SignupIntent()

  /** Update goal skip status. */
  data class UpdateGoalSkipped(
    val skipped: Boolean,
  ) : SignupIntent()
}

/**
 * Reducer for Signup screen state transitions.
 */
class SignupReducer : IReducer<SignupState, SignupIntent> {
  /**
   * Reduces the current state and intent to a new state.
   * @param state The current state.
   * @param intent The intent/action to handle.
   * @return The new state after applying the intent.
   */
  override fun reduce(
    state: SignupState,
    intent: SignupIntent,
  ): SignupState =
    when (intent) {
      is SignupIntent.Next -> {
        if (state.isLastStep) {
          // On submit, if goal type is maintain, clear starting weight (currentWeight)
          val controls = state.form.controls
          if (controls.goalType.value == GoalType.MAINTAIN.value) {
            controls.currentWeight.reset("")
          }
          state.copy(isLoading = true, error = null)
        } else {
          val nextIndex = (state.currentStepIndex + 1).coerceAtMost(state.steps.lastIndex)
          val newStep = state.steps[nextIndex]
          state.copy(currentStep = newStep, error = null)
        }
      }

      is SignupIntent.Back -> {
        val prevIndex = (state.currentStepIndex - 1).coerceAtLeast(0)
        state.copy(currentStep = state.steps[prevIndex], error = null)
      }

      is SignupIntent.Skip -> {
        if (state.currentStep == SignupStep.GOAL) {
          state.form.controls.currentWeight.reset()
          state.form.controls.goalWeight.reset()
          state.form.controls.goalType.reset()
          val nextIndex = (state.currentStepIndex + 1).coerceAtMost(state.steps.lastIndex)
          state.copy(
            currentStep = state.steps[nextIndex],
            goalSkipped = true,
            error = null,
          )
        } else {
          state
        }
      }

      is SignupIntent.OnRequestBack -> {
        state.copy(isLoading = false, error = null)
      }

      is SignupIntent.OpenHelpModal -> {
        state.copy(isLoading = false, error = null)
      }

      is SignupIntent.Error -> {
        state.copy(isLoading = false, error = intent.message)
      }

      is SignupIntent.Success -> {
        state.copy(isLoading = false, error = null)
      }

      is SignupIntent.UpdateGoalSkipped -> {
        state.copy(goalSkipped = intent.skipped)
      }

      is SignupIntent.ToggleMetric -> {
        // Update the metric setting - this will trigger the listener which handles all conversions
        val controls = state.form.controls
        controls.useMetric.onValueChange(intent.useMetric)

        state.copy(error = null)
      }

      else -> state
    }
}

/**
 * Extension functions for SignupFormControls to support metric conversions
 */

/**
 * Converts weight value between units when metric setting changes.
 * Preserves one decimal place precision to prevent accumulation errors.
 *
 * Note: Weight values are stored as integers where the last digit represents the decimal place.
 * For example, "605" represents 60.5, "550" represents 55.0.
 */
fun convertWeightValue(
  value: String,
  fromMetric: Boolean,
  toMetric: Boolean,
): String {
  if (value.isBlank() || fromMetric == toMetric) return value

  return try {
    // Convert integer format (e.g., "605") to decimal format (e.g., "60.5")
    val decimalValue = if (value.length > 1) {
      value.dropLast(1) + "." + value.takeLast(1)
    } else {
      "0." + value
    }

    val numericValue = decimalValue.toDoubleOrNull() ?: return value

    val convertedValue =
      when {
        // Converting FROM kg TO lbs: multiply by conversion factor
        fromMetric && !toMetric -> numericValue * 2.20462
        // Converting FROM lbs TO kg: divide by conversion factor
        !fromMetric && toMetric -> numericValue / 2.20462
        // No conversion needed
        else -> numericValue
      }

    // Round to one decimal place to preserve precision and prevent accumulation errors
    val roundedValue = round(convertedValue * 10) / 10.0

    // Convert back to integer format (e.g., 60.5 -> "605", 133.4 -> "1334")
    // Format as string with one decimal place first
    val formatted = String.format("%.1f", roundedValue)
    // Remove decimal point to get integer format (e.g., "60.5" -> "605")
    formatted.replace(".", "")
  } catch (e: Exception) {
    value
  }
}

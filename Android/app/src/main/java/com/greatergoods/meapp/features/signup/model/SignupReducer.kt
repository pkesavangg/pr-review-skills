package com.greatergoods.meapp.features.signup.model

import com.greatergoods.meapp.core.shared.utilities.DateTimeTools
import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.components.HeightInput
import com.greatergoods.meapp.features.common.helper.form.AppValidatorConfig
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.helper.form.FormValidations
import com.greatergoods.meapp.features.common.helper.form.FormValidations.weightValidator
import com.greatergoods.meapp.features.common.helper.form.Validator

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
                                FormValidations.noWhitespace(),
                                FormValidations.maxLength(AppValidatorConfig.Name.MAX_LENGTH),
                            ),
                        ),
                    lastName =
                        FormControl.create(
                            signupData.lastName,
                            listOf(
                                FormValidations.required(),
                                FormValidations.noWhitespace(),
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
                                FormValidations.minLength(AppValidatorConfig.Password.MIN_LENGTH),
                                FormValidations.maxLength(AppValidatorConfig.Password.MAX_LENGTH),
                            ),
                        ),
                    confirmPassword =
                        FormControl.create(
                            signupData.confirmPassword,
                            listOf(
                                FormValidations.required(),
                                FormValidations.minLength(AppValidatorConfig.Password.MIN_LENGTH),
                                FormValidations.maxLength(AppValidatorConfig.Password.MAX_LENGTH),
                            ),
                        ),
                    zipcode =
                        FormControl.create(
                            signupData.zipcode,
                            listOf(
                                FormValidations.required(),
                                FormValidations.noWhitespace(),
                                FormValidations.maxLength(AppValidatorConfig.ZipCode.MAX_LENGTH),
                            ),
                        ),
                    birthday =
                        FormControl.create(
                            DateTimeValue.Date(
                                DateTimeTools.getEpochMillisFromDateString(AppValidatorConfig.DateOfBirth.DEFAULT_VALUE),
                            ),
                            listOf(),
                        ),
                    sex =
                        FormControl.create(
                            signupData.gender,
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
                            listOf(FormValidations.required(), weightValidator(WeightUnit.LB)),
                        ),
                    goalWeight =
                        FormControl.create(
                            signupData.goalWeight,
                            listOf(FormValidations.required(), weightValidator(WeightUnit.LB)),
                        ),
                    useMetric = FormControl.create(
                        false,
                        emptyList()
                    ),
                )

            val formGroup = FormGroup(controls)

            // Add password matching validation only to confirm password field
            controls.confirmPassword.addValidator(createConfirmPasswordValidator { formGroup })

            // Add dynamic weight validators
            controls.currentWeight.addValidator(createDynamicWeightValidator { formGroup })
            controls.goalWeight.addValidator(createDynamicWeightValidator { formGroup })
            controls.currentWeight.addValidator(FormValidations.required())
            controls.goalWeight.addValidator(FormValidations.required())

            // Set up validation trigger - when password changes, validate confirm password
            controls.password.onValueChangeListener { _, _ ->
                // When password changes, trigger validation on confirm password if it has a value
                if (controls.confirmPassword.value.isNotEmpty()) {
                    controls.confirmPassword.validate()
                }
            }

                        // Set up metric toggle validation trigger
            controls.useMetric.onValueChangeListener { oldValue, newValue ->
                val wasMetric = oldValue
                val isMetric = newValue

                // Convert weight values when switching units
                if (controls.currentWeight.value.isNotEmpty()) {
                    val convertedCurrentWeight = convertWeightValue(
                        controls.currentWeight.value,
                        wasMetric,
                        isMetric
                    )
                    controls.currentWeight.onValueChange(convertedCurrentWeight)
                }

                if (controls.goalWeight.value.isNotEmpty()) {
                    val convertedGoalWeight = convertWeightValue(
                        controls.goalWeight.value,
                        wasMetric,
                        isMetric
                    )
                    controls.goalWeight.onValueChange(convertedGoalWeight)
                }

                // Update height input based on metric setting
                val currentHeight = controls.height.value
                val newHeight = if (isMetric) {
                    // Convert to metric (cm)
                    when (currentHeight) {
                        is HeightInput.FtIn -> {
                            val totalInches = (currentHeight.feet * 12) + currentHeight.inches
                            val cm = (totalInches * 2.54).toInt()
                            HeightInput.Cm(cm)
                        }
                        is HeightInput.Cm -> currentHeight // Already metric
                    }
                } else {
                    // Convert to imperial (ft/in)
                    when (currentHeight) {
                        is HeightInput.Cm -> {
                            val totalInches = (currentHeight.value / 2.54).toInt()
                            val feet = totalInches / 12
                            val inches = totalInches % 12
                            HeightInput.FtIn(feet, inches)
                        }
                        is HeightInput.FtIn -> currentHeight // Already imperial
                    }
                }
                controls.height.onValueChange(newHeight)

                // Revalidate weights after conversion
                if (controls.currentWeight.value.isNotEmpty()) {
                    controls.currentWeight.validate()
                }
                if (controls.goalWeight.value.isNotEmpty()) {
                    controls.goalWeight.validate()
                }
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
                // Update the metric setting in the form controls
                state.form.controls.useMetric.onValueChange(intent.useMetric)
                state.copy(error = null)
            }

            else -> state
        }
}

/**
 * Extension functions for SignupFormControls to support metric conversions
 */

/**
 * Gets the current weight unit based on metric setting
 */
fun SignupFormControls.getCurrentWeightUnit(): WeightUnit {
    return if (useMetric.value) WeightUnit.KG else WeightUnit.LB
}

/**
 * Gets the weight unit display string
 */
fun SignupFormControls.getWeightUnitString(): String {
    return getCurrentWeightUnit().value
}

/**
 * Gets the height unit display string
 */
fun SignupFormControls.getHeightUnitString(): String {
    return if (useMetric.value) "cm" else "ft/in"
}

/**
 * Converts weight value between units when metric setting changes
 */
fun convertWeightValue(value: String, fromMetric: Boolean, toMetric: Boolean): String {
    if (value.isBlank() || fromMetric == toMetric) return value

    return try {
        val numericValue = value.toDoubleOrNull() ?: return value

        if (fromMetric && !toMetric) {
            // Convert kg to lbs
            (numericValue * 2.20462).toInt().toString()
        } else if (!fromMetric && toMetric) {
            // Convert lbs to kg
            (numericValue / 2.20462).toInt().toString()
        } else {
            value
        }
    } catch (e: Exception) {
        value
    }
}

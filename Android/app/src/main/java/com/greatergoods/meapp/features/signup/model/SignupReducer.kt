package com.greatergoods.meapp.features.signup.model

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IReducer
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
    val birthday: FormControl<String>,
    val sex: FormControl<String>,
    val height: FormControl<HeightInput>,
    val goalType: FormControl<String>,
    val currentWeight: FormControl<String>,
    val goalWeight: FormControl<String>,
) {
    companion object {
        /**
         * Creates confirm password validator for password matching
         * Only the confirm password field should show mismatch errors
         */
        private fun createConfirmPasswordValidator(
            formGroup: () -> FormGroup<SignupFormControls>
        ): Validator<String> = FormValidations.confirmPasswordMatch(formGroup)

        /**
         * Creates a new instance of SignupFormControls with default values and validations.
         */
        fun create(): SignupFormControls {
            // Create the SignupFormControls first
            val controls = SignupFormControls(
                firstName = FormControl.create(
                    "",
                    listOf(
                        FormValidations.required(),
                        FormValidations.noWhitespace(),
                        FormValidations.maxLength(100),
                    ),
                ),
                lastName = FormControl.create(
                    "",
                    listOf(
                        FormValidations.required(),
                        FormValidations.noWhitespace(),
                        FormValidations.maxLength(100),
                    ),
                ),
                email = FormControl.create(
                    "",
                    listOf(
                        FormValidations.required(),
                        FormValidations.email(),
                        FormValidations.maxLength(100),
                    ),
                ),
                password = FormControl.create(
                    "",
                    listOf(
                        FormValidations.required(),
                        FormValidations.minLength(AppValidatorConfig.Password.MIN_LENGTH),
                        FormValidations.maxLength(AppValidatorConfig.Password.MAX_LENGTH),
                    ),
                ),
                confirmPassword = FormControl.create(
                    "",
                    listOf(
                        FormValidations.required(),
                        FormValidations.minLength(AppValidatorConfig.Password.MIN_LENGTH),
                        FormValidations.maxLength(AppValidatorConfig.Password.MAX_LENGTH),
                    ),
                ),
                zipcode = FormControl.create(
                    "",
                    listOf(
                        FormValidations.required(),
                        FormValidations.noWhitespace(),
                    FormValidations.maxLength(20),
                    ),
                ),
                birthday = FormControl.create(
                    "2000-01-01",
                    listOf(FormValidations.required()),
                ),
                sex = FormControl.create(
                    "",
                    listOf(FormValidations.required()),
                ),
                height = FormControl.create(
                    HeightInput.Cm(170), // Default height 170 cm
                    emptyList(),
                ),
                goalType = FormControl.create(
                    "losegain",
                    listOf(FormValidations.required()),
                ),
                currentWeight = FormControl.create(
                    "",
                    listOf(FormValidations.required(),weightValidator("lbs") ),
                ),
                goalWeight = FormControl.create(
                    "",
                    listOf(FormValidations.required(), weightValidator("lbs")),
                ),

            )

            val formGroup = FormGroup(controls)
            // Add password matching validation only to confirm password field
            controls.confirmPassword.addValidator(createConfirmPasswordValidator { formGroup })

            // Set up validation trigger - when password changes, validate confirm password
            controls.password.onValueChangeListener { _, _ ->
                // When password changes, trigger validation on confirm password if it has a value
                if (controls.confirmPassword.value.isNotEmpty()) {
                    controls.confirmPassword.validate()
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
 * @property isNavigating Whether we're currently navigating between steps.
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
        val isCurrentStepValid: Boolean get() = when (currentStep) {
        SignupStep.NAME -> {
            val controls = form.controls
            val firstNameNotEmpty = controls.firstName.value.toString().trim().isNotEmpty()
            val lastNameNotEmpty = controls.lastName.value.toString().trim().isNotEmpty()
            val firstNameValid = controls.firstName.isValueValid()
            val lastNameValid = controls.lastName.isValueValid()
            firstNameNotEmpty && lastNameNotEmpty && firstNameValid && lastNameValid
        }
        SignupStep.BIRTHDAY -> {
            val controls = form.controls
            val birthdayNotEmpty = controls.birthday.value.toString().trim().isNotEmpty()
            val birthdayValid = controls.birthday.isValueValid()
            birthdayNotEmpty && birthdayValid
        }
        SignupStep.GENDER -> {
            val controls = form.controls
            val sexNotEmpty = controls.sex.value.toString().trim().isNotEmpty()
            val sexValid = controls.sex.isValueValid()
            sexNotEmpty && sexValid
        }
        SignupStep.HEIGHT -> {
            // Height doesn't have validators and has a default value, so it's always valid
            true
        }
        SignupStep.EMAIL -> {
            val controls = form.controls
            val emailNotEmpty = controls.email.value.toString().trim().isNotEmpty()
            val emailValid = controls.email.isValueValid()
            emailNotEmpty && emailValid
        }
        SignupStep.GOAL -> {
            if (goalSkipped) {
                true
            } else {
                val controls = form.controls
                val goalTypeNotEmpty = controls.goalType.value.toString().trim().isNotEmpty()
                val goalTypeValid = controls.goalType.isValueValid()
                val goalWeightNotEmpty = controls.goalWeight.value.toString().trim().isNotEmpty()
                val goalWeightValid = controls.goalWeight.isValueValid()

                val currentWeightValid = if (controls.goalType.value == "maintain") {
                    true
                } else {
                    val currentWeightNotEmpty = controls.currentWeight.value.toString().trim().isNotEmpty()
                    val currentWeightValidated = controls.currentWeight.isValueValid()
                    currentWeightNotEmpty && currentWeightValidated
                }

                goalTypeNotEmpty && goalTypeValid &&
                goalWeightNotEmpty && goalWeightValid &&
                currentWeightValid
            }
        }
        SignupStep.PASSWORD -> {
            val controls = form.controls
            val passwordNotEmpty = controls.password.value.toString().trim().isNotEmpty()
            val passwordValid = controls.password.isValueValid()
            val confirmPasswordNotEmpty = controls.confirmPassword.value.toString().trim().isNotEmpty()
            val confirmPasswordValid = controls.confirmPassword.isValueValid()
            val zipcodeNotEmpty = controls.zipcode.value.toString().trim().isNotEmpty()
            val zipcodeValid = controls.zipcode.isValueValid()

            passwordNotEmpty && passwordValid &&
            confirmPasswordNotEmpty && confirmPasswordValid &&
            zipcodeNotEmpty && zipcodeValid
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

    /** Skip the current step (only available for goal step). */
    object Skip : SignupIntent()

    /** Move to a specific step. */
    data class GoToStep(val step: SignupStep) : SignupIntent()

    /** Trigger signup submission. */
    object Submit : SignupIntent()

    /** Show an error message. */
    data class Error(val message: String) : SignupIntent()

    /** Signup was successful. */
    object Success : SignupIntent()

    /** Update goal skip status. */
    data class UpdateGoalSkipped(val skipped: Boolean) : SignupIntent()
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
                    AppLog.d("SignupReducer", "Next intent: moving from ${state.currentStep} (index ${state.currentStepIndex}) to $newStep (index $nextIndex)")
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
                        error = null
                    )
                } else {
                    state
                }
            }

            is SignupIntent.GoToStep -> {
                state.copy(currentStep = intent.step, error = null)
            }

            is SignupIntent.Submit -> {
                state.copy(isLoading = true, error = null)
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
        }
}

package com.greatergoods.meapp.features.signup.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.ConversionTools
import com.greatergoods.meapp.core.shared.utilities.DateTimeTools
import com.greatergoods.meapp.core.shared.utilities.browser.ICustomTabManager
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.features.common.components.HeightInput
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.signup.model.SignupFormControls
import com.greatergoods.meapp.features.signup.model.SignupIntent
import com.greatergoods.meapp.features.signup.model.SignupReducer
import com.greatergoods.meapp.features.signup.model.SignupState
import com.greatergoods.meapp.features.signup.model.SignupStep
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Signup screen. Handles form state, validation, signup logic, and navigation.
 * @property accountAuthService Service for authentication.
 */
@HiltViewModel
class SignupViewModel
    @Inject
    constructor(
        private val accountAuthService: IAccountAuthService,
        private val customTabManager: ICustomTabManager,
    ) : BaseIntentViewModel<SignupState, SignupIntent>(
            reducer = SignupReducer(),
        ) {
        override fun provideInitialState(): SignupState =
            SignupState(
                form = FormGroup(SignupFormControls.create()),
            )

        override fun handleIntent(intent: SignupIntent) {
            super.handleIntent(intent)
            when (intent) {
                is SignupIntent.OpenURL -> openUrl(intent.url)
                is SignupIntent.Next -> onNext()
                is SignupIntent.Skip -> onSkip()
                else -> {}
            }
        }

        /**
         * Handles moving to the next step or submitting if on the last step.
         */
        fun onNext() {
            AppLog.d(
                "SignupViewModel",
                "onNext called - currentStep: ${state.value.currentStep}, isLastStep: ${state.value.isLastStep}, isValid: ${state.value.isCurrentStepValid}",
            )

            if (state.value.isLastStep) {
                AppLog.d("SignupViewModel", "Submitting signup form")
                onSubmit()
            } else {
                AppLog.d("SignupViewModel", "Moving to next step from ${state.value.currentStep}")
                AppLog.d("SignupViewModel", "After Next intent - new currentStep: ${state.value.currentStep}")
            }
        }

        /**
         * Handles skipping the current step (only available for goal step).
         */
        fun onSkip() {
            if (state.value.currentStep == SignupStep.GOAL) {
                handleIntent(SignupIntent.Skip)
            }
        }

        /**
         * Handles the signup form submission. Validates the form, shows loading, and attempts signup.
         * On success, navigates to the loading screen. On failure, shows an error message.
         */
        fun onSubmit() {
            dialogQueueService.showLoader(
                message = SignupStrings.LoaderMessage,
            )

            // Validate form fields based on whether goal was skipped
            val isFormValid =
                if (state.value.goalSkipped) {
                    // When goal is skipped, validate all fields except goal-related ones
                    val controls = state.value.form.controls
                    val basicFieldsValid =
                        listOf(
                            controls.firstName.validate(),
                            controls.lastName.validate(),
                            controls.email.validate(),
                            controls.password.validate(),
                            controls.confirmPassword.validate(),
                            controls.zipcode.validate(),
                            controls.birthday.validate(),
                            controls.sex.validate(),
                            // height doesn't have validators, so it's always valid
                        ).all { it }

                    basicFieldsValid
                } else {
                    // When goal is not skipped, validate all fields
                    state.value.form.validate()
                }

            if (!isFormValid) {
                handleIntent(SignupIntent.Error("Something went wrong"))
                dialogQueueService.dismissLoader()
                return
            }

            viewModelScope.launch {
                try {
                    val controls = state.value.form.controls
                    // Create the basic account request (similar to newAccount in wgApp4-1)
                    val signupRequest =
                        mutableMapOf<String, Any>(
                            "email" to controls.email.value.trim(),
                            "firstName" to controls.firstName.value.trim(),
                            "lastName" to
                                controls.lastName.value
                                    .trim()
                                    .ifEmpty { " " },
                            "gender" to controls.sex.value,
                            "zipcode" to
                                controls.zipcode.value
                                    .trim()
                                    .ifEmpty { " " },
                            "password" to controls.password.value,
                            "dob" to DateTimeTools.formatDateForAPI(controls.birthday.value.getTimestamp()),
                            "height" to convertHeightInputToMm(controls.height.value),
                        )

                    var goalData: Map<String, Any>? = null
                    if (!state.value.goalSkipped) {
                        val goalType = controls.goalType.value
                        val currentWeight = controls.currentWeight.value.toDoubleOrNull() ?: 0.0
                        val goalWeight = controls.goalWeight.value.toDoubleOrNull() ?: 0.0
                        // Always use imperial (lbs) for signup
                        val isMetric = false

                        // Use ConversionTools to convert display weights to stored format
                        val convertedCurrentWeight =
                            ConversionTools.convertDisplayToStored(
                                display = currentWeight,
                                isMetric = isMetric,
                            )
                        val convertedGoalWeight =
                            ConversionTools.convertDisplayToStored(
                                display = goalWeight,
                                isMetric = isMetric,
                            )

                        goalData =
                            if (goalType == "maintain") {
                                // For maintain: both goalWeight and initialWeight are the same
                                mapOf(
                                    "type" to "maintain",
                                    "goalWeight" to convertedGoalWeight,
                                    "initialWeight" to convertedGoalWeight,
                                )
                            } else {
                                // Determine gain vs lose based on weight comparison
                                val determinedGoalType =
                                    if (convertedGoalWeight >
                                        convertedCurrentWeight
                                    ) {
                                        "gain"
                                    } else {
                                        "lose"
                                    }
                                mapOf(
                                    "type" to determinedGoalType,
                                    "goalWeight" to convertedGoalWeight,
                                    "initialWeight" to convertedCurrentWeight,
                                )
                            }

                        // Add weight unit to account data for body composition update (always lbs)
                        signupRequest["weightUnit"] = "lb"
                    }
                    val account = accountAuthService.addAccount(signupRequest)
                    if (account != null) {
                        AppLog.i("SignupViewModel", "Account created successfully")
                        // TODO: Handle HealthConnect popup (from wgApp4-1)
                        // this.healthConnectService.shouldShowPopup = false;
                        // If goals weren't skipped, update goal and body composition (similar to wgApp4-1 flow)
                        if (!state.value.goalSkipped && goalData != null) {
                            try {
                                // TODO: Update goal using goal service
                                // await this.goalSrvc.updateGoal(newGoal, false);
                                AppLog.i("SignupViewModel", "Goal data prepared using ConversionTools: $goalData")
                                AppLog.i("SignupViewModel", "Weight unit set to: lb (always imperial)")
                            } catch (goalException: Exception) {
                                AppLog.w(
                                    "SignupViewModel",
                                    "Goal/bodycomp update failed but continuing",
                                    goalException.toString(),
                                )
                            }
                        }
                        navigationService.replaceStack(AppRoute.Init.Loading)
                        AppLog.i("SignupViewModel", "Navigation to loading screen successful after signup")
                        handleIntent(SignupIntent.Success)
                    } else {
                        handleIntent(SignupIntent.Error("Something went wrong"))
                    }
                } catch (e: Exception) {
                    AppLog.e("SignupViewModel", "Signup failed", e.toString())
                    handleIntent(SignupIntent.Error("Signup failed"))
                } finally {
                    dialogQueueService.dismissLoader()
                }
            }
        }

        /**
         * Opens a URL using the injected CustomTabManager.
         * @param url The URL to open.
         */
        fun openUrl(url: String) {
            customTabManager.openChromeTab(url)
        }

        /**
         * Converts [HeightInput] to millimeters (Int) for API request using ConversionTools.
         */
        private fun convertHeightInputToMm(heightInput: HeightInput): Int =
            when (heightInput) {
                is HeightInput.Cm -> {
                    // Convert cm to stored height format, then to mm
                    val storedHeight = ConversionTools.convertCmToStoredHeight(heightInput.value)
                    ConversionTools.convertStoredHeightToCm(storedHeight) * 10
                }

                is HeightInput.FtIn -> {
                    // Convert feet/inches to stored height format, then to mm
                    val storedHeight =
                        ConversionTools.convertFeetInchesToStoredHeight(
                            feet = heightInput.feet,
                            inches = heightInput.inches,
                        )
                    ConversionTools.convertStoredHeightToCm(storedHeight) * 10
                }
            }
    }

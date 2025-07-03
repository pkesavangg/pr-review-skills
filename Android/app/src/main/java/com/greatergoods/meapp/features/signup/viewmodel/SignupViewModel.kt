package com.greatergoods.meapp.features.signup.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.ConversionTools
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.api.auth.SignupRequest
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.IGoalService
import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.components.DialogType
import com.greatergoods.meapp.features.common.components.HeightInput
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.signup.model.SignupData
import com.greatergoods.meapp.features.signup.model.SignupFormControls
import com.greatergoods.meapp.features.signup.model.SignupIntent
import com.greatergoods.meapp.features.signup.model.SignupReducer
import com.greatergoods.meapp.features.signup.model.SignupState
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Signup screen. Handles form state, validation, signup logic, and navigation.
 * @property accountService Service for authentication.
 */
@HiltViewModel
class SignupViewModel
    @Inject
    constructor(
        private val accountService: IAccountService,
        private val goalService: IGoalService,
    ) : BaseIntentViewModel<SignupState, SignupIntent>(
            reducer = SignupReducer(),
        ) {
        private val TAG = "SignupViewModel"

        override fun provideInitialState(): SignupState =
            SignupState(
                form = FormGroup(SignupFormControls.create()),
            )

        override fun handleIntent(intent: SignupIntent) {
            when (intent) {
                is SignupIntent.OpenHelpModal -> openHelpModal()
                is SignupIntent.OpenURL -> openInAppBrowser(intent.url)
                is SignupIntent.Next -> onNext()
                is SignupIntent.OnRequestBack -> onRequestBack()
                else -> {}
            }
            super.handleIntent(intent)
        }

        /**
         * Handles moving to the next step or submitting if on the last step.
         */
        fun onNext() {
            if (state.value.isLastStep) {
                AppLog.d(TAG, "Submitting signup form")
                onSubmit()
            } else {
                AppLog.d(TAG, "After Next intent - new currentStep: ${state.value.currentStep}")
            }
        }

        /**
         * Handles the signup form submission. Validates the form, shows loading, and attempts signup.
         * On success, navigates to the loading screen. On failure, shows an error message.
         */
        private fun onSubmit() {
            dialogQueueService.showLoader(
                message = SignupStrings.LoaderMessage,
            )
            val stateValue = state.value
            // Validate form fields based on whether goal was skipped
            val controls = stateValue.form.controls
            val isFormValid =
                if (stateValue.goalSkipped) {
                    // When goal is skipped, validate all fields except goal-related ones
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
                    stateValue.form.validate()
                }

            if (!isFormValid) {
                handleIntent(SignupIntent.Error("Something went wrong"))
                dialogQueueService.dismissLoader()
                return
            }
            val signupData: SignupData = stateValue.form.getValuesAsType()
            viewModelScope.launch {
                try {
                    val isMetric = if (!stateValue.goalSkipped) signupData.unitMetric else false

                    // Create the basic account request (similar to newAccount in wgApp4-1)
                    val signupRequest =
                        SignupRequest(
                            signupData.email.trim(),
                            signupData.firstName.trim(),
                            signupData.lastName.trim(),
                            signupData.sex,
                            signupData.zipcode
                                .trim(),
                            signupData.password,
                            DateTimeValue.getDateFormatFromMilliseconds(controls.birthday.value.getTimestamp()),
                            controls.height.value.toStoredHeight(),
                            if (isMetric) WeightUnit.KG else WeightUnit.LB,
                        )
                    val account = accountService.signup(signupRequest)
                    if (account != null) {
                        AppLog.i(TAG, "Account created successfully")

                        // Create goal if not skipped - this will complete before proceeding to navigation
                        if (!stateValue.goalSkipped) {
                            AppLog.d(TAG, "Creating goal for new account...")
                            goalService.createGoalForSignup(
                                account = account,
                                goalType = signupData.goalType,
                                currentWeight = signupData.currentWeight.toDoubleOrNull() ?: 0.0,
                                goalWeight = signupData.goalWeight.toDoubleOrNull() ?: 0.0,
                            )
                            AppLog.d(TAG, "Goal creation completed, proceeding to navigation")
                        }

                        navigationService.replaceStack(AppRoute.Init.Loading)
                        AppLog.i(TAG, "Navigation to loading screen successful after signup")
                        handleIntent(SignupIntent.Success)
                    } else {
                        handleIntent(SignupIntent.Error("Something went wrong"))
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Signup failed", e.toString())
                    handleIntent(SignupIntent.Error("Signup failed"))
                } finally {
                    dialogQueueService.dismissLoader()
                }
            }
        }

        /**
         * Opens the Help modal.
         */
        private fun openHelpModal() {
            dialogQueueService.enqueue(
                DialogModel.Custom(
                    contentKey = DialogType.HelpPopup,
                    onDismiss = {},
                ),
            )
        }

        private fun onRequestBack() {
            dialogQueueService.enqueue(
                DialogModel.Confirm(
                    title = "Confirm",
                    message = "Are you sure you want to leave?",
                    confirmText = "EXIT",
                    cancelText = "RETURN",
                    onConfirm = {
                        navigateBack()
                        dialogQueueService.dismissCurrent()
                    },
                    onCancel = {
                        dialogQueueService.dismissCurrent()
                    },
                ),
            )
        }

        /**
         * Handles navigation back/exit from signup screen.
         * Call this when user wants to exit the signup flow.
         */
        private fun navigateBack() {
            viewModelScope.launch {
                try {
                    navigationService.navigateBack(topLevel = null)
                    AppLog.d(TAG, "Successfully navigated back from signup")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to navigate back from signup", e.toString())
                }
            }
        }

        /**
         * Opens a URL using the injected CustomTabManager.
         * @param url The URL to open.
         */
        private fun openUrl(url: String) {
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

package com.dmdbrands.gurus.weight.features.signup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.features.signup.model.SignupData
import com.dmdbrands.gurus.weight.features.signup.model.SignupFormControls
import com.dmdbrands.gurus.weight.features.signup.model.SignupIntent
import com.dmdbrands.gurus.weight.features.signup.model.SignupReducer
import com.dmdbrands.gurus.weight.features.signup.model.SignupState
import com.dmdbrands.gurus.weight.features.signup.strings.PickDeviceStrings
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
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
    val controls = stateValue.form.controls
    val device = controls.device.value

    // Validate common fields + device-specific fields
    val commonValid = listOf(
      controls.firstName.validate(),
      controls.lastName.validate(),
      controls.email.validate(),
      controls.password.validate(),
      controls.confirmPassword.validate(),
      controls.zipcode.validate(),
      controls.birthday.validate(),
    ).all { it }

    val deviceFieldsValid = when (device) {
      PickDeviceStrings.Devices.WEIGHT_SCALE -> {
        if (stateValue.goalSkipped) {
          controls.sex.validate()
        } else {
          listOf(
            controls.sex.validate(),
            controls.goalType.validate(),
            controls.goalWeight.validate(),
            controls.currentWeight.validate(),
          ).all { it }
        }
      }
      PickDeviceStrings.Devices.BLOOD_PRESSURE -> controls.sex.validate()
      else -> true // Baby Scale doesn't need additional form validation
    }

    if (!commonValid || !deviceFieldsValid) {
      handleIntent(SignupIntent.Error("Something went wrong"))
      dialogQueueService.dismissLoader()
      return
    }

    val signupData: SignupData = stateValue.form.getValuesAsType()
    AppLog.d(TAG, "Signup data validated for device: $device")
    viewModelScope.launch {
      try {
        val isMetric = signupData.useMetric
        val signupRequest =
          SignupRequest(
            signupData.email.trim(),
            signupData.firstName.trim(),
            signupData.lastName.trim(),
            signupData.sex,
            signupData.zipcode.trim(),
            signupData.password,
            DateTimeValue.getDateFormatFromMilliseconds(controls.birthday.value.getTimestamp()),
            controls.height.value.toStoredHeight(),
            if (isMetric) WeightUnit.KG.value else WeightUnit.LB.value,
          )
        val account = accountService.signup(signupRequest)
        if (account != null) {
          AppLog.i(TAG, "Account created successfully")

          // Device-specific post-signup
          when (device) {
            PickDeviceStrings.Devices.WEIGHT_SCALE -> {
              if (!stateValue.goalSkipped) {
                AppLog.d(TAG, "Creating goal for Weight Scale account...")
                goalService.createGoalForSignup(
                  account = account,
                  goalType = signupData.goalType,
                  startingWeight = signupData.currentWeight.toDoubleOrNull() ?: 0.0,
                  goalWeight = signupData.goalWeight.toDoubleOrNull() ?: 0.0,
                )
              }
            }
            PickDeviceStrings.Devices.BABY_SCALE -> {
              AppLog.d(TAG, "Baby Scale signup — baby profiles stored locally")
            }
            PickDeviceStrings.Devices.BLOOD_PRESSURE -> {
              AppLog.d(TAG, "Blood Pressure signup — no additional setup")
            }
          }

          navigationService.replaceStack(AppRoute.Init.Loading)
          AppLog.i(TAG, "Navigation to loading screen successful after signup")
          handleIntent(SignupIntent.Success)
        } else {
          handleIntent(SignupIntent.Error("Something went wrong"))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Signup failed", e)
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
      ),
    )
  }

  private fun onRequestBack() {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = AppPopupStrings.UnsavedChanges.Title,
        message = AppPopupStrings.UnsavedChanges.Message,
        confirmText = AppPopupStrings.UnsavedChanges.Exit,
        cancelText = AppPopupStrings.UnsavedChanges.Return,
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
        AppLog.e(TAG, "Failed to navigate back from signup", e)
      }
    }
  }
}

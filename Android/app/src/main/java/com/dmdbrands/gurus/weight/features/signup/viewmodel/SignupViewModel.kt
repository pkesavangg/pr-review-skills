package com.dmdbrands.gurus.weight.features.signup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
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
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the multi-device signup flow.
 *
 * Flow ownership:
 *  - Common form input (NAME / EMAIL / BIRTHDAY / PICK_DEVICE / device path / PASSWORD)
 *    runs through the reducer's `Next` advancement.
 *  - On the **last data step** before a Ready terminal screen, the reducer
 *    short-circuits and this ViewModel runs the side-effecting work:
 *    `accountService.signup(...)` on the first pass, or device-specific
 *    side effects only on subsequent loop passes. Once that completes
 *    successfully, [SignupIntent.RegisterDevice] is dispatched so the
 *    reducer can record the device and advance to DEVICE_READY /
 *    ALL_DEVICES_READY.
 *  - [SignupIntent.FinishSignup] navigates to the Dashboard via
 *    `AppRoute.Init.Loading`. [SignupIntent.ConnectAnotherDevice] is
 *    handled entirely by the reducer (returns user to PICK_DEVICE).
 */
@HiltViewModel
class SignupViewModel
@Inject
constructor(
  private val accountService: IAccountService,
  private val goalService: IGoalService,
  private val analyticsService: IAnalyticsService,
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
      is SignupIntent.FinishSignup -> finishSignup()
      is SignupIntent.ConnectAnotherDevice -> AppLog.i(
        TAG,
        "Loop iteration — registered=${state.value.registeredDevices.map { it.id }}",
      )
      else -> {}
    }
    super.handleIntent(intent)
  }

  /**
   * Triggered on every Next dispatch. When the user is on the final data
   * step before a Ready terminal, hand off to [onSubmit] which runs the
   * signup / side-effect work; otherwise the reducer's Next branch (via
   * super.handleIntent) advances to the next form step.
   */
  fun onNext() {
    if (state.value.isFinalDataStep) {
      AppLog.d(TAG, "Final data step reached — running submit")
      onSubmit()
    } else {
      AppLog.d(TAG, "After Next intent - new currentStep: ${state.value.currentStep}")
    }
  }

  /**
   * Runs the signup work for the current device pass.
   *
   *  - **First pass** (`registeredDevices.isEmpty()`): validates all form
   *    fields, creates the account via [IAccountService.signup], then runs
   *    device-specific side effects.
   *  - **Subsequent pass**: skips account creation, reuses the active
   *    session, and runs only device-specific side effects.
   *
   * On success, dispatches [SignupIntent.RegisterDevice] so the reducer
   * records the device and advances to the terminal Ready step. On
   * failure, surfaces an error via [SignupIntent.Error].
   */
  private fun onSubmit() {
    val stateValue = state.value
    val productType = ProductType.fromId(stateValue.form.controls.device.value) ?: run {
      handleIntent(SignupIntent.Error("Missing device"))
      return
    }
    val isFirstPass = stateValue.registeredDevices.isEmpty()

    if (isFirstPass && !validateAllFields(stateValue, productType)) {
      handleIntent(SignupIntent.Error("Something went wrong"))
      return
    }

    dialogQueueService.showLoader(message = SignupStrings.LoaderMessage)
    AppLog.d(TAG, "Submit pass — device=${productType.id} firstPass=$isFirstPass")

    viewModelScope.launch {
      try {
        val account: Account = if (isFirstPass) {
          val created = accountService.signup(buildSignupRequest(stateValue))
          if (created == null) {
            handleIntent(SignupIntent.Error("Something went wrong"))
            return@launch
          }
          AppLog.i(TAG, "Account created successfully")
          analyticsService.logEvent(IAnalyticsService.Events.SIGNUP_COMPLETED)
          created
        } else {
          accountService.activeAccount.value ?: run {
            handleIntent(SignupIntent.Error("Session expired"))
            return@launch
          }
        }

        runDeviceSideEffects(productType, account, stateValue)
        handleIntent(SignupIntent.RegisterDevice)
      } catch (e: Exception) {
        AppLog.e(TAG, "Signup pass failed", e)
        handleIntent(SignupIntent.Error("Signup failed"))
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  /**
   * Validates every form field required for first-pass account creation.
   * Loop iterations skip this — the per-step `isCurrentStepValid` guard
   * in [com.dmdbrands.gurus.weight.features.signup.components.SignupPager]
   * ensures only valid data ever reaches Next on subsequent passes.
   */
  private fun validateAllFields(stateValue: SignupState, productType: ProductType): Boolean {
    val controls = stateValue.form.controls
    val commonValid = listOf(
      controls.firstName.validate(),
      controls.lastName.validate(),
      controls.email.validate(),
      controls.password.validate(),
      controls.confirmPassword.validate(),
      controls.zipcode.validate(),
      controls.birthday.validate(),
    ).all { it }

    val deviceFieldsValid = when (productType) {
      ProductType.MY_WEIGHT -> if (stateValue.goalSkipped) {
        controls.sex.validate()
      } else {
        listOf(
          controls.sex.validate(),
          controls.goalType.validate(),
          controls.goalWeight.validate(),
          controls.currentWeight.validate(),
        ).all { it }
      }
      ProductType.BLOOD_PRESSURE -> controls.sex.validate()
      ProductType.BABY -> true
    }

    return commonValid && deviceFieldsValid
  }

  private fun buildSignupRequest(stateValue: SignupState): SignupRequest {
    val signupData: SignupData = stateValue.form.getValuesAsType()
    val controls = stateValue.form.controls
    val isMetric = signupData.useMetric
    return SignupRequest(
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
  }

  private suspend fun runDeviceSideEffects(
    productType: ProductType,
    account: Account,
    stateValue: SignupState,
  ) {
    val signupData: SignupData = stateValue.form.getValuesAsType()
    when (productType) {
      ProductType.MY_WEIGHT -> if (!stateValue.goalSkipped) {
        AppLog.d(TAG, "Creating goal for Weight Scale account")
        goalService.createGoalForSignup(
          account = account,
          goalType = signupData.goalType,
          startingWeight = signupData.currentWeight.toDoubleOrNull() ?: 0.0,
          goalWeight = signupData.goalWeight.toDoubleOrNull() ?: 0.0,
        )
      }
      ProductType.BABY -> AppLog.d(TAG, "Baby Scale pass — baby profiles stored locally")
      ProductType.BLOOD_PRESSURE -> AppLog.d(TAG, "Blood Pressure pass — no additional setup")
    }
  }

  private fun finishSignup() {
    AppLog.i(TAG, "Finish tapped — replacing stack with Loading → Dashboard")
    viewModelScope.launch {
      try {
        navigationService.replaceStack(AppRoute.Init.Loading)
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to navigate to Dashboard from FinishSignup", e)
      }
    }
  }

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

package com.dmdbrands.gurus.weight.features.signup.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.signup.model.BabyFormControls
import com.dmdbrands.gurus.weight.features.signup.model.SignupIntent
import com.dmdbrands.gurus.weight.features.signup.model.SignupState
import com.dmdbrands.gurus.weight.features.common.components.dismissKeyboardOnTap
import com.dmdbrands.gurus.weight.features.signup.model.SignupStep
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
@Suppress("LongMethod")
fun SignupPager(
  pagerState: PagerState,
  state: SignupState,
  onNext: () -> Unit,
  onBack: () -> Unit,
  onSkip: () -> Unit,
  onUrlOpen: (String) -> Unit,
  onMetricToggle: (Boolean) -> Unit = {},
  onIntent: (SignupIntent) -> Unit = {},
) {
  // Block hardware back-press on terminal Ready screens and during loop
  // iterations of PICK_DEVICE — the user cannot undo a completed device.
  // Falls through to SignupScreenContent's outer BackHandler when disabled.
  BackHandler(enabled = shouldBlockBack(state)) { /* consume */ }

  // Ready screens render outside the pager (no bottom nav bar) — they
  // own their own FINISH / CONNECT ANOTHER DEVICE buttons.
  when (state.currentStep) {
    SignupStep.DEVICE_READY -> {
      DeviceReadyStep(
        deviceId = state.form.controls.device.value,
        onFinish = { onIntent(SignupIntent.FinishSignup) },
        onConnectAnother = { onIntent(SignupIntent.ConnectAnotherDevice) },
      )
      return
    }
    SignupStep.ALL_DEVICES_READY -> {
      AllDevicesReadyStep(
        onFinish = { onIntent(SignupIntent.FinishSignup) },
      )
      return
    }
    SignupStep.ERROR -> {
      SignupErrorStep(
        failedDeviceId = state.form.controls.device.value,
        registeredDevices = state.registeredDevices,
        onFinish = { onIntent(SignupIntent.FinishSignup) },
        onTryAgain = { onIntent(SignupIntent.RetryDevice) },
      )
      return
    }
    else -> Unit
  }

  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val guardedOnNext: () -> Unit = {
    if (state.isCurrentStepValid) {
      focusManager.clearFocus()
      keyboardController?.hide()
      onNext()
    }
  }
  HorizontalPagerWithBottomNavigation(
    steps = state.steps,
    containerColor = MeTheme.colorScheme.secondaryBackground,
    pagerState = pagerState,
    leadingContent = {
      AppButton(
        type = ButtonType.TextPrimary,
        label = SignupStrings.backButton,
        size = ButtonSize.Small,
        // BACK is disabled on the baby list: once a baby is added the user moves forward
        // (NEXT) or edits via the pencil — they cannot navigate back into the form. (model A)
        enabled = !state.isFirstStep && state.currentStep != SignupStep.BABY_ADDED,
        modifier = Modifier.testTag(TestTags.Signup.BackButton),
        onClick = onBack,
      )
    },
    shouldCenterMiddleContent = true,
    middleContent = {
      if (state.showSkipButton) {
        AppButton(
          type = ButtonType.TextTertiary,
          label = SignupStrings.skipButton,
          size = ButtonSize.Small,
          enabled = !state.isLoading,
          modifier = Modifier.testTag(TestTags.Signup.SkipButton),
          onClick = onSkip,
        )
      }
    },
    trailingContent = {
      AppButton(
        type = ButtonType.PrimaryFilled,
        // The form-completion step is the one immediately before the
        // terminal Ready screen — use isFinalDataStep, not isLastStep
        // (Ready is the literal last step now, but it's a success screen
        // with its own buttons, never reached via the pager nav bar).
        label = if (state.isFinalDataStep) SignupStrings.completeButton else SignupStrings.nextButton,
        size = ButtonSize.Small,
        enabled = state.isCurrentStepValid,
        // Same footer button; id switches with the label (Create account on the final data step).
        modifier = Modifier.testTag(
          if (state.isFinalDataStep) TestTags.Signup.CreateAccountButton else TestTags.Signup.NextButton,
        ),
        onClick = guardedOnNext,
      )
    },
    pageContent = {
      Crossfade(targetState = state.currentStep) { step ->
        val formControls = state.form.controls

        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .dismissKeyboardOnTap(),
        ) {
          when (step) {
            SignupStep.NAME ->
              NameStep(
                firstNameControl = formControls.firstName,
                lastNameControl = formControls.lastName,
                onNext = guardedOnNext,
              )

            SignupStep.EMAIL ->
              EmailStep(
                emailControl = formControls.email,
                onNext = guardedOnNext,
              )

            SignupStep.BIRTHDAY ->
              BirthdayStep(
                birthdayControl = formControls.birthday,
              )

            SignupStep.PICK_DEVICE ->
              PickDeviceStep(
                deviceControl = formControls.device,
                registeredDevices = state.registeredDevices,
                onDeviceSelected = { device -> onIntent(SignupIntent.SelectDevice(device)) },
              )

            SignupStep.GENDER ->
              GenderStep(
                genderControl = formControls.sex,
              )

            SignupStep.HEIGHT ->
              HeightStep(
                heightControl = formControls.height,
                useMetricControl = formControls.useMetric,
                onMetricToggle = onMetricToggle,
              )

            SignupStep.GOAL ->
              GoalStep(
                title = SignupStrings.goalStepTitle,
                goalTypeControl = formControls.goalType,
                currentWeightControl = formControls.currentWeight,
                goalWeightControl = formControls.goalWeight,
                isMetric = formControls.useMetric.value,
                onGoalTypeChange = {},
                onNext = guardedOnNext,
                onMetricToggle = { newValue ->
                  formControls.useMetric.onValueChange(newValue)
                  onMetricToggle(newValue)
                },
                showCurrentWeightForMaintain = false,
                showUnitSegment = true,
              )

            SignupStep.ADD_BABY ->
              AddBabyStep(
                babyForm = state.babyState?.babyForm ?: BabyFormControls.create(),
                isEditing = state.babyState?.editingBabyId != null,
                onOpenSexPicker = { onIntent(SignupIntent.OpenBabySexPicker) },
              )

            SignupStep.BABY_ADDED ->
              BabyAddedStep(
                babies = state.babyState?.babies ?: emptyList(),
                onEditBaby = { baby -> onIntent(SignupIntent.EditBaby(baby)) },
                onDeleteBaby = { baby -> onIntent(SignupIntent.DeleteBaby(baby.id)) },
                onAddBaby = { onIntent(SignupIntent.AddAnotherBaby) },
              )

            SignupStep.PASSWORD ->
              PasswordStep(
                passwordControl = formControls.password,
                confirmPasswordControl = formControls.confirmPassword,
                zipcodeControl = formControls.zipcode,
                onUrlOpen = onUrlOpen,
                onSubmit = guardedOnNext,
              )

            // Unreachable here — handled by the early-return above.
            // Branches kept so the `when` over SignupStep stays exhaustive.
            SignupStep.DEVICE_READY,
            SignupStep.ALL_DEVICES_READY,
            SignupStep.ERROR -> Unit
          }
        }
      }
    },
  )
}

/**
 * Hardware back-press is consumed (no-op) when:
 *  - The user is on a terminal Ready screen (DEVICE_READY / ALL_DEVICES_READY).
 *  - The user is on PICK_DEVICE during a loop iteration (≥ 1 device already
 *    registered) — completed devices cannot be undone.
 *
 * Otherwise this returns false and SignupScreenContent's outer BackHandler
 * runs the normal exit-confirm / pop behaviour.
 */
private fun shouldBlockBack(state: SignupState): Boolean =
  state.currentStep == SignupStep.DEVICE_READY ||
    state.currentStep == SignupStep.ALL_DEVICES_READY ||
    state.currentStep == SignupStep.ERROR ||
    // Baby list: a confirmed baby cannot be undone via back — forward (NEXT) / edit / add only.
    state.currentStep == SignupStep.BABY_ADDED ||
    (state.currentStep == SignupStep.PICK_DEVICE && state.registeredDevices.isNotEmpty())

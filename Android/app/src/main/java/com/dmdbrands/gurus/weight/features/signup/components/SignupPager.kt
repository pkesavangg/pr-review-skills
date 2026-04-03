package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.signup.model.BabyFormControls
import com.dmdbrands.gurus.weight.features.signup.model.SignupIntent
import com.dmdbrands.gurus.weight.features.signup.model.SignupState
import com.dmdbrands.gurus.weight.features.signup.model.SignupStep
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
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
  val focusManager = LocalFocusManager.current
  val guardedOnNext: () -> Unit = {
    if (state.isCurrentStepValid) {
      focusManager.clearFocus()
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
        enabled = !state.isFirstStep,
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
          onClick = onSkip,
        )
      }
    },
    trailingContent = {
      AppButton(
        type = ButtonType.PrimaryFilled,
        label = if (state.isLastStep) SignupStrings.completeButton else SignupStrings.nextButton,
        size = ButtonSize.Small,
        enabled = state.isCurrentStepValid,
        onClick = guardedOnNext,
      )
    },
    pageContent = {
      Crossfade(targetState = state.currentStep) { step ->
        val formControls = state.form.controls

        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
                showCurrentWeightForMaintain = false,
              )

            SignupStep.ADD_BABY ->
              AddBabyStep(
                babyForm = state.babyState?.babyForm ?: BabyFormControls.create(),
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
          }
        }
      }
    },
  )
}

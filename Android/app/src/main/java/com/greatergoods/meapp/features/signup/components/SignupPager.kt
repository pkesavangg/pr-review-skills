package com.greatergoods.meapp.features.signup.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.HorizontalPagerWithBottomNavigation
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.signup.model.SignupState
import com.greatergoods.meapp.features.signup.model.SignupStep
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun SignupPager(
    pagerState: PagerState,
    state: SignupState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onUrlOpen: (String) -> Unit,
) {
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
                onClick = onNext,
            )
        },
        pageContent =
            {
                Crossfade(targetState = state.currentStep) { step ->
                    val formControls = state.form.controls

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                    ) {
                        when (step) {
                            SignupStep.NAME ->
                                NameStep(
                                    firstNameControl = formControls.firstName,
                                    lastNameControl = formControls.lastName,
                                )

                            SignupStep.BIRTHDAY ->
                                BirthdayStep(
                                    birthdayControl = formControls.birthday,
                                )

                            SignupStep.GENDER ->
                                GenderStep(
                                    genderControl = formControls.sex,
                                )

                            SignupStep.HEIGHT ->
                                HeightStep(
                                    heightControl = formControls.height,
                                )

                            SignupStep.GOAL ->
                                GoalStep(
                                    goalTypeControl = formControls.goalType,
                                    currentWeightControl = formControls.currentWeight,
                                    goalWeightControl = formControls.goalWeight,
                                    useMetricControl = FormControl.create(false, emptyList()), // Always false for lbs
                                )

                            SignupStep.EMAIL ->
                                EmailStep(
                                    emailControl = formControls.email,
                                )

                            SignupStep.PASSWORD ->
                                PasswordStep(
                                    passwordControl = formControls.password,
                                    confirmPasswordControl = formControls.confirmPassword,
                                    zipcodeControl = formControls.zipcode,
                                    onUrlOpen = onUrlOpen,
                                )
                        }
                    }
                }
            },
    )
}

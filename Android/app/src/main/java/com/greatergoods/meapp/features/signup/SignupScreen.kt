package com.greatergoods.meapp.features.signup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppLinearProgressIndicator
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.CardAlignmentType
import com.greatergoods.meapp.features.common.components.HorizontalPagerWithBottomNavigation
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.composition.LocalCardAlignment
import com.greatergoods.meapp.features.signup.components.BirthdayStep
import com.greatergoods.meapp.features.signup.components.EmailStep
import com.greatergoods.meapp.features.signup.components.GenderStep
import com.greatergoods.meapp.features.signup.components.GoalStep
import com.greatergoods.meapp.features.signup.components.HeightStep
import com.greatergoods.meapp.features.signup.components.NameStep
import com.greatergoods.meapp.features.signup.components.PasswordStep
import com.greatergoods.meapp.features.signup.model.SignupData
import com.greatergoods.meapp.features.signup.model.SignupStep
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme

/**
 * Main signup screen with horizontal pager navigation
 */
@Composable
fun SignupScreen(
    viewModel: SignupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState { uiState.steps.size }

    LaunchedEffect(uiState.currentStep) {
        if (pagerState.currentPage != uiState.currentStepIndex) {
            pagerState.animateScrollToPage(uiState.currentStepIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val newStep = uiState.steps[pagerState.currentPage]
        if (newStep != uiState.currentStep) {
            viewModel.onStepChanged(newStep)
        }
    }

    val windowSize = LocalWindowInfo.current.containerSize
    val isTablet = with(LocalDensity.current) {
        windowSize.width.toDp() > 600.dp
    }
    val cardAlignment = if (isTablet) CardAlignmentType.TopCenter else CardAlignmentType.TopStart

    AppScaffold(
        title = "",
        containerColor = MeTheme.colorScheme.secondaryBackground,
        navigationIcon = { AppIconButton(AppIcons.Default.Close) {} },
        actions = { AppIconButton(AppIcons.Outlined.Help) {} }
    ) {
        Column(modifier = Modifier) {
            AppLinearProgressIndicator(
                progress = uiState.progress,
                modifier = Modifier
                    .padding(
                        bottom = 24.dp,
                        top = 16.dp,
                        start = MeTheme.spacing.sm,
                        end = MeTheme.spacing.sm
                    )
                    .height(8.dp)
            )
            CompositionLocalProvider(LocalCardAlignment provides cardAlignment) {
                SignupPager(
                    pagerState = pagerState,
                    uiState = uiState,
                    onDataChange = viewModel::onDataChange,
                    onNext = viewModel::onNext,
                    onBack = viewModel::onBack,
                )
            }
        }
    }
}

@Composable
private fun SignupPager(
    pagerState: PagerState,
    uiState: SignupUiState,
    onDataChange: (SignupData) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    HorizontalPagerWithBottomNavigation(
        steps = uiState.steps,
        containerColor = MeTheme.colorScheme.secondaryBackground,
        pagerState = pagerState,
        leadingContent = {
            AppButton(
                type = ButtonType.TextPrimary,
                label = SignupStrings.backButton,
                size = ButtonSize.Small,
                enabled = !uiState.isFirstStep,
                onClick = onBack
            )
        },
        middleContent = {
            if (uiState.showSkipButton) {
                AppButton(
                    type = ButtonType.TextTertiary,
                    label = SignupStrings.skipButton,
                    size = ButtonSize.Small,
                    onClick = onNext
                )
            }
        },
        trailingContent = {
            AppButton(
                type = ButtonType.PrimaryFilled,
                label = if (uiState.isLastStep) SignupStrings.completeButton else SignupStrings.nextButton,
                size = ButtonSize.Small,
                enabled = !uiState.isLoading,
                onClick = onNext
            )
        },
        pageContent = { step ->
            val signupData = uiState.signupData
            when (step) {
                SignupStep.NAME -> NameStep(
                    signupData = signupData,
                    onFirstNameChange = { onDataChange(signupData.copy(firstName = it)) },
                    onLastNameChange = { onDataChange(signupData.copy(lastName = it)) }
                )

                SignupStep.BIRTHDAY -> BirthdayStep(
                    signupData = signupData,
                    onBirthdayChange = { onDataChange(signupData.copy(birthday = it)) }
                )

                SignupStep.GENDER -> GenderStep(
                    signupData = signupData,
                    onGenderChange = { onDataChange(signupData.copy(gender = it)) }
                )

                SignupStep.HEIGHT -> HeightStep(
                    signupData = signupData,
                    onHeightChange = { onDataChange(signupData.copy(height = it)) }
                )

                SignupStep.GOAL -> GoalStep(
                    signupData = signupData,
                    onGoalTypeChange = { onDataChange(signupData.copy(goalType = it)) },
                    onCurrentWeightChange = { onDataChange(signupData.copy(currentWeight = it)) },
                    onGoalWeightChange = { onDataChange(signupData.copy(goalWeight = it)) },
                )

                SignupStep.EMAIL -> EmailStep(
                    signupData = signupData,
                    onEmailChange = { onDataChange(signupData.copy(email = it)) }
                )

                SignupStep.PASSWORD -> PasswordStep(
                    signupData = signupData,
                    onPasswordChange = { onDataChange(signupData.copy(password = it)) },
                    onConfirmPasswordChange = { onDataChange(signupData.copy(confirmPassword = it)) },
                    onZipcodeChange = { onDataChange(signupData.copy(zipcode = it)) }
                )
            }
        }
    )
}

@PreviewTheme
@Composable
fun PreviewSignupScreen() {
    SignupScreen()
}





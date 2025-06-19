package com.greatergoods.meapp.features.signup

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.helper.form.FormControl
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
import com.greatergoods.meapp.features.signup.model.SignupState
import com.greatergoods.meapp.features.signup.model.SignupStep
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.coroutines.delay

/**
 * Main signup screen with horizontal pager navigation
 */
@Composable
fun SignupScreen(
    viewModel: SignupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState { state.steps.size }
    val backStack = LocalNavBackStack.current
    val coroutineScope = rememberCoroutineScope()

    // Track if we're currently animating to prevent conflicts
    val isAnimating = remember { mutableStateOf(false) }

    // Sync ViewModel state to Pager state (when ViewModel changes, update pager)
    LaunchedEffect(state.currentStep) {
        if (!isAnimating.value && pagerState.currentPage != state.currentStepIndex) {
            isAnimating.value = true
            try {
                pagerState.animateScrollToPage(state.currentStepIndex)
            } finally {
                // Add small delay to prevent rapid transitions
                delay(100)
                isAnimating.value = false
            }
        }
    }

    // Sync Pager state to ViewModel state (when user swipes, update ViewModel)
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        // Only update ViewModel when pager stops scrolling and we're not programmatically animating
        if (!pagerState.isScrollInProgress && !isAnimating.value) {
            val newStep = state.steps[pagerState.currentPage]
            if (newStep != state.currentStep) {
                viewModel.goToStep(newStep)
            }
        }
    }

    // Observe form value changes to trigger state updates for Next button reactivity
    LaunchedEffect(
        state.form.controls.firstName.value,
        state.form.controls.lastName.value,
        state.form.controls.email.value,
        state.form.controls.password.value,
        state.form.controls.confirmPassword.value,
        state.form.controls.zipcode.value,
        state.form.controls.birthday.value,
        state.form.controls.sex.value,
        state.form.controls.goalType.value,
        state.form.controls.currentWeight.value,
        state.form.controls.goalWeight.value,
        state.currentStep,
        state.goalSkipped
    ) {
        // Trigger state update when form values change
        viewModel.onFormChanged()
    }

    val windowSize = LocalWindowInfo.current.containerSize
    val isTablet = with(LocalDensity.current) {
        windowSize.width.toDp() > 600.dp
    }
    val cardAlignment = if (isTablet) CardAlignmentType.TopCenter else CardAlignmentType.TopStart

    AppScaffold(
        title = "",
        containerColor = MeTheme.colorScheme.secondaryBackground,
        appBarColor = MeTheme.colorScheme.secondaryBackground,
        navigationIcon = {  AppIconButton(AppIcons.Default.Close) { backStack.removeLast()}  },
        actions = { AppIconButton(AppIcons.Outlined.Help) {} }
    ) {
        Column(modifier = Modifier.fillMaxSize()
             ) {
            AppLinearProgressIndicator(
                progress = state.progress,
                modifier = Modifier
                    .padding(
                        bottom = MeTheme.spacing.md,
                        top = MeTheme.spacing.sm,
                        start = MeTheme.spacing.sm,
                        end = MeTheme.spacing.sm
                    )
                    .height(MeTheme.spacing.xs)
            )
            CompositionLocalProvider(LocalCardAlignment provides cardAlignment) {
                SignupPager(
                    pagerState = pagerState,
                    state = state,
                    onNext = viewModel::onNext,
                    onBack = viewModel::onBack,
                    onSkip = viewModel::onSkip,
                    onUrlOpen = viewModel::openUrl,
                )
            }
        }
    }
}

@Composable
private fun SignupPager(
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
                onClick = onBack
            )
        },
        middleContent = {
            if (state.showSkipButton) {
                AppButton(
                    type = ButtonType.TextTertiary,
                    label = SignupStrings.skipButton,
                    size = ButtonSize.Small,
                    enabled = !state.isLoading,
                    onClick = onSkip
                )
            }
        },
        trailingContent = {
            AppButton(
                type = ButtonType.PrimaryFilled,
                label = if (state.isLastStep) SignupStrings.completeButton else SignupStrings.nextButton,
                size = ButtonSize.Small,
                enabled = state.isCurrentStepValid,
                onClick = onNext
            )
        },
        pageContent =
            {
                Crossfade(targetState = state.currentStep){ step ->
                    val formControls = state.form.controls

                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    ) {
                        when (step) {
                            SignupStep.NAME -> NameStep(
                                firstNameControl = formControls.firstName,
                                lastNameControl = formControls.lastName,
                            )

                            SignupStep.BIRTHDAY -> BirthdayStep(
                                birthdayControl = formControls.birthday,
                            )

                            SignupStep.GENDER -> GenderStep(
                                genderControl = formControls.sex,
                            )

                            SignupStep.HEIGHT -> HeightStep(
                                heightControl = formControls.height,
                            )

                            SignupStep.GOAL -> GoalStep(
                                goalTypeControl = formControls.goalType,
                                currentWeightControl = formControls.currentWeight,
                                goalWeightControl = formControls.goalWeight,
                                useMetricControl = FormControl.create(false, emptyList()), // Always false for lbs
                            )

                            SignupStep.EMAIL -> EmailStep(
                                emailControl = formControls.email,
                            )

                            SignupStep.PASSWORD -> PasswordStep(
                                passwordControl = formControls.password,
                                confirmPasswordControl = formControls.confirmPassword,
                                zipcodeControl = formControls.zipcode,
                                onUrlOpen = onUrlOpen,
                            )
                        }
                    }
                }
            }
    )
}

@PreviewTheme
@Composable
fun PreviewSignupScreen() {
    SignupScreen()
}





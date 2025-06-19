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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import com.example.nav3integration.TopLevelBackStack
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
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
import com.greatergoods.meapp.features.common.helper.form.FormControl
import com.greatergoods.meapp.features.common.helper.form.FormGroup
import com.greatergoods.meapp.features.signup.components.BirthdayStep
import com.greatergoods.meapp.features.signup.components.EmailStep
import com.greatergoods.meapp.features.signup.components.GenderStep
import com.greatergoods.meapp.features.signup.components.GoalStep
import com.greatergoods.meapp.features.signup.components.HeightStep
import com.greatergoods.meapp.features.signup.components.NameStep
import com.greatergoods.meapp.features.signup.components.PasswordStep
import com.greatergoods.meapp.features.signup.components.SignupPager
import com.greatergoods.meapp.features.signup.model.SignupFormControls
import com.greatergoods.meapp.features.signup.model.SignupIntent
import com.greatergoods.meapp.features.signup.model.SignupState
import com.greatergoods.meapp.features.signup.model.SignupStep
import com.greatergoods.meapp.features.signup.strings.SignupStrings
import com.greatergoods.meapp.features.signup.viewmodel.SignupViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.coroutines.delay

/**
 * Main signup screen with horizontal pager navigation
 */
@Composable
fun SignupScreen(viewModel: SignupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val backStack = LocalNavBackStack.current

    SignupScreenContent(state, viewModel::handleIntent) {
        backStack.removeLast()
    }
}

@Composable
fun SignupScreenContent(
    state: SignupState,
    handleIntent: (SignupIntent) -> Unit,
    onBack: () -> Unit,
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val isTablet =
        with(LocalDensity.current) {
            windowSize.width.toDp() > 600.dp
        }
    val cardAlignment = if (isTablet) CardAlignmentType.TopCenter else CardAlignmentType.TopStart
    val pagerState = rememberPagerState { state.steps.size }

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
                handleIntent(SignupIntent.GoToStep(newStep))
            }
        }
    }

    AppScaffold(
        title = "",
        containerColor = MeTheme.colorScheme.secondaryBackground,
        appBarColor = MeTheme.colorScheme.secondaryBackground,
        navigationIcon = { AppIconButton(AppIcons.Default.Close) { onBack() } },
        actions = { AppIconButton(AppIcons.Outlined.Help) {} },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            AppLinearProgressIndicator(
                progress = state.progress,
                modifier =
                    Modifier
                        .padding(
                            bottom = MeTheme.spacing.md,
                            top = MeTheme.spacing.sm,
                            start = MeTheme.spacing.sm,
                            end = MeTheme.spacing.sm,
                        ).height(MeTheme.spacing.xs),
            )
            CompositionLocalProvider(LocalCardAlignment provides cardAlignment) {
                SignupPager(
                    pagerState = pagerState,
                    state = state,
                    onNext = { handleIntent(SignupIntent.Next) },
                    onBack = { handleIntent(SignupIntent.Back) },
                    onSkip = { handleIntent(SignupIntent.Skip) },
                    onUrlOpen = { handleIntent(SignupIntent.OpenURL(it)) },
                )
            }
        }
    }
}


@PreviewTheme
@Composable
fun PreviewSignupScreen() {
    MeAppTheme {
        SignupScreenContent(
            SignupState(
                form = FormGroup(SignupFormControls.create()),
            ),
            {}
        ) {}
    }
}

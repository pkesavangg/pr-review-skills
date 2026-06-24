package com.dmdbrands.gurus.weight.features.signup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppLinearProgressIndicator
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.CardAlignmentType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.signup.components.SignupPager
import com.dmdbrands.gurus.weight.features.signup.model.SignupFormControls
import com.dmdbrands.gurus.weight.features.signup.model.SignupIntent
import com.dmdbrands.gurus.weight.features.signup.model.SignupState
import com.dmdbrands.gurus.weight.features.signup.viewmodel.SignupViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main signup screen with horizontal pager navigation
 */
@Composable
fun SignupScreen(viewModel: SignupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backStack = LocalNavBackStack.current
    val coroutineScope = rememberCoroutineScope()
    SignupScreenContent(state, viewModel::handleIntent) {
        coroutineScope.launch {
            backStack.removeLast()
        }
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
    val pagerState = rememberPagerState(pageCount = { state.steps.size })
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

    val handleBack = {
        if (state.form.isDirty) {
            handleIntent(SignupIntent.OnRequestBack)
        } else {
            onBack()
        }
    }

    BackHandler {
        handleBack()
    }

    AppScaffold(
        title = "",
        containerColor = MeTheme.colorScheme.secondaryBackground,
        appBarColor = MeTheme.colorScheme.secondaryBackground,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) {
                handleBack()
            }
        },
        borderColor = Color.Transparent,
        actions = { AppIconButton(AppIcons.Outlined.Help) { handleIntent.invoke(SignupIntent.OpenHelpModal) } },
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
                    onMetricToggle = { handleIntent(SignupIntent.ToggleMetric(it)) },
                    onIntent = handleIntent,
                )
                Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.md))
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
            {},
        ) {}
    }
}

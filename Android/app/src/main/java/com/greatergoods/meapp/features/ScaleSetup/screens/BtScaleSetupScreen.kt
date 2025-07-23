package com.greatergoods.meapp.features.ScaleSetup.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.ScaleSetup.components.ScaleInfo
import com.greatergoods.meapp.features.ScaleSetup.components.ScaleSetupHeader
import com.greatergoods.meapp.features.ScaleSetup.enums.BtScaleSetupStep
import com.greatergoods.meapp.features.ScaleSetup.modal.SetupInitData
import com.greatergoods.meapp.features.ScaleSetup.reducer.BtScaleSetupState
import com.greatergoods.meapp.features.ScaleSetup.reducer.ScaleSetupIntent
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.ScaleSetup.viewmodel.BtScaleSetupViewModel
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.HorizontalPagerWithBottomNavigation
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.coroutines.delay

@Composable
fun BtScaleSetupScreen(
  sku: String,
) {
  val setupInit = SetupInitData(
    sku = sku,
    initialStep = BtScaleSetupStep.SCALE_INFO,
  )
  val viewModel: BtScaleSetupViewModel =
    hiltViewModel<BtScaleSetupViewModel, BtScaleSetupViewModel.Factory> { factory ->
      factory.create(setupInit)
    }
  val state by viewModel.state.collectAsState()
  BtScaleSetupScreenContent(
    state = state,
    onIntent = viewModel::handleIntent,
  )
}

@Composable
fun BtScaleSetupScreenContent(
  state: BtScaleSetupState,
  onIntent: (ScaleSetupIntent) -> Unit,
) {
  val sku = state.scaleSetupState.sku
  val focusManager = LocalFocusManager.current
  val pagerState = rememberPagerState { state.scaleSetupState.steps.size }
  val isAnimating = remember { mutableStateOf(false) }

  // Sync ViewModel state to Pager state
  LaunchedEffect(state.scaleSetupState.setupState.step) {
    if (!isAnimating.value) {
      isAnimating.value = true
      try {
        pagerState.animateScrollToPage(state.step.ordinal)
      } finally {
        delay(100)
        isAnimating.value = false
      }
    }
  }
  ScaleSetupHeader(
    sku = sku,
    onBack = { onIntent(ScaleSetupIntent.ExitSetup(false)) },
    onHelp = { onIntent(ScaleSetupIntent.OpenHelp) },
  ) {
    HorizontalPagerWithBottomNavigation(
      steps = state.steps,
      containerColor = MeTheme.colorScheme.secondaryBackground,
      pagerState = pagerState,
      leadingContent = {
        AppButton(
          type = ButtonType.TextPrimary,
          label = ScaleSetupStrings.backButton,
          size = ButtonSize.Small,
          enabled = !state.isFirstStep,
          onClick = { onIntent(ScaleSetupIntent.Back) },
        )
      },
      middleContent = {
        // Skip button can be added here when needed for other steps
      },
      trailingContent = {
        AppButton(
          type = ButtonType.PrimaryFilled,
          label = if (state.isLastStep) ScaleSetupStrings.FinishButton else ScaleSetupStrings.nextButton,
          size = ButtonSize.Small,
          enabled = !state.isLoading,
          onClick = {
            focusManager.clearFocus()
            onIntent(ScaleSetupIntent.Next)
          },
        )
      },
      pageContent = { step ->
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(MeTheme.spacing.md),
        ) {
          when (step) {
            BtScaleSetupStep.SCALE_INFO -> {
              ScaleInfo(sku = sku)
            }
            // TODO: Add other steps as needed
            else -> {
              // Placeholder for other steps
            }
          }
        }
      },
    )
  }
}
